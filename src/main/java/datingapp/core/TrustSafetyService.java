package datingapp.core;

import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Report;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/** Consolidated safety and verification workflows. */
public class TrustSafetyService {

    public static final Duration DEFAULT_VERIFICATION_TTL = Duration.ofMinutes(15);

    private final ReportStorage reportStorage;
    private final UserStorage userStorage;
    private final BlockStorage blockStorage;
    private final AppConfig config;
    private final Duration verificationTtl;
    private final Random random;

    public TrustSafetyService() {
        this(DEFAULT_VERIFICATION_TTL, new Random());
    }

    public TrustSafetyService(Duration verificationTtl, Random random) {
        this(null, null, null, null, verificationTtl, random);
    }

    public TrustSafetyService(
            ReportStorage reportStorage, UserStorage userStorage, BlockStorage blockStorage, AppConfig config) {
        this(reportStorage, userStorage, blockStorage, config, DEFAULT_VERIFICATION_TTL, new Random());
    }

    public TrustSafetyService(
            ReportStorage reportStorage,
            UserStorage userStorage,
            BlockStorage blockStorage,
            AppConfig config,
            Duration verificationTtl,
            Random random) {
        this.reportStorage = reportStorage;
        this.userStorage = userStorage;
        this.blockStorage = blockStorage;
        this.config = config;
        this.verificationTtl = Objects.requireNonNull(verificationTtl, "verificationTtl cannot be null");
        this.random = Objects.requireNonNull(random, "random cannot be null");
    }

    /** Generates a six-digit verification code. */
    public String generateVerificationCode() {
        int value = random.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    /** Returns true if the verification code has expired. */
    public boolean isExpired(Instant sentAt) {
        if (sentAt == null) {
            return true;
        }
        return sentAt.plus(verificationTtl).isBefore(Instant.now());
    }

    /**
     * Validates a user-provided verification code against stored user data.
     *
     * @param user the user to verify
     * @param inputCode the user-provided code
     * @return true if the code matches and is not expired
     */
    public boolean verifyCode(User user, String inputCode) {
        Objects.requireNonNull(user, "user cannot be null");
        if (inputCode == null || inputCode.isBlank()) {
            return false;
        }

        String expected = user.getVerificationCode();
        if (expected == null) {
            return false;
        }

        if (isExpired(user.getVerificationSentAt())) {
            return false;
        }

        return expected.equals(inputCode.trim());
    }

    /** Report a user for inappropriate behavior and return moderation action. */
    public ReportResult report(UUID reporterId, UUID reportedUserId, Report.Reason reason, String description) {
        ensureReportDependencies();

        User reporter = userStorage.get(reporterId);
        if (reporter == null || reporter.getState() != User.State.ACTIVE) {
            return new ReportResult(false, false, "Reporter must be active user");
        }

        User reported = userStorage.get(reportedUserId);
        if (reported == null) {
            return new ReportResult(false, false, "Reported user not found");
        }

        if (reportStorage.hasReported(reporterId, reportedUserId)) {
            return new ReportResult(false, false, "Already reported this user");
        }

        Report report = Report.create(reporterId, reportedUserId, reason, description);
        reportStorage.save(report);

        if (!blockStorage.isBlocked(reporterId, reportedUserId)) {
            Block block = Block.create(reporterId, reportedUserId);
            blockStorage.save(block);
        }

        int reportCount = reportStorage.countReportsAgainst(reportedUserId);
        boolean autoBanned = false;
        if (reportCount >= config.autoBanThreshold() && reported.getState() != User.State.BANNED) {
            reported.ban();
            userStorage.save(reported);
            autoBanned = true;
        }

        return new ReportResult(true, autoBanned, null);
    }

    private void ensureReportDependencies() {
        if (reportStorage == null || userStorage == null || blockStorage == null || config == null) {
            throw new IllegalStateException("Report dependencies are not configured");
        }
    }

    /** Result of a report action, including moderation outcome. */
    public record ReportResult(boolean success, boolean userWasBanned, String errorMessage) {}
}
