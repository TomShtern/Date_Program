package datingapp.app.event.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.InProcessAppEventBus;
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
            swipeDirections.add(event.direction());
            matchFlags.add(event.resultedInMatch());
        });

        bus.publish(new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "LIKE", true, Instant.now()));

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
        handler.register(bus);

        // Publishing should not throw (no handlers registered)
        bus.publish(new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "LIKE", false, Instant.now()));
        bus.publish(new AppEvent.MessageSent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now()));
    }

    @Test
    void handlerExceptionsDoNotPropagate() {
        bus.subscribe(
                AppEvent.SwipeRecorded.class,
                event -> {
                    throw new RuntimeException("simulated metrics failure");
                },
                AppEventBus.HandlerPolicy.BEST_EFFORT);

        // Should not throw
        bus.publish(new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "LIKE", true, Instant.now()));
    }
}
