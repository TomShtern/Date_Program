package datingapp.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates whether a candidate passes all of a seeker's dealbreakers.
 * Pure Java - no framework dependencies.
 *
 * <p>
 * Design decision: Missing candidate fields FAIL dealbreakers.
 * This encourages profile completion and is the safer default.
 */
public class DealbreakersEvaluator {

    /**
     * Check if candidate passes all of seeker's dealbreakers.
     *
     * @param seeker    The user looking for matches
     * @param candidate The potential match
     * @return true if candidate passes all dealbreakers, false if any fails
     */
    public boolean passes(User seeker, User candidate) {
        Dealbreakers db = seeker.getDealbreakers();

        // No dealbreakers set = everyone passes
        if (!db.hasAnyDealbreaker()) {
            return true;
        }

        // Check smoking dealbreaker
        if (db.hasSmokingDealbreaker()) {
            if (candidate.getSmoking() == null
                    || !db.acceptableSmoking().contains(candidate.getSmoking())) {
                return false;
            }
        }

        // Check drinking dealbreaker
        if (db.hasDrinkingDealbreaker()) {
            if (candidate.getDrinking() == null
                    || !db.acceptableDrinking().contains(candidate.getDrinking())) {
                return false;
            }
        }

        // Check kids stance dealbreaker
        if (db.hasKidsDealbreaker()) {
            if (candidate.getWantsKids() == null
                    || !db.acceptableKidsStance().contains(candidate.getWantsKids())) {
                return false;
            }
        }

        // Check looking for dealbreaker
        if (db.hasLookingForDealbreaker()) {
            if (candidate.getLookingFor() == null
                    || !db.acceptableLookingFor().contains(candidate.getLookingFor())) {
                return false;
            }
        }

        // Check education dealbreaker
        if (db.hasEducationDealbreaker()) {
            if (candidate.getEducation() == null
                    || !db.acceptableEducation().contains(candidate.getEducation())) {
                return false;
            }
        }

        // Check height dealbreaker
        // Note: If candidate hasn't set height but seeker has height dealbreaker, we
        // PASS
        // (height is optional, don't exclude people who haven't entered it)
        if (db.hasHeightDealbreaker() && candidate.getHeightCm() != null) {
            Integer candidateHeight = candidate.getHeightCm();
            if (db.minHeightCm() != null && candidateHeight < db.minHeightCm()) {
                return false;
            }
            if (db.maxHeightCm() != null && candidateHeight > db.maxHeightCm()) {
                return false;
            }
        }

        // Check age difference dealbreaker
        if (db.hasAgeDealbreaker()) {
            int seekerAge = seeker.getAge();
            int candidateAge = candidate.getAge();
            if (seekerAge > 0 && candidateAge > 0) {
                int ageDiff = Math.abs(seekerAge - candidateAge);
                if (ageDiff > db.maxAgeDifference()) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Get a list of which dealbreakers a candidate fails (for debugging/display).
     *
     * @param seeker    The user looking for matches
     * @param candidate The potential match
     * @return List of human-readable failure descriptions
     */
    public List<String> getFailedDealbreakers(User seeker, User candidate) {
        List<String> failures = new ArrayList<>();
        Dealbreakers db = seeker.getDealbreakers();

        // Smoking
        if (db.hasSmokingDealbreaker()) {
            if (candidate.getSmoking() == null) {
                failures.add("Smoking status not specified");
            } else if (!db.acceptableSmoking().contains(candidate.getSmoking())) {
                failures.add("Smoking: " + candidate.getSmoking().getDisplayName());
            }
        }

        // Drinking
        if (db.hasDrinkingDealbreaker()) {
            if (candidate.getDrinking() == null) {
                failures.add("Drinking status not specified");
            } else if (!db.acceptableDrinking().contains(candidate.getDrinking())) {
                failures.add("Drinking: " + candidate.getDrinking().getDisplayName());
            }
        }

        // Kids stance
        if (db.hasKidsDealbreaker()) {
            if (candidate.getWantsKids() == null) {
                failures.add("Kids stance not specified");
            } else if (!db.acceptableKidsStance().contains(candidate.getWantsKids())) {
                failures.add("Kids: " + candidate.getWantsKids().getDisplayName());
            }
        }

        // Looking for
        if (db.hasLookingForDealbreaker()) {
            if (candidate.getLookingFor() == null) {
                failures.add("Relationship goal not specified");
            } else if (!db.acceptableLookingFor().contains(candidate.getLookingFor())) {
                failures.add("Looking for: " + candidate.getLookingFor().getDisplayName());
            }
        }

        // Education
        if (db.hasEducationDealbreaker()) {
            if (candidate.getEducation() == null) {
                failures.add("Education not specified");
            } else if (!db.acceptableEducation().contains(candidate.getEducation())) {
                failures.add("Education: " + candidate.getEducation().getDisplayName());
            }
        }

        // Height
        if (db.hasHeightDealbreaker() && candidate.getHeightCm() != null) {
            Integer candidateHeight = candidate.getHeightCm();
            if (db.minHeightCm() != null && candidateHeight < db.minHeightCm()) {
                failures.add("Height too short: " + candidateHeight + " cm");
            }
            if (db.maxHeightCm() != null && candidateHeight > db.maxHeightCm()) {
                failures.add("Height too tall: " + candidateHeight + " cm");
            }
        }

        // Age difference
        if (db.hasAgeDealbreaker()) {
            int seekerAge = seeker.getAge();
            int candidateAge = candidate.getAge();
            if (seekerAge > 0 && candidateAge > 0) {
                int ageDiff = Math.abs(seekerAge - candidateAge);
                if (ageDiff > db.maxAgeDifference()) {
                    failures.add("Age difference: " + ageDiff + " years (max: " + db.maxAgeDifference() + ")");
                }
            }
        }

        return failures;
    }
}
