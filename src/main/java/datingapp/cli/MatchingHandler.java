package datingapp.cli;

import datingapp.core.AchievementService;
import datingapp.core.Block;
import datingapp.core.BlockStorage;
import datingapp.core.CandidateFinderService;
import datingapp.core.DailyLimitService;
import datingapp.core.DailyPickService;
import datingapp.core.GeoUtils;
import datingapp.core.InterestMatcher;
import datingapp.core.Like;
import datingapp.core.LikeStorage;
import datingapp.core.Match;
import datingapp.core.MatchQuality;
import datingapp.core.MatchQualityService;
import datingapp.core.MatchStorage;
import datingapp.core.MatchingService;
import datingapp.core.ProfileViewStorage;
import datingapp.core.UndoService;
import datingapp.core.User;
import datingapp.core.UserAchievement;
import datingapp.core.UserStorage;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MatchingHandler {
  private static final Logger logger = LoggerFactory.getLogger(MatchingHandler.class);

  private final CandidateFinderService candidateFinderService;
  private final MatchingService matchingService;
  private final LikeStorage likeStorage;
  private final MatchStorage matchStorage;
  private final BlockStorage blockStorage;
  private final DailyLimitService dailyLimitService;
  private final DailyPickService dailyPickService;
  private final UndoService undoService;
  private final MatchQualityService matchQualityService;
  private final UserStorage userStorage;
  private final AchievementService achievementService;
  private final ProfileViewStorage profileViewStorage;
  private final UserSession userSession;
  private final InputReader inputReader;

  public MatchingHandler(
      CandidateFinderService candidateFinderService,
      MatchingService matchingService,
      LikeStorage likeStorage,
      MatchStorage matchStorage,
      BlockStorage blockStorage,
      DailyLimitService dailyLimitService,
      DailyPickService dailyPickService,
      UndoService undoService,
      MatchQualityService matchQualityService,
      UserStorage userStorage,
      AchievementService achievementService,
      ProfileViewStorage profileViewStorage,
      UserSession userSession,
      InputReader inputReader) {
    this.candidateFinderService = candidateFinderService;
    this.matchingService = matchingService;
    this.likeStorage = likeStorage;
    this.matchStorage = matchStorage;
    this.blockStorage = blockStorage;
    this.dailyLimitService = dailyLimitService;
    this.dailyPickService = dailyPickService;
    this.undoService = undoService;
    this.matchQualityService = matchQualityService;
    this.userStorage = userStorage;
    this.achievementService = achievementService;
    this.profileViewStorage = profileViewStorage;
    this.userSession = userSession;
    this.inputReader = inputReader;
  }

  public void browseCandidates() {
    if (!userSession.isLoggedIn()) {
      logger.info(CliConstants.PLEASE_SELECT_USER);
      return;
    }

    User currentUser = userSession.getCurrentUser();
    if (currentUser.getState() != User.State.ACTIVE) {
      logger.info("\n⚠️  You must be ACTIVE to browse candidates. Complete your profile first.\n");
      return;
    }

    // Check for daily pick first
    Optional<DailyPickService.DailyPick> dailyPick = dailyPickService.getDailyPick(currentUser);
    if (dailyPick.isPresent() && !dailyPickService.hasViewedDailyPick(currentUser.getId())) {
      showDailyPick(dailyPick.get(), currentUser);
    }

    logger.info("\n--- Browse Candidates ---\n");

    List<User> activeUsers = userStorage.findActive();
    Set<UUID> alreadyInteracted = likeStorage.getLikedOrPassedUserIds(currentUser.getId());
    Set<UUID> blockedUsers = blockStorage.getBlockedUserIds(currentUser.getId());

    Set<UUID> excluded = new HashSet<>(alreadyInteracted);
    excluded.addAll(blockedUsers);

    List<User> candidates =
        candidateFinderService.findCandidates(currentUser, activeUsers, excluded);

    if (candidates.isEmpty()) {
      logger.info("😔 No candidates found. Try again later!\n");
      return;
    }

    for (User candidate : candidates) {
      boolean keepBrowsing = processCandidateInteraction(candidate, currentUser);
      if (!keepBrowsing) {
        break;
      }
    }
  }

  private boolean processCandidateInteraction(User candidate, User currentUser) {
    // Record this profile view
    profileViewStorage.recordView(currentUser.getId(), candidate.getId());

    double distance =
        GeoUtils.distanceKm(
            currentUser.getLat(), currentUser.getLon(),
            candidate.getLat(), candidate.getLon());

    logger.info(CliConstants.BOX_TOP);
    logger.info(
        "│ 💝 {}{}{}, {} years old",
        candidate.getName(),
        candidate.isVerified() ? " " : "",
        candidate.isVerified() ? "✅ Verified" : "",
        candidate.getAge());
    if (logger.isInfoEnabled()) {
      logger.info("│ 📍 {} km away", String.format("%.1f", distance));
    }
    logger.info(
        CliConstants.PROFILE_BIO_FORMAT,
        candidate.getBio() != null ? candidate.getBio() : "(no bio)");

    // Enhanced Mutual Interests Badge
    InterestMatcher.MatchResult matchResult =
        InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());
    if (matchResult.hasSharedInterests()) {
      String badge = getMutualInterestsBadge(matchResult.sharedCount());
      logger.info(
          "│ {} {} shared interest{}: {}",
          badge,
          matchResult.sharedCount(),
          matchResult.sharedCount() > 1 ? "s" : "",
          InterestMatcher.formatSharedInterests(matchResult.shared()));
    }

    logger.info(CliConstants.BOX_BOTTOM);

    String action = inputReader.readLine("  [L]ike / [P]ass / [Q]uit browsing: ").toLowerCase();

    if (action.equals("q")) {
      logger.info("\nStopping browse.\n");
      return false;
    }

    Like.Direction direction = action.equals("l") ? Like.Direction.LIKE : Like.Direction.PASS;

    // Check daily limit before recording a like
    if (direction == Like.Direction.LIKE && !dailyLimitService.canLike(currentUser.getId())) {
      showDailyLimitReached(currentUser);
      return false;
    }

    Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
    Optional<Match> match = matchingService.recordLike(like);

    if (match.isPresent()) {
      logger.info("\n🎉🎉🎉 IT'S A MATCH! 🎉🎉🎉");
      logger.info("You and {} like each other!\n", candidate.getName());
    } else if (direction == Like.Direction.LIKE) {
      logger.info("❤️  Liked!\n");
    } else {
      logger.info("👋 Passed.\n");
    }

    if (match.isPresent()) {
      checkAndDisplayNewAchievements(currentUser);
    }

    undoService.recordSwipe(currentUser.getId(), like, match);
    promptUndo(candidate.getName(), currentUser);

    return true;
  }

  public void viewMatches() {
    if (!userSession.isLoggedIn()) {
      logger.info(CliConstants.PLEASE_SELECT_USER);
      return;
    }

    User currentUser = userSession.getCurrentUser();
    logger.info("\n" + CliConstants.SEPARATOR_LINE);
    logger.info("         YOUR MATCHES");
    logger.info(CliConstants.SEPARATOR_LINE + "\n");

    List<Match> matches = matchStorage.getActiveMatchesFor(currentUser.getId());

    if (matches.isEmpty()) {
      logger.info("😢 No matches yet. Keep swiping!\n");
      return;
    }

    logger.info("💕 You have {} active match(es):\n", matches.size());

    for (int i = 0; i < matches.size(); i++) {
      Match match = matches.get(i);
      UUID otherUserId = match.getOtherUser(currentUser.getId());
      User otherUser = userStorage.get(otherUserId);

      if (otherUser != null && logger.isInfoEnabled()) {
        MatchQuality quality = matchQualityService.computeQuality(match, currentUser.getId());
        String verifiedBadge = Boolean.TRUE.equals(otherUser.isVerified()) ? " ✅ Verified" : "";
        logger.info(
            "  {}. {} {}{}, {}         {} {}%",
            i + 1,
            quality.getStarDisplay(),
            otherUser.getName(),
            verifiedBadge,
            otherUser.getAge(),
            " ".repeat(Math.max(0, 10 - otherUser.getName().length())),
            quality.compatibilityScore());
        logger.info("     \"{}\"", quality.getShortSummary());
      }
    }

    logger.info("\n───────────────────────────────────────");
    logger.info("  [V]iew details / [U]nmatch / [B]lock / [Enter] to go back");
    String action = inputReader.readLine("\nYour choice: ").toLowerCase();

    switch (action) {
      case "v" -> viewMatchDetails(matches, currentUser);
      case "u" -> unmatchFromList(matches, currentUser);
      case "b" -> blockFromMatches(matches, currentUser);
      default -> {
        /* back */
      }
    }
  }

  private void viewMatchDetails(List<Match> matches, User currentUser) {
    String input = inputReader.readLine("Enter match number to view: ");
    try {
      int idx = Integer.parseInt(input) - 1;
      if (idx < 0 || idx >= matches.size()) {
        logger.info(CliConstants.INVALID_SELECTION);
        return;
      }

      Match match = matches.get(idx);
      UUID otherUserId = match.getOtherUser(currentUser.getId());
      User otherUser = userStorage.get(otherUserId);
      MatchQuality quality = matchQualityService.computeQuality(match, currentUser.getId());

      displayMatchQuality(otherUser, quality);

      logger.info("\n───────────────────────────────────────");
      logger.info("  (U)nmatch  (B)lock  (Enter to go back)");
      String action = inputReader.readLine("  Your choice: ").toLowerCase();

      handleMatchDetailAction(action, match, otherUser, otherUserId, currentUser);

    } catch (NumberFormatException e) {
      logger.info(CliConstants.INVALID_INPUT);
    }
  }

  private void displayMatchQuality(User otherUser, MatchQuality quality) {
    String nameUpper = otherUser.getName().toUpperCase();
    logger.info("\n" + CliConstants.SEPARATOR_LINE);
    logger.info("         MATCH WITH {}", nameUpper);
    logger.info(CliConstants.SEPARATOR_LINE + "\n");

    logger.info("  👤 {}, {}", otherUser.getName(), otherUser.getAge());
    if (otherUser.getBio() != null) {
      logger.info("  📝 {}", otherUser.getBio());
    }
    String distanceStr = String.format("%.1f", quality.distanceKm());
    logger.info("  📍 {} km away", distanceStr);

    logger.info("\n" + CliConstants.SECTION_LINE);
    logger.info("  COMPATIBILITY: {}%  {}", quality.compatibilityScore(), quality.getStarDisplay());
    logger.info("  {}", quality.getCompatibilityLabel());
    logger.info(CliConstants.SECTION_LINE);

    if (!quality.highlights().isEmpty()) {
      logger.info("\n  ✨ WHY YOU MATCHED");
      quality.highlights().forEach(h -> logger.info("  • {}", h));
    }

    displayScoreBreakdown(quality);

    if (!quality.lifestyleMatches().isEmpty()) {
      logger.info("\n  💫 LIFESTYLE ALIGNMENT");
      quality.lifestyleMatches().forEach(m -> logger.info("  • {}", m));
    }
  }

  private void displayScoreBreakdown(MatchQuality quality) {
    logger.info("\n  📊 SCORE BREAKDOWN");
    logger.info(CliConstants.SECTION_LINE);
    logger.info(
        "  Distance:      {} {}%",
        MatchQualityService.renderProgressBar(quality.distanceScore(), 12),
        (int) (quality.distanceScore() * 100));
    logger.info(
        "  Age match:     {} {}%",
        MatchQualityService.renderProgressBar(quality.ageScore(), 12),
        (int) (quality.ageScore() * 100));
    logger.info(
        "  Interests:     {} {}%",
        MatchQualityService.renderProgressBar(quality.interestScore(), 12),
        (int) (quality.interestScore() * 100));
    logger.info(
        "  Lifestyle:     {} {}%",
        MatchQualityService.renderProgressBar(quality.lifestyleScore(), 12),
        (int) (quality.lifestyleScore() * 100));
    logger.info(
        "  Response:      {} {}%",
        MatchQualityService.renderProgressBar(quality.responseScore(), 12),
        (int) (quality.responseScore() * 100));
  }

  private void handleMatchDetailAction(
      String action, Match match, User otherUser, UUID otherUserId, User currentUser) {
    if (action.equals("u")) {
      String confirm = inputReader.readLine("Unmatch with " + otherUser.getName() + "? (y/n): ");
      if (confirm.equalsIgnoreCase("y")) {
        match.unmatch(currentUser.getId());
        matchStorage.update(match);
        logger.info("✅ Unmatched with {}.\n", otherUser.getName());
      }
    } else if (action.equals("b")) {
      String confirm =
          inputReader.readLine(
              CliConstants.BLOCK_PREFIX + otherUser.getName() + CliConstants.CONFIRM_SUFFIX);
      if (confirm.equalsIgnoreCase("y")) {
        Block block = Block.create(currentUser.getId(), otherUserId);
        blockStorage.save(block);
        match.block(currentUser.getId());
        matchStorage.update(match);
        logger.info("🚫 Blocked {}. Match ended.\n", otherUser.getName());
      }
    }
  }

  private void unmatchFromList(List<Match> matches, User currentUser) {
    String input = inputReader.readLine("Enter match number to unmatch: ");
    try {
      int idx = Integer.parseInt(input) - 1;
      if (idx < 0 || idx >= matches.size()) {
        logger.info(CliConstants.INVALID_SELECTION);
        return;
      }

      Match match = matches.get(idx);
      UUID otherUserId = match.getOtherUser(currentUser.getId());
      User otherUser = userStorage.get(otherUserId);

      String confirm = inputReader.readLine("Unmatch with " + otherUser.getName() + "? (y/n): ");
      if (confirm.equalsIgnoreCase("y")) {
        match.unmatch(currentUser.getId());
        matchStorage.update(match);
        logger.info("✅ Unmatched with {}.\n", otherUser.getName());
      } else {
        logger.info(CliConstants.CANCELLED);
      }
    } catch (NumberFormatException e) {
      logger.info(CliConstants.INVALID_INPUT);
    }
  }

  private void blockFromMatches(List<Match> matches, User currentUser) {
    String input = inputReader.readLine("Enter match number to block: ");
    try {
      int idx = Integer.parseInt(input) - 1;
      if (idx < 0 || idx >= matches.size()) {
        logger.info(CliConstants.INVALID_SELECTION);
        return;
      }

      Match match = matches.get(idx);
      UUID otherUserId = match.getOtherUser(currentUser.getId());
      User otherUser = userStorage.get(otherUserId);

      String confirm =
          inputReader.readLine(
              CliConstants.BLOCK_PREFIX
                  + otherUser.getName()
                  + "? This will end your match. (y/n): ");
      if (confirm.equalsIgnoreCase("y")) {
        Block block = Block.create(currentUser.getId(), otherUserId);
        blockStorage.save(block);
        match.block(currentUser.getId());
        matchStorage.update(match);
        logger.info("🚫 Blocked {}. Match ended.\n", otherUser.getName());
      } else {
        logger.info(CliConstants.CANCELLED);
      }
    } catch (NumberFormatException e) {
      logger.info(CliConstants.INVALID_INPUT);
    }
  }

  private void showDailyLimitReached(User currentUser) {
    DailyLimitService.DailyStatus status = dailyLimitService.getStatus(currentUser.getId());
    String timeUntilReset = DailyLimitService.formatDuration(dailyLimitService.getTimeUntilReset());

    logger.info("\n" + CliConstants.SEPARATOR_LINE);
    logger.info("         💔 DAILY LIMIT REACHED");
    logger.info(CliConstants.SEPARATOR_LINE);
    logger.info("");
    logger.info("   You've used all {} likes for today!", status.likesUsed());
    logger.info("");
    logger.info("   Resets in: {}", timeUntilReset);
    logger.info("");
    logger.info("   Tips for tomorrow:");
    logger.info("   • Take time to read profiles");
    logger.info("   • Quality over quantity");
    logger.info("   • Check your matches!");
    logger.info("");
    inputReader.readLine("   [Press Enter to return to menu]");
    logger.info(CliConstants.SEPARATOR_LINE + "\n");
  }

  private void showDailyPick(DailyPickService.DailyPick pick, User currentUser) {
    logger.info("\n" + CliConstants.SEPARATOR_LINE);
    logger.info("       🎲 YOUR DAILY PICK 🎲");
    logger.info(CliConstants.SEPARATOR_LINE);
    logger.info("");
    logger.info("  ✨ {}", pick.reason());
    logger.info("");

    User candidate = pick.user();
    double distance =
        GeoUtils.distanceKm(
            currentUser.getLat(), currentUser.getLon(),
            candidate.getLat(), candidate.getLon());

    logger.info(CliConstants.BOX_TOP);
    logger.info("│ 🎁 {}, {} years old", candidate.getName(), candidate.getAge());
    if (logger.isInfoEnabled()) {
      logger.info("│ 📍 {} km away", String.format("%.1f", distance));
    }
    logger.info(
        CliConstants.PROFILE_BIO_FORMAT,
        candidate.getBio() != null ? candidate.getBio() : "(no bio)");

    InterestMatcher.MatchResult matchResult =
        InterestMatcher.compare(currentUser.getInterests(), candidate.getInterests());
    if (!matchResult.shared().isEmpty()) {
      logger.info(
          "│ ✨ You both like: {}", InterestMatcher.formatSharedInterests(matchResult.shared()));
    }

    logger.info(CliConstants.BOX_BOTTOM);
    logger.info("");
    logger.info("  This pick resets tomorrow at midnight!");
    logger.info("");

    String action = inputReader.readLine("  [L]ike / [P]ass / [S]kip for now: ").toLowerCase();

    if (action.equals("s")) {
      logger.info("  👋 You can see this pick again later today.\n");
      return;
    }

    dailyPickService.markDailyPickViewed(currentUser.getId());

    Like.Direction direction = action.equals("l") ? Like.Direction.LIKE : Like.Direction.PASS;

    if (direction == Like.Direction.LIKE && !dailyLimitService.canLike(currentUser.getId())) {
      showDailyLimitReached(currentUser);
      return;
    }

    Like like = Like.create(currentUser.getId(), candidate.getId(), direction);
    Optional<Match> match = matchingService.recordLike(like);

    if (match.isPresent()) {
      logger.info("\n🎉🎉🎉 IT'S A MATCH WITH YOUR DAILY PICK! 🎉🎉🎉\n");
    } else if (direction == Like.Direction.LIKE) {
      logger.info("❤️  Liked your daily pick!\n");
    } else {
      logger.info("👋 Passed on daily pick.\n");
    }

    undoService.recordSwipe(currentUser.getId(), like, match);
    promptUndo(candidate.getName(), currentUser);
  }

  private void promptUndo(String candidateName, User currentUser) {
    if (!undoService.canUndo(currentUser.getId())) {
      return;
    }

    int secondsLeft = undoService.getSecondsRemaining(currentUser.getId());
    String prompt = String.format("⏪ Undo last swipe? (%ds remaining) (Y/N): ", secondsLeft);
    String response = inputReader.readLine(prompt).toLowerCase();

    if (response.equals("y")) {
      UndoService.UndoResult result = undoService.undo(currentUser.getId());

      if (result.success()) {
        String directionStr =
            result.undoneSwipe().direction() == Like.Direction.LIKE ? "like" : "pass";
        logger.info("\n✅ Undone! Your {} on {} has been reversed.", directionStr, candidateName);

        if (result.matchDeleted()) {
          logger.info("   (The match was also removed)\n");
        } else {
          logger.info("");
        }
      } else {
        logger.info("\n❌ {}\n", result.message());
      }
    }
  }

  private void checkAndDisplayNewAchievements(User currentUser) {
    List<UserAchievement> newAchievements = achievementService.checkAndUnlock(currentUser.getId());
    if (!newAchievements.isEmpty()) {
      logger.info("\n🏆 NEW ACHIEVEMENTS UNLOCKED! 🏆");
      for (UserAchievement ua : newAchievements) {
        logger.info(
            "  ✨ {} - {}", ua.achievement().getDisplayName(), ua.achievement().getDescription());
      }
      logger.info("");
    }
  }

  /**
   * Returns an emoji badge based on the number of shared interests. More matches = more exciting
   * badge!
   */
  private String getMutualInterestsBadge(int sharedCount) {
    if (sharedCount >= 5) {
      return "🎯🔥"; // Perfect match
    } else if (sharedCount >= 3) {
      return "🎯✨"; // Great match
    } else if (sharedCount >= 2) {
      return "🎯"; // Good match
    } else {
      return "✨"; // Some shared interests
    }
  }
}
