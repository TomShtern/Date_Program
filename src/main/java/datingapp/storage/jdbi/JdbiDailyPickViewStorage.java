package datingapp.storage.jdbi;

import datingapp.core.storage.DailyPickViewStorage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/** JDBI implementation for persisting daily pick views. */
public interface JdbiDailyPickViewStorage extends DailyPickViewStorage {

    @SqlUpdate(
            "MERGE INTO daily_pick_views (user_id, viewed_date, viewed_at) KEY (user_id, viewed_date) VALUES (:userId, :date, :at)")
    void saveView(@Bind("userId") UUID userId, @Bind("date") LocalDate date, @Bind("at") Instant at);

    @Override
    default void markAsViewed(UUID userId, LocalDate date) {
        saveView(userId, date, Instant.now());
    }

    @Override
    @SqlQuery("SELECT COUNT(*) > 0 FROM daily_pick_views WHERE user_id = :userId AND viewed_date = :date")
    boolean isViewed(@Bind("userId") UUID userId, @Bind("date") LocalDate date);

    @Override
    @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_date < :before")
    int deleteOlderThan(@Bind("before") LocalDate before);
}
