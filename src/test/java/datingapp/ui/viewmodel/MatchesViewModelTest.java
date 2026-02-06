package datingapp.ui.viewmodel;

import static org.junit.jupiter.api.Assertions.assertFalse;

import datingapp.core.AppConfig;
import datingapp.core.AppSession;
import datingapp.core.DailyService;
import datingapp.core.Gender;
import datingapp.core.MatchingService;
import datingapp.core.PacePreferences;
import datingapp.core.User;
import datingapp.core.UserState;
import datingapp.core.testutil.TestStorages;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
@DisplayName("MatchesViewModel")
class MatchesViewModelTest {

    @AfterEach
    void tearDown() {
        AppSession.getInstance().reset();
    }

    @Test
    @DisplayName("likeBack does not create like when daily limit reached")
    void likeBackRespectsDailyLimit() {
        TestStorages.Users users = new TestStorages.Users();
        TestStorages.Likes likes = new TestStorages.Likes();
        TestStorages.Matches matches = new TestStorages.Matches();
        TestStorages.Blocks blocks = new TestStorages.Blocks();

        AppConfig config = AppConfig.builder().dailyLikeLimit(0).build();
        DailyService dailyService = new DailyService(likes, config);

        MatchingService matchingService = MatchingService.builder()
                .likeStorage(likes)
                .matchStorage(matches)
                .build();

        MatchesViewModel viewModel = new MatchesViewModel(matches, users, likes, blocks, matchingService, dailyService);

        User currentUser = createActiveUser("Current");
        User otherUser = createActiveUser("Other");
        users.save(currentUser);
        users.save(otherUser);

        AppSession.getInstance().setCurrentUser(currentUser);

        MatchesViewModel.LikeCardData likeCard = new MatchesViewModel.LikeCardData(
                otherUser.getId(), UUID.randomUUID(), otherUser.getName(), otherUser.getAge(), "Bio", "Just now", null);

        viewModel.likeBack(likeCard);

        assertFalse(
                likes.exists(currentUser.getId(), otherUser.getId()),
                "Like should not be created when daily limit is reached");
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
