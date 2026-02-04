package datingapp.core;

import java.time.LocalDate;
import java.util.Objects;

/** Daily pick payload. */
public record DailyPick(User user, LocalDate date, String reason, boolean alreadySeen) {

    public DailyPick {
        Objects.requireNonNull(user, "user cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
    }
}
