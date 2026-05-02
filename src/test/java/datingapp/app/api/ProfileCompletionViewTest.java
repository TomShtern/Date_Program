package datingapp.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.MatchPreferences.PacePreferences.CommunicationStyle;
import datingapp.core.profile.MatchPreferences.PacePreferences.DepthPreference;
import datingapp.core.profile.MatchPreferences.PacePreferences.MessagingFrequency;
import datingapp.core.profile.MatchPreferences.PacePreferences.TimeToFirstDate;
import datingapp.core.workflow.ProfileActivationPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileCompletionView")
class ProfileCompletionViewTest {

    private ProfileActivationPolicy activationPolicy;

    @BeforeEach
    void setUp() {
        activationPolicy = new ProfileActivationPolicy();
    }

    @Nested
    @DisplayName("from() mapper")
    class FromMapper {

        @Test
        @DisplayName("Reports all fields missing for empty user")
        void reportsAllFieldsMissingForEmptyUser() {
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Test", Instant.now())
                    .state(UserState.INCOMPLETE)
                    .build();

            ProfileCompletionView view = ProfileCompletionView.from(user, activationPolicy);

            assertFalse(view.profileComplete());
            assertFalse(view.canBrowse());
            assertFalse(view.canActivate());
            assertTrue(view.missingProfileFields().contains("bio"));
            assertTrue(view.missingProfileFields().contains("birthDate"));
            assertTrue(view.missingProfileFields().contains("gender"));
            assertTrue(view.missingProfileFields().contains("interestedIn"));
            assertTrue(view.missingProfileFields().contains("location"));
            assertTrue(view.missingProfileFields().contains("photoUrls"));
            assertTrue(view.missingProfileFields().contains("pacePreferences"));
        }

        @Test
        @DisplayName("Reports complete profile with canBrowse true for active user")
        void reportsCompleteProfileCanBrowseForActiveUser() {
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Complete User", Instant.now())
                    .state(UserState.ACTIVE)
                    .bio("Bio text")
                    .birthDate(java.time.LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .interestedIn(Set.of(Gender.FEMALE))
                    .photoUrls(List.of("http://example.com/photo.jpg"))
                    .location(32.0853, 34.7818)
                    .hasLocationSet(true)
                    .pacePreferences(new PacePreferences(
                            MessagingFrequency.OFTEN,
                            TimeToFirstDate.FEW_DAYS,
                            CommunicationStyle.MIX_OF_EVERYTHING,
                            DepthPreference.DEEP_CHAT))
                    .build();

            ProfileCompletionView view = ProfileCompletionView.from(user, activationPolicy);

            assertTrue(view.profileComplete());
            assertTrue(view.canBrowse());
            assertTrue(view.missingProfileFields().isEmpty());
            assertEquals(0, view.missingProfileFieldLabels().size());
            assertEquals(8, view.requiredProfileFieldCount());
        }

        @Test
        @DisplayName("Reports canActivate true for INCOMPLETE user with all fields filled")
        void reportsCanActivateForIncompleteButCompleteUser() {
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Ready User", Instant.now())
                    .state(UserState.INCOMPLETE)
                    .bio("Bio text")
                    .birthDate(java.time.LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .interestedIn(Set.of(Gender.FEMALE))
                    .photoUrls(List.of("http://example.com/photo.jpg"))
                    .location(32.0853, 34.7818)
                    .hasLocationSet(true)
                    .pacePreferences(new PacePreferences(
                            MessagingFrequency.OFTEN,
                            TimeToFirstDate.FEW_DAYS,
                            CommunicationStyle.MIX_OF_EVERYTHING,
                            DepthPreference.DEEP_CHAT))
                    .build();

            ProfileCompletionView view = ProfileCompletionView.from(user, activationPolicy);

            assertTrue(view.canActivate());
            assertFalse(view.canBrowse());
            assertTrue(view.profileComplete());
        }

        @Test
        @DisplayName("Reports human-readable labels for missing fields")
        void reportsHumanReadableLabelsForMissingFields() {
            User user = User.StorageBuilder.create(UUID.randomUUID(), "Partial User", Instant.now())
                    .state(UserState.INCOMPLETE)
                    .bio("Bio text")
                    .birthDate(java.time.LocalDate.of(2000, 1, 1))
                    .gender(Gender.MALE)
                    .interestedIn(Set.of(Gender.FEMALE))
                    .build();

            ProfileCompletionView view = ProfileCompletionView.from(user, activationPolicy);

            assertTrue(view.missingProfileFieldLabels().contains("Location"));
            assertTrue(view.missingProfileFieldLabels().contains("Photo"));
            assertTrue(view.missingProfileFieldLabels().contains("Pace Preferences"));
            assertFalse(view.missingProfileFieldLabels().contains("Bio"));
        }
    }
}
