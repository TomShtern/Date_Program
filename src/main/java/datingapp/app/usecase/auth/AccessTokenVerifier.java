package datingapp.app.usecase.auth;

import java.util.Objects;
import java.util.Optional;

/** Verifies a bearer token issued by the external identity provider (Clerk). */
@FunctionalInterface
public interface AccessTokenVerifier {

    /**
     * @param token the raw token, without the {@code Bearer } prefix
     * @return the verified claims, or empty when the token is invalid for any reason
     */
    Optional<VerifiedToken> verify(String token);

    /** Claims the backend relies on. {@code subject} is the Clerk user id; {@code email} may be null. */
    record VerifiedToken(String subject, String email) {
        public VerifiedToken {
            Objects.requireNonNull(subject, "subject cannot be null");
        }
    }

    /** Used when no Clerk issuer is configured (CLI, desktop, plain defaults): every token is rejected. */
    static AccessTokenVerifier rejectAll() {
        return token -> Optional.empty();
    }
}
