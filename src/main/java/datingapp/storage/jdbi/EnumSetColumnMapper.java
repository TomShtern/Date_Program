package datingapp.storage.jdbi;

import datingapp.core.Preferences;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumSet;
import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI column mapper for EnumSet types.
 * Converts comma-separated string from database back to EnumSet.
 *
 * Currently supports Interest EnumSet. Can be extended for other enum types.
 */
public class EnumSetColumnMapper implements ColumnMapper<EnumSet<Preferences.Interest>> {

    @Override
    public EnumSet<Preferences.Interest> map(ResultSet rs, int columnNumber, StatementContext ctx) throws SQLException {
        String csv = rs.getString(columnNumber);
        if (csv == null || csv.isBlank()) {
            return EnumSet.noneOf(Preferences.Interest.class);
        }

        EnumSet<Preferences.Interest> result = EnumSet.noneOf(Preferences.Interest.class);
        for (String name : csv.split(",")) {
            try {
                result.add(Preferences.Interest.valueOf(name.trim()));
            } catch (IllegalArgumentException e) {
                // Skip invalid enum values from database migration - allows graceful handling of legacy data
            }
        }
        return result;
    }
}
