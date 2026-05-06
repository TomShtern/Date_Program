package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.connection.ConnectionModels.Block;
import datingapp.core.connection.ConnectionModels.Report;
import datingapp.core.connection.ConnectionModels.Report.Reason;
import datingapp.core.storage.TrustSafetyStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * Consolidated JDBI storage for trust &amp; safety: blocking and reporting.
 * Merges former {@code JdbiBlockStorage} and {@code JdbiReportStorage}.
 */
public final class JdbiTrustSafetyStorage implements TrustSafetyStorage {

    private static final String INSERT_BLOCK_SQL = """
            INSERT INTO blocks (id, blocker_id, blocked_id, created_at)
            VALUES (:id, :blockerId, :blockedId, :createdAt)
            """;

    private static final String REVIVE_DELETED_BLOCK_SQL = """
            UPDATE blocks
            SET id = :id,
                created_at = :createdAt,
                deleted_at = NULL
            WHERE blocker_id = :blockerId
              AND blocked_id = :blockedId
              AND deleted_at IS NOT NULL
            """;

    private static final String ACTIVE_BLOCK_EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM blocks
                WHERE blocker_id = :blockerId
                  AND blocked_id = :blockedId
                  AND deleted_at IS NULL
            )
            """;

    private static final String IS_BLOCKED_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM blocks
                WHERE deleted_at IS NULL
                  AND ((blocker_id = :userA AND blocked_id = :userB)
                   OR (blocker_id = :userB AND blocked_id = :userA)
                  )
            )
            """;

    private static final String GET_BLOCKED_USER_IDS_SQL = """
            SELECT blocked_id FROM blocks WHERE blocker_id = :userId AND deleted_at IS NULL
            UNION
            SELECT blocker_id FROM blocks WHERE blocked_id = :userId AND deleted_at IS NULL
            """;

    private static final String FIND_BY_BLOCKER_SQL = """
            SELECT id, blocker_id, blocked_id, created_at
            FROM blocks WHERE blocker_id = :blockerId AND deleted_at IS NULL
            """;

    private static final String DELETE_BLOCK_SQL = """
            UPDATE blocks
            SET deleted_at = :now
            WHERE blocker_id = :blockerId AND blocked_id = :blockedId AND deleted_at IS NULL
            """;

    private static final String COUNT_BLOCKS_GIVEN_SQL =
            "SELECT COUNT(*) FROM blocks WHERE blocker_id = :userId AND deleted_at IS NULL";

    private static final String COUNT_BLOCKS_RECEIVED_SQL =
            "SELECT COUNT(*) FROM blocks WHERE blocked_id = :userId AND deleted_at IS NULL";

    private static final String INSERT_REPORT_SQL = """
            INSERT INTO reports (id, reporter_id, reported_user_id, reason, description, created_at)
            VALUES (:id, :reporterId, :reportedUserId, :reason, :description, :createdAt)
            """;

    private static final String REVIVE_DELETED_REPORT_SQL = """
            UPDATE reports
            SET id = :id,
                reason = :reason,
                description = :description,
                created_at = :createdAt,
                deleted_at = NULL
            WHERE reporter_id = :reporterId
              AND reported_user_id = :reportedUserId
              AND deleted_at IS NOT NULL
            """;

    private static final String COUNT_REPORTS_AGAINST_SQL =
            "SELECT COUNT(*) FROM reports WHERE reported_user_id = :userId AND deleted_at IS NULL";

    private static final String HAS_REPORTED_SQL = """
            SELECT EXISTS (
                SELECT 1 FROM reports
                WHERE reporter_id = :reporterId AND reported_user_id = :reportedUserId AND deleted_at IS NULL
            )
            """;

    private static final String GET_REPORTS_AGAINST_SQL = """
            SELECT id, reporter_id, reported_user_id, reason, description, created_at
            FROM reports WHERE reported_user_id = :userId AND deleted_at IS NULL ORDER BY created_at DESC
            """;

    private static final String COUNT_REPORTS_BY_SQL =
            "SELECT COUNT(*) FROM reports WHERE reporter_id = :userId AND deleted_at IS NULL";

    private final Jdbi jdbi;

    public JdbiTrustSafetyStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
    }

    @Override
    public void save(Block block) {
        jdbi.useTransaction(handle -> {
            int revived = handle.createUpdate(REVIVE_DELETED_BLOCK_SQL)
                    .bind("id", block.id())
                    .bind("blockerId", block.blockerId())
                    .bind("blockedId", block.blockedId())
                    .bind("createdAt", block.createdAt())
                    .execute();
            if (revived > 0) {
                return;
            }
            boolean exists = handle.createQuery(ACTIVE_BLOCK_EXISTS_SQL)
                    .bind("blockerId", block.blockerId())
                    .bind("blockedId", block.blockedId())
                    .mapTo(Boolean.class)
                    .one();
            if (exists) {
                return;
            }
            handle.createUpdate(INSERT_BLOCK_SQL)
                    .bind("id", block.id())
                    .bind("blockerId", block.blockerId())
                    .bind("blockedId", block.blockedId())
                    .bind("createdAt", block.createdAt())
                    .execute();
        });
    }

    @Override
    public boolean isBlocked(UUID userA, UUID userB) {
        return jdbi.withHandle(handle -> handle.createQuery(IS_BLOCKED_SQL)
                .bind("userA", userA)
                .bind("userB", userB)
                .mapTo(Boolean.class)
                .one());
    }

    @Override
    public Set<UUID> getBlockedUserIds(UUID userId) {
        return new java.util.HashSet<>(jdbi.withHandle(handle -> handle.createQuery(GET_BLOCKED_USER_IDS_SQL)
                .bind("userId", userId)
                .mapTo(UUID.class)
                .list()));
    }

    @Override
    public List<Block> findByBlocker(UUID blockerId) {
        return jdbi.withHandle(handle -> handle.createQuery(FIND_BY_BLOCKER_SQL)
                .bind("blockerId", blockerId)
                .map(new BlockMapper())
                .list());
    }

    @Override
    public boolean deleteBlock(UUID blockerId, UUID blockedId) {
        return jdbi.withHandle(handle -> handle.createUpdate(DELETE_BLOCK_SQL)
                        .bind("blockerId", blockerId)
                        .bind("blockedId", blockedId)
                        .bind("now", AppClock.now())
                        .execute())
                > 0;
    }

    @Override
    public int countBlocksGiven(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery(COUNT_BLOCKS_GIVEN_SQL)
                .bind("userId", userId)
                .mapTo(int.class)
                .one());
    }

    @Override
    public int countBlocksReceived(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery(COUNT_BLOCKS_RECEIVED_SQL)
                .bind("userId", userId)
                .mapTo(int.class)
                .one());
    }

    @Override
    public void save(Report report) {
        jdbi.useTransaction(handle -> {
            int revived = handle.createUpdate(REVIVE_DELETED_REPORT_SQL)
                    .bind("id", report.id())
                    .bind("reporterId", report.reporterId())
                    .bind("reportedUserId", report.reportedUserId())
                    .bind("reason", report.reason().name())
                    .bind("description", report.description())
                    .bind("createdAt", report.createdAt())
                    .execute();
            if (revived > 0) {
                return;
            }
            boolean exists = handle.createQuery(HAS_REPORTED_SQL)
                    .bind("reporterId", report.reporterId())
                    .bind("reportedUserId", report.reportedUserId())
                    .mapTo(Boolean.class)
                    .one();
            if (exists) {
                return;
            }
            handle.createUpdate(INSERT_REPORT_SQL)
                    .bind("id", report.id())
                    .bind("reporterId", report.reporterId())
                    .bind("reportedUserId", report.reportedUserId())
                    .bind("reason", report.reason().name())
                    .bind("description", report.description())
                    .bind("createdAt", report.createdAt())
                    .execute();
        });
    }

    @Override
    public int countReportsAgainst(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery(COUNT_REPORTS_AGAINST_SQL)
                .bind("userId", userId)
                .mapTo(int.class)
                .one());
    }

    @Override
    public boolean hasReported(UUID reporterId, UUID reportedUserId) {
        return jdbi.withHandle(handle -> handle.createQuery(HAS_REPORTED_SQL)
                .bind("reporterId", reporterId)
                .bind("reportedUserId", reportedUserId)
                .mapTo(Boolean.class)
                .one());
    }

    @Override
    public List<Report> getReportsAgainst(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery(GET_REPORTS_AGAINST_SQL)
                .bind("userId", userId)
                .map(new ReportMapper())
                .list());
    }

    @Override
    public int countReportsBy(UUID userId) {
        return jdbi.withHandle(handle -> handle.createQuery(COUNT_REPORTS_BY_SQL)
                .bind("userId", userId)
                .mapTo(int.class)
                .one());
    }

    /** Row mapper for Block records. */
    public static class BlockMapper implements RowMapper<Block> {
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
    public static class ReportMapper implements RowMapper<Report> {
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
