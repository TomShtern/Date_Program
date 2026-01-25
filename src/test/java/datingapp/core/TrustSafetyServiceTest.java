package datingapp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.Preferences.PacePreferences.CommunicationStyle;
import datingapp.core.Preferences.PacePreferences.DepthPreference;
import datingapp.core.Preferences.PacePreferences.MessagingFrequency;
import datingapp.core.Preferences.PacePreferences.TimeToFirstDate;
import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.Report;
import datingapp.core.UserInteractions.ReportStorage;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
class TrustSafetyServiceTest {

    // ============================================================
    // REPORT FUNCTIONALITY TESTS
    // ============================================================

    @Nested
    @DisplayName("Report Functionality")
    class ReportFunctionality {

        private InMemoryReportStorage reportStorage;
        private InMemoryUserStorage userStorage;
        private InMemoryBlockStorage blockStorage;
        private TrustSafetyService trustSafetyService;

        private User activeReporter;
        private User reportedUser;

        @BeforeEach
        void setUp() {
            reportStorage = new InMemoryReportStorage();
            userStorage = new InMemoryUserStorage();
            blockStorage = new InMemoryBlockStorage();

            AppConfig config = AppConfig.builder().autoBanThreshold(3).build();
            trustSafetyService = new TrustSafetyService(reportStorage, userStorage, blockStorage, config);

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
                        blockStorage.isBlocked(activeReporter.getId(), reportedUser.getId()),
                        "Reporter should have blocked the reported user");
            }

            @Test
            @DisplayName("Report is persisted")
            void reportIsPersisted() {
                trustSafetyService.report(
                        activeReporter.getId(), reportedUser.getId(), Report.Reason.FAKE_PROFILE, "Fake photos");

                assertTrue(
                        reportStorage.hasReported(activeReporter.getId(), reportedUser.getId()),
                        "Report should be persisted");
                assertEquals(1, reportStorage.countReportsAgainst(reportedUser.getId()), "Should have 1 report");
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
                        User.State.ACTIVE,
                        userStorage.get(reportedUser.getId()).getState(),
                        "User should still be ACTIVE after 2 reports");

                // Third report - triggers ban
                var result =
                        trustSafetyService.report(reporter3.getId(), reportedUser.getId(), Report.Reason.SPAM, null);

                assertTrue(result.userWasBanned(), "Third report should trigger ban");
                assertEquals(
                        User.State.BANNED,
                        userStorage.get(reportedUser.getId()).getState(),
                        "User should be BANNED after 3 reports");
            }

            @Test
            @DisplayName("Custom threshold works")
            void customThresholdWorks() {
                AppConfig customConfig = AppConfig.builder().autoBanThreshold(2).build();
                TrustSafetyService customService =
                        new TrustSafetyService(reportStorage, userStorage, blockStorage, customConfig);

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
                            : Instant.now().minus(Duration.ofMinutes(16)));

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
            assertNotEquals(Boolean.TRUE, user.isVerified());
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
        user.setPacePreferences(new Preferences.PacePreferences(
                MessagingFrequency.OFTEN,
                TimeToFirstDate.FEW_DAYS,
                CommunicationStyle.TEXT_ONLY,
                DepthPreference.DEEP_CHAT));
        user.activate();
        return user;
    }

    private static User copyWithVerificationSentAt(User user, Instant sentAt) {
        User.DatabaseRecord data = User.DatabaseRecord.builder()
                .id(user.getId())
                .name(user.getName())
                .bio(user.getBio())
                .birthDate(user.getBirthDate())
                .gender(user.getGender())
                .interestedIn(user.getInterestedIn())
                .lat(user.getLat())
                .lon(user.getLon())
                .maxDistanceKm(user.getMaxDistanceKm())
                .minAge(user.getMinAge())
                .maxAge(user.getMaxAge())
                .photoUrls(user.getPhotoUrls())
                .state(user.getState())
                .createdAt(user.getCreatedAt())
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
                .isVerified(user.isVerified())
                .verificationMethod(user.getVerificationMethod())
                .verificationCode(user.getVerificationCode())
                .verificationSentAt(sentAt)
                .verifiedAt(user.getVerifiedAt())
                .pacePreferences(user.getPacePreferences())
                .build();

        return User.fromDatabase(data);
    }

    // ============================================================
    // IN-MEMORY MOCK STORAGE IMPLEMENTATIONS
    // ============================================================

    private static class InMemoryReportStorage implements ReportStorage {
        private final List<Report> reports = new ArrayList<>();

        @Override
        public void save(Report report) {
            reports.add(report);
        }

        @Override
        public int countReportsAgainst(UUID userId) {
            return (int) reports.stream()
                    .filter(r -> r.reportedUserId().equals(userId))
                    .count();
        }

        @Override
        public boolean hasReported(UUID reporterId, UUID reportedUserId) {
            return reports.stream()
                    .anyMatch(r -> r.reporterId().equals(reporterId)
                            && r.reportedUserId().equals(reportedUserId));
        }

        @Override
        public List<Report> getReportsAgainst(UUID userId) {
            return reports.stream()
                    .filter(r -> r.reportedUserId().equals(userId))
                    .toList();
        }

        @Override
        public int countReportsBy(UUID userId) {
            return (int)
                    reports.stream().filter(r -> r.reporterId().equals(userId)).count();
        }
    }

    private static class InMemoryUserStorage implements User.Storage {
        private final Map<UUID, User> users = new HashMap<>();

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
                    .filter(u -> u.getState() == User.State.ACTIVE)
                    .toList();
        }
    }

    private static class InMemoryBlockStorage implements BlockStorage {
        private final Set<String> blocks = new HashSet<>();

        @Override
        public void save(Block block) {
            blocks.add(block.blockerId() + "->" + block.blockedId());
        }

        @Override
        public boolean isBlocked(UUID userA, UUID userB) {
            return blocks.contains(userA + "->" + userB) || blocks.contains(userB + "->" + userA);
        }

        @Override
        public Set<UUID> getBlockedUserIds(UUID userId) {
            Set<UUID> result = new HashSet<>();
            for (String block : blocks) {
                String[] parts = block.split("->");
                UUID a = UUID.fromString(parts[0]);
                UUID b = UUID.fromString(parts[1]);
                if (a.equals(userId)) result.add(b);
                if (b.equals(userId)) result.add(a);
            }
            return result;
        }

        @Override
        public int countBlocksGiven(UUID userId) {
            return (int)
                    blocks.stream().filter(s -> s.startsWith(userId + "->")).count();
        }

        @Override
        public int countBlocksReceived(UUID userId) {
            return (int) blocks.stream().filter(s -> s.endsWith("->" + userId)).count();
        }
    }
}
