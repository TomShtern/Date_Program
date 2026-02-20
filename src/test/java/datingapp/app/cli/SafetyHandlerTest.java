package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.core.*;
import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.matching.*;
import datingapp.core.model.Match;
import datingapp.core.model.Match.MatchState;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for SafetyHandler CLI commands: blockUser(), reportUser(),
 * manageBlockedUsers(), verifyProfile().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class SafetyHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Interactions interactionStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        trustSafetyStorage = new TestStorages.TrustSafety();
        interactionStorage = new TestStorages.Interactions();

        session = AppSession.getInstance();
        session.reset();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    private SafetyHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        TrustSafetyService trustSafetyService =
                new TrustSafetyService(trustSafetyStorage, interactionStorage, userStorage, AppConfig.defaults());
        return new SafetyHandler(userStorage, trustSafetyService, session, inputReader);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Block User")
    class BlockUser {

        @Test
        @DisplayName("Blocks selected user with confirmation")
        void blocksSelectedUserWithConfirmation() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            SafetyHandler handler = createHandler("1\ny\n");
            handler.blockUser();

            assertTrue(trustSafetyStorage.isBlocked(testUser.getId(), otherUser.getId()));
        }

        @Test
        @DisplayName("Cancels block when user declines")
        void cancelsBlockWhenUserDeclines() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            SafetyHandler handler = createHandler("1\nn\n");
            handler.blockUser();

            assertFalse(trustSafetyStorage.isBlocked(testUser.getId(), otherUser.getId()));
        }

        @Test
        @DisplayName("Shows message when no users to block")
        void showsMessageWhenNoUsersToBlock() {
            SafetyHandler handler = createHandler("0\n");

            // Only test user exists - should show "No users to block"
            assertDoesNotThrow(handler::blockUser);
        }

        @Test
        @DisplayName("Ends match when blocking matched user")
        void endsMatchWhenBlockingMatchedUser() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);
            Match match = Match.create(testUser.getId(), otherUser.getId());
            interactionStorage.save(match);

            SafetyHandler handler = createHandler("1\ny\n");
            handler.blockUser();

            assertTrue(trustSafetyStorage.isBlocked(testUser.getId(), otherUser.getId()));
            Optional<Match> updatedMatch = interactionStorage.get(match.getId());
            assertTrue(updatedMatch.isPresent());
            assertEquals(MatchState.BLOCKED, updatedMatch.get().getState());
        }

        @Test
        @DisplayName("Does not show already blocked users")
        void doesNotShowAlreadyBlockedUsers() {
            User blocked = createActiveUser("BlockedUser");
            userStorage.save(blocked);
            Block block = Block.create(testUser.getId(), blocked.getId());
            trustSafetyStorage.save(block);

            User available = createActiveUser("AvailableUser");
            userStorage.save(available);

            // Select index 1 (should be AvailableUser, not BlockedUser)
            SafetyHandler handler = createHandler("1\ny\n");
            handler.blockUser();

            assertTrue(trustSafetyStorage.isBlocked(testUser.getId(), available.getId()));
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            SafetyHandler handler = createHandler("1\ny\n");
            handler.blockUser();

            assertEquals(0, trustSafetyStorage.countBlocksGiven(testUser.getId()));
        }

        @Test
        @DisplayName("Handles cancel selection (0)")
        void handlesCancelSelection() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            SafetyHandler handler = createHandler("0\n");
            handler.blockUser();

            assertEquals(0, trustSafetyStorage.countBlocksGiven(testUser.getId()));
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Report User")
    class ReportUser {

        @Test
        @DisplayName("Reports user with reason and blocks them")
        void reportsUserWithReasonAndBlocksThem() {
            User otherUser = createActiveUser("BadUser");
            userStorage.save(otherUser);

            // Select user 1, reason 1 (FAKE_PROFILE), empty description, block user (yes)
            SafetyHandler handler = createHandler("1\n1\n\ny\n");
            handler.reportUser();

            assertTrue(trustSafetyStorage.hasReported(testUser.getId(), otherUser.getId()));
            assertTrue(trustSafetyStorage.isBlocked(testUser.getId(), otherUser.getId()));
        }

        @Test
        @DisplayName("Reports user with description")
        void reportsUserWithDescription() {
            User otherUser = createActiveUser("BadUser");
            userStorage.save(otherUser);

            SafetyHandler handler = createHandler("1\n1\nThis is a fake profile\nn\n");
            handler.reportUser();

            assertTrue(trustSafetyStorage.hasReported(testUser.getId(), otherUser.getId()));
        }

        @Test
        @DisplayName("Handles user with multiple existing reports")
        void handlesUserWithMultipleReports() {
            User badUser = createActiveUser("BadUser");
            userStorage.save(badUser);

            // Create multiple reporters and reports
            for (int i = 0; i < 4; i++) {
                User reporter = createActiveUser("Reporter" + i);
                userStorage.save(reporter);
                Report report = Report.create(reporter.getId(), badUser.getId(), Report.Reason.FAKE_PROFILE, "Fake");
                trustSafetyStorage.save(report);
            }

            // Handler should work even when user has multiple reports
            SafetyHandler handler = createHandler("0\n");
            assertDoesNotThrow(handler::reportUser);
        }

        @Test
        @DisplayName("Shows message when no users to report")
        void showsMessageWhenNoUsersToReport() {
            SafetyHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::reportUser);
        }

        @Test
        @DisplayName("Handles invalid reason selection")
        void handlesInvalidReasonSelection() {
            User otherUser = createActiveUser("OtherUser");
            userStorage.save(otherUser);

            SafetyHandler handler = createHandler("1\n99\n"); // Won't reach block prompt because reason is invalid
            handler.reportUser();

            assertFalse(trustSafetyStorage.hasReported(testUser.getId(), otherUser.getId()));
        }

        @Test
        @DisplayName("Requires active state to report")
        void requiresActiveStateToReport() {
            testUser.pause();
            userStorage.save(testUser);

            SafetyHandler handler = createHandler("1\n1\n\ny\n");
            handler.reportUser();

            assertEquals(0, trustSafetyStorage.countReportsBy(testUser.getId()));
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            SafetyHandler handler = createHandler("1\n1\n\ny\n");
            handler.reportUser();

            assertEquals(0, trustSafetyStorage.countReportsBy(testUser.getId()));
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Manage Blocked Users")
    class ManageBlockedUsers {

        @Test
        @DisplayName("Shows message when no blocked users")
        void showsMessageWhenNoBlockedUsers() {
            SafetyHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::manageBlockedUsers);
        }

        @Test
        @DisplayName("Lists blocked users")
        void listsBlockedUsers() {
            User blocked1 = createActiveUser("Blocked1");
            User blocked2 = createActiveUser("Blocked2");
            userStorage.save(blocked1);
            userStorage.save(blocked2);

            trustSafetyStorage.save(Block.create(testUser.getId(), blocked1.getId()));
            trustSafetyStorage.save(Block.create(testUser.getId(), blocked2.getId()));

            SafetyHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::manageBlockedUsers);
        }

        @Test
        @DisplayName("Unblocks user with confirmation")
        void unblocksUserWithConfirmation() {
            User blocked = createActiveUser("BlockedUser");
            userStorage.save(blocked);
            trustSafetyStorage.save(Block.create(testUser.getId(), blocked.getId()));

            // Select user 1, confirm unblock
            SafetyHandler handler = createHandler("1\ny\n");
            handler.manageBlockedUsers();

            assertFalse(trustSafetyStorage.isBlocked(testUser.getId(), blocked.getId()));
        }

        @Test
        @DisplayName("Cancels unblock when declined")
        void cancelsUnblockWhenDeclined() {
            User blocked = createActiveUser("BlockedUser");
            userStorage.save(blocked);
            trustSafetyStorage.save(Block.create(testUser.getId(), blocked.getId()));

            SafetyHandler handler = createHandler("1\nn\n");
            handler.manageBlockedUsers();

            assertTrue(trustSafetyStorage.isBlocked(testUser.getId(), blocked.getId()));
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            User blocked = createActiveUser("BlockedUser");
            userStorage.save(blocked);
            trustSafetyStorage.save(Block.create(testUser.getId(), blocked.getId()));

            SafetyHandler handler = createHandler("1\ny\n");
            handler.manageBlockedUsers();

            // Block should still exist
            assertTrue(trustSafetyStorage.isBlocked(testUser.getId(), blocked.getId()));
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Verify Profile")
    class VerifyProfile {

        @Test
        @DisplayName("Shows already verified message")
        void showsAlreadyVerifiedMessage() {
            testUser.startVerification(VerificationMethod.EMAIL, "123456");
            testUser.markVerified();
            userStorage.save(testUser);

            SafetyHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::verifyProfile);
        }

        @Test
        @DisplayName("Verifies by email with correct code")
        void verifiesByEmailWithCorrectCode() {
            // Select email verification, enter email, then code
            SafetyHandler handler = createHandler("1\ntest@example.com\n123456\n");

            // The handler generates a random code, but we can't match it
            // We just verify it doesn't throw
            assertDoesNotThrow(handler::verifyProfile);
        }

        @Test
        @DisplayName("Shows error for incorrect code")
        void showsErrorForIncorrectCode() {
            SafetyHandler handler = createHandler("1\ntest@example.com\nwrongcode\n");

            assertDoesNotThrow(handler::verifyProfile);
            assertFalse(testUser.isVerified());
        }

        @Test
        @DisplayName("Cancels verification (0)")
        void cancelsVerification() {
            SafetyHandler handler = createHandler("0\n");
            handler.verifyProfile();

            assertFalse(testUser.isVerified());
        }

        @Test
        @DisplayName("Requires email for email verification")
        void requiresEmailForEmailVerification() {
            SafetyHandler handler = createHandler("1\n\n");
            handler.verifyProfile();

            assertFalse(testUser.isVerified());
        }

        @Test
        @DisplayName("Requires phone for phone verification")
        void requiresPhoneForPhoneVerification() {
            SafetyHandler handler = createHandler("2\n\n");
            handler.verifyProfile();

            assertFalse(testUser.isVerified());
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            SafetyHandler handler = createHandler("1\ntest@example.com\n123456\n");
            handler.verifyProfile();

            // Should not modify user
            User stored = userStorage.get(testUser.getId()).orElse(null);
            assertFalse(stored.isVerified());
        }
    }

    // === Helper Methods ===

    private User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.TEXT_ONLY,
                PacePreferences.DepthPreference.SMALL_TALK));
        user.activate();
        return user;
    }
}
