package datingapp.storage;

import datingapp.core.Dealbreakers;
import datingapp.core.Interest;
import datingapp.core.Lifestyle;
import datingapp.core.PacePreferences;
import datingapp.core.PacePreferences.CommunicationStyle;
import datingapp.core.PacePreferences.DepthPreference;
import datingapp.core.PacePreferences.MessagingFrequency;
import datingapp.core.PacePreferences.TimeToFirstDate;
import datingapp.core.User;
import datingapp.core.UserStorage;
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

/** H2 implementation of UserStorage. */
public class H2UserStorage implements UserStorage {

    private final DatabaseManager dbManager;

    public H2UserStorage(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        ensureSchema();
    }

    private void ensureSchema() {
        addColumnIfNotExists("pace_messaging_frequency", "VARCHAR(20)");
        addColumnIfNotExists("pace_time_to_first_date", "VARCHAR(20)");
        addColumnIfNotExists("pace_communication_style", "VARCHAR(20)");
        addColumnIfNotExists("pace_depth_preference", "VARCHAR(20)");
    }

    private void addColumnIfNotExists(String columnName, String columnDef) {
        String checkSql = """
        SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = 'USERS' AND COLUMN_NAME = ?
        """;
        String addSql = "ALTER TABLE users ADD COLUMN " + columnName + " " + columnDef;

        try (Connection conn = dbManager.getConnection();
                PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            checkStmt.setString(1, columnName.toUpperCase());
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement addStmt = conn.prepareStatement(addSql)) {
                    addStmt.executeUpdate();
                }
            }

        } catch (SQLException e) {
            // ignore
        }
    }

    @Override
    public void save(User user) {
        String sql = """
        MERGE INTO users (id, name, bio, birth_date, gender, interested_in, lat, lon,
                          max_distance_km, min_age, max_age, photo_urls, state, created_at,
                          updated_at, smoking, drinking, wants_kids, looking_for, education,
                          height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
                          db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
                          interests, email, phone, is_verified, verification_method,
                          verification_code, verification_sent_at, verified_at,
                          pace_messaging_frequency, pace_time_to_first_date,
                          pace_communication_style, pace_depth_preference)
        KEY (id)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            stmt.setString(
                    19, user.getLookingFor() != null ? user.getLookingFor().name() : null);
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

            // Verification fields (Phase 2)
            stmt.setString(31, user.getEmail());
            stmt.setString(32, user.getPhone());
            stmt.setObject(33, user.isVerified());
            stmt.setString(
                    34,
                    user.getVerificationMethod() != null
                            ? user.getVerificationMethod().name()
                            : null);
            stmt.setString(35, user.getVerificationCode());
            stmt.setTimestamp(
                    36, user.getVerificationSentAt() != null ? Timestamp.from(user.getVerificationSentAt()) : null);
            stmt.setTimestamp(37, user.getVerifiedAt() != null ? Timestamp.from(user.getVerifiedAt()) : null);

            PacePreferences pace = user.getPacePreferences();
            if (pace != null) {
                stmt.setString(
                        38,
                        pace.messagingFrequency() != null
                                ? pace.messagingFrequency().name()
                                : null);
                stmt.setString(
                        39,
                        pace.timeToFirstDate() != null ? pace.timeToFirstDate().name() : null);
                stmt.setString(
                        40,
                        pace.communicationStyle() != null
                                ? pace.communicationStyle().name()
                                : null);
                stmt.setString(
                        41,
                        pace.depthPreference() != null ? pace.depthPreference().name() : null);
            } else {
                stmt.setNull(38, Types.VARCHAR);
                stmt.setNull(39, Types.VARCHAR);
                stmt.setNull(40, Types.VARCHAR);
                stmt.setNull(41, Types.VARCHAR);
            }

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

        String email = rs.getString("email");
        String phone = rs.getString("phone");
        Boolean isVerified = (Boolean) rs.getObject("is_verified");

        String verificationMethodStr = rs.getString("verification_method");
        User.VerificationMethod verificationMethod =
                verificationMethodStr != null ? User.VerificationMethod.valueOf(verificationMethodStr) : null;

        String verificationCode = rs.getString("verification_code");

        Timestamp verificationSentAtTs = rs.getTimestamp("verification_sent_at");
        var verificationSentAt = verificationSentAtTs != null ? verificationSentAtTs.toInstant() : null;

        Timestamp verifiedAtTs = rs.getTimestamp("verified_at");
        var verifiedAt = verifiedAtTs != null ? verifiedAtTs.toInstant() : null;

        String paceFreqStr = rs.getString("pace_messaging_frequency");
        String paceTimeStr = rs.getString("pace_time_to_first_date");
        String paceCommStr = rs.getString("pace_communication_style");
        String paceDepthStr = rs.getString("pace_depth_preference");

        PacePreferences pace = null;
        if (paceFreqStr != null || paceTimeStr != null || paceCommStr != null || paceDepthStr != null) {
            pace = new PacePreferences(
                    paceFreqStr != null ? MessagingFrequency.valueOf(paceFreqStr) : null,
                    paceTimeStr != null ? TimeToFirstDate.valueOf(paceTimeStr) : null,
                    paceCommStr != null ? CommunicationStyle.valueOf(paceCommStr) : null,
                    paceDepthStr != null ? DepthPreference.valueOf(paceDepthStr) : null);
        }

        String smokingStr = rs.getString("smoking");
        Lifestyle.Smoking smoking = smokingStr != null ? Lifestyle.Smoking.valueOf(smokingStr) : null;

        String drinkingStr = rs.getString("drinking");
        Lifestyle.Drinking drinking = drinkingStr != null ? Lifestyle.Drinking.valueOf(drinkingStr) : null;

        String wantsKidsStr = rs.getString("wants_kids");
        Lifestyle.WantsKids wantsKids = wantsKidsStr != null ? Lifestyle.WantsKids.valueOf(wantsKidsStr) : null;

        String lookingForStr = rs.getString("looking_for");
        Lifestyle.LookingFor lookingFor = lookingForStr != null ? Lifestyle.LookingFor.valueOf(lookingForStr) : null;

        User user = User.fromDatabase(
                id,
                name,
                bio,
                birthDate,
                gender,
                interestedIn,
                lat,
                lon,
                maxDistanceKm,
                minAge,
                maxAge,
                photoUrls,
                state,
                createdAt,
                updatedAt,
                interests,
                smoking,
                drinking,
                wantsKids,
                lookingFor,
                email,
                phone,
                isVerified,
                verificationMethod,
                verificationCode,
                verificationSentAt,
                verifiedAt,
                pace);

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
        Integer dbMinHeight = rs.getInt("db_min_height_cm");
        if (rs.wasNull()) {
            dbMinHeight = null;
        }

        Integer dbMaxHeight = rs.getInt("db_max_height_cm");
        if (rs.wasNull()) {
            dbMaxHeight = null;
        }

        Integer dbMaxAgeDiff = rs.getInt("db_max_age_diff");
        if (rs.wasNull()) {
            dbMaxAgeDiff = null;
        }

        Set<Lifestyle.Smoking> dbSmoking = parseEnumSet(rs.getString("db_smoking"), Lifestyle.Smoking.class);
        Set<Lifestyle.Drinking> dbDrinking = parseEnumSet(rs.getString("db_drinking"), Lifestyle.Drinking.class);
        Set<Lifestyle.WantsKids> dbKids = parseEnumSet(rs.getString("db_wants_kids"), Lifestyle.WantsKids.class);
        Set<Lifestyle.LookingFor> dbLookingFor =
                parseEnumSet(rs.getString("db_looking_for"), Lifestyle.LookingFor.class);
        Set<Lifestyle.Education> dbEducation = parseEnumSet(rs.getString("db_education"), Lifestyle.Education.class);

        return new Dealbreakers(
                dbSmoking, dbDrinking, dbKids, dbLookingFor, dbEducation, dbMinHeight, dbMaxHeight, dbMaxAgeDiff);
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
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    private String gendersToString(Set<User.Gender> genders) {
        if (genders == null || genders.isEmpty()) {
            return null;
        }
        return genders.stream().map(User.Gender::name).collect(Collectors.joining(","));
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
        return interests.stream().map(Interest::name).collect(Collectors.joining(","));
    }
}
