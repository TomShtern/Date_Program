package datingapp.app.cli;

import datingapp.core.AppSession;
import datingapp.core.LoggingSupport;
import datingapp.core.User;
import datingapp.core.storage.UserStorage;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler for managing private profile notes. Users can add, view, edit, and
 * delete personal notes
 * about other profiles.
 */
public class ProfileNotesHandler implements LoggingSupport {

    private static final Logger logger = LoggerFactory.getLogger(ProfileNotesHandler.class);

    private final UserStorage userStorage;
    private final AppSession session;
    private final InputReader inputReader;

    public ProfileNotesHandler(UserStorage userStorage, AppSession session, InputReader inputReader) {
        this.userStorage = userStorage;
        this.session = session;
        this.inputReader = inputReader;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    /**
     * Shows the note management menu for a specific user.
     *
     * @param subjectId   the ID of the user the note is about
     * @param subjectName the name of the user (for display)
     */
    public void manageNoteFor(UUID subjectId, String subjectName) {
        CliSupport.requireLogin(() -> {
            User currentUser = session.getCurrentUser();

            logInfo("\n" + CliSupport.MENU_DIVIDER);
            logInfo("       📝 NOTES ABOUT {}", subjectName.toUpperCase(Locale.ROOT));
            logInfo(CliSupport.MENU_DIVIDER);

            Optional<User.ProfileNote> existingNote = userStorage.getProfileNote(currentUser.getId(), subjectId);

            if (existingNote.isPresent()) {
                logInfo("\nCurrent note:");
                logInfo("\"{}\"", existingNote.get().content());
                logInfo("\n  1. Edit note");
                logInfo("  2. Delete note");
                logInfo("  0. Back");

                String choice = inputReader.readLine("\nChoice: ");
                switch (choice) {
                    case "1" -> editNote(existingNote.get());
                    case "2" -> deleteNote(currentUser.getId(), subjectId, subjectName);
                    default -> {
                        /* back */ }
                }
            } else {
                logInfo("\nNo notes yet.");
                logInfo("  1. Add a note");
                logInfo("  0. Back");

                String choice = inputReader.readLine("\nChoice: ");
                if ("1".equals(choice)) {
                    addNote(currentUser.getId(), subjectId, subjectName);
                }
            }
        });
    }

    /**
     * Adds a new note for a user.
     *
     * @param authorId    The ID of the user creating the note
     * @param subjectId   The ID of the user the note is about
     * @param subjectName The name of the user the note is about
     */
    private void addNote(UUID authorId, UUID subjectId, String subjectName) {
        logInfo("\nEnter your note about {} (max {} chars):", subjectName, User.ProfileNote.MAX_LENGTH);
        logInfo("Examples: \"Met at coffee shop\", \"Loves hiking\", \"Dinner Thursday 7pm\"");
        String content = inputReader.readLine("\nNote: ");

        if (content.isBlank()) {
            logInfo("⚠️  Note cannot be empty.");
            return;
        }

        if (content.length() > User.ProfileNote.MAX_LENGTH) {
            logInfo("⚠️  Note is too long ({} chars). Max is {} chars.", content.length(), User.ProfileNote.MAX_LENGTH);
            return;
        }

        try {
            User.ProfileNote note = User.ProfileNote.create(authorId, subjectId, content);
            userStorage.saveProfileNote(note);
            logInfo("✅ Note saved!\n");
        } catch (IllegalArgumentException e) {
            logInfo("❌ {}\n", e.getMessage());
        }
    }

    /**
     * Edits an existing note.
     *
     * @param existing The existing note to edit
     */
    private void editNote(User.ProfileNote existing) {
        logInfo("\nCurrent note: \"{}\"\n", existing.content());
        logInfo("Enter new note (or press Enter to keep current):");
        String content = inputReader.readLine("Note: ");

        if (content.isBlank()) {
            logInfo("✓ Note unchanged.\n");
            return;
        }

        if (content.length() > User.ProfileNote.MAX_LENGTH) {
            logInfo("⚠️  Note is too long ({} chars). Max is {} chars.", content.length(), User.ProfileNote.MAX_LENGTH);
            return;
        }

        try {
            User.ProfileNote updated = existing.withContent(content);
            userStorage.saveProfileNote(updated);
            logInfo("✅ Note updated!\n");
        } catch (IllegalArgumentException e) {
            logInfo("❌ {}\n", e.getMessage());
        }
    }

    /**
     * Deletes a note about a user.
     *
     * @param authorId    The ID of the user who wrote the note
     * @param subjectId   The ID of the user the note is about
     * @param subjectName The name of the user the note is about
     */
    private void deleteNote(UUID authorId, UUID subjectId, String subjectName) {
        String confirm = inputReader.readLine("Delete note about " + subjectName + "? (y/n): ");
        if ("y".equalsIgnoreCase(confirm)) {
            if (userStorage.deleteProfileNote(authorId, subjectId)) {
                logInfo("✅ Note deleted.\n");
            } else {
                logInfo("⚠️  Note not found.\n");
            }
        } else {
            logInfo("Cancelled.\n");
        }
    }

    /** Views all notes the current user has created. */
    public void viewAllNotes() {
        CliSupport.requireLogin(() -> {
            User currentUser = session.getCurrentUser();

            logInfo("\n" + CliSupport.MENU_DIVIDER);
            logInfo("         📝 MY PROFILE NOTES");
            logInfo(CliSupport.MENU_DIVIDER + "\n");

            List<User.ProfileNote> notes = userStorage.getProfileNotesByAuthor(currentUser.getId());

            if (notes.isEmpty()) {
                logInfo("You haven't added any notes yet.");
                logInfo("Tip: Add notes when viewing matches to remember details!\n");
                return;
            }

            logInfo("You have {} note(s):\n", notes.size());

            for (int i = 0; i < notes.size(); i++) {
                User.ProfileNote note = notes.get(i);
                User subject = userStorage.get(note.subjectId());
                String subjectName = subject != null ? subject.getName() : "(deleted user)";

                logInfo("  {}. {} - \"{}\"", i + 1, subjectName, note.getPreview());
            }

            logInfo("\nEnter number to view/edit, or 0 to go back:");
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
                logTrace("Non-numeric input for note selection: {}", e.getMessage());
                // Back to menu - user entered non-numeric input
            }
        });
    }

    /**
     * Gets the preview of a note for display in match lists.
     *
     * @param authorId  the note author
     * @param subjectId the subject of the note
     * @return the note preview, or empty string if no note exists
     */
    public String getNotePreview(UUID authorId, UUID subjectId) {
        return userStorage
                .getProfileNote(authorId, subjectId)
                .map(n -> "📝 " + n.getPreview())
                .orElse("");
    }

    /** Checks if a note exists for the given subject. */
    public boolean hasNote(UUID authorId, UUID subjectId) {
        return userStorage.getProfileNote(authorId, subjectId).isPresent();
    }
}
