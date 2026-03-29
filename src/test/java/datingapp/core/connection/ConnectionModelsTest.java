package datingapp.core.connection;

import static org.junit.jupiter.api.Assertions.assertThrows;

import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.FriendRequest.Status;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConnectionModels.FriendRequest")
class ConnectionModelsTest {

    @Test
    @DisplayName("rejects ACCEPTED requests without respondedAt")
    void rejectsAcceptedWithoutRespondedAt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FriendRequest(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Status.ACCEPTED, null));
    }

    @Test
    @DisplayName("rejects DECLINED requests without respondedAt")
    void rejectsDeclinedWithoutRespondedAt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FriendRequest(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Status.DECLINED, null));
    }

    @Test
    @DisplayName("rejects EXPIRED requests without respondedAt")
    void rejectsExpiredWithoutRespondedAt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FriendRequest(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), Status.EXPIRED, null));
    }
}
