package datingapp.app.usecase.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import datingapp.app.usecase.common.UserContext;
import datingapp.core.AppConfig;
import datingapp.core.connection.ConnectionModels.Conversation;
import datingapp.core.connection.ConnectionModels.FriendRequest;
import datingapp.core.connection.ConnectionModels.Message;
import datingapp.core.connection.ConnectionModels.Notification;
import datingapp.core.connection.ConnectionService;
import datingapp.core.matching.CandidateFinder;
import datingapp.core.matching.RecommendationService;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.model.Match;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.ProfileService;
import datingapp.core.testutil.TestAchievementService;
import datingapp.core.testutil.TestClock;
import datingapp.core.testutil.TestStorages;
import datingapp.core.testutil.TestUserFactory;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DashboardUseCases")
class DashboardUseCasesTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T12:00:00Z");
    private static final UUID CURRENT_USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID MATCHED_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID DAILY_PICK_USER_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private TestStorages.Users users;
    private TestStorages.Interactions interactions;
    private TestStorages.Communications communications;
    private ProfileService profileService;
    private DashboardUseCases useCases;
    private User currentUser;

    @BeforeEach
    void setUp() {
        TestClock.setFixed(FIXED_INSTANT);
        AppConfig config = AppConfig.defaults();
        users = new TestStorages.Users();
        communications = new TestStorages.Communications();
        interactions = new TestStorages.Interactions(communications);
        TestStorages.TrustSafety trustSafetyStorage = new TestStorages.TrustSafety();
        TestStorages.Analytics analyticsStorage = new TestStorages.Analytics();
        profileService = new ProfileService(users);
        CandidateFinder candidateFinder =
                new CandidateFinder(users, interactions, trustSafetyStorage, ZoneId.of("UTC"));
        RecommendationService recommendationService = RecommendationService.builder()
                .interactionStorage(interactions)
                .userStorage(users)
                .trustSafetyStorage(trustSafetyStorage)
                .analyticsStorage(analyticsStorage)
                .candidateFinder(candidateFinder)
                .standoutStorage(new TestStorages.Standouts())
                .profileService(profileService)
                .config(config)
                .build();
        ConnectionService connectionService = new ConnectionService(config, communications, interactions, users);
        useCases = new DashboardUseCases(
                users,
                recommendationService,
                interactions,
                TestAchievementService.unlocked(
                        UserAchievement.of(
                                UUID.randomUUID(),
                                CURRENT_USER_ID,
                                Achievement.FIRST_SPARK,
                                FIXED_INSTANT.minusSeconds(60)),
                        UserAchievement.of(
                                UUID.randomUUID(),
                                CURRENT_USER_ID,
                                Achievement.POPULAR,
                                FIXED_INSTANT.minusSeconds(30))),
                connectionService,
                profileService,
                config);
        currentUser = TestUserFactory.createActiveUser(CURRENT_USER_ID, "Dashboard User");
        users.save(currentUser);
    }

    @AfterEach
    void tearDown() {
        TestClock.reset();
    }

    @Test
    @DisplayName("getDashboardSummary aggregates matches, unread state, daily pick, achievements, and nudge")
    void getDashboardSummaryAggregatesMatchesUnreadStateDailyPickAchievementsAndNudge() {
        User matchedUser = TestUserFactory.createActiveUser(MATCHED_USER_ID, "Matched User");
        User dailyPickUser = TestUserFactory.createActiveUser(DAILY_PICK_USER_ID, "Daily Pick User");
        dailyPickUser.setGender(Gender.FEMALE);
        dailyPickUser.setInterestedIn(EnumSet.of(Gender.MALE));
        dailyPickUser.setInterests(EnumSet.of(Interest.HIKING, Interest.COFFEE, Interest.TRAVEL));
        users.save(matchedUser);
        users.save(dailyPickUser);

        interactions.save(Match.create(currentUser.getId(), matchedUser.getId()));
        Conversation conversation = Conversation.create(currentUser.getId(), matchedUser.getId());
        communications.saveConversation(conversation);
        communications.saveMessage(Message.create(conversation.getId(), matchedUser.getId(), "Hello there"));
        communications.saveFriendRequest(FriendRequest.create(matchedUser.getId(), currentUser.getId()));
        communications.saveNotification(
                Notification.create(currentUser.getId(), Notification.Type.NEW_MESSAGE, "Ping", "Unread", Map.of()));

        var result = useCases.getDashboardSummary(
                new DashboardUseCases.DashboardSummaryQuery(UserContext.ui(currentUser.getId())));

        assertTrue(result.success());
        var summary = result.data();
        assertEquals(currentUser.getName(), summary.userName());
        assertEquals(profileService.calculate(currentUser).getDisplayString(), summary.completionText());
        assertEquals(1, summary.totalMatches());
        assertEquals(1, summary.unreadSummary().unreadMessages());
        assertEquals(1, summary.unreadSummary().pendingRequests());
        assertEquals(1, summary.unreadSummary().unreadNotifications());
        assertEquals(3, summary.unreadSummary().notificationCount());
        assertTrue(summary.dailyStatus().displayText().startsWith("Likes: "));
        assertTrue(summary.dailyPick().available());
        assertEquals(dailyPickUser.getId(), summary.dailyPick().userId());
        assertTrue(summary.dailyPick().displayName().startsWith("Daily Pick User, "));
        assertFalse(summary.dailyPick().alreadySeen());
        assertEquals(2, summary.achievementSummary().unlockedAchievements().size());
        assertEquals("Pick at least 3 interests so your personality shines.", summary.profileNudgeMessage());
    }
}
