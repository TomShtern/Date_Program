package datingapp.storage.jdbi;

import datingapp.core.profile.MatchPreferences;
import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consolidated SQL row-reader and codec helpers for JDBI storage mappings. */
@SuppressWarnings("PMD.MissingStaticMethodInNonInstantiatableClass")
public final class JdbiTypeCodecs {

    private JdbiTypeCodecs() {}

    /** Null-safe SQL row readers shared across storage row mappers. */
    public static final class SqlRowReaders {

        private static final Logger logger = LoggerFactory.getLogger(SqlRowReaders.class);

        private SqlRowReaders() {}

        @Nullable
        public static UUID readUuid(ResultSet rs, String column) throws SQLException {
            Object obj = rs.getObject(column);
            if (obj == null) {
                return null;
            }
            if (obj instanceof UUID uuid) {
                return uuid;
            }
            if (obj instanceof String str) {
                try {
                    return UUID.fromString(str);
                } catch (IllegalArgumentException _) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Skipping invalid UUID value '{}' in column '{}'", str, column);
                    }
                    return null;
                }
            }
            throw new SQLException("Unexpected UUID type: " + obj.getClass());
        }

        @Nullable
        public static Instant readInstant(ResultSet rs, String column) throws SQLException {
            Timestamp ts = rs.getTimestamp(column);
            return ts == null ? null : ts.toInstant();
        }

        @Nullable
        public static LocalDate readLocalDate(ResultSet rs, String column) throws SQLException {
            java.sql.Date date = rs.getDate(column);
            return date == null ? null : date.toLocalDate();
        }

        @Nullable
        public static <E extends Enum<E>> E readEnum(ResultSet rs, String column, Class<E> enumType)
                throws SQLException {
            Objects.requireNonNull(enumType, "enumType cannot be null");
            String value = rs.getString(column);
            if (value == null) {
                return null;
            }
            try {
                return Enum.valueOf(enumType, value);
            } catch (IllegalArgumentException _) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Skipping invalid {} value '{}' in column '{}'", enumType.getSimpleName(), value, column);
                }
                return null;
            }
        }

        @Nullable
        public static Integer readInteger(ResultSet rs, String column) throws SQLException {
            int value = rs.getInt(column);
            return rs.wasNull() ? null : value;
        }

        @Nullable
        public static Double readDouble(ResultSet rs, String column) throws SQLException {
            double value = rs.getDouble(column);
            return rs.wasNull() ? null : value;
        }

        public static List<String> readCsvAsList(ResultSet rs, String column) throws SQLException {
            String csv = rs.getString(column);
            if (csv == null || csv.isBlank()) {
                return List.of();
            }
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }

    /** EnumSet SQL codecs used by JDBI argument and column mapping. */
    public static final class EnumSetSqlCodec {

        private EnumSetSqlCodec() {}

        public static class InterestColumnMapper implements ColumnMapper<Set<MatchPreferences.Interest>> {

            private static final Logger logger = LoggerFactory.getLogger(InterestColumnMapper.class);

            @Override
            public Set<MatchPreferences.Interest> map(ResultSet rs, int columnNumber, StatementContext ctx)
                    throws SQLException {
                String csv = rs.getString(columnNumber);
                if (csv == null || csv.isBlank()) {
                    return EnumSet.noneOf(MatchPreferences.Interest.class);
                }

                Set<MatchPreferences.Interest> result = EnumSet.noneOf(MatchPreferences.Interest.class);
                for (String name : csv.split(",")) {
                    try {
                        result.add(MatchPreferences.Interest.valueOf(name.trim()));
                    } catch (IllegalArgumentException e) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Skipping invalid interest value '{}' from database", name, e);
                        }
                    }
                }
                return result;
            }
        }

        public static class EnumSetArgumentFactory implements ArgumentFactory {
            @Override
            public Optional<Argument> build(Type type, Object value, ConfigRegistry config) {
                if (value instanceof Set<?> set) {
                    if (set.isEmpty()) {
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
}
