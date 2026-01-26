package datingapp.core;

import datingapp.core.Preferences.Interest;
import datingapp.core.Preferences.Lifestyle;
import datingapp.core.Preferences.PacePreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Represents a user in the dating app. Mutable entity - state can change over time. */
public class User {

    /** Represents the gender options available for users. */
    public enum Gender {
        MALE,
        FEMALE,
        OTHER
    }

    /**
     * Represents the lifecycle state of a user account. Valid transitions: INCOMPLETE → ACTIVE ↔
     * PAUSED → BANNED
     */
    public enum State {
        INCOMPLETE,
        ACTIVE,
        PAUSED,
        BANNED
    }

    /**
     * Represents the verification method used to verify a profile.
     *
     * <p>NOTE: Currently simulated - email/phone not sent externally. Future enhancement: integrate
     * real email/SMS service for actual code delivery.
     */
    public enum VerificationMethod {
        EMAIL,
        PHONE
    }

    /** Storage interface for User entities. Defined in core, implemented in storage layer. */
    public interface Storage {

        /** Saves a user (insert or update). */
        void save(User user);

        /**
         * Gets a user by ID.
         *
         * @param id The user ID
         * @return The user, or null if not found
         */
        User get(UUID id);

        /** Finds all active users. */
        List<User> findActive();

        /** Finds all users regardless of state. */
        List<User> findAll();

        /**
         * Deletes a user and all their associated data. When combined with CASCADE DELETE on
         * foreign keys, this will automatically remove likes, matches, sessions, and stats.
         *
         * @param id The user ID to delete
         */
        void delete(UUID id);
    }

    private final UUID id;
    private String name;
    private String bio;
    private LocalDate birthDate;
    private Gender gender;
    private Set<Gender> interestedIn;
    private double lat;
    private double lon;
    private int maxDistanceKm;
    private int minAge;
    private int maxAge;
    private List<String> photoUrls;
    private State state;
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
    private Dealbreakers dealbreakers;

    // Interests (Phase 1 feature)
    private Set<Interest> interests = EnumSet.noneOf(Interest.class);

    // Verification fields
    private String email;
    private String phone;
    private boolean isVerified;
    private VerificationMethod verificationMethod;
    private String verificationCode;
    private Instant verificationSentAt;
    private Instant verifiedAt;

    private PacePreferences pacePreferences;

    /** Creates a new incomplete user with just an ID and name. Timestamps are set to current time. */
    public User(UUID id, String name) {
        this(id, name, Instant.now());
    }

    /**
     * Private constructor for full initialization. Used by public constructor and fromDatabase()
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
        this.state = State.INCOMPLETE;
        this.interests = EnumSet.noneOf(Interest.class);
    }

    /** Builder-backed record of database fields used to reconstruct a User. */
    public static final class DatabaseRecord {
        private UUID id;
        private String name;
        private String bio;
        private LocalDate birthDate;
        private Gender gender;
        private Set<Gender> interestedIn;
        private double lat;
        private double lon;
        private int maxDistanceKm;
        private int minAge;
        private int maxAge;
        private List<String> photoUrls;
        private State state;
        private Instant createdAt;
        private Instant updatedAt;
        private Set<Interest> interests;
        private Lifestyle.Smoking smoking;
        private Lifestyle.Drinking drinking;
        private Lifestyle.WantsKids wantsKids;
        private Lifestyle.LookingFor lookingFor;
        private Lifestyle.Education education;
        private Integer heightCm;
        private String email;
        private String phone;
        private Boolean isVerified;
        private VerificationMethod verificationMethod;
        private String verificationCode;
        private Instant verificationSentAt;
        private Instant verifiedAt;
        private PacePreferences pacePreferences;

        private DatabaseRecord() {}

        public static Builder builder() {
            return new Builder();
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
            return interestedIn;
        }

        public double getLat() {
            return lat;
        }

        public double getLon() {
            return lon;
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
            return photoUrls;
        }

        public State getState() {
            return state;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getUpdatedAt() {
            return updatedAt;
        }

        public Set<Interest> getInterests() {
            return interests;
        }

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

        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }

        public Boolean getIsVerified() {
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

        public PacePreferences getPacePreferences() {
            return pacePreferences;
        }

        public static final class Builder {
            private final DatabaseRecord data = new DatabaseRecord();

            public Builder id(UUID id) {
                data.id = id;
                return this;
            }

            public Builder name(String name) {
                data.name = name;
                return this;
            }

            public Builder bio(String bio) {
                data.bio = bio;
                return this;
            }

            public Builder birthDate(LocalDate birthDate) {
                data.birthDate = birthDate;
                return this;
            }

            public Builder gender(Gender gender) {
                data.gender = gender;
                return this;
            }

            public Builder interestedIn(Set<Gender> interestedIn) {
                data.interestedIn = interestedIn;
                return this;
            }

            public Builder lat(double lat) {
                data.lat = lat;
                return this;
            }

            public Builder lon(double lon) {
                data.lon = lon;
                return this;
            }

            public Builder maxDistanceKm(int maxDistanceKm) {
                data.maxDistanceKm = maxDistanceKm;
                return this;
            }

            public Builder minAge(int minAge) {
                data.minAge = minAge;
                return this;
            }

            public Builder maxAge(int maxAge) {
                data.maxAge = maxAge;
                return this;
            }

            public Builder photoUrls(List<String> photoUrls) {
                data.photoUrls = photoUrls;
                return this;
            }

            public Builder state(State state) {
                data.state = state;
                return this;
            }

            public Builder createdAt(Instant createdAt) {
                data.createdAt = createdAt;
                return this;
            }

            public Builder updatedAt(Instant updatedAt) {
                data.updatedAt = updatedAt;
                return this;
            }

            public Builder interests(Set<Interest> interests) {
                data.interests = interests;
                return this;
            }

            public Builder smoking(Lifestyle.Smoking smoking) {
                data.smoking = smoking;
                return this;
            }

            public Builder drinking(Lifestyle.Drinking drinking) {
                data.drinking = drinking;
                return this;
            }

            public Builder wantsKids(Lifestyle.WantsKids wantsKids) {
                data.wantsKids = wantsKids;
                return this;
            }

            public Builder lookingFor(Lifestyle.LookingFor lookingFor) {
                data.lookingFor = lookingFor;
                return this;
            }

            public Builder education(Lifestyle.Education education) {
                data.education = education;
                return this;
            }

            public Builder heightCm(Integer heightCm) {
                data.heightCm = heightCm;
                return this;
            }

            public Builder email(String email) {
                data.email = email;
                return this;
            }

            public Builder phone(String phone) {
                data.phone = phone;
                return this;
            }

            public Builder isVerified(Boolean isVerified) {
                data.isVerified = isVerified;
                return this;
            }

            public Builder verificationMethod(VerificationMethod verificationMethod) {
                data.verificationMethod = verificationMethod;
                return this;
            }

            public Builder verificationCode(String verificationCode) {
                data.verificationCode = verificationCode;
                return this;
            }

            public Builder verificationSentAt(Instant verificationSentAt) {
                data.verificationSentAt = verificationSentAt;
                return this;
            }

            public Builder verifiedAt(Instant verifiedAt) {
                data.verifiedAt = verifiedAt;
                return this;
            }

            public Builder pacePreferences(PacePreferences pacePreferences) {
                data.pacePreferences = pacePreferences;
                return this;
            }

            public DatabaseRecord build() {
                return data;
            }
        }
    }

    /**
     * Factory method for loading a user from the database. All 16 parameters are needed to fully
     * reconstruct a user record from storage. This method encapsulates the database-to-domain
     * conversion logic.
     */
    public static User fromDatabase(DatabaseRecord data) {
        Objects.requireNonNull(data, "record cannot be null");

        User user = new User(data.getId(), data.getName(), data.getCreatedAt());

        user.bio = data.getBio();
        user.birthDate = data.getBirthDate();
        user.gender = data.getGender();
        user.interestedIn =
                data.getInterestedIn() != null ? EnumSet.copyOf(data.getInterestedIn()) : EnumSet.noneOf(Gender.class);
        user.lat = data.getLat();
        user.lon = data.getLon();
        user.maxDistanceKm = data.getMaxDistanceKm();
        user.minAge = data.getMinAge();
        user.maxAge = data.getMaxAge();
        user.photoUrls = data.getPhotoUrls() != null ? new ArrayList<>(data.getPhotoUrls()) : new ArrayList<>();
        user.state = data.getState();
        user.updatedAt = data.getUpdatedAt();
        user.interests =
                data.getInterests() != null ? EnumSet.copyOf(data.getInterests()) : EnumSet.noneOf(Interest.class);

        user.smoking = data.getSmoking();
        user.drinking = data.getDrinking();
        user.wantsKids = data.getWantsKids();
        user.lookingFor = data.getLookingFor();
        user.education = data.getEducation();
        user.heightCm = data.getHeightCm();

        user.email = data.getEmail();
        user.phone = data.getPhone();
        user.isVerified = data.getIsVerified() != null && data.getIsVerified();
        user.verificationMethod = data.getVerificationMethod();
        user.verificationCode = data.getVerificationCode();
        user.verificationSentAt = data.getVerificationSentAt();
        user.verifiedAt = data.getVerifiedAt();
        user.pacePreferences = data.getPacePreferences();

        return user;
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
        return EnumSet.copyOf(interestedIn);
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
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
        return new ArrayList<>(photoUrls);
    }

    public State getState() {
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
    public Dealbreakers getDealbreakers() {
        return dealbreakers != null ? dealbreakers : Dealbreakers.none();
    }

    /**
     * Returns the user's interests as a defensive copy.
     *
     * @return set of interests (never null, may be empty)
     */
    public Set<Interest> getInterests() {
        return interests.isEmpty() ? EnumSet.noneOf(Interest.class) : EnumSet.copyOf(interests);
    }

    /** Calculates the user's age based on their birth date. */
    public int getAge() {
        if (birthDate == null) {
            return 0;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    // Verification getters (Phase 2 feature)
    public Boolean isVerified() {
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
        this.email = email;
        touch();
    }

    public void setPhone(String phone) {
        this.phone = phone;
        touch();
    }

    public void startVerification(VerificationMethod method, String verificationCode) {
        this.verificationMethod = Objects.requireNonNull(method, "method cannot be null");
        this.verificationCode = Objects.requireNonNull(verificationCode, "verificationCode cannot be null");
        this.verificationSentAt = Instant.now();
        touch();
    }

    public void markVerified() {
        this.isVerified = true;
        this.verifiedAt = Instant.now();
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
        this.interestedIn = interestedIn != null ? EnumSet.copyOf(interestedIn) : EnumSet.noneOf(Gender.class);
        touch();
    }

    public void setLocation(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
        touch();
    }

    public void setMaxDistanceKm(int maxDistanceKm) {
        if (maxDistanceKm < 1) {
            throw new IllegalArgumentException("maxDistanceKm must be at least 1");
        }
        if (maxDistanceKm > 500) {
            throw new IllegalArgumentException("maxDistanceKm cannot exceed 500");
        }
        this.maxDistanceKm = maxDistanceKm;
        touch();
    }

    public void setAgeRange(int minAge, int maxAge) {
        if (minAge < 18) {
            throw new IllegalArgumentException("minAge must be at least 18");
        }
        if (maxAge > 120) {
            throw new IllegalArgumentException("maxAge cannot exceed 120");
        }
        if (maxAge < minAge) {
            throw new IllegalArgumentException("maxAge cannot be less than minAge");
        }
        this.minAge = minAge;
        this.maxAge = maxAge;
        touch();
    }

    /** Sets photo URLs. Maximum 2 photos allowed. */
    public void setPhotoUrls(List<String> photoUrls) {
        if (photoUrls != null && photoUrls.size() > 2) {
            throw new IllegalArgumentException("Maximum 2 photos allowed");
        }
        this.photoUrls = photoUrls != null ? new ArrayList<>(photoUrls) : new ArrayList<>();
        touch();
    }

    /** Adds a photo URL. Maximum 2 photos allowed. */
    public void addPhotoUrl(String url) {
        if (photoUrls.size() >= 2) {
            throw new IllegalArgumentException("Maximum 2 photos allowed");
        }
        photoUrls.add(url);
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
        if (heightCm != null && (heightCm < 100 || heightCm > 250)) {
            throw new IllegalArgumentException("Height must be 100-250 cm");
        }
        this.heightCm = heightCm;
        touch();
    }

    public void setDealbreakers(Dealbreakers dealbreakers) {
        this.dealbreakers = dealbreakers;
        touch();
    }

    /**
     * Sets the user's interests. Maximum of {@link Interest#MAX_PER_USER} interests allowed.
     *
     * @param interests set of interests (null treated as empty)
     * @throws IllegalArgumentException if more than MAX_PER_USER interests
     */
    public void setInterests(Set<Interest> interests) {
        if (interests != null && interests.size() > Interest.MAX_PER_USER) {
            throw new IllegalArgumentException(
                    "Maximum " + Interest.MAX_PER_USER + " interests allowed, got " + interests.size());
        }
        this.interests =
                (interests == null || interests.isEmpty()) ? EnumSet.noneOf(Interest.class) : EnumSet.copyOf(interests);
        touch();
    }

    /**
     * Adds a single interest to the user's profile.
     *
     * @param interest the interest to add
     * @throws IllegalArgumentException if adding would exceed MAX_PER_USER
     */
    public void addInterest(Interest interest) {
        if (interest == null) {
            return;
        }
        if (interests.size() >= Interest.MAX_PER_USER && !interests.contains(interest)) {
            throw new IllegalArgumentException("Maximum " + Interest.MAX_PER_USER + " interests allowed");
        }
        interests.add(interest);
        touch();
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

    // State transitions

    /** Activates the user. Only valid from INCOMPLETE or PAUSED state. Profile must be complete. */
    public void activate() {
        if (state == State.BANNED) {
            throw new IllegalStateException("Cannot activate a banned user");
        }
        if (!isComplete()) {
            throw new IllegalStateException("Cannot activate an incomplete profile");
        }
        this.state = State.ACTIVE;
        touch();
    }

    /** Pauses the user. Only valid from ACTIVE state. */
    public void pause() {
        if (state != State.ACTIVE) {
            throw new IllegalStateException("Can only pause an active user");
        }
        this.state = State.PAUSED;
        touch();
    }

    /** Bans the user. One-way transition. */
    public void ban() {
        if (state == State.BANNED) {
            return; // Already banned
        }
        this.state = State.BANNED;
        touch();
    }

    /** Checks if the user profile is complete. A complete profile has all required fields filled. */
    public boolean isComplete() {
        return name != null
                && !name.isBlank()
                && bio != null
                && !bio.isBlank()
                && birthDate != null
                && gender != null
                && interestedIn != null
                && !interestedIn.isEmpty()
                && maxDistanceKm > 0
                && minAge >= 18
                && maxAge >= minAge
                && photoUrls != null
                && !photoUrls.isEmpty()
                && hasCompletePace();
    }

    /** Checks if the user has completed their pace preferences. */
    public boolean hasCompletePace() {
        return pacePreferences != null && pacePreferences.isComplete();
    }

    private void touch() {
        this.updatedAt = Instant.now();
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
    // Nested Record: ProfileNote
    // ================================

    /**
     * A private note that a user can attach to another user's profile. Notes are only visible to the
     * author - the subject never sees them.
     *
     * <p>Use cases:
     *
     * <ul>
     *   <li>Remember where you met someone ("Coffee shop downtown")
     *   <li>Note conversation topics ("Loves hiking, has a dog named Max")
     *   <li>Track date plans ("Dinner Thursday @ Olive Garden")
     * </ul>
     */
    public static record ProfileNote(
            UUID authorId, UUID subjectId, String content, Instant createdAt, Instant updatedAt) {

        /** Maximum length for note content. */
        public static final int MAX_LENGTH = 500;

        /**
         * Creates a new profile note with current timestamp.
         *
         * @param authorId ID of the user creating the note
         * @param subjectId ID of the user the note is about
         * @param content the note content
         * @return a new ProfileNote
         * @throws IllegalArgumentException if content exceeds MAX_LENGTH or is blank
         */
        public static ProfileNote create(UUID authorId, UUID subjectId, String content) {
            Objects.requireNonNull(authorId, "authorId cannot be null");
            Objects.requireNonNull(subjectId, "subjectId cannot be null");
            Objects.requireNonNull(content, "content cannot be null");

            if (content.isBlank()) {
                throw new IllegalArgumentException("Note content cannot be blank");
            }
            if (content.length() > MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "Note content exceeds maximum length of " + MAX_LENGTH + " characters");
            }
            if (authorId.equals(subjectId)) {
                throw new IllegalArgumentException("Cannot create a note about yourself");
            }

            Instant now = Instant.now();
            return new ProfileNote(authorId, subjectId, content, now, now);
        }

        /**
         * Creates an updated version of this note with new content.
         *
         * @param newContent the new content
         * @return a new ProfileNote with updated content and timestamp
         */
        public ProfileNote withContent(String newContent) {
            if (newContent == null || newContent.isBlank()) {
                throw new IllegalArgumentException("Note content cannot be blank");
            }
            if (newContent.length() > MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "Note content exceeds maximum length of " + MAX_LENGTH + " characters");
            }
            return new ProfileNote(authorId, subjectId, newContent, createdAt, Instant.now());
        }

        /**
         * Gets a preview of the note content (first 50 chars).
         *
         * @return truncated preview with ellipsis if content is longer
         */
        public String getPreview() {
            if (content.length() <= 50) {
                return content;
            }
            return content.substring(0, 47) + "...";
        }
    }

    // ========== STORAGE INTERFACES ==========

    /**
     * Storage interface for private profile notes.
     */
    public interface ProfileNoteStorage {

        /**
         * Saves or updates a note about another user.
         *
         * @param note the profile note to save
         */
        void save(ProfileNote note);

        /**
         * Gets a user's note about another user.
         *
         * @param authorId ID of the note author
         * @param subjectId ID of the user the note is about
         * @return the note if it exists
         */
        java.util.Optional<ProfileNote> get(java.util.UUID authorId, java.util.UUID subjectId);

        /**
         * Gets all notes created by a user.
         *
         * @param authorId ID of the note author
         * @return list of all notes by this user
         */
        java.util.List<ProfileNote> getAllByAuthor(java.util.UUID authorId);

        /**
         * Deletes a note.
         *
         * @param authorId ID of the note author
         * @param subjectId ID of the user the note is about
         * @return true if a note was deleted
         */
        boolean delete(java.util.UUID authorId, java.util.UUID subjectId);
    }

    /**
     * Storage interface for tracking profile views.
     */
    public interface ProfileViewStorage {

        /**
         * Records a profile view.
         *
         * @param viewerId ID of the user who viewed the profile
         * @param viewedId ID of the profile that was viewed
         */
        void recordView(java.util.UUID viewerId, java.util.UUID viewedId);

        /**
         * Gets the total number of views for a user's profile.
         *
         * @param userId ID of the user whose profile was viewed
         * @return total view count
         */
        int getViewCount(java.util.UUID userId);

        /**
         * Gets the number of unique viewers for a user's profile.
         *
         * @param userId ID of the user whose profile was viewed
         * @return unique viewer count
         */
        int getUniqueViewerCount(java.util.UUID userId);

        /**
         * Gets recent viewers of a user's profile.
         *
         * @param userId ID of the user whose profile was viewed
         * @param limit maximum number of viewers to return
         * @return list of viewer IDs (most recent first)
         */
        java.util.List<java.util.UUID> getRecentViewers(java.util.UUID userId, int limit);

        /**
         * Checks if a user has viewed another user's profile.
         *
         * @param viewerId ID of the potential viewer
         * @param viewedId ID of the profile owner
         * @return true if the viewer has viewed the profile
         */
        boolean hasViewed(java.util.UUID viewerId, java.util.UUID viewedId);
    }
}
