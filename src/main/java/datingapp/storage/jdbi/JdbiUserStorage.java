package datingapp.storage.jdbi;

import datingapp.core.model.Gender;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.UserState;
import datingapp.core.model.VerificationMethod;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.storage.UserStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consolidated JDBI-backed user storage implementation. */
public final class JdbiUserStorage implements UserStorage {

    public static final String ALL_COLUMNS = """
                        id, name, bio, birth_date, gender, interested_in, lat, lon,
                        has_location_set, max_distance_km, min_age, max_age, photo_urls, state, created_at,
                        updated_at, smoking, drinking, wants_kids, looking_for, education,
                        height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
                        db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
                        interests, email, phone, is_verified, verification_method,
                        verification_code, verification_sent_at, verified_at,
                        pace_messaging_frequency, pace_time_to_first_date,
                        pace_communication_style, pace_depth_preference, deleted_at""";

    private final Jdbi jdbi;
    private final Dao dao;

    public JdbiUserStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.dao = jdbi.onDemand(Dao.class);
    }

    @Override
    public void save(User user) {
        dao.save(new UserSqlBindings(user));
    }

    @Override
    public User get(UUID id) {
        return dao.get(id);
    }

    @Override
    public List<User> findActive() {
        return dao.findActive();
    }

    @Override
    public List<User> findCandidates(
            UUID excludeId,
            Set<Gender> genders,
            int minAge,
            int maxAge,
            double seekerLat,
            double seekerLon,
            int maxDistanceKm) {
        if (genders == null || genders.isEmpty()) {
            return List.of();
        }
        List<String> genderNames = genders.stream().map(Enum::name).toList();

        return jdbi.withHandle(handle -> {
            StringBuilder sql = new StringBuilder("SELECT ")
                    .append(ALL_COLUMNS)
                    .append(" FROM users WHERE id <> :excludeId")
                    .append(" AND state = 'ACTIVE'")
                    .append(" AND deleted_at IS NULL")
                    .append(" AND gender IN (<genders>)")
                    .append(" AND DATEDIFF('YEAR', birth_date, CURRENT_DATE) BETWEEN :minAge AND :maxAge");

            // Approximate bounding-box pre-filter when seeker has a location.
            // Exact great-circle distance is enforced in-memory by CandidateFinder.
            boolean applyBbox = maxDistanceKm < 50_000;
            if (applyBbox) {
                double latDelta = maxDistanceKm / 111.0;
                double cosLat = Math.max(Math.cos(Math.toRadians(Math.abs(seekerLat))), 0.0001);
                double lonDelta = maxDistanceKm / (111.0 * cosLat);
                sql.append(" AND has_location_set = TRUE")
                        .append(" AND lat BETWEEN :latMin AND :latMax")
                        .append(" AND lon BETWEEN :lonMin AND :lonMax");

                return handle.createQuery(sql.toString())
                        .defineList("genders", genderNames)
                        .bind("excludeId", excludeId)
                        .bind("minAge", minAge)
                        .bind("maxAge", maxAge)
                        .bind("latMin", seekerLat - latDelta)
                        .bind("latMax", seekerLat + latDelta)
                        .bind("lonMin", seekerLon - lonDelta)
                        .bind("lonMax", seekerLon + lonDelta)
                        .map(new Mapper())
                        .list();
            } else {
                return handle.createQuery(sql.toString())
                        .defineList("genders", genderNames)
                        .bind("excludeId", excludeId)
                        .bind("minAge", minAge)
                        .bind("maxAge", maxAge)
                        .map(new Mapper())
                        .list();
            }
        });
    }

    @Override
    public List<User> findAll() {
        return dao.findAll();
    }

    @Override
    public Map<UUID, User> findByIds(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        return jdbi.withHandle(handle -> {
            List<User> users = handle.createQuery(
                            "SELECT " + ALL_COLUMNS + " FROM users WHERE id IN (<userIds>) AND deleted_at IS NULL")
                    .bindList("userIds", new ArrayList<>(ids))
                    .map(new Mapper())
                    .list();

            Map<UUID, User> result = new HashMap<>();
            for (User user : users) {
                result.put(user.getId(), user);
            }
            return result;
        });
    }

    @Override
    public void delete(UUID id) {
        dao.delete(id);
    }

    @Override
    public int purgeDeletedBefore(Instant threshold) {
        return dao.purgeDeletedBefore(threshold);
    }

    @Override
    public void saveProfileNote(ProfileNote note) {
        dao.saveProfileNote(note);
    }

    @Override
    public Optional<ProfileNote> getProfileNote(UUID authorId, UUID subjectId) {
        return dao.getProfileNote(authorId, subjectId);
    }

    @Override
    public List<ProfileNote> getProfileNotesByAuthor(UUID authorId) {
        return dao.getProfileNotesByAuthor(authorId);
    }

    @Override
    public boolean deleteProfileNote(UUID authorId, UUID subjectId) {
        return dao.deleteProfileNote(authorId, subjectId);
    }

    @RegisterRowMapper(Mapper.class)
    @RegisterRowMapper(ProfileNoteMapper.class)
    private static interface Dao {

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
        void save(@BindBean UserSqlBindings helper);

        @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE id = :id AND deleted_at IS NULL")
        User get(@Bind("id") UUID id);

        @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE state = 'ACTIVE' AND deleted_at IS NULL")
        List<User> findActive();

        @SqlQuery("SELECT " + ALL_COLUMNS + " FROM users WHERE deleted_at IS NULL")
        List<User> findAll();

        @SqlUpdate("DELETE FROM users WHERE id = :id")
        void delete(@Bind("id") UUID id);

        @SqlUpdate("DELETE FROM users WHERE deleted_at IS NOT NULL AND deleted_at < :threshold")
        int purgeDeletedBefore(@Bind("threshold") Instant threshold);

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
    }

    /** Row mapper for User entity. */
    public static class Mapper implements RowMapper<User> {

        private static final Logger logger = LoggerFactory.getLogger(Mapper.class);

        @Override
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            Double lat = JdbiTypeCodecs.SqlRowReaders.readDouble(rs, "lat");
            Double lon = JdbiTypeCodecs.SqlRowReaders.readDouble(rs, "lon");
            Boolean hasLocationFlag = rs.getObject("has_location_set", Boolean.class);
            boolean hasLocationSet =
                    Boolean.TRUE.equals(hasLocationFlag) || (lat != null && lon != null && (lat != 0.0 || lon != 0.0));

            User user = User.StorageBuilder.create(
                            JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                            rs.getString("name"),
                            JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at"))
                    .bio(rs.getString("bio"))
                    .birthDate(JdbiTypeCodecs.SqlRowReaders.readLocalDate(rs, "birth_date"))
                    .gender(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "gender", Gender.class))
                    .interestedIn(readGenderSet(rs, "interested_in"))
                    .location(lat != null ? lat : 0.0, lon != null ? lon : 0.0)
                    .hasLocationSet(hasLocationSet)
                    .maxDistanceKm(rs.getInt("max_distance_km"))
                    .ageRange(rs.getInt("min_age"), rs.getInt("max_age"))
                    .photoUrls(readPhotoUrls(rs, "photo_urls"))
                    .state(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "state", UserState.class))
                    .updatedAt(JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "updated_at"))
                    .interests(readInterestSet(rs, "interests"))
                    .smoking(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "smoking", Lifestyle.Smoking.class))
                    .drinking(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "drinking", Lifestyle.Drinking.class))
                    .wantsKids(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "wants_kids", Lifestyle.WantsKids.class))
                    .lookingFor(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "looking_for", Lifestyle.LookingFor.class))
                    .education(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "education", Lifestyle.Education.class))
                    .heightCm(JdbiTypeCodecs.SqlRowReaders.readInteger(rs, "height_cm"))
                    .email(rs.getString("email"))
                    .phone(rs.getString("phone"))
                    .verified(rs.getObject("is_verified", Boolean.class))
                    .verificationMethod(
                            JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "verification_method", VerificationMethod.class))
                    .verificationCode(rs.getString("verification_code"))
                    .verificationSentAt(JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "verification_sent_at"))
                    .verifiedAt(JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "verified_at"))
                    .pacePreferences(readPacePreferences(rs))
                    .deletedAt(JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "deleted_at"))
                    .build();

            Dealbreakers dealbreakers = readDealbreakers(rs);
            if (dealbreakers.hasAnyDealbreaker()) {
                user.setDealbreakers(dealbreakers);
            }

            return user;
        }

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

        private List<String> readPhotoUrls(ResultSet rs, String column) throws SQLException {
            String csv = rs.getString(column);
            if (csv == null || csv.isBlank()) {
                return new ArrayList<>();
            }
            return new ArrayList<>(Arrays.asList(csv.split("\\|")));
        }

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

        private PacePreferences readPacePreferences(ResultSet rs) throws SQLException {
            PacePreferences.MessagingFrequency messagingFrequency = JdbiTypeCodecs.SqlRowReaders.readEnum(
                    rs, "pace_messaging_frequency", PacePreferences.MessagingFrequency.class);
            PacePreferences.TimeToFirstDate timeToFirstDate = JdbiTypeCodecs.SqlRowReaders.readEnum(
                    rs, "pace_time_to_first_date", PacePreferences.TimeToFirstDate.class);
            PacePreferences.CommunicationStyle communicationStyle = JdbiTypeCodecs.SqlRowReaders.readEnum(
                    rs, "pace_communication_style", PacePreferences.CommunicationStyle.class);
            PacePreferences.DepthPreference depthPreference = JdbiTypeCodecs.SqlRowReaders.readEnum(
                    rs, "pace_depth_preference", PacePreferences.DepthPreference.class);

            if (messagingFrequency == null
                    && timeToFirstDate == null
                    && communicationStyle == null
                    && depthPreference == null) {
                return null;
            }

            return new PacePreferences(messagingFrequency, timeToFirstDate, communicationStyle, depthPreference);
        }

        private Dealbreakers readDealbreakers(ResultSet rs) throws SQLException {
            Integer dbMinHeight = JdbiTypeCodecs.SqlRowReaders.readInteger(rs, "db_min_height_cm");
            Integer dbMaxHeight = JdbiTypeCodecs.SqlRowReaders.readInteger(rs, "db_max_height_cm");
            Integer dbMaxAgeDiff = JdbiTypeCodecs.SqlRowReaders.readInteger(rs, "db_max_age_diff");

            Set<Lifestyle.Smoking> dbSmoking = readEnumSet(rs, "db_smoking", Lifestyle.Smoking.class);
            Set<Lifestyle.Drinking> dbDrinking = readEnumSet(rs, "db_drinking", Lifestyle.Drinking.class);
            Set<Lifestyle.WantsKids> dbKids = readEnumSet(rs, "db_wants_kids", Lifestyle.WantsKids.class);
            Set<Lifestyle.LookingFor> dbLookingFor = readEnumSet(rs, "db_looking_for", Lifestyle.LookingFor.class);
            Set<Lifestyle.Education> dbEducation = readEnumSet(rs, "db_education", Lifestyle.Education.class);

            return new Dealbreakers(
                    dbSmoking, dbDrinking, dbKids, dbLookingFor, dbEducation, dbMinHeight, dbMaxHeight, dbMaxAgeDiff);
        }

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

    /** Row mapper for ProfileNote entity. */
    public static class ProfileNoteMapper implements RowMapper<ProfileNote> {

        @Override
        public ProfileNote map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ProfileNote(
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "author_id"),
                    JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "subject_id"),
                    rs.getString("content"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at"),
                    JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "updated_at"));
        }
    }

    /** Bind helper for user upserts. */
    public static final class UserSqlBindings {

        private final User user;
        private final Dealbreakers dealbreakers;
        private final PacePreferences pace;

        public UserSqlBindings(User user) {
            this.user = Objects.requireNonNull(user, "user cannot be null");
            this.dealbreakers = user.getDealbreakers();
            this.pace = user.getPacePreferences();
        }

        public UUID getId() {
            return user.getId();
        }

        public String getName() {
            return user.getName();
        }

        public String getBio() {
            return user.getBio();
        }

        public LocalDate getBirthDate() {
            return user.getBirthDate();
        }

        public String getGender() {
            return user.getGender() != null ? user.getGender().name() : null;
        }

        public String getInterestedInCsv() {
            if (user.getInterestedIn() == null || user.getInterestedIn().isEmpty()) {
                return null;
            }
            return user.getInterestedIn().stream().map(Enum::name).collect(Collectors.joining(","));
        }

        public double getLat() {
            return user.getLat();
        }

        public double getLon() {
            return user.getLon();
        }

        public boolean getHasLocationSet() {
            return user.hasLocationSet();
        }

        public int getMaxDistanceKm() {
            return user.getMaxDistanceKm();
        }

        public int getMinAge() {
            return user.getMinAge();
        }

        public int getMaxAge() {
            return user.getMaxAge();
        }

        public String getPhotoUrlsCsv() {
            if (user.getPhotoUrls() == null || user.getPhotoUrls().isEmpty()) {
                return null;
            }
            return String.join("|", user.getPhotoUrls());
        }

        public String getState() {
            return user.getState() != null ? user.getState().name() : null;
        }

        public Instant getCreatedAt() {
            return user.getCreatedAt();
        }

        public Instant getUpdatedAt() {
            return user.getUpdatedAt();
        }

        public String getSmoking() {
            return user.getSmoking() != null ? user.getSmoking().name() : null;
        }

        public String getDrinking() {
            return user.getDrinking() != null ? user.getDrinking().name() : null;
        }

        public String getWantsKids() {
            return user.getWantsKids() != null ? user.getWantsKids().name() : null;
        }

        public String getLookingFor() {
            return user.getLookingFor() != null ? user.getLookingFor().name() : null;
        }

        public String getEducation() {
            return user.getEducation() != null ? user.getEducation().name() : null;
        }

        public Integer getHeightCm() {
            return user.getHeightCm();
        }

        public String getInterestsCsv() {
            if (user.getInterests() == null || user.getInterests().isEmpty()) {
                return null;
            }
            return user.getInterests().stream().map(Enum::name).collect(Collectors.joining(","));
        }

        public String getEmail() {
            return user.getEmail();
        }

        public String getPhone() {
            return user.getPhone();
        }

        public Boolean getVerified() {
            return user.isVerified();
        }

        public String getVerificationMethod() {
            return user.getVerificationMethod() != null
                    ? user.getVerificationMethod().name()
                    : null;
        }

        public String getVerificationCode() {
            return user.getVerificationCode();
        }

        public Instant getVerificationSentAt() {
            return user.getVerificationSentAt();
        }

        public Instant getVerifiedAt() {
            return user.getVerifiedAt();
        }

        public String getDealbreakerSmokingCsv() {
            return serializeEnumSet(dealbreakers.acceptableSmoking());
        }

        public String getDealbreakerDrinkingCsv() {
            return serializeEnumSet(dealbreakers.acceptableDrinking());
        }

        public String getDealbreakerWantsKidsCsv() {
            return serializeEnumSet(dealbreakers.acceptableKidsStance());
        }

        public String getDealbreakerLookingForCsv() {
            return serializeEnumSet(dealbreakers.acceptableLookingFor());
        }

        public String getDealbreakerEducationCsv() {
            return serializeEnumSet(dealbreakers.acceptableEducation());
        }

        public Integer getDealbreakerMinHeightCm() {
            return dealbreakers.minHeightCm();
        }

        public Integer getDealbreakerMaxHeightCm() {
            return dealbreakers.maxHeightCm();
        }

        public Integer getDealbreakerMaxAgeDiff() {
            return dealbreakers.maxAgeDifference();
        }

        public String getPaceMessagingFrequency() {
            return (pace != null && pace.messagingFrequency() != null)
                    ? pace.messagingFrequency().name()
                    : null;
        }

        public String getPaceTimeToFirstDate() {
            return (pace != null && pace.timeToFirstDate() != null)
                    ? pace.timeToFirstDate().name()
                    : null;
        }

        public String getPaceCommunicationStyle() {
            return (pace != null && pace.communicationStyle() != null)
                    ? pace.communicationStyle().name()
                    : null;
        }

        public String getPaceDepthPreference() {
            return (pace != null && pace.depthPreference() != null)
                    ? pace.depthPreference().name()
                    : null;
        }

        public Instant getDeletedAt() {
            return user.getDeletedAt();
        }

        private String serializeEnumSet(Set<? extends Enum<?>> values) {
            if (values == null || values.isEmpty()) {
                return null;
            }
            return values.stream().map(Enum::name).collect(Collectors.joining(","));
        }
    }
}
