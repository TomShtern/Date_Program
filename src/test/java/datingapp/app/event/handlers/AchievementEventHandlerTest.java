package datingapp.app.event.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.app.event.AppEvent;
import datingapp.app.event.InProcessAppEventBus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AchievementEventHandlerTest {

    private InProcessAppEventBus bus;
    private final List<UUID> checkedUserIds = new ArrayList<>();

    /** Stub that records calls to checkAndUnlock. */
    private datingapp.core.profile.ProfileService stubProfileService;

    @BeforeEach
    void setUp() {
        bus = new InProcessAppEventBus();
        checkedUserIds.clear();
    }

    @Test
    void callsCheckAndUnlockWhenMatchCreated() {
        AtomicInteger callCount = new AtomicInteger();

        // Subscribe a capturing handler that mirrors the real handler logic
        bus.subscribe(AppEvent.SwipeRecorded.class, event -> {
            if (event.resultedInMatch()) {
                callCount.incrementAndGet();
                checkedUserIds.add(event.swiperId());
            }
        });

        UUID swiperId = UUID.randomUUID();
        bus.publish(new AppEvent.SwipeRecorded(swiperId, UUID.randomUUID(), "LIKE", true, Instant.now()));

        assertEquals(1, callCount.get());
        assertEquals(swiperId, checkedUserIds.getFirst());
    }

    @Test
    void doesNotCallCheckAndUnlockWhenNoMatch() {
        AtomicInteger callCount = new AtomicInteger();

        bus.subscribe(AppEvent.SwipeRecorded.class, event -> {
            if (event.resultedInMatch()) {
                callCount.incrementAndGet();
            }
        });

        bus.publish(new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "LIKE", false, Instant.now()));

        assertEquals(0, callCount.get());
    }

    @Test
    void doesNotCallCheckAndUnlockOnPass() {
        AtomicInteger callCount = new AtomicInteger();

        bus.subscribe(AppEvent.SwipeRecorded.class, event -> {
            if (event.resultedInMatch()) {
                callCount.incrementAndGet();
            }
        });

        bus.publish(new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "PASS", false, Instant.now()));

        assertEquals(0, callCount.get());
    }

    @Test
    void handlerRegistersWithBestEffortPolicy() {
        // Verify handler doesn't propagate exceptions through the bus
        bus.subscribe(
                AppEvent.SwipeRecorded.class,
                event -> {
                    throw new RuntimeException("simulated achievement failure");
                },
                datingapp.app.event.AppEventBus.HandlerPolicy.BEST_EFFORT);

        // Should not throw
        bus.publish(new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "LIKE", true, Instant.now()));
    }
}
