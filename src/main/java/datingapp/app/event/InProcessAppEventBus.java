package datingapp.app.event;

import datingapp.core.LoggingSupport;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Synchronous in-process event bus implementation. */
public final class InProcessAppEventBus implements AppEventBus, LoggingSupport {

    private static final Logger LOG = LoggerFactory.getLogger(InProcessAppEventBus.class);

    // ConcurrentHashMap + CopyOnWriteArrayList ensure thread-safe subscribe/publish (C2 fixed).
    private final Map<Class<? extends AppEvent>, List<HandlerEntry<?>>> handlers = new ConcurrentHashMap<>();

    private record HandlerEntry<T extends AppEvent>(AppEventHandler<T> handler, HandlerPolicy policy) {}

    @Override
    public void publish(AppEvent event) {
        List<HandlerEntry<?>> entries = handlers.get(event.getClass());
        if (entries == null || entries.isEmpty()) {
            logDebug(
                    "No handlers registered for event type {}", event.getClass().getSimpleName());
            return;
        }

        logDebug("Publishing {} to {} handler(s)", event.getClass().getSimpleName(), entries.size());

        for (HandlerEntry<?> entry : entries) {
            try {
                dispatchUnchecked(entry, event);
            } catch (RuntimeException e) {
                if (entry.policy() == HandlerPolicy.REQUIRED) {
                    throw e;
                }
                logWarn(
                        "BEST_EFFORT handler failed for {}: {}",
                        event.getClass().getSimpleName(),
                        e.getMessage());
            }
        }
    }

    @Override
    public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
        subscribe(eventType, handler, HandlerPolicy.BEST_EFFORT);
    }

    @Override
    public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(new HandlerEntry<>(handler, policy));
    }

    @Override
    public Logger logger() {
        return LOG;
    }

    @SuppressWarnings("unchecked")
    private <T extends AppEvent> void dispatchUnchecked(HandlerEntry<T> entry, AppEvent event) {
        entry.handler().handle((T) event);
    }
}
