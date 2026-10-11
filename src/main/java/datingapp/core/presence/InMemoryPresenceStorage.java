package datingapp.core.presence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory {@link PresenceStorage} for tests and non-DB runtimes. */
public final class InMemoryPresenceStorage implements PresenceStorage {

    private final ConcurrentHashMap<UUID, Heartbeat> heartbeats = new ConcurrentHashMap<>();

    @Override
    public void upsert(UUID userId, Instant lastSeen, String typingIn, Instant updatedAt) {
        heartbeats.put(
                new UUID(userId.getMostSignificantBits(), userId.getLeastSignificantBits()),
                new Heartbeat(userId, lastSeen, typingIn, updatedAt));
    }

    @Override
    public Optional<Heartbeat> get(UUID userId) {
        return Optional.ofNullable(heartbeats.get(userId));
    }

    @Override
    public void deleteStaleBefore(Instant cutoff) {
        heartbeats
                .values()
                .removeIf(hb -> hb.updatedAt() != null && hb.updatedAt().isBefore(cutoff));
    }
}
