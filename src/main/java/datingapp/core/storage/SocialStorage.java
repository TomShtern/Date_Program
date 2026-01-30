package datingapp.core.storage;

import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Consolidated storage interface for social features: friend requests and
 * notifications.
 * Groups related operations that were previously in separate interfaces.
 *
 * <p>
 * This interface combines:
 * <ul>
 * <li>{@code FriendRequestStorage} - Friend request entity management</li>
 * <li>{@code NotificationStorage} - Notification entity management</li>
 * </ul>
 */
public interface SocialStorage {

    // ═══════════════════════════════════════════════════════════════
    // Friend Request Operations (from FriendRequestStorage)
    // ═══════════════════════════════════════════════════════════════

    /** Saves a new friend request. */
    void saveFriendRequest(FriendRequest request);

    /** Updates an existing friend request (e.g., status change). */
    void updateFriendRequest(FriendRequest request);

    /** Gets a friend request by ID. */
    Optional<FriendRequest> getFriendRequest(UUID id);

    /** Gets a pending friend request between two users (either direction). */
    Optional<FriendRequest> getPendingFriendRequestBetween(UUID user1, UUID user2);

    /** Gets all pending friend requests for a user (requests they received). */
    List<FriendRequest> getPendingFriendRequestsForUser(UUID userId);

    /** Deletes a friend request by ID. */
    void deleteFriendRequest(UUID id);

    // ═══════════════════════════════════════════════════════════════
    // Notification Operations (from NotificationStorage)
    // ═══════════════════════════════════════════════════════════════

    /** Saves a new notification. */
    void saveNotification(Notification notification);

    /** Marks a notification as read. */
    void markNotificationAsRead(UUID id);

    /** Gets notifications for a user, optionally filtering to unread only. */
    List<Notification> getNotificationsForUser(UUID userId, boolean unreadOnly);

    /** Gets a notification by ID. */
    Optional<Notification> getNotification(UUID id);

    /** Deletes a notification by ID. */
    void deleteNotification(UUID id);

    /** Deletes old notifications before a given timestamp. */
    void deleteOldNotifications(Instant before);
}
