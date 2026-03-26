package datingapp.architecture;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.event.AppEvent;
import datingapp.app.event.AppEventBus;
import datingapp.app.event.handlers.AchievementEventHandler;
import datingapp.app.event.handlers.MetricsEventHandler;
import datingapp.app.event.handlers.NotificationEventHandler;
import datingapp.core.AppConfig;
import datingapp.core.metrics.AchievementService;
import datingapp.core.metrics.ActivityMetricsService;
import datingapp.core.storage.CommunicationStorage;
import datingapp.core.testutil.TestStorages;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventCoverageArchitectureTest {

    private static final Map<Class<? extends AppEvent>, Ownership> OWNERSHIP = Map.ofEntries(
            Map.entry(AppEvent.SwipeRecorded.class, Ownership.of(HandlerOwner.ACHIEVEMENT, HandlerOwner.METRICS)),
            Map.entry(AppEvent.MatchCreated.class, Ownership.of(HandlerOwner.NOTIFICATION)),
            Map.entry(AppEvent.ProfileSaved.class, Ownership.of(HandlerOwner.ACHIEVEMENT, HandlerOwner.METRICS)),
            Map.entry(AppEvent.ProfileCompleted.class, Ownership.of(HandlerOwner.ACHIEVEMENT)),
            Map.entry(AppEvent.ProfileNoteSaved.class, Ownership.of(HandlerOwner.ACHIEVEMENT, HandlerOwner.METRICS)),
            Map.entry(AppEvent.ProfileNoteDeleted.class, Ownership.of(HandlerOwner.METRICS)),
            Map.entry(AppEvent.ConversationArchived.class, Ownership.of(HandlerOwner.METRICS)),
            Map.entry(
                    AppEvent.LocationUpdated.class,
                    Ownership.noOp("Location updates are handled directly by profile/location services, not events.")),
            Map.entry(
                    AppEvent.DailyLimitReset.class,
                    Ownership.noOp("Daily swipe limits are scheduler/state-driven rather than handler-owned.")),
            Map.entry(AppEvent.MatchExpired.class, Ownership.of(HandlerOwner.METRICS)),
            Map.entry(AppEvent.AccountDeleted.class, Ownership.of(HandlerOwner.METRICS, HandlerOwner.NOTIFICATION)),
            Map.entry(AppEvent.FriendRequestAccepted.class, Ownership.of(HandlerOwner.NOTIFICATION)),
            Map.entry(AppEvent.RelationshipTransitioned.class, Ownership.of(HandlerOwner.NOTIFICATION)),
            Map.entry(AppEvent.MessageSent.class, Ownership.of(HandlerOwner.METRICS, HandlerOwner.NOTIFICATION)),
            Map.entry(AppEvent.UserBlocked.class, Ownership.of(HandlerOwner.METRICS)),
            Map.entry(AppEvent.UserReported.class, Ownership.of(HandlerOwner.ACHIEVEMENT, HandlerOwner.METRICS)));

    @Test
    void everyPermittedEventHasExplicitOwnership() {
        Map<Class<? extends AppEvent>, Set<HandlerOwner>> actualOwners = new HashMap<>();
        capture(HandlerOwner.ACHIEVEMENT, actualOwners, new AchievementEventHandler(noOpAchievementService()));
        capture(HandlerOwner.METRICS, actualOwners, new MetricsEventHandler(newMetricsService()));
        capture(HandlerOwner.NOTIFICATION, actualOwners, new NotificationEventHandler(noOpCommunicationStorage()));

        List<String> missingEntries = new ArrayList<>();
        List<String> mismatches = new ArrayList<>();

        for (Class<?> permittedSubtype : AppEvent.class.getPermittedSubclasses()) {
            Class<? extends AppEvent> eventType = permittedSubtype.asSubclass(AppEvent.class);
            Ownership expected = OWNERSHIP.get(eventType);
            assertNotNull(expected, "Missing ownership entry for " + eventType.getSimpleName());

            Set<HandlerOwner> actual = actualOwners.getOrDefault(eventType, Set.of());
            if (!actual.equals(expected.owners())) {
                mismatches.add(eventType.getSimpleName() + " expected=" + expected.owners() + " actual=" + actual);
            }

            if (expected.owners().isEmpty()) {
                if (expected.reason().isBlank()) {
                    missingEntries.add(eventType.getSimpleName() + " has NO_OP ownership but no reason");
                }
                assertTrue(actual.isEmpty(), eventType.getSimpleName() + " should remain unowned, but got " + actual);
            }
        }

        assertTrue(missingEntries.isEmpty(), String.join("\n", missingEntries));
        assertTrue(mismatches.isEmpty(), String.join("\n", mismatches));
    }

    private void capture(
            HandlerOwner owner, Map<Class<? extends AppEvent>, Set<HandlerOwner>> actualOwners, Object handler) {
        RecordingBus bus = new RecordingBus(owner, actualOwners);
        if (handler instanceof AchievementEventHandler achievementEventHandler) {
            achievementEventHandler.register(bus);
            return;
        }
        if (handler instanceof MetricsEventHandler metricsEventHandler) {
            metricsEventHandler.register(bus);
            return;
        }
        if (handler instanceof NotificationEventHandler notificationEventHandler) {
            notificationEventHandler.register(bus);
            return;
        }
        throw new IllegalArgumentException("Unsupported handler: " + handler.getClass());
    }

    private static AchievementService noOpAchievementService() {
        return new AchievementService() {
            @Override
            public List<datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement> checkAndUnlock(
                    UUID userId) {
                return List.of();
            }

            @Override
            public List<datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement> getUnlocked(UUID userId) {
                return List.of();
            }

            @Override
            public List<AchievementService.AchievementProgress> getProgress(UUID userId) {
                return List.of();
            }

            @Override
            public Map<
                            datingapp.core.metrics.EngagementDomain.Achievement.Category,
                            List<AchievementService.AchievementProgress>>
                    getProgressByCategory(UUID userId) {
                return Map.of();
            }

            @Override
            public int countUnlocked(UUID userId) {
                return 0;
            }
        };
    }

    private static ActivityMetricsService newMetricsService() {
        return new ActivityMetricsService(
                new TestStorages.Interactions(),
                new TestStorages.TrustSafety(),
                new TestStorages.Analytics(),
                AppConfig.defaults());
    }

    private static CommunicationStorage noOpCommunicationStorage() {
        return (CommunicationStorage) java.lang.reflect.Proxy.newProxyInstance(
                CommunicationStorage.class.getClassLoader(),
                new Class<?>[] {CommunicationStorage.class},
                (proxy, method, args) -> {
                    if (method.getReturnType().equals(boolean.class)) {
                        return false;
                    }
                    if (method.getReturnType().equals(int.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(long.class)) {
                        return 0L;
                    }
                    if (method.getReturnType().equals(double.class)) {
                        return 0.0d;
                    }
                    return null;
                });
    }

    private static final class RecordingBus implements AppEventBus {
        private final HandlerOwner owner;
        private final Map<Class<? extends AppEvent>, Set<HandlerOwner>> actualOwners;

        private RecordingBus(HandlerOwner owner, Map<Class<? extends AppEvent>, Set<HandlerOwner>> actualOwners) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.actualOwners = Objects.requireNonNull(actualOwners, "actualOwners");
        }

        @Override
        public void publish(AppEvent event) {
            // Not needed for the ownership matrix.
        }

        @Override
        public <T extends AppEvent> void subscribe(Class<T> eventType, AppEventHandler<T> handler) {
            subscribe(eventType, handler, HandlerPolicy.BEST_EFFORT);
        }

        @Override
        public <T extends AppEvent> void subscribe(
                Class<T> eventType, AppEventHandler<T> handler, HandlerPolicy policy) {
            actualOwners
                    .computeIfAbsent(eventType, ignored -> EnumSet.noneOf(HandlerOwner.class))
                    .add(owner);
        }
    }

    private enum HandlerOwner {
        ACHIEVEMENT,
        METRICS,
        NOTIFICATION
    }

    private record Ownership(Set<HandlerOwner> owners, String reason) {
        private Ownership {
            owners = Set.copyOf(owners);
            reason = reason == null ? "" : reason;
        }

        static Ownership of(HandlerOwner... owners) {
            EnumSet<HandlerOwner> set = EnumSet.noneOf(HandlerOwner.class);
            for (HandlerOwner owner : owners) {
                set.add(owner);
            }
            return new Ownership(set, "");
        }

        static Ownership noOp(String reason) {
            return new Ownership(Set.of(), reason);
        }
    }
}
