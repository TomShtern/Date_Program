package datingapp.core.model;

import datingapp.core.AppClock;
import datingapp.core.EnumSetUtil;
import datingapp.core.profile.MatchPreferences;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.profile.ValidationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a user in the dating app. Mutable entity - state can change over
 * time.
 */
public class User {

    public static final int MAX_PHOTOS = 6;
    private static final String PLACEHOLDER_PHOTO_URL = "placeholder://default-avatar";
    private static final Set<Gender> MATCHABLE_GENDERS =
            Collections.unmodifiableSet(EnumSet.of(Gender.MALE, Gender.FEMALE, Gender.OTHER));

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
            user.photoUrls = normalizePhotoUrls(photoUrls, true);
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

    public PacePreferences getPacePreferences() {
        return pacePreferences;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBio() {
        return bio;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public Set<Gender> getInterestedIn() {
        return EnumSetUtil.safeCopy(interestedIn, Gender.class);
    }

    /**
     * Returns the currently matchable genders used by mutual-preference checks.
     */
    public static Set<Gender> matchableGenders() {
        return MATCHABLE_GENDERS;
    }

    /**
     * Returns {@code true} when this user is interested in all genders — i.e.,
     * their {@code interestedIn} set contains every value in {@link Gender}.
     *
     * <p>This is the application convention for "open to everyone / any gender".
     * Rather than adding a separate {@code ANY} enum value (which would require
     * schema and codec changes), we store all three genders in the normalized
     * {@code user_interested_in} table and use this helper to detect the "all
     * selected" state. The matching engine short-circuits for this user — they
     * are considered compatible with a candidate of any gender.
     */
    public boolean isInterestedInEveryone() {
        return interestedIn != null && interestedIn.containsAll(MATCHABLE_GENDERS);
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public boolean hasLocationSet() {
        return hasLocationSet;
    }

    /**
     * Returns true when the user has a valid location value that was explicitly
     * set.
     * Coordinates like (0.0, 0.0) are considered valid when location was set.
     */
    public boolean hasLocation() {
        return hasLocationSet && Double.isFinite(lat) && Double.isFinite(lon);
    }

    public int getMaxDistanceKm() {
        return maxDistanceKm;
    }

    public int getMinAge() {
        return minAge;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public List<String> getPhotoUrls() {
        return Collections.unmodifiableList(photoUrls);
    }

    public UserState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Lifestyle getters (Phase 0.5b)

    public Lifestyle.Smoking getSmoking() {
        return smoking;
    }

    public Lifestyle.Drinking getDrinking() {
        return drinking;
    }

    public Lifestyle.WantsKids getWantsKids() {
        return wantsKids;
    }

    public Lifestyle.LookingFor getLookingFor() {
        return lookingFor;
    }

    public Lifestyle.Education getEducation() {
        return education;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    /** Returns the user's dealbreakers, or Dealbreakers.none() if not set. */
    public MatchPreferences.Dealbreakers getDealbreakers() {
        return dealbreakers != null ? dealbreakers : MatchPreferences.Dealbreakers.none();
    }

    /**
     * Returns the user's interests as a defensive copy.
     *
     * @return set of interests (never null, may be empty)
     */
    public Set<Interest> getInterests() {
        return EnumSetUtil.safeCopy(interests, Interest.class);
    }

    /**
     * Calculates the user's age based on their birth date using the specified
     * timezone.
     *
     * @param timezone the timezone to use for age calculation
     * @return the user's age in years, or empty if birth date is not set
     */
    public Optional<Integer> getAge(java.time.ZoneId timezone) {
        if (birthDate == null) {
            return Optional.empty();
        }
        return Optional.of(Period.between(birthDate, AppClock.today(timezone)).getYears());
    }

    // Verification getters (Phase 2 feature)
    public boolean isVerified() {
        return isVerified;
    }

    public VerificationMethod getVerificationMethod() {
        return verificationMethod;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public Instant getVerificationSentAt() {
        return verificationSentAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    // Pace preference setters
    public void setPacePreferences(PacePreferences pacePreferences) {
        this.pacePreferences = pacePreferences;
        touch();
    }

    // Setters (with updatedAt)

    public void setName(String name) {
        this.name = Objects.requireNonNull(name);
        touch();
    }

    public void setBio(String bio) {
        this.bio = bio;
        touch();
    }

    public void setEmail(String email) {
        this.email = ValidationService.normalizeEmail(email);
        touch();
    }

    public void setPhone(String phone) {
        this.phone = ValidationService.normalizePhone(phone);
        touch();
    }

    public void startVerification(VerificationMethod method, String verificationCode) {
        this.verificationMethod = Objects.requireNonNull(method, "method cannot be null");
        this.verificationCode = Objects.requireNonNull(verificationCode, "verificationCode cannot be null");
        this.verificationSentAt = AppClock.now();
        touch();
    }

    public void markVerified() {
        this.isVerified = true;
        this.verifiedAt = AppClock.now();
        this.verificationCode = null;
        this.verificationSentAt = null;
        touch();
    }

    public void clearVerificationAttempt() {
        this.verificationCode = null;
        this.verificationSentAt = null;
        touch();
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        touch();
    }

    public void setGender(Gender gender) {
        this.gender = gender;
        touch();
    }

    public void setInterestedIn(Set<Gender> interestedIn) {
        // Allow empty set during construction; activate() will enforce completeness
        this.interestedIn = EnumSetUtil.safeCopy(interestedIn, Gender.class);
        touch();
    }

    public void setLocation(double lat, double lon) {
        // Validate finite values
        if (!Double.isFinite(lat)) {
            throw new IllegalArgumentException("Latitude cannot be NaN or Infinity");
        }
        if (!Double.isFinite(lon)) {
            throw new IllegalArgumentException("Longitude cannot be NaN or Infinity");
        }

        // Validate latitude bounds [-90, 90]
        if (lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, got " + lat);
        }

        // Validate longitude bounds [-180, 180]
        if (lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, got " + lon);
        }

        this.lat = lat;
        this.lon = lon;
        this.hasLocationSet = true;
        touch();
    }

    public void setMaxDistanceKm(int maxDistanceKm, int systemMaxLimit) {
        if (maxDistanceKm <= 0) {
            throw new IllegalArgumentException("maxDistanceKm must be positive");
        }
        if (maxDistanceKm > systemMaxLimit) {
            throw new IllegalArgumentException("maxDistanceKm cannot exceed " + systemMaxLimit + " km");
        }
        this.maxDistanceKm = maxDistanceKm;
        touch();
    }

    /**
     * Sets max distance with a default system limit of 500km.
     * For production use, prefer {@link #setMaxDistanceKm(int, int)} with explicit system limit.
     *
     * @deprecated Use {@link #setMaxDistanceKm(int, int)} with explicit system limit
     */
    @Deprecated(since = "2026-03", forRemoval = false)
    @SuppressWarnings("java:S1133")
    public void setMaxDistanceKm(int maxDistanceKm) {
        setMaxDistanceKm(maxDistanceKm, 500);
    }

    public void setAgeRange(int minAge, int maxAge, int systemMinAge, int systemMaxAge) {
        if (minAge < systemMinAge) {
            throw new IllegalArgumentException("minAge must be at least " + systemMinAge);
        }
        if (maxAge > systemMaxAge) {
            throw new IllegalArgumentException("maxAge cannot exceed " + systemMaxAge);
        }
        if (maxAge < minAge) {
            throw new IllegalArgumentException("maxAge cannot be less than minAge");
        }
        this.minAge = minAge;
        this.maxAge = maxAge;
        touch();
    }

    /**
     * Sets age range with default system limits (18-120).
     * For production use, prefer {@link #setAgeRange(int, int, int, int)} with explicit system limits.
     *
     * @deprecated Use {@link #setAgeRange(int, int, int, int)} with explicit system limits
     */
    @Deprecated(since = "2026-03", forRemoval = false)
    @SuppressWarnings("java:S1133")
    public void setAgeRange(int minAge, int maxAge) {
        setAgeRange(minAge, maxAge, 18, 120);
    }

    /** Sets photo URLs with null-safe copy. */
    public void setPhotoUrls(List<String> photoUrls) {
        List<String> normalized = normalizePhotoUrls(photoUrls, true);
        validatePhotoLimit(normalized.size());
        this.photoUrls = normalized;
        touch();
    }

    /** Adds a photo URL. */
    public void addPhotoUrl(String url) {
        validatePhotoLimit(photoUrls.size() + 1);
        photoUrls.add(normalizePhotoUrl(url, false));
        touch();
    }

    // Lifestyle setters (Phase 0.5b)

    public void setSmoking(Lifestyle.Smoking smoking) {
        this.smoking = smoking;
        touch();
    }

    public void setDrinking(Lifestyle.Drinking drinking) {
        this.drinking = drinking;
        touch();
    }

    public void setWantsKids(Lifestyle.WantsKids wantsKids) {
        this.wantsKids = wantsKids;
        touch();
    }

    public void setLookingFor(Lifestyle.LookingFor lookingFor) {
        this.lookingFor = lookingFor;
        touch();
    }

    public void setEducation(Lifestyle.Education education) {
        this.education = education;
        touch();
    }

    public void setHeightCm(Integer heightCm) {
        if (heightCm != null && heightCm <= 0) {
            throw new IllegalArgumentException("Height must be positive");
        }
        this.heightCm = heightCm;
        touch();
    }

    public void setDealbreakers(MatchPreferences.Dealbreakers dealbreakers) {
        this.dealbreakers = dealbreakers;
        touch();
    }

    /**
     * Sets the user's interests.
     *
     * @param interests set of interests (null treated as empty)
     */
    public void setInterests(Set<Interest> interests) {
        this.interests = copyAndValidateInterests(interests);
        touch();
    }

    /**
     * Adds a single interest to the user's profile.
     *
     * @param interest the interest to add
     */
    public void addInterest(Interest interest) {
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
    public void removeInterest(Interest interest) {
        if (interest != null && interests.remove(interest)) {
            touch();
        }
    }

    // MatchState transitions

    /**
     * Activates the user. Only valid from INCOMPLETE or PAUSED state. Profile must
     * be complete.
     */
    public void activate() {
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
    public void pause() {
        if (state != UserState.ACTIVE) {
            throw new IllegalStateException("Can only pause an active user");
        }
        this.state = UserState.PAUSED;
        touch();
    }

    /** Bans the user. One-way transition. */
    public void ban() {
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
    public boolean isComplete() {
        return getMissingProfileFields().isEmpty();
    }

    /**
     * Returns the profile fields that are still missing for completeness.
     *
     * <p>This is the single source of truth for profile completeness rules.
     */
    public List<String> getMissingProfileFields() {
        List<String> missing = new ArrayList<>();
        if (name == null || name.isBlank()) {
            missing.add("name");
        }
        if (bio == null || bio.isBlank()) {
            missing.add("bio");
        }
        if (birthDate == null) {
            missing.add("birthDate");
        }
        if (gender == null) {
            missing.add("gender");
        }
        if (interestedIn == null || interestedIn.isEmpty()) {
            missing.add("interestedIn");
        }
        if (maxDistanceKm <= 0) {
            missing.add("maxDistanceKm");
        }
        if (minAge <= 0) {
            missing.add("minAge");
        }
        if (maxAge < minAge) {
            missing.add("maxAge");
        }
        if (photoUrls == null || photoUrls.isEmpty()) {
            missing.add("photoUrls");
        }
        if (!hasCompletePace()) {
            missing.add("pacePreferences");
        }
        return List.copyOf(missing);
    }

    /** Checks if the user has completed their pace MatchPreferences. */
    public boolean hasCompletePace() {
        return pacePreferences != null && pacePreferences.isComplete();
    }

    private void touch() {
        this.updatedAt = AppClock.now();
    }

    private static void validatePhotoLimit(int photoCount) {
        if (photoCount > MAX_PHOTOS) {
            throw new IllegalArgumentException("Cannot set more than " + MAX_PHOTOS + " photos");
        }
    }

    private static List<String> normalizePhotoUrls(List<String> photoUrls, boolean allowPlaceholder) {
        if (photoUrls == null || photoUrls.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> normalized = new ArrayList<>(photoUrls.size());
        for (String photoUrl : photoUrls) {
            normalized.add(normalizePhotoUrl(photoUrl, allowPlaceholder));
        }
        return normalized;
    }

    private static String normalizePhotoUrl(String photoUrl, boolean allowPlaceholder) {
        if (photoUrl == null) {
            throw new IllegalArgumentException("Photo URL cannot be null");
        }
        String trimmed = photoUrl.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Photo URL cannot be blank");
        }
        if (allowPlaceholder && PLACEHOLDER_PHOTO_URL.equals(trimmed)) {
            return trimmed;
        }
        return ValidationService.normalizePhotoUrl(trimmed);
    }

    @Override
    public boolean equals(Object o) {
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
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', state=" + state + "}";
    }

    // ================================
    // Soft-delete support
    // ================================

    /** Returns the deletion timestamp, or {@code null} if not deleted. */
    public Instant getDeletedAt() {
        return deletedAt;
    }

    /** Returns a deep copy of this user, including dealbreakers and mutable collections. */
    public User copy() {
        User copy = StorageBuilder.create(id, name, createdAt)
                .bio(bio)
                .birthDate(birthDate)
                .gender(gender)
                .interestedIn(interestedIn)
                .location(lat, lon)
                .hasLocationSet(hasLocationSet)
                .maxDistanceKm(maxDistanceKm)
                .ageRange(minAge, maxAge)
                .photoUrls(photoUrls)
                .state(state)
                .updatedAt(updatedAt)
                .interests(interests)
                .smoking(smoking)
                .drinking(drinking)
                .wantsKids(wantsKids)
                .lookingFor(lookingFor)
                .education(education)
                .heightCm(heightCm)
                .email(email)
                .phone(phone)
                .verified(isVerified)
                .verificationMethod(verificationMethod)
                .verificationCode(verificationCode)
                .verificationSentAt(verificationSentAt)
                .verifiedAt(verifiedAt)
                .pacePreferences(pacePreferences)
                .deletedAt(deletedAt)
                .build();

        if (dealbreakers != null) {
            copy.dealbreakers = new MatchPreferences.Dealbreakers(
                    dealbreakers.acceptableSmoking(),
                    dealbreakers.acceptableDrinking(),
                    dealbreakers.acceptableKidsStance(),
                    dealbreakers.acceptableLookingFor(),
                    dealbreakers.acceptableEducation(),
                    dealbreakers.minHeightCm(),
                    dealbreakers.maxHeightCm(),
                    dealbreakers.maxAgeDifference());
        }
        return copy;
    }

    /** Marks this entity as soft-deleted at the given instant. */
    public void markDeleted(Instant deletedAt) {
        this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt cannot be null");
        touch();
    }

    /** Returns {@code true} if this user has been soft-deleted. */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
