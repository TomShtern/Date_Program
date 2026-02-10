package datingapp.core.constants;

/** Shared thresholds and tuning constants for scoring and profile completion. */
public final class ScoringConstants {

    private ScoringConstants() {
        // Utility class
    }

    /** Match quality thresholds and scoring values. */
    public static final class MatchQuality {

        private MatchQuality() {
            // Utility class
        }

        public static final int STAR_EXCELLENT_THRESHOLD = 90;
        public static final int STAR_GREAT_THRESHOLD = 75;
        public static final int STAR_GOOD_THRESHOLD = 60;
        public static final int STAR_FAIR_THRESHOLD = 40;

        public static final String LABEL_EXCELLENT = "Excellent Match";
        public static final String LABEL_GREAT = "Great Match";
        public static final String LABEL_GOOD = "Good Match";
        public static final String LABEL_FAIR = "Fair Match";
        public static final String LABEL_LOW = "Low Compatibility";

        public static final int SUMMARY_MAX_LENGTH = 40;
        public static final int SUMMARY_TRUNCATE_LENGTH = 37;
        public static final int HIGHLIGHT_MAX_COUNT = 5;

        public static final int SHARED_INTERESTS_PREVIEW_COUNT = 3;

        public static final int VERY_CLOSE_DISTANCE_KM = 1;
        public static final int NEARBY_DISTANCE_KM = 5;
        public static final int MID_DISTANCE_KM = 15;

        public static final int AGE_SIMILAR_YEARS = 2;
        public static final int QUICK_MUTUAL_INTEREST_HOURS = 24;

        public static final double NEUTRAL_SCORE = 0.5;
        public static final double INTEREST_MISSING_SCORE = 0.3;

        public static final double PACE_SYNC_PERFECT = 0.95;
        public static final double PACE_SYNC_GOOD = 0.8;
        public static final double PACE_SYNC_FAIR = 0.6;
        public static final double PACE_SYNC_LAG = 0.4;

        public static final double RESPONSE_SCORE_EXCELLENT = 1.0;
        public static final double RESPONSE_SCORE_GREAT = 0.9;
        public static final double RESPONSE_SCORE_GOOD = 0.7;
        public static final double RESPONSE_SCORE_OK = 0.5;
        public static final double RESPONSE_SCORE_LOW = 0.3;
        public static final double RESPONSE_SCORE_VERY_LOW = 0.1;

        public static final int WILDCARD_SCORE = 20;
        public static final int PACE_SCORE_EXACT = 25;
        public static final int PACE_SCORE_CLOSE = 15;
        public static final int PACE_SCORE_FAR = 5;
    }

    /** Profile completion thresholds and scoring values. */
    public static final class ProfileCompletion {

        private ProfileCompletion() {
            // Utility class
        }

        public static final int BIO_TIP_MIN_LENGTH = 50;
        public static final int BIO_TIP_BOOST_LENGTH = 100;
        public static final int PHOTO_TIP_MIN_COUNT = 2;
        public static final int LIFESTYLE_FIELDS_MIN = 3;
        public static final int DISTANCE_TIP_MAX_KM = 10;
        public static final int AGE_RANGE_TIP_MIN_YEARS = 5;
        public static final int NEXT_STEPS_MAX = 3;

        public static final int BASIC_NAME_POINTS = 5;
        public static final int BASIC_BIO_POINTS = 10;
        public static final int BASIC_BIRTHDATE_POINTS = 5;
        public static final int BASIC_GENDER_POINTS = 5;
        public static final int BASIC_INTERESTED_POINTS = 5;
        public static final int BASIC_PHOTO_POINTS = 10;

        public static final int INTERESTS_TOTAL_POINTS = 20;
        public static final int INTERESTS_FULL_COUNT = 5;
        public static final int INTERESTS_PARTIAL_HIGH_COUNT = 3;
        public static final int INTERESTS_PARTIAL_HIGH_POINTS = 15;
        public static final int INTERESTS_PARTIAL_LOW_POINTS = 10;

        public static final int LIFESTYLE_FIELD_POINTS = 5;
        public static final int PREFERENCES_FIELD_POINTS = 5;

        public static final int TIER_DIAMOND_THRESHOLD = 95;
        public static final int TIER_GOLD_THRESHOLD = 85;
        public static final int TIER_SILVER_THRESHOLD = 70;
        public static final int TIER_BRONZE_THRESHOLD = 50;

        public static final String TIER_DIAMOND = "Diamond";
        public static final String TIER_GOLD = "Gold";
        public static final String TIER_SILVER = "Silver";
        public static final String TIER_BRONZE = "Bronze";
        public static final String TIER_STARTER = "Starter";

        public static final String TIER_DIAMOND_EMOJI = "💎";
        public static final String TIER_GOLD_EMOJI = "🥇";
        public static final String TIER_SILVER_EMOJI = "🥈";
        public static final String TIER_BRONZE_EMOJI = "🥉";
        public static final String TIER_STARTER_EMOJI = "🌱";
    }
}
