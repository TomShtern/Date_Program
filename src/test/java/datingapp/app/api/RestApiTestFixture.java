package datingapp.app.api;

import datingapp.app.event.handlers.AchievementEventHandler;
import datingapp.app.event.handlers.MetricsEventHandler;
import datingapp.app.event.handlers.NotificationEventHandler;
import datingapp.app.usecase.auth.AuthTokenService;
import datingapp.app.usecase.auth.AuthUseCases.AuthIdentity;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.matching.Standout;
import datingapp.core.metrics.SwipeState.Undo;
import datingapp.core.model.User;
import datingapp.core.storage.OperationalCommunicationStorage;
import datingapp.core.storage.OperationalInteractionStorage;
import datingapp.core.storage.OperationalUserStorage;
import datingapp.core.testutil.TestServiceRegistryBuilder;
import datingapp.core.testutil.TestStorages;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared REST/API test fixture builder for wiring ServiceRegistry graphs. */
final class RestApiTestFixture {

    private RestApiTestFixture() {}

    static String bearerToken(ServiceRegistry services, User user) {
        return bearerToken(services, user.getId(), user.getEmail());
    }

    static String bearerToken(ServiceRegistry services, UUID userId, String email) {
        String resolvedEmail = email == null || email.isBlank() ? userId + "@example.com" : email;
        String accessToken = new AuthTokenService(services.getConfig().auth())
                .issueAccessToken(new AuthIdentity(userId, resolvedEmail), AppClock.now());
        return "Bearer " + accessToken;
    }

    static Builder builder(
            OperationalUserStorage userStorage,
            OperationalInteractionStorage interactionStorage,
            OperationalCommunicationStorage communicationStorage) {
        return new Builder(userStorage, interactionStorage, communicationStorage);
    }

    static final class Builder {
        private final OperationalUserStorage userStorage;
        private final OperationalInteractionStorage interactionStorage;
        private final OperationalCommunicationStorage communicationStorage;
        private AppConfig config = AppConfig.defaults();
        private TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        private TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        private Standout.Storage standoutStorage = new InMemoryStandoutStorage();
        private Undo.Storage undoStorage = new InMemoryUndoStorage();

        private Builder(
                OperationalUserStorage userStorage,
                OperationalInteractionStorage interactionStorage,
                OperationalCommunicationStorage communicationStorage) {
            this.userStorage = Objects.requireNonNull(userStorage, "userStorage cannot be null");
            this.interactionStorage = Objects.requireNonNull(interactionStorage, "interactionStorage cannot be null");
            this.communicationStorage =
                    Objects.requireNonNull(communicationStorage, "communicationStorage cannot be null");
        }

        Builder config(AppConfig config) {
            this.config = Objects.requireNonNull(config, "config cannot be null");
            return this;
        }

        Builder analyticsStorage(TestStorages.Analytics analyticsStorage) {
            this.analyticsStorage = Objects.requireNonNull(analyticsStorage, "analyticsStorage cannot be null");
            return this;
        }

        Builder trustSafetyStorage(TestStorages.TrustSafety trustSafetyStorage) {
            this.trustSafetyStorage = Objects.requireNonNull(trustSafetyStorage, "trustSafetyStorage cannot be null");
            return this;
        }

        Builder standoutStorage(Standout.Storage standoutStorage) {
            this.standoutStorage = Objects.requireNonNull(standoutStorage, "standoutStorage cannot be null");
            return this;
        }

        Builder undoStorage(Undo.Storage undoStorage) {
            this.undoStorage = Objects.requireNonNull(undoStorage, "undoStorage cannot be null");
            return this;
        }

        ServiceRegistry build() {
            ServiceRegistry services = TestServiceRegistryBuilder.builder(
                            userStorage, interactionStorage, communicationStorage)
                    .config(config)
                    .analyticsStorage(analyticsStorage)
                    .trustSafetyStorage(trustSafetyStorage)
                    .standoutStorage(standoutStorage)
                    .undoStorage(undoStorage)
                    .build();

            new AchievementEventHandler(services.getAchievementService()).register(services.getEventBus());
            new MetricsEventHandler(services.getActivityMetricsService()).register(services.getEventBus());
            new NotificationEventHandler(services.getCommunicationStorage()).register(services.getEventBus());

            return services;
        }
    }

    static final class InMemoryUndoStorage implements Undo.Storage {
        private final Map<UUID, Undo> byUserId = new HashMap<>();

        @Override
        public void save(Undo state) {
            byUserId.put(state.userId(), state);
        }

        @Override
        public Optional<Undo> findByUserId(UUID userId) {
            return Optional.ofNullable(byUserId.get(userId));
        }

        @Override
        public boolean delete(UUID userId) {
            return byUserId.remove(userId) != null;
        }

        @Override
        public int deleteExpired(Instant now) {
            List<UUID> toDelete = new ArrayList<>();
            for (Undo undo : byUserId.values()) {
                if (undo.isExpired(now)) {
                    toDelete.add(undo.userId());
                }
            }
            toDelete.forEach(byUserId::remove);
            return toDelete.size();
        }

        @Override
        public List<Undo> findAll() {
            return List.copyOf(byUserId.values());
        }
    }

    static final class InMemoryStandoutStorage implements Standout.Storage {
        @Override
        public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
            // no-op test storage
        }

        @Override
        public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
            return List.of();
        }

        @Override
        public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
            // no-op test storage
        }

        @Override
        public int cleanup(LocalDate before) {
            return 0;
        }
    }
}
