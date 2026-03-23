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
                softDeleteUser(handle, user.getId(), deletedAt);
                softDeleteLikes(handle, user.getId(), deletedAt);
                softDeleteMatches(handle, user.getId(), deletedAt);
                softDeleteConversations(handle, user.getId(), deletedAt);
                softDeleteMessages(handle, user.getId(), deletedAt);
                softDeleteProfileNotes(handle, user.getId(), deletedAt);
                softDeleteBlocks(handle, user.getId(), deletedAt);
                softDeleteReports(handle, user.getId(), deletedAt);
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

    private static void executeSoftDelete(Handle handle, String sql, UUID userId, Instant deletedAt) {
        try (var update = handle.createUpdate(sql)) {
            update.bind(USER_ID_BIND, userId).bind(DELETED_AT_BIND, deletedAt).execute();
        }
    }
}
