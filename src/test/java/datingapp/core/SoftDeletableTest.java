package datingapp.core;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
@DisplayName("Soft-delete behavior")
class SoftDeletableTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUpClock() {
        TestClock.setFixed(FIXED_INSTANT);
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("User soft-delete")
    @SuppressWarnings("unused")
    class UserSoftDelete {

        @Test
        @DisplayName("new user is not deleted")
        void newUserIsNotDeleted() {
            User user = new User(UUID.randomUUID(), "Alice");
            assertNull(user.getDeletedAt());
            assertFalse(user.isDeleted());
        }

        @Test
        @DisplayName("markDeleted sets timestamp and isDeleted returns true")
        void markDeletedSetsTimestamp() {
            User user = new User(UUID.randomUUID(), "Bob");
            Instant now = AppClock.now();

            user.markDeleted(now);

            assertEquals(now, user.getDeletedAt());
            assertTrue(user.isDeleted());
        }

        @Test
        @DisplayName("markDeleted rejects null")
        void markDeletedRejectsNull() {
            User user = new User(UUID.randomUUID(), "Charlie");
            assertThrows(NullPointerException.class, () -> user.markDeleted(null));
        }

        @Test
        @DisplayName("StorageBuilder preserves deletedAt")
        void storageBuilderPreservesDeletedAt() {
            Instant deleted = Instant.parse("2026-01-15T12:00:00Z");
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Dave", AppClock.now())
                    .deletedAt(deleted)
                    .build();

            assertEquals(deleted, user.getDeletedAt());
            assertTrue(user.isDeleted());
        }

        @Test
        @DisplayName("StorageBuilder null deletedAt means not deleted")
        void storageBuilderNullDeletedAt() {
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Eve", AppClock.now())
                    .deletedAt(null)
                    .build();

            assertNull(user.getDeletedAt());
            assertFalse(user.isDeleted());
        }
    }

    @Nested
    @DisplayName("Match soft-delete")
    @SuppressWarnings("unused")
    class MatchSoftDelete {

        @Test
        @DisplayName("new match is not deleted")
        void newMatchIsNotDeleted() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            assertNull(match.getDeletedAt());
            assertFalse(match.isDeleted());
        }

        @Test
        @DisplayName("markDeleted sets timestamp and isDeleted returns true")
        void markDeletedSetsTimestamp() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);
            Instant now = AppClock.now();

            match.markDeleted(now);

            assertEquals(now, match.getDeletedAt());
            assertTrue(match.isDeleted());
        }

        @Test
        @DisplayName("markDeleted rejects null")
        void markDeletedRejectsNull() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            assertThrows(NullPointerException.class, () -> match.markDeleted(null));
        }

        @Test
        @DisplayName("setDeletedAt allows null for storage reconstitution")
        void setDeletedAtAllowsNull() {
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            match.setDeletedAt(null);
            assertNull(match.getDeletedAt());
            assertFalse(match.isDeleted());
        }

        @Test
        @DisplayName("setDeletedAt round-trips from storage")
        void setDeletedAtRoundTrips() {
            Instant ts = Instant.parse("2026-03-01T08:00:00Z");
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Match match = Match.create(a, b);

            match.setDeletedAt(ts);

            assertEquals(ts, match.getDeletedAt());
            assertTrue(match.isDeleted());
        }
    }
}
