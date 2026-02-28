package datingapp.app.event.handlers;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.core.profile.ProfileService;
import java.util.Objects;

/**
 * Listens for {@link AppEvent.SwipeRecorded} events and checks achievement
 * eligibility when a swipe results in a match.
 */
public final class AchievementEventHandler {

    private final ProfileService profileService;

    public AchievementEventHandler(ProfileService profileService) {
        this.profileService = Objects.requireNonNull(profileService, "profileService");
    }

    /** Subscribes this handler to the given event bus with BEST_EFFORT policy. */
    public void register(AppEventBus eventBus) {
        eventBus.subscribe(AppEvent.SwipeRecorded.class, this::onSwipeRecorded, AppEventBus.HandlerPolicy.BEST_EFFORT);
    }

    void onSwipeRecorded(AppEvent.SwipeRecorded event) {
        if (event.resultedInMatch()) {
            profileService.checkAndUnlock(event.swiperId());
        }
    }
}
