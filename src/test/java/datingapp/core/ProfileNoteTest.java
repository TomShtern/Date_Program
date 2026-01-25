package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("User.ProfileNote")
class ProfileNoteTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID SUBJECT = UUID.randomUUID();

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("creates note with valid content")
        void createsNoteWithValidContent() {
            User.ProfileNote note = User.ProfileNote.create(AUTHOR, SUBJECT, "Met at coffee shop");

            assertEquals(AUTHOR, note.authorId());
            assertEquals(SUBJECT, note.subjectId());
            assertEquals("Met at coffee shop", note.content());
            assertNotNull(note.createdAt());
            assertNotNull(note.updatedAt());
        }

        @Test
        @DisplayName("throws on null author")
        void throwsOnNullAuthor() {
            assertThrows(NullPointerException.class, () -> User.ProfileNote.create(null, SUBJECT, "content"));
        }

        @Test
        @DisplayName("throws on null subject")
        void throwsOnNullSubject() {
            assertThrows(NullPointerException.class, () -> User.ProfileNote.create(AUTHOR, null, "content"));
        }

        @Test
        @DisplayName("throws on null content")
        void throwsOnNullContent() {
            assertThrows(NullPointerException.class, () -> User.ProfileNote.create(AUTHOR, SUBJECT, null));
        }

        @Test
        @DisplayName("throws on blank content")
        void throwsOnBlankContent() {
            assertThrows(IllegalArgumentException.class, () -> User.ProfileNote.create(AUTHOR, SUBJECT, "   "));
        }

        @Test
        @DisplayName("throws if content exceeds max length")
        void throwsIfContentExceedsMaxLength() {
            String tooLong = "x".repeat(User.ProfileNote.MAX_LENGTH + 1);
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class, () -> User.ProfileNote.create(AUTHOR, SUBJECT, tooLong));
            assertTrue(ex.getMessage().contains("500"));
        }

        @Test
        @DisplayName("throws if author equals subject")
        void throwsIfAuthorEqualsSubject() {
            assertThrows(
                    IllegalArgumentException.class, () -> User.ProfileNote.create(AUTHOR, AUTHOR, "note about myself"));
        }
    }

    @Nested
    @DisplayName("withContent()")
    class WithContent {

        @Test
        @DisplayName("updates content and timestamp")
        void updatesContentAndTimestamp() throws InterruptedException {
            User.ProfileNote original = User.ProfileNote.create(AUTHOR, SUBJECT, "Original");
            Thread.sleep(10); // Ensure different timestamp
            User.ProfileNote updated = original.withContent("Updated");

            assertEquals("Updated", updated.content());
            assertEquals(original.createdAt(), updated.createdAt());
            assertTrue(updated.updatedAt().isAfter(original.updatedAt()));
        }

        @Test
        @DisplayName("throws on blank content")
        void throwsOnBlankContent() {
            User.ProfileNote note = User.ProfileNote.create(AUTHOR, SUBJECT, "Original");
            assertThrows(IllegalArgumentException.class, () -> note.withContent("  "));
        }
    }

    @Nested
    @DisplayName("getPreview()")
    class GetPreview {

        @Test
        @DisplayName("returns full content if short")
        void returnsFullContentIfShort() {
            User.ProfileNote note = User.ProfileNote.create(AUTHOR, SUBJECT, "Short note");
            assertEquals("Short note", note.getPreview());
        }

        @Test
        @DisplayName("truncates long content with ellipsis")
        void truncatesLongContentWithEllipsis() {
            String longContent = "x".repeat(100);
            User.ProfileNote note = User.ProfileNote.create(AUTHOR, SUBJECT, longContent);

            String preview = note.getPreview();
            assertEquals(50, preview.length());
            assertTrue(preview.endsWith("..."));
        }
    }
}
