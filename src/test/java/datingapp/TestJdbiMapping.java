package datingapp;

import datingapp.core.model.User;
import datingapp.storage.DatabaseDialect;
import datingapp.storage.jdbi.JdbiTypeCodecs;
import datingapp.storage.jdbi.JdbiUserStorage;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.junit.jupiter.api.Test;

public class TestJdbiMapping {
    @Test
    public void test() throws Exception {
        System.out.println("========================================================================");
        try (Connection conn =
                DriverManager.getConnection("jdbc:postgresql://localhost:55432/datingapp", "datingapp", "datingapp")) {
            Jdbi jdbi = Jdbi.create(conn).installPlugin(new SqlObjectPlugin());
            JdbiTypeCodecs.registerInstantCodec(jdbi);
            jdbi.registerArgument(new JdbiTypeCodecs.EnumSetSqlCodec.EnumSetArgumentFactory());
            jdbi.registerColumnMapper(new JdbiTypeCodecs.EnumSetSqlCodec.InterestColumnMapper());

            JdbiUserStorage storage = new JdbiUserStorage(jdbi, DatabaseDialect.POSTGRESQL);
            List<User> users = storage.findAll();
            System.out.println("JDBI Users size: " + users.size());
            for (int i = 0; i < Math.min(users.size(), 3); i++) {
                System.out.println("User: name=" + users.get(i).getName() + ", state="
                        + users.get(i).getState());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("========================================================================");
    }
}
