package datingapp.storage.jdbi;

import datingapp.core.User.ProfileNote;
import datingapp.core.storage.ProfileNoteStorage;
import datingapp.storage.mapper.ProfileNoteMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI storage implementation for ProfileNote entities.
 * Uses declarative SQL methods instead of manual JDBC.
 */
@RegisterRowMapper(ProfileNoteMapper.class)
public interface JdbiProfileNoteStorage extends ProfileNoteStorage {

    @SqlUpdate("""
            MERGE INTO profile_notes (author_id, subject_id, content, created_at, updated_at)
            KEY (author_id, subject_id)
            VALUES (:authorId, :subjectId, :content, :createdAt, :updatedAt)
            """)
    @Override
    void save(@BindBean ProfileNote note);

    @SqlQuery("""
            SELECT author_id, subject_id, content, created_at, updated_at
            FROM profile_notes
            WHERE author_id = :authorId AND subject_id = :subjectId
            """)
    @Override
    Optional<ProfileNote> get(@Bind("authorId") UUID authorId, @Bind("subjectId") UUID subjectId);

    @SqlQuery("""
            SELECT author_id, subject_id, content, created_at, updated_at
            FROM profile_notes
            WHERE author_id = :authorId
            ORDER BY updated_at DESC
            """)
    @Override
    List<ProfileNote> getAllByAuthor(@Bind("authorId") UUID authorId);

    @SqlUpdate("""
            DELETE FROM profile_notes
            WHERE author_id = :authorId AND subject_id = :subjectId
            """)
    int deleteNote(@Bind("authorId") UUID authorId, @Bind("subjectId") UUID subjectId);

    @Override
    default boolean delete(UUID authorId, UUID subjectId) {
        return deleteNote(authorId, subjectId) > 0;
    }
}
