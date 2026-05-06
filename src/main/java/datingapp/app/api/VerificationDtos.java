package datingapp.app.api;

import datingapp.app.usecase.profile.VerificationUseCases;
import datingapp.core.model.User;
import java.time.Instant;
import java.util.UUID;

final class VerificationDtos {
    private VerificationDtos() {}

    /** Request body for starting verification. */
    static record StartVerificationRequest(User.VerificationMethod method, String contact) {}

    /** Response body for starting verification. */
    static record StartVerificationResponse(UUID userId, String method, String contact, String devVerificationCode) {
        static StartVerificationResponse from(VerificationUseCases.StartVerificationResult result) {
            User user = result.user();
            User.VerificationMethod method = user.getVerificationMethod();
            String contact =
                    method == null ? null : method == User.VerificationMethod.PHONE ? user.getPhone() : user.getEmail();
            return new StartVerificationResponse(
                    user.getId(), method != null ? method.name() : null, contact, result.generatedCode());
        }
    }

    /** Request body for confirming verification. */
    static record ConfirmVerificationRequest(String verificationCode) {}

    /** Response body for confirming verification. */
    static record ConfirmVerificationResponse(boolean verified, Instant verifiedAt) {
        static ConfirmVerificationResponse from(VerificationUseCases.ConfirmVerificationResult result) {
            return new ConfirmVerificationResponse(
                    result.user().isVerified(), result.user().getVerifiedAt());
        }
    }
}
