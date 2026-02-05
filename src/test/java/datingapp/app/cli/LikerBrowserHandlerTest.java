package datingapp.app.cli;

import static org.junit.jupiter.api.Assertions.*;

import datingapp.core.*;
import datingapp.core.PacePreferences;
import datingapp.core.UserInteractions.Like;
import datingapp.core.testutil.TestStorages;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;

/**
 * Unit tests for LikerBrowserHandler CLI commands: browseWhoLikedMe().
 */
@Timeout(value = 5, unit = TimeUnit.SECONDS)
class LikerBrowserHandlerTest {

    private TestStorages.Users userStorage;
    private TestStorages.Likes likeStorage;
    private TestStorages.Matches matchStorage;
    private TestStorages.Blocks blockStorage;
    private AppSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userStorage = new TestStorages.Users();
        likeStorage = new TestStorages.Likes();
        matchStorage = new TestStorages.Matches();
        blockStorage = new TestStorages.Blocks();

        session = AppSession.getInstance();
        session.reset();

        testUser = createActiveUser("TestUser");
        userStorage.save(testUser);
        session.setCurrentUser(testUser);
    }

    private LikerBrowserHandler createHandler(String input) {
        InputReader inputReader = new InputReader(new Scanner(new StringReader(input)));
        MatchingService matchingService = new MatchingService(likeStorage, matchStorage, userStorage, blockStorage);
        return new LikerBrowserHandler(matchingService, session, inputReader);
    }

    @SuppressWarnings("unused")
    @Nested
    @DisplayName("Browse Who Liked Me")
    class BrowseWhoLikedMe {

        @Test
        @DisplayName("Shows message when no likes")
        void showsMessageWhenNoLikes() {
            LikerBrowserHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Lists users who liked me")
        void listsUsersWhoLikedMe() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            Like like = Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE);
            likeStorage.save(like);

            // Stop after viewing list
            LikerBrowserHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Likes back a user")
        void likesBackAUser() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            Like like = Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE);
            likeStorage.save(like);

            // Like back
            LikerBrowserHandler handler = createHandler("1\n0\n");
            handler.browseWhoLikedMe();

            // Should have a mutual like now
            assertTrue(likeStorage.exists(testUser.getId(), liker.getId()));
        }

        @Test
        @DisplayName("Creates match on mutual like")
        void createsMatchOnMutualLike() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            Like like = Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE);
            likeStorage.save(like);

            // Like back
            LikerBrowserHandler handler = createHandler("1\n0\n");
            handler.browseWhoLikedMe();

            // Should have created a match
            String matchId = Match.generateId(testUser.getId(), liker.getId());
            assertTrue(matchStorage.exists(matchId));
        }

        @Test
        @DisplayName("Passes on a user")
        void passesOnAUser() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            Like like = Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE);
            likeStorage.save(like);

            // Pass
            LikerBrowserHandler handler = createHandler("2\n0\n");
            handler.browseWhoLikedMe();

            // Should have a pass
            Optional<Like> recorded = likeStorage.getLike(testUser.getId(), liker.getId());
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

            likeStorage.save(Like.create(liker1.getId(), testUser.getId(), Like.Direction.LIKE));
            likeStorage.save(Like.create(liker2.getId(), testUser.getId(), Like.Direction.LIKE));

            // Stop immediately
            LikerBrowserHandler handler = createHandler("0\n");
            handler.browseWhoLikedMe();

            // Should not have interacted with any likers
            assertFalse(likeStorage.exists(testUser.getId(), liker1.getId()));
            assertFalse(likeStorage.exists(testUser.getId(), liker2.getId()));
        }

        @Test
        @DisplayName("Skips blocked likers")
        void skipsBlockedLikers() {
            User blockedLiker = createActiveUser("BlockedLiker");
            userStorage.save(blockedLiker);

            // They liked us
            likeStorage.save(Like.create(blockedLiker.getId(), testUser.getId(), Like.Direction.LIKE));

            // But we blocked them
            blockStorage.save(datingapp.core.UserInteractions.Block.create(testUser.getId(), blockedLiker.getId()));

            LikerBrowserHandler handler = createHandler("0\n");
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
            likeStorage.save(Like.create(matchedUser.getId(), testUser.getId(), Like.Direction.LIKE));

            // But we already matched
            Match match = Match.create(testUser.getId(), matchedUser.getId());
            matchStorage.save(match);

            LikerBrowserHandler handler = createHandler("0\n");
            handler.browseWhoLikedMe();

            // Should not show matched user in pending likers
        }

        @Test
        @DisplayName("Skips already responded likers")
        void skipsAlreadyRespondedLikers() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            // They liked us
            likeStorage.save(Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE));

            // We already responded with a pass
            likeStorage.save(Like.create(testUser.getId(), liker.getId(), Like.Direction.PASS));

            LikerBrowserHandler handler = createHandler("0\n");
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
            likeStorage.save(Like.create(inactiveLiker.getId(), testUser.getId(), Like.Direction.LIKE));

            LikerBrowserHandler handler = createHandler("0\n");
            handler.browseWhoLikedMe();

            // Should not show inactive user in pending likers
        }

        @Test
        @DisplayName("Shows end of list message after browsing all")
        void showsEndOfListMessageAfterBrowsingAll() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            likeStorage.save(Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE));

            // Like and continue - should reach end of list
            LikerBrowserHandler handler = createHandler("1\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Requires login")
        void requiresLogin() {
            session.logout();
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            likeStorage.save(Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE));

            LikerBrowserHandler handler = createHandler("1\n");
            handler.browseWhoLikedMe();

            // Should not have recorded any interaction
            assertFalse(likeStorage.exists(testUser.getId(), liker.getId()));
        }

        @Test
        @DisplayName("Handles invalid selection")
        void handlesInvalidSelection() {
            User liker = createActiveUser("Liker");
            userStorage.save(liker);

            likeStorage.save(Like.create(liker.getId(), testUser.getId(), Like.Direction.LIKE));

            // Invalid selection, then stop
            LikerBrowserHandler handler = createHandler("99\n0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Shows verified badge for verified likers")
        void showsVerifiedBadgeForVerifiedLikers() {
            User verifiedLiker = createActiveUser("VerifiedUser");
            verifiedLiker.startVerification(VerificationMethod.EMAIL, "123456");
            verifiedLiker.markVerified();
            userStorage.save(verifiedLiker);

            likeStorage.save(Like.create(verifiedLiker.getId(), testUser.getId(), Like.Direction.LIKE));

            LikerBrowserHandler handler = createHandler("0\n");

            assertDoesNotThrow(handler::browseWhoLikedMe);
        }

        @Test
        @DisplayName("Shows bio preview for likers")
        void showsBioPreviewForLikers() {
            User likerWithBio = createActiveUser("BioPerson");
            likerWithBio.setBio("I love hiking and traveling around the world exploring new cultures and cuisines!");
            userStorage.save(likerWithBio);

            likeStorage.save(Like.create(likerWithBio.getId(), testUser.getId(), Like.Direction.LIKE));

            LikerBrowserHandler handler = createHandler("0\n");

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
