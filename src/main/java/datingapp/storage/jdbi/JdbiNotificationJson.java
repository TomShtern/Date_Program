package datingapp.storage.jdbi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import datingapp.storage.DatabaseManager.StorageException;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/** Shared notification JSON mapping for JDBI storage adapters. */
final class JdbiNotificationJson {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};

    private JdbiNotificationJson() {}

    @Nullable
    static String write(@Nullable Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            throw new StorageException("Failed to serialize notification data", exception);
        }
    }

    static Map<String, String> read(@Nullable String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, STRING_MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new StorageException("Failed to deserialize notification data", exception);
        }
    }
}
