# Phase 3 File Consolidation Plan (Source Files Only)

**Date:** January 25, 2026
**Project:** Dating App CLI - Date_Program
**Current State:** 128 Java files (81 main + 47 test) after Phase 2 consolidation
**Target Reduction:** ~9 main source files (-11%)
**Target Final Count:** ~72 main source files

> **📝 Note:** Test file consolidation has been moved to the dedicated **[TEST_CONSOLIDATION_PLAN.md](TEST_CONSOLIDATION_PLAN.md)** document, which targets -15 test files.

---

## Executive Summary

This plan identifies source file consolidation opportunities following the successful Phase 2 consolidation (159→128 files, -19.5%). The focus is on:

1. **Storage Layer Grouping** (Batch A) - Combine small H2 storage implementations by domain
2. **UI Layer Optimization** (Batch B) - Merge popup controllers and utility classes
3. **CLI Handler Optimization** (Batch C) - Merge small related handlers (optional)

**Philosophy:** Files under 150 LOC that share a common domain/responsibility are candidates for consolidation. Java 25 supports nested classes/records elegantly.

---

## Current File Analysis

### Main Source Files by Package (81 total)

| Package          | Count | Avg LOC | Notes                    |
|------------------|-------|---------|--------------------------|
| `core/`          | 25    | ~220    | Domain models + services |
| `storage/`       | 18    | ~180    | H2 implementations       |
| `cli/`           | 10    | ~250    | Command handlers         |
| `ui/controller/` | 11    | ~200    | JavaFX controllers       |
| `ui/viewmodel/`  | 8     | ~150    | MVVM view models         |
| `ui/util/`       | 4     | ~195    | UI utilities             |
| `ui/component/`  | 1     | 297     | Already consolidated     |
| `ui/` (root)     | 3     | ~145    | App, Navigation, Factory |
| `Main.java`      | 1     | 191     | Entry point              |

> **📝 Test Files:** See [TEST_CONSOLIDATION_PLAN.md](TEST_CONSOLIDATION_PLAN.md) for test file analysis and consolidation.

---

## BATCH A: Storage Layer Grouping

### 🎯 Objective
Combine small H2 storage implementations into domain-grouped files

### Rationale
Many H2 storage classes are 60-140 LOC and implement closely related storage interfaces. Grouping by domain improves cohesion and reduces file count.

---

### Task A.1: Create H2ModerationStorage.java ✅ COMPLETED
**Combine:** Report + Block storage (both are moderation-related)

**Files to merge:**
- `H2ReportStorage.java` (132 lines)
- `H2BlockStorage.java` (139 lines)

**Target:** `src/main/java/datingapp/storage/H2ModerationStorage.java` (~280 lines)

**Implementation:**
```java
package datingapp.storage;

import datingapp.core.UserInteractions.Block;
import datingapp.core.UserInteractions.BlockStorage;
import datingapp.core.UserInteractions.Report;
import datingapp.core.UserInteractions.ReportStorage;

/**
 * H2 storage implementation for moderation-related entities (blocks and reports).
 * Groups related CRUD operations for content moderation workflows.
 */
public final class H2ModerationStorage extends AbstractH2Storage {

    private final Blocks blocks;
    private final Reports reports;

    public H2ModerationStorage(DatabaseManager dbManager) {
        super(dbManager);
        this.blocks = new Blocks(dbManager);
        this.reports = new Reports(dbManager);
    }

    public BlockStorage blocks() {
        return blocks;
    }

    public ReportStorage reports() {
        return reports;
    }

    /** Block storage implementation. */
    public static class Blocks extends AbstractH2Storage implements BlockStorage {
        public Blocks(DatabaseManager dbManager) { super(dbManager); }
        // ... move all methods from H2BlockStorage
    }

    /** Report storage implementation. */
    public static class Reports extends AbstractH2Storage implements ReportStorage {
        public Reports(DatabaseManager dbManager) { super(dbManager); }
        // ... move all methods from H2ReportStorage
    }
}
```

**Update ServiceRegistry/Builder:**
```java
// OLD
private final BlockStorage blockStorage;
private final ReportStorage reportStorage;

// NEW
private final H2ModerationStorage moderationStorage;
// Access via: moderationStorage.blocks(), moderationStorage.reports()
```

**Delete:**
- `src/main/java/datingapp/storage/H2BlockStorage.java`
- `src/main/java/datingapp/storage/H2ReportStorage.java`

**Savings:** -1 file (2→1)

---

### Task A.2: Create H2ProfileDataStorage.java ✅ COMPLETED
**Combine:** Profile notes + Profile views (both are profile-related ancillary data)

**Files to merge:**
- `H2ProfileNoteStorage.java` (130 lines)
- `H2ProfileViewStorage.java` (130 lines)

**Target:** `src/main/java/datingapp/storage/H2ProfileDataStorage.java` (~270 lines)

**Implementation:**
```java
package datingapp.storage;

import datingapp.core.ProfilePreviewService.ProfileViewStorage;
import datingapp.core.User.ProfileNoteStorage;

/**
 * H2 storage for profile-related ancillary data (notes, views).
 */
public final class H2ProfileDataStorage extends AbstractH2Storage {

    private final Notes notes;
    private final Views views;

    public H2ProfileDataStorage(DatabaseManager dbManager) {
        super(dbManager);
        this.notes = new Notes(dbManager);
        this.views = new Views(dbManager);
    }

    public ProfileNoteStorage notes() { return notes; }
    public ProfileViewStorage views() { return views; }

    public static class Notes extends AbstractH2Storage implements ProfileNoteStorage {
        // ... move H2ProfileNoteStorage implementation
    }

    public static class Views extends AbstractH2Storage implements ProfileViewStorage {
        // ... move H2ProfileViewStorage implementation
    }
}
```

**Delete:**
- `src/main/java/datingapp/storage/H2ProfileNoteStorage.java`
- `src/main/java/datingapp/storage/H2ProfileViewStorage.java`

**Savings:** -1 file (2→1)

---

### Task A.3: Create H2MetricsStorage.java ✅ COMPLETED
**Combine:** Platform stats + Daily pick views + User achievements (all are metrics/tracking)

**Files to merge:**
- `H2PlatformStatsStorage.java` (91 lines)
- `H2DailyPickViewStorage.java` (63 lines)
- `H2UserAchievementStorage.java` (99 lines)

**Target:** `src/main/java/datingapp/storage/H2MetricsStorage.java` (~260 lines)

**Implementation:**
```java
package datingapp.storage;

import datingapp.core.Achievement.UserAchievementStorage;
import datingapp.core.DailyService.DailyPickStorage;
import datingapp.core.Stats.PlatformStatsStorage;

/**
 * H2 storage for platform metrics and tracking data.
 * Groups achievements, daily picks, and platform-wide statistics.
 */
public final class H2MetricsStorage extends AbstractH2Storage {

    private final Achievements achievements;
    private final DailyPicks dailyPicks;
    private final PlatformStats platformStats;

    public H2MetricsStorage(DatabaseManager dbManager) {
        super(dbManager);
        this.achievements = new Achievements(dbManager);
        this.dailyPicks = new DailyPicks(dbManager);
        this.platformStats = new PlatformStats(dbManager);
    }

    public UserAchievementStorage achievements() { return achievements; }
    public DailyPickStorage dailyPicks() { return dailyPicks; }
    public PlatformStatsStorage platformStats() { return platformStats; }

    public static class Achievements extends AbstractH2Storage implements UserAchievementStorage {
        // ... move H2UserAchievementStorage implementation
    }

    public static class DailyPicks extends AbstractH2Storage implements DailyPickStorage {
        // ... move H2DailyPickViewStorage implementation
    }

    public static class PlatformStats extends AbstractH2Storage implements PlatformStatsStorage {
        // ... move H2PlatformStatsStorage implementation
    }
}
```

**Delete:**
- `src/main/java/datingapp/storage/H2PlatformStatsStorage.java`
- `src/main/java/datingapp/storage/H2DailyPickViewStorage.java`
- `src/main/java/datingapp/storage/H2UserAchievementStorage.java`

**Savings:** -2 files (3→1)

---

### Task A.4: Create H2SocialStorage.java ✅ COMPLETED
**Combine:** Friend request + Notification storage (social features)

**Files to merge:**
- `H2FriendRequestStorage.java` (173 lines)
- `H2NotificationStorage.java` (173 lines)

**Target:** `src/main/java/datingapp/storage/H2SocialStorage.java` (~350 lines)

**Implementation:**
```java
package datingapp.storage;

import datingapp.core.Social.FriendRequestStorage;
import datingapp.core.Social.NotificationStorage;

/**
 * H2 storage for social features (friend requests, notifications).
 */
public final class H2SocialStorage extends AbstractH2Storage {

    private final FriendRequests friendRequests;
    private final Notifications notifications;

    public H2SocialStorage(DatabaseManager dbManager) {
        super(dbManager);
        this.friendRequests = new FriendRequests(dbManager);
        this.notifications = new Notifications(dbManager);
    }

    public FriendRequestStorage friendRequests() { return friendRequests; }
    public NotificationStorage notifications() { return notifications; }

    public static class FriendRequests extends AbstractH2Storage implements FriendRequestStorage {
        // ... move H2FriendRequestStorage implementation
    }

    public static class Notifications extends AbstractH2Storage implements NotificationStorage {
        // ... move H2NotificationStorage implementation
    }
}
```

**Delete:**
- `src/main/java/datingapp/storage/H2FriendRequestStorage.java`
- `src/main/java/datingapp/storage/H2NotificationStorage.java`

**Savings:** -1 file (2→1)

---

### Batch A Summary

| Original Files                                                             | Lines | New Files            | Lines | Savings |
|----------------------------------------------------------------------------|-------|----------------------|-------|---------|
| H2ReportStorage + H2BlockStorage                                           | 271   | H2ModerationStorage  | ~280  | -1      |
| H2ProfileNoteStorage + H2ProfileViewStorage                                | 260   | H2ProfileDataStorage | ~270  | -1      |
| H2PlatformStatsStorage + H2DailyPickViewStorage + H2UserAchievementStorage | 253   | H2MetricsStorage     | ~260  | -2      |
| H2FriendRequestStorage + H2NotificationStorage                             | 346   | H2SocialStorage      | ~350  | -1      |

**Total Batch A Savings:** -5 files

---

## BATCH B: UI Layer Optimization

### 🎯 Objective
Consolidate small UI utility classes and popup controllers

---

### Task B.1: Create UiServices.java ✅ COMPLETED
**Combine singleton UI services:**

**Files to merge:**
- `ToastService.java` (138 lines) - notification service
- `ImageCache.java` (152 lines) - image caching

**Target:** `src/main/java/datingapp/ui/util/UiServices.java` (~300 lines)

**Implementation:**
```java
package datingapp.ui.util;

import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton UI services for notifications and image management.
 */
public final class UiServices {
    private UiServices() {}

    /**
     * Toast notification service for non-blocking user feedback.
     */
    public static final class Toast {
        private static Toast instance;
        private StackPane container;

        public static Toast getInstance() {
            if (instance == null) instance = new Toast();
            return instance;
        }

        public void setContainer(StackPane container) { this.container = container; }
        public void showSuccess(String message) { /* ... */ }
        public void showError(String message) { /* ... */ }
        public void showWarning(String message) { /* ... */ }
        public void showInfo(String message) { /* ... */ }

        public enum Level { SUCCESS, ERROR, WARNING, INFO }
        // ... move implementation from ToastService.java
    }

    /**
     * Thread-safe image cache for avatars and profile photos.
     */
    public static final class ImageCache {
        private static final ConcurrentHashMap<String, Image> CACHE = new ConcurrentHashMap<>();

        public static Image getAvatar(String path, double size) { /* ... */ }
        public static Image getImage(String path, double w, double h) { /* ... */ }
        public static void clearCache() { /* ... */ }
        // ... move implementation from ImageCache.java
    }
}
```

**Update imports:**
```java
// OLD
import datingapp.ui.util.ToastService;
import datingapp.ui.util.ImageCache;

// NEW
import datingapp.ui.util.UiServices.Toast;
import datingapp.ui.util.UiServices.ImageCache;
// Or: import static datingapp.ui.util.UiServices.*;
```

**Delete:**
- `src/main/java/datingapp/ui/util/ToastService.java`
- `src/main/java/datingapp/ui/util/ImageCache.java`

**Savings:** -1 file (2→1)

---

### Task B.2: Create PopupControllers.java ✅ COMPLETED
**Combine popup controller classes:**

**Files to merge:**
- `MatchPopupController.java` (127 lines)
- `AchievementPopupController.java` (188 lines)

**Target:** `src/main/java/datingapp/ui/controller/PopupControllers.java` (~320 lines)

**Implementation:**
```java
package datingapp.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

/**
 * Controllers for modal popup dialogs (matches, achievements).
 */
public final class PopupControllers {
    private PopupControllers() {}

    /**
     * Controller for match celebration popup.
     */
    public static class MatchPopup extends BaseController {
        @FXML private ImageView matchPhoto;
        @FXML private Label matchName;
        // ... move from MatchPopupController.java
    }

    /**
     * Controller for achievement unlocked popup.
     */
    public static class AchievementPopup extends BaseController {
        @FXML private Label achievementName;
        @FXML private Label achievementDescription;
        // ... move from AchievementPopupController.java
    }
}
```

**Update FXML files** to reference new controller paths:
```xml
<!-- OLD -->
fx:controller="datingapp.ui.controller.MatchPopupController"

<!-- NEW -->
fx:controller="datingapp.ui.controller.PopupControllers$MatchPopup"
```

**Delete:**
- `src/main/java/datingapp/ui/controller/MatchPopupController.java`
- `src/main/java/datingapp/ui/controller/AchievementPopupController.java`

**Savings:** -1 file (2→1)

---

### Batch B Summary

| Task | Files Merged                                      | New File         | Savings |
|------|---------------------------------------------------|------------------|---------|
| B.1  | ToastService + ImageCache                         | UiServices       | -1      |
| B.2  | MatchPopupController + AchievementPopupController | PopupControllers | -1      |

**Total Batch B Savings:** -2 files

---

## BATCH C: CLI Handler Optimization (Optional)

### 🎯 Objective
Merge small CLI handlers into related handlers

### ⚠️ Risk Level: Medium
CLI handlers are user-facing and heavily tested. Proceed carefully.

---

### Task C.1: Merge LikerBrowserHandler into MatchingHandler
**Current state:**
- `LikerBrowserHandler.java` (98 lines) - Browse pending likers
- `MatchingHandler.java` (536 lines) - Core matching workflow

**Rationale:**
- Browsing likers is part of the matching workflow
- LikerBrowserHandler is the smallest handler
- MatchingHandler already handles related browse functionality

**Implementation:**
Add "Browse Likers" submenu to MatchingHandler's menu

**Delete:**
- `src/main/java/datingapp/cli/LikerBrowserHandler.java`

**Update Main.java** to remove likerBrowserHandler initialization

**Savings:** -1 file

---

### Task C.2: Merge RelationshipHandler into SafetyHandler
**Current state:**
- `RelationshipHandler.java` (135 lines) - Relationship transitions
- `SafetyHandler.java` (249 lines) - Blocking, reporting, safety

**Rationale:**
- Both handle relationship state changes (blocking, friend-zoning)
- Natural grouping under "relationship management"

**Implementation:**
Add relationship transition menu to SafetyHandler

**Delete:**
- `src/main/java/datingapp/cli/RelationshipHandler.java`

**Savings:** -1 file

---

### Batch C Summary (Optional)

| Task | Files Merged        | Target File     | Savings |
|------|---------------------|-----------------|---------|
| C.1  | LikerBrowserHandler | MatchingHandler | -1      |
| C.2  | RelationshipHandler | SafetyHandler   | -1      |

**Total Batch C Savings:** -2 files

---

## Implementation Schedule

### Recommended Order (Risk-Ordered)

| Phase | Batch | Focus         | Files Saved | Risk      | Status        |
|-------|-------|---------------|-------------|-----------|---------------|
| 1     | A     | Storage Layer | -5          | 🟡 Medium | ✅ Completed  |
| 2     | B     | UI Layer      | -2          | 🟡 Medium | ✅ Completed  |
| 3     | C     | CLI Handlers  | -2          | 🟡 Medium | ⬜ Pending    |

**Total: -9 main source files**

> **📝 Note:** Test file consolidation (-15 files) is tracked separately in [TEST_CONSOLIDATION_PLAN.md](TEST_CONSOLIDATION_PLAN.md).

---

## Expected Final Results

| Category             | Before | After | Change       |
|----------------------|--------|-------|--------------|
| Main source files    | 81     | 72    | -9 (-11%)    |
| Storage files        | 18     | 13    | -5           |
| UI files             | 27     | 25    | -2           |
| CLI files            | 10     | 8     | -2           |

---

## Validation Checklist

### Per-Batch Validation
- [ ] `mvn clean compile` - No compilation errors
- [ ] `mvn test` - All 464+ tests pass
- [ ] `mvn spotless:apply` - Code formatted
- [ ] `mvn checkstyle:check` - Style compliance
- [ ] Import updates complete (use `rg "import.*OldClass" src`)

### Final Validation
- [ ] `mvn verify` - Full build + quality checks pass
- [ ] File count: `(Get-ChildItem -Path src/main -Recurse -Filter *.java).Count`
- [ ] Expected: ~72 main source files
- [ ] No duplicate class names
- [ ] ServiceRegistry wiring updated
- [ ] FXML controller paths updated (Batch B)

---

## Risk Mitigation

### Storage Layer (Batch A)
- **Risk:** ServiceRegistry wiring breaks
- **Mitigation:** Update ServiceRegistryBuilder carefully, test DI thoroughly

### UI Layer (Batch B)
- **Risk:** FXML loading fails with inner class references
- **Mitigation:** Test FXML loading explicitly, use `$` syntax for inner classes

### CLI Handlers (Batch C)
- **Risk:** Menu navigation breaks
- **Mitigation:** Manual CLI testing after merge

---

## Alternative: Aggressive Consolidation

If targeting -15 main source files instead of -9, consider:

1. **Merge H2UserStatsStorage into H2MetricsStorage** (-1 more)
2. **Merge StatsHandler into ProfileHandler** (-1 more)
3. **Merge DatingApp.java + NavigationService into ViewModelFactory** (-1 to -2 more)
4. **Merge more ViewModels** (Dashboard + Stats ViewModels) (-1 more)

This would bring main source files to ~66 but increases risk.

---

## Conclusion

This plan identifies **9 main source files** that can be safely consolidated:
- **5 storage files** through domain grouping
- **2 UI files** through service/controller grouping
- **2 CLI files** (optional) through handler merging

The consolidation follows Java 25 best practices:
- Uses static nested classes for grouped implementations
- Maintains single responsibility within groups
- Preserves clean architecture boundaries
- Keeps file sizes reasonable (250-400 LOC)

> **📝 Cross-Reference:** Test file consolidation (-15 files) is documented separately in [TEST_CONSOLIDATION_PLAN.md](TEST_CONSOLIDATION_PLAN.md).

**Recommendation:** Start with Batch A (storage consolidation) as it has clear domain boundaries and moderate complexity.

---

**Generated:** January 25, 2026
**Author:** GitHub Copilot (Claude Opus 4.5)
**Phase:** 3 Planning (Source Files Only)
**Status:** Batch A & B COMPLETED ✅ | Batch C pending

---

## Implementation Results (January 25, 2026)

### Batch A - Storage Layer ✅ COMPLETED
**Files Created (4):**
- `src/main/java/datingapp/storage/H2ModerationStorage.java` (Blocks + Reports)
- `src/main/java/datingapp/storage/H2ProfileDataStorage.java` (ProfileNotes + ProfileViews)
- `src/main/java/datingapp/storage/H2MetricsStorage.java` (PlatformStats + DailyPicks + UserAchievements)
- `src/main/java/datingapp/storage/H2SocialStorage.java` (FriendRequests + Notifications)

**Files Deleted (9):**
- H2BlockStorage.java, H2ReportStorage.java
- H2ProfileNoteStorage.java, H2ProfileViewStorage.java
- H2PlatformStatsStorage.java, H2DailyPickViewStorage.java, H2UserAchievementStorage.java
- H2FriendRequestStorage.java, H2NotificationStorage.java

**Files Updated:**
- `ServiceRegistry.java` - New imports and buildH2 method updated
- `H2StorageIntegrationTest.java` - Uses H2ModerationStorage.blocks()
- `H2DailyPickViewStorageTest.java` - Uses H2MetricsStorage.dailyPicks()

**Net Change:** -5 storage files (9 deleted - 4 created)

### Batch B - UI Layer ✅ COMPLETED
**Files Created (2):**
- `src/main/java/datingapp/ui/util/UiServices.java` (Toast + ImageCache)
- `src/main/java/datingapp/ui/controller/PopupControllers.java` (MatchPopup + AchievementPopup)

**Files Deleted (4):**
- ToastService.java, ImageCache.java
- MatchPopupController.java, AchievementPopupController.java

**Files Updated:**
- `match_popup.fxml` - fx:controller="datingapp.ui.controller.PopupControllers$MatchPopup"
- `achievement_popup.fxml` - fx:controller="datingapp.ui.controller.PopupControllers$AchievementPopup"
- `ProfileViewModel.java` - ToastService → UiServices.Toast
- `ProfileController.java` - ToastService → UiServices.Toast

**Net Change:** -2 UI files (4 deleted - 2 created)

### Validation Results
- **Tests:** 535 passed, 0 failures, 0 errors ✅
- **Compilation:** BUILD SUCCESS ✅
- **Total Files Reduced:** -7 main source files (81 → 74) ✅ VERIFIED
- **Storage Files:** 18 → 13 (-5 files) ✅ VERIFIED
- **UI Util Files:** 4 → 3 (-1 files) ✅ VERIFIED
- **UI Controller Files:** 11 → 10 (-1 files) ✅ VERIFIED
- **Actual vs Target:** Achieved -7 files vs planned -7 from Batch A+B (100%)

### Remaining Work
- **Batch C (Optional):** CLI Handler consolidation pending (-2 files if implemented)
