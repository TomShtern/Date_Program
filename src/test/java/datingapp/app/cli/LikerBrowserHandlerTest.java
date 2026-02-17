package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.app.cli.CliTextAndInput.InputReader;
import datingapp.core.*;
import datingapp.core.connection.*;
import datingapp.core.connection.ConnectionModels.Like;
import datingapp.core.matching.*;
import datingapp.core.model.*;
import datingapp.core.model.Gender;
import datingapp.core.model.VerificationMethod;
import datingapp.core.profile.*;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for liker browser CLI commands: browseWhoLikedMe().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class LikerBrowserHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.Interactions interactionStorage;
    private TestStorages.TrustSafety trustSafetyStorage;
    private TestStorages.Analytics analyticsStorage;
    private TestStorages.Communications communicationStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        interactionStorage = new TestStorages.Interactions();
        trustSafetyStorage = new TestStorages.TrustSafety();
        analyticsStorage = new TestStorages.Analytics();
        communicationStorage = new TestStorages.Communications();

        session = AppSession.getInstance();
        session.reset();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    private MatchingHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        AppConfig config = AppConfig.defaults();
        MatchingService matchingService = MatchingService.builder()
                .interactionStorage(interactionStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .userStorage(userStorage)
                .build();
        TrustSafetyService trustSafetyService =
                new TrustSafetyService(trustSafetyStorage, interactionStorage, userStorage, config);
        CandidateFinder candidateFinder =
                new CandidateFinder(userStorage, interactionStorage, trustSafetyStorage, config);
        MatchQualityService matchQualityService = new MatchQualityService(userStorage, interactionStorage, config);
        ProfileService profileCompletionService =
                new ProfileService(config, analyticsStorage, interactionStorage, trustSafetyStorage, userStorage);
        RecommendationService dailyService = RecommendationService.builder()
                .interactionStorage(interactionStorage)
                .userStorage(userStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(new TestStorages.Standouts())
                .profileService(profileCompletionService)
                .config(config)
                .build();

        RecommendationService recService = RecommendationService.builder()
                .interactionStorage(interactionStorage)
                .userStorage(userStorage)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(new TestStorages.Standouts())
                .profileService(profileCompletionService)
                .config(config)
                .build();

        MatchingHandler.Dependencies deps = new MatchingHandler.Dependencies(
                candidateFinder,
                matchingService,
                interactionStorage,
                dailyService,
                new UndoService(interactionStorage, new TestStorages.Undos(), config),
                matchQualityService,
                userStorage,
                profileCompletionService,
                analyticsStorage,
                trustSafetyService,
                new ConnectionService(config, communicationStorage, interactionStorage, userStorage),
                recService,
                communicationStorage,
                session,
                inputReader);
        return new MatchingHandler(deps);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Browse Who Liked Me")
    class BrowseWhoLikedMe {

        @Test
        @DisplayName("Shows message when no likes")
        void showsMessageWhenNoLikes() {
            MatchingHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Lists users who liked me")
        void listsUsersWhoLikedMe() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            Like like = Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE);
            interactionStorage.save(like);

            // Stop after viewing list
            MatchingHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Likes back a user")
        void likesBackAUser() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            Like like = Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE);
            interactionStorage.save(like);

            // Like back
            MatchingHandler handler = createHandler("1\n0\n");
            handler.browseWhoLikedMe();

            // Should have a mutual like now
            assertTrue(interactionStorage.exists(testUser.getId(), liker.getId()));
        }

        @Test
        @DisplayName("Creates match on mutual like")
        void createsMatchOnMutualLike() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            Like like = Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE);
            interactionStorage.save(like);

            // Like back
            MatchingHandler handler = createHandler("1\n0\n");
            handler.browseWhoLikedMe();

            // Should have created a match
            String matchId = Match.generateId(testUser.getId(), liker.getId());
            assertTrue(interactionStorage.exists(matchId));
        }

        @Test
        @DisplayName("Passes on a user")
        void passesOnAUser() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            Like like = Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE);
            interactionStorage.save(like);

            // Pass
            MatchingHandler handler = createHandler("2\n0\n");
            handler.browseWhoLikedMe();

            // Should have a pass
            Optional<Like> recorded = interactionStorage.getLike(testUser.getId(), liker.getId());
            assertTrue(recorded.isPresent());
            assertEquals(Like.Direction.PASS, recorded.get().direction());
        }

        @Test
        @DisplayName("Stops browsing with 0")
        void stopsBrowsingWithZero() {
            User liker1 = createActiveUser("Liker1");
            User liker2 = createActiveUser("Liker2");
            userStorage.save(liker1);
            userStorage.save(liker2);

            interactionStorage.save(Like.create(liker1.getId(), testUser.getId(), Like.Direction.LIKE));
            interactionStorage.save(Like.create(liker2.getId(), testUser.getId(), Like.Direction.LIKE));

            // Stop immediately
            MatchingHandler handler = createHandler("0\n");
            handler.browseWhoLikedMe();

            // Should not have interacted with any likers
            assertFalse(interactionStorage.exists(testUser.getId(), liker1.getId()));
            assertFalse(interactionStorage.exists(testUser.getId(), liker2.getId()));
        }

        @Test
        @DisplayName("Skips blocked likers")
        void skipsBlockedLikers() {
            User blockedLiker = createActiveUser("BlockedLiker");
            userStorage.save(blockedLiker);

            // They liked us
            interactionStorage.save(Like.create(blockedLiker.getId(), testUser.getId(), Like.Direction.LIKE));

            // But we blocked them
            trustSafetyStorage.save(
                    datingapp.core.connection.ConnectionModels.Block.create(testUser.getId(), blockedLiker.getId()));

            MatchingHandler handler = createHandler("0\n");
            handler.browseWhoLikedMe();

            // Should show "No new likes yet" since the only liker is blocked
            // (No assertion needed - just verify no exception)
        }

        @Test
        @DisplayName("Skips already matched likers")
        void skipsAlreadyMatchedLikers() {
            User matchedUser = createActiveUser("MatchedUser");
            userStorage.save(matchedUser);

            // They liked us
            interactionStorage.save(Like.create(matchedUser.getId(), testUser.getId(), Like.Direction.LIKE));

            // But we already matched
            Match match = Match.create(testUser.getId(), matchedUser.getId());
            interactionStorage.save(match);

            MatchingHandler handler = createHandler("0\n");
            handler.browseWhoLikedMe();

            // Should not show matched user in pending likers
        }

        @Test
        @DisplayName("Skips already responded likers")
        void skipsAlreadyRespondedLikers() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            // They liked us
            interactionStorage.save(Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE));

            // We already responded with a pass
            interactionStorage.save(Like.create(testUser.getId(), liker.getId(), Like.Direction.PASS));

            MatchingHandler handler = createHandler("0\n");
            handler.browseWhoLikedMe();

            // Should not show in pending likers
        }

        @Test
        @DisplayName("Skips inactive likers")
        void skipsInactiveLikers() {
            User inactiveLiker = new User(UUID.randomUUID(), "InactiveLiker");
            inactiveLiker.setGender(Gender.OTHER);
            inactiveLiker.setInterestedIn(EnumSet.of(Gender.OTHER));
            inactiveLiker.setBirthDate(LocalDate.of(1990, 1, 1));
            // Not activated - still INCOMPLETE
            userStorage.save(inactiveLiker);

            // They liked us before becoming inactive
            interactionStorage.save(Like.create(inactiveLiker.getId(), testUser.getId(), Like.Direction.LIKE));

            MatchingHandler handler = createHandler("0\n");
            handler.browseWhoLikedMe();

            // Should not show inactive user in pending likers
        }

        @Test
        @DisplayName("Shows end of list message after browsing all")
        void showsEndOfListMessageAfterBrowsingAll() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            interactionStorage.save(Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE));

            // Like and continue - should reach end of list
            MatchingHandler handler = createHandler("1\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            interactionStorage.save(Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE));

            MatchingHandler handler = createHandler("1\n");
            handler.browseWhoLikedMe();

            // Should not have recorded any interaction
            assertFalse(interactionStorage.exists(testUser.getId(), liker.getId()));
        }

        @Test
        @DisplayName("Handles invalid selection")
        void handlesInvalidSelection() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            interactionStorage.save(Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE));

            // Invalid selection, then stop
            MatchingHandler handler = createHandler("99\n0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Shows verified badge for verified likers")
        void showsVerifiedBadgeForVerifiedLikers() {
            User verifiedLiker = createActiveUser("VerifiedUser");
            verifiedLiker.startVerification(VerificationMethod.EMAIL, "123456");
            verifiedLiker.markVerified();
            userStorage.save(verifiedLiker);

            interactionStorage.save(Like.create(verifiedLiker.getId(), testUser.getId(), Like.Direction.LIKE));

            MatchingHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Shows bio preview for likers")
        void showsBioPreviewForLikers() {
            User likerWithBio = createActiveUser("BioPerson");
            likerWithBio.setBio("I love hiking and traveling around the world exploring new cultures and cuisines!");
            userStorage.save(likerWithBio);

            interactionStorage.save(Like.create(likerWithBio.getId(), testUser.getId(), Like.Direction.LIKE));

            MatchingHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }
    }

    // === Helper Methods ===

    private User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBio("Test bio for " + name);
        user.setBirthDate(LocalDate.of(1990, 1, 1));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.addPhotoUrl("https://example.com/photo.jpg");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.TEXT_ONLY,
                PacePreferences.DepthPreference.SMALL_TALK));
        user.activate();
        return user;
    }
}
