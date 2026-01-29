package datingapp.storage.mapper;

import datingapp.core.User.ProfileNote;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for ProfileNote records.
 * Maps database rows to ProfileNote domain objects.
 */
public class ProfileNoteMapper implements RowMapper<ProfileNote> {

    @Override
    public ProfileNote map(ResultSet rs, StatementContext ctx) throws SQLException {
        UUID authorId = MapperHelper.readUuid(rs, "author_id");
        UUID subjectId = MapperHelper.readUuid(rs, "subject_id");
        String content = rs.getString("content");
        var createdAt = MapperHelper.readInstant(rs, "created_at");
        var updatedAt = MapperHelper.readInstant(rs, "updated_at");

        return new ProfileNote(authorId, subjectId, content, createdAt, updatedAt);
    }
}
