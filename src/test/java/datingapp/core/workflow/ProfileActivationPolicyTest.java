package datingapp.core.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestUserFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileActivationPolicy")
class ProfileActivationPolicyTest {

    private static final AppConfig CONFIG = AppConfig.defaults();
    private ProfileActivationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new ProfileActivationPolicy();
    }

    /** Creates a user with all fields filled but state = INCOMPLETE (ready to activate). */
    private User completeIncompleteUser() {
        return User.StorageBuilder.create(UUID.randomUUID(), "Complete User", AppClock.now())
                .state(UserState.INCOMPLETE)
                .bio("Full bio")
                .birthDate(AppClock.today().minusYears(25))
                .gender(Gender.FEMALE)
                .interestedIn(Set.of(Gender.MALE))
                .photoUrls(List.of("http://example.com/photo.jpg"))
                .location(32.0853, 34.7818)
                .hasLocationSet(true)
                .maxDistanceKm(CONFIG.matching().maxDistanceKm())
                .ageRange(CONFIG.validation().minAge(), CONFIG.validation().maxAge())
                .pacePreferences(new PacePreferences(
                        PacePreferences.MessagingFrequency.OFTEN,
                        PacePreferences.TimeToFirstDate.FEW_DAYS,
                        PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                        PacePreferences.DepthPreference.DEEP_CHAT))
                .build();
    }

    /** Creates a user with all fields filled and state = ACTIVE. */
    private User completeActiveUser() {
        User user = completeIncompleteUser();
        user.activate();
        return user;
    }

    @Nested
    @DisplayName("canActivate")
    class CanActivate {

        @Test
        @DisplayName("allows complete INCOMPLETE user")
        void canActivateCompleteIncompleteUser() {
            assertTrue(policy.canActivate(completeIncompleteUser()).isAllowed());
        }

        @Test
        @DisplayName("denies null user")
        void cannotActivateNullUser() {
            WorkflowDecision d = policy.canActivate(null);
            assertTrue(d.isDenied());
            assertEquals("NULL_USER", ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("denies banned user")
        void cannotActivateBannedUser() {
            User u = TestUserFactory.createActiveUser("Banned");
            u.ban();
            WorkflowDecision d = policy.canActivate(u);
            assertTrue(d.isDenied());
            assertEquals("BANNED", ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("denies already-active user")
        void cannotActivateAlreadyActiveUser() {
            User u = TestUserFactory.createActiveUser("Active");
            WorkflowDecision d = policy.canActivate(u);
            assertTrue(d.isDenied());
            assertEquals(ProfileActivationPolicy.REASON_ALREADY_ACTIVE, ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("allows paused user with a complete profile")
        void canActivatePausedUserWithCompleteProfile() {
            User u = completeActiveUser();
            u.pause();
            WorkflowDecision d = policy.canActivate(u);
            assertTrue(d.isAllowed());
        }

        @Test
        @DisplayName("denies paused user with an incomplete profile")
        void cannotActivatePausedUserWithIncompleteProfile() {
            User u = TestUserFactory.createActiveUser("Paused");
            u.pause();
            u.setBio("");
            WorkflowDecision d = policy.canActivate(u);
            assertTrue(d.isDenied());
            assertEquals("INCOMPLETE_PROFILE", ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("denies user missing name")
        void cannotActivateWithMissingName() {
            User u = TestUserFactory.createIncompleteUser("");
            WorkflowDecision d = policy.canActivate(u);
            assertTrue(d.isDenied());
            assertEquals("INCOMPLETE_PROFILE", ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("denies user missing photos")
        void cannotActivateWithMissingPhotos() {
            User u = User.StorageBuilder.create(UUID.randomUUID(), "NoPhotos", AppClock.now())
                    .state(UserState.INCOMPLETE)
                    .bio("Has bio")
                    .birthDate(AppClock.today().minusYears(25))
                    .gender(Gender.MALE)
                    .interestedIn(Set.of(Gender.FEMALE))
                    .maxDistanceKm(50)
                    .ageRange(18, 40)
                    .pacePreferences(new PacePreferences(
                            PacePreferences.MessagingFrequency.OFTEN,
                            PacePreferences.TimeToFirstDate.FEW_DAYS,
                            PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                            PacePreferences.DepthPreference.DEEP_CHAT))
                    .build();
            WorkflowDecision d = policy.canActivate(u);
            assertTrue(d.isDenied());
            assertEquals("INCOMPLETE_PROFILE", ((WorkflowDecision.Denied) d).reasonCode());
        }

        @Test
        @DisplayName("denies user with incomplete pace")
        void cannotActivateWithIncompletePace() {
            User u = User.StorageBuilder.create(UUID.randomUUID(), "NoPace", AppClock.now())
                    .state(UserState.INCOMPLETE)
                    .bio("Has bio")
                    .birthDate(AppClock.today().minusYears(25))
                    .gender(Gender.MALE)
                    .interestedIn(Set.of(Gender.FEMALE))
                    .photoUrls(List.of("http://example.com/photo.jpg"))
                    .maxDistanceKm(50)
                    .ageRange(18, 40)
                    .build();
            WorkflowDecision d = policy.canActivate(u);
            assertTrue(d.isDenied());
            assertEquals("INCOMPLETE_PROFILE", ((WorkflowDecision.Denied) d).reasonCode());
        }
    }

    @Nested
    @DisplayName("tryActivate")
    class TryActivate {

        @Test
        @DisplayName("succeeds for paused user with a complete profile")
        void tryActivateSucceedsForPausedCompleteUser() {
            User u = completeActiveUser();
            u.pause();
            ProfileActivationPolicy.ActivationResult result = policy.tryActivate(u);
            assertTrue(result.activated());
            assertNotNull(result.user());
            assertEquals(UserState.ACTIVE, result.user().getState());
            assertTrue(result.decision().isAllowed());
        }

        @Test
        @DisplayName("succeeds for eligible INCOMPLETE user")
        void tryActivateSucceedsForEligibleUser() {
            User u = completeIncompleteUser();
            ProfileActivationPolicy.ActivationResult result = policy.tryActivate(u);
            assertTrue(result.activated());
            assertNotNull(result.user());
            assertEquals(UserState.ACTIVE, result.user().getState());
            assertTrue(result.decision().isAllowed());
        }

        @Test
        @DisplayName("returns decision for ineligible user")
        void tryActivateReturnsDecisionForIneligibleUser() {
            User u = TestUserFactory.createActiveUser("Active");
            u.ban();
            ProfileActivationPolicy.ActivationResult result = policy.tryActivate(u);
            assertFalse(result.activated());
            assertNull(result.user());
            assertTrue(result.decision().isDenied());
        }
    }

    @Nested
    @DisplayName("missingFields")
    class MissingFields {

        @Test
        @DisplayName("lists all gaps for empty user")
        void missingFieldsListsAllGaps() {
            User u = TestUserFactory.createIncompleteUser("Bare");
            List<String> missing = policy.missingFields(u);
            assertEquals(u.getMissingProfileFields(), missing);
        }

        @Test
        @DisplayName("delegates missing fields to the User domain rules")
        void missingFieldsDelegatesToUserDomainRules() {
            User u = User.StorageBuilder.create(UUID.randomUUID(), "Bare", AppClock.now())
                    .state(UserState.INCOMPLETE)
                    .bio("Has bio")
                    .birthDate(AppClock.today().minusYears(25))
                    .gender(Gender.MALE)
                    .interestedIn(Set.of(Gender.FEMALE))
                    .maxDistanceKm(50)
                    .ageRange(18, 40)
                    .build();

            assertEquals(u.getMissingProfileFields(), policy.missingFields(u));
        }

        @Test
        @DisplayName("returns empty for complete user")
        void missingFieldsEmptyForCompleteUser() {
            User u = completeIncompleteUser();
            List<String> missing = policy.missingFields(u);
            assertTrue(missing.isEmpty());
        }
    }
}
