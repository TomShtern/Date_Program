package datingapp.storage.mapper;

import datingapp.core.UserInteractions.Block;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for Block records.
 * Maps database rows to Block domain objects.
 */
public class BlockMapper implements RowMapper<Block> {

    @Override
    public Block map(ResultSet rs, StatementContext ctx) throws SQLException {
        UUID id = MapperHelper.readUuid(rs, "id");
        UUID blockerId = MapperHelper.readUuid(rs, "blocker_id");
        UUID blockedId = MapperHelper.readUuid(rs, "blocked_id");
        var createdAt = MapperHelper.readInstant(rs, "created_at");

        return new Block(id, blockerId, blockedId, createdAt);
    }
}
