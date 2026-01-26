package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Social.FriendRequest;
import datingapp.core.Social.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("H2SocialStorage")
class H2SocialStorageTest {

    private H2SocialStorage.FriendRequests friendRequests;
    private H2SocialStorage.Notifications notifications;

    @BeforeEach
    void setUp() {
        DatabaseManager.setJdbcUrl(
                "jdbc:h2:mem:social-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;REFERENTIAL_INTEGRITY=FALSE");
        DatabaseManager.resetInstance();
        DatabaseManager dbManager = DatabaseManager.getInstance();

        friendRequests = new H2SocialStorage.FriendRequests(dbManager);
        notifications = new H2SocialStorage.Notifications(dbManager);
    }

    @Test
    @DisplayName("Saves and retrieves friend requests")
    void savesAndRetrievesFriendRequest() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        FriendRequest request = FriendRequest.create(from, to);
        friendRequests.save(request);

        Optional<FriendRequest> loaded = friendRequests.get(request.id());
        assertTrue(loaded.isPresent());
        assertEquals(FriendRequest.Status.PENDING, loaded.get().status());
    }

    @Test
    @DisplayName("Updates friend request status")
    void updatesFriendRequestStatus() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        FriendRequest request = FriendRequest.create(from, to);
        friendRequests.save(request);

        FriendRequest accepted = new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.ACCEPTED,
                java.time.Instant.now());
        friendRequests.update(accepted);

        Optional<FriendRequest> loaded = friendRequests.get(request.id());
        assertTrue(loaded.isPresent());
        assertEquals(FriendRequest.Status.ACCEPTED, loaded.get().status());
        assertNotNull(loaded.get().respondedAt());
    }

    @Test
    @DisplayName("Pending requests query returns only pending")
    void pendingRequestsQueries() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        FriendRequest request = FriendRequest.create(from, to);
        friendRequests.save(request);

        assertTrue(friendRequests.getPendingBetween(from, to).isPresent());
        List<FriendRequest> pending = friendRequests.getPendingForUser(to);
        assertEquals(1, pending.size());

        friendRequests.update(new FriendRequest(
                request.id(),
                request.fromUserId(),
                request.toUserId(),
                request.createdAt(),
                FriendRequest.Status.ACCEPTED,
                java.time.Instant.now()));
        assertFalse(friendRequests.getPendingBetween(from, to).isPresent());
    }

    @Test
    @DisplayName("Saves and retrieves notifications")
    void savesAndRetrievesNotifications() {
        UUID userId = UUID.randomUUID();

        Notification notification =
                Notification.create(userId, Notification.Type.MATCH_FOUND, "Match!", "You matched", null);
        notifications.save(notification);

        List<Notification> unread = notifications.getForUser(userId, true);
        assertEquals(1, unread.size());
        assertEquals(notification.id(), unread.get(0).id());

        notifications.markAsRead(notification.id());
        List<Notification> unreadAfter = notifications.getForUser(userId, true);
        assertTrue(unreadAfter.isEmpty());
    }
}
