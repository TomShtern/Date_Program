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
     * Creates a StorageModule using JDBI declarative SQL implementations.
     * All 16 storage interfaces now use JDBI for declarative, type-safe database access.
     *
     * @param dbManager The database manager (used for JDBI connection supplier)
     * @return Fully configured StorageModule with all JDBI implementations
     */
    public static StorageModule forH2(DatabaseManager dbManager) {
        Objects.requireNonNull(dbManager, "dbManager cannot be null");

        // Initialize JDBI for declarative SQL
        org.jdbi.v3.core.Jdbi jdbi = org.jdbi.v3.core.Jdbi.create(() -> {
                    try {
                        return dbManager.getConnection();
                    } catch (java.sql.SQLException e) {
                        throw new RuntimeException("Failed to get database connection", e);
                    }
                })
                .installPlugin(new org.jdbi.v3.sqlobject.SqlObjectPlugin());

        // Register custom type handlers
        jdbi.registerArgument(new datingapp.storage.jdbi.EnumSetArgumentFactory());
        jdbi.registerColumnMapper(new datingapp.storage.jdbi.EnumSetColumnMapper());

        // All storage implementations now use JDBI declarative SQL
        UserStorage userStorage = new datingapp.storage.jdbi.JdbiUserStorageAdapter(jdbi);
        LikeStorage likeStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiLikeStorage.class);
        MatchStorage matchStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiMatchStorage.class);
        BlockStorage blockStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiBlockStorage.class);
        ReportStorage reportStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiReportStorage.class);
        SwipeSessionStorage sessionStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiSwipeSessionStorage.class);
        UserStatsStorage userStatsStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiUserStatsStorage.class);
        PlatformStatsStorage platformStatsStorage =
                jdbi.onDemand(datingapp.storage.jdbi.JdbiPlatformStatsStorage.class);
        DailyPickStorage dailyPickStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiDailyPickStorage.class);
        UserAchievementStorage userAchievementStorage =
                jdbi.onDemand(datingapp.storage.jdbi.JdbiUserAchievementStorage.class);
        ProfileViewStorage profileViewStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiProfileViewStorage.class);
        ProfileNoteStorage profileNoteStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiProfileNoteStorage.class);
        ConversationStorage conversationStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiConversationStorage.class);
        MessageStorage messageStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiMessageStorage.class);
        FriendRequestStorage friendRequestStorage =
                jdbi.onDemand(datingapp.storage.jdbi.JdbiFriendRequestStorage.class);
        NotificationStorage notificationStorage = jdbi.onDemand(datingapp.storage.jdbi.JdbiNotificationStorage.class);

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
