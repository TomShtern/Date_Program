package datingapp.storage.jdbi;

import datingapp.core.Dealbreakers;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.Preferences.PacePreferences;
import datingapp.core.User;
import datingapp.storage.mapper.MapperHelper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * JDBI DAO interface for User storage operations.
 * Handles all database interactions for User entities.
 */
@RegisterRowMapper(JdbiUserStorage.Mapper.class)
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

    /**
     * Row mapper for User entity - inlined from former UserMapper class.
     * Maps database rows to User objects using the StorageBuilder pattern.
     */
    class Mapper implements RowMapper<User> {

        @Override
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            // Core fields (required)
            User user = User.StorageBuilder.create(
                            MapperHelper.readUuid(rs, "id"),
                            rs.getString("name"),
                            MapperHelper.readInstant(rs, "created_at"))
                    .bio(rs.getString("bio"))
                    .birthDate(MapperHelper.readLocalDate(rs, "birth_date"))
                    .gender(MapperHelper.readEnum(rs, "gender", User.Gender.class))
                    .interestedIn(readGenderSet(rs, "interested_in"))
                    .location(rs.getDouble("lat"), rs.getDouble("lon"))
                    .maxDistanceKm(rs.getInt("max_distance_km"))
                    .ageRange(rs.getInt("min_age"), rs.getInt("max_age"))
                    .photoUrls(readPhotoUrls(rs, "photo_urls"))
                    .state(MapperHelper.readEnum(rs, "state", User.State.class))
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
                    .verificationMethod(MapperHelper.readEnum(rs, "verification_method", User.VerificationMethod.class))
                    .verificationCode(rs.getString("verification_code"))
                    .verificationSentAt(MapperHelper.readInstant(rs, "verification_sent_at"))
                    .verifiedAt(MapperHelper.readInstant(rs, "verified_at"))
                    .pacePreferences(readPacePreferences(rs))
                    .build();

            // Set dealbreakers if any are present
            Dealbreakers dealbreakers = readDealbreakers(rs);
            if (dealbreakers.hasAnyDealbreaker()) {
                user.setDealbreakers(dealbreakers);
            }

            return user;
        }

        /** Reads a comma-separated list of genders into an EnumSet. */
        private Set<User.Gender> readGenderSet(ResultSet rs, String column) throws SQLException {
            String csv = rs.getString(column);
            if (csv == null || csv.isBlank()) {
                return EnumSet.noneOf(User.Gender.class);
            }
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(User.Gender::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(User.Gender.class)));
        }

        /** Reads pipe-delimited photo URLs into a List. */
        private List<String> readPhotoUrls(ResultSet rs, String column) throws SQLException {
            String csv = rs.getString(column);
            if (csv == null || csv.isBlank()) {
                return Collections.emptyList();
            }
            return Arrays.asList(csv.split("\\|"));
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
                        // Skip invalid interests from data migration - maintains backward compatibility
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
                return new HashSet<>();
            }
            return Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> Enum.valueOf(enumClass, s))
                    .collect(Collectors.toSet());
        }
    }
}
