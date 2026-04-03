package datingapp.ui.viewmodel;

import datingapp.app.usecase.profile.ProfileNormalizationSupport;
import datingapp.core.AppClock;
import datingapp.core.AppConfig;
import datingapp.core.model.User;
import datingapp.core.model.User.Gender;
import datingapp.core.profile.MatchPreferences.Dealbreakers;
import datingapp.core.profile.MatchPreferences.Interest;
import datingapp.core.profile.MatchPreferences.Lifestyle;
import datingapp.core.profile.MatchPreferences.PacePreferences;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ProfileDraftAssembler {
    private static final Logger logger = LoggerFactory.getLogger(ProfileDraftAssembler.class);

    private final AppConfig config;

    ProfileDraftAssembler(AppConfig config) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
    }

    User assemble(User currentUser, DraftState state, Consumer<String> warningSink) {
        if (currentUser == null) {
            throw new IllegalStateException("No current user available for preview.");
        }

        User draftUser = copyCurrentUserToDraft(currentUser);
        applyBasicFields(draftUser, state, warningSink);
        applyLocation(draftUser, state);
        applyInterests(draftUser, state);
        applyLifestyleFields(draftUser, state);
        applySearchPreferences(draftUser, state);
        applyDealbreakers(draftUser, state);
        applyPacePreferences(draftUser, state);
        return draftUser;
    }

    private static User copyCurrentUserToDraft(User currentUser) {
        return User.StorageBuilder.create(currentUser.getId(), currentUser.getName(), currentUser.getCreatedAt())
                .bio(currentUser.getBio())
                .birthDate(currentUser.getBirthDate())
                .gender(currentUser.getGender())
                .interestedIn(currentUser.getInterestedIn())
                .location(currentUser.getLat(), currentUser.getLon())
                .hasLocationSet(currentUser.hasLocationSet())
                .maxDistanceKm(currentUser.getMaxDistanceKm())
                .ageRange(currentUser.getMinAge(), currentUser.getMaxAge())
                .photoUrls(currentUser.getPhotoUrls())
                .state(currentUser.getState())
                .updatedAt(currentUser.getUpdatedAt())
                .interests(currentUser.getInterests())
                .smoking(currentUser.getSmoking())
                .drinking(currentUser.getDrinking())
                .wantsKids(currentUser.getWantsKids())
                .lookingFor(currentUser.getLookingFor())
                .education(currentUser.getEducation())
                .heightCm(currentUser.getHeightCm())
                .email(currentUser.getEmail())
                .phone(currentUser.getPhone())
                .verified(currentUser.isVerified())
                .verificationMethod(currentUser.getVerificationMethod())
                .verificationCode(currentUser.getVerificationCode())
                .verificationSentAt(currentUser.getVerificationSentAt())
                .verifiedAt(currentUser.getVerifiedAt())
                .pacePreferences(currentUser.getPacePreferences())
                .build();
    }

    private void applyBasicFields(User user, DraftState state, Consumer<String> warningSink) {
        user.setBio(state.bio());
        if (state.gender() != null) {
            user.setGender(state.gender());
        }
        user.setInterestedIn(state.interestedIn());
        applyBirthDate(user, state.birthDate(), warningSink);
    }

    private void applyLocation(User user, DraftState state) {
        if (state.hasLocation()) {
            user.setLocation(state.latitude(), state.longitude());
        }
    }

    private void applyInterests(User user, DraftState state) {
        user.setInterests(state.interests());
    }

    private void applyLifestyleFields(User user, DraftState state) {
        if (state.height() != null) {
            user.setHeightCm(state.height());
        }
        if (state.smoking() != null) {
            user.setSmoking(state.smoking());
        }
        if (state.drinking() != null) {
            user.setDrinking(state.drinking());
        }
        if (state.wantsKids() != null) {
            user.setWantsKids(state.wantsKids());
        }
        if (state.lookingFor() != null) {
            user.setLookingFor(state.lookingFor());
        }
    }

    private void applySearchPreferences(User user, DraftState state) {
        applyAgeRangePreference(user, state);
        applyMaxDistancePreference(user, state);
    }

    private void applyAgeRangePreference(User user, DraftState state) {
        try {
            int min = Integer.parseInt(state.minAge());
            int max = Integer.parseInt(state.maxAge());
            if (min >= config.validation().minAge()
                    && max <= config.validation().maxAge()
                    && min <= max) {
                ProfileNormalizationSupport.DiscoveryPreferences discoveryPreferences =
                        ProfileNormalizationSupport.normalizeDiscoveryPreferences(
                                config, min, max, user.getMaxDistanceKm());
                user.setAgeRange(
                        discoveryPreferences.minAge(),
                        discoveryPreferences.maxAge(),
                        config.validation().minAge(),
                        config.validation().maxAge());
            } else {
                logger.warn("Invalid age range values: {}-{}", min, max);
            }
        } catch (NumberFormatException _) {
            logger.warn("Invalid age range values");
        }
    }

    private void applyMaxDistancePreference(User user, DraftState state) {
        try {
            int dist = Integer.parseInt(state.maxDistance());
            if (dist > 0 && dist <= config.matching().maxDistanceKm()) {
                int normalized = ProfileNormalizationSupport.normalizeDiscoveryPreferences(
                                config, user.getMinAge(), user.getMaxAge(), dist)
                        .maxDistanceKm();
                user.setMaxDistanceKm(normalized, config.matching().maxDistanceKm());
            } else {
                logger.warn("Invalid max distance value: {}", dist);
            }
        } catch (NumberFormatException _) {
            logger.warn("Invalid max distance value");
        }
    }

    private void applyDealbreakers(User user, DraftState state) {
        Dealbreakers dealbreakers = state.dealbreakers();
        if (dealbreakers != null) {
            user.setDealbreakers(dealbreakers);
        }
    }

    private void applyPacePreferences(User user, DraftState state) {
        boolean anySet = state.messagingFrequency() != null
                || state.timeToFirstDate() != null
                || state.communicationStyle() != null
                || state.depthPreference() != null;
        boolean allSet = state.messagingFrequency() != null
                && state.timeToFirstDate() != null
                && state.communicationStyle() != null
                && state.depthPreference() != null;

        if (!anySet) {
            user.setPacePreferences(null);
            return;
        }

        if (!allSet) {
            throw new IllegalArgumentException("Complete all pace preferences or clear them all.");
        }

        user.setPacePreferences(new PacePreferences(
                state.messagingFrequency(),
                state.timeToFirstDate(),
                state.communicationStyle(),
                state.depthPreference()));
    }

    private void applyBirthDate(User user, LocalDate selected, Consumer<String> warningSink) {
        if (selected == null) {
            return;
        }
        LocalDate today = AppClock.today();
        if (selected.isAfter(today)) {
            logger.warn("Birth date cannot be in the future: {}", selected);
            if (warningSink != null) {
                warningSink.accept("Birth date cannot be in the future");
            }
            return;
        }
        int age = Period.between(selected, today).getYears();
        if (age < config.validation().minAge() || age > config.validation().maxAge()) {
            logger.warn("Birth date outside allowed age range: {}", selected);
            if (warningSink != null) {
                warningSink.accept("Birth date must be for ages "
                        + config.validation().minAge()
                        + "-"
                        + config.validation().maxAge());
            }
            return;
        }
        user.setBirthDate(selected);
    }

    record DraftState(
            String bio,
            LocalDate birthDate,
            Gender gender,
            Set<Gender> interestedIn,
            double latitude,
            double longitude,
            boolean hasLocation,
            Set<Interest> interests,
            Integer height,
            Lifestyle.Smoking smoking,
            Lifestyle.Drinking drinking,
            Lifestyle.WantsKids wantsKids,
            Lifestyle.LookingFor lookingFor,
            String minAge,
            String maxAge,
            String maxDistance,
            Dealbreakers dealbreakers,
            PacePreferences.MessagingFrequency messagingFrequency,
            PacePreferences.TimeToFirstDate timeToFirstDate,
            PacePreferences.CommunicationStyle communicationStyle,
            PacePreferences.DepthPreference depthPreference) {
        DraftState {
            interestedIn = interestedIn == null ? Set.of() : Set.copyOf(interestedIn);
            interests = interests == null ? Set.of() : Set.copyOf(interests);
        }
    }
}
