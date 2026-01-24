package datingapp.core;

import datingapp.core.Preferences.Lifestyle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Immutable record representing a user's hard filters (dealbreakers). Candidates who don't meet
 * these criteria will be excluded from matching.
 *
 * <p>Dealbreakers are one-way: if I have a dealbreaker against smokers, I won't see smokers, but
 * smokers can still see me (unless they have their own dealbreakers).
 */
public record Dealbreakers(

        // Lifestyle dealbreakers - empty Set means "no preference"
        Set<Lifestyle.Smoking> acceptableSmoking,
        Set<Lifestyle.Drinking> acceptableDrinking,
        Set<Lifestyle.WantsKids> acceptableKidsStance,
        Set<Lifestyle.LookingFor> acceptableLookingFor,
        Set<Lifestyle.Education> acceptableEducation,

        // Physical dealbreakers
        Integer minHeightCm, // null = no minimum
        Integer maxHeightCm, // null = no maximum

        // Age dealbreaker (stricter than preference)
        Integer maxAgeDifference // null = use standard age preference
        ) {

    /** Compact constructor with validation and defensive copies. */
    public Dealbreakers {
        // Defensive copies - null becomes empty set
        acceptableSmoking = acceptableSmoking == null ? Set.of() : Set.copyOf(acceptableSmoking);
        acceptableDrinking = acceptableDrinking == null ? Set.of() : Set.copyOf(acceptableDrinking);
        acceptableKidsStance = acceptableKidsStance == null ? Set.of() : Set.copyOf(acceptableKidsStance);
        acceptableLookingFor = acceptableLookingFor == null ? Set.of() : Set.copyOf(acceptableLookingFor);
        acceptableEducation = acceptableEducation == null ? Set.of() : Set.copyOf(acceptableEducation);

        // Validate height range
        if (minHeightCm != null && minHeightCm < 100) {
            throw new IllegalArgumentException("minHeightCm too low: " + minHeightCm);
        }
        if (maxHeightCm != null && maxHeightCm > 250) {
            throw new IllegalArgumentException("maxHeightCm too high: " + maxHeightCm);
        }
        if (minHeightCm != null && maxHeightCm != null && minHeightCm > maxHeightCm) {
            throw new IllegalArgumentException("minHeightCm > maxHeightCm");
        }

        // Validate age difference
        if (maxAgeDifference != null && maxAgeDifference < 0) {
            throw new IllegalArgumentException("maxAgeDifference cannot be negative");
        }
    }

    /** Factory for no dealbreakers (accepts everyone). */
    public static Dealbreakers none() {
        return new Dealbreakers(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), null, null, null);
    }

    /** Builder for constructing dealbreakers. */
    public static Builder builder() {
        return new Builder();
    }

    // Convenience methods to check if specific dealbreakers are active

    public boolean hasSmokingDealbreaker() {
        return !acceptableSmoking.isEmpty();
    }

    public boolean hasDrinkingDealbreaker() {
        return !acceptableDrinking.isEmpty();
    }

    public boolean hasKidsDealbreaker() {
        return !acceptableKidsStance.isEmpty();
    }

    public boolean hasLookingForDealbreaker() {
        return !acceptableLookingFor.isEmpty();
    }

    public boolean hasEducationDealbreaker() {
        return !acceptableEducation.isEmpty();
    }

    public boolean hasHeightDealbreaker() {
        return minHeightCm != null || maxHeightCm != null;
    }

    public boolean hasAgeDealbreaker() {
        return maxAgeDifference != null;
    }

    /** Check if any dealbreaker is set. */
    public boolean hasAnyDealbreaker() {
        return hasSmokingDealbreaker()
                || hasDrinkingDealbreaker()
                || hasKidsDealbreaker()
                || hasLookingForDealbreaker()
                || hasEducationDealbreaker()
                || hasHeightDealbreaker()
                || hasAgeDealbreaker();
    }

    /**
     * Creates a Builder pre-populated with this record's values. Enables partial updates without
     * copying every field manually.
     *
     * @return a new Builder with all current values
     */
    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.smoking.addAll(this.acceptableSmoking);
        builder.drinking.addAll(this.acceptableDrinking);
        builder.kids.addAll(this.acceptableKidsStance);
        builder.lookingFor.addAll(this.acceptableLookingFor);
        builder.education.addAll(this.acceptableEducation);
        builder.minHeight = this.minHeightCm;
        builder.maxHeight = this.maxHeightCm;
        builder.maxAgeDiff = this.maxAgeDifference;
        return builder;
    }

    /** Builder for fluent construction of Dealbreakers. */
    public static class Builder {
        private final Set<Lifestyle.Smoking> smoking = new HashSet<>();
        private final Set<Lifestyle.Drinking> drinking = new HashSet<>();
        private final Set<Lifestyle.WantsKids> kids = new HashSet<>();
        private final Set<Lifestyle.LookingFor> lookingFor = new HashSet<>();
        private final Set<Lifestyle.Education> education = new HashSet<>();
        private Integer minHeight = null;
        private Integer maxHeight = null;
        private Integer maxAgeDiff = null;

        public Builder acceptSmoking(Lifestyle.Smoking... values) {
            smoking.addAll(Arrays.asList(values));
            return this;
        }

        public Builder acceptDrinking(Lifestyle.Drinking... values) {
            drinking.addAll(Arrays.asList(values));
            return this;
        }

        public Builder acceptKidsStance(Lifestyle.WantsKids... values) {
            kids.addAll(Arrays.asList(values));
            return this;
        }

        public Builder acceptLookingFor(Lifestyle.LookingFor... values) {
            lookingFor.addAll(Arrays.asList(values));
            return this;
        }

        public Builder requireEducation(Lifestyle.Education... values) {
            education.addAll(Arrays.asList(values));
            return this;
        }

        public Builder heightRange(Integer min, Integer max) {
            this.minHeight = min;
            this.maxHeight = max;
            return this;
        }

        public Builder minHeight(Integer min) {
            this.minHeight = min;
            return this;
        }

        public Builder maxHeight(Integer max) {
            this.maxHeight = max;
            return this;
        }

        public Builder maxAgeDifference(int years) {
            this.maxAgeDiff = years;
            return this;
        }

        // Clear methods for replacement semantics
        public Builder clearSmoking() {
            smoking.clear();
            return this;
        }

        public Builder clearDrinking() {
            drinking.clear();
            return this;
        }

        public Builder clearKids() {
            kids.clear();
            return this;
        }

        public Builder clearLookingFor() {
            lookingFor.clear();
            return this;
        }

        public Builder clearEducation() {
            education.clear();
            return this;
        }

        public Builder clearHeight() {
            minHeight = null;
            maxHeight = null;
            return this;
        }

        public Builder clearAge() {
            maxAgeDiff = null;
            return this;
        }

        public Dealbreakers build() {
            return new Dealbreakers(smoking, drinking, kids, lookingFor, education, minHeight, maxHeight, maxAgeDiff);
        }
    }
}
