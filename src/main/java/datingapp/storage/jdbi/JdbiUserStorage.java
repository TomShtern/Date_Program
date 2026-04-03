package datingapp.storage.jdbi;

import datingapp.core.AppClock;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.model.User.UserState;
import datingapp.core.model.User.VerificationMethod;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.storage.PageData;
import datingapp.core.storage.UserStorage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
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

/** Consolidated JDBI-backed user storage implementation. */
public final class JdbiUserStorage implements UserStorage {

    private static final int MAX_CACHE_SIZE = 500;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String GENDER_COLUMN = "gender";

    private final Jdbi jdbi;
    private final Dao dao;
    private final NormalizedProfileRepository normalizedProfileRepository;
    private final NormalizedProfileHydrator normalizedProfileHydrator;
    private final Map<UUID, CacheEntry<User>> userCache = new LinkedHashMap<>(16, 0.75f, true);

    enum NormalizedGroup {
        PHOTOS("photos"),
        INTERESTS("interests"),
        INTERESTED_IN("interested_in"),
        DEALBREAKER("dealbreaker");

        private final String storageName;

        NormalizedGroup(String storageName) {
            this.storageName = storageName;
        }
    }

    public JdbiUserStorage(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
        this.dao = jdbi.onDemand(Dao.class);
        this.normalizedProfileRepository = new NormalizedProfileRepository(jdbi);
        this.normalizedProfileHydrator = new NormalizedProfileHydrator(new DealbreakerAssembler());
    }

    /** Clears the user read cache. Primarily for tests. */
    public void clearCache() {
        synchronized (userCache) {
            userCache.clear();
        }
    }

    @Override
    public void save(User user) {
        Objects.requireNonNull(user, "user cannot be null");
        try {
            jdbi.useTransaction(handle -> saveWithHandle(handle, user));
        } finally {
            invalidateCachedUser(user.getId());
        }
    }

    @Override
    public Optional<User> get(UUID id) {
        Optional<User> cached = getCachedUser(id);
        if (cached.isPresent()) {
            return cached.map(JdbiUserStorage::copyUser);
        }

        Optional<User> loaded = jdbi.withHandle(handle -> loadUser(handle, id));
        loaded.ifPresent(user -> cacheUser(id, user));
        return loaded.map(JdbiUserStorage::copyUser);
    }

    @Override
    public List<User> findActive() {
        return jdbi.withHandle(
                handle -> hydrateUsers(handle, handle.attach(Dao.class).findActive()));
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
        LocalDate today = AppClock.today(ZoneOffset.UTC);
        LocalDate oldestBirthDate = today.minusYears(maxAge);
        LocalDate youngestBirthDate = today.minusYears(minAge);

        return jdbi.withHandle(handle -> {
            int effectiveDistanceKm = Math.clamp(maxDistanceKm, 0, 20_000);
            double latDelta = effectiveDistanceKm / 111.0;
            double cosLat = Math.max(Math.cos(Math.toRadians(Math.abs(seekerLat))), 0.0001);
            double lonDelta = Math.min(effectiveDistanceKm / (111.0 * cosLat), 180.0);

            List<User> candidates = handle.createQuery("SELECT * FROM users WHERE id <> :excludeId"
                            + " AND state = 'ACTIVE'"
                            + " AND deleted_at IS NULL"
                            + " AND gender IN (<genders>)"
                            + " AND birth_date BETWEEN :oldestBirthDate AND :youngestBirthDate"
                            + " AND has_location_set = TRUE"
                            + " AND lat BETWEEN :latMin AND :latMax"
                            + " AND lon BETWEEN :lonMin AND :lonMax")
                    .bindList("genders", genderNames)
                    .bind("excludeId", excludeId)
                    .bind("oldestBirthDate", oldestBirthDate)
                    .bind("youngestBirthDate", youngestBirthDate)
                    .bind("latMin", seekerLat - latDelta)
                    .bind("latMax", seekerLat + latDelta)
                    .bind("lonMin", seekerLon - lonDelta)
                    .bind("lonMax", seekerLon + lonDelta)
                    .map(new Mapper())
                    .list();
            return hydrateUsers(handle, candidates);
        });
    }

    @Override
    public List<User> findAll() {
        return jdbi.withHandle(
                handle -> hydrateUsers(handle, handle.attach(Dao.class).findAll()));
    }

    @Override
    public PageData<User> getPageOfActiveUsers(int offset, int limit) {
        return loadPagedUsers(offset, limit, Dao::countActiveUsers, Dao::getPageOfActiveUsers);
    }

    @Override
    public PageData<User> getPageOfAllUsers(int offset, int limit) {
        return loadPagedUsers(offset, limit, Dao::countAllUsers, Dao::getPageOfAllUsers);
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
            hydrateUsers(handle, users);

            java.util.HashMap<UUID, User> result = new java.util.HashMap<>();
            for (User user : users) {
                result.put(user.getId(), user);
            }
            return result;
        });
    }

    @Override
    public void delete(UUID id) {
        try {
            dao.delete(id);
        } finally {
            invalidateCachedUser(id);
        }
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

    @Override
    public void executeWithUserLock(UUID userId, Runnable operation) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        withLockedHandle(userId, handle -> {
            operation.run();
            return null;
        });
    }

    @Override
    public <T> T withUserLock(UUID userId, Function<UserStorage.LockedUserAccess, T> operation) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(operation, "operation cannot be null");
        return withLockedHandle(
                userId,
                handle -> operation.apply(new LockedUserAccess() {
                    @Override
                    public Optional<User> get(UUID lockedUserId) {
                        return loadUser(handle, lockedUserId).map(JdbiUserStorage::copyUser);
                    }

                    @Override
                    public void save(User user) {
                        saveWithHandle(handle, user);
                        invalidateCachedUser(user.getId());
                    }
                }));
    }

    public void saveUserPhotos(UUID userId, List<String> urls) {
        saveNormalizedProfileFragment(userId, () -> normalizedProfileRepository.saveUserPhotos(userId, urls));
    }

    public List<String> loadUserPhotos(UUID userId) {
        return normalizedProfileRepository.loadUserPhotos(userId);
    }

    public void saveUserInterests(UUID userId, Set<String> interests) {
        saveNormalizedProfileFragment(userId, () -> normalizedProfileRepository.saveUserInterests(userId, interests));
    }

    public Set<String> loadUserInterests(UUID userId) {
        return normalizedProfileRepository.loadUserInterests(userId);
    }

    public void saveUserInterestedIn(UUID userId, Set<String> genders) {
        saveNormalizedProfileFragment(userId, () -> normalizedProfileRepository.saveUserInterestedIn(userId, genders));
    }

    public Set<String> loadUserInterestedIn(UUID userId) {
        return normalizedProfileRepository.loadUserInterestedIn(userId);
    }

    public void saveDealbreaker(UUID userId, String tableName, Set<String> values) {
        saveNormalizedProfileFragment(
                userId, () -> normalizedProfileRepository.saveDealbreaker(userId, tableName, values));
    }

    public Set<String> loadDealbreaker(UUID userId, String tableName) {
        return normalizedProfileRepository.loadDealbreaker(userId, tableName);
    }

    private Optional<User> loadUser(Handle handle, UUID id) {
        return handle.attach(Dao.class).get(id).map(user -> hydrateUsers(handle, List.of(user))
                .getFirst());
    }

    private List<User> hydrateUsers(Handle handle, List<User> users) {
        if (users == null || users.isEmpty()) {
            return users;
        }
        List<UUID> userIds = users.stream().map(User::getId).toList();
        return normalizedProfileHydrator.hydrate(
                users, normalizedProfileRepository.loadNormalizedProfileData(handle, userIds));
    }

    private PageData<User> loadPagedUsers(int offset, int limit, ToIntFunction<Dao> counter, PageFetcher pageFetcher) {
        validatePageArguments(offset, limit);
        return jdbi.withHandle(handle -> {
            Dao localDao = handle.attach(Dao.class);
            int total = counter.applyAsInt(localDao);
            if (offset >= total) {
                return PageData.empty(limit, total);
            }

            List<User> page = hydrateUsers(handle, pageFetcher.fetch(localDao, offset, limit));
            return new PageData<>(page, total, offset, limit);
        });
    }

    private Optional<User> getCachedUser(UUID id) {
        Instant now = AppClock.now();
        synchronized (userCache) {
            pruneExpiredEntriesLocked(now);
            CacheEntry<User> entry = userCache.get(id);
            return entry != null ? Optional.of(entry.value()) : Optional.empty();
        }
    }

    private void cacheUser(UUID id, User user) {
        Instant now = AppClock.now();
        synchronized (userCache) {
            pruneExpiredEntriesLocked(now);
            if (!userCache.containsKey(id) && userCache.size() >= MAX_CACHE_SIZE) {
                evictOldestEntryLocked();
            }
            userCache.put(id, new CacheEntry<>(user, now.plus(CACHE_TTL)));
        }
    }

    private void invalidateCachedUser(UUID id) {
        synchronized (userCache) {
            userCache.remove(id);
        }
    }

    private void pruneExpiredEntriesLocked(Instant now) {
        var iterator = userCache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired(now)) {
                iterator.remove();
            }
        }
    }

    private void evictOldestEntryLocked() {
        var iterator = userCache.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private void saveWithHandle(Handle handle, User user) {
        handle.attach(Dao.class).save(new UserSqlBindings(user));
        normalizedProfileRepository.saveNormalizedProfileData(handle, user);
    }

    private void saveNormalizedProfileFragment(UUID userId, Runnable saveOperation) {
        try {
            saveOperation.run();
        } finally {
            invalidateCachedUser(userId);
        }
    }

    private <T> T withLockedHandle(UUID userId, Function<Handle, T> operation) {
        return jdbi.inTransaction(handle -> {
            handle.createQuery("SELECT id FROM users WHERE id = :userId FOR UPDATE")
                    .bind("userId", userId)
                    .mapTo(UUID.class)
                    .first();
            return operation.apply(handle);
        });
    }

    static NormalizedGroup normalizedGroupFromStorage(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            throw new IllegalArgumentException("Normalized group cannot be blank");
        }
        for (NormalizedGroup group : NormalizedGroup.values()) {
            if (group.storageName.equals(groupName)) {
                return group;
            }
        }
        throw new IllegalArgumentException("Unknown normalized group: " + groupName);
    }

    private static User copyUser(User source) {
        return source.copy();
    }

    private static void validatePageArguments(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
    }

    @FunctionalInterface
    private interface PageFetcher {
        List<User> fetch(Dao dao, int offset, int limit);
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
        private boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    @RegisterRowMapper(Mapper.class)
    @RegisterRowMapper(ProfileNoteMapper.class)
    private interface Dao {

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

        @SqlQuery("SELECT COUNT(*) FROM users WHERE state = 'ACTIVE' AND deleted_at IS NULL")
        int countActiveUsers();

        @SqlQuery("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL")
        int countAllUsers();

        @SqlQuery("SELECT * FROM users"
                + " WHERE state = 'ACTIVE' AND deleted_at IS NULL"
                + " ORDER BY created_at DESC, id ASC"
                + " LIMIT :limit OFFSET :offset")
        List<User> getPageOfActiveUsers(@Bind("offset") int offset, @Bind("limit") int limit);

        @SqlQuery("SELECT * FROM users"
                + " WHERE deleted_at IS NULL"
                + " ORDER BY created_at DESC, id ASC"
                + " LIMIT :limit OFFSET :offset")
        List<User> getPageOfAllUsers(@Bind("offset") int offset, @Bind("limit") int limit);

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
                    .gender(JdbiTypeCodecs.SqlRowReaders.readEnum(rs, GENDER_COLUMN, Gender.class))
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

        private Dealbreakers readDealbreakers(ResultSet rs) throws SQLException {
            Integer dbMinHeight = JdbiTypeCodecs.SqlRowReaders.readInteger(rs, "db_min_height_cm");
            Integer dbMaxHeight = JdbiTypeCodecs.SqlRowReaders.readInteger(rs, "db_max_height_cm");
            Integer dbMaxAgeDiff = JdbiTypeCodecs.SqlRowReaders.readInteger(rs, "db_max_age_diff");

            return new Dealbreakers(
                    Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), dbMinHeight, dbMaxHeight, dbMaxAgeDiff);
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

        public Integer getDealbreakerMinHeightCm() {
            return dealbreakers == null ? null : dealbreakers.minHeightCm();
        }

        public Integer getDealbreakerMaxHeightCm() {
            return dealbreakers == null ? null : dealbreakers.maxHeightCm();
        }

        public Integer getDealbreakerMaxAgeDiff() {
            return dealbreakers == null ? null : dealbreakers.maxAgeDifference();
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
