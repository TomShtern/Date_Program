package datingapp.storage.jdbi;

import datingapp.core.Preferences;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Set;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBI column mapper for EnumSet types.
 * Converts comma-separated string from database back to EnumSet.
 *
 * Currently supports Interest EnumSet. Can be extended for other enum types.
 */
public class EnumSetColumnMapper implements ColumnMapper<Set<Preferences.Interest>> {

    private static final Logger logger = LoggerFactory.getLogger(EnumSetColumnMapper.class);

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
