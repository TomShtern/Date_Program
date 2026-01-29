package datingapp.storage.jdbi;

import datingapp.core.UserInteractions.Report;
import datingapp.core.storage.ReportStorage;
import datingapp.storage.mapper.ReportMapper;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for Report entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(ReportMapper.class)
public interface JdbiReportStorage extends ReportStorage {

    @SqlUpdate("""
            INSERT INTO reports (id, reporter_id, reported_user_id, reason, description, created_at)
            VALUES (:id, :reporterId, :reportedUserId, :reason, :description, :createdAt)
            """)
    @Override
    void save(@BindBean Report report);

    @SqlQuery("SELECT COUNT(*) FROM reports WHERE reported_user_id = :userId")
    @Override
    int countReportsAgainst(@Bind("userId") UUID userId);

    @SqlQuery("""
            SELECT EXISTS (
                SELECT 1 FROM reports
                WHERE reporter_id = :reporterId AND reported_user_id = :reportedUserId
            )
            """)
    @Override
    boolean hasReported(@Bind("reporterId") UUID reporterId, @Bind("reportedUserId") UUID reportedUserId);

    @SqlQuery("SELECT * FROM reports WHERE reported_user_id = :userId ORDER BY created_at DESC")
    @Override
    List<Report> getReportsAgainst(@Bind("userId") UUID userId);

    @SqlQuery("SELECT COUNT(*) FROM reports WHERE reporter_id = :userId")
    @Override
    int countReportsBy(@Bind("userId") UUID userId);
}
