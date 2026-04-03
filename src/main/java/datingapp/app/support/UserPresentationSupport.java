package datingapp.app.support;

import datingapp.core.model.User;
import java.time.ZoneId;
import java.util.Objects;

/** Shared presentation helpers for repeated user display transformations. */
public final class UserPresentationSupport {

    private UserPresentationSupport() {}

    public static int safeAge(User user, ZoneId userTimeZone) {
        if (user == null || userTimeZone == null) {
            return 0;
        }
        return user.getAge(userTimeZone).orElse(0);
    }

    public static String fallbackBio(User user, String fallbackText, int maxLength) {
        Objects.requireNonNull(fallbackText, "fallbackText cannot be null");
        if (user == null) {
            return fallbackText;
        }

        String bio = user.getBio();
        if (bio == null || bio.isBlank()) {
            return fallbackText;
        }

        String trimmed = bio.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }

        return trimmed.substring(0, Math.max(0, maxLength)).trim() + "...";
    }
}
