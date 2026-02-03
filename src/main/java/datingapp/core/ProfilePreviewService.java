package datingapp.core;

import datingapp.core.Preferences.Interest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service for generating profile previews. Shows users how their profile
 * appears to others, with
 * completeness scoring and improvement tips.
 */
public class ProfilePreviewService {

    /** Result of profile completeness calculation. */
    public static record ProfileCompleteness(int percentage, List<String> filledFields, List<String> missingFields) {

        public ProfileCompleteness {
            if (percentage < 0 || percentage > 100) {
                throw new IllegalArgumentException("percentage must be 0-100, got: " + percentage);
            }
            filledFields = filledFields != null ? List.copyOf(filledFields) : List.of();
            missingFields = missingFields != null ? List.copyOf(missingFields) : List.of();
        }
    }

    /** Full profile preview result. */
    public static record ProfilePreview(
            User user,
            ProfileCompleteness completeness,
            List<String> improvementTips,
            String displayBio,
            String displayLookingFor) {

        public ProfilePreview {
            Objects.requireNonNull(user);
            Objects.requireNonNull(completeness);
            improvementTips = improvementTips != null ? List.copyOf(improvementTips) : List.of();
            displayBio = displayBio != null ? displayBio.trim() : null;
            displayLookingFor = displayLookingFor != null ? displayLookingFor.trim() : null;
        }
    }

    /** Generate a complete profile preview for a user. */
    public ProfilePreview generatePreview(User user) {
        Objects.requireNonNull(user, "user cannot be null");

        ProfileCompleteness completeness = calculateCompleteness(user);
        List<String> tips = generateTips(user);
        String displayBio = user.getBio() != null ? user.getBio() : "(no bio)";
        String displayLookingFor =
                user.getLookingFor() != null ? user.getLookingFor().getDisplayName() : null;

        return new ProfilePreview(user, completeness, tips, displayBio, displayLookingFor);
    }

    /** Calculate how complete a user's profile is. */
    public ProfileCompleteness calculateCompleteness(User user) {
        List<String> filled = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // Required fields (core profile)
        checkField("Name", user.getName() != null && !user.getName().isBlank(), filled, missing);
        checkField("Bio", user.getBio() != null && !user.getBio().isBlank(), filled, missing);
        checkField("Birth Date", user.getBirthDate() != null, filled, missing);
        checkField("Gender", user.getGender() != null, filled, missing);
        checkField(
                "Interested In",
                user.getInterestedIn() != null && !user.getInterestedIn().isEmpty(),
                filled,
                missing);
        checkField("Location", user.getLat() != 0.0 || user.getLon() != 0.0, filled, missing);
        checkField("Photo", !user.getPhotoUrls().isEmpty(), filled, missing);

        // Lifestyle fields (optional but encouraged)
        checkField("Height", user.getHeightCm() != null, filled, missing);
        checkField("Smoking", user.getSmoking() != null, filled, missing);
        checkField("Drinking", user.getDrinking() != null, filled, missing);
        checkField("Kids Stance", user.getWantsKids() != null, filled, missing);
        checkField("Looking For", user.getLookingFor() != null, filled, missing);
        checkField("Interests", user.getInterests().size() >= Interest.MIN_FOR_COMPLETE, filled, missing);

        int total = filled.size() + missing.size();
        int percentage = total > 0 ? filled.size() * 100 / total : 0;

        return new ProfileCompleteness(percentage, filled, missing);
    }

    private void checkField(String fieldName, boolean isFilled, List<String> filled, List<String> missing) {
        if (isFilled) {
            filled.add(fieldName);
        } else {
            missing.add(fieldName);
        }
    }

    /** Generate improvement tips based on profile state. */
    public List<String> generateTips(User user) {
        List<String> tips = new ArrayList<>();

        // Bio tips
        if (user.getBio() == null || user.getBio().isBlank()) {
            tips.add("📝 Add a bio to tell others about yourself");
        } else if (user.getBio().length() < 50) {
            tips.add("💡 Expand your bio - profiles with 100+ chars get 2x more likes");
        }

        // Photo tips
        if (user.getPhotoUrls().isEmpty()) {
            tips.add("📸 Add a photo - it's required for browsing");
        } else if (user.getPhotoUrls().size() < 2) {
            tips.add("📸 Add a second photo - users with 2 photos get 40% more matches");
        }

        // Lifestyle tips
        if (user.getLookingFor() == null) {
            tips.add("💝 Share what you're looking for - helps find compatible matches");
        }

        if (user.getHeightCm() == null) {
            tips.add("📏 Add your height - many users filter by height");
        }

        int lifestyleFieldsSet = countLifestyleFields(user);
        if (lifestyleFieldsSet < 3) {
            tips.add("🧘 Complete more lifestyle fields for better match quality");
        }

        // Distance tips
        if (user.getMaxDistanceKm() < 10) {
            tips.add("📍 Consider expanding your distance for more options");
        }

        // Age range tips
        if (user.getMaxAge() - user.getMinAge() < 5) {
            tips.add("🎂 A wider age range gives you more potential matches");
        }

        // Interest tips
        int interestCount = user.getInterests().size();
        if (interestCount == 0) {
            tips.add("🎯 Add at least "
                    + Interest.MIN_FOR_COMPLETE
                    + " interests - profiles with shared interests get more matches");
        } else if (interestCount < Interest.MIN_FOR_COMPLETE) {
            int needed = Interest.MIN_FOR_COMPLETE - interestCount;
            tips.add("🎯 Add " + needed + " more interest(s) to complete your profile");
        }

        return tips;
    }

    private int countLifestyleFields(User user) {
        int count = 0;
        if (user.getSmoking() != null) {
            count++;
        }
        if (user.getDrinking() != null) {
            count++;
        }
        if (user.getWantsKids() != null) {
            count++;
        }
        if (user.getLookingFor() != null) {
            count++;
        }
        if (user.getHeightCm() != null) {
            count++;
        }
        return count;
    }

    /** Render a text-based progress bar. */
    public static String renderProgressBar(double fraction, int width) {
        int filled = (int) (fraction * width);
        int empty = width - filled;
        return "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }
}
