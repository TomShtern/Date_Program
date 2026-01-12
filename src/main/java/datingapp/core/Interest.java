package datingapp.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Predefined interests for user profiles. Organized by category for easier selection in CLI.
 *
 * <p>Usage example:
 *
 * <pre>
 * Set&lt;Interest&gt; interests = EnumSet.of(Interest.HIKING, Interest.COFFEE);
 * Interest.Category cat = Interest.HIKING.getCategory(); // OUTDOORS
 * List&lt;Interest&gt; outdoorInterests = Interest.byCategory(Interest.Category.OUTDOORS);
 * </pre>
 */
public enum Interest {
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
   * Maximum number of interests a user can select. Enforced in User.setInterests() and CLI prompts.
   */
  public static final int MAX_PER_USER = 10;

  /** Minimum interests for "interests complete" in ProfilePreviewService. */
  public static final int MIN_FOR_COMPLETE = 3;

  /** Interest categories for organized display. */
  public enum Category {
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
