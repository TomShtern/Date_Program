package datingapp.storage.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JDBI row mapper for daily pick view records.
 * Maps database rows to a simple record containing user ID and viewed date.
 */
public final class DailyPickMapper {

    private DailyPickMapper() {
        /* Utility class with only static methods */
    }

    /**
     * Simple record to represent a daily pick view.
     * Used for tracking which users have viewed their daily pick on which dates.
     */
    public record DailyPickView(UUID userId, LocalDate viewedDate) {
        public DailyPickView {
            java.util.Objects.requireNonNull(userId, "userId cannot be null");
            java.util.Objects.requireNonNull(viewedDate, "viewedDate cannot be null");
        }
    }

    /**
     * Maps a ResultSet row to a DailyPickView record.
     *
     * @param rs the ResultSet positioned at the current row
     * @return a DailyPickView record
     * @throws SQLException if reading from ResultSet fails
     */
    public static DailyPickView map(ResultSet rs) throws SQLException {
        UUID userId = MapperHelper.readUuid(rs, "user_id");
        LocalDate viewedDate = MapperHelper.readLocalDate(rs, "viewed_date");
        return new DailyPickView(userId, viewedDate);
    }
}
