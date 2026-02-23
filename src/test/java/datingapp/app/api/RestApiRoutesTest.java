package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.testutil.TestClock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
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
class RestApiRoutesTest {

    @BeforeEach
    void setUpClock() {
        TestClock.setFixed(Instant.parse("2026-01-01T00:30:00Z"));
    }

    @AfterEach
    void resetClock() {
        TestClock.reset();
    }

    @Nested
    @DisplayName("RestApiServer.MessageDto")
    class MessageDtoTests {

        @Test
        @DisplayName("Creates from message")
        void createsFromMessage() {
            java.util.UUID senderId = java.util.UUID.randomUUID();
            String conversationId = "test-conversation";
            datingapp.core.connection.ConnectionModels.Message message =
                    datingapp.core.connection.ConnectionModels.Message.create(conversationId, senderId, "Hello!");

            RestApiServer.MessageDto dto = RestApiServer.MessageDto.from(message);

            assertEquals(message.id(), dto.id());
            assertEquals(conversationId, dto.conversationId());
            assertEquals(senderId, dto.senderId());
            assertEquals("Hello!", dto.content());
            assertNotNull(dto.sentAt());
        }
    }

    @Nested
    @DisplayName("RestApiServer.ErrorResponse")
    class ErrorResponseTests {

        @Test
        @DisplayName("Creates error response")
        void createsErrorResponse() {
            RestApiServer.ErrorResponse error = new RestApiServer.ErrorResponse("NOT_FOUND", "User not found");

            assertEquals("NOT_FOUND", error.code());
            assertEquals("User not found", error.message());
        }
    }

    @Nested
    @DisplayName("RestApiServer.HealthResponse")
    class HealthResponseTests {

        @Test
        @DisplayName("Creates health response")
        void createsHealthResponse() {
            RestApiServer.HealthResponse health = new RestApiServer.HealthResponse("ok", 12345L);

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

            RestApiServer.UserSummary utcSummary = RestApiServer.UserSummary.from(user, utc);
            RestApiServer.UserSummary laSummary = RestApiServer.UserSummary.from(user, losAngeles);

            assertEquals(user.getAge(utc), utcSummary.age());
            assertEquals(user.getAge(losAngeles), laSummary.age());
            assertEquals(26, utcSummary.age());
            assertEquals(25, laSummary.age());
        }

        @Test
        @DisplayName("UserDetail uses provided timezone for age")
        void userDetailUsesProvidedTimezoneForAge() {
            User user = testUser();
            ZoneId utc = ZoneId.of("UTC");
            ZoneId losAngeles = ZoneId.of("America/Los_Angeles");

            RestApiServer.UserDetail utcDetail = RestApiServer.UserDetail.from(user, utc);
            RestApiServer.UserDetail laDetail = RestApiServer.UserDetail.from(user, losAngeles);

            assertEquals(user.getAge(utc), utcDetail.age());
            assertEquals(user.getAge(losAngeles), laDetail.age());
            assertEquals(26, utcDetail.age());
            assertEquals(25, laDetail.age());
        }

        private User testUser() {
            User user = new User(UUID.randomUUID(), "User");
            user.setBirthDate(LocalDate.of(2000, 1, 1));
            user.setGender(Gender.FEMALE);
            user.setInterestedIn(EnumSet.of(Gender.MALE));
            return user;
        }
    }
}
