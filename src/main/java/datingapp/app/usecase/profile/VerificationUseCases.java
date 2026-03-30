package datingapp.app.usecase.profile;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.storage.UserStorage;
import java.util.Objects;

/** Application-layer verification flows shared by adapters. */
public class VerificationUseCases {

    private final UserStorage userStorage;
    private final TrustSafetyService trustSafetyService;

    public VerificationUseCases(UserStorage userStorage, TrustSafetyService trustSafetyService) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.trustSafetyService = Objects.requireNonNull(trustSafetyService, "trustSafetyService cannot be null");
    }

    public UseCaseResult<StartVerificationResult> startVerification(StartVerificationCommand command) {
        if (command == null || command.context() == null || command.method() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context and verification method are required"));
        }
        String contact = command.contact() == null ? "" : command.contact().trim();
        if (contact.isBlank()) {
            return UseCaseResult.failure(UseCaseError.validation(requiredContactMessage(command.method())));
        }

        User user = userStorage.get(command.context().userId()).orElse(null);
        if (user == null) {
            return UseCaseResult.failure(UseCaseError.notFound("User not found"));
        }

        if (command.method() == VerificationMethod.EMAIL) {
            user.setEmail(contact);
        } else {
            user.setPhone(contact);
        }

        String generatedCode = trustSafetyService.generateVerificationCode();
        user.startVerification(command.method(), generatedCode);
        userStorage.save(user);
        return UseCaseResult.success(new StartVerificationResult(user, generatedCode));
    }

    public UseCaseResult<ConfirmVerificationResult> confirmVerification(ConfirmVerificationCommand command) {
        if (command == null || command.context() == null) {
            return UseCaseResult.failure(UseCaseError.validation("Context is required"));
        }
        String verificationCode = command.verificationCode() == null
                ? ""
                : command.verificationCode().trim();
        if (verificationCode.isBlank()) {
            return UseCaseResult.failure(UseCaseError.validation("Verification code is required"));
        }

        User user = userStorage.get(command.context().userId()).orElse(null);
        if (user == null) {
            return UseCaseResult.failure(UseCaseError.notFound("User not found"));
        }

        if (!trustSafetyService.verifyCode(user, verificationCode)) {
            String message = trustSafetyService.isExpired(user.getVerificationSentAt())
                    ? "Code expired. Please try again."
                    : "Incorrect code.";
            return UseCaseResult.failure(UseCaseError.validation(message));
        }

        user.markVerified();
        userStorage.save(user);
        return UseCaseResult.success(new ConfirmVerificationResult(user));
    }

    public record StartVerificationCommand(UserContext context, VerificationMethod method, String contact) {}

    public record StartVerificationResult(User user, String generatedCode) {}

    public record ConfirmVerificationCommand(UserContext context, String verificationCode) {}

    public record ConfirmVerificationResult(User user) {}

    private static String requiredContactMessage(VerificationMethod method) {
        return method == VerificationMethod.EMAIL ? "Email required." : "Phone required.";
    }
}
