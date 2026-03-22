package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionModels.Report.Reason;
import datingapp.core.storage.TrustSafetyStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Consolidated JDBI storage for trust &amp; safety: blocking and reporting.
 * Merges former {@code JdbiBlockStorage} and {@code JdbiReportStorage}.
 */
public interface JdbiTrustSafetyStorage extends TrustSafetyStorage {

    // ═══════════════════════════════════════════════════════════════
    // Block operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO blocks (id, blocker_id, blocked_id, created_at)
            VALUES (:id, :blockerId, :blockedId, :createdAt)
            """)
    @Override
    void save(@BindBean Block block);

    @SqlQuery("""
            SELECT EXISTS (
                SELECT 1 FROM blocks
                WHERE deleted_at IS NULL
                  AND ((blocker_id = :userA AND blocked_id = :userB)
                   OR (blocker_id = :userB AND blocked_id = :userA)
                  )
            )
            """)
    @Override
    boolean isBlocked(@Bind("userA") UUID userA, @Bind("userB") UUID userB);

    @SqlQuery("""
            SELECT blocked_id FROM blocks WHERE blocker_id = :userId AND deleted_at IS NULL
            UNION
            SELECT blocker_id FROM blocks WHERE blocked_id = :userId AND deleted_at IS NULL
            """)
    @Override
    Set<UUID> getBlockedUserIds(@Bind("userId") UUID userId);

    @RegisterRowMapper(BlockMapper.class)
    @SqlQuery("""
            SELECT id, blocker_id, blocked_id, created_at
            FROM blocks WHERE blocker_id = :blockerId AND deleted_at IS NULL
            """)
    @Override
    List<Block> findByBlocker(@Bind("blockerId") UUID blockerId);

    @SqlUpdate("""
            UPDATE blocks
            SET deleted_at = :now
            WHERE blocker_id = :blockerId AND blocked_id = :blockedId AND deleted_at IS NULL
            """)
    int deleteBlockRow(@Bind("blockerId") UUID blockerId, @Bind("blockedId") UUID blockedId, @Bind("now") Instant now);

    @Override
    default boolean deleteBlock(UUID blockerId, UUID blockedId) {
        return deleteBlockRow(blockerId, blockedId, AppClock.now()) > 0;
    }

    @SqlQuery("SELECT COUNT(*) FROM blocks WHERE blocker_id = :userId AND deleted_at IS NULL")
    @Override
    int countBlocksGiven(@Bind("userId") UUID userId);

    @SqlQuery("SELECT COUNT(*) FROM blocks WHERE blocked_id = :userId AND deleted_at IS NULL")
    @Override
    int countBlocksReceived(@Bind("userId") UUID userId);

    // ═══════════════════════════════════════════════════════════════
    // Report operations
    // ═══════════════════════════════════════════════════════════════

    @SqlUpdate("""
            INSERT INTO reports (id, reporter_id, reported_user_id, reason, description, created_at)
            VALUES (:id, :reporterId, :reportedUserId, :reason, :description, :createdAt)
            """)
    @Override
    void save(@BindBean Report report);

    @SqlQuery("SELECT COUNT(*) FROM reports WHERE reported_user_id = :userId AND deleted_at IS NULL")
    @Override
    int countReportsAgainst(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT EXISTS (
                SELECT 1 FROM reports
                                WHERE reporter_id = :reporterId AND reported_user_id = :reportedUserId AND deleted_at IS NULL
            )
            """)
    @Override
    boolean hasReported(@Bind("reporterId") UUID reporterId, @Bind("reportedUserId") UUID reportedUserId);

    @RegisterRowMapper(ReportMapper.class)
    @SqlQuery("""
            SELECT id, reporter_id, reported_user_id, reason, description, created_at
            FROM reports WHERE reported_user_id = :userId AND deleted_at IS NULL ORDER BY created_at DESC
            """)
    @Override
    List<Report> getReportsAgainst(@Bind("userId") UUID userId);

    @SqlQuery("SELECT COUNT(*) FROM reports WHERE reporter_id = :userId AND deleted_at IS NULL")
    @Override
    int countReportsBy(@Bind("userId") UUID userId);

    // ═══════════════════════════════════════════════════════════════
    // Row Mappers
    // ═══════════════════════════════════════════════════════════════

    /** Row mapper for Block records. */
    class BlockMapper implements RowMapper<Block> {
        @Override
        public Block map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Block(
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "blocker_id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "blocked_id"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at"));
        }
    }

    /** Row mapper for Report records. */
    class ReportMapper implements RowMapper<Report> {
        @Override
        public Report map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new Report(
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "reporter_id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "reported_user_id"),
                    JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "reason", Reason.class),
                    rs.getString("description"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at"));
        }
    }
}
