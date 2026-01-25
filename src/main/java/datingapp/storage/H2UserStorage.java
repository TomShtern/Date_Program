package datingapp.storage;

import datingapp.core.Dealbreakers;
import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.Preferences.PacePreferences;
import datingapp.core.User;
import datingapp.core.UserStorage;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
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
public class H2UserStorage extends AbstractH2Storage implements UserStorage {

    private static final String VARCHAR_20 = "VARCHAR(20)";
    private static final String USER_COLUMNS = """
            id, name, bio, birth_date, gender, interested_in, lat, lon,
            max_distance_km, min_age, max_age, photo_urls, state, created_at,
            updated_at, smoking, drinking, wants_kids, looking_for, education,
            height_cm, db_smoking, db_drinking, db_wants_kids, db_looking_for,
            db_education, db_min_height_cm, db_max_height_cm, db_max_age_diff,
            interests, email, phone, is_verified, verification_method,
            verification_code, verification_sent_at, verified_at,
            pace_messaging_frequency, pace_time_to_first_date,
            pace_communication_style, pace_depth_preference
            """;
    private static final String SELECT_USERS = "SELECT " + USER_COLUMNS + " FROM users";

    public H2UserStorage(DatabaseManager dbManager) {
        super(dbManager);
        ensureSchema();
    }

    @Override
    protected void ensureSchema() {
        addColumnIfNotExists("users", "pace_messaging_frequency", VARCHAR_20);
        addColumnIfNotExists("users", "pace_time_to_first_date", VARCHAR_20);
        addColumnIfNotExists("users", "pace_communication_style", VARCHAR_20);
        addColumnIfNotExists("users", "pace_depth_preference", VARCHAR_20);
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
            bindCoreFields(stmt, user);
            bindLifestyleFields(stmt, user);
            bindDealbreakerFields(stmt, user.getDealbreakers());
            stmt.setString(30, serializeInterests(user.getInterests()));
            bindVerificationFields(stmt, user);
            bindPaceFields(stmt, user.getPacePreferences());

            stmt.executeUpdate();

        } catch (SQLException ex) {
            throw new StorageException("Failed to save user: " + user.getId(), ex);
        }
    }

    private void bindCoreFields(PreparedStatement stmt, User user) throws SQLException {
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
    }

    private void bindLifestyleFields(PreparedStatement stmt, User user) throws SQLException {
        stmt.setString(16, user.getSmoking() != null ? user.getSmoking().name() : null);
        stmt.setString(17, user.getDrinking() != null ? user.getDrinking().name() : null);
        stmt.setString(18, user.getWantsKids() != null ? user.getWantsKids().name() : null);
        stmt.setString(19, user.getLookingFor() != null ? user.getLookingFor().name() : null);
        stmt.setString(20, user.getEducation() != null ? user.getEducation().name() : null);
        setNullableInt(stmt, 21, user.getHeightCm());
    }

    private void bindDealbreakerFields(PreparedStatement stmt, Dealbreakers dealbreakers) throws SQLException {
        stmt.setString(22, serializeEnumSet(dealbreakers.acceptableSmoking()));
        stmt.setString(23, serializeEnumSet(dealbreakers.acceptableDrinking()));
        stmt.setString(24, serializeEnumSet(dealbreakers.acceptableKidsStance()));
        stmt.setString(25, serializeEnumSet(dealbreakers.acceptableLookingFor()));
        stmt.setString(26, serializeEnumSet(dealbreakers.acceptableEducation()));
        setNullableInt(stmt, 27, dealbreakers.minHeightCm());
        setNullableInt(stmt, 28, dealbreakers.maxHeightCm());
        setNullableInt(stmt, 29, dealbreakers.maxAgeDifference());
    }

    private void bindVerificationFields(PreparedStatement stmt, User user) throws SQLException {
        stmt.setString(31, user.getEmail());
        stmt.setString(32, user.getPhone());
        stmt.setObject(33, user.isVerified());
        stmt.setString(
                34,
                user.getVerificationMethod() != null
                        ? user.getVerificationMethod().name()
                        : null);
        stmt.setString(35, user.getVerificationCode());
        setNullableTimestamp(stmt, 36, user.getVerificationSentAt());
        setNullableTimestamp(stmt, 37, user.getVerifiedAt());
    }

    private void bindPaceFields(PreparedStatement stmt, PacePreferences pace) throws SQLException {
        if (pace != null) {
            stmt.setString(
                    38,
                    pace.messagingFrequency() != null
                            ? pace.messagingFrequency().name()
                            : null);
            stmt.setString(
                    39, pace.timeToFirstDate() != null ? pace.timeToFirstDate().name() : null);
            stmt.setString(
                    40,
                    pace.communicationStyle() != null
                            ? pace.communicationStyle().name()
                            : null);
            stmt.setString(
                    41, pace.depthPreference() != null ? pace.depthPreference().name() : null);
        } else {
            stmt.setNull(38, Types.VARCHAR);
            stmt.setNull(39, Types.VARCHAR);
            stmt.setNull(40, Types.VARCHAR);
            stmt.setNull(41, Types.VARCHAR);
        }
    }

    @Override
    public User get(UUID id) {
        String sql = SELECT_USERS + " WHERE id = ?";

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
        String sql = SELECT_USERS + " WHERE state = 'ACTIVE'";
        return findByQuery(sql);
    }

    @Override
    public List<User> findAll() {
        String sql = SELECT_USERS;
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
        User.DatabaseRecord data = User.DatabaseRecord.builder()
                .id(rs.getObject("id", UUID.class))
                .name(rs.getString("name"))
                .bio(rs.getString("bio"))
                .birthDate(readLocalDate(rs, "birth_date"))
                .gender(readEnum(rs, "gender", User.Gender.class))
                .interestedIn(stringToGenders(rs.getString("interested_in")))
                .lat(rs.getDouble("lat"))
                .lon(rs.getDouble("lon"))
                .maxDistanceKm(rs.getInt("max_distance_km"))
                .minAge(rs.getInt("min_age"))
                .maxAge(rs.getInt("max_age"))
                .photoUrls(stringToUrls(rs.getString("photo_urls")))
                .state(User.State.valueOf(rs.getString("state")))
                .createdAt(readInstant(rs, "created_at"))
                .updatedAt(readInstant(rs, "updated_at"))
                .interests(parseInterests(rs.getString("interests")))
                .smoking(readEnum(rs, "smoking", Lifestyle.Smoking.class))
                .drinking(readEnum(rs, "drinking", Lifestyle.Drinking.class))
                .wantsKids(readEnum(rs, "wants_kids", Lifestyle.WantsKids.class))
                .lookingFor(readEnum(rs, "looking_for", Lifestyle.LookingFor.class))
                .education(readEnum(rs, "education", Lifestyle.Education.class))
                .heightCm(readInteger(rs, "height_cm"))
                .email(rs.getString("email"))
                .phone(rs.getString("phone"))
                .isVerified((Boolean) rs.getObject("is_verified"))
                .verificationMethod(readEnum(rs, "verification_method", User.VerificationMethod.class))
                .verificationCode(rs.getString("verification_code"))
                .verificationSentAt(readInstant(rs, "verification_sent_at"))
                .verifiedAt(readInstant(rs, "verified_at"))
                .pacePreferences(readPacePreferences(rs))
                .build();

        User user = User.fromDatabase(data);

        Dealbreakers dealbreakers = mapDealbreakers(rs);
        if (dealbreakers.hasAnyDealbreaker()) {
            user.setDealbreakers(dealbreakers);
        }

        return user;
    }

    private LocalDate readLocalDate(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date != null ? date.toLocalDate() : null;
    }

    private Instant readInstant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toInstant() : null;
    }

    private Integer readInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        if (rs.wasNull()) {
            return null;
        }
        return value;
    }

    private <E extends Enum<E>> E readEnum(ResultSet rs, String column, Class<E> enumType) throws SQLException {
        String value = rs.getString(column);
        return value != null ? Enum.valueOf(enumType, value) : null;
    }

    private PacePreferences readPacePreferences(ResultSet rs) throws SQLException {
        PacePreferences.MessagingFrequency messagingFrequency =
                readEnum(rs, "pace_messaging_frequency", PacePreferences.MessagingFrequency.class);
        PacePreferences.TimeToFirstDate timeToFirstDate =
                readEnum(rs, "pace_time_to_first_date", PacePreferences.TimeToFirstDate.class);
        PacePreferences.CommunicationStyle communicationStyle =
                readEnum(rs, "pace_communication_style", PacePreferences.CommunicationStyle.class);
        PacePreferences.DepthPreference depthPreference =
                readEnum(rs, "pace_depth_preference", PacePreferences.DepthPreference.class);

        if (messagingFrequency == null
                && timeToFirstDate == null
                && communicationStyle == null
                && depthPreference == null) {
            return null;
        }

        return new PacePreferences(messagingFrequency, timeToFirstDate, communicationStyle, depthPreference);
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
                } catch (IllegalArgumentException _) {
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
