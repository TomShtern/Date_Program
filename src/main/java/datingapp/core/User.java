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

  // Verification fields (Phase 2 feature)
  private String email;
  private String phone;
  private Boolean isVerified;
  private VerificationMethod verificationMethod;
  private String verificationCode;
  private Instant verificationSentAt;
  private Instant verifiedAt;

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

  /**
   * Factory method for loading a user from the database. All 16 parameters are needed to fully
   * reconstruct a user record from storage. This method encapsulates the database-to-domain
   * conversion logic.
   */
  @SuppressWarnings("too-many-parameters")
  public static User fromDatabase(
      UUID id,
      String name,
      String bio,
      LocalDate birthDate,
      Gender gender,
      Set<Gender> interestedIn,
      double lat,
      double lon,
      int maxDistanceKm,
      int minAge,
      int maxAge,
      List<String> photoUrls,
      State state,
      Instant createdAt,
      Instant updatedAt,
      Set<Interest> interests,
      String email,
      String phone,
      Boolean isVerified,
      VerificationMethod verificationMethod,
      String verificationCode,
      Instant verificationSentAt,
      Instant verifiedAt) {

    User user = new User(id, name, createdAt);

    user.bio = bio;
    user.birthDate = birthDate;
    user.gender = gender;
    user.interestedIn =
        interestedIn != null ? EnumSet.copyOf(interestedIn) : EnumSet.noneOf(Gender.class);
    user.lat = lat;
    user.lon = lon;
    user.maxDistanceKm = maxDistanceKm;
    user.minAge = minAge;
    user.maxAge = maxAge;
    user.photoUrls = photoUrls != null ? new ArrayList<>(photoUrls) : new ArrayList<>();
    user.state = state;
    user.updatedAt = updatedAt;
    user.interests = interests != null ? EnumSet.copyOf(interests) : EnumSet.noneOf(Interest.class);

    user.email = email;
    user.phone = phone;
    user.isVerified = isVerified;
    user.verificationMethod = verificationMethod;
    user.verificationCode = verificationCode;
    user.verificationSentAt = verificationSentAt;
    user.verifiedAt = verifiedAt;

    return user;
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
    this.verificationCode =
        Objects.requireNonNull(verificationCode, "verificationCode cannot be null");
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
    this.interestedIn =
        interestedIn != null ? EnumSet.copyOf(interestedIn) : EnumSet.noneOf(Gender.class);
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
        (interests == null || interests.isEmpty())
            ? EnumSet.noneOf(Interest.class)
            : EnumSet.copyOf(interests);
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
        && !photoUrls.isEmpty();
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
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
