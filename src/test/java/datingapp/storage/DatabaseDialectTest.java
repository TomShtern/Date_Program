package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DatabaseDialect")
class DatabaseDialectTest {

    @Test
    @DisplayName("fromConfig should prefer an explicit configured dialect")
    void fromConfigPrefersConfiguredDialect() {
        assertEquals(
                DatabaseDialect.POSTGRESQL,
                DatabaseDialect.fromConfig("POSTGRESQL", "jdbc:h2:mem:ignored;DB_CLOSE_DELAY=-1"));
    }

    @Test
    @DisplayName("fromConfig should infer dialect from JDBC URL when config is blank")
    void fromConfigInfersDialectFromJdbcUrl() {
        assertEquals(
                DatabaseDialect.POSTGRESQL,
                DatabaseDialect.fromConfig(" ", "jdbc:postgresql://localhost:5432/datingapp"));
        assertEquals(DatabaseDialect.H2, DatabaseDialect.fromConfig(null, "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"));
    }

    @Test
    @DisplayName("fromDatabaseProductName should detect supported engines")
    void fromDatabaseProductNameDetectsSupportedEngines() {
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromDatabaseProductName("PostgreSQL"));
        assertEquals(DatabaseDialect.H2, DatabaseDialect.fromDatabaseProductName("H2"));
    }
}
