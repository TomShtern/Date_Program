package datingapp.app.event.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.InProcessAppEventBus;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AchievementEventHandlerTest {

    private InProcessAppEventBus bus;
    private CapturingAchievementService achievementService;

    @BeforeEach
    void setUp() {
        bus = new InProcessAppEventBus();
        achievementService = new CapturingAchievementService();
        new AchievementEventHandler(achievementService).register(bus);
    }

    @Test
    void callsCheckAndUnlockWhenMatchCreated() {
        UUID swiperId = UUID.randomUUID();
        bus.publish(new AppEvent.SwipeRecorded(swiperId, UUID.randomUUID(), Like.Direction.LIKE, true, Instant.now()));

        assertEquals(List.of(swiperId), achievementService.checkedUserIds);
    }

    @Test
    void doesNotCallCheckAndUnlockWhenNoMatch() {
        bus.publish(new AppEvent.SwipeRecorded(
                UUID.randomUUID(), UUID.randomUUID(), Like.Direction.LIKE, false, Instant.now()));

        assertEquals(List.of(), achievementService.checkedUserIds);
    }

    @Test
    void doesNotCallCheckAndUnlockOnPass() {
        bus.publish(new AppEvent.SwipeRecorded(
                UUID.randomUUID(), UUID.randomUUID(), Like.Direction.PASS, false, Instant.now()));

        assertEquals(List.of(), achievementService.checkedUserIds);
    }

    @Test
    void profileSavedTriggersAchievementCheck() {
        UUID userId = UUID.randomUUID();

        bus.publish(new AppEvent.ProfileSaved(userId, true, Instant.now()));

        assertEquals(List.of(userId), achievementService.checkedUserIds);
    }

    @Test
    void profileNoteSavedTriggersAchievementCheck() {
        UUID authorId = UUID.randomUUID();

        bus.publish(new AppEvent.ProfileNoteSaved(authorId, UUID.randomUUID(), 12, Instant.now()));

        assertEquals(List.of(authorId), achievementService.checkedUserIds);
    }

    @Test
    void profileCompletedTriggersAchievementCheck() {
        UUID userId = UUID.randomUUID();

        bus.publish(new AppEvent.ProfileCompleted(userId, Instant.now()));

        assertEquals(List.of(userId), achievementService.checkedUserIds);
    }

    @Test
    void unvalidatedUserReportedDoesNotTriggerAchievementCheck() {
        UUID reporterId = UUID.randomUUID();

        bus.publish(new AppEvent.UserReported(reporterId, UUID.randomUUID(), "spam", false, false, Instant.now()));

        assertEquals(List.of(), achievementService.checkedUserIds);
    }

    @Test
    void validatedUserReportedTriggersAchievementCheck() {
        UUID reporterId = UUID.randomUUID();

        bus.publish(new AppEvent.UserReported(reporterId, UUID.randomUUID(), "spam", false, true, Instant.now()));

        assertEquals(List.of(reporterId), achievementService.checkedUserIds);
    }

    @Test
    void handlerRegistersWithBestEffortPolicy() {
        // Verify handler doesn't propagate exceptions through the bus
        bus.subscribe(
                AppEvent.ProfileSaved.class,
                event -> {
                    throw new RuntimeException("simulated achievement failure");
                },
                AppEventBus.HandlerPolicy.BEST_EFFORT);

        // Should not throw
        UUID userId = UUID.randomUUID();
        bus.publish(new AppEvent.ProfileSaved(userId, true, Instant.now()));

        assertEquals(List.of(userId), achievementService.checkedUserIds);
    }

    private static final class CapturingAchievementService implements AchievementService {
        private final List<UUID> checkedUserIds = new ArrayList<>();

        @Override
        public List<UserAchievement> checkAndUnlock(UUID userId) {
            checkedUserIds.add(userId);
            return List.of();
        }

        @Override
        public List<UserAchievement> getUnlocked(UUID userId) {
            return List.of();
        }

        @Override
        public List<AchievementProgress> getProgress(UUID userId) {
            return List.of();
        }

        @Override
        public Map<Achievement.Category, List<AchievementProgress>> getProgressByCategory(UUID userId) {
            return Map.of();
        }

        @Override
        public int countUnlocked(UUID userId) {
            return 0;
        }
    }
}
