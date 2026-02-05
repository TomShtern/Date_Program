package datingapp.storage.jdbi;

import java.lang.reflect.Type;
import java.sql.Types;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.jdbi.v3.core.config.ConfigRegistry;

/**
 * JDBI argument factory for EnumSet types.
 * Converts EnumSet to comma-separated string for database storage.
 */
public class EnumSetArgumentFactory implements ArgumentFactory {

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
