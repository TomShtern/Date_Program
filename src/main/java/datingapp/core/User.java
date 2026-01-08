package datingapp.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a user in the dating app.
 * Mutable entity - state can change over time.
 */
public class User {

    /**
     * Represents the gender options available for users.
     */
    public enum Gender {
        MALE, FEMALE, OTHER
    }

    /**
     * Represents the lifecycle state of a user account.
     * Valid transitions: INCOMPLETE → ACTIVE ↔ PAUSED → BANNED
     */
    public enum State {
        INCOMPLETE, ACTIVE, PAUSED, BANNED
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

    /**
     * Creates a new incomplete user with just an ID and name.
     */
    public User(UUID id, String name) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.interestedIn = EnumSet.noneOf(Gender.class);
        this.photoUrls = new ArrayList<>();
        this.maxDistanceKm = 50;
        this.minAge = 18;
        this.maxAge = 99;
        this.state = State.INCOMPLETE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Full constructor for loading from database.
     */
    public User(UUID id, String name, String bio, LocalDate birthDate, Gender gender,
            Set<Gender> interestedIn, double lat, double lon, int maxDistanceKm,
            int minAge, int maxAge, List<String> photoUrls, State state,
            Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.name = name;
        this.bio = bio;
        this.birthDate = birthDate;
        this.gender = gender;
        this.interestedIn = interestedIn != null ? EnumSet.copyOf(interestedIn) : EnumSet.noneOf(Gender.class);
        this.lat = lat;
        this.lon = lon;
        this.maxDistanceKm = maxDistanceKm;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.photoUrls = photoUrls != null ? new ArrayList<>(photoUrls) : new ArrayList<>();
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters

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

    /**
     * Returns the user's dealbreakers, or Dealbreakers.none() if not set.
     */
    public Dealbreakers getDealbreakers() {
        return dealbreakers != null ? dealbreakers : Dealbreakers.none();
    }

    /**
     * Calculates the user's age based on their birth date.
     */
    public int getAge() {
        if (birthDate == null) {
            return 0;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
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
        this.maxDistanceKm = maxDistanceKm;
        touch();
    }

    public void setAgeRange(int minAge, int maxAge) {
        if (minAge < 18) {
            throw new IllegalArgumentException("minAge must be at least 18");
        }
        if (maxAge < minAge) {
            throw new IllegalArgumentException("maxAge cannot be less than minAge");
        }
        this.minAge = minAge;
        this.maxAge = maxAge;
        touch();
    }

    /**
     * Sets photo URLs. Maximum 2 photos allowed.
     */
    public void setPhotoUrls(List<String> photoUrls) {
        if (photoUrls != null && photoUrls.size() > 2) {
            throw new IllegalArgumentException("Maximum 2 photos allowed");
        }
        this.photoUrls = photoUrls != null ? new ArrayList<>(photoUrls) : new ArrayList<>();
        touch();
    }

    /**
     * Adds a photo URL. Maximum 2 photos allowed.
     */
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

    // State transitions

    /**
     * Activates the user. Only valid from INCOMPLETE or PAUSED state.
     * Profile must be complete.
     */
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

    /**
     * Pauses the user. Only valid from ACTIVE state.
     */
    public void pause() {
        if (state != State.ACTIVE) {
            throw new IllegalStateException("Can only pause an active user");
        }
        this.state = State.PAUSED;
        touch();
    }

    /**
     * Bans the user. One-way transition.
     */
    public void ban() {
        if (state == State.BANNED) {
            return; // Already banned
        }
        this.state = State.BANNED;
        touch();
    }

    /**
     * Checks if the user profile is complete.
     * A complete profile has all required fields filled.
     */
    public boolean isComplete() {
        return name != null && !name.isBlank()
                && bio != null && !bio.isBlank()
                && birthDate != null
                && gender != null
                && interestedIn != null && !interestedIn.isEmpty()
                && maxDistanceKm > 0
                && minAge >= 18
                && maxAge >= minAge
                && photoUrls != null && !photoUrls.isEmpty();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
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
}
