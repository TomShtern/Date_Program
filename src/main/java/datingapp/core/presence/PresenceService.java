package datingapp.core.presence;

import datingapp.core.AppClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Heartbeat presence + typing indicators. Thresholds come from {@code AppConfig} presence config. */
public final class PresenceService {

    public static final Duration ONLINE_THRESHOLD = Duration.ofMinutes(2);
    public static final Duration AWAY_THRESHOLD = Duration.ofMinutes(10);
    public static final Duration TYPING_FRESHNESS = Duration.ofSeconds(10);

    public enum Status {
        ONLINE,
        AWAY,
        OFFLINE,
        UNKNOWN
    }

    private final PresenceStorage storage;
    private final Duration onlineThreshold;
    private final Duration awayThreshold;
    private final Duration typingFreshness;

    public PresenceService(PresenceStorage storage) {
        this(storage, ONLINE_THRESHOLD, AWAY_THRESHOLD, TYPING_FRESHNESS);
    }

    public PresenceService(PresenceStorage storage, datingapp.core.AppConfig.PresenceConfig config) {
        this(
                storage,
                Duration.ofSeconds(Math.max(1, config.onlineSeconds())),
                Duration.ofSeconds(Math.max(1, config.awaySeconds())),
                Duration.ofSeconds(Math.max(1, config.typingFreshSeconds())));
    }

    public PresenceService(
            PresenceStorage storage, Duration onlineThreshold, Duration awayThreshold, Duration typingFreshness) {
        this.storage = Objects.requireNonNull(storage, "storage cannot be null");
        this.onlineThreshold = Objects.requireNonNull(onlineThreshold, "onlineThreshold cannot be null");
        this.awayThreshold = Objects.requireNonNull(awayThreshold, "awayThreshold cannot be null");
        this.typingFreshness = Objects.requireNonNull(typingFreshness, "typingFreshness cannot be null");
    }

    public void heartbeat(UUID userId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Instant now = AppClock.now();
        Optional<PresenceStorage.Heartbeat> existing = storage.get(userId);
        storage.upsert(
                userId, now, existing.map(PresenceStorage.Heartbeat::typingIn).orElse(null), now);
    }

    public void setTyping(UUID userId, String conversationId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Instant now = AppClock.now();
        Optional<PresenceStorage.Heartbeat> existing = storage.get(userId);
        storage.upsert(userId, existing.map(PresenceStorage.Heartbeat::lastSeen).orElse(now), conversationId, now);
    }

    public Status getStatus(UUID userId) {
        Optional<PresenceStorage.Heartbeat> hb = storage.get(userId);
        if (hb.isEmpty() || hb.get().lastSeen() == null) {
            return Status.OFFLINE;
        }
        Duration idle = Duration.between(hb.get().lastSeen(), AppClock.now());
        if (idle.isNegative()) {
            return Status.ONLINE;
        }
        if (idle.compareTo(onlineThreshold) <= 0) {
            return Status.ONLINE;
        }
        if (idle.compareTo(awayThreshold) <= 0) {
            return Status.AWAY;
        }
        return Status.OFFLINE;
    }

    public boolean isTyping(UUID userId, String conversationId) {
        Optional<PresenceStorage.Heartbeat> hb = storage.get(userId);
        if (hb.isEmpty() || hb.get().typingIn() == null || hb.get().updatedAt() == null) {
            return false;
        }
        if (conversationId != null && !conversationId.equals(hb.get().typingIn())) {
            return false;
        }
        return Duration.between(hb.get().updatedAt(), AppClock.now()).compareTo(typingFreshness) <= 0;
    }

    public boolean isTyping(UUID userId) {
        return isTyping(userId, null);
    }

    public void purgeStale() {
        storage.deleteStaleBefore(AppClock.now().minus(Duration.ofHours(24)));
    }
}
