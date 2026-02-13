package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.connection.*;
import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.matching.*;
import datingapp.core.metrics.*;
import datingapp.core.model.*;
import datingapp.core.model.User.ProfileNote;
import datingapp.core.profile.*;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.recommendation.*;
import datingapp.core.safety.*;
import datingapp.core.storage.UserStorage;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit tests for TrustSafetyService - consolidated from ReportServiceTest and
 * VerificationServiceTest.
 *
 * <p>Tests cover:
 * - Report functionality (auto-block, auto-ban, validation)
 * - Verification code validation (expiration, mismatch)
 */
@SuppressWarnings("unused")
@DisplayName("TrustSafetyService")
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class TrustSafetyServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");

    @BeforeEach
    void setUpClock() {
        TestClock.setFixed(FIXED_INSTANT);
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    // ============================================================
    // REPORT FUNCTIONALITY TESTS
    // ============================================================

    @Nested
    @DisplayName("Report Functionality")
    class ReportFunctionality {

        private InMemoryUserStorage userStorage;
        private TestStorages.TrustSafety trustSafetyStorage;
        private TestStorages.Interactions interactionStorage;
        private TrustSafetyService trustSafetyService;

        private User activeReporter;
        private User reportedUser;

        @BeforeEach
        void setUp() {
            userStorage = new InMemoryUserStorage();
            trustSafetyStorage = new TestStorages.TrustSafety();
            interactionStorage = new TestStorages.Interactions();

            AppConfig config = AppConfig.builder().autoBanThreshold(3).build();
            trustSafetyService = new TrustSafetyService(trustSafetyStorage, interactionStorage, userStorage, config);

            // Create test users
            activeReporter = createActiveUser("Reporter");
            reportedUser = createActiveUser("Reported");
            userStorage.save(activeReporter);
            userStorage.save(reportedUser);
        }

        @Nested
        @DisplayName("Successful Reports")
        class SuccessfulReports {

            @Test
            @DisplayName("Valid report succeeds")
            void validReportSucceeds() {
                var result = trustSafetyService.report(
                        activeReporter.getId(), reportedUser.getId(), Report.Reason.SPAM, "Sent spam messages");

                assertTrue(result.success(), "Report should succeed");
                assertFalse(result.userWasBanned(), "First report should not trigger ban");
                assertNull(result.errorMessage(), "No error message on success");
            }

            @Test
            @DisplayName("Report auto-blocks the reported user")
            void reportAutoBlocks() {
                trustSafetyService.report(activeReporter.getId(), reportedUser.getId(), Report.Reason.HARASSMENT, null);

                assertTrue(
                        trustSafetyStorage.isBlocked(activeReporter.getId(), reportedUser.getId()),
                        "Reporter should have blocked the reported user");
            }

            @Test
            @DisplayName("Report is persisted")
            void reportIsPersisted() {
                trustSafetyService.report(
                        activeReporter.getId(), reportedUser.getId(), Report.Reason.FAKE_PROFILE, "Fake photos");

                assertTrue(
                        trustSafetyStorage.hasReported(activeReporter.getId(), reportedUser.getId()),
                        "Report should be persisted");
                assertEquals(1, trustSafetyStorage.countReportsAgainst(reportedUser.getId()), "Should have 1 report");
            }
        }

        @Nested
        @DisplayName("Auto-Ban Logic")
        class AutoBanLogic {

            @Test
            @DisplayName("User is banned after 3 reports")
            void userBannedAfterThreeReports() {
                User reporter2 = createActiveUser("Reporter2");
                User reporter3 = createActiveUser("Reporter3");
                userStorage.save(reporter2);
                userStorage.save(reporter3);

                // First two reports - no ban
                trustSafetyService.report(activeReporter.getId(), reportedUser.getId(), Report.Reason.SPAM, null);
                trustSafetyService.report(reporter2.getId(), reportedUser.getId(), Report.Reason.SPAM, null);

                assertEquals(
                        User.UserState.ACTIVE,
                        userStorage.get(reportedUser.getId()).getState(),
                        "User should still be ACTIVE after 2 reports");

                // Third report - triggers ban
                var result =
                        trustSafetyService.report(reporter3.getId(), reportedUser.getId(), Report.Reason.SPAM, null);

                assertTrue(result.userWasBanned(), "Third report should trigger ban");
                assertEquals(
                        User.UserState.BANNED,
                        userStorage.get(reportedUser.getId()).getState(),
                        "User should be BANNED after 3 reports");
            }

            @Test
            @DisplayName("Custom threshold works")
            void customThresholdWorks() {
                AppConfig customConfig = AppConfig.builder().autoBanThreshold(2).build();
                TrustSafetyService customService =
                        new TrustSafetyService(trustSafetyStorage, interactionStorage, userStorage, customConfig);

                User reporter2 = createActiveUser("Reporter2");
                userStorage.save(reporter2);

                customService.report(activeReporter.getId(), reportedUser.getId(), Report.Reason.SPAM, null);
                var result = customService.report(reporter2.getId(), reportedUser.getId(), Report.Reason.SPAM, null);

                assertTrue(result.userWasBanned(), "Should ban at custom threshold of 2");
            }
        }

        @Nested
        @DisplayName("Validation Errors")
        class ValidationErrors {

            @Test
            @DisplayName("Inactive reporter cannot report")
            void inactiveReporterCannotReport() {
                User incompleteUser = new User(UUID.randomUUID(), "Incomplete");
                userStorage.save(incompleteUser);

                var result = trustSafetyService.report(
                        incompleteUser.getId(), reportedUser.getId(), Report.Reason.SPAM, null);

                assertFalse(result.success(), "Report should fail for inactive reporter");
                assertEquals("Reporter must be active user", result.errorMessage());
            }

            @Test
            @DisplayName("Cannot report non-existent user")
            void cannotReportNonexistentUser() {
                var result = trustSafetyService.report(
                        activeReporter.getId(),
                        UUID.randomUUID(), // Non-existent
                        Report.Reason.SPAM,
                        null);

                assertFalse(result.success(), "Report should fail for non-existent user");
                assertEquals("Reported user not found", result.errorMessage());
            }

            @Test
            @DisplayName("Cannot report same user twice")
            void cannotReportSameUserTwice() {
                trustSafetyService.report(activeReporter.getId(), reportedUser.getId(), Report.Reason.SPAM, null);

                var result = trustSafetyService.report(
                        activeReporter.getId(), reportedUser.getId(), Report.Reason.HARASSMENT, "Different reason");

                assertFalse(result.success(), "Duplicate report should fail");
                assertEquals("Already reported this user", result.errorMessage());
            }
        }

        @Nested
        @DisplayName("Unblock Functionality")
        class UnblockFunctionality {

            @Test
            @DisplayName("Successfully unblocks a blocked user")
            void successfullyUnblocksBlockedUser() {
                Block block = Block.create(activeReporter.getId(), reportedUser.getId());
                trustSafetyStorage.save(block);

                assertTrue(
                        trustSafetyStorage.isBlocked(activeReporter.getId(), reportedUser.getId()),
                        "Users should be blocked initially");

                boolean result = trustSafetyService.unblock(activeReporter.getId(), reportedUser.getId());

                assertTrue(result, "Unblock should succeed");
                assertFalse(
                        trustSafetyStorage.isBlocked(activeReporter.getId(), reportedUser.getId()),
                        "Users should no longer be blocked");
            }

            @Test
            @DisplayName("Returns false when unblocking non-existent block")
            void returnsFalseForNonexistentBlock() {
                boolean result = trustSafetyService.unblock(activeReporter.getId(), reportedUser.getId());

                assertFalse(result, "Should return false when no block exists");
            }

            @Test
            @DisplayName("Gets list of blocked users")
            void getsListOfBlockedUsers() {
                User user1 = createActiveUser("Blocked1");
                User user2 = createActiveUser("Blocked2");
                User user3 = createActiveUser("Blocked3");
                userStorage.save(user1);
                userStorage.save(user2);
                userStorage.save(user3);

                trustSafetyStorage.save(Block.create(activeReporter.getId(), user1.getId()));
                trustSafetyStorage.save(Block.create(activeReporter.getId(), user2.getId()));
                trustSafetyStorage.save(Block.create(activeReporter.getId(), user3.getId()));

                List<User> blockedUsers = trustSafetyService.getBlockedUsers(activeReporter.getId());

                assertEquals(3, blockedUsers.size(), "Should have 3 blocked users");
                assertTrue(
                        blockedUsers.stream().anyMatch(u -> u.getId().equals(user1.getId())), "Should contain user1");
                assertTrue(
                        blockedUsers.stream().anyMatch(u -> u.getId().equals(user2.getId())), "Should contain user2");
                assertTrue(
                        blockedUsers.stream().anyMatch(u -> u.getId().equals(user3.getId())), "Should contain user3");
            }

            @Test
            @DisplayName("Returns empty list when no users are blocked")
            void returnsEmptyListWhenNoUsersBlocked() {
                List<User> blockedUsers = trustSafetyService.getBlockedUsers(activeReporter.getId());

                assertTrue(blockedUsers.isEmpty(), "Should return empty list when no users blocked");
            }
        }
    }

    // ============================================================
    // VERIFICATION CODE TESTS
    // ============================================================

    @Nested
    @DisplayName("Verification Code")
    class VerificationCode {

        @Test
        @DisplayName("Returns false when code expired")
        void returnsFalseWhenCodeExpired() {
            TrustSafetyService trustSafetyService = new TrustSafetyService(Duration.ofMinutes(15), new Random(123));

            User user = createActiveUser("ExpiredVerify");
            user.startVerification(User.VerificationMethod.EMAIL, "123456");

            // Create a copy with verification sent in the past (expired)
            User expired = copyWithVerificationSentAt(
                    user,
                    user.getVerificationSentAt() != null
                            ? user.getVerificationSentAt().minus(Duration.ofMinutes(16))
                            : AppClock.now().minus(Duration.ofMinutes(16)));

            assertFalse(trustSafetyService.verifyCode(expired, "123456"));
            assertTrue(trustSafetyService.isExpired(expired.getVerificationSentAt()));
        }

        @Test
        @DisplayName("Returns false when code mismatches")
        void returnsFalseWhenCodeMismatches() {
            TrustSafetyService trustSafetyService = new TrustSafetyService();

            User user = createActiveUser("MismatchVerify");
            user.startVerification(User.VerificationMethod.PHONE, "123456");

            assertFalse(trustSafetyService.verifyCode(user, "000000"));
            assertFalse(user.isVerified());
        }
    }

    // ============================================================
    // HELPER METHODS
    // ============================================================

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test user");
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(User.Gender.MALE);
        user.setInterestedIn(EnumSet.of(User.Gender.FEMALE));
        user.setLocation(32.0, 34.0);
        user.setMaxDistanceKm(50);
        user.setAgeRange(18, 60);
        user.addPhotoUrl("photo.jpg");
        user.setPacePreferences(new PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static User copyWithVerificationSentAt(User user, Instant sentAt) {
        return User.StorageBuilder.create(user.getId(), user.getName(), user.getCreatedAt())
                .bio(user.getBio())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .interestedIn(user.getInterestedIn())
                .location(user.getLat(), user.getLon())
                .maxDistanceKm(user.getMaxDistanceKm())
                .ageRange(user.getMinAge(), user.getMaxAge())
                .photoUrls(user.getPhotoUrls())
                .state(user.getState())
                .updatedAt(user.getUpdatedAt())
                .interests(user.getInterests())
                .smoking(user.getSmoking())
                .drinking(user.getDrinking())
                .wantsKids(user.getWantsKids())
                .lookingFor(user.getLookingFor())
                .education(user.getEducation())
                .heightCm(user.getHeightCm())
                .email(user.getEmail())
                .phone(user.getPhone())
                .verified(user.isVerified())
                .verificationMethod(user.getVerificationMethod())
                .verificationCode(user.getVerificationCode())
                .verificationSentAt(sentAt)
                .verifiedAt(user.getVerifiedAt())
                .pacePreferences(user.getPacePreferences())
                .build();
    }

    // ============================================================
    // IN-MEMORY MOCK STORAGE IMPLEMENTATIONS
    // ============================================================

    private static class InMemoryUserStorage implements UserStorage {
        private final Map<UUID, User> users = new HashMap<>();
        private final Map<String, ProfileNote> profileNotes = new java.util.concurrent.ConcurrentHashMap<>();

        private static String noteKey(UUID authorId, UUID subjectId) {
            return authorId + "_" + subjectId;
        }

        @Override
        public void save(User user) {
            users.put(user.getId(), user);
        }

        @Override
        public User get(UUID id) {
            return users.get(id);
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(users.values());
        }

        @Override
        public List<User> findActive() {
            return users.values().stream()
                    .filter(u -> u.getState() == User.UserState.ACTIVE)
                    .toList();
        }

        @Override
        public void delete(UUID id) {
            users.remove(id);
        }

        @Override
        public void saveProfileNote(ProfileNote note) {
            profileNotes.put(noteKey(note.authorId(), note.subjectId()), note);
        }

        @Override
        public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
            return Optional.ofNullable(profileNotes.get(noteKey(authorId, subjectId)));
        }

        @Override
        public List<ProfileNote> getProfileNotesByAuthor(UUID authorId) {
            return profileNotes.values().stream()
                    .filter(note -> note.authorId().equals(authorId))
                    .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
                    .toList();
        }

        @Override
        public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
            return profileNotes.remove(noteKey(authorId, subjectId)) != null;
        }
    }
}
