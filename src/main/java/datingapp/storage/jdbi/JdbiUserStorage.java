package datingapp.storage.jdbi;

import datingapp.core.Dealbreakers;
import datingapp.core.PacePreferences;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.User;
import datingapp.core.User.Gender;
import datingapp.core.User.ProfileNote;
import datingapp.core.User.UserState;
import datingapp.core.User.VerificationMethod;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JDBI DAO interface for User storage operations.
 * Handles all database interactions for User entities.
 */
@RegisterRowMapper(JdbiUserStorage.Mapper.class)
@RegisterRowMapper(JdbiUserStorage.ProfileNoteMapper.class)
public interface JdbiUserStorage {

    // ===== SQL Column Constants =====
    // Single source of truth for User table columns - prevents copy-paste errors

    /** All columns in the users table (for SELECT queries). */
    String ALL_COLUMNS = """
                        id, name, bio, birth_date, gender, interested_in, lat, lon,
                        has_location_set, max_distance_km, min_age, max_age, photo_urls, state, created_at,
                        updated_at, smoking, drinking, wants_kids, looking_for, education,
                        height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
                        db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
                        interests, email, phone, is_verified, verification_method,
                        verification_code, verification_sent_at, verified_at,
                        pace_messaging_frequency, pace_time_to_first_date,
                        pace_communication_style, pace_depth_preference, deleted_at""";

    /**
     * Saves a user (insert or update via MERGE).
     * All 41 user fields are persisted.
     * Uses UserBindingHelper to serialize complex fields (EnumSets, Lists).
     */
    @SqlUpdate("""
                        MERGE INTO users (
                            id, name, bio, birth_date, gender, interested_in, lat, lon,
                            has_location_set, max_distance_km, min_age, max_age, photo_urls, state, created_at,
                            updated_at, smoking, drinking, wants_kids, looking_for, education,
                            height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
                            db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
                            interests, email, phone, is_verified, verification_method,
                            verification_code, verification_sent_at, verified_at,
                            pace_messaging_frequency, pace_time_to_first_date,
                            pace_communication_style, pace_depth_preference, deleted_at
                        ) KEY (id) VALUES (
                            :id, :name, :bio, :birthDate, :gender, :interestedInCsv,
                            :lat, :lon, :hasLocationSet, :maxDistanceKm, :minAge, :maxAge, :photoUrlsCsv,
                            :state, :createdAt, :updatedAt,
                            :smoking, :drinking, :wantsKids, :lookingFor,
                            :education, :heightCm,
                            :dealbreakerSmokingCsv, :dealbreakerDrinkingCsv, :dealbreakerWantsKidsCsv,
                            :dealbreakerLookingForCsv, :dealbreakerEducationCsv,
                            :dealbreakerMinHeightCm, :dealbreakerMaxHeightCm, :dealbreakerMaxAgeDiff,
                            :interestsCsv, :email, :phone, :verified, :verificationMethod,
                            :verificationCode, :verificationSentAt, :verifiedAt,
                            :paceMessagingFrequency, :paceTimeToFirstDate,
                            :paceCommunicationStyle, :paceDepthPreference, :deletedAt
                        )
                        """)
    void save(@BindBean UserBindingHelper helper);

    /**
     * Gets a user by ID.
     */
    @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE id = :id AND deleted_at IS NULL")
    User get(@Bind("id") UUID id);

    /**
     * Finds all active users.
     */
    @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE state = 'ACTIVE' AND deleted_at IS NULL")
    List<User> findActive();

    /**
     * Finds all users regardless of state.
     */
    @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE deleted_at IS NULL")
    List<User> findAll();

    /**
     * Deletes a user by ID.
     * Cascades to all related records (likes, matches, sessions, stats).
     */
    @SqlUpdate("DELETE FROM users WHERE id = :id")
    void delete(@Bind("id") UUID id);

    /** Permanently removes soft-deleted users older than the given threshold. */
    @SqlUpdate("DELETE FROM users WHERE deleted_at IS NOT NULL AND deleted_at < :threshold")
    int purgeDeletedBefore(@Bind("threshold") java.time.Instant threshold);

    // ===== ProfileNote Methods =====

    @SqlUpdate("""
                        MERGE INTO profile_notes (author_id, subject_id, content, created_at, updated_at)
                        KEY (author_id, subject_id)
                        VALUES (:authorId, :subjectId, :content, :createdAt, :updatedAt)
                        """)
    void saveProfileNote(@BindBean ProfileNote note);

    @SqlQuery("""
                        SELECT author_id, subject_id, content, created_at, updated_at
                        FROM profile_notes
                        WHERE author_id = :authorId AND subject_id = :subjectId
                        """)
    Optional<ProfileNote> getProfileNote(@Bind("authorId") UUID authorId, @Bind("subjectId") UUID subjectId);

    @SqlQuery("""
                        SELECT author_id, subject_id, content, created_at, updated_at
                        FROM profile_notes
                        WHERE author_id = :authorId
                        ORDER BY updated_at DESC
                        """)
    List<ProfileNote> getProfileNotesByAuthor(@Bind("authorId") UUID authorId);

    @SqlUpdate("""
                        DELETE FROM profile_notes
                        WHERE author_id = :authorId AND subject_id = :subjectId
                        """)
    int deleteProfileNoteInternal(@Bind("authorId") UUID authorId, @Bind("subjectId") UUID subjectId);

    default boolean deleteProfileNote(UUID authorId, UUID subjectId) {
        return deleteProfileNoteInternal(authorId, subjectId) > 0;
    }

    /**
     * Row mapper for User entity - inlined from former UserMapper class.
     * Maps database rows to User objects using the StorageBuilder pattern.
     */
    public static class Mapper implements RowMapper<User> {

        private static final Logger logger = LoggerFactory.getLogger(Mapper.class);

        @Override
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            // Core fields (required)
            Double lat = MapperHelper.readDouble(rs, "lat");
            Double lon = MapperHelper.readDouble(rs, "lon");
            Boolean hasLocationFlag = rs.getObject("has_location_set", Boolean.class);
            boolean hasLocationSet =
                    Boolean.TRUE.equals(hasLocationFlag) || (lat != null && lon != null && (lat != 0.0 || lon != 0.0));

            User user = User.StorageBuilder.create(
                            MapperHelper.readUuid(rs, "id"),
                            rs.getString("name"),
                            MapperHelper.readInstant(rs, "created_at"))
                    .bio(rs.getString("bio"))
                    .birthDate(MapperHelper.readLocalDate(rs, "birth_date"))
                    .gender(MapperHelper.readEnum(rs, "gender", Gender.class))
                    .interestedIn(readGenderSet(rs, "interested_in"))
                    .location(lat != null ? lat : 0.0, lon != null ? lon : 0.0)
                    .hasLocationSet(hasLocationSet)
                    .maxDistanceKm(rs.getInt("max_distance_km"))
                    .ageRange(rs.getInt("min_age"), rs.getInt("max_age"))
                    .photoUrls(readPhotoUrls(rs, "photo_urls"))
                    .state(MapperHelper.readEnum(rs, "state", UserState.class))
                    .updatedAt(MapperHelper.readInstant(rs, "updated_at"))
                    .interests(readInterestSet(rs, "interests"))
                    .smoking(MapperHelper.readEnum(rs, "smoking", Lifestyle.Smoking.class))
                    .drinking(MapperHelper.readEnum(rs, "drinking", Lifestyle.Drinking.class))
                    .wantsKids(MapperHelper.readEnum(rs, "wants_kids", Lifestyle.WantsKids.class))
                    .lookingFor(MapperHelper.readEnum(rs, "looking_for", Lifestyle.LookingFor.class))
                    .education(MapperHelper.readEnum(rs, "education", Lifestyle.Education.class))
                    .heightCm(MapperHelper.readInteger(rs, "height_cm"))
                    .email(rs.getString("email"))
                    .phone(rs.getString("phone"))
                    .verified(rs.getObject("is_verified", Boolean.class))
                    .verificationMethod(MapperHelper.readEnum(rs, "verification_method", VerificationMethod.class))
                    .verificationCode(rs.getString("verification_code"))
                    .verificationSentAt(MapperHelper.readInstant(rs, "verification_sent_at"))
                    .verifiedAt(MapperHelper.readInstant(rs, "verified_at"))
                    .pacePreferences(readPacePreferences(rs))
                    .deletedAt(MapperHelper.readInstant(rs, "deleted_at"))
                    .build();

            // Set dealbreakers if any are present
            Dealbreakers dealbreakers = readDealbreakers(rs);
            if (dealbreakers.hasAnyDealbreaker()) {
                user.setDealbreakers(dealbreakers);
            }

            return user;
        }

        /** Reads a comma-separated list of genders into an EnumSet. */
        private Set<Gender> readGenderSet(ResultSet rs, String column) throws SQLException {
            String csv = rs.getString(column);
            if (csv == null || csv.isBlank()) {
                return EnumSet.noneOf(Gender.class);
            }
            Set<Gender> result = EnumSet.noneOf(Gender.class);
            for (String s : csv.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        result.add(Gender.valueOf(trimmed));
                    } catch (IllegalArgumentException e) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Skipping invalid Gender value '{}' from database", trimmed, e);
                        }
                    }
                }
            }
            return result;
        }

        /** Reads pipe-delimited photo URLs into a List. */
        private List<String> readPhotoUrls(ResultSet rs, String column) throws SQLException {
            String csv = rs.getString(column);
            if (csv == null || csv.isBlank()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Arrays.asList(csv.split("\\|")));
        }

        /** Reads a comma-separated list of interests into an EnumSet. */
        private Set<Interest> readInterestSet(ResultSet rs, String column) throws SQLException {
            String csv = rs.getString(column);
            if (csv == null || csv.isBlank()) {
                return EnumSet.noneOf(Interest.class);
            }
            Set<Interest> result = EnumSet.noneOf(Interest.class);
            for (String s : csv.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        result.add(Interest.valueOf(trimmed));
                    } catch (IllegalArgumentException e) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Skipping invalid interest value '{}' from database", trimmed, e);
                        }
                    }
                }
            }
            return result;
        }

        /**
         * Reads pace preferences from the result set.
         * Returns null if all pace fields are null.
         */
        private PacePreferences readPacePreferences(ResultSet rs) throws SQLException {
            PacePreferences.MessagingFrequency messagingFrequency =
                    MapperHelper.readEnum(rs, "pace_messaging_frequency", PacePreferences.MessagingFrequency.class);
            PacePreferences.TimeToFirstDate timeToFirstDate =
                    MapperHelper.readEnum(rs, "pace_time_to_first_date", PacePreferences.TimeToFirstDate.class);
            PacePreferences.CommunicationStyle communicationStyle =
                    MapperHelper.readEnum(rs, "pace_communication_style", PacePreferences.CommunicationStyle.class);
            PacePreferences.DepthPreference depthPreference =
                    MapperHelper.readEnum(rs, "pace_depth_preference", PacePreferences.DepthPreference.class);

            if (messagingFrequency == null
                    && timeToFirstDate == null
                    && communicationStyle == null
                    && depthPreference == null) {
                return null;
            }

            return new PacePreferences(messagingFrequency, timeToFirstDate, communicationStyle, depthPreference);
        }

        /** Reads dealbreakers from the result set. */
        private Dealbreakers readDealbreakers(ResultSet rs) throws SQLException {
            Integer dbMinHeight = MapperHelper.readInteger(rs, "db_min_height_cm");
            Integer dbMaxHeight = MapperHelper.readInteger(rs, "db_max_height_cm");
            Integer dbMaxAgeDiff = MapperHelper.readInteger(rs, "db_max_age_diff");

            Set<Lifestyle.Smoking> dbSmoking = readEnumSet(rs, "db_smoking", Lifestyle.Smoking.class);
            Set<Lifestyle.Drinking> dbDrinking = readEnumSet(rs, "db_drinking", Lifestyle.Drinking.class);
            Set<Lifestyle.WantsKids> dbKids = readEnumSet(rs, "db_wants_kids", Lifestyle.WantsKids.class);
            Set<Lifestyle.LookingFor> dbLookingFor = readEnumSet(rs, "db_looking_for", Lifestyle.LookingFor.class);
            Set<Lifestyle.Education> dbEducation = readEnumSet(rs, "db_education", Lifestyle.Education.class);

            return new Dealbreakers(
                    dbSmoking, dbDrinking, dbKids, dbLookingFor, dbEducation, dbMinHeight, dbMaxHeight, dbMaxAgeDiff);
        }

        /** Reads a comma-separated enum set from a column. */
        private <E extends Enum<E>> Set<E> readEnumSet(ResultSet rs, String column, Class<E> enumClass)
                throws SQLException {
            String csv = rs.getString(column);
            if (csv == null || csv.isBlank()) {
                return EnumSet.noneOf(enumClass);
            }
            Set<E> result = EnumSet.noneOf(enumClass);
            for (String s : csv.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        result.add(Enum.valueOf(enumClass, trimmed));
                    } catch (IllegalArgumentException e) {
                        if (logger.isDebugEnabled()) {
                            logger.debug(
                                    "Skipping invalid {} value '{}' from database",
                                    enumClass.getSimpleName(),
                                    trimmed,
                                    e);
                        }
                    }
                }
            }
            return result;
        }
    }

    /**
     * Row mapper for ProfileNote entity.
     * Maps database rows to ProfileNote objects.
     */
    public static class ProfileNoteMapper implements RowMapper<ProfileNote> {

        @Override
        public ProfileNote map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ProfileNote(
                    MapperHelper.readUuid(rs, "author_id"),
                    MapperHelper.readUuid(rs, "subject_id"),
                    rs.getString("content"),
                    MapperHelper.readInstant(rs, "created_at"),
                    MapperHelper.readInstant(rs, "updated_at"));
        }
    }
}
