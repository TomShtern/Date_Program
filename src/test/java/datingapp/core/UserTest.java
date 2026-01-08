package datingapp.core;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for User domain model.
 */
class UserTest {

    @Test
    @DisplayName("New user starts as INCOMPLETE")
    void newUserStartsAsIncomplete() {
        User user = new User(UUID.randomUUID(), "John");
        assertEquals(User.State.INCOMPLETE, user.getState());
    }

    @Test
    @DisplayName("User with complete profile can activate")
    void completeUserCanActivate() {
        User user = createCompleteUser("Alice");

        assertTrue(user.isComplete());
        assertEquals(User.State.INCOMPLETE, user.getState());

        user.activate();
        assertEquals(User.State.ACTIVE, user.getState());
    }

    @Test
    @DisplayName("Incomplete user cannot activate")
    void incompleteUserCannotActivate() {
        User user = new User(UUID.randomUUID(), "Bob");

        assertFalse(user.isComplete());
        assertThrows(IllegalStateException.class, user::activate);
    }

    @Test
    @DisplayName("Active user can pause and unpause")
    void activeUserCanPauseAndUnpause() {
        User user = createCompleteUser("Charlie");
        user.activate();

        user.pause();
        assertEquals(User.State.PAUSED, user.getState());

        user.activate();
        assertEquals(User.State.ACTIVE, user.getState());
    }

    @Test
    @DisplayName("Banned user cannot activate")
    void bannedUserCannotActivate() {
        User user = createCompleteUser("Dave");
        user.activate();
        user.ban();

        assertEquals(User.State.BANNED, user.getState());
        assertThrows(IllegalStateException.class, user::activate);
    }

    @Test
    @DisplayName("Age is calculated correctly from birth date")
    void ageIsCalculatedCorrectly() {
        User user = new User(UUID.randomUUID(), "Eve");
        user.setBirthDate(LocalDate.now().minusYears(25).minusDays(10));

        assertEquals(25, user.getAge());
    }

    @Test
    @DisplayName("Photo URLs limited to 2")
    void photoUrlsLimitedToTwo() {
        User user = new User(UUID.randomUUID(), "Frank");
        user.addPhotoUrl("photo1.jpg");
        user.addPhotoUrl("photo2.jpg");

        assertThrows(IllegalArgumentException.class, () -> user.addPhotoUrl("photo3.jpg"));
    }

    private User createCompleteUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("A great person");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(User.Gender.MALE);
        user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
        user.setLocation(32.0, 34.0);
        user.setMaxDistanceKm(50);
        user.setAgeRange(20, 40);
        user.addPhotoUrl("photo.jpg");
        return user;
    }
}
