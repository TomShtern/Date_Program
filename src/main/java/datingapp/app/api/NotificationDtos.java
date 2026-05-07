package datingapp.app.api;

import datingapp.core.connection.ConnectionModels.Notification;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class NotificationDtos {
    private NotificationDtos() {}

    /** Notification DTO. */
    static record NotificationDto(
            UUID id,
            String type,
            String title,
            String message,
            Instant createdAt,
            boolean isRead,
            Map<String, String> data) {
        NotificationDto {
            data = filterNullEntries(data);
        }

        static NotificationDto from(Notification notification) {
            return new NotificationDto(
                    notification.id(),
                    notification.type().name(),
                    notification.title(),
                    notification.message(),
                    notification.createdAt(),
                    notification.isRead(),
                    notification.data());
        }
    }

    static <K, V> Map<K, V> filterNullEntries(Map<K, V> source) {
        if (source == null) {
            return Map.of();
        }
        Map<K, V> clean = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                clean.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(clean);
    }

    /** Mark-all-notifications-read response. */
    static record MarkAllNotificationsReadResponse(int updatedCount) {}
}
