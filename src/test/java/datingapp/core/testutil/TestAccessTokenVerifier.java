package datingapp.core.testutil;

import datingapp.app.usecase.auth.AccessTokenVerifier;
import java.util.Optional;
import java.util.UUID;

/**
 * Test double for the Clerk token verifier. A token is {@code test-<clerkUserId>} and verifies to that Clerk id;
 * anything else is rejected. Real signature and claim checks are covered by {@code ClerkJwtVerifierTest}.
 */
public final class TestAccessTokenVerifier implements AccessTokenVerifier {

    private static final String TOKEN_PREFIX = "test-";
    private static final String IMPLICIT_CLERK_ID_PREFIX = "user_test_";

    @Override
    public Optional<VerifiedToken> verify(String token) {
        if (token == null || !token.startsWith(TOKEN_PREFIX) || token.length() == TOKEN_PREFIX.length()) {
            return Optional.empty();
        }
        return Optional.of(new VerifiedToken(token.substring(TOKEN_PREFIX.length()), null));
    }

    /** Raw token (no {@code Bearer } prefix) for an arbitrary Clerk user id. */
    public static String tokenFor(String clerkUserId) {
        return TOKEN_PREFIX + clerkUserId;
    }

    /** Raw token for a pre-seeded local user; {@link TestStorages.Auth} resolves its Clerk id implicitly. */
    public static String tokenFor(UUID userId) {
        return tokenFor(IMPLICIT_CLERK_ID_PREFIX + userId);
    }

    static Optional<UUID> implicitUserId(String clerkUserId) {
        if (clerkUserId == null || !clerkUserId.startsWith(IMPLICIT_CLERK_ID_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(clerkUserId.substring(IMPLICIT_CLERK_ID_PREFIX.length())));
        } catch (IllegalArgumentException _) {
            return Optional.empty();
        }
    }
}
