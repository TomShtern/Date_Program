package datingapp.storage.jdbi;

import datingapp.core.User;
import datingapp.storage.mapper.UserMapper;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI DAO interface for User storage operations.
 * Handles all database interactions for User entities.
 */
@RegisterRowMapper(UserMapper.class)
public interface JdbiUserStorage {

    /**
     * Saves a user (insert or update via MERGE).
     * All 41 user fields are persisted.
     * Uses UserBindingHelper to serialize complex fields (EnumSets, Lists).
     */
    @SqlUpdate("""
        MERGE INTO users (
            id, name, bio, birth_date, gender, interested_in, lat, lon,
            max_distance_km, min_age, max_age, photo_urls, state, created_at,
            updated_at, smoking, drinking, wants_kids, looking_for, education,
            height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
            db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
            interests, email, phone, is_verified, verification_method,
            verification_code, verification_sent_at, verified_at,
            pace_messaging_frequency, pace_time_to_first_date,
            pace_communication_style, pace_depth_preference
        ) KEY (id) VALUES (
            :id, :name, :bio, :birthDate, :gender, :interestedInCsv,
            :lat, :lon, :maxDistanceKm, :minAge, :maxAge, :photoUrlsCsv,
            :state, :createdAt, :updatedAt,
            :smoking, :drinking, :wantsKids, :lookingFor,
            :education, :heightCm,
            :dealbreakerSmokingCsv, :dealbreakerDrinkingCsv, :dealbreakerWantsKidsCsv,
            :dealbreakerLookingForCsv, :dealbreakerEducationCsv,
            :dealbreakerMinHeightCm, :dealbreakerMaxHeightCm, :dealbreakerMaxAgeDiff,
            :interestsCsv, :email, :phone, :verified, :verificationMethod,
            :verificationCode, :verificationSentAt, :verifiedAt,
            :paceMessagingFrequency, :paceTimeToFirstDate,
            :paceCommunicationStyle, :paceDepthPreference
        )
        """)
    void save(@BindBean UserBindingHelper helper);

    /**
     * Gets a user by ID.
     */
    @SqlQuery("""
        SELECT id, name, bio, birth_date, gender, interested_in, lat, lon,
               max_distance_km, min_age, max_age, photo_urls, state, created_at,
               updated_at, smoking, drinking, wants_kids, looking_for, education,
               height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
               db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
               interests, email, phone, is_verified, verification_method,
               verification_code, verification_sent_at, verified_at,
               pace_messaging_frequency, pace_time_to_first_date,
               pace_communication_style, pace_depth_preference
        FROM users WHERE id = :id
        """)
    User get(@Bind("id") UUID id);

    /**
     * Finds all active users.
     */
    @SqlQuery("""
        SELECT id, name, bio, birth_date, gender, interested_in, lat, lon,
               max_distance_km, min_age, max_age, photo_urls, state, created_at,
               updated_at, smoking, drinking, wants_kids, looking_for, education,
               height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
               db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
               interests, email, phone, is_verified, verification_method,
               verification_code, verification_sent_at, verified_at,
               pace_messaging_frequency, pace_time_to_first_date,
               pace_communication_style, pace_depth_preference
        FROM users WHERE state = 'ACTIVE'
        """)
    List<User> findActive();

    /**
     * Finds all users regardless of state.
     */
    @SqlQuery("""
        SELECT id, name, bio, birth_date, gender, interested_in, lat, lon,
               max_distance_km, min_age, max_age, photo_urls, state, created_at,
               updated_at, smoking, drinking, wants_kids, looking_for, education,
               height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
               db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
               interests, email, phone, is_verified, verification_method,
               verification_code, verification_sent_at, verified_at,
               pace_messaging_frequency, pace_time_to_first_date,
               pace_communication_style, pace_depth_preference
        FROM users
        """)
    List<User> findAll();

    /**
     * Deletes a user by ID.
     * Cascades to all related records (likes, matches, sessions, stats).
     */
    @SqlUpdate("DELETE FROM users WHERE id = :id")
    void delete(@Bind("id") UUID id);
}
