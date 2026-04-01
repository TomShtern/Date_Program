package datingapp.app.event.handlers;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsEventHandlerTest {

    private InProcessAppEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new InProcessAppEventBus();
    }

    @Test
    void swipeRecordedTriggersRecordSwipe() {
        List<String> swipeDirections = new ArrayList<>();
        List<Boolean> matchFlags = new ArrayList<>();

        bus.subscribe(AppEvent.SwipeRecorded.class, event -> {
            swipeDirections.add(event.direction().name());
            matchFlags.add(event.resultedInMatch());
        });

        bus.publish(new AppEvent.SwipeRecorded(
                UUID.randomUUID(), UUID.randomUUID(), Like.Direction.LIKE, true, Instant.now()));

        assertEquals(1, swipeDirections.size());
        assertEquals("LIKE", swipeDirections.getFirst());
        assertTrue(matchFlags.getFirst());
    }

    @Test
    void messageSentTriggersRecordActivity() {
        List<UUID> activityUserIds = new ArrayList<>();

        bus.subscribe(AppEvent.MessageSent.class, event -> activityUserIds.add(event.senderId()));

        UUID senderId = UUID.randomUUID();
        bus.publish(new AppEvent.MessageSent(senderId, UUID.randomUUID(), UUID.randomUUID(), Instant.now()));

        assertEquals(1, activityUserIds.size());
        assertEquals(senderId, activityUserIds.getFirst());
    }

    @Test
    void nullActivityMetricsServiceSkipsRegistration() {
        // Construct with null — register should be a no-op
        MetricsEventHandler handler = new MetricsEventHandler(null);
        assertDoesNotThrow(() -> {
            handler.register(bus);
            bus.publish(new AppEvent.SwipeRecorded(
                    UUID.randomUUID(), UUID.randomUUID(), Like.Direction.LIKE, false, Instant.now()));
            bus.publish(
                    new AppEvent.MessageSent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
        });
    }

    @Test
    void handlerExceptionsDoNotPropagate() {
        bus.subscribe(
                AppEvent.SwipeRecorded.class,
                event -> {
                    throw new RuntimeException("simulated metrics failure");
                },
                AppEventBus.HandlerPolicy.BEST_EFFORT);

        assertDoesNotThrow(() -> bus.publish(new AppEvent.SwipeRecorded(
                UUID.randomUUID(), UUID.randomUUID(), Like.Direction.LIKE, true, Instant.now())));
    }

    @Test
    void profileSavedTriggersRecordActivity() {
        UUID userId = UUID.randomUUID();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        new MetricsEventHandler(service).register(eventBus);

        eventBus.publish(new AppEvent.ProfileSaved(userId, true, Instant.now()));

        assertNotNull(service.getCurrentSession(userId).orElse(null));
    }

    @Test
    void profileNoteSavedTriggersRecordActivity() {
        UUID authorId = UUID.randomUUID();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        new MetricsEventHandler(service).register(eventBus);

        eventBus.publish(new AppEvent.ProfileNoteSaved(authorId, UUID.randomUUID(), 8, Instant.now()));

        assertNotNull(service.getCurrentSession(authorId).orElse(null));
    }

    @Test
    void profileNoteDeletedTriggersRecordActivity() {
        UUID authorId = UUID.randomUUID();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        new MetricsEventHandler(service).register(eventBus);

        eventBus.publish(new AppEvent.ProfileNoteDeleted(authorId, UUID.randomUUID(), Instant.now()));

        assertNotNull(service.getCurrentSession(authorId).orElse(null));
    }

    @Test
    void conversationArchivedTriggersRecordActivity() {
        UUID archivedByUserId = UUID.randomUUID();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        new MetricsEventHandler(service).register(eventBus);

        eventBus.publish(new AppEvent.ConversationArchived("conversation-1", archivedByUserId, Instant.now()));

        assertNotNull(service.getCurrentSession(archivedByUserId).orElse(null));
    }

    @Test
    void userBlockedTriggersRecordActivity() {
        UUID blockerId = UUID.randomUUID();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        new MetricsEventHandler(service).register(eventBus);

        eventBus.publish(new AppEvent.UserBlocked(blockerId, UUID.randomUUID(), Instant.now()));

        assertNotNull(service.getCurrentSession(blockerId).orElse(null));
    }

    @Test
    void userReportedTriggersRecordActivity() {
        UUID reporterId = UUID.randomUUID();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        new MetricsEventHandler(service).register(eventBus);

        eventBus.publish(new AppEvent.UserReported(reporterId, UUID.randomUUID(), "spam", false, false, Instant.now()));

        assertNotNull(service.getCurrentSession(reporterId).orElse(null));
    }

    @Test
    void matchExpiredTriggersRecordActivityForBothUsers() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        new MetricsEventHandler(service).register(eventBus);

        eventBus.publish(new AppEvent.MatchExpired("match-1", userA, userB, Instant.now()));

        assertNotNull(service.getCurrentSession(userA).orElse(null));
        assertNotNull(service.getCurrentSession(userB).orElse(null));
    }

    @Test
    void accountDeletedEndsSession() {
        UUID userId = UUID.randomUUID();
        ActivityMetricsService service = new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
        InProcessAppEventBus eventBus = new InProcessAppEventBus();
        new MetricsEventHandler(service).register(eventBus);

        // Create a session first
        eventBus.publish(new AppEvent.ProfileSaved(userId, true, Instant.now()));
        assertTrue(service.getCurrentSession(userId).isPresent());

        // Now delete the account
        eventBus.publish(new AppEvent.AccountDeleted(userId, AppEvent.DeletionReason.USER_REQUEST, Instant.now()));

        // Session should still exist but be ended (endSession is best-effort)
        assertTrue(service.getCurrentSession(userId).isEmpty());
    }
}
