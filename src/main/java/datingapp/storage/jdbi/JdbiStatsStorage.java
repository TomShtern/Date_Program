package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.model.Achievement;
import datingapp.core.model.Achievement.UserAchievement;
import datingapp.core.model.Stats.PlatformStats;
import datingapp.core.model.Stats.UserStats;
import datingapp.core.storage.StatsStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Consolidated JDBI storage implementation for statistics.
 * Combines operations from former JdbiUserStatsStorage and
 * JdbiPlatformStatsStorage, and now includes ProfileView and UserAchievement operations.
 */
@RegisterRowMapper(JdbiStatsStorage.UserAchievementMapper.class)
public interface JdbiStatsStorage extends StatsStorage {

    // ═══════════════════════════════════════════════════════════════
    // User Stats Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO user_stats (id, user_id, computed_at,
                total_swipes_given, likes_given, passes_given, like_ratio,
                total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                total_matches, active_matches, match_rate,
                blocks_given, blocks_received, reports_given, reports_received,
                reciprocity_score, selectiveness_score, attractiveness_score)
            VALUES (:id, :userId, :computedAt, :totalSwipesGiven, :likesGiven, :passesGiven, :likeRatio,
                :totalSwipesReceived, :likesReceived, :passesReceived, :incomingLikeRatio,
                :totalMatches, :activeMatches, :matchRate,
                :blocksGiven, :blocksReceived, :reportsGiven, :reportsReceived,
                :reciprocityScore, :selectivenessScore, :attractivenessScore)
            """)
    @Override
    void saveUserStats(@BindBean UserStats stats);

    @SqlQuery("""
            SELECT id, user_id, computed_at,
                total_swipes_given, likes_given, passes_given, like_ratio,
                total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                total_matches, active_matches, match_rate,
                blocks_given, blocks_received, reports_given, reports_received,
                reciprocity_score, selectiveness_score, attractiveness_score
            FROM user_stats
            WHERE user_id = :userId
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    @RegisterRowMapper(UserStatsMapper.class)
    @Override
    Optional<UserStats> getLatestUserStats(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT id, user_id, computed_at,
                total_swipes_given, likes_given, passes_given, like_ratio,
                total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                total_matches, active_matches, match_rate,
                blocks_given, blocks_received, reports_given, reports_received,
                reciprocity_score, selectiveness_score, attractiveness_score
            FROM user_stats
            WHERE user_id = :userId
            ORDER BY computed_at DESC
            LIMIT :limit
            """)
    @RegisterRowMapper(UserStatsMapper.class)
    @Override
    List<UserStats> getUserStatsHistory(@Bind("userId") UUID userId, @Bind("limit") int limit);

    @SqlQuery("""
            SELECT s.id, s.user_id, s.computed_at,
                s.total_swipes_given, s.likes_given, s.passes_given, s.like_ratio,
                s.total_swipes_received, s.likes_received, s.passes_received, s.incoming_like_ratio,
                s.total_matches, s.active_matches, s.match_rate,
                s.blocks_given, s.blocks_received, s.reports_given, s.reports_received,
                s.reciprocity_score, s.selectiveness_score, s.attractiveness_score
            FROM user_stats s
            WHERE NOT EXISTS (
                SELECT 1 FROM user_stats s2
                WHERE s2.user_id = s.user_id AND s2.computed_at > s.computed_at
            )
            """)
    @RegisterRowMapper(UserStatsMapper.class)
    @Override
    List<UserStats> getAllLatestUserStats();

    @SqlUpdate("DELETE FROM user_stats WHERE computed_at < :cutoff")
    @Override
    int deleteUserStatsOlderThan(@Bind("cutoff") Instant cutoff);

    // ═══════════════════════════════════════════════════════════════
    // Platform Stats Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO platform_stats (id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio)
            VALUES (:id, :computedAt, :totalActiveUsers, :avgLikesReceived,
                :avgLikesGiven, :avgMatchRate, :avgLikeRatio)
            """)
    @Override
    void savePlatformStats(@BindBean PlatformStats stats);

    @SqlQuery("""
            SELECT id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
            FROM platform_stats
            ORDER BY computed_at DESC
            LIMIT 1
            """)
    @RegisterRowMapper(PlatformStatsMapper.class)
    @Override
    Optional<PlatformStats> getLatestPlatformStats();

    @SqlQuery("""
            SELECT id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
            FROM platform_stats
            ORDER BY computed_at DESC
            LIMIT :limit
            """)
    @RegisterRowMapper(PlatformStatsMapper.class)
    @Override
    List<PlatformStats> getPlatformStatsHistory(@Bind("limit") int limit);

    // ═══════════════════════════════════════════════════════════════
    // Profile View Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO profile_views (viewer_id, viewed_id, viewed_at)
            VALUES (:viewerId, :viewedId, :viewedAt)
            """)
    void insertView(
            @Bind("viewerId") UUID viewerId, @Bind("viewedId") UUID viewedId, @Bind("viewedAt") Instant viewedAt);

    @Override
    default void recordProfileView(UUID viewerId, UUID viewedId) {
        if (viewerId.equals(viewedId)) {
            return; // Don't record self-views
        }
        insertView(viewerId, viewedId, AppClock.now());
    }

    @SqlQuery("SELECT COUNT(*) FROM profile_views WHERE viewed_id = :userId")
    @Override
    int getProfileViewCount(@Bind("userId") UUID userId);

    @SqlQuery("SELECT COUNT(DISTINCT viewer_id) FROM profile_views WHERE viewed_id = :userId")
    @Override
    int getUniqueViewerCount(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT viewer_id, MAX(viewed_at) as last_view
            FROM profile_views
            WHERE viewed_id = :userId
            GROUP BY viewer_id
            ORDER BY last_view DESC
            LIMIT :limit
            """)
    List<UUID> getRecentViewersRaw(@Bind("userId") UUID userId, @Bind("limit") int limit);

    @Override
    default List<UUID> getRecentViewers(UUID userId, int limit) {
        return getRecentViewersRaw(userId, limit);
    }

    @SqlQuery("""
            SELECT EXISTS (
                SELECT 1 FROM profile_views
                WHERE viewer_id = :viewerId AND viewed_id = :viewedId
                LIMIT 1
            )
            """)
    @Override
    boolean hasViewedProfile(@Bind("viewerId") UUID viewerId, @Bind("viewedId") UUID viewedId);

    // ═══════════════════════════════════════════════════════════════
    // User Achievement Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
                        MERGE INTO user_achievements (id, user_id, achievement, unlocked_at)
                        KEY (user_id, achievement)
                        VALUES (:id, :userId, :achievement, :unlockedAt)
                        """)
    @Override
    void saveUserAchievement(@BindBean UserAchievement achievement);

    @SqlQuery("""
                        SELECT id, user_id, achievement, unlocked_at
                        FROM user_achievements
                        WHERE user_id = :userId
                        ORDER BY unlocked_at DESC
                        """)
    @Override
    List<UserAchievement> getUnlockedAchievements(@Bind("userId") UUID userId);

    @SqlQuery("""
                        SELECT COUNT(*) > 0 FROM user_achievements
                        WHERE user_id = :userId AND achievement = :achievement
                        """)
    @Override
    boolean hasAchievement(@Bind("userId") UUID userId, @Bind("achievement") Achievement achievement);

    @SqlQuery("SELECT COUNT(*) FROM user_achievements WHERE user_id = :userId")
    @Override
    int countUnlockedAchievements(@Bind("userId") UUID userId);

    // ═══════════════════════════════════════════════════════════════
    // Daily Pick View Operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate(
            "MERGE INTO daily_pick_views (user_id, viewed_date, viewed_at) KEY (user_id, viewed_date) VALUES (:userId, :date, :at)")
    void saveDailyPickView(@Bind("userId") UUID userId, @Bind("date") LocalDate date, @Bind("at") Instant at);

    @Override
    default void markDailyPickAsViewed(UUID userId, LocalDate date) {
        saveDailyPickView(userId, date, AppClock.now());
    }

    @Override
    @SqlQuery("SELECT COUNT(*) > 0 FROM daily_pick_views WHERE user_id = :userId AND viewed_date = :date")
    boolean isDailyPickViewed(@Bind("userId") UUID userId, @Bind("date") LocalDate date);

    @Override
    @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_date < :before")
    int deleteDailyPickViewsOlderThan(@Bind("before") LocalDate before);

    // ═══════════════════════════════════════════════════════════════
    // Cleanup Operations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Deletes expired daily pick view records older than the cutoff date.
     * Used by SessionService to purge stale tracking data.
     */
    @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_at < :cutoff")
    @Override
    int deleteExpiredDailyPickViews(@Bind("cutoff") Instant cutoff);

    // ═══════════════════════════════════════════════════════════════
    // Row Mappers
    // ═══════════════════════════════════════════════════════════════

    /** Row mapper for UserStats records. */
    class UserStatsMapper implements RowMapper<UserStats> {
        @Override
        public UserStats map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserStats(
                    MapperHelper.readUuid(rs, "id"),
                    MapperHelper.readUuid(rs, "user_id"),
                    MapperHelper.readInstant(rs, "computed_at"),
                    rs.getInt("total_swipes_given"),
                    rs.getInt("likes_given"),
                    rs.getInt("passes_given"),
                    rs.getDouble("like_ratio"),
                    rs.getInt("total_swipes_received"),
                    rs.getInt("likes_received"),
                    rs.getInt("passes_received"),
                    rs.getDouble("incoming_like_ratio"),
                    rs.getInt("total_matches"),
                    rs.getInt("active_matches"),
                    rs.getDouble("match_rate"),
                    rs.getInt("blocks_given"),
                    rs.getInt("blocks_received"),
                    rs.getInt("reports_given"),
                    rs.getInt("reports_received"),
                    rs.getDouble("reciprocity_score"),
                    rs.getDouble("selectiveness_score"),
                    rs.getDouble("attractiveness_score"));
        }
    }

    /** Row mapper for PlatformStats records. */
    class PlatformStatsMapper implements RowMapper<PlatformStats> {
        @Override
        public PlatformStats map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new PlatformStats(
                    MapperHelper.readUuid(rs, "id"),
                    MapperHelper.readInstant(rs, "computed_at"),
                    rs.getInt("total_active_users"),
                    rs.getDouble("avg_likes_received"),
                    rs.getDouble("avg_likes_given"),
                    rs.getDouble("avg_match_rate"),
                    rs.getDouble("avg_like_ratio"));
        }
    }

    /** Row mapper for UserAchievement records. */
    class UserAchievementMapper implements RowMapper<UserAchievement> {
        @Override
        public UserAchievement map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = MapperHelper.readUuid(rs, "id");
            var userId = MapperHelper.readUuid(rs, "user_id");
            var achievement = MapperHelper.readEnum(rs, "achievement", Achievement.class);
            var unlockedAt = MapperHelper.readInstant(rs, "unlocked_at");

            return UserAchievement.of(id, userId, achievement, unlockedAt);
        }
    }
}
