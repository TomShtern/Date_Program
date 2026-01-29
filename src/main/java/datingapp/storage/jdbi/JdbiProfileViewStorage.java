package datingapp.storage.jdbi;

import datingapp.core.storage.ProfileViewStorage;
import datingapp.storage.mapper.ProfileViewMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for ProfileView entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(ProfileViewMapper.class)
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
}
