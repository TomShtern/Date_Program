package datingapp.storage.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.core.Social.Notification;
import datingapp.core.Social.Notification.Type;
import datingapp.storage.StorageException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * JDBI row mapper for Notification records.
 * Maps database rows to Notification domain objects including JSON data deserialization.
 */
public class NotificationMapper implements RowMapper<Notification> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public Notification map(ResultSet rs, StatementContext ctx) throws SQLException {
        var id = MapperHelper.readUuid(rs, "id");
        var userId = MapperHelper.readUuid(rs, "user_id");
        var type = MapperHelper.readEnum(rs, "type", Type.class);
        var title = rs.getString("title");
        var message = rs.getString("message");
        var createdAt = MapperHelper.readInstant(rs, "created_at");
        var isRead = rs.getBoolean("is_read");
        var data = fromJson(rs.getString("data_json"));

        return new Notification(id, userId, type, title, message, createdAt, isRead, data);
    }

    private Map<String, String> fromJson(String json) {
        if (json == null) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new StorageException("Failed to deserialize notification data", e);
        }
    }
}
