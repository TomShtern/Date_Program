package datingapp.storage.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MigrationRunner metadata portability")
class MigrationRunnerMetadataTest {

    @Test
    @DisplayName("metadataIdentifier should respect uppercase metadata stores")
    void metadataIdentifierUsesUppercaseWhenMetadataStoresUppercaseIdentifiers() {
        assertEquals("MESSAGES", MigrationRunner.metadataIdentifier("messages", true, false));
    }

    @Test
    @DisplayName("metadataIdentifier should respect lowercase metadata stores")
    void metadataIdentifierUsesLowercaseWhenMetadataStoresLowercaseIdentifiers() {
        assertEquals("messages", MigrationRunner.metadataIdentifier("MESSAGES", false, true));
    }

    @Test
    @DisplayName("schema initializer uses PostgreSQL-safe float type token")
    void schemaInitializerUsesPostgresqlSafeFloatTypeToken() {
        assertEquals("DOUBLE PRECISION", SchemaInitializer.FLOAT64_SQL_TYPE);
    }
}
