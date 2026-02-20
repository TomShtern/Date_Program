package datingapp.core.model;

import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.EnumSetUtil;
import datingapp.core.profile.MatchPreferences;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a user in the dating app. Mutable entity - state can change over
 * time.
 */
public class User {

    // ── Nested domain types ────────────────────────────────────────────

    /** Gender options available for users. */
    public static enum Gender {
        MALE,
        FEMALE,
        OTHER
    }

    /**
     * Lifecycle state of a user account.
     * Valid transitions: INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
     */
    public static enum UserState {
        INCOMPLETE,
        ACTIVE,
        PAUSED,
        BANNED
    }

    /**
     * Verification method used to verify a profile.
     * NOTE: Currently simulated - email/phone not sent externally.
     */
    public static enum VerificationMethod {
        EMAIL,
        PHONE
    }

    /**
     * A private note that a user can attach to another user's profile. Notes are
     * only visible to the
     * author - the subject never sees them.
     *
     * <p>
     * Use cases:
     *
     * <ul>
     * <li>Remember where you met someone ("Coffee shop downtown")
     * <li>Note conversation topics ("Loves hiking, has a dog named Max")
     * <li>Track date plans ("Dinner Thursday @ Olive Garden")
     * </ul>
     */
    // ── End nested domain types ────────────────────────────────────────

    private static final AppConfig CONFIG = AppConfig.defaults();

    private final UUID id;
    private String name;
    private String bio;
    private LocalDate birthDate;
    private Gender gender;
    private Set<Gender> interestedIn;
    private double lat;
    private double lon;
    private boolean hasLocationSet;
    private int maxDistanceKm;
    private int minAge;
    private int maxAge;
    private List<String> photoUrls;
    private UserState state;
    private final Instant createdAt;
    private Instant updatedAt;

    // Lifestyle fields (Phase 0.5b)
    private Lifestyle.Smoking smoking;
    private Lifestyle.Drinking drinking;
    private Lifestyle.WantsKids wantsKids;
    private Lifestyle.LookingFor lookingFor;
    private Lifestyle.Education education;
    private Integer heightCm;

    // Dealbreakers (Phase 0.5b)
    private MatchPreferences.Dealbreakers dealbreakers;

    // Interests (Phase 1 feature)
    private Set<Interest> interests;

    // Verification fields
    private String email;
    private String phone;
    private boolean isVerified;
    private VerificationMethod verificationMethod;
    private String verificationCode;
    private Instant verificationSentAt;
    private Instant verifiedAt;

    private PacePreferences pacePreferences;

    // Soft-delete support
    private Instant deletedAt;

    /**
     * Creates a new incomplete user with just an ID and name. Timestamps are set to
     * current time.
     */
    public User(UUID id, String name) {
        this(id, name, AppClock.now());
    }

    /**
     * Private constructor for full initialization. Used by public constructor and
     * fromDatabase()
     * factory method.
     */
    private User(UUID id, String name, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.createdAt = createdAt;
        this.updatedAt = createdAt;

        // Initialize mutable fields with defaults
        this.interestedIn = EnumSet.noneOf(Gender.class);
        this.photoUrls = new ArrayList<>();
        this.maxDistanceKm = 50;
        this.minAge = 18;
        this.maxAge = 99;
        this.state = UserState.INCOMPLETE;
        this.interests = EnumSet.noneOf(Interest.class);
    }

    /**
     * Builder for constructing User instances from storage. Use this when loading
     * users from the
     * database to bypass normal validation and set all fields directly.
     */
    public static final class StorageBuilder {
        private final User user;

        private StorageBuilder(UUID id, String name, Instant createdAt) {
            this.user = new User(id, name, createdAt);
        }

        /** Starts building a User from storage with required fields. */
        public static StorageBuilder create(UUID id, String name, Instant createdAt) {
            return new StorageBuilder(id, name, createdAt);
        }

        public StorageBuilder bio(String bio) {
            user.bio = bio;
            return this;
        }

        public StorageBuilder birthDate(LocalDate birthDate) {
            user.birthDate = birthDate;
            return this;
        }

        public StorageBuilder gender(Gender gender) {
            user.gender = gender;
            return this;
        }

        public StorageBuilder interestedIn(Set<Gender> interestedIn) {
            user.interestedIn = EnumSetUtil.safeCopy(interestedIn, Gender.class);
            return this;
        }

        public StorageBuilder location(double lat, double lon) {
            user.lat = lat;
            user.lon = lon;
            return this;
        }

        public StorageBuilder hasLocationSet(boolean hasLocationSet) {
            user.hasLocationSet = hasLocationSet;
            return this;
        }

        public StorageBuilder maxDistanceKm(int maxDistanceKm) {
            user.maxDistanceKm = maxDistanceKm;
            return this;
        }

        public StorageBuilder ageRange(int minAge, int maxAge) {
            user.minAge = minAge;
            user.maxAge = maxAge;
            return this;
        }

        public StorageBuilder photoUrls(List<String> photoUrls) {
            user.photoUrls = photoUrls != null ? new ArrayList<>(photoUrls) : new ArrayList<>();
            return this;
        }

        public StorageBuilder state(UserState state) {
            user.state = state;
            return this;
        }

        public StorageBuilder updatedAt(Instant updatedAt) {
            user.updatedAt = updatedAt;
            return this;
        }

        public StorageBuilder interests(Set<Interest> interests) {
            user.interests = copyAndValidateInterests(interests);
            return this;
        }

        public StorageBuilder smoking(Lifestyle.Smoking smoking) {
            user.smoking = smoking;
            return this;
        }

        public StorageBuilder drinking(Lifestyle.Drinking drinking) {
            user.drinking = drinking;
            return this;
        }

        public StorageBuilder wantsKids(Lifestyle.WantsKids wantsKids) {
            user.wantsKids = wantsKids;
            return this;
        }

        public StorageBuilder lookingFor(Lifestyle.LookingFor lookingFor) {
            user.lookingFor = lookingFor;
            return this;
        }

        public StorageBuilder education(Lifestyle.Education education) {
            user.education = education;
            return this;
        }

        public StorageBuilder heightCm(Integer heightCm) {
            user.heightCm = heightCm;
            return this;
        }

        public StorageBuilder email(String email) {
            user.email = email;
            return this;
        }

        public StorageBuilder phone(String phone) {
            user.phone = phone;
            return this;
        }

        public StorageBuilder verified(Boolean isVerified) {
            user.isVerified = isVerified != null && isVerified;
            return this;
        }

        public StorageBuilder verificationMethod(VerificationMethod method) {
            user.verificationMethod = method;
            return this;
        }

        public StorageBuilder verificationCode(String code) {
            user.verificationCode = code;
            return this;
        }

        public StorageBuilder verificationSentAt(Instant sentAt) {
            user.verificationSentAt = sentAt;
            return this;
        }

        public StorageBuilder verifiedAt(Instant verifiedAt) {
            user.verifiedAt = verifiedAt;
            return this;
        }

        public StorageBuilder pacePreferences(PacePreferences pacePreferences) {
            user.pacePreferences = pacePreferences;
            return this;
        }

        public StorageBuilder deletedAt(Instant deletedAt) {
            user.deletedAt = deletedAt;
            return this;
        }

        /** Builds and returns the User instance. */
        public User build() {
            return user;
        }
    }

    // Getters

    public synchronized PacePreferences getPacePreferences() {
        return pacePreferences;
    }

    public synchronized UUID getId() {
        return id;
    }

    public synchronized String getName() {
        return name;
    }

    public synchronized String getBio() {
        return bio;
    }

    public synchronized LocalDate getBirthDate() {
        return birthDate;
    }

    public synchronized Gender getGender() {
        return gender;
    }

    public synchronized Set<Gender> getInterestedIn() {
        return EnumSetUtil.safeCopy(interestedIn, Gender.class);
    }

    public synchronized double getLat() {
        return lat;
    }

    public synchronized double getLon() {
        return lon;
    }

    public synchronized boolean hasLocationSet() {
        return hasLocationSet;
    }

    /**
     * Returns true when the user has a valid location value that was explicitly
     * set.
     * Coordinates like (0.0, 0.0) are considered valid when location was set.
     */
    public synchronized boolean hasLocation() {
        return hasLocationSet && Double.isFinite(lat) && Double.isFinite(lon);
    }

    public synchronized int getMaxDistanceKm() {
        return maxDistanceKm;
    }

    public synchronized int getMinAge() {
        return minAge;
    }

    public synchronized int getMaxAge() {
        return maxAge;
    }

    public synchronized List<String> getPhotoUrls() {
        return Collections.unmodifiableList(photoUrls);
    }

    public synchronized UserState getState() {
        return state;
    }

    public synchronized Instant getCreatedAt() {
        return createdAt;
    }

    public synchronized Instant getUpdatedAt() {
        return updatedAt;
    }

    // Lifestyle getters (Phase 0.5b)

    public synchronized Lifestyle.Smoking getSmoking() {
        return smoking;
    }

    public synchronized Lifestyle.Drinking getDrinking() {
        return drinking;
    }

    public synchronized Lifestyle.WantsKids getWantsKids() {
        return wantsKids;
    }

    public synchronized Lifestyle.LookingFor getLookingFor() {
        return lookingFor;
    }

    public synchronized Lifestyle.Education getEducation() {
        return education;
    }

    public synchronized Integer getHeightCm() {
        return heightCm;
    }

    /** Returns the user's dealbreakers, or Dealbreakers.none() if not set. */
    public synchronized MatchPreferences.Dealbreakers getDealbreakers() {
        return dealbreakers != null ? dealbreakers : MatchPreferences.Dealbreakers.none();
    }

    /**
     * Returns the user's interests as a defensive copy.
     *
     * @return set of interests (never null, may be empty)
     */
    public synchronized Set<Interest> getInterests() {
        return EnumSetUtil.safeCopy(interests, Interest.class);
    }

    /**
     * Calculates the user's age based on their birth date using the system default
     * timezone.
     * Uses system default timezone to match local date perception and avoid
     * off-by-one issues
     * when the birthday occurs at midnight across timezones.
     */
    public synchronized int getAge() {
        return getAge(AppConfig.defaults().safety().userTimeZone());
    }

    /**
     * Calculates the user's age based on their birth date using the specified
     * timezone.
     *
     * @param timezone the timezone to use for age calculation
     * @return the user's age in years, or 0 if birth date is not set
     */
    public synchronized int getAge(java.time.ZoneId timezone) {
        if (birthDate == null) {
            return 0;
        }
        return Period.between(birthDate, AppClock.today(timezone)).getYears();
    }

    // Verification getters (Phase 2 feature)
    public synchronized boolean isVerified() {
        return isVerified;
    }

    public synchronized VerificationMethod getVerificationMethod() {
        return verificationMethod;
    }

    public synchronized String getVerificationCode() {
        return verificationCode;
    }

    public synchronized Instant getVerificationSentAt() {
        return verificationSentAt;
    }

    public synchronized Instant getVerifiedAt() {
        return verifiedAt;
    }

    public synchronized String getEmail() {
        return email;
    }

    public synchronized String getPhone() {
        return phone;
    }

    // Pace preference setters
    public synchronized void setPacePreferences(PacePreferences pacePreferences) {
        this.pacePreferences = pacePreferences;
        touch();
    }

    // Setters (with updatedAt)

    public synchronized void setName(String name) {
        this.name = Objects.requireNonNull(name);
        touch();
    }

    public synchronized void setBio(String bio) {
        this.bio = bio;
        touch();
    }

    public synchronized void setEmail(String email) {
        this.email = email;
        touch();
    }

    public synchronized void setPhone(String phone) {
        this.phone = phone;
        touch();
    }

    public synchronized void startVerification(VerificationMethod method, String verificationCode) {
        this.verificationMethod = Objects.requireNonNull(method, "method cannot be null");
        this.verificationCode = Objects.requireNonNull(verificationCode, "verificationCode cannot be null");
        this.verificationSentAt = AppClock.now();
        touch();
    }

    public synchronized void markVerified() {
        this.isVerified = true;
        this.verifiedAt = AppClock.now();
        this.verificationCode = null;
        this.verificationSentAt = null;
        touch();
    }

    public synchronized void clearVerificationAttempt() {
        this.verificationCode = null;
        this.verificationSentAt = null;
        touch();
    }

    public synchronized void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        touch();
    }

    public synchronized void setGender(Gender gender) {
        this.gender = gender;
        touch();
    }

    public synchronized void setInterestedIn(Set<Gender> interestedIn) {
        this.interestedIn = EnumSetUtil.safeCopy(interestedIn, Gender.class);
        touch();
    }

    public synchronized void setLocation(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
        this.hasLocationSet = true;
        touch();
    }

    public synchronized void setMaxDistanceKm(int maxDistanceKm) {
        if (maxDistanceKm <= 0) {
            throw new IllegalArgumentException("maxDistanceKm must be positive");
        }
        this.maxDistanceKm = maxDistanceKm;
        touch();
    }

    public synchronized void setAgeRange(int minAge, int maxAge) {
        if (minAge < CONFIG.minAge()) {
            throw new IllegalArgumentException("minAge must be at least " + CONFIG.minAge());
        }
        if (maxAge > CONFIG.maxAge()) {
            throw new IllegalArgumentException("maxAge cannot exceed " + CONFIG.maxAge());
        }
        if (maxAge < minAge) {
            throw new IllegalArgumentException("maxAge cannot be less than minAge");
        }
        this.minAge = minAge;
        this.maxAge = maxAge;
        touch();
    }

    /** Sets photo URLs with null-safe copy. */
    public synchronized void setPhotoUrls(List<String> photoUrls) {
        this.photoUrls = photoUrls != null ? new ArrayList<>(photoUrls) : new ArrayList<>();
        touch();
    }

    /** Adds a photo URL. */
    public synchronized void addPhotoUrl(String url) {
        photoUrls.add(url);
        touch();
    }

    // Lifestyle setters (Phase 0.5b)

    public synchronized void setSmoking(Lifestyle.Smoking smoking) {
        this.smoking = smoking;
        touch();
    }

    public synchronized void setDrinking(Lifestyle.Drinking drinking) {
        this.drinking = drinking;
        touch();
    }

    public synchronized void setWantsKids(Lifestyle.WantsKids wantsKids) {
        this.wantsKids = wantsKids;
        touch();
    }

    public synchronized void setLookingFor(Lifestyle.LookingFor lookingFor) {
        this.lookingFor = lookingFor;
        touch();
    }

    public synchronized void setEducation(Lifestyle.Education education) {
        this.education = education;
        touch();
    }

    public synchronized void setHeightCm(Integer heightCm) {
        if (heightCm != null && heightCm <= 0) {
            throw new IllegalArgumentException("Height must be positive");
        }
        this.heightCm = heightCm;
        touch();
    }

    public synchronized void setDealbreakers(MatchPreferences.Dealbreakers dealbreakers) {
        this.dealbreakers = dealbreakers;
        touch();
    }

    /**
     * Sets the user's interests.
     *
     * @param interests set of interests (null treated as empty)
     */
    public synchronized void setInterests(Set<Interest> interests) {
        this.interests = copyAndValidateInterests(interests);
        touch();
    }

    /**
     * Adds a single interest to the user's profile.
     *
     * @param interest the interest to add
     */
    public synchronized void addInterest(Interest interest) {
        if (interest == null) {
            return;
        }
        if (interests.contains(interest)) {
            return;
        }
        if (interests.size() >= Interest.MAX_PER_USER) {
            throw new IllegalStateException("Cannot add more than " + Interest.MAX_PER_USER + " interests");
        }
        interests.add(interest);
        touch();
    }

    private static Set<Interest> copyAndValidateInterests(Set<Interest> interests) {
        Set<Interest> safe = EnumSetUtil.safeCopy(interests, Interest.class);
        if (safe.size() > Interest.MAX_PER_USER) {
            throw new IllegalArgumentException("Cannot set more than " + Interest.MAX_PER_USER + " interests");
        }
        return safe;
    }

    /**
     * Removes an interest from the user's profile.
     *
     * @param interest the interest to remove
     */
    public synchronized void removeInterest(Interest interest) {
        if (interest != null && interests.remove(interest)) {
            touch();
        }
    }

    // MatchState transitions

    /**
     * Activates the user. Only valid from INCOMPLETE or PAUSED state. Profile must
     * be complete.
     */
    public synchronized void activate() {
        if (state == UserState.BANNED) {
            throw new IllegalStateException("Cannot activate a banned user");
        }
        if (!isComplete()) {
            throw new IllegalStateException("Cannot activate an incomplete profile");
        }
        this.state = UserState.ACTIVE;
        touch();
    }

    /** Pauses the user. Only valid from ACTIVE state. */
    public synchronized void pause() {
        if (state != UserState.ACTIVE) {
            throw new IllegalStateException("Can only pause an active user");
        }
        this.state = UserState.PAUSED;
        touch();
    }

    /** Bans the user. One-way transition. */
    public synchronized void ban() {
        if (state == UserState.BANNED) {
            return; // Already banned
        }
        this.state = UserState.BANNED;
        touch();
    }

    /**
     * Checks if the user profile is complete. A complete profile has all required
     * fields filled.
     */
    public synchronized boolean isComplete() {
        return name != null
                && !name.isBlank()
                && bio != null
                && !bio.isBlank()
                && birthDate != null
                && gender != null
                && interestedIn != null
                && !interestedIn.isEmpty()
                && maxDistanceKm > 0
                && minAge > 0
                && maxAge >= minAge
                && photoUrls != null
                && !photoUrls.isEmpty()
                && hasCompletePace();
    }

    /** Checks if the user has completed their pace MatchPreferences. */
    public synchronized boolean hasCompletePace() {
        return pacePreferences != null && pacePreferences.isComplete();
    }

    private synchronized void touch() {
        this.updatedAt = AppClock.now();
    }

    @Override
    public synchronized boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public synchronized int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public synchronized String toString() {
        return "User{id=" + id + ", name='" + name + "', state=" + state + "}";
    }

    // ================================
    // Soft-delete support
    // ================================

    /** Returns the deletion timestamp, or {@code null} if not deleted. */
    public synchronized Instant getDeletedAt() {
        return deletedAt;
    }

    /** Marks this entity as soft-deleted at the given instant. */
    public synchronized void markDeleted(Instant deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt cannot be null");
    }

    /** Returns {@code true} if this user has been soft-deleted. */
    public synchronized boolean isDeleted() {
        return deletedAt != null;
    }
}
