package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import datingapp.app.bootstrap.ApplicationStartup;
import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.testutil.TestUserFactory;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
@DisplayName("PostgreSQL runtime smoke")
class PostgresqlRuntimeSmokeTest {

    private static final String URL_PROPERTY = "datingapp.pgtest.url";
    private static final String USERNAME_PROPERTY = "datingapp.pgtest.username";
    private static final String PASSWORD_PROPERTY = "datingapp.pgtest.password";
    private static final String DB_PASSWORD_PROPERTY = "datingapp.db.password";
    private static final String DB_PROFILE_PROPERTY = "datingapp.db.profile";

    @BeforeEach
    void setUp() {
        assumeTrue(isConfigured(), "PostgreSQL smoke test requires datingapp.pgtest.* system properties");
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(DB_PROFILE_PROPERTY);
        System.setProperty(DB_PASSWORD_PROPERTY, System.getProperty(PASSWORD_PROPERTY));
    }

    @AfterEach
    void tearDown() {
        ApplicationStartup.reset();
        DatabaseManager.resetInstance();
        System.clearProperty(DB_PROFILE_PROPERTY);
        System.clearProperty(DB_PASSWORD_PROPERTY);
    }

    @Test
    @DisplayName("ApplicationStartup should bootstrap and round-trip core storage flows against PostgreSQL")
    void applicationStartupBootstrapsAndRoundTripsCoreStorageFlowsAgainstPostgresql() {
        AppConfig config = AppConfig.builder()
                .databaseDialect("POSTGRESQL")
                .databaseUrl(System.getProperty(URL_PROPERTY))
                .databaseUsername(System.getProperty(USERNAME_PROPERTY))
                .build();

        ServiceRegistry services = ApplicationStartup.initialize(config);

        verifyRoundTripStorageFlows(services);
    }

    @Test
    @DisplayName("ApplicationStartup.load should use the default PostgreSQL runtime path")
    void applicationStartupLoadUsesDefaultPostgresqlRuntimePath() {
        ServiceRegistry services = ApplicationStartup.initialize(ApplicationStartup.load());

        assertEquals("POSTGRESQL", services.getConfig().storage().databaseDialect());
        assertEquals(
                "jdbc:postgresql://localhost:55432/datingapp",
                services.getConfig().storage().databaseUrl());

        verifyRoundTripStorageFlows(services);
    }

    private static void verifyRoundTripStorageFlows(ServiceRegistry services) {

        var userA = TestUserFactory.createActiveUser(UUID.randomUUID(), "Postgres-A");
        var userB = TestUserFactory.createActiveUser(UUID.randomUUID(), "Postgres-B");
        services.getUserStorage().save(userA);
        services.getUserStorage().save(userB);

        assertTrue(services.getUserStorage().get(userA.getId()).isPresent());
        assertTrue(services.getUserStorage().get(userB.getId()).isPresent());

        var firstLike = services.getInteractionStorage()
                .saveLikeAndMaybeCreateMatch(Like.create(userA.getId(), userB.getId(), Like.Direction.LIKE));
        var secondLike = services.getInteractionStorage()
                .saveLikeAndMaybeCreateMatch(Like.create(userB.getId(), userA.getId(), Like.Direction.LIKE));

        assertTrue(firstLike.likePersisted());
        assertTrue(secondLike.likePersisted());
        assertTrue(secondLike.createdMatch().isPresent());

        Session session = new Session(
                UUID.randomUUID(),
                userA.getId(),
                Instant.parse("2026-04-05T12:00:00Z"),
                Instant.parse("2026-04-05T12:00:15Z"),
                Instant.parse("2026-04-05T12:00:15Z"),
                Session.MatchState.COMPLETED,
                2,
                1,
                1,
                1);
        services.getAnalyticsStorage().saveSession(session);

        assertTrue(services.getAnalyticsStorage().getSession(session.getId()).isPresent());
        assertEquals(
                1,
                services.getAnalyticsStorage()
                        .getSessionAggregates(userA.getId())
                        .totalSessions());
    }

    private static boolean isConfigured() {
        return isNonBlank(System.getProperty(URL_PROPERTY))
                && isNonBlank(System.getProperty(USERNAME_PROPERTY))
                && isNonBlank(System.getProperty(PASSWORD_PROPERTY));
    }

    private static boolean isNonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
