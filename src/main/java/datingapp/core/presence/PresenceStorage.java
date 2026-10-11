package datingapp.core.presence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Persistence seam for heartbeat presence. */
public interface PresenceStorage {

    void upsert(UUID userId, Instant lastSeen, String typingIn, Instant updatedAt);

    Optional<Heartbeat> get(UUID userId);

    void deleteStaleBefore(Instant cutoff);

    record Heartbeat(UUID userId, Instant lastSeen, String typingIn, Instant updatedAt) {}
}
