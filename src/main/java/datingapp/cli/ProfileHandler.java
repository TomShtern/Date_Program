package datingapp.cli;

import datingapp.core.AchievementService;
import datingapp.core.Dealbreakers;
import datingapp.core.Interest;
import datingapp.core.InterestMatcher;
import datingapp.core.Lifestyle;
import datingapp.core.ProfilePreviewService;
import datingapp.core.User;
import datingapp.core.UserAchievement;
import datingapp.core.UserStorage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileHandler {
  private static final Logger logger = LoggerFactory.getLogger(ProfileHandler.class);
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final UserStorage userStorage;
  private final ProfilePreviewService profilePreviewService;
  private final AchievementService achievementService;
  private final UserSession userSession;
  private final InputReader inputReader;

  private static final String PROMPT_CHOICE = "Your choice: ";
  private static final String PROMPT_CHOICES = "Choices: ";

  public ProfileHandler(
      UserStorage userStorage,
      ProfilePreviewService profilePreviewService,
      AchievementService achievementService,
      UserSession userSession,
      InputReader inputReader) {
    this.userStorage = userStorage;
    this.profilePreviewService = profilePreviewService;
    this.achievementService = achievementService;
    this.userSession = userSession;
    this.inputReader = inputReader;
  }

  public void completeProfile() {
    if (!userSession.isLoggedIn()) {
      logger.info(CliConstants.PLEASE_SELECT_USER);
      return;
    }

    User currentUser = userSession.getCurrentUser();
    logger.info("\n--- Complete Profile for {} ---\n", currentUser.getName());

    promptBio(currentUser);
    promptBirthDate(currentUser);
    promptGender(currentUser);
    promptInterestedIn(currentUser);
    promptInterests(currentUser);
    promptLocation(currentUser);
    promptPreferences(currentUser);
    promptPhoto(currentUser);
    promptLifestyle(currentUser);

    // Try to activate if complete
    if (currentUser.isComplete() && currentUser.getState() == User.State.INCOMPLETE) {
      currentUser.activate();
      logger.info("\n🎉 Profile complete! Status changed to ACTIVE.");
    } else if (!currentUser.isComplete()) {
      logger.info("\n⚠️  Profile still incomplete. Missing required fields.");
    }

    userStorage.save(currentUser);
    logger.info("✅ Profile saved!\n");

    checkAndDisplayNewAchievements(currentUser);
  }

  public void previewProfile() {
    if (!userSession.isLoggedIn()) {
      logger.info(CliConstants.PLEASE_SELECT_USER);
      return;
    }

    User currentUser = userSession.getCurrentUser();
    ProfilePreviewService.ProfilePreview preview =
        profilePreviewService.generatePreview(currentUser);

    logger.info("\n" + CliConstants.SEPARATOR_LINE);
    logger.info("      👤 YOUR PROFILE PREVIEW");
    logger.info(CliConstants.SEPARATOR_LINE);
    logger.info("");
    logger.info("  This is how others see you:");
    logger.info("");

    // Card display
    logger.info(CliConstants.BOX_TOP);
    logger.info("│ 💝 {}, {} years old", currentUser.getName(), currentUser.getAge());
    logger.info("│ 📍 Location: {}, {}", currentUser.getLat(), currentUser.getLon());
    String bio = preview.displayBio();
    if (bio.length() > 50) {
      bio = bio.substring(0, 47) + "...";
    }
    logger.info(CliConstants.PROFILE_BIO_FORMAT, bio);
    if (preview.displayLookingFor() != null) {
      logger.info("│ 💭 {}", preview.displayLookingFor());
    }
    logger.info(CliConstants.BOX_BOTTOM);

    // Completeness
    ProfilePreviewService.ProfileCompleteness comp = preview.completeness();
    logger.info("");
    logger.info("  📊 PROFILE COMPLETENESS: {}%", comp.percentage());

    // Render progress bar (copied from profilePreviewService/Main logic)
    // Main was accessing static method on ProfilePreviewService or just
    // implementing it?
    // Main used ProfilePreviewService.renderProgressBar. Assuming it's public
    // static.
    if (comp.percentage() > 0) {
      logger.info("  {}", ProfilePreviewService.renderProgressBar(comp.percentage() / 100.0, 20));
    }

    if (!comp.missingFields().isEmpty()) {
      logger.info("");
      logger.info("  ⚠️  Missing fields:");
      comp.missingFields().forEach(f -> logger.info("    • {}", f));
    }

    // Tips
    if (!preview.improvementTips().isEmpty()) {
      logger.info("");
      logger.info("  💡 IMPROVEMENT TIPS:");
      preview.improvementTips().forEach(tip -> logger.info("    {}", tip));
    }

    logger.info("");
    inputReader.readLine("  [Press Enter to return to menu]");
  }

  public void setDealbreakers() {
    if (!userSession.isLoggedIn()) {
      logger.info(CliConstants.PLEASE_SELECT_USER);
      return;
    }

    User currentUser = userSession.getCurrentUser();
    displayCurrentDealbreakers(currentUser);
    displayDealbreakerMenu();

    String choice = inputReader.readLine(PROMPT_CHOICE);
    handleDealbreakerChoice(choice, currentUser);

    userStorage.save(currentUser);
  }

  // --- Helper Methods ---

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

  private void promptBio(User currentUser) {
    String bio = inputReader.readLine("Bio (short description): ");
    if (!bio.isBlank()) currentUser.setBio(bio);
  }

  private void promptBirthDate(User currentUser) {
    String birthStr = inputReader.readLine("Birth date (yyyy-MM-dd): ");
    try {
      LocalDate birthDate = LocalDate.parse(birthStr, DATE_FORMAT);
      currentUser.setBirthDate(birthDate);
    } catch (DateTimeParseException e) {
      logger.info("⚠️  Invalid date format, skipping.");
    }
  }

  private void promptGender(User currentUser) {
    logger.info("\nGender options: 1=MALE, 2=FEMALE, 3=OTHER");
    String genderChoice = inputReader.readLine("Your gender (1/2/3): ");
    User.Gender gender =
        switch (genderChoice) {
          case "1" -> User.Gender.MALE;
          case "2" -> User.Gender.FEMALE;
          case "3" -> User.Gender.OTHER;
          default -> null;
        };
    if (gender != null) currentUser.setGender(gender);
  }

  private void promptInterestedIn(User currentUser) {
    logger.info("\nInterested in (comma-separated, e.g., 1,2):");
    logger.info("  1=MALE, 2=FEMALE, 3=OTHER");
    String interestedStr = inputReader.readLine("Your preferences: ");
    Set<User.Gender> interestedIn = parseGenderSet(interestedStr);
    if (!interestedIn.isEmpty()) currentUser.setInterestedIn(interestedIn);
  }

  // Quick fix: implementing parseGenderSet helper here as MainUtils is not
  // created yet
  private Set<User.Gender> parseGenderSet(String input) {
    Set<User.Gender> result = java.util.EnumSet.noneOf(User.Gender.class);
    if (input == null || input.isBlank()) return result;

    for (String part : input.split(",")) {
      switch (part.trim()) {
        case "1" -> result.add(User.Gender.MALE);
        case "2" -> result.add(User.Gender.FEMALE);
        case "3" -> result.add(User.Gender.OTHER);
        default -> {
          // Ignore unrecognized input
        }
      }
    }
    return result;
  }

  private void promptInterests(User currentUser) {
    logger.info("\n--- Interests & Hobbies (max 10) ---");
    Set<Interest> selected = currentUser.getInterests();

    boolean editing = true;
    while (editing) {
      logger.info(
          "\nCurrent interests: {}",
          selected.isEmpty() ? "(none)" : InterestMatcher.formatSharedInterests(selected));
      logger.info("  1. Add by category");
      logger.info("  2. Clear all");
      logger.info("  0. Done");

      String choice = inputReader.readLine("\nChoice: ");
      switch (choice) {
        case "1" -> {
          if (selected.size() >= Interest.MAX_PER_USER) {
            logger.info("❌ Limit of {} reached.\n", Interest.MAX_PER_USER);
            continue;
          }
          addInterestByCategory(selected);
        }
        case "2" -> {
          selected.clear();
          logger.info("✅ Interests cleared.\n");
        }
        case "0" -> editing = false;
        default -> logger.info(CliConstants.INVALID_SELECTION);
      }
    }
    currentUser.setInterests(selected);
  }

  private void addInterestByCategory(Set<Interest> selected) {
    Interest.Category[] categories = Interest.Category.values();
    for (int i = 0; i < categories.length; i++) {
      logger.info("  {}. {}", i + 1, categories[i].getDisplayName());
    }

    String input = inputReader.readLine("\nSelect category (or 0 to cancel): ");
    try {
      int catIdx = Integer.parseInt(input) - 1;
      if (catIdx < 0 || catIdx >= categories.length) return;

      Interest.Category cat = categories[catIdx];
      List<Interest> options = Interest.byCategory(cat);

      logger.info("\n--- {} ---", cat.getDisplayName());
      for (int i = 0; i < options.size(); i++) {
        Interest interest = options.get(i);
        String marker = selected.contains(interest) ? " [x]" : "";
        logger.info("  {}. {}{}", i + 1, interest.getDisplayName(), marker);
      }

      String intInput = inputReader.readLine("\nSelect interest (or 0 to cancel): ");
      int intIdx = Integer.parseInt(intInput) - 1;
      if (intIdx >= 0 && intIdx < options.size()) {
        Interest chosen = options.get(intIdx);
        if (selected.contains(chosen)) {
          selected.remove(chosen);
          logger.info("✅ Removed {}.\n", chosen.getDisplayName());
        } else if (selected.size() < Interest.MAX_PER_USER) {
          selected.add(chosen);
          logger.info("✅ Added {}.\n", chosen.getDisplayName());
        } else {
          logger.info("❌ Limit reached.\n");
        }
      }
    } catch (NumberFormatException e) {
      logger.info(CliConstants.INVALID_INPUT);
    }
  }

  private void promptLocation(User currentUser) {
    String latStr = inputReader.readLine("\nLatitude (e.g., 32.0853): ");
    String lonStr = inputReader.readLine("Longitude (e.g., 34.7818): ");
    try {
      double lat = Double.parseDouble(latStr);
      double lon = Double.parseDouble(lonStr);
      currentUser.setLocation(lat, lon);
    } catch (NumberFormatException e) {
      logger.info("⚠️  Invalid coordinates, skipping.");
    }
  }

  private void promptPreferences(User currentUser) {
    String distStr = inputReader.readLine("Max distance (km, default 50): ");
    try {
      int dist = Integer.parseInt(distStr);
      currentUser.setMaxDistanceKm(dist);
    } catch (NumberFormatException e) {
      // Keep default
    }

    String minAgeStr = inputReader.readLine("Min age preference (default 18): ");
    String maxAgeStr = inputReader.readLine("Max age preference (default 99): ");
    try {
      int minAge = minAgeStr.isBlank() ? 18 : Integer.parseInt(minAgeStr);
      int maxAge = maxAgeStr.isBlank() ? 99 : Integer.parseInt(maxAgeStr);
      currentUser.setAgeRange(minAge, maxAge);
    } catch (IllegalArgumentException e) {
      logger.info("⚠️  Invalid age range, using defaults.");
    }
  }

  private void promptPhoto(User currentUser) {
    String photoUrl = inputReader.readLine("Photo URL (or press Enter to skip): ");
    if (!photoUrl.isBlank()) {
      currentUser.addPhotoUrl(photoUrl);
    }
  }

  private void promptLifestyle(User currentUser) {
    logger.info("\n--- Lifestyle (optional, helps with matching) ---\n");

    String heightStr = inputReader.readLine("Height in cm (e.g., 175, or Enter to skip): ");
    if (!heightStr.isBlank()) {
      try {
        currentUser.setHeightCm(Integer.valueOf(heightStr));
      } catch (NumberFormatException e) {
        logger.info("⚠️  Invalid height, skipping.");
      }
    }

    logger.info("Smoking: 1=Never, 2=Sometimes, 3=Regularly, 0=Skip");
    String smokingChoice = inputReader.readLine(PROMPT_CHOICE);
    Lifestyle.Smoking smoking =
        switch (smokingChoice) {
          case "1" -> Lifestyle.Smoking.NEVER;
          case "2" -> Lifestyle.Smoking.SOMETIMES;
          case "3" -> Lifestyle.Smoking.REGULARLY;
          default -> null;
        };
    if (smoking != null) currentUser.setSmoking(smoking);

    logger.info("Drinking: 1=Never, 2=Socially, 3=Regularly, 0=Skip");
    String drinkingChoice = inputReader.readLine(PROMPT_CHOICE);
    Lifestyle.Drinking drinking =
        switch (drinkingChoice) {
          case "1" -> Lifestyle.Drinking.NEVER;
          case "2" -> Lifestyle.Drinking.SOCIALLY;
          case "3" -> Lifestyle.Drinking.REGULARLY;
          default -> null;
        };
    if (drinking != null) currentUser.setDrinking(drinking);

    logger.info("Kids: 1=Don't want, 2=Open to it, 3=Want someday, 4=Have kids, 0=Skip");
    String kidsChoice = inputReader.readLine(PROMPT_CHOICE);
    Lifestyle.WantsKids wantsKids =
        switch (kidsChoice) {
          case "1" -> Lifestyle.WantsKids.NO;
          case "2" -> Lifestyle.WantsKids.OPEN;
          case "3" -> Lifestyle.WantsKids.SOMEDAY;
          case "4" -> Lifestyle.WantsKids.HAS_KIDS;
          default -> null;
        };
    if (wantsKids != null) currentUser.setWantsKids(wantsKids);

    logger.info("Looking for: 1=Casual, 2=Short-term, 3=Long-term, 4=Marriage, 5=Unsure, 0=Skip");
    String lookingForChoice = inputReader.readLine(PROMPT_CHOICE);
    Lifestyle.LookingFor lookingFor =
        switch (lookingForChoice) {
          case "1" -> Lifestyle.LookingFor.CASUAL;
          case "2" -> Lifestyle.LookingFor.SHORT_TERM;
          case "3" -> Lifestyle.LookingFor.LONG_TERM;
          case "4" -> Lifestyle.LookingFor.MARRIAGE;
          case "5" -> Lifestyle.LookingFor.UNSURE;
          default -> null;
        };
    if (lookingFor != null) currentUser.setLookingFor(lookingFor);
  }

  // --- Dealbreaker Helpers ---

  private void displayCurrentDealbreakers(User currentUser) {
    logger.info("\n" + CliConstants.SEPARATOR_LINE);
    logger.info("         SET YOUR DEALBREAKERS");
    logger.info(CliConstants.SEPARATOR_LINE + "\n");
    logger.info("Dealbreakers are HARD filters.\n");

    Dealbreakers current = currentUser.getDealbreakers();
    if (current.hasAnyDealbreaker()) {
      logger.info("Current dealbreakers:");
      if (current.hasSmokingDealbreaker())
        logger.info("  - Smoking: {}", current.acceptableSmoking());
      if (current.hasDrinkingDealbreaker())
        logger.info("  - Drinking: {}", current.acceptableDrinking());
      if (current.hasKidsDealbreaker())
        logger.info("  - Kids stance: {}", current.acceptableKidsStance());
      if (current.hasLookingForDealbreaker())
        logger.info("  - Looking for: {}", current.acceptableLookingFor());
      if (current.hasHeightDealbreaker())
        logger.info("  - Height: {} - {} cm", current.minHeightCm(), current.maxHeightCm());
      if (current.hasAgeDealbreaker())
        logger.info("  - Max age diff: {} years", current.maxAgeDifference());
      logger.info("");
    } else {
      logger.info("No dealbreakers set (showing everyone).\n");
    }
  }

  private void displayDealbreakerMenu() {
    logger.info("───────────────────────────────────────");
    logger.info("  1. Set smoking dealbreaker");
    logger.info("  2. Set drinking dealbreaker");
    logger.info("  3. Set kids stance dealbreaker");
    logger.info("  4. Set relationship goal dealbreaker");
    logger.info("  5. Set height range");
    logger.info("  6. Set max age difference");
    logger.info("  7. Clear all dealbreakers");
    logger.info("  0. Cancel");
    logger.info("───────────────────────────────────────\n");
  }

  // Note: I am copying the logic from Main.java for dealbreakers
  // Including the "copyExceptX" helpers directly here or refactoring.
  // Refactoring them to a Builder merge would be nicer on Dealbreakers class, but
  // for this task I will implement them as private helpers here to avoid changing
  // core classes too much.

  private void handleDealbreakerChoice(String choice, User currentUser) {
    Dealbreakers current = currentUser.getDealbreakers();
    switch (choice) {
      case "1" -> editSmokingDealbreaker(currentUser, current);
      case "2" -> editDrinkingDealbreaker(currentUser, current);
      case "3" -> editKidsDealbreaker(currentUser, current);
      case "4" -> editLookingForDealbreaker(currentUser, current);
      case "5" -> editHeightDealbreaker(currentUser, current);
      case "6" -> editAgeDealbreaker(currentUser, current);
      case "7" -> {
        currentUser.setDealbreakers(Dealbreakers.none());
        logger.info("✅ All dealbreakers cleared.\n");
      }
      case "0" -> logger.info(CliConstants.CANCELLED);
      default -> logger.info(CliConstants.INVALID_SELECTION);
    }
  }

  private void editSmokingDealbreaker(User currentUser, Dealbreakers current) {
    logger.info("\nAcceptable smoking (comma-separated, e.g., 1,2):");
    logger.info("  1=Never, 2=Sometimes, 3=Regularly, 0=Clear");
    String input = inputReader.readLine(PROMPT_CHOICES);

    Dealbreakers.Builder builder = Dealbreakers.builder();
    copyExceptSmoking(builder, current);
    if (!input.equals("0")) {
      for (String s : input.split(",")) {
        switch (s.trim()) {
          case "1" -> builder.acceptSmoking(Lifestyle.Smoking.NEVER);
          case "2" -> builder.acceptSmoking(Lifestyle.Smoking.SOMETIMES);
          case "3" -> builder.acceptSmoking(Lifestyle.Smoking.REGULARLY);
          default -> {
            // Ignore
          }
        }
      }
    }
    currentUser.setDealbreakers(builder.build());
    logger.info("✅ Smoking dealbreaker updated.\n");
  }

  private void editDrinkingDealbreaker(User currentUser, Dealbreakers current) {
    logger.info("\nAcceptable drinking (comma-separated):");
    logger.info("  1=Never, 2=Socially, 3=Regularly, 0=Clear");
    String input = inputReader.readLine(PROMPT_CHOICES);
    Dealbreakers.Builder builder = Dealbreakers.builder();
    copyExceptDrinking(builder, current);
    if (!input.equals("0")) {
      for (String s : input.split(",")) {
        switch (s.trim()) {
          case "1" -> builder.acceptDrinking(Lifestyle.Drinking.NEVER);
          case "2" -> builder.acceptDrinking(Lifestyle.Drinking.SOCIALLY);
          case "3" -> builder.acceptDrinking(Lifestyle.Drinking.REGULARLY);
          default -> {
            // Ignore
          }
        }
      }
    }
    currentUser.setDealbreakers(builder.build());
    logger.info("✅ Drinking dealbreaker updated.\n");
  }

  private void editKidsDealbreaker(User currentUser, Dealbreakers current) {
    logger.info("\nAcceptable kids stance (comma-separated):");
    logger.info("  1=Don't want, 2=Open, 3=Want someday, 4=Has kids, 0=Clear");
    String input = inputReader.readLine(PROMPT_CHOICES);
    Dealbreakers.Builder builder = Dealbreakers.builder();
    copyExceptKids(builder, current);
    if (!input.equals("0")) {
      for (String s : input.split(",")) {
        switch (s.trim()) {
          case "1" -> builder.acceptKidsStance(Lifestyle.WantsKids.NO);
          case "2" -> builder.acceptKidsStance(Lifestyle.WantsKids.OPEN);
          case "3" -> builder.acceptKidsStance(Lifestyle.WantsKids.SOMEDAY);
          case "4" -> builder.acceptKidsStance(Lifestyle.WantsKids.HAS_KIDS);
          default -> {
            // Ignore
          }
        }
      }
    }
    currentUser.setDealbreakers(builder.build());
    logger.info("✅ Kids stance dealbreaker updated.\n");
  }

  private void editLookingForDealbreaker(User currentUser, Dealbreakers current) {
    logger.info("\nAcceptable relationship goals (comma-separated):");
    logger.info("  1=Casual, 2=Short-term, 3=Long-term, 4=Marriage, 5=Unsure, 0=Clear");
    String input = inputReader.readLine(PROMPT_CHOICES);
    Dealbreakers.Builder builder = Dealbreakers.builder();
    copyExceptLookingFor(builder, current);
    if (!input.equals("0")) {
      for (String s : input.split(",")) {
        switch (s.trim()) {
          case "1" -> builder.acceptLookingFor(Lifestyle.LookingFor.CASUAL);
          case "2" -> builder.acceptLookingFor(Lifestyle.LookingFor.SHORT_TERM);
          case "3" -> builder.acceptLookingFor(Lifestyle.LookingFor.LONG_TERM);
          case "4" -> builder.acceptLookingFor(Lifestyle.LookingFor.MARRIAGE);
          case "5" -> builder.acceptLookingFor(Lifestyle.LookingFor.UNSURE);
          default -> {
            // Ignore
          }
        }
      }
    }
    currentUser.setDealbreakers(builder.build());
    logger.info("✅ Looking for dealbreaker updated.\n");
  }

  private void editHeightDealbreaker(User currentUser, Dealbreakers current) {
    logger.info("\nHeight range (in cm), or Enter to clear:");
    String minStr = inputReader.readLine("Minimum height (e.g., 160): ");
    String maxStr = inputReader.readLine("Maximum height (e.g., 190): ");
    Dealbreakers.Builder builder = Dealbreakers.builder();
    copyExceptHeight(builder, current);
    try {
      Integer min = minStr.isBlank() ? null : Integer.valueOf(minStr);
      Integer max = maxStr.isBlank() ? null : Integer.valueOf(maxStr);
      if (min != null || max != null) {
        builder.heightRange(min, max);
      }
      currentUser.setDealbreakers(builder.build());
      logger.info("✅ Height dealbreaker updated.\n");
    } catch (NumberFormatException e) {
      logger.info(CliConstants.INVALID_INPUT);
    } catch (IllegalArgumentException e) {
      logger.info("❌ {}\n", e.getMessage());
    }
  }

  private void editAgeDealbreaker(User currentUser, Dealbreakers current) {
    logger.info("\nMax age difference (years), or Enter to clear:");
    String input = inputReader.readLine("Max years: ");
    Dealbreakers.Builder builder = Dealbreakers.builder();
    copyExceptAge(builder, current);
    if (!input.isBlank()) {
      try {
        builder.maxAgeDifference(Integer.parseInt(input));
        currentUser.setDealbreakers(builder.build());
        logger.info("✅ Age dealbreaker updated.\n");
      } catch (NumberFormatException e) {
        logger.info(CliConstants.INVALID_INPUT);
      }
    } else {
      currentUser.setDealbreakers(builder.build());
      logger.info("✅ Age dealbreaker cleared.\n");
    }
  }

  // -- Copy Helpers --
  // These ensure we don't overwrite other dealbreakers when editing one category.
  // They are copied from Main.java but slightly cleaned up if possible.

  private void copyExceptSmoking(Dealbreakers.Builder b, Dealbreakers c) {
    if (c.hasDrinkingDealbreaker())
      b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
    if (c.hasKidsDealbreaker())
      b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
    if (c.hasLookingForDealbreaker())
      b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
    if (c.hasHeightDealbreaker()) b.heightRange(c.minHeightCm(), c.maxHeightCm());
    if (c.hasAgeDealbreaker()) b.maxAgeDifference(c.maxAgeDifference());
  }

  private void copyExceptDrinking(Dealbreakers.Builder b, Dealbreakers c) {
    if (c.hasSmokingDealbreaker())
      b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
    if (c.hasKidsDealbreaker())
      b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
    if (c.hasLookingForDealbreaker())
      b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
    if (c.hasHeightDealbreaker()) b.heightRange(c.minHeightCm(), c.maxHeightCm());
    if (c.hasAgeDealbreaker()) b.maxAgeDifference(c.maxAgeDifference());
  }

  private void copyExceptKids(Dealbreakers.Builder b, Dealbreakers c) {
    if (c.hasSmokingDealbreaker())
      b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
    if (c.hasDrinkingDealbreaker())
      b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
    if (c.hasLookingForDealbreaker())
      b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
    if (c.hasHeightDealbreaker()) b.heightRange(c.minHeightCm(), c.maxHeightCm());
    if (c.hasAgeDealbreaker()) b.maxAgeDifference(c.maxAgeDifference());
  }

  private void copyExceptLookingFor(Dealbreakers.Builder b, Dealbreakers c) {
    if (c.hasSmokingDealbreaker())
      b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
    if (c.hasDrinkingDealbreaker())
      b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
    if (c.hasKidsDealbreaker())
      b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
    if (c.hasHeightDealbreaker()) b.heightRange(c.minHeightCm(), c.maxHeightCm());
    if (c.hasAgeDealbreaker()) b.maxAgeDifference(c.maxAgeDifference());
  }

  private void copyExceptHeight(Dealbreakers.Builder b, Dealbreakers c) {
    if (c.hasSmokingDealbreaker())
      b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
    if (c.hasDrinkingDealbreaker())
      b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
    if (c.hasKidsDealbreaker())
      b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
    if (c.hasLookingForDealbreaker())
      b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
    if (c.hasAgeDealbreaker()) b.maxAgeDifference(c.maxAgeDifference());
  }

  private void copyExceptAge(Dealbreakers.Builder b, Dealbreakers c) {
    if (c.hasSmokingDealbreaker())
      b.acceptSmoking(c.acceptableSmoking().toArray(Lifestyle.Smoking[]::new));
    if (c.hasDrinkingDealbreaker())
      b.acceptDrinking(c.acceptableDrinking().toArray(Lifestyle.Drinking[]::new));
    if (c.hasKidsDealbreaker())
      b.acceptKidsStance(c.acceptableKidsStance().toArray(Lifestyle.WantsKids[]::new));
    if (c.hasLookingForDealbreaker())
      b.acceptLookingFor(c.acceptableLookingFor().toArray(Lifestyle.LookingFor[]::new));
    if (c.hasHeightDealbreaker()) b.heightRange(c.minHeightCm(), c.maxHeightCm());
  }
}
