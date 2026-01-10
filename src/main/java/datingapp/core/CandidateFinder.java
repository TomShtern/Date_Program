package datingapp.core;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Finds candidate users for matching based on preferences and filters. Pure Java - no framework
 * dependencies.
 */
public class CandidateFinder implements CandidateFinderService {

  private final DealbreakersEvaluator dealbreakersEvaluator;

  public CandidateFinder() {
    this.dealbreakersEvaluator = new DealbreakersEvaluator();
  }

  /**
   * Finds candidates for the given seeker from a list of active users.
   *
   * <p>Filtering rules (ALL must be true for a candidate): 1. Not self 2. Not already interacted
   * (liked/passed) 3. Mutual gender preferences (both ways) 4. Mutual age preferences (both ways)
   * 5. Within seeker's distance preference 6. Passes seeker's dealbreakers (Phase 0.5b)
   *
   * <p>Results are sorted by distance (closest first).
   */
  @Override
  public List<User> findCandidates(User seeker, List<User> allActive, Set<UUID> alreadyInteracted) {
    return allActive.stream()
        .filter(candidate -> !candidate.getId().equals(seeker.getId())) // Not self
        .filter(candidate -> candidate.getState() == User.State.ACTIVE) // Must be active
        .filter(
            candidate -> !alreadyInteracted.contains(candidate.getId())) // Not already interacted
        .filter(candidate -> hasMatchingGenderPreferences(seeker, candidate)) // Mutual gender
        .filter(candidate -> hasMatchingAgePreferences(seeker, candidate)) // Mutual age
        .filter(candidate -> isWithinDistance(seeker, candidate)) // Within distance
        .filter(
            candidate ->
                dealbreakersEvaluator.passes(seeker, candidate)) // Dealbreakers (Phase 0.5b)
        .sorted(Comparator.comparingDouble(c -> distanceTo(seeker, c))) // Sort by distance
        .collect(Collectors.toList());
  }

  /**
   * Checks if gender preferences match both ways: - Seeker is interested in candidate's gender -
   * Candidate is interested in seeker's gender
   */
  private boolean hasMatchingGenderPreferences(User seeker, User candidate) {
    if (seeker.getGender() == null || candidate.getGender() == null) {
      return false;
    }
    if (seeker.getInterestedIn() == null || candidate.getInterestedIn() == null) {
      return false;
    }

    boolean seekerInterestedInCandidate = seeker.getInterestedIn().contains(candidate.getGender());
    boolean candidateInterestedInSeeker = candidate.getInterestedIn().contains(seeker.getGender());

    return seekerInterestedInCandidate && candidateInterestedInSeeker;
  }

  /**
   * Checks if age preferences match both ways: - Candidate's age is within seeker's age range -
   * Seeker's age is within candidate's age range
   */
  private boolean hasMatchingAgePreferences(User seeker, User candidate) {
    int seekerAge = seeker.getAge();
    int candidateAge = candidate.getAge();

    if (seekerAge == 0 || candidateAge == 0) {
      return false; // Missing birth date
    }

    boolean candidateInSeekerRange =
        candidateAge >= seeker.getMinAge() && candidateAge <= seeker.getMaxAge();
    boolean seekerInCandidateRange =
        seekerAge >= candidate.getMinAge() && seekerAge <= candidate.getMaxAge();

    return candidateInSeekerRange && seekerInCandidateRange;
  }

  /** Checks if the candidate is within the seeker's max distance preference. */
  private boolean isWithinDistance(User seeker, User candidate) {
    double distance = distanceTo(seeker, candidate);
    return distance <= seeker.getMaxDistanceKm();
  }

  /** Calculates distance between two users. */
  private double distanceTo(User a, User b) {
    return GeoUtils.distanceKm(a.getLat(), a.getLon(), b.getLat(), b.getLon());
  }
}
