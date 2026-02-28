package datingapp.app.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InProcessAppEventBusTest {

    private InProcessAppEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new InProcessAppEventBus();
    }

    @Test
    void publishDispatchesToSubscribedHandler() {
        AtomicInteger callCount = new AtomicInteger();
        bus.subscribe(AppEvent.SwipeRecorded.class, e -> callCount.incrementAndGet());

        bus.publish(new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "LIKE", false, Instant.now()));
        assertEquals(1, callCount.get());
    }

    @Test
    void publishDoesNotDispatchToUnrelatedHandler() {
        AtomicInteger callCount = new AtomicInteger();
        bus.subscribe(AppEvent.SwipeRecorded.class, e -> callCount.incrementAndGet());

        bus.publish(new AppEvent.ProfileSaved(UUID.randomUUID(), true, Instant.now()));
        assertEquals(0, callCount.get());
    }

    @Test
    void bestEffortHandlerExceptionDoesNotPropagate() {
        bus.subscribe(
                AppEvent.SwipeRecorded.class,
                e -> {
                    throw new RuntimeException("handler failure");
                },
                AppEventBus.HandlerPolicy.BEST_EFFORT);

        assertDoesNotThrow(() -> bus.publish(
                new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "LIKE", false, Instant.now())));
    }

    @Test
    void requiredHandlerExceptionPropagates() {
        bus.subscribe(
                AppEvent.SwipeRecorded.class,
                e -> {
                    throw new RuntimeException("required failure");
                },
                AppEventBus.HandlerPolicy.REQUIRED);

        assertThrows(
                RuntimeException.class,
                () -> bus.publish(new AppEvent.SwipeRecorded(
                        UUID.randomUUID(), UUID.randomUUID(), "LIKE", false, Instant.now())));
    }

    @Test
    void multipleHandlersCalledInOrder() {
        List<Integer> order = new ArrayList<>();
        bus.subscribe(AppEvent.SwipeRecorded.class, e -> order.add(1));
        bus.subscribe(AppEvent.SwipeRecorded.class, e -> order.add(2));
        bus.subscribe(AppEvent.SwipeRecorded.class, e -> order.add(3));

        bus.publish(new AppEvent.SwipeRecorded(UUID.randomUUID(), UUID.randomUUID(), "LIKE", false, Instant.now()));
        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void concurrentSubscribeAndPublish() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger callCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            Thread.ofVirtual().start(() -> {
                bus.subscribe(AppEvent.ProfileSaved.class, e -> callCount.incrementAndGet());
                latch.countDown();
            });
        }
        latch.await();

        bus.publish(new AppEvent.ProfileSaved(UUID.randomUUID(), false, Instant.now()));
        assertEquals(threadCount, callCount.get());
    }
}
