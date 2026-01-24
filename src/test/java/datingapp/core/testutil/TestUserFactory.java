package datingapp.core.testutil;

import datingapp.core.User;
import datingapp.core.User.Gender;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Factory for creating test users with sensible defaults. Simplifies test setup and reduces
 * boilerplate code.
 */
public final class TestUserFactory {

    private TestUserFactory() {
        // Utility class
    }

    /**
     * Creates an ACTIVE user with minimal required fields. Uses default coordinates (Tel Aviv).
     *
     * @param name the user's name
     * @return a new active user
     */
    public static User createActiveUser(String name) {
        return createActiveUser(UUID.randomUUID(), name);
    }

    /**
     * Creates an ACTIVE user with specified ID.
     *
     * @param id the user's ID
     * @param name the user's name
     * @return a new active user
     */
    public static User createActiveUser(UUID id, String name) {
        User user = new User(id, name);
        user.activate(); // State: INCOMPLETE → ACTIVE
        return user;
    }

    /**
     * Creates a complete user with all essential fields populated. Profile is ACTIVE and ready for
     * matching.
     *
     * @param name the user's name
     * @return a new complete user with bio, location, and preferences
     */
    public static User createCompleteUser(String name) {
        User user = createActiveUser(name);
        user.setBio("Test bio for " + name);
        user.setLocation(32.0853, 34.7818); // Tel Aviv coordinates
        user.setMaxDistanceKm(50);
        user.setAgeRange(18, 99);
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
     * @param id the user's ID
     * @param name the user's name
     * @return a new incomplete user
     */
    public static User createUser(UUID id, String name) {
        return new User(id, name);
    }

    /**
     * Creates a user with specified ID, name, and birthdate.
     *
     * @param id the user's ID
     * @param name the user's name
     * @param birthDate the user's birthdate
     * @param gender the user's gender
     * @return a new incomplete user with birthdate
     */
    public static User createUser(UUID id, String name, LocalDate birthDate, Gender gender) {
        User user = new User(id, name);
        user.setBirthDate(birthDate);
        user.setGender(gender);
        return user;
    }
}
