package datingapp.app.event.handlers;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.ActivityMetricsService;

/**
 * Listens for swipe and message events and forwards them to
 * {@link ActivityMetricsService} for session metrics tracking.
 */
public final class MetricsEventHandler {

    private final ActivityMetricsService activityMetricsService;

    public MetricsEventHandler(ActivityMetricsService activityMetricsService) {
        this.activityMetricsService = activityMetricsService;
    }

    /** Subscribes this handler to the given event bus with BEST_EFFORT policy. */
    public void register(AppEventBus eventBus) {
        if (activityMetricsService == null) {
            return;
        }
        eventBus.subscribe(AppEvent.SwipeRecorded.class, this::onSwipeRecorded, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(AppEvent.MessageSent.class, this::onMessageSent, AppEventBus.HandlerPolicy.BEST_EFFORT);
    }

    void onSwipeRecorded(AppEvent.SwipeRecorded event) {
        Like.Direction direction = Like.Direction.valueOf(event.direction());
        activityMetricsService.recordSwipe(event.swiperId(), direction, event.resultedInMatch());
    }

    void onMessageSent(AppEvent.MessageSent event) {
        activityMetricsService.recordActivity(event.senderId());
    }
}
