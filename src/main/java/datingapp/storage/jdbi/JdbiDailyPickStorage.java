package datingapp.storage.jdbi;

import datingapp.core.storage.DailyPickStorage;
import java.time.LocalDate;
import java.util.UUID;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for DailyPickStorage.
 * Uses declarative SQL methods instead of manual JDBC.
 */
public interface JdbiDailyPickStorage extends DailyPickStorage {

    @SqlUpdate("""
            MERGE INTO daily_pick_views (user_id, viewed_date, viewed_at)
            VALUES (:userId, :date, CURRENT_TIMESTAMP)
            """)
    @Override
    void markViewed(@Bind("userId") UUID userId, @Bind("date") LocalDate date);

    @SqlQuery("""
            SELECT COUNT(*) > 0 FROM daily_pick_views
            WHERE user_id = :userId AND viewed_date = :date
            """)
    @Override
    boolean hasViewed(@Bind("userId") UUID userId, @Bind("date") LocalDate date);

    @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_date < :before")
    @Override
    int cleanup(@Bind("before") LocalDate before);
}
