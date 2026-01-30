package datingapp.storage.jdbi;

import datingapp.core.storage.ProfileViewStorage;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for ProfileView entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(JdbiProfileViewStorage.Mapper.class)
public interface JdbiProfileViewStorage extends ProfileViewStorage {

    @SqlUpdate("""
            INSERT INTO profile_views (viewer_id, viewed_id, viewed_at)
            VALUES (:viewerId, :viewedId, :viewedAt)
            """)
    void insertView(
            @Bind("viewerId") UUID viewerId, @Bind("viewedId") UUID viewedId, @Bind("viewedAt") Instant viewedAt);

    @Override
    default void recordView(UUID viewerId, UUID viewedId) {
        if (viewerId.equals(viewedId)) {
            return; // Don't record self-views
        }
        insertView(viewerId, viewedId, Instant.now());
    }

    @SqlQuery("SELECT COUNT(*) FROM profile_views WHERE viewed_id = :userId")
    @Override
    int getViewCount(@Bind("userId") UUID userId);

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
    boolean hasViewed(@Bind("viewerId") UUID viewerId, @Bind("viewedId") UUID viewedId);

    /**
     * Simple record to hold profile view data from the database.
     * Internal to the mapper, used for full row mapping when needed.
     */
    record ProfileView(long id, UUID viewerId, UUID viewedId, Instant viewedAt) {}

    /**
     * Row mapper for ProfileView records - inlined from former ProfileViewMapper
     * class.
     */
    class Mapper implements RowMapper<ProfileView> {
        @Override
        public ProfileView map(ResultSet rs, StatementContext ctx) throws SQLException {
            long id = rs.getLong("id");
            UUID viewerId = MapperHelper.readUuid(rs, "viewer_id");
            UUID viewedId = MapperHelper.readUuid(rs, "viewed_id");
            Instant viewedAt = MapperHelper.readInstant(rs, "viewed_at");

            return new ProfileView(id, viewerId, viewedId, viewedAt);
        }
    }
}
