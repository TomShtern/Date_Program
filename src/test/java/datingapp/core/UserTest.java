package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.model.User.UserState;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.profile.MatchPreferences;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Unit tests for User domain model. */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class UserTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUpClock() {
        TestClock.setFixed(FIXED_INSTANT);
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    @Test
    @DisplayName("New user starts as INCOMPLETE")
    void newUserStartsAsIncomplete() {
        User user = new User(UUID.randomUUID(), "John");
        assertEquals(UserState.INCOMPLETE, user.getState());
    }

    @Test
    @DisplayName("User with complete profile can activate")
    void completeUserCanActivate() {
        User user = createCompleteUser("Alice");

        assertTrue(user.isComplete());
        assertEquals(UserState.INCOMPLETE, user.getState());

        user.activate();
        assertEquals(UserState.ACTIVE, user.getState());
    }

    @Test
    @DisplayName("Incomplete user cannot activate")
    void incompleteUserCannotActivate() {
        User user = new User(UUID.randomUUID(), "Bob");

        assertFalse(user.isComplete());
        assertThrows(IllegalStateException.class, user::activate);
    }

    @Test
    @DisplayName("Incomplete user cannot pause")
    void incompleteUserCannotPause() {
        User user = new User(UUID.randomUUID(), "Paula");

        assertFalse(user.isComplete());
        assertThrows(IllegalStateException.class, user::pause);
    }

    @Test
    @DisplayName("Active user can pause and unpause")
    void activeUserCanPauseAndUnpause() {
        User user = createCompleteUser("Charlie");
        user.activate();

        user.pause();
        assertEquals(UserState.PAUSED, user.getState());

        user.activate();
        assertEquals(UserState.ACTIVE, user.getState());
    }

    @Test
    @DisplayName("Banned user cannot activate")
    void bannedUserCannotActivate() {
        User user = createCompleteUser("Dave");
        user.activate();
        user.ban();

        assertEquals(UserState.BANNED, user.getState());
        assertThrows(IllegalStateException.class, user::activate);
    }

    @Test
    @DisplayName("Age is calculated correctly from birth date")
    void ageIsCalculatedCorrectly() {
        User user = new User(UUID.randomUUID(), "Eve");
        user.setBirthDate(AppClock.today().minusYears(25).minusDays(10));

        assertEquals(25, user.getAge());
    }

    @Test
    @DisplayName("Multiple photo URLs can be added")
    void multiplePhotoUrlsCanBeAdded() {
        User user = new User(UUID.randomUUID(), "Frank");
        user.addPhotoUrl("photo1.jpg");
        user.addPhotoUrl("photo2.jpg");
        user.addPhotoUrl("photo3.jpg");

        assertEquals(3, user.getPhotoUrls().size());
    }

    @Test
    @DisplayName("New user has no interests")
    void newUserHasNoInterests() {
        User user = new User(UUID.randomUUID(), "John");
        assertTrue(user.getInterests().isEmpty());
    }

    @Test
    @DisplayName("Set and get interests")
    void setAndGetInterests() {
        User user = new User(UUID.randomUUID(), "Alice");
        Set<Interest> interests = EnumSet.of(Interest.HIKING, Interest.COFFEE);
        user.setInterests(interests);

        assertEquals(2, user.getInterests().size());
        assertTrue(user.getInterests().contains(Interest.HIKING));
        assertTrue(user.getInterests().contains(Interest.COFFEE));
    }

    @Test
    @DisplayName("Adding many interests does not throw")
    void addingManyInterestsAllowed() {
        User user = new User(UUID.randomUUID(), "Bob");
        for (int i = 0; i < Interest.MAX_PER_USER; i++) {
            user.addInterest(Interest.values()[i]);
        }
        user.addInterest(Interest.TRAVEL);

        assertTrue(user.getInterests().contains(Interest.TRAVEL));
    }

    @Test
    @DisplayName("Removing interest")
    void removingInterest() {
        User user = new User(UUID.randomUUID(), "Charlie");
        user.addInterest(Interest.HIKING);
        assertTrue(user.getInterests().contains(Interest.HIKING));

        user.removeInterest(Interest.HIKING);
        assertFalse(user.getInterests().contains(Interest.HIKING));
    }

    @Test
    @DisplayName("Setting interests to null is treated as empty")
    void setInterests_null_treatedAsEmpty() {
        User user = new User(UUID.randomUUID(), "Dave");
        user.setInterests(EnumSet.of(Interest.HIKING));
        assertEquals(1, user.getInterests().size());

        user.setInterests(null);
        assertTrue(user.getInterests().isEmpty());
    }

    @Test
    @DisplayName("getInterests returns defensive copy")
    void getInterests_returnsDefensiveCopy() {
        User user = new User(UUID.randomUUID(), "Eve");
        user.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE));

        Set<Interest> retrieved = user.getInterests();
        retrieved.add(Interest.TRAVEL); // Modify the retrieved set

        // Original should not be affected
        assertEquals(2, user.getInterests().size());
        assertFalse(user.getInterests().contains(Interest.TRAVEL));
    }

    @Test
    @DisplayName("setAgeRange allows min and max equal")
    void setAgeRange_allowsEqualBounds() {
        User user = new User(UUID.randomUUID(), "Eve");
        user.setAgeRange(18, 18);

        assertEquals(18, user.getMinAge());
        assertEquals(18, user.getMaxAge());
    }

    @Test
    @DisplayName("addInterest is idempotent for duplicates")
    void addInterest_isIdempotent() {
        User user = new User(UUID.randomUUID(), "Eve");
        user.addInterest(Interest.TRAVEL);
        user.addInterest(Interest.TRAVEL);

        assertEquals(1, user.getInterests().size());
        assertTrue(user.getInterests().contains(Interest.TRAVEL));
    }

    private User createCompleteUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("A great person");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
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

    /** Tests for the ProfileNote nested record. */
    @Nested
    @DisplayName("ProfileNote")
    @SuppressWarnings("unused")
    class ProfileNoteTests {

        private final UUID authorId = UUID.randomUUID();
        private final UUID subjectId = UUID.randomUUID();

        @Test
        @DisplayName("Create valid profile note")
        void createValidNote() {
            ProfileNote note = ProfileNote.create(authorId, subjectId, "Met at coffee shop");

            assertEquals(authorId, note.authorId());
            assertEquals(subjectId, note.subjectId());
            assertEquals("Met at coffee shop", note.content());
            assertNotNull(note.createdAt());
            assertNotNull(note.updatedAt());
        }

        @Test
        @DisplayName("Cannot create note with null authorId")
        void cannotCreateWithNullAuthor() {
            assertThrows(NullPointerException.class, () -> ProfileNote.create(null, subjectId, "content"));
        }

        @Test
        @DisplayName("Cannot create note with null subjectId")
        void cannotCreateWithNullSubject() {
            assertThrows(NullPointerException.class, () -> ProfileNote.create(authorId, null, "content"));
        }

        @Test
        @DisplayName("Cannot create note with null content")
        void cannotCreateWithNullContent() {
            assertThrows(NullPointerException.class, () -> ProfileNote.create(authorId, subjectId, null));
        }

        @Test
        @DisplayName("Cannot create note with blank content")
        void cannotCreateWithBlankContent() {
            assertThrows(IllegalArgumentException.class, () -> ProfileNote.create(authorId, subjectId, "   "));
        }

        @Test
        @DisplayName("Cannot create note about yourself")
        void cannotCreateNoteAboutSelf() {
            UUID sameId = UUID.randomUUID();
            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> ProfileNote.create(sameId, sameId, "Note"));
            assertTrue(ex.getMessage().contains("yourself"));
        }

        @Test
        @DisplayName("Cannot exceed maximum length")
        void cannotExceedMaxLength() {
            String tooLong = "x".repeat(ProfileNote.MAX_LENGTH + 1);
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class, () -> ProfileNote.create(authorId, subjectId, tooLong));
            assertTrue(ex.getMessage().contains("maximum length"));
        }

        @Test
        @DisplayName("Content at maximum length is allowed")
        void contentAtMaxLengthAllowed() {
            String maxContent = "x".repeat(ProfileNote.MAX_LENGTH);
            ProfileNote note = ProfileNote.create(authorId, subjectId, maxContent);
            assertEquals(ProfileNote.MAX_LENGTH, note.content().length());
        }

        @Test
        @DisplayName("withContent updates content and timestamp")
        void withContentUpdatesContent() {
            Instant fixed = Instant.parse("2026-01-26T00:00:00Z");
            ProfileNote original = new ProfileNote(authorId, subjectId, "Original", fixed, fixed);

            ProfileNote updated = original.withContent("Updated note");

            assertEquals("Updated note", updated.content());
            assertEquals(original.createdAt(), updated.createdAt());
            assertTrue(updated.updatedAt().isAfter(original.createdAt()));
        }

        @Test
        @DisplayName("withContent rejects blank content")
        void withContentRejectsBlank() {
            ProfileNote note = ProfileNote.create(authorId, subjectId, "Original");
            assertThrows(IllegalArgumentException.class, () -> note.withContent(""));
        }

        @Test
        @DisplayName("withContent rejects content exceeding max length")
        void withContentRejectsExceedingLength() {
            ProfileNote note = ProfileNote.create(authorId, subjectId, "Original");
            String tooLong = "x".repeat(ProfileNote.MAX_LENGTH + 1);
            assertThrows(IllegalArgumentException.class, () -> note.withContent(tooLong));
        }

        @Test
        @DisplayName("getPreview returns full content for short notes")
        void getPreviewReturnsFullForShort() {
            ProfileNote note = ProfileNote.create(authorId, subjectId, "Short note");
            assertEquals("Short note", note.getPreview());
        }

        @Test
        @DisplayName("getPreview truncates long content with ellipsis")
        void getPreviewTruncatesLong() {
            String longContent =
                    "This is a very long note that exceeds fifty characters in length and should be truncated";
            ProfileNote note = ProfileNote.create(authorId, subjectId, longContent);

            String preview = note.getPreview();
            assertEquals(50, preview.length());
            assertTrue(preview.endsWith("..."));
        }

        @Test
        @DisplayName("getPreview returns exact 50 chars without ellipsis")
        void getPreviewExact50Chars() {
            String exact50 = "x".repeat(50);
            ProfileNote note = ProfileNote.create(authorId, subjectId, exact50);
            assertEquals(exact50, note.getPreview());
        }
    }

    @Nested
    @DisplayName("StorageBuilder")
    @SuppressWarnings("unused")
    class StorageBuilderTests {

        private User buildUserFromStorage() {
            PacePreferences pace = new PacePreferences(
                    MessagingFrequency.OFTEN,
                    TimeToFirstDate.FEW_DAYS,
                    CommunicationStyle.TEXT_ONLY,
                    DepthPreference.DEEP_CHAT);

            return User.StorageBuilder.create(UUID.randomUUID(), "Alice", Instant.parse("2026-01-01T10:00:00Z"))
                    .bio("Bio")
                    .birthDate(LocalDate.of(1990, 1, 1))
                    .gender(Gender.FEMALE)
                    .interestedIn(EnumSet.of(Gender.MALE))
                    .location(32.1, 34.8)
                    .maxDistanceKm(50)
                    .ageRange(20, 30)
                    .photoUrls(List.of("a.jpg", "b.jpg"))
                    .state(UserState.ACTIVE)
                    .updatedAt(Instant.parse("2026-01-02T10:00:00Z"))
                    .interests(EnumSet.of(Interest.COFFEE))
                    .smoking(MatchPreferences.Lifestyle.Smoking.NEVER)
                    .drinking(MatchPreferences.Lifestyle.Drinking.SOCIALLY)
                    .wantsKids(MatchPreferences.Lifestyle.WantsKids.SOMEDAY)
                    .lookingFor(MatchPreferences.Lifestyle.LookingFor.LONG_TERM)
                    .education(MatchPreferences.Lifestyle.Education.BACHELORS)
                    .heightCm(170)
                    .email("alice@example.com")
                    .phone("+123456789")
                    .verified(true)
                    .verificationMethod(VerificationMethod.EMAIL)
                    .verificationCode("123456")
                    .verificationSentAt(Instant.parse("2026-01-01T10:00:00Z"))
                    .verifiedAt(Instant.parse("2026-01-02T10:00:00Z"))
                    .pacePreferences(pace)
                    .build();
        }

        @Test
        @DisplayName("Builds user with core fields")
        void buildsUserWithCoreFields() {
            User user = buildUserFromStorage();

            assertEquals("Alice", user.getName());
            assertEquals(LocalDate.of(1990, 1, 1), user.getBirthDate());
            assertEquals(Gender.FEMALE, user.getGender());
            assertEquals(EnumSet.of(Gender.MALE), user.getInterestedIn());
            assertEquals(32.1, user.getLat());
            assertEquals(34.8, user.getLon());
            assertEquals(50, user.getMaxDistanceKm());
            assertEquals(20, user.getMinAge());
            assertEquals(30, user.getMaxAge());
            assertEquals(List.of("a.jpg", "b.jpg"), user.getPhotoUrls());
            assertEquals(UserState.ACTIVE, user.getState());
        }

        @Test
        @DisplayName("StorageBuilder maps optional fields")
        void storageBuilderMapsOptionalFields() {
            User user = buildUserFromStorage();

            assertEquals(EnumSet.of(Interest.COFFEE), user.getInterests());
            assertEquals(MatchPreferences.Lifestyle.Smoking.NEVER, user.getSmoking());
            assertEquals(MatchPreferences.Lifestyle.Drinking.SOCIALLY, user.getDrinking());
            assertEquals(MatchPreferences.Lifestyle.WantsKids.SOMEDAY, user.getWantsKids());
            assertEquals(MatchPreferences.Lifestyle.LookingFor.LONG_TERM, user.getLookingFor());
            assertEquals(MatchPreferences.Lifestyle.Education.BACHELORS, user.getEducation());
            assertTrue(user.isVerified());
            assertEquals(VerificationMethod.EMAIL, user.getVerificationMethod());
        }
    }
}
