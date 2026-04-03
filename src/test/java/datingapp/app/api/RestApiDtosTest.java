package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.api.RestApiDtos.AchievementSnapshotDto;
import datingapp.app.api.RestApiDtos.BlockedUsersResponse;
import datingapp.app.api.RestApiDtos.ConfirmVerificationResponse;
import datingapp.app.api.RestApiDtos.ErrorResponse;
import datingapp.app.api.RestApiDtos.HealthResponse;
import datingapp.app.api.RestApiDtos.MessageDto;
import datingapp.app.api.RestApiDtos.StartVerificationResponse;
import datingapp.app.api.RestApiDtos.UserDetail;
import datingapp.app.api.RestApiDtos.UserSummary;
import datingapp.app.usecase.profile.ProfileInsightsUseCases;
import datingapp.app.usecase.profile.VerificationUseCases;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for REST API DTOs.
 *
 * <p>Tests the DTO records used in API responses.
 */
@SuppressWarnings("unused")
@DisplayName("REST API DTOs")
class RestApiDtosTest {

    @BeforeEach
    void setUpClock() {
        TestClock.setFixed(Instant.parse("2026-01-01T00:30:00Z"));
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("MessageDto")
    class MessageDtoTests {

        @Test
        @DisplayName("Creates from message")
        void createsFromMessage() {
            java.util.UUID senderId = java.util.UUID.randomUUID();
            String conversationId = "test-conversation";
            datingapp.core.connection.ConnectionModels.Message message =
                    datingapp.core.connection.ConnectionModels.Message.create(conversationId, senderId, "Hello!");

            MessageDto dto = MessageDto.from(message);

            assertEquals(message.id(), dto.id());
            assertEquals(conversationId, dto.conversationId());
            assertEquals(senderId, dto.senderId());
            assertEquals("Hello!", dto.content());
            assertNotNull(dto.sentAt());
        }
    }

    @Nested
    @DisplayName("ErrorResponse")
    class ErrorResponseTests {

        @Test
        @DisplayName("Creates error response")
        void createsErrorResponse() {
            ErrorResponse error = new ErrorResponse("NOT_FOUND", "User not found");

            assertEquals("NOT_FOUND", error.code());
            assertEquals("User not found", error.message());
        }
    }

    @Nested
    @DisplayName("HealthResponse")
    class HealthResponseTests {

        @Test
        @DisplayName("Creates health response")
        void createsHealthResponse() {
            HealthResponse health = new HealthResponse("ok", 12345L);

            assertEquals("ok", health.status());
            assertEquals(12345L, health.timestamp());
        }
    }

    @Nested
    @DisplayName("User DTO timezone mapping")
    class UserDtoTimezoneMappingTests {

        @Test
        @DisplayName("UserSummary uses provided timezone for age")
        void userSummaryUsesProvidedTimezoneForAge() {
            User user = testUser();
            ZoneId utc = ZoneId.of("UTC");
            ZoneId losAngeles = ZoneId.of("America/Los_Angeles");

            UserSummary utcSummary = UserSummary.from(user, utc);
            UserSummary laSummary = UserSummary.from(user, losAngeles);

            assertEquals(user.getAge(utc).orElseThrow(), utcSummary.age());
            assertEquals(user.getAge(losAngeles).orElseThrow(), laSummary.age());
            assertEquals(26, utcSummary.age());
            assertEquals(25, laSummary.age());
        }

        @Test
        @DisplayName("UserDetail uses provided timezone for age")
        void userDetailUsesProvidedTimezoneForAge() {
            User user = testUser();
            ZoneId utc = ZoneId.of("UTC");
            ZoneId losAngeles = ZoneId.of("America/Los_Angeles");

            UserDetail utcDetail = UserDetail.from(user, utc, null);
            UserDetail laDetail = UserDetail.from(user, losAngeles, null);

            assertEquals(user.getAge(utc).orElseThrow(), utcDetail.age());
            assertEquals(user.getAge(losAngeles).orElseThrow(), laDetail.age());
            assertEquals(26, utcDetail.age());
            assertEquals(25, laDetail.age());
        }

        private User testUser() {
            return buildTestUser();
        }
    }

    @Nested
    @DisplayName("Safety parity DTOs")
    class SafetyParityDtoTests {

        @Test
        @DisplayName("BlockedUsersResponse maps blocked user summaries")
        void blockedUsersResponseMapsBlockedUserSummaries() {
            BlockedUsersResponse response = BlockedUsersResponse.from(List.of(
                    new datingapp.app.usecase.social.SocialUseCases.BlockedUserSummary(UUID.randomUUID(), "Blocked")));

            assertEquals(1, response.blockedUsers().size());
            assertEquals("Blocked", response.blockedUsers().getFirst().name());
            assertEquals("Blocked profile", response.blockedUsers().getFirst().statusLabel());
        }

        @Test
        @DisplayName("StartVerificationResponse exposes the dev verification code")
        void startVerificationResponseExposesTheDevVerificationCode() {
            User user = buildTestUser();
            user.setEmail("verified@example.com");
            user.startVerification(VerificationMethod.EMAIL, "123456");

            StartVerificationResponse response =
                    StartVerificationResponse.from(new VerificationUseCases.StartVerificationResult(user, "123456"));

            assertEquals(user.getId(), response.userId());
            assertEquals("EMAIL", response.method());
            assertEquals("verified@example.com", response.contact());
            assertEquals("123456", response.devVerificationCode());
        }

        @Test
        @DisplayName("ConfirmVerificationResponse reflects verified user state")
        void confirmVerificationResponseReflectsVerifiedUserState() {
            User user = buildTestUser();
            user.startVerification(VerificationMethod.EMAIL, "123456");
            user.markVerified();

            ConfirmVerificationResponse response =
                    ConfirmVerificationResponse.from(new VerificationUseCases.ConfirmVerificationResult(user));

            assertTrue(response.verified());
            assertNotNull(response.verifiedAt());
        }

        @Test
        @DisplayName("AchievementSnapshotDto maps profile insights snapshots directly")
        void achievementSnapshotDtoMapsProfileInsightsSnapshotsDirectly() {
            UUID userId = UUID.randomUUID();
            var unlocked = datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement.create(
                    userId, Achievement.FIRST_SPARK);
            var snapshot = new ProfileInsightsUseCases.AchievementSnapshot(List.of(unlocked), List.of(unlocked));

            AchievementSnapshotDto dto = AchievementSnapshotDto.from(snapshot);

            assertEquals(1, dto.unlockedCount());
            assertEquals(1, dto.newlyUnlockedCount());
            assertEquals("First Spark", dto.unlocked().getFirst().achievementName());
        }
    }

    private static User buildTestUser() {
        User user = new User(UUID.randomUUID(), "User");
        user.setBirthDate(LocalDate.of(2000, 1, 1));
        user.setGender(Gender.FEMALE);
        user.setInterestedIn(EnumSet.of(Gender.MALE));
        return user;
    }
}
