package datingapp.app.usecase.auth;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.storage.AuthStorage;
import datingapp.core.storage.AuthStorage.RefreshTokenRecord;
import datingapp.core.storage.UserStorage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;

public final class AuthUseCases {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final int BCRYPT_LOG_ROUNDS = 12;

    private final AppConfig config;
    private final UserStorage userStorage;
    private final AuthStorage authStorage;
    private final AuthTokenService authTokenService;

    public AuthUseCases(
            AppConfig config, UserStorage userStorage, AuthStorage authStorage, AuthTokenService authTokenService) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.authStorage = Objects.requireNonNull(authStorage, "authStorage cannot be null");
        this.authTokenService = Objects.requireNonNull(authTokenService, "authTokenService cannot be null");
    }

    public AuthSession signup(SignupCommand command) {
        String normalizedEmail = normalizeEmail(command.email());
        validatePassword(command.password());
        validateDateOfBirth(command.dateOfBirth());
        if (userStorage.findByEmail(normalizedEmail).isPresent()) {
            throw new DuplicateAccountException("Account already exists for email");
        }

        Instant now = AppClock.now();
        User user = User.StorageBuilder.create(UUID.randomUUID(), "", now)
                .email(normalizedEmail)
                .birthDate(command.dateOfBirth())
                .updatedAt(now)
                .build();
        userStorage.save(user);
        authStorage.savePasswordHash(
                user.getId(), BCrypt.hashpw(command.password(), BCrypt.gensalt(BCRYPT_LOG_ROUNDS)), now, now);
        return createSession(user, now);
    }

    public AuthSession login(LoginCommand command) {
        String normalizedEmail = normalizeEmail(command.email());
        User user = userStorage.findByEmail(normalizedEmail).orElseThrow(UnauthorizedException::invalidCredentials);
        if (isDeletedOrBanned(user)) {
            throw UnauthorizedException.invalidCredentials();
        }
        String passwordHash =
                authStorage.findPasswordHash(user.getId()).orElseThrow(UnauthorizedException::invalidCredentials);
        if (!BCrypt.checkpw(command.password(), passwordHash)) {
            throw UnauthorizedException.invalidCredentials();
        }
        return createSession(user, AppClock.now());
    }

    public AuthSession refresh(String rawRefreshToken) {
        RefreshTokenRecord currentToken = requireRefreshToken(rawRefreshToken);
        Instant now = AppClock.now();
        User user = userStorage.get(currentToken.userId()).orElseThrow(UnauthorizedException::invalidRefreshToken);
        if (isDeletedOrBanned(user)) {
            throw UnauthorizedException.invalidRefreshToken();
        }
        IssuedRefreshToken rotatedToken = issueRefreshToken(user.getId(), now);
        authStorage.insertRefreshToken(rotatedToken.record());
        authStorage.revokeRefreshToken(
                currentToken.tokenId(), now, rotatedToken.record().tokenId());
        return buildSession(user, now, rotatedToken.rawToken());
    }

    public void logout(String rawRefreshToken) {
        RefreshTokenRecord currentToken = requireRefreshToken(rawRefreshToken);
        authStorage.revokeRefreshToken(currentToken.tokenId(), AppClock.now(), null);
    }

    public AuthUser requireAuthenticatedUser(UUID userId) {
        User user = userStorage.get(userId).orElseThrow(UnauthorizedException::invalidCredentials);
        if (isDeletedOrBanned(user)) {
            throw UnauthorizedException.invalidCredentials();
        }
        return AuthUser.from(user);
    }

    public Optional<AuthIdentity> authenticateAccessToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthIdentity> identity = authTokenService.validateAccessToken(token, AppClock.now());
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        Optional<User> user = userStorage.get(identity.get().userId());
        if (user.isEmpty() || isDeletedOrBanned(user.get())) {
            return Optional.empty();
        }
        return identity;
    }

    private static boolean isDeletedOrBanned(User user) {
        return user.getDeletedAt() != null || user.getState() == User.UserState.BANNED;
    }

    private AuthSession createSession(User user, Instant now) {
        IssuedRefreshToken refreshToken = issueRefreshToken(user.getId(), now);
        authStorage.insertRefreshToken(refreshToken.record());
        return buildSession(user, now, refreshToken.rawToken());
    }

    private AuthSession buildSession(User user, Instant now, String rawRefreshToken) {
        AuthUser authUser = AuthUser.from(user);
        String accessToken = authTokenService.issueAccessToken(new AuthIdentity(user.getId(), authUser.email()), now);
        return new AuthSession(accessToken, rawRefreshToken, config.auth().accessTokenTtlSeconds(), authUser);
    }

    private IssuedRefreshToken issueRefreshToken(UUID userId, Instant now) {
        String rawToken = generateOpaqueToken();
        String tokenHash = hashRefreshToken(rawToken);
        Instant expiresAt = now.plusSeconds((long) config.auth().refreshTokenTtlDays() * 24L * 60L * 60L);
        RefreshTokenRecord record =
                new RefreshTokenRecord(UUID.randomUUID(), userId, tokenHash, now, expiresAt, null, null);
        return new IssuedRefreshToken(rawToken, record);
    }

    private RefreshTokenRecord requireRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw UnauthorizedException.invalidRefreshToken();
        }
        Instant now = AppClock.now();
        RefreshTokenRecord record = authStorage
                .findRefreshTokenByHash(hashRefreshToken(rawRefreshToken))
                .orElseThrow(UnauthorizedException::invalidRefreshToken);
        if (record.revokedAt() != null || !record.expiresAt().isAfter(now)) {
            throw UnauthorizedException.invalidRefreshToken();
        }
        return record;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < config.auth().minPasswordLength()) {
            throw new IllegalArgumentException(
                    "Password must be at least " + config.auth().minPasswordLength() + " characters long");
        }
    }

    private void validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            throw new IllegalArgumentException("dateOfBirth is required");
        }
        int age = Period.between(dateOfBirth, AppClock.today()).getYears();
        if (age < config.validation().minAge()) {
            throw new IllegalArgumentException(
                    "User must be at least " + config.validation().minAge() + " years old");
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashRefreshToken(String rawRefreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(digest.digest(rawRefreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash refresh token", ex);
        }
    }

    private record IssuedRefreshToken(String rawToken, RefreshTokenRecord record) {}

    public record SignupCommand(String email, String password, LocalDate dateOfBirth) {}

    public record LoginCommand(String email, String password) {}

    public record AuthIdentity(UUID userId, String email) {}

    public record AuthSession(String accessToken, String refreshToken, int expiresInSeconds, AuthUser user) {}

    public record AuthUser(UUID id, String email, String displayName, String profileCompletionState) {
        static AuthUser from(User user) {
            String displayName = user.getName() == null || user.getName().isBlank() ? null : user.getName();
            String profileCompletionState;
            if (user.isComplete()) {
                profileCompletionState = "complete";
            } else {
                List<String> missingProfileFields = user.getMissingProfileFields();
                String firstMissingField = missingProfileFields.isEmpty() ? "unknown" : missingProfileFields.getFirst();
                profileCompletionState = "needs_" + firstMissingField;
            }
            return new AuthUser(user.getId(), user.getEmail(), displayName, profileCompletionState);
        }
    }

    public static final class DuplicateAccountException extends IllegalStateException {
        public DuplicateAccountException(String message) {
            super(message);
        }
    }

    public static final class UnauthorizedException extends RuntimeException {
        private UnauthorizedException(String message) {
            super(message);
        }

        static UnauthorizedException invalidCredentials() {
            return new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        static UnauthorizedException invalidRefreshToken() {
            return new UnauthorizedException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
    }
}
