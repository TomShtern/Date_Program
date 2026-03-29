# Achievement Popup Wiring — Work Item

**Status:** Not started
**Scope:** Backend event propagation + frontend popup display
**Risk:** Low — all pieces exist, just not connected

---

## The Problem

When a user unlocks an achievement, the backend saves it to the database and the confetti animation plays — but the `MilestonePopupController` popup (with the achievement name, icon, XP, and description) **never appears**. The popup controller is fully built and tested; it is simply never called.

---

## Two Break Points

### Break Point 1 — `AchievementEventHandler` discards the result
**File:** `src/main/java/datingapp/app/event/handlers/AchievementEventHandler.java`

```java
// current — return value thrown away
achievementService.checkAndUnlock(event.swiperId());

// needed — capture newly unlocked list and propagate it
List<UserAchievement> newlyUnlocked = achievementService.checkAndUnlock(event.swiperId());
// then do something with newlyUnlocked (see options below)
```

### Break Point 2 — `DashboardController` shows confetti only, not the popup
**File:** `src/main/java/datingapp/ui/screen/DashboardController.java`
**Method:** `handleAchievementCelebration(Boolean)` (subscribed to `viewModel.newAchievementsAvailableProperty()`)

Currently only plays a 3-second confetti animation then calls `viewModel.markAchievementsSeen()`.
The `MilestonePopupController` is never instantiated here.

---

## Enum Mismatch — Must Resolve First

The popup controller has its own `AchievementType` enum that does NOT match the domain `Achievement` enum:

| `EngagementDomain.Achievement` (backend, 11 entries) | `MilestonePopupController.AchievementType` (UI, 10 entries) |
|------------------------------------------------------|-------------------------------------------------------------|
| `FIRST_SPARK`                                        | `FIRST_MATCH`                                               |
| `SOCIAL_BUTTERFLY`                                   | `PROFILE_COMPLETE`                                          |
| `POPULAR`                                            | `FIRST_MESSAGE`                                             |
| `SUPERSTAR`                                          | `TEN_MATCHES`                                               |
| `LEGEND`                                             | `TWENTY_FIVE_MATCHES`                                       |
| `COMPLETE_PACKAGE`                                   | `FIFTY_MATCHES`                                             |
| `STORYTELLER`                                        | `FIRST_DATE`                                                |
| `LIFESTYLE_GURU`                                     | `ACTIVE_WEEK`                                               |
| `GUARDIAN`                                           | `SUPER_LIKER`                                               |
| `SELECTIVE`                                          | `PHOTO_PERFECT`                                             |
| `OPEN_MINDED`                                        | *(no equivalent)*                                           |

No mapping exists between the two. `MilestonePopupController.showAchievement(AchievementType)` takes the UI enum.
`StatsViewModel` and `StatsController` use only `EngagementDomain.Achievement` (the correct one).

### Recommended resolution
Remove `MilestonePopupController.AchievementType` entirely. Add the display fields it carries (MDI2 icon literal, XP value) directly onto `EngagementDomain.Achievement`. Then change `MilestonePopupController.showAchievement(Achievement)` to accept the domain type directly.

---

## Files Involved

| File                                              | Role                                                                 | Change Needed                                                         |
|---------------------------------------------------|----------------------------------------------------------------------|-----------------------------------------------------------------------|
| `app/event/handlers/AchievementEventHandler.java` | Calls `checkAndUnlock()`, discards result                            | Capture result; propagate somehow                                     |
| `app/event/AppEvent.java`                         | Domain events                                                        | Add `AchievementUnlocked` event (optional approach)                   |
| `ui/screen/DashboardController.java`              | Shows confetti only                                                  | Also show `MilestonePopupController`                                  |
| `ui/screen/MilestonePopupController.java`         | Popup — complete and tested                                          | Change signature to accept `Achievement` instead of `AchievementType` |
| `ui/viewmodel/DashboardViewModel.java`            | Has `newAchievementsAvailable` + `recentAchievements`                | May need to expose which specific achievements are new                |
| `core/metrics/EngagementDomain.java`              | Domain achievement enum                                              | Add `iconLiteral` (MDI2) and `xp` fields per entry                    |
| `i18n/messages.properties`                        | Has 14 `ui.achievement.*` keys mapped to old `AchievementType` names | Update keys to match new `Achievement` names                          |

---

## Suggested Fix Sequence

1. **Add `iconLiteral` and `xp` to `EngagementDomain.Achievement`** — gives the domain enum all the display data it needs
2. **Delete `MilestonePopupController.AchievementType`** — the orphaned enum
3. **Update `MilestonePopupController.showAchievement()`** to accept `Achievement` instead
4. **Update `messages.properties`** — rename the 14 `ui.achievement.*` keys to match `Achievement` constant names
5. **`AchievementEventHandler`** — capture `checkAndUnlock()` return value; either:
   - Fire a new `AppEvent.AchievementUnlocked(userId, List<UserAchievement>)` event, or
   - Update `DashboardViewModel` directly via a listener/property (simpler, stays in-process)
6. **`DashboardController.handleAchievementCelebration()`** — after confetti, iterate `viewModel.getRecentAchievements()` and call `MilestonePopupController.showAchievement()` for each one

---

## What Already Works (Do Not Break)

- `StatsController` renders `EngagementDomain.Achievement` correctly (emoji → MDI2 icon code mapping in `getIconCode()`)
- `DefaultAchievementService.checkAndUnlock()` returns only *newly* unlocked entries — correct semantics
- `DashboardViewModel.newAchievementsAvailable` + `recentAchievements` infrastructure is sound
- `MilestonePopupController` match popup (`setMatchedUser()`) is working and unrelated to this task
- `MilestonePopupControllerTest` has test scaffolding to extend
