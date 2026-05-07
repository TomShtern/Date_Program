package datingapp.app.api;

import datingapp.core.connection.ConnectionModels.Notification;
import java.time.Instant;
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
            data = data == null ? Map.of() : Map.copyOf(data);
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

    /** Mark-all-notifications-read response. */
    static record MarkAllNotificationsReadResponse(int updatedCount) {}
}
