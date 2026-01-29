package datingapp.storage.mapper;

import datingapp.core.Achievement;
import datingapp.core.Achievement.UserAchievement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for UserAchievement records.
 * Maps database rows to UserAchievement domain objects.
 */
public class UserAchievementMapper implements RowMapper<UserAchievement> {

    @Override
    public UserAchievement map(ResultSet rs, StatementContext ctx) throws SQLException {
        var id = MapperHelper.readUuid(rs, "id");
        var userId = MapperHelper.readUuid(rs, "user_id");
        var achievement = MapperHelper.readEnum(rs, "achievement", Achievement.class);
        var unlockedAt = MapperHelper.readInstant(rs, "unlocked_at");

        return UserAchievement.of(id, userId, achievement, unlockedAt);
    }
}
