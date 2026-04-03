package datingapp.storage.jdbi;

import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Query;

final class NormalizedProfileRepository {

    private static final String USER_ID_BIND = "userId";
    private static final String NORMALIZED_VALUE_COLUMN = "value";
    private static final String GENDER_COLUMN = "gender";
    private static final String USER_DB_SMOKING = "user_db_smoking";
    private static final String USER_DB_DRINKING = "user_db_drinking";
    private static final String USER_DB_WANTS_KIDS = "user_db_wants_kids";
    private static final String USER_DB_LOOKING_FOR = "user_db_looking_for";
    private static final String USER_DB_EDUCATION = "user_db_education";

    private final Jdbi jdbi;

    NormalizedProfileRepository(Jdbi jdbi) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi cannot be null");
    }

    void saveNormalizedProfileData(Handle handle, User user) {
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

    void saveUserPhotos(UUID userId, List<String> urls) {
        jdbi.useTransaction(handle -> saveUserPhotos(handle, userId, urls));
    }

    List<String> loadUserPhotos(UUID userId) {
        return jdbi.withHandle(handle -> loadUserPhotos(handle, userId));
    }

    void saveUserInterests(UUID userId, Set<String> interests) {
        jdbi.useTransaction(handle -> saveUserInterests(handle, userId, interests));
    }

    Set<String> loadUserInterests(UUID userId) {
        return jdbi.withHandle(handle -> loadUserInterests(handle, userId));
    }

    void saveUserInterestedIn(UUID userId, Set<String> genders) {
        jdbi.useTransaction(handle -> saveUserInterestedIn(handle, userId, genders));
    }

    Set<String> loadUserInterestedIn(UUID userId) {
        return jdbi.withHandle(handle -> loadUserInterestedIn(handle, userId));
    }

    void saveDealbreaker(UUID userId, String tableName, Set<String> values) {
        validateNormalizedTable(tableName);
        jdbi.useTransaction(handle -> saveDealbreaker(handle, userId, tableName, values));
    }

    Set<String> loadDealbreaker(UUID userId, String tableName) {
        validateNormalizedTable(tableName);
        return jdbi.withHandle(handle -> loadDealbreaker(handle, userId, tableName));
    }

    void saveUserPhotos(Handle handle, UUID userId, List<String> urls) {
        replaceNormalizedCollection(
                handle,
                userId,
                "DELETE FROM user_photos WHERE user_id = ?",
                "INSERT INTO user_photos (user_id, position, url) VALUES (:userId, :position, :url)",
                urls,
                (batch, indexedValue) -> batch.bind(USER_ID_BIND, userId)
                        .bind("position", indexedValue.index())
                        .bind("url", indexedValue.value()));
    }

    List<String> loadUserPhotos(Handle handle, UUID userId) {
        try (Query query = handle.createQuery("SELECT url FROM user_photos WHERE user_id = :userId ORDER BY position")
                .bind(USER_ID_BIND, userId)) {
            return query.mapTo(String.class).list();
        }
    }

    void saveUserInterests(Handle handle, UUID userId, Set<String> interests) {
        replaceNormalizedCollection(
                handle,
                userId,
                "DELETE FROM user_interests WHERE user_id = ?",
                "INSERT INTO user_interests (user_id, interest) VALUES (:userId, :interest)",
                interests,
                (batch, indexedValue) -> batch.bind(USER_ID_BIND, userId).bind("interest", indexedValue.value()));
    }

    Set<String> loadUserInterests(Handle handle, UUID userId) {
        try (Query query = handle.createQuery("SELECT interest FROM user_interests WHERE user_id = :userId")
                .bind(USER_ID_BIND, userId)) {
            return new HashSet<>(query.mapTo(String.class).list());
        }
    }

    void saveUserInterestedIn(Handle handle, UUID userId, Set<String> genders) {
        replaceNormalizedCollection(
                handle,
                userId,
                "DELETE FROM user_interested_in WHERE user_id = ?",
                "INSERT INTO user_interested_in (user_id, gender) VALUES (:userId, :gender)",
                genders,
                (batch, indexedValue) -> batch.bind(USER_ID_BIND, userId).bind(GENDER_COLUMN, indexedValue.value()));
    }

    Set<String> loadUserInterestedIn(Handle handle, UUID userId) {
        try (Query query = handle.createQuery("SELECT gender FROM user_interested_in WHERE user_id = :userId")
                .bind(USER_ID_BIND, userId)) {
            return new HashSet<>(query.mapTo(String.class).list());
        }
    }

    void saveDealbreaker(Handle handle, UUID userId, String tableName, Set<String> values) {
        DealbreakerTable dealbreakerTable = dealbreakerTableFromStorage(tableName);
        replaceNormalizedCollection(
                handle,
                userId,
                "DELETE FROM " + dealbreakerTable.storageName + " WHERE user_id = ?",
                "INSERT INTO " + dealbreakerTable.storageName + " (user_id, \"value\") VALUES (:userId, :value)",
                values,
                (batch, indexedValue) ->
                        batch.bind(USER_ID_BIND, userId).bind(NORMALIZED_VALUE_COLUMN, indexedValue.value()));
    }

    Set<String> loadDealbreaker(Handle handle, UUID userId, String tableName) {
        DealbreakerTable dealbreakerTable = dealbreakerTableFromStorage(tableName);
        try (Query query = handle.createQuery(
                        "SELECT \"value\" FROM " + dealbreakerTable.storageName + " WHERE user_id = :userId")
                .bind(USER_ID_BIND, userId)) {
            return new HashSet<>(query.mapTo(String.class).list());
        }
    }

    NormalizedProfileData loadNormalizedProfileData(Handle handle, Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return NormalizedProfileData.empty();
        }

        String sql = """
            SELECT user_id, group_name, group_key, item_value
            FROM (
            SELECT user_id, 'photos' AS group_name, NULL AS group_key, url AS item_value, position AS sort_order
            FROM user_photos
            WHERE user_id IN (<userIds>)
            UNION ALL
            SELECT user_id, 'interests' AS group_name, NULL AS group_key, interest AS item_value, NULL AS sort_order
            FROM user_interests
            WHERE user_id IN (<userIds>)
            UNION ALL
            SELECT user_id, 'interested_in' AS group_name, NULL AS group_key, gender AS item_value, NULL AS sort_order
            FROM user_interested_in
            WHERE user_id IN (<userIds>)
            UNION ALL
            SELECT user_id, 'dealbreaker' AS group_name, 'user_db_smoking' AS group_key, "value" AS item_value, NULL AS sort_order
                FROM user_db_smoking
                WHERE user_id IN (<userIds>)
                UNION ALL
            SELECT user_id, 'dealbreaker' AS group_name, 'user_db_drinking' AS group_key, "value" AS item_value, NULL AS sort_order
                FROM user_db_drinking
                WHERE user_id IN (<userIds>)
                UNION ALL
            SELECT user_id, 'dealbreaker' AS group_name, 'user_db_wants_kids' AS group_key, "value" AS item_value, NULL AS sort_order
                FROM user_db_wants_kids
                WHERE user_id IN (<userIds>)
                UNION ALL
            SELECT user_id, 'dealbreaker' AS group_name, 'user_db_looking_for' AS group_key, "value" AS item_value, NULL AS sort_order
                FROM user_db_looking_for
                WHERE user_id IN (<userIds>)
            UNION ALL
            SELECT user_id, 'dealbreaker' AS group_name, 'user_db_education' AS group_key, "value" AS item_value, NULL AS sort_order
                FROM user_db_education
                WHERE user_id IN (<userIds>)
            ) AS normalized_profile_data
            ORDER BY user_id, group_name, group_key, sort_order, item_value
            """;

        try (Query query = handle.createQuery(sql)) {
            List<NormalizedProfileRow> rows = query.bindList("userIds", new ArrayList<>(userIds))
                    .map((rs, ctx) -> new NormalizedProfileRow(
                            JdbiTypeCodecs.SqlRowReaders.readUuid(rs, "user_id"),
                            rs.getString("group_name"),
                            rs.getString("group_key"),
                            rs.getString("item_value")))
                    .list();

            Map<UUID, List<String>> photoUrlsByUserId = new HashMap<>();
            Map<UUID, Set<String>> interestsByUserId = new HashMap<>();
            Map<UUID, Set<String>> interestedInByUserId = new HashMap<>();
            Map<UUID, Map<DealbreakerTable, Set<String>>> dealbreakerValuesByUserId = new HashMap<>();
            for (NormalizedProfileRow row : rows) {
                switch (JdbiUserStorage.normalizedGroupFromStorage(row.groupName())) {
                    case PHOTOS ->
                        photoUrlsByUserId
                                .computeIfAbsent(row.userId(), key -> new ArrayList<>())
                                .add(row.itemValue());
                    case INTERESTS ->
                        interestsByUserId
                                .computeIfAbsent(row.userId(), key -> new HashSet<>())
                                .add(row.itemValue());
                    case INTERESTED_IN ->
                        interestedInByUserId
                                .computeIfAbsent(row.userId(), key -> new HashSet<>())
                                .add(row.itemValue());
                    case DEALBREAKER ->
                        dealbreakerValuesByUserId
                                .computeIfAbsent(row.userId(), key -> new EnumMap<>(DealbreakerTable.class))
                                .computeIfAbsent(dealbreakerTableFromStorage(row.groupKey()), key -> new HashSet<>())
                                .add(row.itemValue());
                    default ->
                        throw new IllegalStateException("Unhandled normalized group: "
                                + JdbiUserStorage.normalizedGroupFromStorage(row.groupName()));
                }
            }
            return new NormalizedProfileData(
                    photoUrlsByUserId, interestsByUserId, interestedInByUserId, dealbreakerValuesByUserId);
        }
    }

    private static void validateNormalizedTable(String tableName) {
        dealbreakerTableFromStorage(tableName);
    }

    private static DealbreakerTable dealbreakerTableFromStorage(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Invalid dealbreaker table: " + tableName);
        }
        for (DealbreakerTable dealbreakerTable : DealbreakerTable.values()) {
            if (dealbreakerTable.storageName.equals(tableName)) {
                return dealbreakerTable;
            }
        }
        throw new IllegalArgumentException("Invalid dealbreaker table: " + tableName);
    }

    private static Set<String> enumNames(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream().map(Enum::name).collect(Collectors.toSet());
    }

    private <T> void replaceNormalizedCollection(
            Handle handle,
            UUID userId,
            String deleteSql,
            String insertSql,
            Collection<T> values,
            BiConsumer<org.jdbi.v3.core.statement.PreparedBatch, IndexedValue<T>> binder) {
        handle.execute(deleteSql, userId);
        if (values == null || values.isEmpty()) {
            return;
        }
        try (var batch = handle.prepareBatch(insertSql)) {
            int index = 0;
            for (T value : values) {
                binder.accept(batch, new IndexedValue<>(index, value));
                index++;
                batch.add();
            }
            batch.execute();
        }
    }

    private record IndexedValue<T>(int index, T value) {}

    private record NormalizedProfileRow(UUID userId, String groupName, String groupKey, String itemValue) {}

    record NormalizedProfileData(
            Map<UUID, List<String>> photoUrlsByUserId,
            Map<UUID, Set<String>> interestsByUserId,
            Map<UUID, Set<String>> interestedInByUserId,
            Map<UUID, Map<DealbreakerTable, Set<String>>> dealbreakerValuesByUserId) {

        private static NormalizedProfileData empty() {
            return new NormalizedProfileData(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    enum DealbreakerTable {
        SMOKING(USER_DB_SMOKING),
        DRINKING(USER_DB_DRINKING),
        WANTS_KIDS(USER_DB_WANTS_KIDS),
        LOOKING_FOR(USER_DB_LOOKING_FOR),
        EDUCATION(USER_DB_EDUCATION);

        private final String storageName;

        DealbreakerTable(String storageName) {
            this.storageName = storageName;
        }
    }
}
