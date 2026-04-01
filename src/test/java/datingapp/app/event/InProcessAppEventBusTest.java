package datingapp.app.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import datingapp.core.connection.ConnectionModels.Like;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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

        bus.publish(new AppEvent.SwipeRecorded(
                UUID.randomUUID(), UUID.randomUUID(), Like.Direction.LIKE, false, Instant.now()));
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
    void publishWithNoSubscribersIsANoop() {
        assertDoesNotThrow(() -> bus.publish(new AppEvent.AccountDeleted(
                UUID.randomUUID(), AppEvent.DeletionReason.ANONYMIZED_CODE, Instant.now())));
    }

    @Test
    void bestEffortHandlerExceptionDoesNotPropagate() {
        Logger busLogger = (Logger) LoggerFactory.getLogger(InProcessAppEventBus.class);
        Level originalLevel = busLogger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        busLogger.setLevel(Level.WARN);
        appender.start();
        busLogger.addAppender(appender);

        bus.subscribe(
                AppEvent.SwipeRecorded.class,
                e -> {
                    throw new RuntimeException("handler failure");
                },
                AppEventBus.HandlerPolicy.BEST_EFFORT);

        try {
            assertDoesNotThrow(() -> bus.publish(new AppEvent.SwipeRecorded(
                    UUID.randomUUID(), UUID.randomUUID(), Like.Direction.LIKE, false, Instant.now())));

            ILoggingEvent event = appender.list.getFirst();
            assertNotNull(event.getThrowableProxy());
            assertEquals("handler failure", event.getThrowableProxy().getMessage());
        } finally {
            busLogger.detachAppender(appender);
            busLogger.setLevel(originalLevel);
        }
    }

    @Test
    void requiredHandlerExceptionPropagates() {
        AppEvent.SwipeRecorded event = new AppEvent.SwipeRecorded(
                UUID.randomUUID(), UUID.randomUUID(), Like.Direction.LIKE, false, Instant.now());
        bus.subscribe(
                AppEvent.SwipeRecorded.class,
                e -> {
                    throw new RuntimeException("required failure");
                },
                AppEventBus.HandlerPolicy.REQUIRED);

        assertThrows(RuntimeException.class, () -> bus.publish(event));
    }

    @Test
    void multipleHandlersCalledInOrder() {
        List<Integer> order = new ArrayList<>();
        bus.subscribe(AppEvent.SwipeRecorded.class, e -> order.add(1));
        bus.subscribe(AppEvent.SwipeRecorded.class, e -> order.add(2));
        bus.subscribe(AppEvent.SwipeRecorded.class, e -> order.add(3));

        bus.publish(new AppEvent.SwipeRecorded(
                UUID.randomUUID(), UUID.randomUUID(), Like.Direction.LIKE, false, Instant.now()));
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

    @Test
    void subscribeDuringPublishDefersNewHandlerToLaterEvents() {
        AtomicInteger primaryHandlerCount = new AtomicInteger();
        AtomicInteger lateHandlerCount = new AtomicInteger();

        bus.subscribe(AppEvent.ProfileSaved.class, event -> {
            primaryHandlerCount.incrementAndGet();
            bus.subscribe(AppEvent.ProfileSaved.class, ignored -> lateHandlerCount.incrementAndGet());
        });

        bus.publish(new AppEvent.ProfileSaved(UUID.randomUUID(), true, Instant.now()));
        assertEquals(1, primaryHandlerCount.get());
        assertEquals(0, lateHandlerCount.get());

        bus.publish(new AppEvent.ProfileSaved(UUID.randomUUID(), false, Instant.now()));
        assertEquals(2, primaryHandlerCount.get());
        assertEquals(1, lateHandlerCount.get());
    }
}
