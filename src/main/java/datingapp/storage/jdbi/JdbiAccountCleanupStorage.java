package datingapp.storage.jdbi;

import datingapp.core.model.User;
import datingapp.core.storage.AccountCleanupStorage;
import datingapp.storage.DatabaseManager.StorageException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;

/** JDBI-backed account cleanup storage that soft-deletes the user and all related graph rows in one transaction. */
public final class JdbiAccountCleanupStorage implements AccountCleanupStorage {

    private static final String USER_ID_BIND = "userId";
    private static final String DELETED_AT_BIND = "deletedAt";
    private static final String STATE_BIND = "state";

    private final Jdbi jdbi;

    public JdbiAccountCleanupStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
    }

    @Override
    public void softDeleteAccount(User user, Instant deletedAt) {
        Objects.requireNonNull(user, "user cannot be null");
        Objects.requireNonNull(deletedAt, "deletedAt cannot be null");

        try {
            jdbi.useTransaction(handle -> {
                // Core user table (must be last due to FK dependencies)
                // First delete dependent tables in proper order

                // ── Profile-related ──
                softDeleteProfileNotes(handle, user.getId(), deletedAt);
                deleteProfileViews(handle, user.getId());
                deleteUserPhotos(handle, user.getId());
                deleteUserInterests(handle, user.getId());
                deleteUserInterestedIn(handle, user.getId());
                deleteUserLifestylePreferences(handle, user.getId());

                // ── Stats & achievements ──
                deleteUserStats(handle, user.getId());
                deleteUserAchievements(handle, user.getId());
                deleteDailyPickViews(handle, user.getId());
                deleteDailyPicks(handle, user.getId());
                deleteSwipeSessions(handle, user.getId());

                // ── Social graph ──
                softDeleteLikes(handle, user.getId(), deletedAt);
                softDeleteMatches(handle, user.getId(), deletedAt);
                softDeleteConversations(handle, user.getId(), deletedAt);
                softDeleteMessages(handle, user.getId(), deletedAt);
                deleteStandouts(handle, user.getId());
                deleteFriendRequests(handle, user.getId());
                deleteNotifications(handle, user.getId());

                // ── Moderation ──
                softDeleteBlocks(handle, user.getId(), deletedAt);
                softDeleteReports(handle, user.getId(), deletedAt);

                // ── State ──
                deleteUndoStates(handle, user.getId());

                // ── Finally mark user as deleted ──
                softDeleteUser(handle, user.getId(), deletedAt);
            });
        } catch (Exception e) {
            throw new StorageException("Failed to soft-delete account graph for user " + user.getId(), e);
        }
    }

    private static void softDeleteUser(Handle handle, UUID userId, Instant deletedAt) {
        try (var update = handle.createUpdate("""
            UPDATE users
            SET state = :state,
                updated_at = :deletedAt,
                deleted_at = :deletedAt
            WHERE id = :userId AND deleted_at IS NULL
            """)) {
            update.bind(USER_ID_BIND, userId)
                    .bind(STATE_BIND, User.UserState.BANNED.name())
                    .bind(DELETED_AT_BIND, deletedAt)
                    .execute();
        }
    }

    private static void softDeleteLikes(Handle handle, UUID userId, Instant deletedAt) {
        executeSoftDelete(handle, """
            UPDATE likes
            SET deleted_at = :deletedAt
            WHERE deleted_at IS NULL
              AND (who_likes = :userId OR who_got_liked = :userId)
            """, userId, deletedAt);
    }

    private static void softDeleteMatches(Handle handle, UUID userId, Instant deletedAt) {
        executeSoftDelete(handle, """
            UPDATE matches
            SET updated_at = :deletedAt,
                deleted_at = :deletedAt
            WHERE deleted_at IS NULL
              AND (user_a = :userId OR user_b = :userId)
            """, userId, deletedAt);
    }

    private static void softDeleteConversations(Handle handle, UUID userId, Instant deletedAt) {
        executeSoftDelete(handle, """
            UPDATE conversations
            SET deleted_at = :deletedAt
            WHERE deleted_at IS NULL
              AND (user_a = :userId OR user_b = :userId)
            """, userId, deletedAt);
    }

    private static void softDeleteMessages(Handle handle, UUID userId, Instant deletedAt) {
        executeSoftDelete(handle, """
            UPDATE messages
            SET deleted_at = :deletedAt
            WHERE deleted_at IS NULL
              AND (
                  sender_id = :userId
                  OR conversation_id IN (
                  SELECT id FROM conversations WHERE user_a = :userId OR user_b = :userId
                  )
              )
            """, userId, deletedAt);
    }

    private static void softDeleteProfileNotes(Handle handle, UUID userId, Instant deletedAt) {
        executeSoftDelete(handle, """
            UPDATE profile_notes
            SET updated_at = :deletedAt,
                deleted_at = :deletedAt
            WHERE deleted_at IS NULL
              AND (author_id = :userId OR subject_id = :userId)
            """, userId, deletedAt);
    }

    private static void deleteProfileViews(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM profile_views WHERE viewer_id = :userId OR viewed_id = :userId", userId);
    }

    private static void deleteUserPhotos(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM user_photos WHERE user_id = :userId", userId);
    }

    private static void deleteUserInterests(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM user_interests WHERE user_id = :userId", userId);
    }

    private static void deleteUserInterestedIn(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM user_interested_in WHERE user_id = :userId", userId);
    }

    private static void deleteUserLifestylePreferences(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM user_db_smoking WHERE user_id = :userId", userId);
        executeHardDelete(handle, "DELETE FROM user_db_drinking WHERE user_id = :userId", userId);
        executeHardDelete(handle, "DELETE FROM user_db_wants_kids WHERE user_id = :userId", userId);
        executeHardDelete(handle, "DELETE FROM user_db_looking_for WHERE user_id = :userId", userId);
        executeHardDelete(handle, "DELETE FROM user_db_education WHERE user_id = :userId", userId);
    }

    private static void deleteUserStats(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM user_stats WHERE user_id = :userId", userId);
    }

    private static void deleteUserAchievements(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM user_achievements WHERE user_id = :userId", userId);
    }

    private static void deleteDailyPickViews(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM daily_pick_views WHERE user_id = :userId", userId);
    }

    private static void deleteDailyPicks(Handle handle, UUID userId) {
        executeHardDelete(
                handle, "DELETE FROM daily_picks WHERE user_id = :userId OR picked_user_id = :userId", userId);
    }

    private static void deleteSwipeSessions(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM swipe_sessions WHERE user_id = :userId", userId);
    }

    private static void deleteStandouts(Handle handle, UUID userId) {
        executeHardDelete(
                handle, "DELETE FROM standouts WHERE seeker_id = :userId OR standout_user_id = :userId", userId);
    }

    private static void deleteFriendRequests(Handle handle, UUID userId) {
        executeHardDelete(
                handle, "DELETE FROM friend_requests WHERE from_user_id = :userId OR to_user_id = :userId", userId);
    }

    private static void deleteNotifications(Handle handle, UUID userId) {
        executeHardDelete(handle, "DELETE FROM notifications WHERE user_id = :userId", userId);
    }

    private static void softDeleteBlocks(Handle handle, UUID userId, Instant deletedAt) {
        executeSoftDelete(handle, """
            UPDATE blocks
            SET deleted_at = :deletedAt
            WHERE deleted_at IS NULL
              AND (blocker_id = :userId OR blocked_id = :userId)
            """, userId, deletedAt);
    }

    private static void softDeleteReports(Handle handle, UUID userId, Instant deletedAt) {
        executeSoftDelete(handle, """
            UPDATE reports
            SET deleted_at = :deletedAt
            WHERE deleted_at IS NULL
              AND (reporter_id = :userId OR reported_user_id = :userId)
            """, userId, deletedAt);
    }

    private static void deleteUndoStates(Handle handle, UUID userId) {
        try (var update = handle.createUpdate("DELETE FROM undo_states WHERE user_id = :userId")) {
            update.bind(USER_ID_BIND, userId).execute();
        }
    }

    private static void executeSoftDelete(Handle handle, String sql, UUID userId, Instant deletedAt) {
        try (var update = handle.createUpdate(sql)) {
            update.bind(USER_ID_BIND, userId).bind(DELETED_AT_BIND, deletedAt).execute();
        }
    }

    private static void executeHardDelete(Handle handle, String sql, UUID userId) {
        try (var update = handle.createUpdate(sql)) {
            update.bind(USER_ID_BIND, userId).execute();
        }
    }
}
