package datingapp.core.matching;

import datingapp.core.model.User;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing the "Daily Pick" feature.
 * Selects a featured profile once per day for each user.
 */
public interface DailyPickService {

    /** Get the daily pick for a user if available. */
    Optional<DailyPick> getDailyPick(User seeker);

    /** Check whether user has viewed today's daily pick. */
    boolean hasViewedDailyPick(UUID userId);

    /** Mark today's daily pick as viewed for a user. */
    void markDailyPickViewed(UUID userId);

    /** Cleanup old daily pick view records (for maintenance). */
    int cleanupOldDailyPickViews(LocalDate before);

    /** Daily pick payload. */
    record DailyPick(User user, LocalDate date, String reason, boolean alreadySeen) {
        public DailyPick {
            Objects.requireNonNull(user, "user cannot be null");
            Objects.requireNonNull(date, "date cannot be null");
            Objects.requireNonNull(reason, "reason cannot be null");
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason cannot be blank");
            }
        }
    }
}
