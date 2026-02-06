package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.DailyService;
import datingapp.core.Gender;
import datingapp.core.Match;
import datingapp.core.MatchingService;
import datingapp.core.PacePreferences;
import datingapp.core.User;
import datingapp.core.UserInteractions.Like;
import datingapp.core.UserState;
import datingapp.core.testutil.TestStorages;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
@DisplayName("MatchesViewModel")
class MatchesViewModelTest {

    private TestStorages.Users users;
    private TestStorages.Likes likes;
    private TestStorages.Matches matches;
    private TestStorages.Blocks blocks;
    private MatchingService matchingService;
    private DailyService dailyService;
    private MatchesViewModel viewModel;
    private User currentUser;

    @BeforeEach
    void setUp() {
        users = new TestStorages.Users();
        likes = new TestStorages.Likes();
        matches = new TestStorages.Matches();
        blocks = new TestStorages.Blocks();

        AppConfig config = AppConfig.defaults();
        dailyService = new DailyService(likes, config);

        matchingService = MatchingService.builder()
                .likeStorage(likes)
                .matchStorage(matches)
                .userStorage(users)
                .blockStorage(blocks)
                .build();

        viewModel = new MatchesViewModel(matches, users, likes, blocks, matchingService, dailyService);
        currentUser = createActiveUser("Current");
        users.save(currentUser);
        AppSession.getInstance().setCurrentUser(currentUser);
    }

    @AfterEach
    void tearDown() {
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("likeBack does not create like when daily limit reached")
    void likeBackRespectsDailyLimit() {
        AppConfig zeroLimitConfig = AppConfig.builder().dailyLikeLimit(0).build();
        DailyService zeroLimitDailyService = new DailyService(likes, zeroLimitConfig);

        MatchesViewModel limitViewModel =
                new MatchesViewModel(matches, users, likes, blocks, matchingService, zeroLimitDailyService);

        User otherUser = createActiveUser("Other");
        users.save(otherUser);

        MatchesViewModel.LikeCardData likeCard = new MatchesViewModel.LikeCardData(
                otherUser.getId(), UUID.randomUUID(), otherUser.getName(), otherUser.getAge(), "Bio", "Just now", null);

        limitViewModel.likeBack(likeCard);

        assertFalse(
                likes.exists(currentUser.getId(), otherUser.getId()),
                "Like should not be created when daily limit is reached");
    }

    @Test
    @DisplayName("refreshMatches populates matches list")
    void refreshMatchesPopulatesList() {
        User otherUser = createActiveUser("MatchedUser");
        users.save(otherUser);
        Match match = Match.create(currentUser.getId(), otherUser.getId());
        matches.save(match);

        viewModel.initialize(); // Calls refreshAll which calls refreshMatches

        assertEquals(1, viewModel.getMatches().size());
        assertEquals(otherUser.getName(), viewModel.getMatches().get(0).userName());
        assertEquals(1, viewModel.matchCountProperty().get());
    }

    @Test
    @DisplayName("refreshLikesReceived populates likesReceived list")
    void refreshLikesReceivedPopulatesList() {
        User otherUser = createActiveUser("Liker");
        users.save(otherUser);
        Like like = Like.create(otherUser.getId(), currentUser.getId(), Like.Direction.LIKE);
        likes.save(like);

        viewModel.initialize();

        assertEquals(1, viewModel.getLikesReceived().size());
        assertEquals(otherUser.getName(), viewModel.getLikesReceived().get(0).userName());
        assertEquals(1, viewModel.likesReceivedCountProperty().get());
    }

    @Test
    @DisplayName("refreshLikesSent populates likesSent list")
    void refreshLikesSentPopulatesList() {
        User otherUser = createActiveUser("LikedUser");
        users.save(otherUser);
        Like like = Like.create(currentUser.getId(), otherUser.getId(), Like.Direction.LIKE);
        likes.save(like);

        viewModel.initialize();

        assertEquals(1, viewModel.getLikesSent().size());
        assertEquals(otherUser.getName(), viewModel.getLikesSent().get(0).userName());
        assertEquals(1, viewModel.likesSentCountProperty().get());
    }

    @Test
    @DisplayName("refreshLikesSent excludes matched users")
    void refreshLikesSentExcludesMatches() {
        User otherUser = createActiveUser("MatchedUser");
        users.save(otherUser);
        Like like = Like.create(currentUser.getId(), otherUser.getId(), Like.Direction.LIKE);
        likes.save(like);

        // Create match
        Match match = Match.create(currentUser.getId(), otherUser.getId());
        matches.save(match);

        viewModel.initialize();

        assertEquals(0, viewModel.getLikesSent().size(), "Matched users should not appear in likes sent");
    }

    @Test
    @DisplayName("withdrawLike deletes like and refreshes list")
    void withdrawLikeDeletesAndRefreshes() {
        User otherUser = createActiveUser("LikedUser");
        users.save(otherUser);
        Like like = Like.create(currentUser.getId(), otherUser.getId(), Like.Direction.LIKE);
        likes.save(like);

        viewModel.initialize();
        assertEquals(1, viewModel.getLikesSent().size());

        MatchesViewModel.LikeCardData likeCard = viewModel.getLikesSent().get(0);
        viewModel.withdrawLike(likeCard);

        assertFalse(likes.exists(currentUser.getId(), otherUser.getId()));
        assertEquals(0, viewModel.getLikesSent().size());
    }

    @Test
    @DisplayName("passOn records PASS and refreshes lists")
    void passOnRecordsPassAndRefreshes() {
        User otherUser = createActiveUser("Liker");
        users.save(otherUser);
        Like receivedLike = Like.create(otherUser.getId(), currentUser.getId(), Like.Direction.LIKE);
        likes.save(receivedLike);

        viewModel.initialize();
        assertEquals(1, viewModel.getLikesReceived().size());

        MatchesViewModel.LikeCardData likeCard = viewModel.getLikesReceived().get(0);
        viewModel.passOn(likeCard);

        // Check that a PASS exists from current user to other user
        assertTrue(likes.getLike(currentUser.getId(), otherUser.getId())
                .map(l -> l.direction() == Like.Direction.PASS)
                .orElse(false));

        assertEquals(0, viewModel.getLikesReceived().size());
        assertEquals(0, viewModel.getMatches().size());
    }

    @Test
    @DisplayName("likeBack creates match if user liked us")
    void likeBackCreatesMatch() {
        User otherUser = createActiveUser("Liker");
        users.save(otherUser);
        likes.save(Like.create(otherUser.getId(), currentUser.getId(), Like.Direction.LIKE));

        viewModel.initialize();
        MatchesViewModel.LikeCardData likeCard = viewModel.getLikesReceived().get(0);

        viewModel.likeBack(likeCard);

        assertTrue(
                matches.getActiveMatchesFor(currentUser.getId()).stream().anyMatch(m -> m.involves(otherUser.getId())));
        assertEquals(1, viewModel.getMatches().size());
        assertEquals(0, viewModel.getLikesReceived().size());
    }

    private static User createActiveUser(String name) {
        User user = new User(UUID.randomUUID(), name);
        user.setBirthDate(LocalDate.now().minusYears(25));
        user.setGender(Gender.OTHER);
        user.setInterestedIn(EnumSet.of(Gender.OTHER));
        user.setAgeRange(18, 60);
        user.setMaxDistanceKm(50);
        user.setLocation(40.7128, -74.0060);
        user.addPhotoUrl("http://example.com/" + name + ".jpg");
        user.setBio("Bio");
        user.setPacePreferences(new PacePreferences(
                PacePreferences.MessagingFrequency.OFTEN,
                PacePreferences.TimeToFirstDate.FEW_DAYS,
                PacePreferences.CommunicationStyle.MIX_OF_EVERYTHING,
                PacePreferences.DepthPreference.DEEP_CHAT));
        user.activate();
        if (user.getState() != UserState.ACTIVE) {
            throw new IllegalStateException("User should be active for test");
        }
        return user;
    }
}
