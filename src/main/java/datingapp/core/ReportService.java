package datingapp.core;

import java.util.Objects;

/**
 * Service for handling user reports with auto-ban logic.
 */
public class ReportService {

    private final ReportStorage reportStorage;
    private final UserStorage userStorage;
    private final BlockStorage blockStorage;
    private final AppConfig config;

    public ReportService(ReportStorage reportStorage,
            UserStorage userStorage,
            BlockStorage blockStorage,
            AppConfig config) {
        this.reportStorage = Objects.requireNonNull(reportStorage);
        this.userStorage = Objects.requireNonNull(userStorage);
        this.blockStorage = Objects.requireNonNull(blockStorage);
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Backwards-compatible constructor using default config.
     */
    public ReportService(ReportStorage reportStorage,
            UserStorage userStorage,
            BlockStorage blockStorage) {
        this(reportStorage, userStorage, blockStorage, AppConfig.defaults());
    }

    /**
     * File a report against a user.
     * Returns a ReportResult indicating success/failure and if user was
     * auto-banned.
     */
    public ReportResult report(java.util.UUID reporterId, java.util.UUID reportedUserId,
            Report.Reason reason, String description) {

        // Validate reporter exists and is active
        User reporter = userStorage.get(reporterId);
        if (reporter == null || reporter.getState() != User.State.ACTIVE) {
            return new ReportResult(false, false, "Reporter must be active user");
        }

        // Validate reported user exists
        User reportedUser = userStorage.get(reportedUserId);
        if (reportedUser == null) {
            return new ReportResult(false, false, "Reported user not found");
        }

        // Check for duplicate report
        if (reportStorage.hasReported(reporterId, reportedUserId)) {
            return new ReportResult(false, false, "Already reported this user");
        }

        // Save report
        Report report = Report.create(reporterId, reportedUserId, reason, description);
        reportStorage.save(report);

        // Auto-block: reporter automatically blocks reported user
        if (!blockStorage.isBlocked(reporterId, reportedUserId)) {
            Block block = Block.create(reporterId, reportedUserId);
            blockStorage.save(block);
        }

        // Check for auto-ban threshold (now configurable)
        int reportCount = reportStorage.countReportsAgainst(reportedUserId);
        boolean autoBanned = false;

        if (reportCount >= config.autoBanThreshold() &&
                reportedUser.getState() != User.State.BANNED) {
            reportedUser.ban();
            userStorage.save(reportedUser);
            autoBanned = true;
        }

        return new ReportResult(true, autoBanned, null);
    }

    /**
     * Result of a report operation.
     */
    public record ReportResult(
            boolean success,
            boolean userWasBanned,
            String errorMessage) {
    }
}
