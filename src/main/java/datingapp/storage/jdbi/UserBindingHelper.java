package datingapp.storage.jdbi;

import datingapp.core.Dealbreakers;
import datingapp.core.PacePreferences;
import datingapp.core.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Helper class for binding User objects to JDBI SQL parameters.
 * Provides methods to serialize complex fields (EnumSets, Lists) as CSV
 * strings.
 */
public class UserBindingHelper {

    private final User user;
    private final Dealbreakers dealbreakers;
    private final PacePreferences pace;

    public UserBindingHelper(User user) {
        this.user = user;
        this.dealbreakers = user.getDealbreakers();
        this.pace = user.getPacePreferences();
    }

    // ===== Core User Fields =====

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

    public String getInterestedInCsv() {
        if (user.getInterestedIn() == null || user.getInterestedIn().isEmpty()) {
            return null;
        }
        return user.getInterestedIn().stream().map(Enum::name).collect(Collectors.joining(","));
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

    public String getPhotoUrlsCsv() {
        if (user.getPhotoUrls() == null || user.getPhotoUrls().isEmpty()) {
            return null;
        }
        // Use pipe as delimiter to avoid corrupting URLs that contain commas
        return String.join("|", user.getPhotoUrls());
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

    // ===== Lifestyle Fields =====

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

    // ===== Interests =====

    public String getInterestsCsv() {
        if (user.getInterests() == null || user.getInterests().isEmpty()) {
            return null;
        }
        return user.getInterests().stream().map(Enum::name).collect(Collectors.joining(","));
    }

    // ===== Verification Fields =====

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

    // ===== Dealbreaker Fields =====

    public String getDealbreakerSmokingCsv() {
        return serializeEnumSet(dealbreakers.acceptableSmoking());
    }

    public String getDealbreakerDrinkingCsv() {
        return serializeEnumSet(dealbreakers.acceptableDrinking());
    }

    public String getDealbreakerWantsKidsCsv() {
        return serializeEnumSet(dealbreakers.acceptableKidsStance());
    }

    public String getDealbreakerLookingForCsv() {
        return serializeEnumSet(dealbreakers.acceptableLookingFor());
    }

    public String getDealbreakerEducationCsv() {
        return serializeEnumSet(dealbreakers.acceptableEducation());
    }

    public Integer getDealbreakerMinHeightCm() {
        return dealbreakers.minHeightCm();
    }

    public Integer getDealbreakerMaxHeightCm() {
        return dealbreakers.maxHeightCm();
    }

    public Integer getDealbreakerMaxAgeDiff() {
        return dealbreakers.maxAgeDifference();
    }

    // ===== Pace Preference Fields =====

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

    // ===== Soft Delete =====

    public Instant getDeletedAt() {
        return user.getDeletedAt();
    }

    // ===== Helper Methods =====

    private String serializeEnumSet(java.util.Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().map(Enum::name).collect(Collectors.joining(","));
    }
}
