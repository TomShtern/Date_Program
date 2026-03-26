package datingapp.app.event.handlers;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.ActivityMetricsService;

/** Listens for activity-producing events and forwards them to session metrics tracking. */
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
        eventBus.subscribe(AppEvent.ProfileSaved.class, this::onProfileSaved, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(
                AppEvent.ProfileNoteSaved.class, this::onProfileNoteSaved, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(
                AppEvent.ProfileNoteDeleted.class, this::onProfileNoteDeleted, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(
                AppEvent.AccountDeleted.class, this::onAccountDeleted, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(
                AppEvent.ConversationArchived.class,
                this::onConversationArchived,
                AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(AppEvent.UserBlocked.class, this::onUserBlocked, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(AppEvent.UserReported.class, this::onUserReported, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(AppEvent.MatchExpired.class, this::onMatchExpired, AppEventBus.HandlerPolicy.BEST_EFFORT);
    }

    void onSwipeRecorded(AppEvent.SwipeRecorded event) {
        Like.Direction direction = Like.Direction.valueOf(event.direction());
        activityMetricsService.recordSwipe(event.swiperId(), direction, event.resultedInMatch());
    }

    void onMessageSent(AppEvent.MessageSent event) {
        activityMetricsService.recordActivity(event.senderId());
    }

    void onProfileSaved(AppEvent.ProfileSaved event) {
        activityMetricsService.recordActivity(event.userId());
    }

    void onProfileNoteSaved(AppEvent.ProfileNoteSaved event) {
        activityMetricsService.recordActivity(event.authorId());
    }

    void onProfileNoteDeleted(AppEvent.ProfileNoteDeleted event) {
        activityMetricsService.recordActivity(event.authorId());
    }

    void onAccountDeleted(AppEvent.AccountDeleted event) {
        activityMetricsService.endSession(event.userId());
    }

    void onConversationArchived(AppEvent.ConversationArchived event) {
        activityMetricsService.recordActivity(event.archivedByUserId());
    }

    void onUserBlocked(AppEvent.UserBlocked event) {
        activityMetricsService.recordActivity(event.blockerId());
    }

    void onUserReported(AppEvent.UserReported event) {
        activityMetricsService.recordActivity(event.reporterId());
    }

    void onMatchExpired(AppEvent.MatchExpired event) {
        activityMetricsService.recordActivity(event.userA());
        activityMetricsService.recordActivity(event.userB());
    }
}
