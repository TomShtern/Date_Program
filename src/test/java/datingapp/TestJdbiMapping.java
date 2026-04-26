package datingapp;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.User;
import datingapp.storage.DatabaseDialect;
import datingapp.storage.jdbi.JdbiTypeCodecs;
import datingapp.storage.jdbi.JdbiUserStorage;
import datingapp.support.LivePostgresqlTestConfig;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.Test;

class TestJdbiMapping {
    @Test
    void test() throws Exception {
        LivePostgresqlTestConfig.assumeConfigured();
        try (Connection conn = LivePostgresqlTestConfig.openConnection()) {
            LivePostgresqlTestConfig.initializeSchema(conn);
            Jdbi jdbi = Jdbi.create(conn).installPlugin(new SqlObjectPlugin());
            JdbiTypeCodecs.registerInstantCodec(jdbi);
            jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
            jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());

            JdbiUserStorage storage = new JdbiUserStorage(jdbi, DatabaseDialect.POSTGRESQL);
            List<User> users = storage.findAll();
            assertNotNull(users, "findAll() should return a non-null user list");
            assertTrue(users.stream().allMatch(Objects::nonNull), "findAll() should not return null users");
            assertTrue(users.stream().allMatch(user -> user.getState() != null), "Mapped users should include a state");
            assertTrue(
                    users.stream()
                            .allMatch(user ->
                                    user.getName() != null && !user.getName().isBlank()),
                    "Mapped users should include a non-blank name");
        }
    }
}
