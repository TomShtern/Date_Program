package datingapp.cli;

import datingapp.core.User;
import datingapp.core.User.ProfileNoteStorage;
import datingapp.core.UserStorage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for managing private profile notes. Users can add, view, edit, and delete personal notes
 * about other profiles.
 */
public class ProfileNotesHandler {

    private static final Logger logger = LoggerFactory.getLogger(ProfileNotesHandler.class);

    private final ProfileNoteStorage profileNoteStorage;
    private final UserStorage userStorage;
    private final CliUtilities.UserSession userSession;
    private final CliUtilities.InputReader inputReader;

    public ProfileNotesHandler(
            ProfileNoteStorage profileNoteStorage,
            UserStorage userStorage,
            CliUtilities.UserSession userSession,
            CliUtilities.InputReader inputReader) {
        this.profileNoteStorage = profileNoteStorage;
        this.userStorage = userStorage;
        this.userSession = userSession;
        this.inputReader = inputReader;
    }

    /**
     * Shows the note management menu for a specific user.
     *
     * @param subjectId the ID of the user the note is about
     * @param subjectName the name of the user (for display)
     */
    public void manageNoteFor(UUID subjectId, String subjectName) {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();

        logger.info("\n" + CliConstants.MENU_DIVIDER);
        logger.info("       📝 NOTES ABOUT {}", subjectName.toUpperCase());
        logger.info(CliConstants.MENU_DIVIDER);

        Optional<User.ProfileNote> existingNote = profileNoteStorage.get(currentUser.getId(), subjectId);

        if (existingNote.isPresent()) {
            logger.info("\nCurrent note:");
            logger.info("\"{}\"", existingNote.get().content());
            logger.info("\n  1. Edit note");
            logger.info("  2. Delete note");
            logger.info("  0. Back");

            String choice = inputReader.readLine("\nChoice: ");
            switch (choice) {
                case "1" -> editNote(currentUser.getId(), subjectId, existingNote.get());
                case "2" -> deleteNote(currentUser.getId(), subjectId, subjectName);
                default -> {
                    /* back */ }
            }
        } else {
            logger.info("\nNo notes yet.");
            logger.info("  1. Add a note");
            logger.info("  0. Back");

            String choice = inputReader.readLine("\nChoice: ");
            if (choice.equals("1")) {
                addNote(currentUser.getId(), subjectId, subjectName);
            }
        }
    }

    /**
     * Adds a new note for a user.
     *
     * @param authorId The ID of the user creating the note
     * @param subjectId The ID of the user the note is about
     * @param subjectName The name of the user the note is about
     */
    private void addNote(UUID authorId, UUID subjectId, String subjectName) {
        logger.info("\nEnter your note about {} (max {} chars):", subjectName, User.ProfileNote.MAX_LENGTH);
        logger.info("Examples: \"Met at coffee shop\", \"Loves hiking\", \"Dinner Thursday 7pm\"");
        String content = inputReader.readLine("\nNote: ");

        if (content.isBlank()) {
            logger.info("⚠️  Note cannot be empty.");
            return;
        }

        if (content.length() > User.ProfileNote.MAX_LENGTH) {
            logger.info(
                    "⚠️  Note is too long ({} chars). Max is {} chars.", content.length(), User.ProfileNote.MAX_LENGTH);
            return;
        }

        try {
            User.ProfileNote note = User.ProfileNote.create(authorId, subjectId, content);
            profileNoteStorage.save(note);
            logger.info("✅ Note saved!\n");
        } catch (IllegalArgumentException e) {
            logger.info("❌ {}\n", e.getMessage());
        }
    }

    /**
     * Edits an existing note.
     *
     * @param authorId The ID of the user editing the note
     * @param subjectId The ID of the user the note is about
     * @param existing The existing note to edit
     */
    private void editNote(UUID authorId, UUID subjectId, User.ProfileNote existing) {
        logger.info("\nCurrent note: \"{}\"\n", existing.content());
        logger.info("Enter new note (or press Enter to keep current):");
        String content = inputReader.readLine("Note: ");

        if (content.isBlank()) {
            logger.info("✓ Note unchanged.\n");
            return;
        }

        if (content.length() > User.ProfileNote.MAX_LENGTH) {
            logger.info(
                    "⚠️  Note is too long ({} chars). Max is {} chars.", content.length(), User.ProfileNote.MAX_LENGTH);
            return;
        }

        try {
            User.ProfileNote updated = existing.withContent(content);
            profileNoteStorage.save(updated);
            logger.info("✅ Note updated!\n");
        } catch (IllegalArgumentException e) {
            logger.info("❌ {}\n", e.getMessage());
        }
    }

    /**
     * Deletes a note about a user.
     *
     * @param authorId The ID of the user who wrote the note
     * @param subjectId The ID of the user the note is about
     * @param subjectName The name of the user the note is about
     */
    private void deleteNote(UUID authorId, UUID subjectId, String subjectName) {
        String confirm = inputReader.readLine("Delete note about " + subjectName + "? (y/n): ");
        if (confirm.equalsIgnoreCase("y")) {
            if (profileNoteStorage.delete(authorId, subjectId)) {
                logger.info("✅ Note deleted.\n");
            } else {
                logger.info("⚠️  Note not found.\n");
            }
        } else {
            logger.info("Cancelled.\n");
        }
    }

    /** Views all notes the current user has created. */
    public void viewAllNotes() {
        if (!userSession.isLoggedIn()) {
            logger.info(CliConstants.PLEASE_SELECT_USER);
            return;
        }

        User currentUser = userSession.getCurrentUser();

        logger.info("\n" + CliConstants.MENU_DIVIDER);
        logger.info("         📝 MY PROFILE NOTES");
        logger.info(CliConstants.MENU_DIVIDER + "\n");

        List<User.ProfileNote> notes = profileNoteStorage.getAllByAuthor(currentUser.getId());

        if (notes.isEmpty()) {
            logger.info("You haven't added any notes yet.");
            logger.info("Tip: Add notes when viewing matches to remember details!\n");
            return;
        }

        logger.info("You have {} note(s):\n", notes.size());

        for (int i = 0; i < notes.size(); i++) {
            User.ProfileNote note = notes.get(i);
            User subject = userStorage.get(note.subjectId());
            String subjectName = subject != null ? subject.getName() : "(deleted user)";

            logger.info("  {}. {} - \"{}\"", i + 1, subjectName, note.getPreview());
        }

        logger.info("\nEnter number to view/edit, or 0 to go back:");
        String input = inputReader.readLine("Choice: ");

        try {
            int idx = Integer.parseInt(input) - 1;
            if (idx >= 0 && idx < notes.size()) {
                User.ProfileNote note = notes.get(idx);
                User subject = userStorage.get(note.subjectId());
                String subjectName = subject != null ? subject.getName() : "this user";
                manageNoteFor(note.subjectId(), subjectName);
            }
        } catch (NumberFormatException e) {
            // Back to menu
        }
    }

    /**
     * Gets the preview of a note for display in match lists.
     *
     * @param authorId the note author
     * @param subjectId the subject of the note
     * @return the note preview, or empty string if no note exists
     */
    public String getNotePreview(UUID authorId, UUID subjectId) {
        return profileNoteStorage
                .get(authorId, subjectId)
                .map(n -> "📝 " + n.getPreview())
                .orElse("");
    }

    /** Checks if a note exists for the given subject. */
    public boolean hasNote(UUID authorId, UUID subjectId) {
        return profileNoteStorage.get(authorId, subjectId).isPresent();
    }
}
