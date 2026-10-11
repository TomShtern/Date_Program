package datingapp.app.usecase.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.matching.MatchingUseCases;
import datingapp.app.usecase.messaging.MessagingUseCases;
import datingapp.app.usecase.profile.ProfileMutationUseCases;
import datingapp.app.usecase.profile.ProfileNotesUseCases;
import datingapp.app.usecase.social.SocialUseCases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UseCaseError mapping")
class UseCaseErrorMappingTest {

    @Test
    @DisplayName(" RATE_LIMITED and GONE codes have factories")
    void rateLimitedAndGoneCodesHaveFactories() {
        assertEquals(
                UseCaseError.Code.RATE_LIMITED,
                UseCaseError.rateLimited("slow down").code());
        assertEquals(UseCaseError.Code.GONE, UseCaseError.gone("gone").code());
    }

    @Test
    @DisplayName("internalFailure returns a stable message and never leaks the cause")
    void internalFailureReturnsStableMessage() {
        UseCaseError error =
                UseCaseError.internalFailure("load users", new IllegalStateException("jdbc:secret-row-42"));
        assertEquals(UseCaseError.Code.INTERNAL, error.code());
        assertEquals("Failed to load users.", error.message());
    }

    @Test
    @DisplayName("stable user-facing messages do not echo internals")
    void stableMessagesDoNotEchoInternals() {
        String secret = "users table exploded at jdbc:h2:./data/dating row=42";
        assertEquals("Invalid profile update.", ProfileMutationUseCases.stableProfileUpdateMessage(secret));
        assertEquals("Invalid profile note.", ProfileNotesUseCases.stableNoteMessage(secret));
        assertEquals("Cannot archive match.", MatchingUseCases.stableArchiveMatchMessage(secret));
        assertEquals("Invalid conversation request.", MessagingUseCases.stableConversationMessage(secret));
        assertEquals("Cannot block user.", SocialUseCases.stableBlockMessage(secret));
        assertEquals("Cannot report user.", SocialUseCases.stableReportMessage(secret));
        assertEquals("Cannot update relationship.", SocialUseCases.stableRelationshipMessage(secret));
        assertTrue(MessagingUseCases.stableSendMessage(secret).startsWith("Cannot"));
    }

    @Test
    @DisplayName("age-range errors are not reported as birth-date errors")
    void ageRangeErrorsAreNotReportedAsBirthDateErrors() {
        assertEquals(
                "Invalid age range.",
                ProfileMutationUseCases.stableProfileUpdateMessage("Min age cannot exceed max age"));
        assertEquals(
                "Invalid age range.", ProfileMutationUseCases.stableProfileUpdateMessage("minAge must be at least 18"));
        assertEquals(
                "Invalid birth date.",
                ProfileMutationUseCases.stableProfileUpdateMessage("birthDate is in the future"));
    }
}
