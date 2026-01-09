package datingapp.storage;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import datingapp.core.Dealbreakers;
import datingapp.core.Interest;
import datingapp.core.Lifestyle;
import datingapp.core.User;
import datingapp.core.UserStorage;

/**
 * H2 implementation of UserStorage.
 */
public class H2UserStorage implements UserStorage {

    private final DatabaseManager dbManager;

    public H2UserStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public void save(User user) {
        String sql = """
                MERGE INTO users (id, name, bio, birth_date, gender, interested_in, lat, lon,
                                  max_distance_km, min_age, max_age, photo_urls, state, created_at, updated_at,
                                  smoking, drinking, wants_kids, looking_for, education, height_cm,
                                  db_smoking, db_drinking, db_wants_kids, db_looking_for, db_education,
                                  db_min_height_cm, db_max_height_cm, db_max_age_diff, interests)
                KEY (id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, user.getId());
            stmt.setString(2, user.getName());
            stmt.setString(3, user.getBio());
            stmt.setDate(4, user.getBirthDate() != null ? Date.valueOf(user.getBirthDate()) : null);
            stmt.setString(5, user.getGender() != null ? user.getGender().name() : null);
            stmt.setString(6, gendersToString(user.getInterestedIn()));
            stmt.setDouble(7, user.getLat());
            stmt.setDouble(8, user.getLon());
            stmt.setInt(9, user.getMaxDistanceKm());
            stmt.setInt(10, user.getMinAge());
            stmt.setInt(11, user.getMaxAge());
            stmt.setString(12, urlsToString(user.getPhotoUrls()));
            stmt.setString(13, user.getState().name());
            stmt.setTimestamp(14, Timestamp.from(user.getCreatedAt()));
            stmt.setTimestamp(15, Timestamp.from(user.getUpdatedAt()));

            // Lifestyle fields (Phase 0.5b)
            stmt.setString(16, user.getSmoking() != null ? user.getSmoking().name() : null);
            stmt.setString(17, user.getDrinking() != null ? user.getDrinking().name() : null);
            stmt.setString(18, user.getWantsKids() != null ? user.getWantsKids().name() : null);
            stmt.setString(19, user.getLookingFor() != null ? user.getLookingFor().name() : null);
            stmt.setString(20, user.getEducation() != null ? user.getEducation().name() : null);
            if (user.getHeightCm() != null) {
                stmt.setInt(21, user.getHeightCm());
            } else {
                stmt.setNull(21, Types.INTEGER);
            }

            // Dealbreaker fields (Phase 0.5b)
            Dealbreakers db = user.getDealbreakers();
            stmt.setString(22, serializeEnumSet(db.acceptableSmoking()));
            stmt.setString(23, serializeEnumSet(db.acceptableDrinking()));
            stmt.setString(24, serializeEnumSet(db.acceptableKidsStance()));
            stmt.setString(25, serializeEnumSet(db.acceptableLookingFor()));
            stmt.setString(26, serializeEnumSet(db.acceptableEducation()));

            if (db.minHeightCm() != null) {
                stmt.setInt(27, db.minHeightCm());
            } else {
                stmt.setNull(27, Types.INTEGER);
            }
            if (db.maxHeightCm() != null) {
                stmt.setInt(28, db.maxHeightCm());
            } else {
                stmt.setNull(28, Types.INTEGER);
            }
            if (db.maxAgeDifference() != null) {
                stmt.setInt(29, db.maxAgeDifference());
            } else {
                stmt.setNull(29, Types.INTEGER);
            }

            stmt.setString(30, serializeInterests(user.getInterests()));

            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new StorageException("Failed to save user: " + user.getId(), e);
        }
    }

    @Override
    public User get(UUID id) {
        String sql = "SELECT * FROM users WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapUser(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new StorageException("Failed to get user: " + id, e);
        }
    }

    @Override
    public List<User> findActive() {
        String sql = "SELECT * FROM users WHERE state = 'ACTIVE'";
        return findByQuery(sql);
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users";
        return findByQuery(sql);
    }

    private List<User> findByQuery(String sql) {
        List<User> users = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                users.add(mapUser(rs));
            }
            return users;

        } catch (SQLException e) {
            throw new StorageException("Failed to query users", e);
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String name = rs.getString("name");
        String bio = rs.getString("bio");
        Date birthDateSql = rs.getDate("birth_date");
        LocalDate birthDate = birthDateSql != null ? birthDateSql.toLocalDate() : null;
        String genderStr = rs.getString("gender");
        User.Gender gender = genderStr != null ? User.Gender.valueOf(genderStr) : null;
        Set<User.Gender> interestedIn = stringToGenders(rs.getString("interested_in"));
        double lat = rs.getDouble("lat");
        double lon = rs.getDouble("lon");
        int maxDistanceKm = rs.getInt("max_distance_km");
        int minAge = rs.getInt("min_age");
        int maxAge = rs.getInt("max_age");
        List<String> photoUrls = stringToUrls(rs.getString("photo_urls"));
        User.State state = User.State.valueOf(rs.getString("state"));
        var createdAt = rs.getTimestamp("created_at").toInstant();
        var updatedAt = rs.getTimestamp("updated_at").toInstant();

        String interestsStr = rs.getString("interests");
        Set<Interest> interests = parseInterests(interestsStr);

        User user = User.fromDatabase(id, name, bio, birthDate, gender, interestedIn, lat, lon,
                maxDistanceKm, minAge, maxAge, photoUrls, state, createdAt, updatedAt, interests);

        // Map lifestyle fields (Phase 0.5b)
        String smokingStr = rs.getString("smoking");
        if (smokingStr != null) {
            user.setSmoking(Lifestyle.Smoking.valueOf(smokingStr));
        }

        String drinkingStr = rs.getString("drinking");
        if (drinkingStr != null) {
            user.setDrinking(Lifestyle.Drinking.valueOf(drinkingStr));
        }

        String wantsKidsStr = rs.getString("wants_kids");
        if (wantsKidsStr != null) {
            user.setWantsKids(Lifestyle.WantsKids.valueOf(wantsKidsStr));
        }

        String lookingForStr = rs.getString("looking_for");
        if (lookingForStr != null) {
            user.setLookingFor(Lifestyle.LookingFor.valueOf(lookingForStr));
        }

        String educationStr = rs.getString("education");
        if (educationStr != null) {
            user.setEducation(Lifestyle.Education.valueOf(educationStr));
        }

        int heightCm = rs.getInt("height_cm");
        if (!rs.wasNull()) {
            user.setHeightCm(heightCm);
        }

        // Map dealbreakers (Phase 0.5b)
        Dealbreakers dealbreakers = mapDealbreakers(rs);
        if (dealbreakers.hasAnyDealbreaker()) {
            user.setDealbreakers(dealbreakers);
        }

        return user;
    }

    private Dealbreakers mapDealbreakers(ResultSet rs) throws SQLException {
        Set<Lifestyle.Smoking> dbSmoking = parseEnumSet(
                rs.getString("db_smoking"), Lifestyle.Smoking.class);
        Set<Lifestyle.Drinking> dbDrinking = parseEnumSet(
                rs.getString("db_drinking"), Lifestyle.Drinking.class);
        Set<Lifestyle.WantsKids> dbKids = parseEnumSet(
                rs.getString("db_wants_kids"), Lifestyle.WantsKids.class);
        Set<Lifestyle.LookingFor> dbLookingFor = parseEnumSet(
                rs.getString("db_looking_for"), Lifestyle.LookingFor.class);
        Set<Lifestyle.Education> dbEducation = parseEnumSet(
                rs.getString("db_education"), Lifestyle.Education.class);

        Integer dbMinHeight = rs.getInt("db_min_height_cm");
        if (rs.wasNull())
            dbMinHeight = null;

        Integer dbMaxHeight = rs.getInt("db_max_height_cm");
        if (rs.wasNull())
            dbMaxHeight = null;

        Integer dbMaxAgeDiff = rs.getInt("db_max_age_diff");
        if (rs.wasNull())
            dbMaxAgeDiff = null;

        return new Dealbreakers(
                dbSmoking, dbDrinking, dbKids, dbLookingFor, dbEducation,
                dbMinHeight, dbMaxHeight, dbMaxAgeDiff);
    }

    private <E extends Enum<E>> Set<E> parseEnumSet(String csv, Class<E> enumClass) {
        if (csv == null || csv.isBlank()) {
            return new HashSet<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> Enum.valueOf(enumClass, s))
                .collect(Collectors.toSet());
    }

    private String serializeEnumSet(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    private String gendersToString(Set<User.Gender> genders) {
        if (genders == null || genders.isEmpty()) {
            return null;
        }
        return genders.stream()
                .map(User.Gender::name)
                .collect(Collectors.joining(","));
    }

    private Set<User.Gender> stringToGenders(String str) {
        if (str == null || str.isBlank()) {
            return EnumSet.noneOf(User.Gender.class);
        }
        return Arrays.stream(str.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(User.Gender::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(User.Gender.class)));
    }

    private String urlsToString(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        // Use pipe as delimiter to avoid corrupting URLs that contain commas
        return String.join("|", urls);
    }

    private List<String> stringToUrls(String str) {
        if (str == null || str.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(str.split("\\|")));
    }

    private Set<Interest> parseInterests(String csv) {
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
                    // Skip invalid interests
                }
            }
        }
        return result;
    }

    private String serializeInterests(Set<Interest> interests) {
        if (interests == null || interests.isEmpty()) {
            return null;
        }
        return interests.stream()
                .map(Interest::name)
                .collect(Collectors.joining(","));
    }
}
