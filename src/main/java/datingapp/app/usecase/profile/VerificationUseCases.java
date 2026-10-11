package datingapp.app.usecase.profile;

import datingapp.app.usecase.common.UseCaseError;
import datingapp.app.usecase.common.UseCaseResult;
import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.matching.TrustSafetyService;
import datingapp.core.model.User;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.storage.UserStorage;
import datingapp.core.verification.LocalLogVerificationSender;
import datingapp.core.verification.VerificationSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/** Application-layer verification flows shared by adapters. */
public class VerificationUseCases {

    public static final Duration DEFAULT_VERIFICATION_TTL = Duration.ofMinutes(15);
    public static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final UserStorage userStorage;
    private final SecureRandom random;
    private final Duration verificationTtl;
    private final VerificationSender sender;
    private final Duration resendCooldown;
    private final int maxAttempts;
    private final Duration lockDuration;

    /** Compatibility constructor for existing wiring; verification behavior is owned here. */
    public VerificationUseCases(UserStorage userStorage, TrustSafetyService trustSafetyService) {
        this(
                userStorage,
                new SecureRandom(),
                validatedDefaultVerificationTtl(trustSafetyService),
                new LocalLogVerificationSender(),
                RESEND_COOLDOWN,
                MAX_ATTEMPTS,
                LOCK_DURATION);
    }

    /** Config-wired constructor: cooldown/attempts/lockout/TTL come from {@code AppConfig}. */
    public VerificationUseCases(UserStorage userStorage, AppConfig config) {
        this(userStorage, config, new LocalLogVerificationSender());
    }

    /** Config-wired constructor with an injected delivery channel (the provider seam). */
    public VerificationUseCases(UserStorage userStorage, AppConfig config, VerificationSender sender) {
        this(
                userStorage,
                new SecureRandom(),
                Duration.ofMinutes(Math.max(1, config.safety().verification().codeTtlMinutes())),
                sender,
                Duration.ofSeconds(Math.max(0, config.safety().verification().resendCooldownSeconds())),
                config.safety().verification().maxAttempts(),
                Duration.ofMinutes(Math.max(0, config.safety().verification().lockMinutes())));
    }

    public VerificationUseCases(UserStorage userStorage, SecureRandom random, Duration verificationTtl) {
        this(
                userStorage,
                random,
                verificationTtl,
                new LocalLogVerificationSender(),
                RESEND_COOLDOWN,
                MAX_ATTEMPTS,
                LOCK_DURATION);
    }

    public VerificationUseCases(
            UserStorage userStorage,
            SecureRandom random,
            Duration verificationTtl,
            VerificationSender sender,
            Duration resendCooldown,
            int maxAttempts,
            Duration lockDuration) {
        this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
        this.verificationTtl = Objects.requireNonNull(verificationTtl, "verificationTtl cannot be null");
        this.sender = Objects.requireNonNull(sender, "sender cannot be null");
        this.resendCooldown = Objects.requireNonNull(resendCooldown, "resendCooldown cannot be null");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.lockDuration = Objects.requireNonNull(lockDuration, "lockDuration cannot be null");
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

        Instant now = AppClock.now();
        if (user.getVerificationLockedUntil() != null
                && user.getVerificationLockedUntil().isAfter(now)) {
            return UseCaseResult.failure(UseCaseError.rateLimited("Too many attempts. Please try again later."));
        }
        Instant lastSent = user.getVerificationLastSentAt() != null
                ? user.getVerificationLastSentAt()
                : user.getVerificationSentAt();
        if (lastSent != null && lastSent.plus(resendCooldown).isAfter(now) && user.getVerificationCode() != null) {
            return UseCaseResult.failure(
                    UseCaseError.validation("Verification code already sent. Please wait before retrying."));
        }

        try {
            if (command.method() == VerificationMethod.EMAIL) {
                user.setEmail(contact);
            } else {
                user.setPhone(contact);
            }
        } catch (IllegalArgumentException e) {
            return UseCaseResult.failure(UseCaseError.validation(stableContactMessage(command.method())));
        }

        String generatedCode = generateVerificationCode();
        user.startVerification(command.method(), hashCode(generatedCode));
        try {
            userStorage.save(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internalFailure(command.context(), "start verification", e));
        }
        sender.send(command.method(), contact, generatedCode);
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

        Instant now = AppClock.now();
        if (user.getVerificationLockedUntil() != null
                && user.getVerificationLockedUntil().isAfter(now)) {
            return UseCaseResult.failure(UseCaseError.rateLimited("Too many attempts. Please try again later."));
        }

        if (isExpired(user.getVerificationSentAt())) {
            user.clearVerificationAttempt();
            try {
                userStorage.save(user);
            } catch (Exception e) {
                return UseCaseResult.failure(
                        UseCaseError.internalFailure(command.context(), "confirm verification", e));
            }
            return UseCaseResult.failure(UseCaseError.validation("Code expired. Please try again."));
        }

        if (!verifyCode(user, verificationCode)) {
            user.recordVerificationAttempt();
            if (user.getVerificationAttemptCount() >= maxAttempts) {
                user.lockVerificationUntil(now.plus(lockDuration));
            }
            try {
                userStorage.save(user);
            } catch (Exception e) {
                return UseCaseResult.failure(
                        UseCaseError.internalFailure(command.context(), "confirm verification", e));
            }
            if (user.getVerificationLockedUntil() != null
                    && user.getVerificationLockedUntil().isAfter(now)) {
                return UseCaseResult.failure(UseCaseError.rateLimited("Too many attempts. Please try again later."));
            }
            return UseCaseResult.failure(UseCaseError.validation("Incorrect code."));
        }

        user.markVerified();
        try {
            userStorage.save(user);
        } catch (Exception e) {
            return UseCaseResult.failure(UseCaseError.internalFailure(command.context(), "confirm verification", e));
        }
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

    /**
     * Maps contact-normalization internals to a stable user-facing message. Raw
     * normalization details stay server-side; the API surface only exposes which contact
     * field was invalid.
     */
    private static String stableContactMessage(VerificationMethod method) {
        return method == VerificationMethod.EMAIL ? "Invalid email format" : "Invalid phone number";
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

        return !isExpired(user.getVerificationSentAt())
                && MessageDigest.isEqual(
                        expected.getBytes(StandardCharsets.UTF_8),
                        hashCode(inputCode.trim()).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * SHA-256 hex of the code. Only the hash is stored, so the database never holds a usable code. With six digits
     * this stops casual reads, not an offline search of a leaked database.
     */
    static String hashCode(String code) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
