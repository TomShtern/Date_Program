package datingapp.app.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UserPresentationSupport")
class UserPresentationSupportTest {

    @Test
    @DisplayName("safeAge returns zero when birth date is missing")
    void safeAgeReturnsZeroWhenBirthDateIsMissing() {
        User user = new User(UUID.randomUUID(), "No Birth Date");

        assertEquals(0, UserPresentationSupport.safeAge(user, ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("safeAge returns the user age when available")
    void safeAgeReturnsTheUserAgeWhenAvailable() {
        User user = new User(UUID.randomUUID(), "Age User");
        user.setBirthDate(AppClock.today().minusYears(30));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));

        assertEquals(30, UserPresentationSupport.safeAge(user, ZoneId.of("UTC")));
    }

    @Test
    @DisplayName("fallbackBio returns default text for blank bios")
    void fallbackBioReturnsDefaultTextForBlankBios() {
        User user = new User(UUID.randomUUID(), "Blank Bio");
        user.setBio("   ");

        assertEquals("No bio yet.", UserPresentationSupport.fallbackBio(user, "No bio yet.", 80));
    }

    @Test
    @DisplayName("fallbackBio trims and truncates long bios")
    void fallbackBioTrimsAndTruncatesLongBios() {
        User user = new User(UUID.randomUUID(), "Long Bio");
        user.setBio(
                "  This is a very long biography that should be shortened when the requested preview length is small.  ");

        assertEquals("This is a very long...", UserPresentationSupport.fallbackBio(user, "No bio yet.", 20));
    }
}
