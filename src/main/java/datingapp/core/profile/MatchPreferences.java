package datingapp.core.profile;

import datingapp.core.AppConfig;
import datingapp.core.matching.LifestyleMatcher;
import datingapp.core.model.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Container for user match preferences, interests, lifestyle choices, and
 * dealbreakers.
 */
public final class MatchPreferences {

    private MatchPreferences() {
        // Utility class - prevent instantiation
    }

    /** Returns the maximum number of interests a user can select. */
    public static int maxInterestsPerUser() {
        return Interest.MAX_PER_USER;
    }

    /**
     * Predefined interests for user profiles. Organized by category for easier
     * selection in CLI.
     *
     * <p>
     * Usage example:
     *
     * <pre>
     * Set&lt;Interest&gt; interests = EnumSet.of(Interest.HIKING, Interest.COFFEE);
     * Interest.Category cat = Interest.HIKING.getCategory(); // OUTDOORS
     * List&lt;Interest&gt; outdoorInterests = Interest.byCategory(Interest.Category.OUTDOORS);
     * </pre>
     */
    public static enum Interest {
        // ===== OUTDOORS =====
        HIKING("Hiking", Category.OUTDOORS),
        CAMPING("Camping", Category.OUTDOORS),
        FISHING("Fishing", Category.OUTDOORS),
        CYCLING("Cycling", Category.OUTDOORS),
        RUNNING("Running", Category.OUTDOORS),
        CLIMBING("Climbing", Category.OUTDOORS),

        // ===== ARTS & CULTURE =====
        MOVIES("Movies", Category.ARTS),
        MUSIC("Music", Category.ARTS),
        CONCERTS("Concerts", Category.ARTS),
        ART_GALLERIES("Art Galleries", Category.ARTS),
        THEATER("Theater", Category.ARTS),
        PHOTOGRAPHY("Photography", Category.ARTS),
        READING("Reading", Category.ARTS),
        WRITING("Writing", Category.ARTS),

        // ===== FOOD & DRINK =====
        COOKING("Cooking", Category.FOOD),
        BAKING("Baking", Category.FOOD),
        WINE("Wine", Category.FOOD),
        CRAFT_BEER("Craft Beer", Category.FOOD),
        COFFEE("Coffee", Category.FOOD),
        FOODIE("Foodie", Category.FOOD),

        // ===== SPORTS & FITNESS =====
        GYM("Gym", Category.SPORTS),
        YOGA("Yoga", Category.SPORTS),
        BASKETBALL("Basketball", Category.SPORTS),
        SOCCER("Soccer", Category.SPORTS),
        TENNIS("Tennis", Category.SPORTS),
        SWIMMING("Swimming", Category.SPORTS),
        GOLF("Golf", Category.SPORTS),

        // ===== GAMES & TECH =====
        VIDEO_GAMES("Video Games", Category.TECH),
        BOARD_GAMES("Board Games", Category.TECH),
        CODING("Coding", Category.TECH),
        TECH("Tech", Category.TECH),
        PODCASTS("Podcasts", Category.TECH),

        // ===== SOCIAL =====
        TRAVEL("Travel", Category.SOCIAL),
        DANCING("Dancing", Category.SOCIAL),
        VOLUNTEERING("Volunteering", Category.SOCIAL),
        PETS("Pets", Category.SOCIAL),
        DOGS("Dogs", Category.SOCIAL),
        CATS("Cats", Category.SOCIAL),
        NIGHTLIFE("Nightlife", Category.SOCIAL);

        /**
         * Maximum number of interests a user can select. Enforced in
         * User.setInterests().
         */
        public static final int MAX_PER_USER = 10;

        /** Minimum interests for "interests complete" in ProfileService. */
        public static final int MIN_FOR_COMPLETE = 3;

        /** Interest categories for organized display. */
        public static enum Category {
            OUTDOORS("🏕️ Outdoors"),
            ARTS("🎨 Arts & Culture"),
            FOOD("🍳 Food & Drink"),
            SPORTS("🏃 Sports & Fitness"),
            TECH("🎮 Games & Tech"),
            SOCIAL("🎉 Social");

            private final String displayName;

            Category(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        private final String displayName;
        private final Category category;

        Interest(String displayName, Category category) {
            this.displayName = Objects.requireNonNull(displayName);
            this.category = Objects.requireNonNull(category);
        }

        public String getDisplayName() {
            return displayName;
        }

        public Category getCategory() {
            return category;
        }

        /**
         * Returns all interests in the given category.
         *
         * @param category the category to filter by
         * @return list of interests (never null, may be empty)
         */
        public static List<Interest> byCategory(Category category) {
            if (category == null) {
                return List.of();
            }
            return Arrays.stream(values()).filter(i -> i.category == category).toList();
        }

        /** Returns total count of available interests. */
        public static int count() {
            return values().length;
        }
    }

    /**
     * Container for lifestyle-related enums. Used for profile data and dealbreaker
     * filtering.
     */
    public static final class Lifestyle {

        private Lifestyle() {
            // Utility class - prevent instantiation
        }

        /** Smoking habits. */
        public static enum Smoking {
            NEVER("Never"),
            SOMETIMES("Sometimes"),
            REGULARLY("Regularly");

            private final String displayName;

            Smoking(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        /** Drinking habits. */
        public static enum Drinking {
            NEVER("Never"),
            SOCIALLY("Socially"),
            REGULARLY("Regularly");

            private final String displayName;

            Drinking(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        /** Stance on having children. */
        public static enum WantsKids {
            NO("Don't want"),
            OPEN("Open to it"),
            SOMEDAY("Want someday"),
            HAS_KIDS("Have kids");

            private final String displayName;

            WantsKids(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        /** Relationship goals. */
        public static enum LookingFor {
            CASUAL("Something casual"),
            SHORT_TERM("Short-term dating"),
            LONG_TERM("Long-term relationship"),
            MARRIAGE("Marriage"),
            UNSURE("Not sure yet");

            private final String displayName;

            LookingFor(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        /** Education level. */
        public static enum Education {
            HIGH_SCHOOL("High school"),
            SOME_COLLEGE("Some college"),
            BACHELORS("Bachelor's degree"),
            MASTERS("Master's degree"),
            PHD("PhD/Doctorate"),
            TRADE_SCHOOL("Trade school"),
            OTHER("Other");

            private final String displayName;

            Education(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }
    }

    /** Consolidates communication and dating pace preference enums and record. */
    public static record PacePreferences(
            MessagingFrequency messagingFrequency,
            TimeToFirstDate timeToFirstDate,
            CommunicationStyle communicationStyle,
            DepthPreference depthPreference) {

        public PacePreferences {
            boolean anySet = messagingFrequency != null
                    || timeToFirstDate != null
                    || communicationStyle != null
                    || depthPreference != null;
            boolean anyMissing = messagingFrequency == null
                    || timeToFirstDate == null
                    || communicationStyle == null
                    || depthPreference == null;

            if (anySet && anyMissing) {
                throw new IllegalArgumentException("PacePreferences must be all set or all null");
            }
        }

        public boolean isComplete() {
            return messagingFrequency != null
                    && timeToFirstDate != null
                    && communicationStyle != null
                    && depthPreference != null;
        }

        /** Dimensions for messaging frequency MatchPreferences. */
        public static enum MessagingFrequency {
            RARELY("Rarely"),
            OFTEN("Often"),
            CONSTANTLY("Constantly"),
            WILDCARD("No preference");

            private final String displayName;

            MessagingFrequency(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        /** Dimensions for how soon a user wants to go on a first date. */
        public static enum TimeToFirstDate {
            QUICKLY("Quickly (1-2 days)"),
            FEW_DAYS("A few days"),
            WEEKS("Weeks"),
            MONTHS("Months"),
            WILDCARD("No preference");

            private final String displayName;

            TimeToFirstDate(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        /** Dimensions for communication style MatchPreferences. */
        public static enum CommunicationStyle {
            TEXT_ONLY("Text only"),
            VOICE_NOTES("Voice notes"),
            VIDEO_CALLS("Video calls"),
            IN_PERSON_ONLY("In person only"),
            MIX_OF_EVERYTHING("Mix of everything");

            private final String displayName;

            CommunicationStyle(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }

        /** Dimensions for conversation depth MatchPreferences. */
        public static enum DepthPreference {
            SMALL_TALK("Small talk"),
            DEEP_CHAT("Deep chat"),
            EXISTENTIAL("Existential exploration"),
            DEPENDS_ON_VIBE("Depends on the vibe");

            private final String displayName;

            DepthPreference(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return displayName;
            }
        }
    }

    /**
     * Immutable record representing a user's hard filters (dealbreakers).
     * Candidates who don't meet
     * these criteria will be excluded from matching.
     *
     * <p>
     * Dealbreakers are one-way: if I have a dealbreaker against smokers, I won't
     * see smokers, but
     * smokers can still see me (unless they have their own dealbreakers).
     */
    public static record Dealbreakers(

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

        private static final AppConfig CONFIG = AppConfig.defaults();

        // Compact constructor - validates and creates defensive copies
        public Dealbreakers {
            // Defensive copies - null becomes empty set
            acceptableSmoking = Collections.unmodifiableSet(
                    acceptableSmoking != null && !acceptableSmoking.isEmpty()
                            ? EnumSet.copyOf(acceptableSmoking)
                            : EnumSet.noneOf(Lifestyle.Smoking.class));
            acceptableDrinking = Collections.unmodifiableSet(
                    acceptableDrinking != null && !acceptableDrinking.isEmpty()
                            ? EnumSet.copyOf(acceptableDrinking)
                            : EnumSet.noneOf(Lifestyle.Drinking.class));
            acceptableKidsStance = Collections.unmodifiableSet(
                    acceptableKidsStance != null && !acceptableKidsStance.isEmpty()
                            ? EnumSet.copyOf(acceptableKidsStance)
                            : EnumSet.noneOf(Lifestyle.WantsKids.class));
            acceptableLookingFor = Collections.unmodifiableSet(
                    acceptableLookingFor != null && !acceptableLookingFor.isEmpty()
                            ? EnumSet.copyOf(acceptableLookingFor)
                            : EnumSet.noneOf(Lifestyle.LookingFor.class));
            acceptableEducation = Collections.unmodifiableSet(
                    acceptableEducation != null && !acceptableEducation.isEmpty()
                            ? EnumSet.copyOf(acceptableEducation)
                            : EnumSet.noneOf(Lifestyle.Education.class));

            // Validate height range using configured bounds
            if (minHeightCm != null && minHeightCm < CONFIG.minHeightCm()) {
                throw new IllegalArgumentException("minHeightCm too low: " + minHeightCm);
            }
            if (maxHeightCm != null && maxHeightCm > CONFIG.maxHeightCm()) {
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
         * Creates a Builder pre-populated with this record's values. Enables partial
         * updates without
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
            private final Set<Lifestyle.Smoking> smoking = EnumSet.noneOf(Lifestyle.Smoking.class);
            private final Set<Lifestyle.Drinking> drinking = EnumSet.noneOf(Lifestyle.Drinking.class);
            private final Set<Lifestyle.WantsKids> kids = EnumSet.noneOf(Lifestyle.WantsKids.class);
            private final Set<Lifestyle.LookingFor> lookingFor = EnumSet.noneOf(Lifestyle.LookingFor.class);
            private final Set<Lifestyle.Education> education = EnumSet.noneOf(Lifestyle.Education.class);
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
                return new Dealbreakers(
                        smoking, drinking, kids, lookingFor, education, minHeight, maxHeight, maxAgeDiff);
            }
        }

        /**
         * Evaluates whether a candidate passes all of a seeker's dealbreakers.
         * Stateless utility class -
         * all methods are static.
         *
         * <p>
         * Design decision: Missing candidate fields FAIL dealbreakers. This encourages
         * profile
         * completion and is the safer default.
         */
        public static final class Evaluator {

            private Evaluator() {
                // Prevent instantiation - all methods are static
            }

            public static boolean passes(User seeker, User candidate) {
                Dealbreakers db = seeker.getDealbreakers();

                return !db.hasAnyDealbreaker()
                        || (passesSmoking(db, candidate)
                                && passesDrinking(db, candidate)
                                && passesKids(db, candidate)
                                && passesLookingFor(db, candidate)
                                && passesEducation(db, candidate)
                                && passesHeight(db, candidate)
                                && passesAgeDifference(db, seeker, candidate));
            }

            /**
             * Get a list of which dealbreakers a candidate fails (for debugging/display).
             *
             * @param seeker    The user looking for matches
             * @param candidate The potential match
             * @return List of human-readable failure descriptions
             */
            public static List<String> getFailedDealbreakers(User seeker, User candidate) {
                List<String> failures = new ArrayList<>();
                Dealbreakers db = seeker.getDealbreakers();

                addSmokingFailure(db, candidate, failures);
                addDrinkingFailure(db, candidate, failures);
                addKidsFailure(db, candidate, failures);
                addLookingForFailure(db, candidate, failures);
                addEducationFailure(db, candidate, failures);
                addHeightFailure(db, candidate, failures);
                addAgeFailure(db, seeker, candidate, failures);

                return failures;
            }

            private static boolean passesSmoking(Dealbreakers db, User candidate) {
                return !db.hasSmokingDealbreaker()
                        || LifestyleMatcher.isAcceptable(candidate.getSmoking(), db.acceptableSmoking());
            }

            private static boolean passesDrinking(Dealbreakers db, User candidate) {
                return !db.hasDrinkingDealbreaker()
                        || LifestyleMatcher.isAcceptable(candidate.getDrinking(), db.acceptableDrinking());
            }

            private static boolean passesKids(Dealbreakers db, User candidate) {
                return !db.hasKidsDealbreaker()
                        || LifestyleMatcher.isAcceptable(candidate.getWantsKids(), db.acceptableKidsStance());
            }

            private static boolean passesLookingFor(Dealbreakers db, User candidate) {
                return !db.hasLookingForDealbreaker()
                        || LifestyleMatcher.isAcceptable(candidate.getLookingFor(), db.acceptableLookingFor());
            }

            private static boolean passesEducation(Dealbreakers db, User candidate) {
                return !db.hasEducationDealbreaker()
                        || LifestyleMatcher.isAcceptable(candidate.getEducation(), db.acceptableEducation());
            }

            private static boolean passesHeight(Dealbreakers db, User candidate) {
                if (!db.hasHeightDealbreaker()) {
                    return true;
                }
                Integer candidateHeight = candidate.getHeightCm();
                if (candidateHeight == null) {
                    return false;
                }
                Integer minHeight = db.minHeightCm();
                if (minHeight != null && candidateHeight < minHeight) {
                    return false;
                }
                Integer maxHeight = db.maxHeightCm();
                return maxHeight == null || candidateHeight <= maxHeight;
            }

            private static boolean passesAgeDifference(Dealbreakers db, User seeker, User candidate) {
                if (!db.hasAgeDealbreaker()) {
                    return true;
                }
                int seekerAge = seeker.getAge();
                int candidateAge = candidate.getAge();
                if (seekerAge <= 0 || candidateAge <= 0) {
                    return true;
                }
                return Math.abs(seekerAge - candidateAge) <= db.maxAgeDifference();
            }

            private static void addSmokingFailure(Dealbreakers db, User candidate, List<String> failures) {
                if (!db.hasSmokingDealbreaker()) {
                    return;
                }
                Lifestyle.Smoking smoking = candidate.getSmoking();
                if (!LifestyleMatcher.isAcceptable(smoking, db.acceptableSmoking())) {
                    failures.add(
                            smoking == null ? "Smoking status not specified" : "Smoking: " + smoking.getDisplayName());
                }
            }

            private static void addDrinkingFailure(Dealbreakers db, User candidate, List<String> failures) {
                if (!db.hasDrinkingDealbreaker()) {
                    return;
                }
                Lifestyle.Drinking drinking = candidate.getDrinking();
                if (!LifestyleMatcher.isAcceptable(drinking, db.acceptableDrinking())) {
                    failures.add(
                            drinking == null
                                    ? "Drinking status not specified"
                                    : "Drinking: " + drinking.getDisplayName());
                }
            }

            private static void addKidsFailure(Dealbreakers db, User candidate, List<String> failures) {
                if (!db.hasKidsDealbreaker()) {
                    return;
                }
                Lifestyle.WantsKids wantsKids = candidate.getWantsKids();
                if (!LifestyleMatcher.isAcceptable(wantsKids, db.acceptableKidsStance())) {
                    failures.add(
                            wantsKids == null ? "Kids stance not specified" : "Kids: " + wantsKids.getDisplayName());
                }
            }

            private static void addLookingForFailure(Dealbreakers db, User candidate, List<String> failures) {
                if (!db.hasLookingForDealbreaker()) {
                    return;
                }
                Lifestyle.LookingFor lookingFor = candidate.getLookingFor();
                if (!LifestyleMatcher.isAcceptable(lookingFor, db.acceptableLookingFor())) {
                    failures.add(
                            lookingFor == null
                                    ? "Relationship goal not specified"
                                    : "Looking for: " + lookingFor.getDisplayName());
                }
            }

            private static void addEducationFailure(Dealbreakers db, User candidate, List<String> failures) {
                if (!db.hasEducationDealbreaker()) {
                    return;
                }
                Lifestyle.Education education = candidate.getEducation();
                if (!LifestyleMatcher.isAcceptable(education, db.acceptableEducation())) {
                    failures.add(
                            education == null ? "Education not specified" : "Education: " + education.getDisplayName());
                }
            }

            private static void addHeightFailure(Dealbreakers db, User candidate, List<String> failures) {
                if (!db.hasHeightDealbreaker()) {
                    return;
                }
                Integer candidateHeight = candidate.getHeightCm();
                if (candidateHeight == null) {
                    return;
                }
                Integer minHeight = db.minHeightCm();
                if (minHeight != null && candidateHeight < minHeight) {
                    failures.add("Height too short: " + candidateHeight + " cm");
                }
                Integer maxHeight = db.maxHeightCm();
                if (maxHeight != null && candidateHeight > maxHeight) {
                    failures.add("Height too tall: " + candidateHeight + " cm");
                }
            }

            private static void addAgeFailure(Dealbreakers db, User seeker, User candidate, List<String> failures) {
                if (!db.hasAgeDealbreaker()) {
                    return;
                }
                int seekerAge = seeker.getAge();
                int candidateAge = candidate.getAge();
                if (seekerAge <= 0 || candidateAge <= 0) {
                    return;
                }
                int ageDiff = Math.abs(seekerAge - candidateAge);
                if (ageDiff > db.maxAgeDifference()) {
                    failures.add("Age difference: " + ageDiff + " years (max: " + db.maxAgeDifference() + ")");
                }
            }
        }
    }
}
