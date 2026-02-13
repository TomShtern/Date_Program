package datingapp.core.storage;

import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Report;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Unified storage interface for trust &amp; safety concerns: blocking and reporting.
 * Consolidates former {@code BlockStorage} and {@code ReportStorage}.
 */
public interface TrustSafetyStorage {

    // ─────────────────────────────────────────────
    // Block operations
    // ─────────────────────────────────────────────

    /** Saves a block. */
    void save(Block block);

    /** Returns true if EITHER user has blocked the other. Block is bidirectional in effect. */
    boolean isBlocked(UUID userA, UUID userB);

    /** Returns all user IDs that the given user should not see. */
    Set<UUID> getBlockedUserIds(UUID userId);

    /** Returns all blocks created by the given user. */
    List<Block> findByBlocker(UUID blockerId);

    /**
     * Deletes a block between two users.
     *
     * @param blockerId the user who created the block
     * @param blockedId the user who was blocked
     * @return true if a block was deleted, false if no block existed
     */
    boolean deleteBlock(UUID blockerId, UUID blockedId);

    /** Count blocks GIVEN by a user (users they blocked). */
    int countBlocksGiven(UUID userId);

    /** Count blocks RECEIVED by a user (users who blocked them). */
    int countBlocksReceived(UUID userId);

    // ─────────────────────────────────────────────
    // Report operations
    // ─────────────────────────────────────────────

    /** Saves a report. */
    void save(Report report);

    /** Count reports against a user. */
    int countReportsAgainst(UUID userId);

    /** Check if reporter has already reported this user. */
    boolean hasReported(UUID reporterId, UUID reportedUserId);

    /** Get all reports against a user (for admin review later). */
    List<Report> getReportsAgainst(UUID userId);

    /** Count reports MADE BY a user (reports they filed). */
    int countReportsBy(UUID userId);
}
