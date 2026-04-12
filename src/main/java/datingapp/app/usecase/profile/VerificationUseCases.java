package datingapp.app.usecase.profile;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.storage.UserStorage;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Application-layer verification flows shared by adapters. */
public class VerificationUseCases {

    public static final Duration DEFAULT_VERIFICATION_TTL = Duration.ofMinutes(15);

    private final UserStorage userStorage;
    private final SecureRandom random;
    private final Duration verificationTtl;

    /** Compatibility constructor for existing wiring; verification behavior is owned here. */
    public VerificationUseCases(UserStorage userStorage, TrustSafetyService trustSafetyService) {
        this(userStorage, new SecureRandom(), validatedDefaultVerificationTtl(trustSafetyService));
    }

    public VerificationUseCases(UserStorage userStorage, SecureRandom random, Duration verificationTtl) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.verificationTtl = Objects.requireNonNull(verificationTtl, "verificationTtl cannot be null");
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

        try {
            if (command.method() == VerificationMethod.EMAIL) {
                user.setEmail(contact);
            } else {
                user.setPhone(contact);
            }
        } catch (IllegalArgumentException e) {
            return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
        }

        String generatedCode = generateVerificationCode();
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

        if (!verifyCode(user, verificationCode)) {
            String message =
                    isExpired(user.getVerificationSentAt()) ? "Code expired. Please try again." : "Incorrect code.";
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

    private static Duration validatedDefaultVerificationTtl(TrustSafetyService trustSafetyService) {
        Objects.requireNonNull(trustSafetyService, "trustSafetyService cannot be null");
        return DEFAULT_VERIFICATION_TTL;
    }

    private static String requiredContactMessage(VerificationMethod method) {
        return method == VerificationMethod.EMAIL ? "Email required." : "Phone required.";
    }

    private String generateVerificationCode() {
        int value = random.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private boolean isExpired(Instant sentAt) {
        return sentAt == null || sentAt.plus(verificationTtl).isBefore(AppClock.now());
    }

    private boolean verifyCode(User user, String inputCode) {
        Objects.requireNonNull(user, "user cannot be null");
        if (inputCode == null || inputCode.isBlank()) {
            return false;
        }

        String expected = user.getVerificationCode();
        if (expected == null) {
            return false;
        }

        return !isExpired(user.getVerificationSentAt()) && expected.equals(inputCode.trim());
    }
}
