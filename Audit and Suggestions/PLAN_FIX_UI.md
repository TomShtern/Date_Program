# Implementation Plan: UI/JavaFX & UX Fixes

**Source:** `AUDIT_PROBLEMS_UI.md`
**Scope:** JavaFX controllers, ViewModels, navigation, and UX behavior
**Target:** Fix all findings (C-06 through UI-05)

---

## Pre-Implementation Checklist

Before starting any fix:
1. Run `mvn test` to confirm all tests pass (green baseline)
2. Run `mvn spotless:apply` to ensure formatting is clean
3. Create a git branch: `git checkout -b fix/audit-ui-javafx`

After each fix group:
- Run `mvn test` to verify no regressions
- Run `mvn spotless:apply && mvn verify` before committing

---

## Fix 1: C-06 — ProfileController.handleSave() cleanup() Called BEFORE save()
**File:** `src/main/java/datingapp/ui/controller/ProfileController.java`
**Lines:** 594-598

### Current Code:
```java
private void handleSave() {
    cleanup(); // destroys all subscriptions FIRST
    viewModel.save(); // save may trigger property updates — nobody listening
    NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
}
```

### Steps:
1. Open `ProfileController.java`
2. Reorder lines 594-598 so `save()` happens first:
   ```java
   @FXML
   @SuppressWarnings("unused")
   private void handleSave() {
       viewModel.save();
       cleanup();
       NavigationService.getInstance().navigateTo(NavigationService.ViewType.DASHBOARD);
   }
   ```
3. That's it — one-line reorder

### Test:
- Manual: verify that saving profile data persists correctly before navigating away
- Existing tests pass

---

## Fix 2: C-07 — MatchesController.handleStartChat() Missing Navigation Context
**File:** `src/main/java/datingapp/ui/controller/MatchesController.java`
**Lines:** 647-650

### Current Code:
```java
private void handleStartChat(MatchCardData match) {
    logInfo("Starting chat with match: {}", match.userName());
    NavigationService.getInstance().navigateTo(NavigationService.ViewType.CHAT);
}
```

### Steps:
1. Open `MatchesController.java`
2. Set the navigation context before navigating. Change `handleStartChat()` to:
   ```java
   private void handleStartChat(MatchCardData match) {
       logInfo("Starting chat with match: {}", match.userName());
       NavigationService.getInstance().setNavigationContext(match.userId());
       NavigationService.getInstance().navigateTo(NavigationService.ViewType.CHAT);
   }
   ```
3. Verify that `ChatController.initialize()` already calls `consumeNavigationContext()` and handles the UUID. Open `ChatController.java` to confirm it has:
   ```java
   Object context = NavigationService.getInstance().consumeNavigationContext();
   if (context instanceof UUID userId) {
       viewModel.selectConversationWithUser(userId);
   }
   ```
   If this code doesn't exist, add it to `ChatController.initialize()`

### Test:
- Click "Message" on a match card → should navigate to chat with that user's conversation selected

---

## Fix 3: C-08 — MatchesController 5 INDEFINITE Animations Never Stopped
**File:** `src/main/java/datingapp/ui/controller/MatchesController.java`
**Lines:** 252-372 (multiple animation sites)

### Steps:
1. Open `MatchesController.java`
2. Add a field to track all running animations:
   ```java
   private final List<javafx.animation.Animation> runningAnimations = new ArrayList<>();
   ```
   Add import: `import javafx.animation.Animation;`
3. Identify all 5 INDEFINITE animations and store their references. They are:
   - **Line 256**: `spawnTimeline` (heart particle spawner) — `setCycleCount(Animation.INDEFINITE)`
   - **Line 347**: `glowPulse` (glow pulse on icon) — `setCycleCount(Animation.INDEFINITE)`
   - **Line 357**: `breathe` (scale breathing) — `setCycleCount(Animation.INDEFINITE)`
   - **Line 368**: `floatAnim` (empty state float) — `setCycleCount(Animation.INDEFINITE)`
   - **Line 493**: `badgePulse` (new badge pulse) — `setCycleCount(Animation.INDEFINITE)`
4. After each `.play()` call, add `runningAnimations.add(animation);`:
   - After `spawnTimeline.play();` (line 257) add: `runningAnimations.add(spawnTimeline);`
   - After `glowPulse.play();` (line 349) add: `runningAnimations.add(glowPulse);`
   - After `breathe.play();` (line 360) add: `runningAnimations.add(breathe);`
   - After `floatAnim.play();` (line 371) add: `runningAnimations.add(floatAnim);`
   - After `badgePulse.play();` (line 495) add: `runningAnimations.add(badgePulse);`
5. Override the `cleanup()` method from `BaseController`:
   ```java
   @Override
   public void cleanup() {
       for (Animation anim : runningAnimations) {
           anim.stop();
       }
       runningAnimations.clear();
       if (particleLayer != null) {
           particleLayer.getChildren().clear();
       }
       super.cleanup();
   }
   ```

### Test:
- Navigate to Matches screen, then away → verify no CPU usage from orphaned animations
- Existing tests pass

---

## Fix 4: H-12 — FX Thread Violations in MatchesViewModel, LoginViewModel, StatsViewModel
**Files:**
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`

### Steps for MatchesViewModel:
1. Open `MatchesViewModel.java`
2. The methods `refreshMatches()`, `refreshLikesReceived()`, `refreshLikesSent()` all perform blocking storage calls on the calling thread (likely FX thread)
3. Wrap the storage calls in virtual threads, and update ObservableLists via `Platform.runLater()`:
   ```java
   private void refreshMatches() {
       if (currentUser == null) {
           currentUser = AppSession.getInstance().getCurrentUser();
       }
       if (currentUser == null) { return; }

       loading.set(true);
       final User user = currentUser;
       Thread.ofVirtual().name("matches-refresh").start(() -> {
           try {
               List<Match> activeMatches = matchStorage.getActiveMatchesFor(user.getId());
               List<MatchCardData> cards = new ArrayList<>();
               for (Match match : activeMatches) {
                   UUID otherUserId = match.getOtherUser(user.getId());
                   User otherUser = userStorage.get(otherUserId);
                   if (otherUser != null) {
                       String timeAgo = formatTimeAgo(match.getCreatedAt());
                       cards.add(new MatchCardData(
                               match.getId(), otherUser.getId(), otherUser.getName(), timeAgo, match.getCreatedAt()));
                   }
               }
               Platform.runLater(() -> {
                   matches.setAll(cards);
                   matchCount.set(cards.size());
                   loading.set(false);
               });
           } catch (Exception e) {
               Platform.runLater(() -> {
                   loading.set(false);
                   notifyError("Failed to load matches", e);
               });
           }
       });
   }
   ```
4. Apply the same pattern to `refreshLikesReceived()` and `refreshLikesSent()`

### Steps for LoginViewModel:
1. Open `LoginViewModel.java`
2. `loadUsers()` (line 77) does `userStorage.findAll()` on calling thread
3. Wrap in virtual thread:
   ```java
   private void loadUsers() {
       if (disposed.get()) { return; }
       loading.set(true);
       Thread.ofVirtual().name("login-load-users").start(() -> {
           try {
               List<User> allUsers = userStorage.findAll();
               Platform.runLater(() -> {
                   users.setAll(allUsers);
                   applyFilter(filterText.get());
                   loading.set(false);
               });
           } catch (Exception e) {
               Platform.runLater(() -> {
                   loading.set(false);
                   notifyError("Failed to load users", e);
               });
           }
       });
   }
   ```

### Steps for StatsViewModel:
1. Open `StatsViewModel.java`
2. `refresh()` (line 85) does blocking storage calls
3. Wrap in virtual thread, update properties on FX thread via `Platform.runLater()`

### Test:
- Existing tests pass
- UI should remain responsive during data loading

---

## Fix 5: H-13 — Memory Leaks: Untracked Listeners
**Files:** `ProfileController.java`, `DashboardController.java`, `MatchesController.java`

### Steps for ProfileController:
1. Open `ProfileController.java`
2. Find all raw `addListener()` calls not wrapped in `addSubscription()`:
   - **Line 443**: `heightField.textProperty().addListener(...)` — This is a raw listener
   - **Line 456**: `heightField.focusedProperty().addListener(...)` — Another raw listener
3. Convert these to use `subscribe()` + `addSubscription()`:
   ```java
   // Replace line 443's addListener with:
   addSubscription(heightField.textProperty().subscribe(newVal -> {
       if (newVal == null || newVal.isBlank()) {
           viewModel.heightProperty().set(null);
           return;
       }
       try {
           int height = Integer.parseInt(newVal.trim());
           viewModel.heightProperty().set(height);
       } catch (NumberFormatException _) {
           LoggerFactory.getLogger(ProfileController.class).trace("Invalid height input: {}", newVal);
       }
   }));

   // Replace line 456's addListener with:
   addSubscription(heightField.focusedProperty().subscribe(isNowFocused -> {
       if (Boolean.FALSE.equals(isNowFocused)) {
           validateHeightRange();
       }
   }));
   ```

### Steps for MatchesController:
1. Open `MatchesController.java`
2. Find **line 175**: `sectionGroup.selectedToggleProperty().addListener(...)` — raw listener
3. Convert to subscribe pattern:
   ```java
   addSubscription(sectionGroup.selectedToggleProperty().subscribe(newToggle -> {
       if (newToggle == null) {
           matchesTabButton.setSelected(true);
           return;
       }
       currentSection = (Section) newToggle.getUserData();
       updateHeader();
       populateCards();
   }));
   ```

### Steps for DashboardController:
1. Open `DashboardController.java` (find it in the project)
2. Search for all `addListener(` calls not wrapped in `addSubscription()`
3. Convert each to the subscribe pattern

### Test:
- Verify `getSubscriptionCount()` in BaseController increases appropriately
- Navigate away and back — no duplicate listeners

---

## Fix 6: M-12 — ProfileController.validateHeightRange() Hardcoded Thresholds
**File:** `src/main/java/datingapp/ui/controller/ProfileController.java`
**Lines:** 463-476

### Steps:
1. Open `ProfileController.java`
2. Add a static config reference at top of class:
   ```java
   private static final AppConfig CONFIG = AppConfig.defaults();
   ```
3. Import: `import datingapp.core.AppConfig;`
4. Change `validateHeightRange()` (line 467) from:
   ```java
   if (height < 120 || height > 250) {
   ```
   To:
   ```java
   if (height < CONFIG.minHeightCm() || height > CONFIG.maxHeightCm()) {
   ```
5. Update the toast message to use config values:
   ```java
   Toast.showWarning("Please enter a height between " + CONFIG.minHeightCm() + "-" + CONFIG.maxHeightCm() + " cm");
   ```

### Test:
- Verify the validation uses config values (50-300 from AppConfig.defaults)

---

## Fix 7: M-13 — ProfileController.handleEditDealbreakers() Uses HashSet for Enums
**File:** `src/main/java/datingapp/ui/controller/ProfileController.java`
**Lines:** 746-750

### Steps:
1. Change lines 746-750 from `java.util.HashSet` to `java.util.EnumSet`:
   ```java
   java.util.Set<Lifestyle.Smoking> selectedSmoking = java.util.EnumSet.copyOf(current.acceptableSmoking());
   java.util.Set<Lifestyle.Drinking> selectedDrinking = java.util.EnumSet.copyOf(current.acceptableDrinking());
   java.util.Set<Lifestyle.WantsKids> selectedKids = java.util.EnumSet.copyOf(current.acceptableKidsStance());
   java.util.Set<Lifestyle.LookingFor> selectedLookingFor = java.util.EnumSet.copyOf(current.acceptableLookingFor());
   ```
2. Handle the case where the source sets might be empty (EnumSet.copyOf throws on empty):
   ```java
   java.util.Set<Lifestyle.Smoking> selectedSmoking = current.acceptableSmoking().isEmpty()
       ? java.util.EnumSet.noneOf(Lifestyle.Smoking.class)
       : java.util.EnumSet.copyOf(current.acceptableSmoking());
   ```
   Apply this pattern to all 4 sets

### Test:
- Open dealbreakers dialog, make changes, save — should work identically

---

## Fix 8: M-14 — MatchesController Random Status Dots in Production
**File:** `src/main/java/datingapp/ui/controller/MatchesController.java`
**Lines:** 507-510

### Current Code:
```java
String[] statuses = {"status-online", "status-away", "status-offline"};
statusDot.getStyleClass().add(statuses[RANDOM.nextInt(statuses.length)]);
```

### Steps:
1. Replace the random assignment with a default offline status:
   ```java
   statusDot.getStyleClass().add("status-offline");
   ```
2. Add a TODO comment for when real presence tracking is implemented:
   ```java
   // TODO: Replace with real presence status when user presence tracking is implemented
   statusDot.getStyleClass().add("status-offline");
   ```

### Test:
- All match cards should show offline status consistently

---

## Fix 9: M-15 — MatchesController Unbounded Particle Creation
**File:** `src/main/java/datingapp/ui/controller/MatchesController.java`
**Lines:** 252-257 (spawner) and 271-325 (spawn method)

### Steps:
1. Add a maximum particle cap constant:
   ```java
   private static final int MAX_PARTICLES = 30;
   ```
2. In `spawnFloatingHeart()` (line 271), add a size check at the top:
   ```java
   private void spawnFloatingHeart() {
       if (particleLayer == null) { return; }
       if (particleLayer.getChildren().size() >= MAX_PARTICLES) { return; }
       // ... rest of existing code
   }
   ```

### Test:
- On slow devices, particle count stays bounded at 30

---

## Fix 10: M-17 — PreferencesViewModel.savePreferences() No Input Validation
**File:** `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`

### Steps:
1. Open `PreferencesViewModel.java`
2. Find the `savePreferences()` method
3. Add validation before saving:
   ```java
   private static final AppConfig CONFIG = AppConfig.defaults();

   public boolean savePreferences() {
       int minAge = parseIntOrDefault(minAgeProperty.get(), CONFIG.minAge());
       int maxAge = parseIntOrDefault(maxAgeProperty.get(), CONFIG.maxAge());
       int maxDistance = parseIntOrDefault(maxDistanceProperty.get(), 50);

       // Validate bounds
       if (minAge < CONFIG.minAge() || minAge > CONFIG.maxAge()) {
           notifyError("Min age must be between " + CONFIG.minAge() + " and " + CONFIG.maxAge());
           return false;
       }
       if (maxAge < CONFIG.minAge() || maxAge > CONFIG.maxAge()) {
           notifyError("Max age must be between " + CONFIG.minAge() + " and " + CONFIG.maxAge());
           return false;
       }
       if (minAge > maxAge) {
           notifyError("Min age cannot be greater than max age");
           return false;
       }
       if (maxDistance < 1 || maxDistance > CONFIG.maxDistanceKm()) {
           notifyError("Distance must be between 1 and " + CONFIG.maxDistanceKm() + " km");
           return false;
       }
       // ... proceed with save
   }
   ```
4. Add helper:
   ```java
   private static int parseIntOrDefault(String value, int defaultVal) {
       try { return Integer.parseInt(value.trim()); }
       catch (NumberFormatException e) { return defaultVal; }
   }
   ```

### Test:
- Setting minAge > maxAge should show error and not save

---

## Fix 11: M-18 — PreferencesViewModel Incomplete OTHER Gender Handling
**File:** `src/main/java/datingapp/ui/viewmodel/PreferencesViewModel.java`

### Steps:
1. Find the gender preference handling logic in `savePreferences()`
2. Look for a switch or if-chain that maps gender preferences
3. Ensure `Gender.OTHER` is handled as its own case and doesn't fall through to EVERYONE:
   ```java
   // When user is interested in OTHER only, save that explicitly
   if (interestedIn.contains(Gender.OTHER) && interestedIn.size() == 1) {
       user.setInterestedIn(EnumSet.of(Gender.OTHER));
   }
   ```
4. Find the exact code that falls through and fix the conditional

---

## Fix 12: M-19 — ProfileViewModel.savePhoto() Single Background Thread Tracking
**File:** `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`

### Steps:
1. Open `ProfileViewModel.java`
2. Find `savePhoto()` and the thread tracking field
3. Replace single thread reference with a list:
   ```java
   private final List<Thread> backgroundThreads = new ArrayList<>();
   ```
4. In `savePhoto()`, add the new thread to the list:
   ```java
   Thread photoThread = Thread.ofVirtual().name("photo-save").start(() -> { ... });
   backgroundThreads.add(photoThread);
   ```
5. In `dispose()`, interrupt all tracked threads:
   ```java
   public void dispose() {
       for (Thread t : backgroundThreads) {
           t.interrupt();
       }
       backgroundThreads.clear();
   }
   ```

---

## Fix 13: M-21 — NavigationService Singleton Context Not Thread-Safe
**File:** `src/main/java/datingapp/ui/NavigationService.java`
**Line:** 37

### Steps:
1. Change the `navigationContext` field from plain `Object` to `AtomicReference`:
   - Replace line 37:
     ```java
     private Object navigationContext;
     ```
     With:
     ```java
     private final java.util.concurrent.atomic.AtomicReference<Object> navigationContext = new java.util.concurrent.atomic.AtomicReference<>();
     ```
2. Update `setNavigationContext()` (line 288):
   ```java
   public void setNavigationContext(Object context) {
       this.navigationContext.set(context);
   }
   ```
3. Update `consumeNavigationContext()` (lines 292-296):
   ```java
   public Object consumeNavigationContext() {
       return navigationContext.getAndSet(null);
   }
   ```

### Test:
- Existing navigation tests pass

---

## Fix 14: M-22 — StatsViewModel Silent Degradation with Null Storage
**File:** `src/main/java/datingapp/ui/viewmodel/StatsViewModel.java`
**Lines:** 47-53

### Steps:
1. In `refresh()`, add user-visible indication when storage is unavailable:
   ```java
   if (likeStorage == null) {
       responseRate.set("N/A");
       logInfo("Stats unavailable: storage not configured");
   }
   ```
2. Optionally, add an `ErrorHandler` field (like other ViewModels have) and notify:
   ```java
   private ErrorHandler errorHandler;
   public void setErrorHandler(ErrorHandler handler) { this.errorHandler = handler; }
   ```
   In `refresh()`:
   ```java
   if (likeStorage == null && errorHandler != null) {
       Platform.runLater(() -> errorHandler.onError("Stats are partially unavailable"));
   }
   ```

---

## Fix 15: L-06 — LoginViewModel.createUser() Double Save
**File:** `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
**Lines:** 286-299

### Steps:
1. Remove the first `userStorage.save(newUser)` at line 286
2. Move it after the activation check:
   ```java
   // Set default dealbreakers (none) - marks section as reviewed for profile completion
   newUser.setDealbreakers(Dealbreakers.none());

   // Attempt to activate now that profile is complete
   if (newUser.isComplete()) {
       newUser.activate();
       logInfo("User {} is complete and activated. State: {}", newUser.getName(), newUser.getState());
   }

   // Single save after all modifications
   userStorage.save(newUser);
   ```
3. Delete the duplicate save

---

## Fix 16: L-07 — DashboardController Unnecessary Objects.requireNonNull
**File:** `src/main/java/datingapp/ui/controller/DashboardController.java`

### Steps:
1. Open `DashboardController.java`
2. Find the `Objects.requireNonNull(obs)` call in a scene property change listener
3. Remove the unnecessary null check since JavaFX never passes null for the observable parameter

---

## Fix 17: UI-01 — ImageCache Has No Eviction Policy
**File:** `src/main/java/datingapp/ui/util/ImageCache.java`

### Steps:
1. **Already Fixed!** — Reading the actual source code shows `ImageCache` already uses an LRU `LinkedHashMap` with `MAX_CACHE_SIZE = 100` and `removeEldestEntry()` override (lines 31-40)
2. The audit finding is **stale** — mark as resolved, no changes needed

---

## Fix 18: UI-02 — BaseController Animations May Never Be Stopped
**File:** `src/main/java/datingapp/ui/controller/BaseController.java`

### Steps:
1. This is addressed by Fix 3 (C-08) for MatchesController specifically
2. For a general solution, add animation tracking to BaseController:
   ```java
   private final List<javafx.animation.Animation> animations = new ArrayList<>();

   protected void trackAnimation(javafx.animation.Animation animation) {
       if (animation != null) {
           animations.add(animation);
       }
   }
   ```
3. In `cleanup()`, add animation stopping:
   ```java
   public void cleanup() {
       // Stop all tracked animations
       animations.forEach(javafx.animation.Animation::stop);
       animations.clear();

       // ... existing subscription cleanup
       subscriptions.forEach(Subscription::unsubscribe);
       subscriptions.clear();
       // ... existing overlay cleanup
   }
   ```
4. Update MatchesController (Fix 3) to use `trackAnimation()` instead of its own list

---

## Fix 19: UI-03 — Navigation Context Can Be Lost
**File:** `src/main/java/datingapp/ui/NavigationService.java`

### Steps:
1. Fix 13 (M-21) addresses thread safety with `AtomicReference`
2. To prevent context loss from quick navigation, the `consumeNavigationContext()` already atomically gets-and-sets to null
3. Consider adding a guard in `navigateTo()` that checks if context was set but not consumed:
   ```java
   public void navigateTo(ViewType viewType) {
       // Log if context was set but never consumed (debugging aid)
       Object unconsumed = navigationContext.get();
       if (unconsumed != null && logger.isDebugEnabled()) {
           logger.debug("Navigation context {} was not consumed before navigating to {}", unconsumed, viewType);
       }
       navigateWithTransition(viewType, TransitionType.NONE);
   }
   ```

---

## Fix 20: UI-04 — ViewModelFactory Creates Singletons: Stale State After Logout
**File:** `src/main/java/datingapp/ui/ViewModelFactory.java`

### Steps:
1. The `reset()` method already exists (lines 213-222) and nulls all cached ViewModels
2. The issue is that `reset()` is never called on logout
3. Find the logout flow. In `NavigationService` or the login screen controller, ensure that on logout:
   ```java
   // In the logout handler (wherever it lives):
   AppSession.getInstance().logout();
   NavigationService.getInstance().getViewModelFactory().reset();
   ImageCache.clearCache();
   NavigationService.getInstance().clearHistory();
   NavigationService.getInstance().navigateTo(NavigationService.ViewType.LOGIN);
   ```
4. Also, before resetting, call `dispose()` on each cached ViewModel that has a dispose method:
   ```java
   public void reset() {
       if (loginViewModel != null) loginViewModel.dispose();
       if (dashboardViewModel != null) dashboardViewModel.dispose();
       if (matchesViewModel != null) matchesViewModel.dispose();
       if (statsViewModel != null) statsViewModel.dispose();
       // ... then null them all
       loginViewModel = null;
       dashboardViewModel = null;
       // ... etc
   }
   ```

---

## Fix 21: UI-05 — getFirst() Calls: Non-Standard List API
**Files:** `ProfileViewModel.java`, `MatchQualityService.java`, `StandoutsService.java`, `UiComponents.java`

### Steps:
1. Search all files for `.getFirst()` usage:
   ```
   grep -rn "\.getFirst()" src/main/java/
   ```
2. For each occurrence, replace with guarded access:
   ```java
   // Instead of:
   String first = list.getFirst();

   // Use:
   String first = list.isEmpty() ? defaultValue : list.get(0);
   ```
3. Specific locations to check:
   - `MatchQualityService.java` line 150: `highlights.getFirst()` → `highlights.isEmpty() ? getCompatibilityLabel() : highlights.get(0)`
   - `MatchQualityService.InterestMatcher.formatSharedInterests()` lines 266-270: `names.getFirst()` → `names.get(0)`
   - Find and fix occurrences in `ProfileViewModel`, `StandoutsService`, `UiComponents`

### Test:
- Test with empty lists to verify no `NoSuchElementException`

---

## Post-Implementation

1. Run full test suite: `mvn test`
2. Run quality checks: `mvn spotless:apply && mvn verify`
3. Run the JavaFX GUI: `mvn javafx:run` — manually test:
   - Profile save → navigate back
   - Click "Message" on match card → opens correct chat
   - Navigate between screens → no animation CPU leak
   - Logout → login as different user → no stale data
4. Commit: `git add -A && git commit -m "fix: resolve UI/JavaFX audit findings C-06 through UI-05"`
