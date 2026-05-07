package datingapp.app.api;

import datingapp.core.AppClock;
import datingapp.core.model.ProfileNote;
import datingapp.core.model.User;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import datingapp.core.workflow.ProfileActivationPolicy;
import datingapp.location.LocationModels.City;
import datingapp.location.LocationModels.ResolvedLocation;
import datingapp.location.LocationService.SelectionSeed;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ProfileDtos {
    private ProfileDtos() {}

    /** Request body for profile updates. */
    static record ProfileUpdateRequest(
            String name,
            String bio,
            LocalDate birthDate,
            User.Gender gender,
            Set<User.Gender> interestedIn,
            Double latitude,
            Double longitude,
            Integer maxDistanceKm,
            Integer minAge,
            Integer maxAge,
            Integer heightCm,
            Lifestyle.Smoking smoking,
            Lifestyle.Drinking drinking,
            Lifestyle.WantsKids wantsKids,
            Lifestyle.LookingFor lookingFor,
            Lifestyle.Education education,
            Set<Interest> interests,
            Dealbreakers dealbreakers,
            LocationDtos.ProfileLocationRequest location,
            WritePacePreferencesDto pacePreferences) {}

    /** Nested DTO for pace preferences in profile update requests. */
    static record WritePacePreferencesDto(
            PacePreferences.MessagingFrequency messagingFrequency,
            PacePreferences.TimeToFirstDate timeToFirstDate,
            PacePreferences.CommunicationStyle communicationStyle,
            PacePreferences.DepthPreference depthPreference) {

        PacePreferences toPacePreferences() {
            if (messagingFrequency == null
                    && timeToFirstDate == null
                    && communicationStyle == null
                    && depthPreference == null) {
                return null;
            }
            return new PacePreferences(messagingFrequency, timeToFirstDate, communicationStyle, depthPreference);
        }
    }

    static record PresentationContextDto(
            UUID viewerUserId,
            UUID targetUserId,
            String summary,
            List<String> reasonTags,
            List<String> details,
            Instant generatedAt) {
        static PresentationContextDto from(
                User viewer, User target, String targetLocation, java.time.ZoneId userTimeZone) {
            List<String> tags = new ArrayList<>();
            List<String> details = new ArrayList<>();

            Set<Interest> sharedInterests = new HashSet<>(viewer.getInterests());
            sharedInterests.retainAll(target.getInterests());
            if (!sharedInterests.isEmpty()) {
                tags.add("shared_interests");
                String interestText =
                        sharedInterests.stream().map(Interest::getDisplayName).sorted().limit(3).toList().stream()
                                .reduce((left, right) -> left + " and " + right)
                                .orElse("interests");
                details.add("You both list " + interestText + " as interests.");
            }

            if (viewer.hasLocation() && target.hasLocation()) {
                tags.add("nearby");
                details.add(
                        targetLocation != null
                                ? "This profile is near " + targetLocation + "."
                                : "This profile is within your eligible match area.");
            }

            Integer viewerAge = viewer.getAge(userTimeZone).orElse(null);
            Integer targetAge = target.getAge(userTimeZone).orElse(null);
            if (viewerAge != null
                    && targetAge != null
                    && targetAge >= viewer.getMinAge()
                    && targetAge <= viewer.getMaxAge()) {
                tags.add("age_compatible");
                details.add("This profile is within your preferred age range.");
            }

            if (viewer.getLookingFor() != null && viewer.getLookingFor() == target.getLookingFor()) {
                tags.add("same_relationship_goals");
                details.add("You are both looking for " + viewer.getLookingFor().getDisplayName() + ".");
            }

            if (tags.isEmpty()) {
                tags.add("eligible_match_pool");
                details.add("This profile is currently eligible for your match pool.");
            }

            String summary =
                    switch (tags.getFirst()) {
                        case "shared_interests" -> "Shown because you share interests with this profile.";
                        case "nearby" -> "Shown because this profile is nearby.";
                        case "age_compatible" -> "Shown because this profile fits your current preferences.";
                        case "same_relationship_goals" -> "Shown because your relationship goals line up.";
                        default -> "Shown because this profile is eligible for your current match pool.";
                    };

            return new PresentationContextDto(
                    viewer.getId(), target.getId(), summary, List.copyOf(tags), List.copyOf(details), AppClock.now());
        }
    }

    static record ProfileEditSnapshotDto(
            UUID userId,
            ProfileEditableDto editable,
            ProfileReadOnlyDto readOnly,
            List<String> missingProfileFields,
            List<String> missingProfileFieldLabels,
            Integer requiredProfileFieldCount,
            Boolean profileComplete,
            Boolean canActivate,
            Boolean canBrowse) {
        static ProfileEditSnapshotDto from(User user, SelectionSeed seed, ProfileActivationPolicy activationPolicy) {
            ProfileCompletionView completion =
                    activationPolicy != null ? ProfileCompletionView.from(user, activationPolicy) : null;
            return new ProfileEditSnapshotDto(
                    user.getId(),
                    ProfileEditableDto.from(user, seed),
                    ProfileReadOnlyDto.from(user),
                    completion != null ? completion.missingProfileFields() : null,
                    completion != null ? completion.missingProfileFieldLabels() : null,
                    completion != null ? completion.requiredProfileFieldCount() : null,
                    completion != null ? completion.profileComplete() : null,
                    completion != null ? completion.canActivate() : null,
                    completion != null ? completion.canBrowse() : null);
        }
    }

    static record ProfileEditableDto(
            String bio,
            LocalDate birthDate,
            String gender,
            List<String> interestedIn,
            int maxDistanceKm,
            int minAge,
            int maxAge,
            Integer heightCm,
            String smoking,
            String drinking,
            String wantsKids,
            String lookingFor,
            String education,
            List<String> interests,
            DealbreakersDto dealbreakers,
            PacePreferencesEditableDto pacePreferences,
            ProfileEditLocationDto location) {
        static ProfileEditableDto from(User user, SelectionSeed seed) {
            return new ProfileEditableDto(
                    user.getBio(),
                    user.getBirthDate(),
                    user.getGender() != null ? user.getGender().name() : null,
                    enumNames(user.getInterestedIn()),
                    user.getMaxDistanceKm(),
                    user.getMinAge(),
                    user.getMaxAge(),
                    user.getHeightCm(),
                    user.getSmoking() != null ? user.getSmoking().name() : null,
                    user.getDrinking() != null ? user.getDrinking().name() : null,
                    user.getWantsKids() != null ? user.getWantsKids().name() : null,
                    user.getLookingFor() != null ? user.getLookingFor().name() : null,
                    user.getEducation() != null ? user.getEducation().name() : null,
                    enumNames(user.getInterests()),
                    DealbreakersDto.from(user.getDealbreakers()),
                    PacePreferencesEditableDto.from(user.getPacePreferences()),
                    ProfileEditLocationDto.from(seed));
        }
    }

    static record DealbreakersDto(
            List<String> acceptableSmoking,
            List<String> acceptableDrinking,
            List<String> acceptableKidsStance,
            List<String> acceptableLookingFor,
            List<String> acceptableEducation,
            Integer minHeightCm,
            Integer maxHeightCm,
            Integer maxAgeDifference) {
        static DealbreakersDto from(Dealbreakers dealbreakers) {
            Dealbreakers safe = dealbreakers != null ? dealbreakers : Dealbreakers.none();
            return new DealbreakersDto(
                    enumNames(safe.acceptableSmoking()),
                    enumNames(safe.acceptableDrinking()),
                    enumNames(safe.acceptableKidsStance()),
                    enumNames(safe.acceptableLookingFor()),
                    enumNames(safe.acceptableEducation()),
                    safe.minHeightCm(),
                    safe.maxHeightCm(),
                    safe.maxAgeDifference());
        }
    }

    static record PacePreferencesEditableDto(
            String messagingFrequency, String timeToFirstDate, String communicationStyle, String depthPreference) {
        static PacePreferencesEditableDto from(PacePreferences pace) {
            if (pace == null) {
                return new PacePreferencesEditableDto(null, null, null, null);
            }
            return new PacePreferencesEditableDto(
                    pace.messagingFrequency() != null
                            ? pace.messagingFrequency().name()
                            : null,
                    pace.timeToFirstDate() != null ? pace.timeToFirstDate().name() : null,
                    pace.communicationStyle() != null
                            ? pace.communicationStyle().name()
                            : null,
                    pace.depthPreference() != null ? pace.depthPreference().name() : null);
        }
    }

    static record ProfileEditLocationDto(
            String label,
            double latitude,
            double longitude,
            String precision,
            String countryCode,
            String cityName,
            String zipCode,
            boolean approximate) {
        static ProfileEditLocationDto from(SelectionSeed seed) {
            if (seed == null) {
                return null;
            }
            ResolvedLocation location = seed.resolvedLocation();
            return new ProfileEditLocationDto(
                    location.label(),
                    location.latitude(),
                    location.longitude(),
                    location.precision().name(),
                    seed.country().code(),
                    seed.city().map(City::name).orElse(null),
                    seed.zipPrefix().orElse(null),
                    false);
        }
    }

    static record ProfileReadOnlyDto(
            String name,
            String state,
            List<String> photoUrls,
            boolean verified,
            String verificationMethod,
            Instant verifiedAt) {
        static ProfileReadOnlyDto from(User user) {
            return new ProfileReadOnlyDto(
                    user.getName(),
                    user.getState().name(),
                    List.copyOf(user.getPhotoUrls()),
                    user.isVerified(),
                    user.getVerificationMethod() != null
                            ? user.getVerificationMethod().name()
                            : null,
                    user.getVerifiedAt());
        }
    }

    static record ProfileUpdateResponse(
            UUID id,
            String name,
            int age,
            String bio,
            String gender,
            List<String> interestedIn,
            String approximateLocation,
            int maxDistanceKm,
            String state,
            boolean activated,
            List<String> missingProfileFields,
            List<String> missingProfileFieldLabels,
            int requiredProfileFieldCount,
            boolean profileComplete,
            boolean canActivate,
            boolean canBrowse) {
        static ProfileUpdateResponse from(
                User user, boolean activated, java.time.ZoneId userTimeZone, String approximateLocation) {
            return from(user, activated, userTimeZone, approximateLocation, null);
        }

        static ProfileUpdateResponse from(
                User user,
                boolean activated,
                java.time.ZoneId userTimeZone,
                String approximateLocation,
                ProfileActivationPolicy activationPolicy) {
            RestApiUserDtos.UserFields fields = RestApiUserDtos.mapUserFields(user, userTimeZone, approximateLocation);
            ProfileCompletionView completion =
                    activationPolicy != null ? ProfileCompletionView.from(user, activationPolicy) : null;
            return new ProfileUpdateResponse(
                    user.getId(),
                    user.getName(),
                    fields.age(),
                    user.getBio(),
                    fields.gender(),
                    fields.interestedIn(),
                    fields.approximateLocation(),
                    fields.maxDistanceKm(),
                    fields.state(),
                    activated,
                    completion != null ? completion.missingProfileFields() : null,
                    completion != null ? completion.missingProfileFieldLabels() : null,
                    completion != null ? completion.requiredProfileFieldCount() : 0,
                    completion != null && completion.profileComplete(),
                    completion != null && completion.canActivate(),
                    completion != null && completion.canBrowse());
        }
    }

    /** Private profile note DTO. */
    static record ProfileNoteDto(UUID authorId, UUID subjectId, String content, Instant createdAt, Instant updatedAt) {
        static ProfileNoteDto from(ProfileNote note) {
            return new ProfileNoteDto(
                    note.authorId(), note.subjectId(), note.content(), note.createdAt(), note.updatedAt());
        }
    }

    /** Request body for creating or updating a private profile note. */
    static record ProfileNoteUpsertRequest(String content) {}

    private static List<String> enumNames(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(Enum::name).sorted().toList();
    }
}
