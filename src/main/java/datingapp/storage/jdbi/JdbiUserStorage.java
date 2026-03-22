package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.model.User.VerificationMethod;
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Consolidated JDBI-backed user storage implementation. */
public final class JdbiUserStorage implements UserStorage {

    private static final Logger LOGGER = LoggerFactory.getLogger(JdbiUserStorage.class);
    private static final String USER_ID_BIND = "userId";
    private static final String USER_DB_SMOKING = "user_db_smoking";
    private static final String USER_DB_DRINKING = "user_db_drinking";
    private static final String USER_DB_WANTS_KIDS = "user_db_wants_kids";
    private static final String USER_DB_LOOKING_FOR = "user_db_looking_for";
    private static final String USER_DB_EDUCATION = "user_db_education";

    private final Jdbi jdbi;
    private final Dao dao;

    public JdbiUserStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.dao = jdbi.onDemand(Dao.class);
    }

    @Override
    public void save(User user) {
        Objects.requireNonNull(user, "user cannot be null");
        jdbi.useTransaction(handle -> {
            handle.attach(Dao.class).save(new UserSqlBindings(user));
            saveNormalizedProfileData(handle, user);
        });
    }

    @Override
    public Optional<User> get(UUID id) {
        return dao.get(id).map(this::applyNormalizedProfileData);
    }

    @Override
    public List<User> findActive() {
        return applyNormalizedProfileData(dao.findActive());
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
            double effectiveDistanceKm = Math.min(Math.max(maxDistanceKm, 0), 20_000);
            double latDelta = effectiveDistanceKm / 111.0;
            double cosLat = Math.max(Math.cos(Math.toRadians(Math.abs(seekerLat))), 0.0001);
            double lonDelta = Math.min(effectiveDistanceKm / (111.0 * cosLat), 180.0);

            StringBuilder sql = new StringBuilder("SELECT * FROM users WHERE id <> :excludeId")
                    .append(" AND state = 'ACTIVE'")
                    .append(" AND deleted_at IS NULL")
                    .append(" AND gender IN (<genders>)")
                    .append(" AND DATEDIFF('YEAR', birth_date, CURRENT_DATE) BETWEEN :minAge AND :maxAge")
                    .append(" AND has_location_set = TRUE")
                    .append(" AND lat BETWEEN :latMin AND :latMax")
                    .append(" AND lon BETWEEN :lonMin AND :lonMax");

            return handle.createQuery(sql.toString())
                    .bindList("genders", genderNames)
                    .bind("excludeId", excludeId)
                    .bind("minAge", minAge)
                    .bind("maxAge", maxAge)
                    .bind("latMin", seekerLat - latDelta)
                    .bind("latMax", seekerLat + latDelta)
                    .bind("lonMin", seekerLon - lonDelta)
                    .bind("lonMax", seekerLon + lonDelta)
                    .map(new Mapper())
                    .list();
        });
    }

    @Override
    public List<User> findAll() {
        return applyNormalizedProfileData(dao.findAll());
    }

    @Override
    public Map<UUID, User> findByIds(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        return jdbi.withHandle(handle -> {
            List<User> users = handle.createQuery("SELECT * FROM users WHERE id IN (<userIds>) AND deleted_at IS NULL")
                    .bindList("userIds", new ArrayList<>(ids))
                    .map(new Mapper())
                    .list();
            applyNormalizedProfileData(users);

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
        return dao.deleteProfileNoteInternal(authorId, subjectId, AppClock.now()) > 0;
    }

    // ═══════════════════════════════════════════════════════════════
    // Normalized table DAO methods (V3 schema)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Replaces all photo URLs for a user in the normalized {@code user_photos}
     * table. Uses delete-then-insert within a transaction to ensure atomicity.
     */
    public void saveUserPhotos(UUID userId, List<String> urls) {
        jdbi.useTransaction(handle -> saveUserPhotos(handle, userId, urls));
    }

    /**
     * Loads all photo URLs for a user from the normalized table, ordered by
     * position.
     */
    public List<String> loadUserPhotos(UUID userId) {
        return jdbi.withHandle(
                handle -> handle.createQuery("SELECT url FROM user_photos WHERE user_id = :userId ORDER BY position")
                        .bind(USER_ID_BIND, userId)
                        .mapTo(String.class)
                        .list());
    }

    /**
     * Replaces all interests for a user in the normalized {@code user_interests}
     * table.
     */
    public void saveUserInterests(UUID userId, Set<String> interests) {
        jdbi.useTransaction(handle -> saveUserInterests(handle, userId, interests));
    }

    /** Loads all interests for a user from the normalized table. */
    public Set<String> loadUserInterests(UUID userId) {
        return jdbi.withHandle(handle ->
                new HashSet<>(handle.createQuery("SELECT interest FROM user_interests WHERE user_id = :userId")
                        .bind(USER_ID_BIND, userId)
                        .mapTo(String.class)
                        .list()));
    }

    /**
     * Replaces all gender preferences for a user in the normalized
     * {@code user_interested_in} table.
     */
    public void saveUserInterestedIn(UUID userId, Set<String> genders) {
        jdbi.useTransaction(handle -> saveUserInterestedIn(handle, userId, genders));
    }

    /** Loads all gender preferences for a user from the normalized table. */
    public Set<String> loadUserInterestedIn(UUID userId) {
        return jdbi.withHandle(handle ->
                new HashSet<>(handle.createQuery("SELECT gender FROM user_interested_in WHERE user_id = :userId")
                        .bind(USER_ID_BIND, userId)
                        .mapTo(String.class)
                        .list()));
    }

    /**
     * Replaces all values for a single dealbreaker dimension in its normalized
     * table. The {@code tableName} must be one of: {@code user_db_smoking},
     * {@code user_db_drinking}, {@code user_db_wants_kids},
     * {@code user_db_looking_for}, {@code user_db_education}.
     */
    public void saveDealbreaker(UUID userId, String tableName, Set<String> values) {
        validateNormalizedTable(tableName);
        jdbi.useTransaction(handle -> saveDealbreaker(handle, userId, tableName, values));
    }

    private void saveNormalizedProfileData(Handle handle, User user) {
        UUID userId = user.getId();
        saveUserPhotos(handle, userId, user.getPhotoUrls());
        saveUserInterests(handle, userId, enumNames(user.getInterests()));
        saveUserInterestedIn(handle, userId, enumNames(user.getInterestedIn()));

        Dealbreakers dealbreakers = user.getDealbreakers();
        saveDealbreaker(
                handle,
                userId,
                USER_DB_SMOKING,
                dealbreakers != null ? enumNames(dealbreakers.acceptableSmoking()) : Set.of());
        saveDealbreaker(
                handle,
                userId,
                USER_DB_DRINKING,
                dealbreakers != null ? enumNames(dealbreakers.acceptableDrinking()) : Set.of());
        saveDealbreaker(
                handle,
                userId,
                USER_DB_WANTS_KIDS,
                dealbreakers != null ? enumNames(dealbreakers.acceptableKidsStance()) : Set.of());
        saveDealbreaker(
                handle,
                userId,
                USER_DB_LOOKING_FOR,
                dealbreakers != null ? enumNames(dealbreakers.acceptableLookingFor()) : Set.of());
        saveDealbreaker(
                handle,
                userId,
                USER_DB_EDUCATION,
                dealbreakers != null ? enumNames(dealbreakers.acceptableEducation()) : Set.of());
    }

    private void saveUserPhotos(Handle handle, UUID userId, List<String> urls) {
        handle.execute("DELETE FROM user_photos WHERE user_id = ?", userId);
        if (urls == null || urls.isEmpty()) {
            return;
        }
        try (var batch = handle.prepareBatch(
                "INSERT INTO user_photos (user_id, position, url) VALUES (:userId, :position, :url)")) {
            for (int i = 0; i < urls.size(); i++) {
                batch.bind(USER_ID_BIND, userId)
                        .bind("position", i)
                        .bind("url", urls.get(i))
                        .add();
            }
            batch.execute();
        }
    }

    private void saveUserInterests(Handle handle, UUID userId, Set<String> interests) {
        handle.execute("DELETE FROM user_interests WHERE user_id = ?", userId);
        if (interests == null || interests.isEmpty()) {
            return;
        }
        try (var batch =
                handle.prepareBatch("INSERT INTO user_interests (user_id, interest) VALUES (:userId, :interest)")) {
            for (String interest : interests) {
                batch.bind(USER_ID_BIND, userId).bind("interest", interest).add();
            }
            batch.execute();
        }
    }

    private void saveUserInterestedIn(Handle handle, UUID userId, Set<String> genders) {
        handle.execute("DELETE FROM user_interested_in WHERE user_id = ?", userId);
        if (genders == null || genders.isEmpty()) {
            return;
        }
        try (var batch =
                handle.prepareBatch("INSERT INTO user_interested_in (user_id, gender) VALUES (:userId, :gender)")) {
            for (String gender : genders) {
                batch.bind(USER_ID_BIND, userId).bind("gender", gender).add();
            }
            batch.execute();
        }
    }

    private void saveDealbreaker(Handle handle, UUID userId, String tableName, Set<String> values) {
        validateNormalizedTable(tableName);
        handle.execute("DELETE FROM " + tableName + " WHERE user_id = ?", userId);
        if (values == null || values.isEmpty()) {
            return;
        }
        try (var batch =
                handle.prepareBatch("INSERT INTO " + tableName + " (user_id, \"value\") VALUES (:userId, :value)")) {
            for (String value : values) {
                batch.bind(USER_ID_BIND, userId).bind("value", value).add();
            }
            batch.execute();
        }
    }

    private List<User> applyNormalizedProfileData(List<User> users) {
        users.forEach(this::applyNormalizedProfileData);
        return users;
    }

    private User applyNormalizedProfileData(User user) {
        UUID userId = user.getId();
        user.setPhotoUrls(loadUserPhotos(userId));
        user.setInterests(parseEnumNames(loadUserInterests(userId), Interest.class));
        user.setInterestedIn(parseEnumNames(loadUserInterestedIn(userId), Gender.class));

        Dealbreakers existing = user.getDealbreakers();
        Dealbreakers.Builder builder = (existing != null ? existing : Dealbreakers.none()).toBuilder();

        Set<Lifestyle.Smoking> smoking =
                parseEnumNames(loadDealbreaker(userId, USER_DB_SMOKING), Lifestyle.Smoking.class);
        builder.clearSmoking();
        builder.acceptSmoking(smoking.toArray(Lifestyle.Smoking[]::new));

        Set<Lifestyle.Drinking> drinking =
                parseEnumNames(loadDealbreaker(userId, USER_DB_DRINKING), Lifestyle.Drinking.class);
        builder.clearDrinking();
        builder.acceptDrinking(drinking.toArray(Lifestyle.Drinking[]::new));

        Set<Lifestyle.WantsKids> kids =
                parseEnumNames(loadDealbreaker(userId, USER_DB_WANTS_KIDS), Lifestyle.WantsKids.class);
        builder.clearKids();
        builder.acceptKidsStance(kids.toArray(Lifestyle.WantsKids[]::new));

        Set<Lifestyle.LookingFor> lookingFor =
                parseEnumNames(loadDealbreaker(userId, USER_DB_LOOKING_FOR), Lifestyle.LookingFor.class);
        builder.clearLookingFor();
        builder.acceptLookingFor(lookingFor.toArray(Lifestyle.LookingFor[]::new));

        Set<Lifestyle.Education> education =
                parseEnumNames(loadDealbreaker(userId, USER_DB_EDUCATION), Lifestyle.Education.class);
        builder.clearEducation();
        builder.requireEducation(education.toArray(Lifestyle.Education[]::new));
        user.setDealbreakers(builder.build());

        return user;
    }

    private static Set<String> enumNames(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream().map(Enum::name).collect(Collectors.toSet());
    }

    private static <E extends Enum<E>> Set<E> parseEnumNames(Set<String> values, Class<E> enumType) {
        if (values == null || values.isEmpty()) {
            return EnumSet.noneOf(enumType);
        }
        EnumSet<E> parsed = EnumSet.noneOf(enumType);
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            try {
                parsed.add(Enum.valueOf(enumType, value.trim()));
            } catch (IllegalArgumentException _) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "Ignoring invalid {} value '{}' during compatibility read",
                            enumType.getSimpleName(),
                            value);
                }
            }
        }
        return parsed;
    }

    /**
     * Loads all values for a single dealbreaker dimension from its normalized
     * table.
     */
    public Set<String> loadDealbreaker(UUID userId, String tableName) {
        validateNormalizedTable(tableName);
        return jdbi.withHandle(handle ->
                new HashSet<>(handle.createQuery("SELECT \"value\" FROM " + tableName + " WHERE user_id = :userId")
                        .bind(USER_ID_BIND, userId)
                        .mapTo(String.class)
                        .list()));
    }

    private static final Set<String> VALID_DEALBREAKER_TABLES =
            Set.of(USER_DB_SMOKING, USER_DB_DRINKING, USER_DB_WANTS_KIDS, USER_DB_LOOKING_FOR, USER_DB_EDUCATION);

    private static void validateNormalizedTable(String tableName) {
        if (!VALID_DEALBREAKER_TABLES.contains(tableName)) {
            throw new IllegalArgumentException("Invalid dealbreaker table: " + tableName);
        }
    }

    @RegisterRowMapper(Mapper.class)
    @RegisterRowMapper(ProfileNoteMapper.class)
    private static interface Dao {

        @SqlUpdate("""
                MERGE INTO users (
                    id, name, bio, birth_date, gender, lat, lon,
                    has_location_set, max_distance_km, min_age, max_age, state, created_at,
                    updated_at, smoking, drinking, wants_kids, looking_for, education,
                    height_cm, db_min_height_cm, db_max_height_cm, db_max_age_diff,
                    email, phone, is_verified, verification_method,
                    verification_code, verification_sent_at, verified_at,
                    pace_messaging_frequency, pace_time_to_first_date,
                    pace_communication_style, pace_depth_preference, deleted_at
                ) KEY (id) VALUES (
                    :id, :name, :bio, :birthDate, :gender,
                    :lat, :lon, :hasLocationSet, :maxDistanceKm, :minAge, :maxAge,
                    :state, :createdAt, :updatedAt,
                    :smoking, :drinking, :wantsKids, :lookingFor,
                    :education, :heightCm,
                    :dealbreakerMinHeightCm, :dealbreakerMaxHeightCm, :dealbreakerMaxAgeDiff,
                    :email, :phone, :verified, :verificationMethod,
                    :verificationCode, :verificationSentAt, :verifiedAt,
                    :paceMessagingFrequency, :paceTimeToFirstDate,
                    :paceCommunicationStyle, :paceDepthPreference, :deletedAt
                )
                """)
        void save(@BindBean UserSqlBindings helper);

        @SqlQuery("SELECT * FROM users WHERE id = :id AND deleted_at IS NULL")
        Optional<User> get(@Bind("id") UUID id);

        @SqlQuery("SELECT * FROM users WHERE state = 'ACTIVE' AND deleted_at IS NULL")
        List<User> findActive();

        @SqlQuery("SELECT * FROM users WHERE deleted_at IS NULL")
        List<User> findAll();

        @SqlUpdate("DELETE FROM users WHERE id = :id")
        void delete(@Bind("id") UUID id);

        @SqlUpdate("DELETE FROM users WHERE deleted_at IS NOT NULL AND deleted_at < :threshold")
        int purgeDeletedBefore(@Bind("threshold") Instant threshold);

        @SqlUpdate("""
                MERGE INTO profile_notes (author_id, subject_id, content, created_at, updated_at, deleted_at)
                KEY (author_id, subject_id)
                VALUES (:authorId, :subjectId, :content, :createdAt, :updatedAt, NULL)
                """)
        void saveProfileNote(@BindMethods ProfileNote note);

        @SqlQuery("""
                SELECT author_id, subject_id, content, created_at, updated_at
                FROM profile_notes
                WHERE author_id = :authorId AND subject_id = :subjectId AND deleted_at IS NULL
                """)
        Optional<ProfileNote> getProfileNote(@Bind("authorId") UUID authorId, @Bind("subjectId") UUID subjectId);

        @SqlQuery("""
                SELECT author_id, subject_id, content, created_at, updated_at
                FROM profile_notes
                WHERE author_id = :authorId AND deleted_at IS NULL
                ORDER BY updated_at DESC
                """)
        List<ProfileNote> getProfileNotesByAuthor(@Bind("authorId") UUID authorId);

        @SqlUpdate("""
                UPDATE profile_notes
                SET deleted_at = :now
                WHERE author_id = :authorId AND subject_id = :subjectId AND deleted_at IS NULL
                """)
        int deleteProfileNoteInternal(
                @Bind("authorId") UUID authorId, @Bind("subjectId") UUID subjectId, @Bind("now") Instant now);
    }

    /** Row mapper for User entity. */
    public static class Mapper implements RowMapper<User> {

        @Override
        public User map(ResultSet rs, StatementContext ctx) throws SQLException {
            Double lat = JdbiTypeCodecs.SqlRowReaders.readDouble(rs, "lat");
            Double lon = JdbiTypeCodecs.SqlRowReaders.readDouble(rs, "lon");
            Boolean hasLocationFlag = rs.getObject("has_location_set", Boolean.class);
            boolean hasLocationSet = Boolean.TRUE.equals(hasLocationFlag);
            User user = User.StorageBuilder.create(
                            JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "id"),
                            rs.getString("name"),
                            JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "created_at"))
                    .bio(rs.getString("bio"))
                    .birthDate(JdbiTypeCodecs.SqlRowReaders.readLocalDate(rs, "birth_date"))
                    .gender(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "gender", Gender.class))
                    .location(lat != null ? lat : 0.0, lon != null ? lon : 0.0)
                    .hasLocationSet(hasLocationSet)
                    .maxDistanceKm(rs.getInt("max_distance_km"))
                    .ageRange(rs.getInt("min_age"), rs.getInt("max_age"))
                    .state(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, "state", UserState.class))
                    .updatedAt(JdbiTypeCodecs.SqlRowReaders.readInstant(rs, "updated_at"))
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

            return new Dealbreakers(
                    Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), dbMinHeight, dbMaxHeight, dbMaxAgeDiff);
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

        public Integer getDealbreakerMinHeightCm() {
            if (dealbreakers == null) {
                return null;
            }
            return dealbreakers.minHeightCm();
        }

        public Integer getDealbreakerMaxHeightCm() {
            if (dealbreakers == null) {
                return null;
            }
            return dealbreakers.maxHeightCm();
        }

        public Integer getDealbreakerMaxAgeDiff() {
            if (dealbreakers == null) {
                return null;
            }
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
    }
}
