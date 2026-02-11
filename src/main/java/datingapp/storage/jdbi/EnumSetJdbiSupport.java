package datingapp.storage.jdbi;

import datingapp.core.model.Preferences;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consolidated JDBI support for EnumSet serialization.
 * Contains both the column mapper (DB → EnumSet) and argument factory (EnumSet → DB).
 */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class EnumSetJdbiSupport {

    private EnumSetJdbiSupport() {}

    /**
     * JDBI column mapper for EnumSet types.
     * Converts comma-separated string from database back to EnumSet.
     *
     * Currently supports Interest EnumSet. Can be extended for other enum types.
     */
    public static class InterestColumnMapper implements ColumnMapper<Set<Preferences.Interest>> {

        private static final Logger logger = LoggerFactory.getLogger(InterestColumnMapper.class);

        @Override
        public Set<Preferences.Interest> map(ResultSet rs, int columnNumber, StatementContext ctx) throws SQLException {
            String csv = rs.getString(columnNumber);
            if (csv == null || csv.isBlank()) {
                return EnumSet.noneOf(Preferences.Interest.class);
            }

            Set<Preferences.Interest> result = EnumSet.noneOf(Preferences.Interest.class);
            for (String name : csv.split(",")) {
                try {
                    result.add(Preferences.Interest.valueOf(name.trim()));
                } catch (IllegalArgumentException e) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Skipping invalid interest value '{}' from database", name, e);
                    }
                }
            }
            return result;
        }
    }

    /**
     * JDBI argument factory for EnumSet types.
     * Converts EnumSet to comma-separated string for database storage.
     */
    public static class EnumSetArgumentFactoryImpl implements ArgumentFactory {

        @Override
        public Optional<Argument> build(Type type, Object value, ConfigRegistry config) {
            if (value instanceof Set<?> set) {
                if (set.isEmpty()) {
                    // Store empty sets as NULL
                    return Optional.of((pos, stmt, ctx) -> stmt.setNull(pos, Types.VARCHAR));
                }

                if (!set.stream().allMatch(e -> e instanceof Enum<?>)) {
                    return Optional.empty();
                }

                String csv = set.stream().map(e -> ((Enum<?>) e).name()).collect(Collectors.joining(","));

                return Optional.of((pos, stmt, ctx) -> stmt.setString(pos, csv));
            }
            return Optional.empty();
        }
    }
}
