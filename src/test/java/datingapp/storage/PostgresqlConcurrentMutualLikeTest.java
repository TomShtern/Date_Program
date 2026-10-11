package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.MatchingService.RecordLikeOutcome;
import datingapp.core.testutil.TestUserFactory;
import datingapp.support.LivePostgresqlTestConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Two users liking each other at the same instant must both succeed and end up matched. Before the
 * pair-ordered lock, each request held its own user row and then needed the other user's row for the
 * like's foreign-key check, which PostgreSQL resolves by aborting one of them with a deadlock.
 */
@Timeout(120)
@DisplayName("PostgreSQL concurrent mutual likes")
class PostgresqlConcurrentMutualLikeTest {

    private static final String URL_PROPERTY = "datingapp.pgtest.url";
    private static final String USERNAME_PROPERTY = "datingapp.pgtest.username";
    private static final String PASSWORD_PROPERTY = "datingapp.pgtest.password";
    private static final String DB_PASSWORD_PROPERTY = "datingapp.db.password";
    private static final String DB_PROFILE_PROPERTY = "datingapp.db.profile";
    private static final int ROUNDS = 10;

    private final Set<UUID> createdUserIds = new LinkedHashSet<>();
    private ServiceRegistry servicesUnderTest;

    @BeforeEach
    void setUp() {
        LivePostgresqlTestConfig.assumeConfigured();
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(DB_PROFILE_PROPERTY);
        var pg = LivePostgresqlTestConfig.requireConfig();
        System.setProperty(URL_PROPERTY, pg.jdbcUrl());
        System.setProperty(USERNAME_PROPERTY, pg.username());
        System.setProperty(PASSWORD_PROPERTY, pg.password());
        System.setProperty(DB_PASSWORD_PROPERTY, pg.password());
        createdUserIds.clear();
        servicesUnderTest = null;
    }

    @AfterEach
    void tearDown() {
        if (servicesUnderTest != null) {
            createdUserIds.forEach(userId -> servicesUnderTest.getUserStorage().delete(userId));
        }
        createdUserIds.clear();
        servicesUnderTest = null;
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(URL_PROPERTY);
        System.clearProperty(USERNAME_PROPERTY);
        System.clearProperty(PASSWORD_PROPERTY);
        System.clearProperty(DB_PASSWORD_PROPERTY);
    }

    @Test
    @DisplayName("simultaneous mutual likes never deadlock and always create the match")
    void simultaneousMutualLikesNeverDeadlockAndAlwaysCreateTheMatch() throws Exception {
        AppConfig config = AppConfig.builder()
                .databaseDialect("POSTGRESQL")
                .databaseUrl(System.getProperty(URL_PROPERTY))
                .databaseUsername(System.getProperty(USERNAME_PROPERTY))
                .build();
        ServiceRegistry services = ApplicationStartup.initialize(config);
        servicesUnderTest = services;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                runRound(services, executor, round);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private void runRound(ServiceRegistry services, ExecutorService executor, int round) throws Exception {
        var a = TestUserFactory.createActiveUser(UUID.randomUUID(), "Race-A-" + round);
        var b = TestUserFactory.createActiveUser(UUID.randomUUID(), "Race-B-" + round);
        createdUserIds.add(a.getId());
        createdUserIds.add(b.getId());
        services.getUserStorage().save(a);
        services.getUserStorage().save(b);

        CyclicBarrier barrier = new CyclicBarrier(2);
        List<Future<RecordLikeOutcome>> futures = new ArrayList<>();
        for (UUID[] pair : new UUID[][] {{a.getId(), b.getId()}, {b.getId(), a.getId()}}) {
            futures.add(executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return services.getMatchingService().recordLike(Like.create(pair[0], pair[1], Like.Direction.LIKE));
            }));
        }

        List<RecordLikeOutcome> outcomes = new ArrayList<>();
        for (Future<RecordLikeOutcome> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }

        assertTrue(outcomes.stream().allMatch(RecordLikeOutcome::persisted), "round " + round + ": both likes persist");
        assertEquals(
                1,
                outcomes.stream().filter(o -> o.match().isPresent()).count(),
                "round " + round + ": exactly one of the two requests creates the match");
        assertTrue(services.getInteractionStorage()
                .get(datingapp.core.model.Match.generateId(a.getId(), b.getId()))
                .isPresent());
    }
}
