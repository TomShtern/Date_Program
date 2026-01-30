package datingapp.storage.jdbi;

import datingapp.core.Achievement;
import datingapp.core.Achievement.UserAchievement;
import datingapp.core.storage.UserAchievementStorage;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for UserAchievement entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(JdbiUserAchievementStorage.Mapper.class)
public interface JdbiUserAchievementStorage extends UserAchievementStorage {

    @SqlUpdate("""
                        MERGE INTO user_achievements (id, user_id, achievement, unlocked_at)
                        KEY (user_id, achievement)
                        VALUES (:id, :userId, :achievement, :unlockedAt)
                        """)
    @Override
    void save(@BindBean UserAchievement achievement);

    @SqlQuery("""
                        SELECT id, user_id, achievement, unlocked_at
                        FROM user_achievements
                        WHERE user_id = :userId
                        ORDER BY unlocked_at DESC
                        """)
    @Override
    List<UserAchievement> getUnlocked(@Bind("userId") UUID userId);

    @SqlQuery("""
                        SELECT COUNT(*) > 0 FROM user_achievements
                        WHERE user_id = :userId AND achievement = :achievement
                        """)
    @Override
    boolean hasAchievement(@Bind("userId") UUID userId, @Bind("achievement") Achievement achievement);

    @SqlQuery("SELECT COUNT(*) FROM user_achievements WHERE user_id = :userId")
    @Override
    int countUnlocked(@Bind("userId") UUID userId);

    /**
     * Row mapper for UserAchievement records - inlined from former
     * UserAchievementMapper class.
     */
    class Mapper implements RowMapper<UserAchievement> {
        @Override
        public UserAchievement map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = MapperHelper.readUuid(rs, "id");
            var userId = MapperHelper.readUuid(rs, "user_id");
            var achievement = MapperHelper.readEnum(rs, "achievement", Achievement.class);
            var unlockedAt = MapperHelper.readInstant(rs, "unlocked_at");

            return UserAchievement.of(id, userId, achievement, unlockedAt);
        }
    }
}
