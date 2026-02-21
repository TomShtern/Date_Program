package datingapp.core.testutil;

import datingapp.core.AppClock;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Factory for creating test users with sensible defaults. Simplifies test setup
 * and reduces
 * boilerplate code.
 */
public final class TestUserFactory {

    private TestUserFactory() {
        // Utility class
    }

    /**
     * Creates an ACTIVE user with all required matching fields populated (bio,
     * birthDate, gender, interestedIn, photoUrls, location, and pacePreferences).
     *
     * @param name the user's name
     * @return a new active user
     */
    public static User createActiveUser(String name) {
        return createActiveUser(UUID.randomUUID(), name);
    }

    /**
     * Creates an ACTIVE user with all required matching fields populated and a
     * specified ID.
     *
     * @param id   the user's ID
     * @param name the user's name
     * @return a new active user
     */
    public static User createActiveUser(UUID id, String name) {
        return User.StorageBuilder.create(id, name, AppClock.now())
                .state(UserState.ACTIVE)
                .bio("Test bio")
                .birthDate(AppClock.today().minusYears(25))
                .gender(Gender.MALE)
                .interestedIn(java.util.Set.of(Gender.FEMALE))
                .photoUrls(java.util.List.of("http://example.com/photo.jpg"))
                .location(32.0853, 34.7818)
                .hasLocationSet(true)
                .pacePreferences(new datingapp.core.profile.MatchPreferences.PacePreferences(
                        datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency.OFTEN,
                        datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate.FEW_DAYS,
                        datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                        datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference.DEEP_CHAT))
                .build();
    }

    /**
     * Creates a complete user with all essential fields populated. Profile is
     * ACTIVE and ready for
     * matching.
     *
     * @param name the user's name
     * @return a new complete user with bio, location, and preferences
     */
    public static User createCompleteUser(String name) {
        User user = createActiveUser(name);
        user.setBio("Test bio for " + name);
        user.setLocation(32.0853, 34.7818); // Tel Aviv coordinates
        user.setMaxDistanceKm(50, 500);
        user.setAgeRange(18, 99, 18, 120);
        return user;
    }

    /**
     * Creates an INCOMPLETE user (profile not ready for matching).
     *
     * @param name the user's name
     * @return a new incomplete user
     */
    public static User createIncompleteUser(String name) {
        return new User(UUID.randomUUID(), name);
    }

    /**
     * Creates a user with specified ID.
     *
     * @param id   the user's ID
     * @param name the user's name
     * @return a new incomplete user
     */
    public static User createUser(UUID id, String name) {
        return new User(id, name);
    }

    /**
     * Creates a user with specified ID, name, and birthdate.
     *
     * @param id        the user's ID
     * @param name      the user's name
     * @param birthDate the user's birthdate
     * @param gender    the user's gender
     * @return a new incomplete user with birthdate
     */
    public static User createUser(UUID id, String name, LocalDate birthDate, Gender gender) {
        User user = new User(id, name);
        user.setBirthDate(birthDate);
        user.setGender(gender);
        return user;
    }
}
