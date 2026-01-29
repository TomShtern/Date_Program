package datingapp.storage.jdbi;

import datingapp.core.UserInteractions.Block;
import datingapp.core.storage.BlockStorage;
import datingapp.storage.mapper.BlockMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for Block entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(BlockMapper.class)
public interface JdbiBlockStorage extends BlockStorage {

    @SqlUpdate("""
            INSERT INTO blocks (id, blocker_id, blocked_id, created_at)
            VALUES (:id, :blockerId, :blockedId, :createdAt)
            """)
    @Override
    void save(@BindBean Block block);

    @SqlQuery("""
            SELECT EXISTS (
                SELECT 1 FROM blocks
                WHERE (blocker_id = :userA AND blocked_id = :userB)
                   OR (blocker_id = :userB AND blocked_id = :userA)
            )
            """)
    @Override
    boolean isBlocked(@Bind("userA") UUID userA, @Bind("userB") UUID userB);

    @SqlQuery("""
            SELECT blocked_id FROM blocks WHERE blocker_id = :userId
            UNION
            SELECT blocker_id FROM blocks WHERE blocked_id = :userId
            """)
    @Override
    Set<UUID> getBlockedUserIds(@Bind("userId") UUID userId);

    @SqlQuery("SELECT * FROM blocks WHERE blocker_id = :blockerId")
    @Override
    List<Block> findByBlocker(@Bind("blockerId") UUID blockerId);

    @SqlUpdate("""
            DELETE FROM blocks
            WHERE blocker_id = :blockerId AND blocked_id = :blockedId
            """)
    int deleteBlock(@Bind("blockerId") UUID blockerId, @Bind("blockedId") UUID blockedId);

    @Override
    default boolean delete(UUID blockerId, UUID blockedId) {
        return deleteBlock(blockerId, blockedId) > 0;
    }

    @SqlQuery("SELECT COUNT(*) FROM blocks WHERE blocker_id = :userId")
    @Override
    int countBlocksGiven(@Bind("userId") UUID userId);

    @SqlQuery("SELECT COUNT(*) FROM blocks WHERE blocked_id = :userId")
    @Override
    int countBlocksReceived(@Bind("userId") UUID userId);
}
