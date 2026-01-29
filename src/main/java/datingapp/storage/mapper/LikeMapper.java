package datingapp.storage.mapper;

import datingapp.core.UserInteractions.Like;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for Like records.
 * Maps database rows to Like domain objects.
 */
public class LikeMapper implements RowMapper<Like> {

    @Override
    public Like map(ResultSet rs, StatementContext ctx) throws SQLException {
        var id = MapperHelper.readUuid(rs, "id");
        var whoLikes = MapperHelper.readUuid(rs, "who_likes");
        var whoGotLiked = MapperHelper.readUuid(rs, "who_got_liked");
        var direction = MapperHelper.readEnum(rs, "direction", Like.Direction.class);
        var createdAt = MapperHelper.readInstant(rs, "created_at");

        return new Like(id, whoLikes, whoGotLiked, direction, createdAt);
    }
}
