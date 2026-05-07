package datingapp.app.usecase.auth;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.TextNormalization;
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

    public UseCaseResult<AuthSession> signup(SignupCommand command) {
        try {
            String normalizedEmail = normalizeEmail(command.email());
            validatePassword(command.password());
            validateDateOfBirth(command.dateOfBirth());
            if (userStorage.findByEmail(normalizedEmail).isPresent()) {
                return UseCaseResult.failure(UseCaseError.conflict("Account already exists for email"));
            }

            Instant now = AppClock.now();
            User user = User.StorageBuilder.create(UUID.randomUUID(), normalizeSignupName(command.name()), now)
                    .email(normalizedEmail)
                    .birthDate(command.dateOfBirth())
                    .updatedAt(now)
                    .build();
            userStorage.save(user);
            authStorage.savePasswordHash(
                    user.getId(), BCrypt.hashpw(command.password(), BCrypt.gensalt(BCRYPT_LOG_ROUNDS)), now, now);
            return UseCaseResult.success(createSession(user, now));
        } catch (IllegalArgumentException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        }
    }

    public UseCaseResult<AuthSession> login(LoginCommand command) {
        try {
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
            return UseCaseResult.success(createSession(user, AppClock.now()));
        } catch (UnauthorizedException e) {
            return UseCaseResult.failure(UseCaseError.unauthorized(e.getMessage()));
        } catch (IllegalArgumentException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        }
    }

    public UseCaseResult<AuthSession> refresh(String rawRefreshToken) {
        try {
            RefreshTokenRecord currentToken = requireRefreshToken(rawRefreshToken);
            Instant now = AppClock.now();
            User user = userStorage.get(currentToken.userId()).orElseThrow(UnauthorizedException::invalidRefreshToken);
            if (isDeletedOrBanned(user)) {
                throw UnauthorizedException.invalidRefreshToken();
            }
            IssuedRefreshToken rotatedToken = issueRefreshToken(user.getId(), now);
            authStorage.insertRefreshToken(rotatedToken.refreshTokenRecord());
            authStorage.revokeRefreshToken(
                    currentToken.tokenId(),
                    now,
                    rotatedToken.refreshTokenRecord().tokenId());
            return UseCaseResult.success(buildSession(user, now, rotatedToken.rawToken()));
        } catch (UnauthorizedException e) {
            return UseCaseResult.failure(UseCaseError.unauthorized(e.getMessage()));
        }
    }

    public UseCaseResult<Void> logout(String rawRefreshToken) {
        try {
            RefreshTokenRecord currentToken = requireRefreshToken(rawRefreshToken);
            authStorage.revokeRefreshToken(currentToken.tokenId(), AppClock.now(), null);
            return UseCaseResult.success(null);
        } catch (UnauthorizedException e) {
            return UseCaseResult.failure(UseCaseError.unauthorized(e.getMessage()));
        }
    }

    public UseCaseResult<AuthUser> requireAuthenticatedUser(UUID userId) {
        try {
            User user = userStorage.get(userId).orElseThrow(UnauthorizedException::invalidCredentials);
            if (isDeletedOrBanned(user)) {
                throw UnauthorizedException.invalidCredentials();
            }
            return UseCaseResult.success(AuthUser.from(user));
        } catch (UnauthorizedException e) {
            return UseCaseResult.failure(UseCaseError.unauthorized(e.getMessage()));
        }
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
        User authUser = user.get();
        return Optional.of(
                new AuthIdentity(authUser.getId(), AuthUser.from(authUser).email()));
    }

    private static boolean isDeletedOrBanned(User user) {
        return user.getDeletedAt() != null || user.getState() == User.UserState.BANNED;
    }

    private AuthSession createSession(User user, Instant now) {
        IssuedRefreshToken refreshToken = issueRefreshToken(user.getId(), now);
        authStorage.insertRefreshToken(refreshToken.refreshTokenRecord());
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
        Instant expiresAt = now.plusSeconds(config.auth().refreshTokenTtlDays() * 24L * 60L * 60L);
        RefreshTokenRecord refreshTokenRecord =
                new RefreshTokenRecord(UUID.randomUUID(), userId, tokenHash, now, expiresAt, null, null);
        return new IssuedRefreshToken(rawToken, refreshTokenRecord);
    }

    private RefreshTokenRecord requireRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw UnauthorizedException.invalidRefreshToken();
        }
        Instant now = AppClock.now();
        RefreshTokenRecord refreshTokenRecord = authStorage
                .findRefreshTokenByHash(hashRefreshToken(rawRefreshToken))
                .orElseThrow(UnauthorizedException::invalidRefreshToken);
        if (refreshTokenRecord.revokedAt() != null
                || !refreshTokenRecord.expiresAt().isAfter(now)) {
            throw UnauthorizedException.invalidRefreshToken();
        }
        return refreshTokenRecord;
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
        return TextNormalization.normalizeEmail(email);
    }

    private static String normalizeSignupName(String name) {
        if (name == null || name.isBlank()) {
            return User.SIGNUP_PLACEHOLDER_NAME;
        }
        return name.trim();
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

    private record IssuedRefreshToken(String rawToken, RefreshTokenRecord refreshTokenRecord) {}

    public record SignupCommand(String email, String password, LocalDate dateOfBirth, String name) {}

    public record LoginCommand(String email, String password) {}

    public record AuthIdentity(UUID userId, String email) {}

    public record AuthSession(String accessToken, String refreshToken, int expiresInSeconds, AuthUser user) {}

    public record AuthUser(UUID id, String email, String displayName, String profileCompletionState) {
        static AuthUser from(User user) {
            String displayName = user.getName() == null
                            || user.getName().isBlank()
                            || User.SIGNUP_PLACEHOLDER_NAME.equals(user.getName())
                    ? null
                    : user.getName();
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
