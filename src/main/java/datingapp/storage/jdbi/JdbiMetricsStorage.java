package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.matching.Standout;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.PlatformStats;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.storage.AnalyticsStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/** Consolidated JDBI storage for stats, achievements, profile views, daily picks, and swipe sessions. */
public final class JdbiMetricsStorage implements AnalyticsStorage, Standout.Storage {

    private final StatsDao statsDao;
    private final SessionDao sessionDao;
    private final StandoutDao standoutDao;

    public JdbiMetricsStorage(Jdbi jdbi) {
        Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.statsDao = jdbi.onDemand(StatsDao.class);
        this.sessionDao = jdbi.onDemand(SessionDao.class);
        this.standoutDao = jdbi.onDemand(StandoutDao.class);
    }

    @Override
    public void saveUserStats(UserStats stats) {
        statsDao.saveUserStats(stats);
    }

    @Override
    public Optional<UserStats> getLatestUserStats(UUID userId) {
        return statsDao.getLatestUserStats(userId);
    }

    @Override
    public List<UserStats> getUserStatsHistory(UUID userId, int limit) {
        return statsDao.getUserStatsHistory(userId, limit);
    }

    @Override
    public List<UserStats> getAllLatestUserStats() {
        return statsDao.getAllLatestUserStats();
    }

    @Override
    public int deleteUserStatsOlderThan(Instant cutoff) {
        return statsDao.deleteUserStatsOlderThan(cutoff);
    }

    @Override
    public void savePlatformStats(PlatformStats stats) {
        statsDao.savePlatformStats(stats);
    }

    @Override
    public Optional<PlatformStats> getLatestPlatformStats() {
        return statsDao.getLatestPlatformStats();
    }

    @Override
    public List<PlatformStats> getPlatformStatsHistory(int limit) {
        return statsDao.getPlatformStatsHistory(limit);
    }

    @Override
    public void recordProfileView(UUID viewerId, UUID viewedId) {
        statsDao.recordProfileView(viewerId, viewedId);
    }

    @Override
    public int getProfileViewCount(UUID userId) {
        return statsDao.getProfileViewCount(userId);
    }

    @Override
    public int getUniqueViewerCount(UUID userId) {
        return statsDao.getUniqueViewerCount(userId);
    }

    @Override
    public List<UUID> getRecentViewers(UUID userId, int limit) {
        return statsDao.getRecentViewers(userId, limit);
    }

    @Override
    public boolean hasViewedProfile(UUID viewerId, UUID viewedId) {
        return statsDao.hasViewedProfile(viewerId, viewedId);
    }

    @Override
    public void saveUserAchievement(UserAchievement achievement) {
        statsDao.saveUserAchievement(achievement);
    }

    @Override
    public List<UserAchievement> getUnlockedAchievements(UUID userId) {
        return statsDao.getUnlockedAchievements(userId);
    }

    @Override
    public boolean hasAchievement(UUID userId, Achievement achievement) {
        return statsDao.hasAchievement(userId, achievement);
    }

    @Override
    public int countUnlockedAchievements(UUID userId) {
        return statsDao.countUnlockedAchievements(userId);
    }

    @Override
    public void markDailyPickAsViewed(UUID userId, LocalDate date) {
        statsDao.markDailyPickAsViewed(userId, date);
    }

    @Override
    public boolean isDailyPickViewed(UUID userId, LocalDate date) {
        return statsDao.isDailyPickViewed(userId, date);
    }

    @Override
    public int deleteDailyPickViewsOlderThan(LocalDate before) {
        return statsDao.deleteDailyPickViewsOlderThan(before);
    }

    @Override
    public int deleteExpiredDailyPickViews(Instant cutoff) {
        return statsDao.deleteExpiredDailyPickViews(cutoff);
    }

    @Override
    public void saveSession(Session session) {
        sessionDao.save(session);
    }

    @Override
    public Optional<Session> getSession(UUID sessionId) {
        return sessionDao.get(sessionId);
    }

    @Override
    public Optional<Session> getActiveSession(UUID userId) {
        return sessionDao.getActiveSession(userId);
    }

    @Override
    public List<Session> getSessionsFor(UUID userId, int limit) {
        return sessionDao.getSessionsFor(userId, limit);
    }

    @Override
    public List<Session> getSessionsInRange(UUID userId, Instant start, Instant end) {
        return sessionDao.getSessionsInRange(userId, start, end);
    }

    @Override
    public SessionAggregates getSessionAggregates(UUID userId) {
        return sessionDao.getAggregates(userId);
    }

    @Override
    public int endStaleSessions(Duration timeout) {
        return sessionDao.endStaleSessions(timeout);
    }

    @Override
    public int deleteExpiredSessions(Instant cutoff) {
        return sessionDao.deleteExpiredSessions(cutoff);
    }

    @Override
    public void saveStandouts(UUID seekerId, List<Standout> standouts, LocalDate date) {
        for (Standout standout : standouts) {
            standoutDao.upsert(new StandoutBindingHelper(standout));
        }
    }

    @Override
    public List<Standout> getStandouts(UUID seekerId, LocalDate date) {
        return standoutDao.getStandouts(seekerId, date);
    }

    @Override
    public void markInteracted(UUID seekerId, UUID standoutUserId, LocalDate date) {
        standoutDao.markInteracted(seekerId, standoutUserId, date, AppClock.now());
    }

    @Override
    public int cleanup(LocalDate before) {
        return standoutDao.cleanup(before);
    }

    @RegisterRowMapper(UserAchievementMapper.class)
    private interface StatsDao {

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
        List<UserStats> getAllLatestUserStats();

        @SqlUpdate("DELETE FROM user_stats WHERE computed_at < :cutoff")
        int deleteUserStatsOlderThan(@Bind("cutoff") Instant cutoff);

        @SqlUpdate("""
            INSERT INTO platform_stats (id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio)
            VALUES (:id, :computedAt, :totalActiveUsers, :avgLikesReceived,
                :avgLikesGiven, :avgMatchRate, :avgLikeRatio)
            """)
        void savePlatformStats(@BindBean PlatformStats stats);

        @SqlQuery("""
            SELECT id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
            FROM platform_stats
            ORDER BY computed_at DESC
            LIMIT 1
            """)
        @RegisterRowMapper(PlatformStatsMapper.class)
        Optional<PlatformStats> getLatestPlatformStats();

        @SqlQuery("""
            SELECT id, computed_at, total_active_users,
                avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
            FROM platform_stats
            ORDER BY computed_at DESC
            LIMIT :limit
            """)
        @RegisterRowMapper(PlatformStatsMapper.class)
        List<PlatformStats> getPlatformStatsHistory(@Bind("limit") int limit);

        @SqlUpdate("""
            INSERT INTO profile_views (viewer_id, viewed_id, viewed_at)
            VALUES (:viewerId, :viewedId, :viewedAt)
            """)
        void insertView(
                @Bind("viewerId") UUID viewerId, @Bind("viewedId") UUID viewedId, @Bind("viewedAt") Instant viewedAt);

        default void recordProfileView(UUID viewerId, UUID viewedId) {
            if (viewerId.equals(viewedId)) {
                return;
            }
            insertView(viewerId, viewedId, AppClock.now());
        }

        @SqlQuery("SELECT COUNT(*) FROM profile_views WHERE viewed_id = :userId")
        int getProfileViewCount(@Bind("userId") UUID userId);

        @SqlQuery("SELECT COUNT(DISTINCT viewer_id) FROM profile_views WHERE viewed_id = :userId")
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
        boolean hasViewedProfile(@Bind("viewerId") UUID viewerId, @Bind("viewedId") UUID viewedId);

        @SqlUpdate("""
                        MERGE INTO user_achievements (id, user_id, achievement, unlocked_at)
                        KEY (user_id, achievement)
                        VALUES (:id, :userId, :achievement, :unlockedAt)
                        """)
        void saveUserAchievement(@BindBean UserAchievement achievement);

        @SqlQuery("""
                        SELECT id, user_id, achievement, unlocked_at
                        FROM user_achievements
                        WHERE user_id = :userId
                        ORDER BY unlocked_at DESC
                        """)
        List<UserAchievement> getUnlockedAchievements(@Bind("userId") UUID userId);

        @SqlQuery("""
                        SELECT COUNT(*) > 0 FROM user_achievements
                        WHERE user_id = :userId AND achievement = :achievement
                        """)
        boolean hasAchievement(@Bind("userId") UUID userId, @Bind("achievement") Achievement achievement);

        @SqlQuery("SELECT COUNT(*) FROM user_achievements WHERE user_id = :userId")
        int countUnlockedAchievements(@Bind("userId") UUID userId);

        @SqlUpdate(
                "MERGE INTO daily_pick_views (user_id, viewed_date, viewed_at) KEY (user_id, viewed_date) VALUES (:userId, :date, :at)")
        void saveDailyPickView(@Bind("userId") UUID userId, @Bind("date") LocalDate date, @Bind("at") Instant at);

        default void markDailyPickAsViewed(UUID userId, LocalDate date) {
            saveDailyPickView(userId, date, AppClock.now());
        }

        @SqlQuery("SELECT COUNT(*) > 0 FROM daily_pick_views WHERE user_id = :userId AND viewed_date = :date")
        boolean isDailyPickViewed(@Bind("userId") UUID userId, @Bind("date") LocalDate date);

        @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_date < :before")
        int deleteDailyPickViewsOlderThan(@Bind("before") LocalDate before);

        @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_at < :cutoff")
        int deleteExpiredDailyPickViews(@Bind("cutoff") Instant cutoff);
    }

    @RegisterRowMapper(SwipeSessionMapper.class)
    private interface SessionDao {

        @SqlUpdate("""
                        MERGE INTO swipe_sessions (id, user_id, started_at, last_activity_at, ended_at,
                                                   state, swipe_count, like_count, pass_count, match_count)
                        KEY (id)
                        VALUES (:id, :userId, :startedAt, :lastActivityAt, :endedAt,
                                :state, :swipeCount, :likeCount, :passCount, :matchCount)
                        """)
        void save(@BindBean Session session);

        @SqlQuery("""
                        SELECT id, user_id, started_at, last_activity_at, ended_at,
                               state, swipe_count, like_count, pass_count, match_count
                        FROM swipe_sessions WHERE id = :sessionId
                        """)
        Optional<Session> get(@Bind("sessionId") UUID sessionId);

        @SqlQuery("""
                        SELECT id, user_id, started_at, last_activity_at, ended_at,
                               state, swipe_count, like_count, pass_count, match_count
                        FROM swipe_sessions WHERE user_id = :userId AND state = 'ACTIVE'
                        LIMIT 1
                        """)
        Optional<Session> getActiveSession(@Bind("userId") UUID userId);

        @SqlQuery("""
                        SELECT id, user_id, started_at, last_activity_at, ended_at,
                               state, swipe_count, like_count, pass_count, match_count
                        FROM swipe_sessions WHERE user_id = :userId
                        ORDER BY started_at DESC LIMIT :limit
                        """)
        List<Session> getSessionsFor(@Bind("userId") UUID userId, @Bind("limit") int limit);

        @SqlQuery("""
                        SELECT id, user_id, started_at, last_activity_at, ended_at,
                               state, swipe_count, like_count, pass_count, match_count
                        FROM swipe_sessions
                        WHERE user_id = :userId AND started_at >= :start AND started_at <= :end
                        ORDER BY started_at DESC
                        """)
        List<Session> getSessionsInRange(
                @Bind("userId") UUID userId, @Bind("start") Instant start, @Bind("end") Instant end);

        @SqlQuery("""
                        SELECT
                            COUNT(*) as total_sessions,
                            COALESCE(SUM(swipe_count), 0) as total_swipes,
                            COALESCE(SUM(like_count), 0) as total_likes,
                            COALESCE(SUM(pass_count), 0) as total_passes,
                            COALESCE(SUM(match_count), 0) as total_matches,
                            COALESCE(AVG(
                                CASE WHEN ended_at IS NOT NULL
                                THEN DATEDIFF('SECOND', started_at, ended_at)
                                ELSE 0 END
                            ), 0) as avg_session_duration_seconds,
                            CASE
                                WHEN COUNT(*) = 0 THEN 0
                                ELSE COALESCE(SUM(swipe_count), 0) * 1.0 / COUNT(*)
                            END as avg_swipes_per_session,
                            CASE
                                WHEN COALESCE(SUM(
                                    CASE WHEN ended_at IS NOT NULL
                                    THEN DATEDIFF('SECOND', started_at, ended_at)
                                    ELSE 0 END
                                ), 0) = 0 THEN 0
                                ELSE COALESCE(SUM(swipe_count), 0) * 1.0 /
                                    COALESCE(SUM(
                                        CASE WHEN ended_at IS NOT NULL
                                        THEN DATEDIFF('SECOND', started_at, ended_at)
                                        ELSE 0 END
                                    ), 0)
                            END as avg_swipe_velocity
                        FROM swipe_sessions
                        WHERE user_id = :userId
                        """)
        SessionAggregates getAggregates(@Bind("userId") UUID userId);

        @SqlUpdate("""
                        UPDATE swipe_sessions
                        SET state = 'COMPLETED', ended_at = :now
                        WHERE state = 'ACTIVE' AND last_activity_at < :cutoff
                        """)
        int endStaleSessions(@Bind("now") Instant now, @Bind("cutoff") Instant cutoff);

        default int endStaleSessions(Duration timeout) {
            Instant now = AppClock.now();
            Instant cutoff = now.minus(timeout);
            return endStaleSessions(now, cutoff);
        }

        @SqlUpdate("DELETE FROM swipe_sessions WHERE started_at < :cutoff")
        int deleteExpiredSessions(@Bind("cutoff") Instant cutoff);
    }

    @RegisterRowMapper(StandoutMapper.class)
    private interface StandoutDao {

        @SqlUpdate("""
                MERGE INTO standouts (
                    id, seeker_id, standout_user_id, featured_date, rank, score, reason, created_at, interacted_at
                ) KEY (seeker_id, standout_user_id, featured_date)
                VALUES (
                    :id, :seekerId, :standoutUserId, :featuredDate, :rank, :score, :reason, :createdAt, :interactedAt
                )
                """)
        void upsert(@BindBean StandoutBindingHelper standout);

        @SqlQuery("""
                SELECT id, seeker_id, standout_user_id, featured_date, rank, score, reason, created_at, interacted_at
                FROM standouts
                WHERE seeker_id = :seekerId AND featured_date = :date
                ORDER BY rank ASC
                """)
        List<Standout> getStandouts(@Bind("seekerId") UUID seekerId, @Bind("date") LocalDate date);

        @SqlUpdate("""
                UPDATE standouts
                SET interacted_at = :now
                WHERE seeker_id = :seekerId AND standout_user_id = :standoutUserId AND featured_date = :date
                """)
        void markInteracted(
                @Bind("seekerId") UUID seekerId,
                @Bind("standoutUserId") UUID standoutUserId,
                @Bind("date") LocalDate date,
                @Bind("now") Instant now);

        @SqlUpdate("DELETE FROM standouts WHERE featured_date < :before")
        int cleanup(@Bind("before") LocalDate before);
    }

    public static class UserStatsMapper implements RowMapper<UserStats> {
        @Override
        public UserStats map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserStats(
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_id"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "computed_at"),
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

    public static class PlatformStatsMapper implements RowMapper<PlatformStats> {
        @Override
        public PlatformStats map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new PlatformStats(
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "computed_at"),
                    rs.getInt("total_active_users"),
                    rs.getDouble("avg_likes_received"),
                    rs.getDouble("avg_likes_given"),
                    rs.getDouble("avg_match_rate"),
                    rs.getDouble("avg_like_ratio"));
        }
    }

    public static class UserAchievementMapper implements RowMapper<UserAchievement> {
        @Override
        public UserAchievement map(ResultSet rs, StatementContext ctx) throws SQLException {
            var id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            var userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_id");
            var achievement = JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "achievement", Achievement.class);
            var unlockedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "unlocked_at");
            return UserAchievement.of(id, userId, achievement, unlockedAt);
        }
    }

    public static class SwipeSessionMapper implements RowMapper<Session> {
        @Override
        public Session map(ResultSet rs, StatementContext ctx) throws SQLException {
            UUID id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            UUID userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_id");
            var startedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "started_at");
            var lastActivityAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "last_activity_at");
            var endedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "ended_at");
            Session.State state = Session.State.valueOf(rs.getString("state"));
            int swipeCount = rs.getInt("swipe_count");
            int likeCount = rs.getInt("like_count");
            int passCount = rs.getInt("pass_count");
            int matchCount = rs.getInt("match_count");

            return new Session(
                    id,
                    userId,
                    startedAt,
                    lastActivityAt,
                    endedAt,
                    state,
                    swipeCount,
                    likeCount,
                    passCount,
                    matchCount);
        }
    }

    public static class StandoutBindingHelper {
        private final Standout standout;

        public StandoutBindingHelper(Standout standout) {
            this.standout = standout;
        }

        public UUID getId() {
            return standout.id();
        }

        public UUID getSeekerId() {
            return standout.seekerId();
        }

        public UUID getStandoutUserId() {
            return standout.standoutUserId();
        }

        public LocalDate getFeaturedDate() {
            return standout.featuredDate();
        }

        public int getRank() {
            return standout.rank();
        }

        public int getScore() {
            return standout.score();
        }

        public String getReason() {
            return standout.reason();
        }

        public Instant getCreatedAt() {
            return standout.createdAt();
        }

        public Instant getInteractedAt() {
            return standout.interactedAt();
        }
    }

    public static class StandoutMapper implements RowMapper<Standout> {
        @Override
        public Standout map(ResultSet rs, StatementContext ctx) throws SQLException {
            Instant interactedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "interacted_at");
            return Standout.fromDatabase(
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "seeker_id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "standout_user_id"),
                    rs.getDate("featured_date").toLocalDate(),
                    rs.getInt("rank"),
                    rs.getInt("score"),
                    rs.getString("reason"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at"),
                    interactedAt);
        }
    }
}
