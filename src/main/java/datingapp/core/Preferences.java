package datingapp.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Container for user preferences such as interests and lifestyle choices. */
public final class Preferences {

    private Preferences() {
        // Utility class - prevent instantiation
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

        /** Minimum interests for "interests complete" in ProfilePreviewService. */
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

    // ================================
    // PacePreferences Record
    // ================================

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

        /** Dimensions for messaging frequency preferences. */
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

        /** Dimensions for communication style preferences. */
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

        /** Dimensions for conversation depth preferences. */
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
}
