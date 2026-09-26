# Implementation Plan: Thread-Safety & Mutable Singletons

**Status:** ✅ **COMPLETED** (2026-03-08)

**Source Report:** `Generated_Report_Generated_By_Qwen3.5-256K_21.02.2026.md` (Finding F-016)

## Completion Notes (2026-03-08)

- ✅ `AppSession` now uses lock-free state via `AtomicReference<User>` and no longer synchronizes read/write session operations.
- ✅ `ViewModelFactory` now has explicit `dispose()` lifecycle cleanup and robust listener unbinding/rebinding behavior for `reset()`.
- ✅ `DatingApp.stop()` now calls `viewModelFactory.dispose()` to prevent session-listener leaks on shutdown.
- ✅ Added regression coverage in `UserSessionTest` for read responsiveness while listener work is in progress.
- ✅ Stabilized async latest-wins behavior in `ViewModelAsyncScope` by serializing keyed task registration (`version + handle`), eliminating a race that could cancel the newest task under concurrent submissions.
- ✅ Verified dashboard concurrency sentinel stability with 3 consecutive passes of `DashboardViewModelTest.shouldHandleConcurrentRefreshes`.

## 1. Goal Description
The `AppSession` class currently implements a mutable singleton pattern using `synchronized` blocks that encapsulate both state changes and listener notifications. This design carries a substantial risk of deadlocks (e.g., if a listener invokes a method that blocks while the thread holds the `AppSession` lock) and creates unnecessary contention across the application. Specifically, the JavaFX UI threads and potential background CLI task threads interact with `AppSession.getCurrentUser()`, which is a hotspot.

**Objective:**
Refactor `AppSession` to utilize lock-free concurrency utilities (such as `AtomicReference` for the `User` state) and ensure that `notifyListeners` is invoked *outside* of any monitor lock, preventing thread deadlocks and improving application responsiveness.

## 2. Proposed Changes

### `datingapp.core`

#### [MODIFY] `AppSession.java`
- Replace `private User currentUser;` with `private final java.util.concurrent.atomic.AtomicReference<User> currentUser = new AtomicReference<>(null);`
- Remove the `synchronized` keyword from `getCurrentUser()`, `setCurrentUser()`, `isLoggedIn()`, `isActive()`, and `reset()`.
- Update methods to interact safely step-by-step:
  - `getCurrentUser()`: `return currentUser.get();`
  - `isLoggedIn()`: `return currentUser.get() != null;`
  - `isActive()`: `User u = currentUser.get(); return u != null && u.getState() == UserState.ACTIVE;`
- Update `setCurrentUser(User user)` to perform the update and listener notification without holding a lock:
  ```java
  public void setCurrentUser(User user) {
      this.currentUser.set(user);
      notifyListeners(user);
  }
  ```
- *Rational:* Since `listeners` is a `CopyOnWriteArrayList`, iterating over it via `notifyListeners(user)` is thread-safe. By removing the `synchronized` modifier on `setCurrentUser`, we guarantee that listener executions happen fully lock-free with respect to the `AppSession` singleton, entirely eliminating the deadlock risk.

### `datingapp.ui.viewmodel`

#### [MODIFY] `ViewModelFactory.java`
- Address the associated listener memory leak (identified related to session state management).
- In `initializeSessionBinding()`, wrap the `sessionListener` with a `WeakReference` logic internally or simply guarantee `session.removeListener(sessionListener)` is definitively called when the UI drops the view model factory. The safest approach is ensuring `reset()` correctly cleans up:
  - Double check the bounds and add an `@PreDestroy` equivalent or explicit application shutdown hook to invoke `reset()`, so listeners are safely swept.

## 3. Verification Plan

### Automated Tests
- ✅ `UserSessionTest` updated with a listener-blocking concurrency regression that verifies reads stay responsive.
- ✅ `ViewModelAsyncScopeTest` + `DashboardViewModelTest` executed for async/session interaction safety.
- ✅ `DashboardViewModelTest#shouldHandleConcurrentRefreshes` executed 3 consecutive times and passed.

### Manual Verification
1. Start the application in JavaFX mode (`mvn javafx:run`).
2. Log in with an account, click across different tabs (which query the session state).
3. Log out and log back in rapidly to mimic thread contention.
4. If no UI freezing occurs, thread-safety restructuring is verified active and performant.
