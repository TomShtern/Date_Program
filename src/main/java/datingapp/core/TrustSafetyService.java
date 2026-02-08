package datingapp.core;

import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.Report;
import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.ReportStorage;
import datingapp.core.storage.UserStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consolidated safety and verification workflows. */
public class TrustSafetyService {

    private static final Logger logger = LoggerFactory.getLogger(TrustSafetyService.class);
    public static final Duration DEFAULT_VERIFICATION_TTL = Duration.ofMinutes(15);

    private final ReportStorage reportStorage;
    private final UserStorage userStorage;
    private final BlockStorage blockStorage;
    private final MatchStorage matchStorage;
    private final AppConfig config;
    private final Duration verificationTtl;
    private final Random random;

    /** Package-private: testing only. Core dependencies are null. */
    TrustSafetyService() {
        this(DEFAULT_VERIFICATION_TTL, new Random());
    }

    /** Package-private: testing only. Core dependencies are null. */
    TrustSafetyService(Duration verificationTtl, Random random) {
        this(null, null, null, null, null, verificationTtl, random);
    }

    public TrustSafetyService(
            ReportStorage reportStorage,
            UserStorage userStorage,
            BlockStorage blockStorage,
            MatchStorage matchStorage,
            AppConfig config) {
        this(
                Objects.requireNonNull(reportStorage, "reportStorage cannot be null"),
                Objects.requireNonNull(userStorage, "userStorage cannot be null"),
                Objects.requireNonNull(blockStorage, "blockStorage cannot be null"),
                matchStorage, // Optional - may be null for backward compatibility
                Objects.requireNonNull(config, "config cannot be null"),
                DEFAULT_VERIFICATION_TTL,
                new Random());
    }

    public TrustSafetyService(
            ReportStorage reportStorage,
            UserStorage userStorage,
            BlockStorage blockStorage,
            MatchStorage matchStorage,
            AppConfig config,
            Duration verificationTtl,
            Random random) {
        this.reportStorage = reportStorage;
        this.userStorage = userStorage;
        this.blockStorage = blockStorage;
        this.matchStorage = matchStorage;
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
        return sentAt == null || sentAt.plus(verificationTtl).isBefore(AppClock.now());
    }

    /**
     * Validates a user-provided verification code against stored user data.
     *
     * @param user      the user to verify
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

        return !isExpired(user.getVerificationSentAt()) && expected.equals(inputCode.trim());
    }

    /** Report a user for inappropriate behavior and return moderation action. */
    public ReportResult report(UUID reporterId, UUID reportedUserId, Report.Reason reason, String description) {
        ensureReportDependencies();

        User reporter = userStorage.get(reporterId);
        if (reporter == null || reporter.getState() != UserState.ACTIVE) {
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
            updateMatchStateForBlock(reporterId, reportedUserId);
        }

        int reportCount = reportStorage.countReportsAgainst(reportedUserId);
        boolean autoBanned = false;
        if (reportCount >= config.autoBanThreshold() && reported.getState() != UserState.BANNED) {
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

    /**
     * Updates the match state to BLOCKED if a match exists between the two users.
     * Silently succeeds if matchStorage is not configured or no match exists.
     */
    private void updateMatchStateForBlock(UUID blockerId, UUID blockedId) {
        if (matchStorage == null) {
            logger.debug("MatchStorage not configured; skipping match state update for block");
            return;
        }
        matchStorage.getByUsers(blockerId, blockedId).ifPresent(match -> {
            if (match.getState() != Match.State.BLOCKED) {
                match.block(blockerId);
                matchStorage.update(match);
                if (logger.isInfoEnabled()) {
                    logger.info("Match {} transitioned to BLOCKED by user {}", match.getId(), blockerId);
                }
            }
        });
    }

    /**
     * Blocks a user without filing a report. Updates both the block storage and
     * any existing match state.
     *
     * @param blockerId the user initiating the block
     * @param blockedId the user being blocked
     * @return result of the block operation
     */
    public BlockResult block(UUID blockerId, UUID blockedId) {
        Objects.requireNonNull(blockerId, "blockerId cannot be null");
        Objects.requireNonNull(blockedId, "blockedId cannot be null");

        if (blockStorage == null || userStorage == null) {
            throw new IllegalStateException("Block dependencies are not configured");
        }

        if (blockerId.equals(blockedId)) {
            return new BlockResult(false, "Cannot block yourself");
        }

        User blocker = userStorage.get(blockerId);
        if (blocker == null || blocker.getState() != UserState.ACTIVE) {
            return new BlockResult(false, "Blocker must be an active user");
        }

        User blocked = userStorage.get(blockedId);
        if (blocked == null) {
            return new BlockResult(false, "User to block not found");
        }

        if (blockStorage.isBlocked(blockerId, blockedId)) {
            return new BlockResult(false, "User is already blocked");
        }

        Block block = Block.create(blockerId, blockedId);
        blockStorage.save(block);
        updateMatchStateForBlock(blockerId, blockedId);

        logger.info("User {} blocked user {}", blockerId, blockedId);
        return new BlockResult(true, null);
    }

    /** Result of a block action. */
    public record BlockResult(boolean success, String errorMessage) {
        public BlockResult {
            if (success && errorMessage != null) {
                throw new IllegalArgumentException("errorMessage must be null on success");
            }
            if (!success && (errorMessage == null || errorMessage.isBlank())) {
                throw new IllegalArgumentException("errorMessage is required on failure");
            }
        }
    }

    /**
     * Unblocks a previously blocked user.
     *
     * @param blockerId the user who created the block
     * @param blockedId the user who was blocked
     * @return true if the block was removed, false if no block existed
     */
    public boolean unblock(UUID blockerId, UUID blockedId) {
        Objects.requireNonNull(blockerId, "blockerId cannot be null");
        Objects.requireNonNull(blockedId, "blockedId cannot be null");

        if (blockStorage == null) {
            throw new IllegalStateException("BlockStorage is not configured");
        }

        boolean deleted = blockStorage.delete(blockerId, blockedId);
        if (deleted) {
            logger.info("User {} unblocked user {}", blockerId, blockedId);
        } else {
            logger.debug("No block found between {} and {}", blockerId, blockedId);
        }
        return deleted;
    }

    /**
     * Retrieves all users that the given user has blocked.
     *
     * @param userId the user whose blocked users to retrieve
     * @return list of blocked users
     */
    public List<User> getBlockedUsers(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");

        if (blockStorage == null || userStorage == null) {
            throw new IllegalStateException("BlockStorage or UserStorage is not configured");
        }

        return blockStorage.findByBlocker(userId).stream()
                .map(block -> userStorage.get(block.blockedId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /** Result of a report action, including moderation outcome. */
    public record ReportResult(boolean success, boolean userWasBanned, String errorMessage) {

        public ReportResult {
            if (success && errorMessage != null) {
                throw new IllegalArgumentException("errorMessage must be null on success");
            }
            if (!success && (errorMessage == null || errorMessage.isBlank())) {
                throw new IllegalArgumentException("errorMessage is required on failure");
            }
            if (!success && userWasBanned) {
                throw new IllegalArgumentException("userWasBanned cannot be true on failure");
            }
        }
    }
}
