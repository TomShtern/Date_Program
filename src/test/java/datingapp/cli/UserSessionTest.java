package datingapp.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.CommunicationStyle;
import datingapp.core.DepthPreference;
import datingapp.core.MessagingFrequency;
import datingapp.core.PacePreferences;
import datingapp.core.TimeToFirstDate;
import datingapp.core.User;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for UserSession. */
class UserSessionTest {

    private UserSession userSession;

    @SuppressWarnings("unused") // JUnit 5 invokes via reflection
    @BeforeEach
    void setUp() {
        userSession = new UserSession();
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("Session State Management")
    class SessionState {

        @Test
        @DisplayName("New session has no current user")
        void newSessionIsEmpty() {
            assertNull(userSession.getCurrentUser(), "New session should have no user");
            assertFalse(userSession.isLoggedIn(), "Should not be logged in");
            assertFalse(userSession.isActive(), "Should not be active");
        }

        @Test
        @DisplayName("Can set and retrieve current user")
        void setAndGetCurrentUser() {
            User user = new User(UUID.randomUUID(), "Alice");
            userSession.setCurrentUser(user);

            assertEquals(user, userSession.getCurrentUser(), "Should return set user");
            assertTrue(userSession.isLoggedIn(), "Should be logged in");
        }

        @Test
        @DisplayName("Can clear current user by setting null")
        void clearCurrentUser() {
            User user = new User(UUID.randomUUID(), "Bob");
            userSession.setCurrentUser(user);
            assertTrue(userSession.isLoggedIn());

            userSession.setCurrentUser(null);
            assertFalse(userSession.isLoggedIn(), "Should no longer be logged in");
            assertNull(userSession.getCurrentUser());
        }
    }

    @SuppressWarnings("unused") // JUnit 5 discovers via reflection
    @Nested
    @DisplayName("Login State Checks")
    class LoginState {

        @Test
        @DisplayName("isLoggedIn returns true only when user is set")
        void isLoggedInCheck() {
            assertFalse(userSession.isLoggedIn());

            User user = new User(UUID.randomUUID(), "Charlie");
            userSession.setCurrentUser(user);
            assertTrue(userSession.isLoggedIn());
        }

        @Test
        @DisplayName("isActive returns false for INCOMPLETE user")
        void incompleteUserNotActive() {
            User user = new User(UUID.randomUUID(), "Dave");
            // User is INCOMPLETE by default
            assertEquals(User.State.INCOMPLETE, user.getState());

            userSession.setCurrentUser(user);
            assertTrue(userSession.isLoggedIn(), "Should be logged in");
            assertFalse(userSession.isActive(), "INCOMPLETE user should not be active");
        }

        @Test
        @DisplayName("isActive returns true for ACTIVE user")
        void activeUserIsActive() {
            User user = createCompleteUser("Eve");
            user.activate(); // State becomes ACTIVE
            assertEquals(User.State.ACTIVE, user.getState());

            userSession.setCurrentUser(user);
            assertTrue(userSession.isLoggedIn(), "Should be logged in");
            assertTrue(userSession.isActive(), "ACTIVE user should be active");
        }

        @Test
        @DisplayName("isActive returns false for PAUSED user")
        void pausedUserNotActive() {
            User user = createCompleteUser("Frank");
            user.activate();
            user.pause(); // State becomes PAUSED
            assertEquals(User.State.PAUSED, user.getState());

            userSession.setCurrentUser(user);
            assertTrue(userSession.isLoggedIn(), "Should be logged in");
            assertFalse(userSession.isActive(), "PAUSED user should not be active");
        }

        @Test
        @DisplayName("isActive returns false for BANNED user")
        void bannedUserNotActive() {
            User user = createCompleteUser("Grace");
            user.activate();
            user.ban(); // State becomes BANNED
            assertEquals(User.State.BANNED, user.getState());

            userSession.setCurrentUser(user);
            assertTrue(userSession.isLoggedIn(), "Should be logged in");
            assertFalse(userSession.isActive(), "BANNED user should not be active");
        }

        @Test
        @DisplayName("isActive returns false when no user is logged in")
        void noUserNotActive() {
            assertFalse(userSession.isActive(), "No user means not active");
        }
    }

    // === Helper Methods ===

    private User createCompleteUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(User.Gender.MALE);
        user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
        user.setLocation(32.0, 34.0);
        user.setMaxDistanceKm(50);
        user.setAgeRange(20, 40);
        user.addPhotoUrl("photo.jpg");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        return user;
    }
}
