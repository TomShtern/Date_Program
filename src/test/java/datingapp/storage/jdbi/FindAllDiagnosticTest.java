package datingapp.storage.jdbi;

import static org.junit.jupiter.api.Assertions.fail;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import datingapp.core.model.User;
import java.util.List;
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
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:postgresql://localhost:55432/datingapp");
        hikari.setUsername("datingapp");
        hikari.setPassword("datingapp");
        hikari.setMaximumPoolSize(2);

        try (HikariDataSource ds = new HikariDataSource(hikari)) {
            Jdbi jdbi = Jdbi.create(ds);
            jdbi.installPlugin(new SqlObjectPlugin());
            JdbiTypeCodecs.registerInstantCodec(jdbi);

            JdbiUserStorage storage = new JdbiUserStorage(jdbi);

            List<User> users = storage.findAll();
            System.out.println("SUCCESS: findAll returned " + users.size() + " users");
            for (User u : users) {
                System.out.println("  - " + u.getName() + " (" + u.getState() + ")");
            }
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            fail("findAll() threw: " + e);
        }
    }
}
