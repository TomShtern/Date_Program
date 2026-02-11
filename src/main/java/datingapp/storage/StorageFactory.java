package datingapp.storage;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.Standout;
import datingapp.core.model.UndoState;
import datingapp.core.service.AchievementService;
import datingapp.core.service.CandidateFinder;
import datingapp.core.service.DailyService;
import datingapp.core.service.MatchQualityService;
import datingapp.core.service.MatchingService;
import datingapp.core.service.MessagingService;
import datingapp.core.service.ProfileCompletionService;
import datingapp.core.service.RelationshipTransitionService;
import datingapp.core.service.SessionService;
import datingapp.core.service.StandoutsService;
import datingapp.core.service.StatsService;
import datingapp.core.service.TrustSafetyService;
import datingapp.core.service.UndoService;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessagingStorage;
import datingapp.core.storage.SocialStorage;
import datingapp.core.storage.StatsStorage;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.core.storage.TrustSafetyStorage;
import datingapp.core.storage.UserStorage;
import datingapp.storage.jdbi.EnumSetJdbiSupport;
import datingapp.storage.jdbi.JdbiLikeStorage;
import datingapp.storage.jdbi.JdbiMatchStorage;
import datingapp.storage.jdbi.JdbiMessagingStorage;
import datingapp.storage.jdbi.JdbiSocialStorage;
import datingapp.storage.jdbi.JdbiStandoutStorage;
import datingapp.storage.jdbi.JdbiStatsStorage;
import datingapp.storage.jdbi.JdbiSwipeSessionStorage;
import datingapp.storage.jdbi.JdbiTransactionExecutor;
import datingapp.storage.jdbi.JdbiTrustSafetyStorage;
import datingapp.storage.jdbi.JdbiUndoStorage;
import datingapp.storage.jdbi.JdbiUserStorageAdapter;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

/**
 * Factory for creating fully-wired {@link ServiceRegistry} instances backed by different storage
 * backends. Extracted from the former {@code ServiceRegistry.Builder} to keep the registry focused
 * on holding references while this factory handles wiring concerns.
 *
 * <p>Extension point: add {@code buildPostgres()} or similar for other backends.
 */
public final class StorageFactory {

    private StorageFactory() {
        // Utility class
    }

    /**
     * Builds a ServiceRegistry with H2 database storage. All storage and service instantiation
     * happens directly here.
     *
     * @param dbManager The H2 database manager
     * @param config Application configuration
     * @return Fully wired ServiceRegistry
     */
    public static ServiceRegistry buildH2(DatabaseManager dbManager, AppConfig config) {
        Objects.requireNonNull(dbManager, "dbManager cannot be null");
        Objects.requireNonNull(config, "config cannot be null");

        // ═══════════════════════════════════════════════════════════════
        // JDBI Setup
        // ═══════════════════════════════════════════════════════════════
        Jdbi jdbi = Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new DatabaseManager.StorageException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new SqlObjectPlugin());

        jdbi.registerArgument(new EnumSetJdbiSupport.EnumSetArgumentFactoryImpl());
        jdbi.registerColumnMapper(new EnumSetJdbiSupport.InterestColumnMapper());
        jdbi.registerColumnMapper(Instant.class, (rs, col, ctx) -> {
            Timestamp ts = rs.getTimestamp(col);
            return ts != null ? ts.toInstant() : null;
        });

        // ═══════════════════════════════════════════════════════════════
        // Storage Instantiation
        // ═══════════════════════════════════════════════════════════════
        UserStorage userStorage = new JdbiUserStorageAdapter(jdbi);
        LikeStorage likeStorage = jdbi.onDemand(JdbiLikeStorage.class);
        MatchStorage matchStorage = jdbi.onDemand(JdbiMatchStorage.class);
        TrustSafetyStorage trustSafetyStorage = jdbi.onDemand(JdbiTrustSafetyStorage.class);
        SwipeSessionStorage sessionStorage = jdbi.onDemand(JdbiSwipeSessionStorage.class);
        StatsStorage statsStorage = jdbi.onDemand(JdbiStatsStorage.class);
        MessagingStorage messagingStorage = jdbi.onDemand(JdbiMessagingStorage.class);
        SocialStorage socialStorage = jdbi.onDemand(JdbiSocialStorage.class);

        UndoState.Storage undoStorage = createUndoStorage(jdbi);

        // ═══════════════════════════════════════════════════════════════
        // Matching Services
        // ═══════════════════════════════════════════════════════════════
        CandidateFinder candidateFinder = new CandidateFinder(userStorage, likeStorage, trustSafetyStorage, config);
        DailyService dailyService =
                new DailyService(userStorage, likeStorage, trustSafetyStorage, statsStorage, candidateFinder, config);
        UndoService undoService = new UndoService(likeStorage, matchStorage, undoStorage, config);

        JdbiTransactionExecutor txExecutor = new JdbiTransactionExecutor(jdbi);
        undoService.setTransactionExecutor(txExecutor);

        SessionService sessionService = new SessionService(sessionStorage, statsStorage, config);
        MatchingService matchingService = MatchingService.builder()
                .likeStorage(likeStorage)
                .matchStorage(matchStorage)
                .userStorage(userStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .sessionService(sessionService)
                .undoService(undoService)
                .dailyService(dailyService)
                .build();
        MatchQualityService matchQualityService = new MatchQualityService(userStorage, likeStorage, config);

        // ═══════════════════════════════════════════════════════════════
        // Messaging Services
        // ═══════════════════════════════════════════════════════════════
        MessagingService messagingService = new MessagingService(messagingStorage, matchStorage, userStorage);
        RelationshipTransitionService relationshipTransitionService =
                new RelationshipTransitionService(matchStorage, socialStorage, messagingStorage);

        // ═══════════════════════════════════════════════════════════════
        // Safety Services
        // ═══════════════════════════════════════════════════════════════
        TrustSafetyService trustSafetyService =
                new TrustSafetyService(trustSafetyStorage, userStorage, matchStorage, config);

        // ═══════════════════════════════════════════════════════════════
        // Stats Services
        // ═══════════════════════════════════════════════════════════════
        ProfileCompletionService profileCompletionService = new ProfileCompletionService(config);
        StatsService statsService = new StatsService(likeStorage, matchStorage, trustSafetyStorage, statsStorage);
        AchievementService achievementService = new AchievementService(
                statsStorage,
                matchStorage,
                likeStorage,
                userStorage,
                trustSafetyStorage,
                profileCompletionService,
                config);

        // ═══════════════════════════════════════════════════════════════
        // Standouts Service
        // ═══════════════════════════════════════════════════════════════
        Standout.Storage standoutStorage = createStandoutStorage(jdbi);
        StandoutsService standoutsService =
                new StandoutsService(userStorage, standoutStorage, candidateFinder, profileCompletionService, config);

        // ═══════════════════════════════════════════════════════════════
        // Build ServiceRegistry
        // ═══════════════════════════════════════════════════════════════
        return new ServiceRegistry(
                config,
                userStorage,
                likeStorage,
                matchStorage,
                trustSafetyStorage,
                sessionStorage,
                statsStorage,
                messagingStorage,
                socialStorage,
                candidateFinder,
                matchingService,
                trustSafetyService,
                sessionService,
                statsService,
                matchQualityService,
                profileCompletionService,
                dailyService,
                undoService,
                achievementService,
                messagingService,
                relationshipTransitionService,
                standoutsService);
    }

    /** Builds a ServiceRegistry with H2 database and default configuration. */
    public static ServiceRegistry buildH2(DatabaseManager dbManager) {
        return buildH2(dbManager, AppConfig.defaults());
    }

    /** Builds an in-memory ServiceRegistry for testing. Uses H2 in-memory mode. */
    public static ServiceRegistry buildInMemory(AppConfig config) {
        return buildH2(DatabaseManager.getInstance(), config);
    }

    /** Creates a {@link Standout.Storage} backed by JDBI, inlining the former adapter. */
    private static Standout.Storage createStandoutStorage(Jdbi jdbi) {
        JdbiStandoutStorage proxy = jdbi.onDemand(JdbiStandoutStorage.class);
        return new Standout.Storage() {
            @Override
            public void saveStandouts(java.util.UUID seekerId, List<Standout> standouts, java.time.LocalDate date) {
                for (Standout s : standouts) {
                    proxy.upsert(new JdbiStandoutStorage.StandoutBindingHelper(s));
                }
            }

            @Override
            public List<Standout> getStandouts(java.util.UUID seekerId, java.time.LocalDate date) {
                return proxy.getStandouts(seekerId, date);
            }

            @Override
            public void markInteracted(
                    java.util.UUID seekerId, java.util.UUID standoutUserId, java.time.LocalDate date) {
                proxy.markInteracted(seekerId, standoutUserId, date, datingapp.core.AppClock.now());
            }

            @Override
            public int cleanup(java.time.LocalDate before) {
                return proxy.cleanup(before);
            }
        };
    }

    /** Creates an {@link UndoState.Storage} backed by JDBI, inlining the former adapter. */
    private static UndoState.Storage createUndoStorage(Jdbi jdbi) {
        return new UndoState.Storage() {
            @Override
            public void save(UndoState state) {
                jdbi.useExtension(JdbiUndoStorage.class, storage -> {
                    var like = state.like();
                    storage.upsert(
                            state.userId(),
                            like.id(),
                            like.whoLikes(),
                            like.whoGotLiked(),
                            like.direction().name(),
                            like.createdAt(),
                            state.matchId(),
                            state.expiresAt());
                });
            }

            @Override
            public Optional<UndoState> findByUserId(java.util.UUID userId) {
                return jdbi.withExtension(
                        JdbiUndoStorage.class, storage -> Optional.ofNullable(storage.findByUserId(userId)));
            }

            @Override
            public boolean delete(java.util.UUID userId) {
                return jdbi.withExtension(JdbiUndoStorage.class, storage -> storage.deleteByUserId(userId) > 0);
            }

            @Override
            public int deleteExpired(Instant now) {
                return jdbi.withExtension(JdbiUndoStorage.class, storage -> storage.deleteExpired(now));
            }

            @Override
            public List<UndoState> findAll() {
                return jdbi.withExtension(JdbiUndoStorage.class, JdbiUndoStorage::findAll);
            }
        };
    }
}
