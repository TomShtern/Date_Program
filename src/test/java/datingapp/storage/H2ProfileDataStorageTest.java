package datingapp.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.User.ProfileNote;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("H2ProfileDataStorage")
class H2ProfileDataStorageTest {

    private H2ProfileDataStorage.Notes notes;
    private H2ProfileDataStorage.Views views;

    @BeforeEach
    void setUp() {
        DatabaseManager.setJdbcUrl("jdbc:h2:mem:profile-data-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        DatabaseManager.resetInstance();
        DatabaseManager dbManager = DatabaseManager.getInstance();

        notes = new H2ProfileDataStorage.Notes(dbManager);
        views = new H2ProfileDataStorage.Views(dbManager);
    }

    @Test
    @DisplayName("Saves and retrieves profile notes")
    void savesAndRetrievesNotes() {
        UUID author = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        ProfileNote note = ProfileNote.create(author, subject, "Met at coffee shop");

        notes.save(note);

        Optional<ProfileNote> loaded = notes.get(author, subject);
        assertTrue(loaded.isPresent());
        assertEquals("Met at coffee shop", loaded.get().content());
    }

    @Test
    @DisplayName("Updates note content with MERGE")
    void updatesNoteContent() {
        UUID author = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        ProfileNote note = ProfileNote.create(author, subject, "Original");

        notes.save(note);

        ProfileNote updated = note.withContent("Updated");
        notes.save(updated);

        Optional<ProfileNote> loaded = notes.get(author, subject);
        assertTrue(loaded.isPresent());
        assertEquals("Updated", loaded.get().content());
    }

    @Test
    @DisplayName("Deletes notes")
    void deletesNotes() {
        UUID author = UUID.randomUUID();
        UUID subject = UUID.randomUUID();
        ProfileNote note = ProfileNote.create(author, subject, "Delete me");

        notes.save(note);
        assertTrue(notes.delete(author, subject));
        assertFalse(notes.get(author, subject).isPresent());
    }

    @Test
    @DisplayName("Returns notes sorted by updatedAt descending")
    void returnsNotesSortedByUpdatedAt() {
        UUID author = UUID.randomUUID();
        UUID subjectA = UUID.randomUUID();
        UUID subjectB = UUID.randomUUID();
        Instant base = Instant.parse("2026-01-01T10:00:00Z");

        ProfileNote first = new ProfileNote(author, subjectA, "First", base, base);
        ProfileNote second = new ProfileNote(author, subjectB, "Second", base, base.plusSeconds(10));

        notes.save(first);
        notes.save(second);

        List<ProfileNote> result = notes.getAllByAuthor(author);
        assertEquals(2, result.size());
        assertEquals("Second", result.get(0).content());
        assertEquals("First", result.get(1).content());
    }

    @Test
    @DisplayName("Records profile views and counts")
    void recordsProfileViews() {
        UUID viewer = UUID.randomUUID();
        UUID viewed = UUID.randomUUID();

        views.recordView(viewer, viewed);
        views.recordView(viewer, viewed);

        assertTrue(views.hasViewed(viewer, viewed));
        assertEquals(2, views.getViewCount(viewed));
        assertEquals(1, views.getUniqueViewerCount(viewed));
    }

    @Test
    @DisplayName("Ignores self views")
    void ignoresSelfViews() {
        UUID userId = UUID.randomUUID();

        views.recordView(userId, userId);

        assertEquals(0, views.getViewCount(userId));
        assertFalse(views.hasViewed(userId, userId));
    }

    @Test
    @DisplayName("Returns recent viewers ordered by latest view")
    void returnsRecentViewers() {
        UUID viewed = UUID.randomUUID();
        UUID viewerA = UUID.randomUUID();
        UUID viewerB = UUID.randomUUID();

        views.recordView(viewerA, viewed);
        views.recordView(viewerB, viewed);
        views.recordView(viewerA, viewed);

        List<UUID> recent = views.getRecentViewers(viewed, 10);
        assertEquals(List.of(viewerA, viewerB), recent);
    }
}
