package datingapp.core.time;

import datingapp.core.AppClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Default implementation of {@link TimePolicy} backed by {@link AppClock}. */
public final class DefaultTimePolicy implements TimePolicy {

    private final ZoneId userZone;

    public DefaultTimePolicy(ZoneId userZone) {
        this.userZone = Objects.requireNonNull(userZone, "userZone cannot be null");
    }

    @Override
    public Instant now() {
        return AppClock.now();
    }

    @Override
    public LocalDate today() {
        return AppClock.today(userZone);
    }

    @Override
    public ZoneId userZone() {
        return userZone;
    }

    @Override
    public DateTimeFormatter withUserZone(DateTimeFormatter formatter) {
        return formatter.withZone(userZone);
    }
}
