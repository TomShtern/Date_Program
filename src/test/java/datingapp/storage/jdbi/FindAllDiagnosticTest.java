package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import datingapp.core.model.User;
import datingapp.support.LivePostgresqlTestConfig;
import java.util.List;
import java.util.Objects;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.Test;

/**
 * Throwaway diagnostic: connects to local PostgreSQL and calls findAll().
 * Run with: mvn -Dtest=FindAllDiagnosticTest test
 */
class FindAllDiagnosticTest {

    @Test
    void findAllAgainstLiveDb() {
        LivePostgresqlTestConfig.assumeConfigured();
        LivePostgresqlTestConfig.ConnectionInfo config = LivePostgresqlTestConfig.requireConfig();

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.jdbcUrl());
        hikari.setUsername(config.username());
        hikari.setPassword(config.password());
        hikari.setMaximumPoolSize(2);

        try (HikariDataSource ds = new HikariDataSource(hikari)) {
            Jdbi jdbi = Jdbi.create(ds);
            jdbi.installPlugin(new SqlObjectPlugin());
            JdbiTypeCodecs.registerInstantCodec(jdbi);

            JdbiUserStorage storage = new JdbiUserStorage(jdbi);

            List<User> users = storage.findAll();
            assertNotNull(users, "findAll() should return a user list");
            assertTrue(users.stream().allMatch(Objects::nonNull), "findAll() should not return null users");
        }
    }
}
