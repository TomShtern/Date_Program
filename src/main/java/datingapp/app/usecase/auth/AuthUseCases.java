package datingapp.app.usecase.auth;

import datingapp.app.usecase.auth.AccessTokenVerifier.VerifiedToken;
import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.core.storage.AuthStorage;
import datingapp.core.storage.UserStorage;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Maps Clerk-authenticated callers onto local users. Clerk owns sign-up, sign-in and sessions; this class only
 * verifies the session token, resolves the Clerk user to a local user id and creates that local profile on first use.
 */
public final class AuthUseCases {

    private static final String INVALID_TOKEN_MESSAGE = "Missing or invalid bearer token";
    private static final String NOT_PROVISIONED_MESSAGE =
            "Signed in, but no profile exists yet. Call POST /api/auth/session";
    private static final int LOCK_STRIPES = 64;

    private final UserStorage userStorage;
    private final AuthStorage authStorage;
    private final AccessTokenVerifier tokenVerifier;
    /** Serialises find-then-create per Clerk id inside this process. The primary key is the cross-process backstop. */
    private final ReentrantLock[] provisionLocks = new ReentrantLock[LOCK_STRIPES];

    public AuthUseCases(UserStorage userStorage, AuthStorage authStorage, AccessTokenVerifier tokenVerifier) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.authStorage = Objects.requireNonNull(authStorage, "authStorage cannot be null");
        this.tokenVerifier = Objects.requireNonNull(tokenVerifier, "tokenVerifier cannot be null");
        for (int i = 0; i < LOCK_STRIPES; i++) {
            provisionLocks[i] = new ReentrantLock();
        }
    }

    /**
     * Resolves a Clerk session token to the acting local user.
     *
     * @return the identity, or empty when the token is invalid or the user is banned
     * @throws NotProvisionedException when the token is valid but its Clerk user has no live local profile: never
     *     provisioned, or the profile was deleted
     */
    public Optional<AuthIdentity> authenticateAccessToken(String token) {
        Optional<VerifiedToken> verified = tokenVerifier.verify(token);
        if (verified.isEmpty()) {
            return Optional.empty();
        }
        VerifiedToken claims = verified.get();
        User user = authStorage
                .findUserIdByClerkId(claims.subject())
                .flatMap(userStorage::get)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(() -> new NotProvisionedException(NOT_PROVISIONED_MESSAGE));
        if (user.getState() == User.UserState.BANNED) {
            return Optional.empty();
        }
        return Optional.of(new AuthIdentity(user.getId(), claims.email()));
    }

    /**
     * Finds or creates the local profile for the Clerk user behind {@code token}. A new profile is {@code INCOMPLETE}:
     * it has no email and no birth date until the profile update supplies them.
     */
    public UseCaseResult<ProvisionedSession> provisionSession(String token) {
        Optional<VerifiedToken> verified = tokenVerifier.verify(token);
        if (verified.isEmpty()) {
            return UseCaseResult.failure(UseCaseError.unauthorized(INVALID_TOKEN_MESSAGE));
        }
        VerifiedToken claims = verified.get();
        ReentrantLock lock = provisionLocks[Math.floorMod(claims.subject().hashCode(), LOCK_STRIPES)];
        lock.lock();
        try {
            Optional<UUID> existing = authStorage.findUserIdByClerkId(claims.subject());
            if (existing.isPresent()) {
                Optional<User> current = userStorage.get(existing.get());
                if (current.isPresent() && current.get().getDeletedAt() == null) {
                    return sessionFor(current.get(), claims, false);
                }
                // The linked profile was deleted: drop the stale link so this sign-in starts a fresh profile.
                authStorage.deleteIdentityForUser(existing.get());
            }
            Instant now = AppClock.now();
            User user = User.StorageBuilder.create(UUID.randomUUID(), User.SIGNUP_PLACEHOLDER_NAME, now)
                    .updatedAt(now)
                    .build();
            userStorage.save(user);
            if (authStorage.linkClerkId(claims.subject(), user.getId(), now)) {
                return UseCaseResult.success(new ProvisionedSession(AuthUser.from(user, claims.email()), true));
            }
            // Another server process linked this Clerk id first. Use its profile; our row stays an unused INCOMPLETE
            // user.
            UUID winner = authStorage
                    .findUserIdByClerkId(claims.subject())
                    .orElseThrow(() -> new IllegalStateException("Clerk identity link failed but no link exists"));
            return userStorage
                    .get(winner)
                    .filter(candidate -> candidate.getDeletedAt() == null)
                    .map(candidate -> sessionFor(candidate, claims, false))
                    .orElseGet(() -> UseCaseResult.failure(UseCaseError.unauthorized(INVALID_TOKEN_MESSAGE)));
        } finally {
            lock.unlock();
        }
    }

    private static UseCaseResult<ProvisionedSession> sessionFor(User user, VerifiedToken claims, boolean created) {
        if (user.getState() == User.UserState.BANNED) {
            return UseCaseResult.failure(UseCaseError.unauthorized(INVALID_TOKEN_MESSAGE));
        }
        return UseCaseResult.success(new ProvisionedSession(AuthUser.from(user, claims.email()), created));
    }

    /** {@code email} is the optional email claim from the Clerk token, not a stored value. */
    public record AuthIdentity(UUID userId, String email) {}

    public record ProvisionedSession(AuthUser user, boolean created) {}

    public record AuthUser(UUID id, String email, String displayName, String profileCompletionState) {
        static AuthUser from(User user, String email) {
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
            return new AuthUser(user.getId(), email, displayName, profileCompletionState);
        }
    }

    /** The token is valid but the Clerk user has no local profile; the client should call the session route. */
    public static final class NotProvisionedException extends RuntimeException {
        public NotProvisionedException(String message) {
            super(message);
        }
    }
}
