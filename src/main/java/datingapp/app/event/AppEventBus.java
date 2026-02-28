package datingapp.app.event;

/** Contract for publishing and subscribing to application events. */
public interface AppEventBus {

    /**
     * Dispatches event to all registered handlers for that event type.
     * Handlers execute synchronously in publication order.
     * {@link HandlerPolicy#BEST_EFFORT} handler exceptions are logged but do NOT propagate.
     * {@link HandlerPolicy#REQUIRED} handler exceptions propagate to the publisher.
     */
    void publish(AppEvent event);

    /** Registers a handler for a specific event type with {@link HandlerPolicy#BEST_EFFORT} policy. */
    <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler);

    /** Registers a handler for a specific event type with the given policy. */
    <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy);

    enum HandlerPolicy {
        /** Exception propagates to publisher (transaction fails). */
        REQUIRED,
        /** Exception logged, publisher continues. */
        BEST_EFFORT
    }

    @FunctionalInterface
    interface AppEventHandler<T extends AppEvent> {
        void handle(T event);
    }
}
