# Plan P1-B — Achievement Count: "3 / 11" instead of "3 / ???"

> **Phase:** 1 — Finish & Polish (current priority)
> **Effort:** Small (~20 lines across 6 files including tests + FXML)
> **Blocked by:** Nothing — fully independent
> **Source-verified:** 2026-03-13 against actual code

---

## Verification & Progress (updated 2026-03-13)

- [x] Plan validated against live source and tests.
- [x] CLI output updated from `???` to dynamic `Achievement.values().length`.
- [x] `StatsViewModel#getTotalAchievementCount()` implemented.
- [x] JavaFX achievement header now shows live `unlocked / total` count.
- [x] CLI regression test added (log capture) for unlocked/total output.
- [x] ViewModel regression test added for total-count accessor.
- [x] Full quality gate completed successfully.

Verification result recorded (2026-03-13): `BUILD SUCCESS`, `Tests run: 1100, Failures: 0, Errors: 0, Skipped: 2`.

---

## Goal

Replace the hardcoded `???` placeholder with the real total achievement count in both
the CLI stats display and the JavaFX stats screen. Output should read "Unlocked: 3 / 11"
rather than "Unlocked: 3 / ???".

---

## Current State

### CLI — `src/main/java/datingapp/app/cli/StatsHandler.java` line 103

```java
logInfo("  Unlocked: {} / ???\n", unlocked.size());
// I don't know total count easily without asking service for all definitions.
```

The comment is stale — `Achievement.values().length` gives the total count directly.
`Achievement` is a domain enum already imported in this file (used on line ~107 for
display). No new import needed.

### GUI — `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java` line 52

```java
ObservableList<Achievement> achievements;
```

Only **unlocked** achievements are in this list. The total count is not currently
exposed as a property. `StatsController` renders achievements in a list cell factory.
The controller needs a way to get the total — either from the ViewModel or directly
from `Achievement.values().length`.

---

## Changes Required

### Change 1 — CLI Fix

**File:** `src/main/java/datingapp/app/cli/StatsHandler.java`

**Before (line 103):**
```java
logInfo("  Unlocked: {} / ???\n", unlocked.size());
```

**After:**
```java
logInfo("  Unlocked: {} / {}\n", unlocked.size(), Achievement.values().length);
```

Remove the stale comment on the line below (the one saying "I don't know total count…").

---

### Change 2 — ViewModel Accessor

**File:** `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`

Add a public accessor method alongside `getAchievements()`:

```java
/** Returns the total number of achievements defined in the system. Always {@code Achievement.values().length}. */
public int getTotalAchievementCount() {
    return Achievement.values().length;
}
```

This keeps the total count calculation in one place and makes it mockable in tests.

---

### Change 3 — Controller Binding

**File:** `src/main/java/datingapp/ui/screen/StatsController.java`

**Before implementing this change, read `StatsController.java` and `stats.fxml` in full.**
Find the `fx:id` of the label that shows achievement count (search for `achievement` in
`stats.fxml` and in the controller's `@FXML` fields).

Once found, update the display binding. The pattern will be one of these two forms
depending on what already exists:

**If the label is set once in `initialize()`:**
```java
// Example — adapt fx:id to what actually exists in the controller:
achievementCountLabel.setText(
    viewModel.getAchievements().size() + " / " + viewModel.getTotalAchievementCount()
);
```

**If the label is bound to an observable (preferred — stays live):**
```java
achievementCountLabel.textProperty().bind(
    Bindings.createStringBinding(
        () -> viewModel.getAchievements().size() + " / " + viewModel.getTotalAchievementCount(),
        viewModel.getAchievements()
    )
);
```

Use the observable binding form if the existing code already uses `textProperty().bind()`.
Follow the pattern already present in `StatsController` — do not introduce a new pattern.

---

## Files to Modify

| File                                                       | Change                                                   |
|------------------------------------------------------------|----------------------------------------------------------|
| `src/main/java/datingapp/app/cli/StatsHandler.java`        | Line 103 — one line change + remove stale comment        |
| `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java` | Add 1 public method (3 lines)                            |
| `src/main/java/datingapp/ui/screen/StatsController.java`   | Update achievement count label binding (read file first) |

### Additional files updated during implementation

| File                                                           | Reason                                                                              |
|----------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `src/main/resources/fxml/stats.fxml`                           | Replaced "Coming Soon" header placeholder with bound `achievementCountLabel` target |
| `src/test/java/datingapp/app/cli/StatsHandlerTest.java`        | Added unlocked/total output regression test                                         |
| `src/test/java/datingapp/ui/viewmodel/StatsViewModelTest.java` | Added `getTotalAchievementCount()` regression test                                  |

---

## Test Requirements

1. **CLI test:** Search `src/test/java/` for `StatsHandlerTest`. Find the test that
   exercises `viewAchievements()` and asserts on the output string. Update the assertion
   to expect `"/ " + Achievement.values().length` (i.e., `"/ 11"`) instead of `"/ ???"`.

2. **ViewModel test:** In `StatsViewModelTest`, add:
   ```java
   @Test
   void totalAchievementCountMatchesEnumLength() {
       assertThat(viewModel.getTotalAchievementCount())
           .isEqualTo(Achievement.values().length);
   }
   ```

3. Run `mvn spotless:apply && mvn verify` — ensure no coverage regression.

---

## Gotchas

- **Do NOT hardcode `11`.** Use `Achievement.values().length` everywhere. The count will
  grow as achievements are added, and the display will update automatically.

- **`Achievement` import:** In `StatsHandler`, the enum is used on line ~107 already.
  In `StatsViewModel`, it is used on line 52 already. No new imports needed in those files.
  In `StatsController`, check whether `Achievement` is already imported — if not, add:
  ```java
  import datingapp.core.metrics.EngagementDomain.Achievement;
  ```

- **`Bindings.createStringBinding` import** (if used in controller):
  ```java
  import javafx.beans.binding.Bindings;
  ```
