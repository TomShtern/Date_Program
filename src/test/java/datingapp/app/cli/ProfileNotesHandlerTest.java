package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.core.*;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.*;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for profile notes CLI commands: manageNoteFor(), viewAllNotes().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class ProfileNotesHandlerTest {

    private TestStorages.Users userStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();

        session = AppSession.getInstance();
        session.reset();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    private ProfileHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        return new ProfileHandler(userStorage, null, null, new ValidationService(), session, inputReader);
    }

    private void saveNote(UUID authorId, UUID subjectId, String content) {
        ProfileNote note = ProfileNote.create(authorId, subjectId, content);
        userStorage.saveProfileNote(note);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Manage Note For User")
    class ManageNoteFor {

        @Test
        @DisplayName("Shows message when not logged in")
        void showsMessageWhenNotLoggedIn() {
            session.logout();
            User target = createActiveUser("Target");
            userStorage.save(target);

            ProfileHandler handler = createHandler("\n");

            assertDoesNotThrow(() -> handler.manageNoteFor(target.getId(), target.getName()));
        }

        @Test
        @DisplayName("Shows empty note message when no note exists")
        void showsEmptyNoteMessage() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            // Just cancel (return to previous menu)
            ProfileHandler handler = createHandler("0\n");

            assertDoesNotThrow(() -> handler.manageNoteFor(target.getId(), target.getName()));
        }

        @Test
        @DisplayName("Displays existing note")
        void displaysExistingNote() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            // Add note for target via storage
            saveNote(testUser.getId(), target.getId(), "Great conversation partner!");

            // Cancel
            ProfileHandler handler = createHandler("0\n");

            assertDoesNotThrow(() -> handler.manageNoteFor(target.getId(), target.getName()));
        }

        @Test
        @DisplayName("Adds new note")
        void addsNewNote() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            // Add note (1), enter note content
            ProfileHandler handler = createHandler("1\nReally nice person!\n");
            handler.manageNoteFor(target.getId(), target.getName());

            // Verify note was added via storage
            Optional<ProfileNote> note = userStorage.getProfileNote(testUser.getId(), target.getId());
            assertTrue(note.isPresent());
            assertEquals("Really nice person!", note.get().content());
        }

        @Test
        @DisplayName("Updates existing note")
        void updatesExistingNote() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            saveNote(testUser.getId(), target.getId(), "Old note");

            // Edit (1), enter new content
            ProfileHandler handler = createHandler("1\nUpdated note!\n");
            handler.manageNoteFor(target.getId(), target.getName());

            Optional<ProfileNote> note = userStorage.getProfileNote(testUser.getId(), target.getId());
            assertTrue(note.isPresent());
            assertEquals("Updated note!", note.get().content());
        }

        @Test
        @DisplayName("Deletes note with confirmation")
        void deletesNoteWithConfirmation() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            saveNote(testUser.getId(), target.getId(), "Note to delete");

            // Delete (2), confirm (y)
            ProfileHandler handler = createHandler("2\ny\n");
            handler.manageNoteFor(target.getId(), target.getName());

            assertTrue(
                    userStorage.getProfileNote(testUser.getId(), target.getId()).isEmpty());
        }

        @Test
        @DisplayName("Cancels note deletion")
        void cancelsNoteDeletion() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            saveNote(testUser.getId(), target.getId(), "Keep this note");

            // Delete (2), cancel (n)
            ProfileHandler handler = createHandler("2\nn\n");
            handler.manageNoteFor(target.getId(), target.getName());

            assertTrue(
                    userStorage.getProfileNote(testUser.getId(), target.getId()).isPresent());
        }

        @Test
        @DisplayName("Shows delete option only when note exists")
        void showsDeleteOptionOnlyWhenNoteExists() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            // No note exists - try to delete (should be ignored)
            ProfileHandler handler = createHandler("2\n");

            assertDoesNotThrow(() -> handler.manageNoteFor(target.getId(), target.getName()));
        }

        @Test
        @DisplayName("Does not add empty note")
        void doesNotAddEmptyNote() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            // Add (1), enter empty string
            ProfileHandler handler = createHandler("1\n\n");
            handler.manageNoteFor(target.getId(), target.getName());

            assertTrue(
                    userStorage.getProfileNote(testUser.getId(), target.getId()).isEmpty());
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("View All Notes")
    class ViewAllNotes {

        @Test
        @DisplayName("Shows message when not logged in")
        void showsMessageWhenNotLoggedIn() {
            session.logout();

            ProfileHandler handler = createHandler("\n");

            assertDoesNotThrow(handler::viewAllNotes);
        }

        @Test
        @DisplayName("Shows message when no notes exist")
        void showsMessageWhenNoNotesExist() {
            ProfileHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::viewAllNotes);
        }

        @Test
        @DisplayName("Lists all notes with usernames")
        void listsAllNotesWithUsernames() {
            User target1 = createActiveUser("Person1");
            User target2 = createActiveUser("Person2");
            userStorage.save(target1);
            userStorage.save(target2);

            saveNote(testUser.getId(), target1.getId(), "Note for person 1");
            saveNote(testUser.getId(), target2.getId(), "Note for person 2");

            ProfileHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::viewAllNotes);
        }

        @Test
        @DisplayName("Shows unknown user for deleted users")
        void showsUnknownUserForDeletedUsers() {
            UUID deletedUserId = UUID.randomUUID();
            saveNote(testUser.getId(), deletedUserId, "Note for deleted user");

            // User not in storage - should show "(deleted user)"
            ProfileHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::viewAllNotes);
        }

        @Test
        @DisplayName("Shows notes sorted by most recent")
        void showsNotesSortedByMostRecent() {
            User target1 = createActiveUser("FirstNote");
            User target2 = createActiveUser("SecondNote");
            userStorage.save(target1);
            userStorage.save(target2);

            saveNote(testUser.getId(), target1.getId(), "First note");
            saveNote(testUser.getId(), target2.getId(), "Second note");

            ProfileHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::viewAllNotes);
        }

        @Test
        @DisplayName("Shows note count")
        void showsNoteCount() {
            for (int i = 0; i < 5; i++) {
                User target = createActiveUser("Person" + i);
                userStorage.save(target);
                saveNote(testUser.getId(), target.getId(), "Note " + i);
            }

            ProfileHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::viewAllNotes);
            assertEquals(
                    5, userStorage.getProfileNotesByAuthor(testUser.getId()).size());
        }
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Note Preview and Has Note")
    class NoteUtilities {

        @Test
        @DisplayName("getNotePreview returns preview for existing note")
        void getNotePreviewReturnsPreview() {
            User target = createActiveUser("Target");
            userStorage.save(target);
            saveNote(testUser.getId(), target.getId(), "A great person!");

            ProfileHandler handler = createHandler("");

            String preview = handler.getNotePreview(testUser.getId(), target.getId());
            assertTrue(preview.contains("A great person!"));
        }

        @Test
        @DisplayName("getNotePreview returns empty string for no note")
        void getNotePreviewReturnsEmptyForNoNote() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            ProfileHandler handler = createHandler("");

            String preview = handler.getNotePreview(testUser.getId(), target.getId());
            assertEquals("", preview);
        }

        @Test
        @DisplayName("hasNote returns true when note exists")
        void hasNoteReturnsTrue() {
            User target = createActiveUser("Target");
            userStorage.save(target);
            saveNote(testUser.getId(), target.getId(), "Some note");

            ProfileHandler handler = createHandler("");

            assertTrue(handler.hasNote(testUser.getId(), target.getId()));
        }

        @Test
        @DisplayName("hasNote returns false when no note")
        void hasNoteReturnsFalse() {
            User target = createActiveUser("Target");
            userStorage.save(target);

            ProfileHandler handler = createHandler("");

            assertFalse(handler.hasNote(testUser.getId(), target.getId()));
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
