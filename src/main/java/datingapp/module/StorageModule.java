package datingapp.module;

import datingapp.core.storage.BlockStorage;
import datingapp.core.storage.ConversationStorage;
import datingapp.core.storage.DailyPickStorage;
import datingapp.core.storage.FriendRequestStorage;
import datingapp.core.storage.LikeStorage;
import datingapp.core.storage.MatchStorage;
import datingapp.core.storage.MessageStorage;
import datingapp.core.storage.NotificationStorage;
import datingapp.core.storage.PlatformStatsStorage;
import datingapp.core.storage.ProfileNoteStorage;
import datingapp.core.storage.ProfileViewStorage;
import datingapp.core.storage.ReportStorage;
import datingapp.core.storage.SwipeSessionStorage;
import datingapp.core.storage.UserAchievementStorage;
import datingapp.core.storage.UserStatsStorage;
import datingapp.core.storage.UserStorage;
import datingapp.storage.DatabaseManager;
import datingapp.storage.H2ConversationStorage;
import datingapp.storage.H2LikeStorage;
import datingapp.storage.H2MatchStorage;
import datingapp.storage.H2MessageStorage;
import datingapp.storage.H2MetricsStorage;
import datingapp.storage.H2ModerationStorage;
import datingapp.storage.H2ProfileDataStorage;
import datingapp.storage.H2SocialStorage;
import datingapp.storage.H2SwipeSessionStorage;
import datingapp.storage.H2UserStatsStorage;
import datingapp.storage.H2UserStorage;
import java.util.Objects;

/**
 * Module containing all storage layer implementations. Groups all 16 storage interfaces for easy
 * dependency injection.
 *
 * <p>Currently uses H2 implementations. Future migration to JDBI will only require changes to the
 * factory method, not to consumers of this module.
 */
public record StorageModule(
        UserStorage users,
        LikeStorage likes,
        MatchStorage matches,
        BlockStorage blocks,
        ReportStorage reports,
        SwipeSessionStorage swipeSessions,
        UserStatsStorage userStats,
        PlatformStatsStorage platformStats,
        DailyPickStorage dailyPicks,
        UserAchievementStorage achievements,
        ProfileViewStorage profileViews,
        ProfileNoteStorage profileNotes,
        ConversationStorage conversations,
        MessageStorage messages,
        FriendRequestStorage friendRequests,
        NotificationStorage notifications)
        implements Module {

    public StorageModule {
        Objects.requireNonNull(users, "users storage cannot be null");
        Objects.requireNonNull(likes, "likes storage cannot be null");
        Objects.requireNonNull(matches, "matches storage cannot be null");
        Objects.requireNonNull(blocks, "blocks storage cannot be null");
        Objects.requireNonNull(reports, "reports storage cannot be null");
        Objects.requireNonNull(swipeSessions, "swipeSessions storage cannot be null");
        Objects.requireNonNull(userStats, "userStats storage cannot be null");
        Objects.requireNonNull(platformStats, "platformStats storage cannot be null");
        Objects.requireNonNull(dailyPicks, "dailyPicks storage cannot be null");
        Objects.requireNonNull(achievements, "achievements storage cannot be null");
        Objects.requireNonNull(profileViews, "profileViews storage cannot be null");
        Objects.requireNonNull(profileNotes, "profileNotes storage cannot be null");
        Objects.requireNonNull(conversations, "conversations storage cannot be null");
        Objects.requireNonNull(messages, "messages storage cannot be null");
        Objects.requireNonNull(friendRequests, "friendRequests storage cannot be null");
        Objects.requireNonNull(notifications, "notifications storage cannot be null");
    }

    /**
     * Creates a StorageModule using H2 database implementations.
     *
     * @param dbManager The H2 database manager
     * @return Fully configured StorageModule
     */
    public static StorageModule forH2(DatabaseManager dbManager) {
        Objects.requireNonNull(dbManager, "dbManager cannot be null");

        // Core storage
        UserStorage userStorage = new H2UserStorage(dbManager);
        LikeStorage likeStorage = new H2LikeStorage(dbManager);
        MatchStorage matchStorage = new H2MatchStorage(dbManager);
        SwipeSessionStorage sessionStorage = new H2SwipeSessionStorage(dbManager);
        UserStatsStorage userStatsStorage = new H2UserStatsStorage(dbManager);

        // Consolidated moderation storage (blocks + reports)
        H2ModerationStorage moderationStorage = new H2ModerationStorage(dbManager);
        BlockStorage blockStorage = moderationStorage.blocks();
        ReportStorage reportStorage = moderationStorage.reports();

        // Consolidated metrics storage (platform stats, daily picks, achievements)
        H2MetricsStorage metricsStorage = new H2MetricsStorage(dbManager);
        PlatformStatsStorage platformStatsStorage = metricsStorage.platformStats();
        DailyPickStorage dailyPickStorage = metricsStorage.dailyPicks();
        UserAchievementStorage userAchievementStorage = metricsStorage.achievements();

        // Consolidated profile data storage (views + notes)
        H2ProfileDataStorage profileDataStorage = new H2ProfileDataStorage(dbManager);
        ProfileViewStorage profileViewStorage = profileDataStorage.views();
        ProfileNoteStorage profileNoteStorage = profileDataStorage.notes();

        // Messaging storage
        ConversationStorage conversationStorage = new H2ConversationStorage(dbManager);
        MessageStorage messageStorage = new H2MessageStorage(dbManager);

        // Consolidated social storage (friend requests + notifications)
        H2SocialStorage socialStorage = new H2SocialStorage(dbManager);
        FriendRequestStorage friendRequestStorage = socialStorage.friendRequests();
        NotificationStorage notificationStorage = socialStorage.notifications();

        return new StorageModule(
                userStorage,
                likeStorage,
                matchStorage,
                blockStorage,
                reportStorage,
                sessionStorage,
                userStatsStorage,
                platformStatsStorage,
                dailyPickStorage,
                userAchievementStorage,
                profileViewStorage,
                profileNoteStorage,
                conversationStorage,
                messageStorage,
                friendRequestStorage,
                notificationStorage);
    }
}
