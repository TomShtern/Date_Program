package datingapp.storage.jdbi;

import datingapp.core.Achievement;
import datingapp.core.Achievement.UserAchievement;
import datingapp.core.storage.UserAchievementStorage;
import datingapp.storage.mapper.UserAchievementMapper;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for UserAchievement entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(UserAchievementMapper.class)
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
}
