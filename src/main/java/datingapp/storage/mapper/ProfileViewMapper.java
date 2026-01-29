package datingapp.storage.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for profile view records.
 * Maps database rows to a simple tuple containing view data.
 */
public class ProfileViewMapper implements RowMapper<ProfileViewMapper.ProfileView> {

    /**
     * Simple record to hold profile view data from the database.
     * Internal to the mapper, not exposed to core domain.
     */
    record ProfileView(long id, UUID viewerId, UUID viewedId, Instant viewedAt) {}

    @Override
    public ProfileView map(ResultSet rs, StatementContext ctx) throws SQLException {
        long id = rs.getLong("id");
        UUID viewerId = MapperHelper.readUuid(rs, "viewer_id");
        UUID viewedId = MapperHelper.readUuid(rs, "viewed_id");
        Instant viewedAt = MapperHelper.readInstant(rs, "viewed_at");

        return new ProfileView(id, viewerId, viewedId, viewedAt);
    }
}
