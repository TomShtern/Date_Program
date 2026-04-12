package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("StorageFactory.buildSqlDatabase")
class StorageFactorySqlDatabaseTest {

    @Test
    @DisplayName("wraps dialect detection failures in StorageException")
    void buildSqlDatabaseWrapsDialectDetectionFailuresInStorageException() {
        DatabaseManager dbManager = DatabaseManager.createIsolated("jdbc:invalid:dialect-detection");

        try {
            DatabaseManager.StorageException exception = assertThrows(
                    DatabaseManager.StorageException.class,
                    () -> StorageFactory.buildSqlDatabase(dbManager, AppConfig.defaults()));

            String message = exception.getMessage();
            assertTrue(message.contains("detect"), "Expected message to mention 'detect'");
            assertTrue(message.contains("dialect"), "Expected message to mention 'dialect'");
        } finally {
            dbManager.shutdown();
        }
    }
}
