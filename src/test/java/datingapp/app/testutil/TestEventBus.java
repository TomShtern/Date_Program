package datingapp.app.testutil;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Simple in-memory event bus for tests that need a valid, inspectable bus. */
public final class TestEventBus implements AppEventBus {

    private final List<AppEvent> publishedEvents = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, List<AppEventHandler<?>>> handlers = new ConcurrentHashMap<>();

    @Override
    public void publish(AppEvent event) {
        publishedEvents.add(event);
        List<AppEventHandler<?>> matchingHandlers = handlers.getOrDefault(event.getClass(), List.of());
        for (AppEventHandler<?> handler : matchingHandlers) {
            notifyHandler(handler, event);
        }
    }

    @Override
    public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
        handlers.computeIfAbsent(eventType, ignored -> new CopyOnWriteArrayList<>())
                .add(handler);
    }

    @Override
    public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
        subscribe(eventType, handler);
    }

    public List<AppEvent> publishedEvents() {
        return List.copyOf(publishedEvents);
    }

    public <T extends AppEvent> List<T> publishedEventsOfType(Class<T> eventType) {
        List<T> typedEvents = new ArrayList<>();
        for (AppEvent event : publishedEvents) {
            if (eventType.isInstance(event)) {
                typedEvents.add(eventType.cast(event));
            }
        }
        return List.copyOf(typedEvents);
    }

    public void clear() {
        publishedEvents.clear();
    }

    @SuppressWarnings("unchecked")
    private static <T extends AppEvent> void notifyHandler(AppEventHandler<?> handler, AppEvent event) {
        ((AppEventHandler<T>) handler).handle((T) event);
    }
}
