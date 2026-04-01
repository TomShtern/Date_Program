package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.ServiceRegistry;
import datingapp.core.model.User;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StorageFactory.buildInMemory")
class StorageFactoryInMemoryTest {

    private static final String PROFILE_PROPERTY = "datingapp.db.profile";

    @BeforeEach
    void setUp() {
        System.setProperty(PROFILE_PROPERTY, "test");
        DatabaseManager.resetInstance();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(PROFILE_PROPERTY);
        DatabaseManager.resetInstance();
    }

    @Test
    @DisplayName("buildInMemory creates isolated databases for separate registries")
    void buildInMemoryCreatesIsolatedDatabasesForSeparateRegistries() {
        DatabaseManager.setJdbcUrl("jdbc:h2:./target/storage-factory-global-" + UUID.randomUUID());

        ServiceRegistry first = StorageFactory.buildInMemory(AppConfig.defaults());
        first.getUserStorage().save(new User(UUID.randomUUID(), "First Registry User"));
        assertEquals(1, first.getUserStorage().findAll().size());

        ServiceRegistry second = StorageFactory.buildInMemory(AppConfig.defaults());

        assertTrue(
                second.getUserStorage().findAll().isEmpty(),
                "second in-memory registry should not see first registry data");
    }
}
