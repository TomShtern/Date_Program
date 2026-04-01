package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
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
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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

        assertEquals(25, user.getAge(ZoneId.of("UTC")).orElseThrow());
    }

    @Test
    @DisplayName("Multiple photo URLs can be added")
    void multiplePhotoUrlsCanBeAdded() {
        User user = new User(UUID.randomUUID(), "Frank");
        for (int i = 1; i <= User.MAX_PHOTOS; i++) {
            user.addPhotoUrl("https://example.com/photo" + i + ".jpg");
        }

        assertEquals(User.MAX_PHOTOS, user.getPhotoUrls().size());
    }

    @Test
    @DisplayName("Adding a photo beyond the limit throws")
    void addPhotoAboveLimitThrows() {
        User user = new User(UUID.randomUUID(), "Grace");
        for (int i = 1; i <= User.MAX_PHOTOS; i++) {
            user.addPhotoUrl("https://example.com/photo" + i + ".jpg");
        }

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> user.addPhotoUrl("https://example.com/photo-overflow.jpg"));
        assertTrue(ex.getMessage().contains("Cannot set more than " + User.MAX_PHOTOS + " photos"));
    }

    @Test
    @DisplayName("Setting placeholder photo URLs preserves the sentinel")
    void setPhotoUrlsAllowsPlaceholder() {
        User user = new User(UUID.randomUUID(), "Hannah");

        user.setPhotoUrls(List.of("placeholder://default-avatar"));

        assertEquals(List.of("placeholder://default-avatar"), user.getPhotoUrls());
    }

    @Test
    @DisplayName("Setting too many photo URLs throws")
    void setPhotoUrlsAboveLimitThrows() {
        User user = new User(UUID.randomUUID(), "Hannah");
        List<String> tooManyPhotos = IntStream.rangeClosed(1, User.MAX_PHOTOS + 1)
                .mapToObj(i -> "https://example.com/photo" + i + ".jpg")
                .toList();

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> user.setPhotoUrls(tooManyPhotos));
        assertTrue(ex.getMessage().contains("Cannot set more than " + User.MAX_PHOTOS + " photos"));
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
    @DisplayName("Adding interest above max limit throws")
    void addingInterestAboveLimitThrows() {
        User user = new User(UUID.randomUUID(), "Bob");
        for (int i = 0; i < Interest.MAX_PER_USER; i++) {
            user.addInterest(Interest.values()[i]);
        }

        Interest extraInterest = Interest.values()[Interest.MAX_PER_USER];
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> user.addInterest(extraInterest));

        assertTrue(ex.getMessage().contains("Cannot add more than"));
        assertEquals(Interest.MAX_PER_USER, user.getInterests().size());
    }

    @Test
    @DisplayName("Setting interests above max limit throws")
    void setInterestsAboveLimitThrows() {
        User user = new User(UUID.randomUUID(), "Mila");
        Set<Interest> tooMany = EnumSet.noneOf(Interest.class);
        for (int i = 0; i <= Interest.MAX_PER_USER; i++) {
            tooMany.add(Interest.values()[i]);
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> user.setInterests(tooMany));

        assertTrue(ex.getMessage().contains("Cannot set more than"));
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
    @DisplayName("isInterestedInEveryone uses matchable gender policy helper")
    void isInterestedInEveryone_usesMatchableGenderPolicy() {
        User openUser = new User(UUID.randomUUID(), "OpenUser");
        openUser.setInterestedIn(User.matchableGenders());
        assertTrue(openUser.isInterestedInEveryone());

        User partialUser = new User(UUID.randomUUID(), "PartialUser");
        partialUser.setInterestedIn(EnumSet.of(Gender.MALE, Gender.FEMALE));
        assertFalse(partialUser.isInterestedInEveryone());
    }

    @Test
    @DisplayName("missing profile fields follow the same completeness rules as isComplete")
    void missingProfileFieldsFollowCompletenessRules() {
        User partialUser = new User(UUID.randomUUID(), "PartialUser");
        partialUser.setBio("A short bio");
        partialUser.setBirthDate(LocalDate.of(1991, 6, 15));
        partialUser.setGender(Gender.FEMALE);

        assertFalse(partialUser.isComplete());
        assertEquals(
                List.of("interestedIn", "location", "photoUrls", "pacePreferences"),
                partialUser.getMissingProfileFields());

        User completeUser = createCompleteUser("CompleteUser");
        assertTrue(completeUser.isComplete());
        assertTrue(completeUser.getMissingProfileFields().isEmpty());
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
        user.setAgeRange(18, 18, 18, 120);

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

    @Test
    @DisplayName("setEmail accepts valid format and trims input")
    void setEmail_acceptsValidFormat() {
        User user = new User(UUID.randomUUID(), "EmailUser");

        user.setEmail("  valid.user+test@example.com ");

        assertEquals("valid.user+test@example.com", user.getEmail());
    }

    @Test
    @DisplayName("setEmail rejects invalid format")
    void setEmail_rejectsInvalidFormat() {
        User user = new User(UUID.randomUUID(), "BadEmailUser");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> user.setEmail("not-an-email"));
        assertTrue(ex.getMessage().contains("Invalid email format"));
    }

    @Test
    @DisplayName("setPhone accepts valid format")
    void setPhone_acceptsValidFormat() {
        User user = new User(UUID.randomUUID(), "PhoneUser");

        user.setPhone("+1 (555) 123-4567");

        assertEquals("+15551234567", user.getPhone());
    }

    @Test
    @DisplayName("setPhone rejects invalid format")
    void setPhone_rejectsInvalidFormat() {
        User user = new User(UUID.randomUUID(), "BadPhoneUser");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> user.setPhone("abc123"));
        assertTrue(ex.getMessage().contains("Invalid phone format"));
    }

    @Test
    @DisplayName("markDeleted refreshes updatedAt")
    void markDeletedRefreshesUpdatedAt() {
        User user = new User(UUID.randomUUID(), "Soft Delete User");
        Instant initialUpdatedAt = user.getUpdatedAt();
        Instant deletedAt = FIXED_INSTANT.plusSeconds(45);
        TestClock.setFixed(deletedAt);

        user.markDeleted(AppClock.now());

        assertEquals(deletedAt, user.getDeletedAt());
        assertEquals(deletedAt, user.getUpdatedAt());
        assertTrue(deletedAt.isAfter(initialUpdatedAt));
    }

    private User createCompleteUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("A great person");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.MALE);
        user.setInterestedIn(EnumSet.of(Gender.FEMALE));
        user.setLocation(32.0, 34.0);
        user.setMaxDistanceKm(50, 500);
        user.setAgeRange(20, 40, 18, 120);
        user.addPhotoUrl("https://example.com/photo.jpg");
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

    /** Tests for location validation in setLocation(). */
    @Nested
    @DisplayName("Location Validation")
    @SuppressWarnings("unused")
    class LocationValidationTests {

        @Test
        @DisplayName("Valid location with positive coordinates")
        void validLocationWithPositiveCoordinates() {
            User user = new User(UUID.randomUUID(), "Alice");
            user.setLocation(51.5, 0.1);

            assertEquals(51.5, user.getLat());
            assertEquals(0.1, user.getLon());
            assertTrue(user.hasLocationSet());
        }

        @Test
        @DisplayName("Valid location with negative coordinates")
        void validLocationWithNegativeCoordinates() {
            User user = new User(UUID.randomUUID(), "Bob");
            user.setLocation(-33.9, -151.2);

            assertEquals(-33.9, user.getLat());
            assertEquals(-151.2, user.getLon());
            assertTrue(user.hasLocationSet());
        }

        @Test
        @DisplayName("Valid location at origin (0, 0)")
        void validLocationAtOrigin() {
            User user = new User(UUID.randomUUID(), "Charlie");
            user.setLocation(0.0, 0.0);

            assertEquals(0.0, user.getLat());
            assertEquals(0.0, user.getLon());
            assertTrue(user.hasLocationSet());
        }

        @Test
        @DisplayName("Valid latitude at minimum boundary (-90)")
        void validLatitudeAtMinimumBoundary() {
            User user = new User(UUID.randomUUID(), "Dave");
            user.setLocation(-90.0, 0.0);

            assertEquals(-90.0, user.getLat());
            assertTrue(user.hasLocationSet());
        }

        @Test
        @DisplayName("Valid latitude at maximum boundary (90)")
        void validLatitudeAtMaximumBoundary() {
            User user = new User(UUID.randomUUID(), "Eve");
            user.setLocation(90.0, 0.0);

            assertEquals(90.0, user.getLat());
            assertTrue(user.hasLocationSet());
        }

        @Test
        @DisplayName("Valid longitude at minimum boundary (-180)")
        void validLongitudeAtMinimumBoundary() {
            User user = new User(UUID.randomUUID(), "Frank");
            user.setLocation(0.0, -180.0);

            assertEquals(-180.0, user.getLon());
            assertTrue(user.hasLocationSet());
        }

        @Test
        @DisplayName("Valid longitude at maximum boundary (180)")
        void validLongitudeAtMaximumBoundary() {
            User user = new User(UUID.randomUUID(), "Grace");
            user.setLocation(0.0, 180.0);

            assertEquals(180.0, user.getLon());
            assertTrue(user.hasLocationSet());
        }

        @Test
        @DisplayName("Latitude NaN throws IllegalArgumentException")
        void latitudeNaNThrows() {
            User user = new User(UUID.randomUUID(), "Hannah");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(Double.NaN, 0.0));
            assertTrue(ex.getMessage().contains("Latitude cannot be NaN or Infinity"));
        }

        @Test
        @DisplayName("Longitude NaN throws IllegalArgumentException")
        void longitudeNaNThrows() {
            User user = new User(UUID.randomUUID(), "Ivan");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(0.0, Double.NaN));
            assertTrue(ex.getMessage().contains("Longitude cannot be NaN or Infinity"));
        }

        @Test
        @DisplayName("Latitude positive Infinity throws IllegalArgumentException")
        void latitudePositiveInfinityThrows() {
            User user = new User(UUID.randomUUID(), "Jack");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(Double.POSITIVE_INFINITY, 0.0));
            assertTrue(ex.getMessage().contains("Latitude cannot be NaN or Infinity"));
        }

        @Test
        @DisplayName("Latitude negative Infinity throws IllegalArgumentException")
        void latitudeNegativeInfinityThrows() {
            User user = new User(UUID.randomUUID(), "Kelly");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(Double.NEGATIVE_INFINITY, 0.0));
            assertTrue(ex.getMessage().contains("Latitude cannot be NaN or Infinity"));
        }

        @Test
        @DisplayName("Longitude positive Infinity throws IllegalArgumentException")
        void longitudePositiveInfinityThrows() {
            User user = new User(UUID.randomUUID(), "Larry");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(0.0, Double.POSITIVE_INFINITY));
            assertTrue(ex.getMessage().contains("Longitude cannot be NaN or Infinity"));
        }

        @Test
        @DisplayName("Longitude negative Infinity throws IllegalArgumentException")
        void longitudeNegativeInfinityThrows() {
            User user = new User(UUID.randomUUID(), "Mia");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(0.0, Double.NEGATIVE_INFINITY));
            assertTrue(ex.getMessage().contains("Longitude cannot be NaN or Infinity"));
        }

        @Test
        @DisplayName("Latitude below minimum (-91) throws IllegalArgumentException")
        void latitudeBelowMinimumThrows() {
            User user = new User(UUID.randomUUID(), "Nathan");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(-90.1, 0.0));
            assertTrue(ex.getMessage().contains("Latitude must be between -90 and 90"));
        }

        @Test
        @DisplayName("Latitude above maximum (91) throws IllegalArgumentException")
        void latitudeAboveMaximumThrows() {
            User user = new User(UUID.randomUUID(), "Olivia");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(90.1, 0.0));
            assertTrue(ex.getMessage().contains("Latitude must be between -90 and 90"));
        }

        @Test
        @DisplayName("Longitude below minimum (-181) throws IllegalArgumentException")
        void longitudeBelowMinimumThrows() {
            User user = new User(UUID.randomUUID(), "Peter");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(0.0, -180.1));
            assertTrue(ex.getMessage().contains("Longitude must be between -180 and 180"));
        }

        @Test
        @DisplayName("Longitude above maximum (181) throws IllegalArgumentException")
        void longitudeAboveMaximumThrows() {
            User user = new User(UUID.randomUUID(), "Quinn");

            IllegalArgumentException ex =
                    assertThrows(IllegalArgumentException.class, () -> user.setLocation(0.0, 180.1));
            assertTrue(ex.getMessage().contains("Longitude must be between -180 and 180"));
        }

        @Test
        @DisplayName("Valid setLocation calls touch() to update timestamp")
        void setLocationCallsTouchUpdatesTimestamp() {
            User user = new User(UUID.randomUUID(), "Rachel");
            Instant initialUpdate = user.getUpdatedAt();

            // Advance to a different time
            TestClock.setFixed(FIXED_INSTANT.plusSeconds(1));

            user.setLocation(10.0, 20.0);

            assertTrue(user.getUpdatedAt().isAfter(initialUpdate));

            // Reset TestClock state
            TestClock.setFixed(FIXED_INSTANT);
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
                    .photoUrls(List.of("https://example.com/a.jpg", "https://example.com/b.jpg"))
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
            assertEquals(List.of("https://example.com/a.jpg", "https://example.com/b.jpg"), user.getPhotoUrls());
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

        @Test
        @DisplayName("StorageBuilder requires coordinates before setting hasLocationSet true")
        void storageBuilderRequiresCoordinatesBeforeEnablingLocation() {
            IllegalStateException error = assertThrows(IllegalStateException.class, this::buildLocationlessStorageUser);

            assertTrue(error.getMessage().contains("location"));
        }

        @Test
        @DisplayName("StorageBuilder clears coordinates when hasLocationSet is false")
        void storageBuilderClearsCoordinatesWhenLocationFlagIsFalse() {
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Ghost", AppClock.now())
                    .location(32.0853, 34.7818)
                    .hasLocationSet(false)
                    .build();

            assertFalse(user.hasLocationSet());
            assertFalse(user.hasLocation());
            assertEquals(0.0, user.getLat());
            assertEquals(0.0, user.getLon());
        }

        private User buildLocationlessStorageUser() {
            return User.StorageBuilder.create(UUID.randomUUID(), "Locationless", AppClock.now())
                    .hasLocationSet(true)
                    .build();
        }
    }
}
