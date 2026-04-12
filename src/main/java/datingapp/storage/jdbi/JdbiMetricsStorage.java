package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.matching.Standout;
import datingapp.core.metrics.EngagementDomain.Achievement;
import datingapp.core.metrics.EngagementDomain.Achievement.UserAchievement;
import datingapp.core.metrics.EngagementDomain.PlatformStats;
import datingapp.core.metrics.EngagementDomain.UserStats;
import datingapp.core.metrics.SwipeState.Session;
import datingapp.core.storage.AnalyticsStorage;
import datingapp.storage.DatabaseDialect;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Consolidated JDBI storage for stats, achievements, profile views, daily
 * picks, and swipe sessions.
 */
public final class JdbiMetricsStorage implements AnalyticsStorage, Standout.Storage {

    private static final String USER_ID_COLUMN = "user_id";
    private static final String USER_ID_BIND = "userId";
    private static final String VIEWED_AT_COLUMN = "viewed_at";
    private static final String ACHIEVEMENT_COLUMN = "achievement";
    private static final String CREATED_AT_BIND = "createdAt";
    private static final String CREATED_AT_COLUMN = "created_at";
    private static final String STATE_COLUMN = "state";
    private static final String SCORE_COLUMN = "score";
    private static final String REASON_COLUMN = "reason";
    private static final String STARTED_AT_COLUMN = "started_at";
    private static final String ENDED_AT_COLUMN = "ended_at";
    private static final String FEATURED_DATE_COLUMN = "featured_date";
    private static final String SEEKER_ID_COLUMN = "seeker_id";
    private static final String STANDOUT_USER_ID_COLUMN = "standout_user_id";

    private final Jdbi jdbi;
    private final StatsDao statsDao;
    private final SessionDao sessionDao;
    private final StandoutDao standoutDao;
    private final String profileViewUpsertSql;
    private final String userAchievementUpsertSql;
    private final String dailyPickViewUpsertSql;
    private final String dailyPickUserUpsertSql;
    private final String sessionUpsertSql;
    private final String sessionAggregatesSql;
    private final String standoutUpsertSql;

    public JdbiMetricsStorage(Jdbi jdbi) {
        this(jdbi, SqlDialectSupport.detectDialect(jdbi));
    }

    public JdbiMetricsStorage(Jdbi jdbi, DatabaseDialect dialect) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.statsDao = jdbi.onDemand(StatsDao.class);
        this.sessionDao = jdbi.onDemand(SessionDao.class);
        this.standoutDao = jdbi.onDemand(StandoutDao.class);
        Objects.requireNonNull(dialect, "dialect cannot be null");
        this.profileViewUpsertSql = buildProfileViewUpsertSql(dialect);
        this.userAchievementUpsertSql = buildUserAchievementUpsertSql(dialect);
        this.dailyPickViewUpsertSql = buildDailyPickViewUpsertSql(dialect);
        this.dailyPickUserUpsertSql = buildDailyPickUserUpsertSql(dialect);
        this.sessionUpsertSql = buildSessionUpsertSql(dialect);
        this.sessionAggregatesSql = buildSessionAggregatesSql(dialect);
        this.standoutUpsertSql = buildStandoutUpsertSql(dialect);
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
        if (viewerId.equals(viewedId)) {
            return;
        }
        jdbi.useHandle(handle -> {
            try (var update = handle.createUpdate(profileViewUpsertSql)) {
                update.bind("viewerId", viewerId)
                        .bind("viewedId", viewedId)
                        .bind("viewedAt", AppClock.now())
                        .execute();
            }
        });
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
        jdbi.useHandle(handle -> {
            try (var update = handle.createUpdate(userAchievementUpsertSql)) {
                update.bind("id", achievement.id())
                        .bind(USER_ID_BIND, achievement.userId())
                        .bind(ACHIEVEMENT_COLUMN, achievement.achievement())
                        .bind("unlockedAt", achievement.unlockedAt())
                        .execute();
            }
        });
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
        jdbi.useHandle(handle -> {
            try (var update = handle.createUpdate(dailyPickViewUpsertSql)) {
                update.bind(USER_ID_BIND, userId)
                        .bind("date", date)
                        .bind("at", AppClock.now())
                        .execute();
            }
        });
    }

    @Override
    public boolean isDailyPickViewed(UUID userId, LocalDate date) {
        return statsDao.isDailyPickViewed(userId, date);
    }

    @Override
    public Optional<UUID> getDailyPickUser(UUID userId, LocalDate date) {
        return statsDao.getDailyPickUser(userId, date);
    }

    @Override
    public void saveDailyPickUser(UUID userId, UUID pickedUserId, LocalDate date) {
        jdbi.useHandle(handle -> {
            try (var update = handle.createUpdate(dailyPickUserUpsertSql)) {
                update.bind(USER_ID_BIND, userId)
                        .bind("pickedUserId", pickedUserId)
                        .bind("date", date)
                        .bind(CREATED_AT_BIND, AppClock.now())
                        .execute();
            }
        });
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
    public int deleteExpiredStandouts(Instant cutoff) {
        return standoutDao.deleteExpiredStandouts(cutoff);
    }

    @Override
    public boolean markStandoutInteracted(UUID standoutId, Instant timestamp) {
        return standoutDao.markStandoutInteractedRaw(standoutId, timestamp) > 0;
    }

    @Override
    public void saveSession(Session session) {
        jdbi.useHandle(handle -> {
            try (var update = handle.createUpdate(sessionUpsertSql)) {
                update.bind("id", session.getId())
                        .bind(USER_ID_BIND, session.getUserId())
                        .bind("startedAt", session.getStartedAt())
                        .bind("lastActivityAt", session.getLastActivityAt())
                        .bind(STATE_COLUMN, session.getState())
                        .bind("swipeCount", session.getSwipeCount())
                        .bind("likeCount", session.getLikeCount())
                        .bind("passCount", session.getPassCount())
                        .bind("matchCount", session.getMatchCount());
                bindNullableInstant(update, "endedAt", session.getEndedAt());
                update.execute();
            }
        });
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
        return jdbi.withHandle(handle -> handle.createQuery(sessionAggregatesSql)
                .bind(USER_ID_BIND, userId)
                .map((rs, ctx) -> new SessionAggregates(
                        rs.getInt("total_sessions"),
                        rs.getInt("total_swipes"),
                        rs.getInt("total_likes"),
                        rs.getInt("total_passes"),
                        rs.getInt("total_matches"),
                        rs.getDouble("avg_session_duration_seconds"),
                        rs.getDouble("avg_swipes_per_session"),
                        rs.getDouble("avg_swipe_velocity")))
                .one());
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
        Objects.requireNonNull(seekerId, "seekerId cannot be null");
        Objects.requireNonNull(standouts, "standouts cannot be null");
        Objects.requireNonNull(date, "date cannot be null");
        if (standouts.isEmpty()) {
            return;
        }

        jdbi.useTransaction(handle -> {
            PreparedBatch batch = handle.prepareBatch(standoutUpsertSql);
            for (Standout standout : standouts) {
                if (standout == null) {
                    continue;
                }

                Standout normalized = standout;
                if (!seekerId.equals(standout.seekerId()) || !date.equals(standout.featuredDate())) {
                    normalized = Standout.fromDatabase(
                            standout.id(),
                            seekerId,
                            standout.standoutUserId(),
                            date,
                            standout.rank(),
                            standout.score(),
                            standout.reason(),
                            standout.createdAt(),
                            standout.interactedAt());
                }

                batch.bind("id", normalized.id())
                        .bind("seekerId", normalized.seekerId())
                        .bind("standoutUserId", normalized.standoutUserId())
                        .bind("featuredDate", normalized.featuredDate())
                        .bind("rank", normalized.rank())
                        .bind(SCORE_COLUMN, normalized.score())
                        .bind(REASON_COLUMN, normalized.reason())
                        .bind(CREATED_AT_BIND, normalized.createdAt());
                bindNullableInstant(batch, "interactedAt", normalized.interactedAt());
                batch.add();
            }

            batch.execute();
        });
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
        void saveUserStats(@BindMethods UserStats stats);

        @SqlQuery("""
                SELECT id, user_id, computed_at,
                    total_swipes_given, likes_given, passes_given, like_ratio,
                    total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                    total_matches, active_matches, match_rate,
                    blocks_given, blocks_received, reports_given, reports_received,
                    reciprocity_score, selectiveness_score, attractiveness_score
                FROM user_stats
                WHERE user_id = :userId
                ORDER BY computed_at DESC, id DESC
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
                ORDER BY computed_at DESC, id DESC
                LIMIT :limit
                """)
        @RegisterRowMapper(UserStatsMapper.class)
        List<UserStats> getUserStatsHistory(@Bind("userId") UUID userId, @Bind("limit") int limit);

        @SqlQuery("""
                SELECT id, user_id, computed_at,
                    total_swipes_given, likes_given, passes_given, like_ratio,
                    total_swipes_received, likes_received, passes_received, incoming_like_ratio,
                    total_matches, active_matches, match_rate,
                    blocks_given, blocks_received, reports_given, reports_received,
                    reciprocity_score, selectiveness_score, attractiveness_score
                FROM (
                    SELECT s.*,
                        ROW_NUMBER() OVER (
                            PARTITION BY s.user_id
                            ORDER BY s.computed_at DESC, s.id DESC
                        ) AS rn
                    FROM user_stats s
                ) ranked
                WHERE rn = 1
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
        void savePlatformStats(@BindMethods PlatformStats stats);

        @SqlQuery("""
                SELECT id, computed_at, total_active_users,
                    avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
                FROM platform_stats
                ORDER BY computed_at DESC, id DESC
                LIMIT 1
                """)
        @RegisterRowMapper(PlatformStatsMapper.class)
        Optional<PlatformStats> getLatestPlatformStats();

        @SqlQuery("""
                SELECT id, computed_at, total_active_users,
                    avg_likes_received, avg_likes_given, avg_match_rate, avg_like_ratio
                FROM platform_stats
                ORDER BY computed_at DESC, id DESC
                LIMIT :limit
                """)
        @RegisterRowMapper(PlatformStatsMapper.class)
        List<PlatformStats> getPlatformStatsHistory(@Bind("limit") int limit);

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

        @SqlQuery("SELECT COUNT(*) > 0 FROM daily_pick_views WHERE user_id = :userId AND viewed_date = :date")
        boolean isDailyPickViewed(@Bind("userId") UUID userId, @Bind("date") LocalDate date);

        @SqlQuery("SELECT picked_user_id FROM daily_picks WHERE user_id = :userId AND pick_date = :date")
        Optional<UUID> getDailyPickUser(@Bind("userId") UUID userId, @Bind("date") LocalDate date);

        @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_date < :before")
        int deleteDailyPickViewsOlderThan(@Bind("before") LocalDate before);

        @SqlUpdate("DELETE FROM daily_pick_views WHERE viewed_at < :cutoff")
        int deleteExpiredDailyPickViews(@Bind("cutoff") Instant cutoff);
    }

    @RegisterRowMapper(SwipeSessionMapper.class)
    private interface SessionDao {

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
                  ORDER BY last_activity_at DESC, started_at DESC, id DESC
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

        @SqlUpdate("DELETE FROM swipe_sessions WHERE state <> 'ACTIVE' AND started_at < :cutoff")
        int deleteExpiredSessions(@Bind("cutoff") Instant cutoff);
    }

    @RegisterRowMapper(StandoutMapper.class)
    private interface StandoutDao {

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

        @SqlUpdate("DELETE FROM standouts WHERE created_at < :cutoff")
        int deleteExpiredStandouts(@Bind("cutoff") Instant cutoff);

        @SqlUpdate("UPDATE standouts SET interacted_at = :timestamp WHERE id = :id AND interacted_at IS NULL")
        int markStandoutInteractedRaw(@Bind("id") UUID standoutId, @Bind("timestamp") Instant timestamp);
    }

    public static class UserStatsMapper implements RowMapper<UserStats> {
        @Override
        public UserStats map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new UserStats(
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, USER_ID_COLUMN),
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
            var userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, USER_ID_COLUMN);
            var achievement = JdbiTypeCodecs.SqlRowReaders.readEnum(rs, ACHIEVEMENT_COLUMN, Achievement.class);
            var unlockedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "unlocked_at");
            return UserAchievement.of(id, userId, achievement, unlockedAt);
        }
    }

    public static class SwipeSessionMapper implements RowMapper<Session> {
        @Override
        public Session map(ResultSet rs, StatementContext ctx) throws SQLException {
            UUID id = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id");
            UUID userId = JdbiTypeCodecs.SqlRowReaders.readUuid(rs, USER_ID_COLUMN);
            var startedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, STARTED_AT_COLUMN);
            var lastActivityAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "last_activity_at");
            var endedAt = JdbiTypeCodecs.SqlRowReaders.readInstant(rs, ENDED_AT_COLUMN);
            String stateStr = rs.getString(STATE_COLUMN);
            Session.MatchState state =
                    stateStr == null ? Session.MatchState.ACTIVE : Session.MatchState.valueOf(stateStr);
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
            LocalDate featuredDate = JdbiTypeCodecs.SqlRowReaders.readLocalDate(rs, FEATURED_DATE_COLUMN);
            if (featuredDate == null) {
                featuredDate = AppClock.today();
            }
            return Standout.fromDatabase(
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, SEEKER_ID_COLUMN),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, STANDOUT_USER_ID_COLUMN),
                    featuredDate,
                    rs.getInt("rank"),
                    rs.getInt(SCORE_COLUMN),
                    rs.getString(REASON_COLUMN),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, CREATED_AT_COLUMN),
                    interactedAt);
        }
    }

    private static void bindNullableInstant(SqlStatement<?> statement, String parameter, Instant value) {
        if (value != null) {
            statement.bind(parameter, value);
            return;
        }
        statement.bindNull(parameter, Types.TIMESTAMP);
    }

    private static String buildProfileViewUpsertSql(DatabaseDialect dialect) {
        return SqlDialectSupport.upsertSql(
                dialect,
                "profile_views",
                List.of(
                        new SqlDialectSupport.ColumnBinding("viewer_id", "viewerId"),
                        new SqlDialectSupport.ColumnBinding("viewed_id", "viewedId"),
                        new SqlDialectSupport.ColumnBinding(VIEWED_AT_COLUMN, "viewedAt")),
                List.of("viewer_id", "viewed_id", VIEWED_AT_COLUMN));
    }

    private static String buildUserAchievementUpsertSql(DatabaseDialect dialect) {
        return SqlDialectSupport.upsertSql(
                dialect,
                "user_achievements",
                List.of(
                        new SqlDialectSupport.ColumnBinding("id", "id"),
                        new SqlDialectSupport.ColumnBinding(USER_ID_COLUMN, USER_ID_BIND),
                        new SqlDialectSupport.ColumnBinding(ACHIEVEMENT_COLUMN, ACHIEVEMENT_COLUMN),
                        new SqlDialectSupport.ColumnBinding("unlocked_at", "unlockedAt")),
                List.of(USER_ID_COLUMN, ACHIEVEMENT_COLUMN));
    }

    private static String buildDailyPickViewUpsertSql(DatabaseDialect dialect) {
        return SqlDialectSupport.upsertSql(
                dialect,
                "daily_pick_views",
                List.of(
                        new SqlDialectSupport.ColumnBinding(USER_ID_COLUMN, USER_ID_BIND),
                        new SqlDialectSupport.ColumnBinding("viewed_date", "date"),
                        new SqlDialectSupport.ColumnBinding(VIEWED_AT_COLUMN, "at")),
                List.of(USER_ID_COLUMN, "viewed_date"));
    }

    private static String buildDailyPickUserUpsertSql(DatabaseDialect dialect) {
        return SqlDialectSupport.upsertSql(
                dialect,
                "daily_picks",
                List.of(
                        new SqlDialectSupport.ColumnBinding(USER_ID_COLUMN, USER_ID_BIND),
                        new SqlDialectSupport.ColumnBinding("pick_date", "date"),
                        new SqlDialectSupport.ColumnBinding("picked_user_id", "pickedUserId"),
                        new SqlDialectSupport.ColumnBinding(CREATED_AT_COLUMN, CREATED_AT_BIND)),
                List.of(USER_ID_COLUMN, "pick_date"));
    }

    private static String buildSessionUpsertSql(DatabaseDialect dialect) {
        return SqlDialectSupport.upsertSql(
                dialect,
                "swipe_sessions",
                List.of(
                        new SqlDialectSupport.ColumnBinding("id", "id"),
                        new SqlDialectSupport.ColumnBinding(USER_ID_COLUMN, USER_ID_BIND),
                        new SqlDialectSupport.ColumnBinding(STARTED_AT_COLUMN, "startedAt"),
                        new SqlDialectSupport.ColumnBinding("last_activity_at", "lastActivityAt"),
                        new SqlDialectSupport.ColumnBinding(ENDED_AT_COLUMN, "endedAt"),
                        new SqlDialectSupport.ColumnBinding(STATE_COLUMN, STATE_COLUMN),
                        new SqlDialectSupport.ColumnBinding("swipe_count", "swipeCount"),
                        new SqlDialectSupport.ColumnBinding("like_count", "likeCount"),
                        new SqlDialectSupport.ColumnBinding("pass_count", "passCount"),
                        new SqlDialectSupport.ColumnBinding("match_count", "matchCount")),
                List.of("id"));
    }

    private static String buildSessionAggregatesSql(DatabaseDialect dialect) {
        String sessionDurationSecondsExpression =
                SqlDialectSupport.sessionDurationSecondsExpression(dialect, STARTED_AT_COLUMN, ENDED_AT_COLUMN);
        return """
                SELECT
                    COUNT(*) as total_sessions,
                    COALESCE(SUM(swipe_count), 0) as total_swipes,
                    COALESCE(SUM(like_count), 0) as total_likes,
                    COALESCE(SUM(pass_count), 0) as total_passes,
                    COALESCE(SUM(match_count), 0) as total_matches,
                    COALESCE(AVG(
                        CASE WHEN ended_at IS NOT NULL
                        THEN %s
                        ELSE NULL END
                    ), 0) as avg_session_duration_seconds,
                    CASE
                        WHEN COUNT(*) = 0 THEN 0
                        ELSE COALESCE(SUM(swipe_count), 0) * 1.0 / COUNT(*)
                    END as avg_swipes_per_session,
                    CASE
                        WHEN COALESCE(SUM(
                            CASE WHEN ended_at IS NOT NULL
                            THEN %s
                            ELSE 0 END
                        ), 0) = 0 THEN 0
                        ELSE COALESCE(SUM(
                            CASE WHEN ended_at IS NOT NULL THEN swipe_count ELSE 0 END
                        ), 0) * 1.0 /
                            COALESCE(SUM(
                                CASE WHEN ended_at IS NOT NULL
                                THEN %s
                                ELSE 0 END
                            ), 0)
                    END as avg_swipe_velocity
                FROM swipe_sessions
                WHERE user_id = :userId
                """.formatted(
                sessionDurationSecondsExpression, sessionDurationSecondsExpression, sessionDurationSecondsExpression);
    }

    private static String buildStandoutUpsertSql(DatabaseDialect dialect) {
        return SqlDialectSupport.upsertSql(
                dialect,
                "standouts",
                List.of(
                        new SqlDialectSupport.ColumnBinding("id", "id"),
                        new SqlDialectSupport.ColumnBinding(SEEKER_ID_COLUMN, "seekerId"),
                        new SqlDialectSupport.ColumnBinding(STANDOUT_USER_ID_COLUMN, "standoutUserId"),
                        new SqlDialectSupport.ColumnBinding(FEATURED_DATE_COLUMN, "featuredDate"),
                        new SqlDialectSupport.ColumnBinding("rank", "rank"),
                        new SqlDialectSupport.ColumnBinding(SCORE_COLUMN, SCORE_COLUMN),
                        new SqlDialectSupport.ColumnBinding(REASON_COLUMN, REASON_COLUMN),
                        new SqlDialectSupport.ColumnBinding(CREATED_AT_COLUMN, CREATED_AT_BIND),
                        new SqlDialectSupport.ColumnBinding("interacted_at", "interactedAt")),
                List.of(SEEKER_ID_COLUMN, STANDOUT_USER_ID_COLUMN, FEATURED_DATE_COLUMN));
    }
}
