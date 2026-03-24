package datingapp.app.event.handlers;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.core.metrics.AchievementService;
import java.util.Objects;

/** Listens for profile and swipe events and checks achievement eligibility. */
public final class AchievementEventHandler {

    private final AchievementService achievementService;

    public AchievementEventHandler(AchievementService achievementService) {
        this.achievementService = Objects.requireNonNull(achievementService, "achievementService");
    }

    /** Subscribes this handler to the given event bus with BEST_EFFORT policy. */
    public void register(AppEventBus eventBus) {
        eventBus.subscribe(AppEvent.SwipeRecorded.class, this::onSwipeRecorded, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(AppEvent.ProfileSaved.class, this::onProfileSaved, AppEventBus.HandlerPolicy.BEST_EFFORT);
        eventBus.subscribe(
                AppEvent.ProfileNoteSaved.class, this::onProfileNoteSaved, AppEventBus.HandlerPolicy.BEST_EFFORT);
    }

    void onSwipeRecorded(AppEvent.SwipeRecorded event) {
        if (event.resultedInMatch()) {
            achievementService.checkAndUnlock(event.swiperId());
        }
    }

    void onProfileSaved(AppEvent.ProfileSaved event) {
        achievementService.checkAndUnlock(event.userId());
    }

    void onProfileNoteSaved(AppEvent.ProfileNoteSaved event) {
        achievementService.checkAndUnlock(event.authorId());
    }
}
