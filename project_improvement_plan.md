# Clean Next Steps – Project Improvement Plan

Organized and deduplicated list of suggested improvements for the JavaFX + Maven dating app project.
Items are grouped by theme and roughly prioritized.

---

# 1. High-Impact UI / UX Improvements
Improvements users will immediately notice.

## Profile UI
- Replace raw `lat,lon` input with a **location dialog/wizard** with validation and clear error messages.
- Improve **photo management**:
  - Increase limit to **6 photos**
  - Enforce file type filters
  - Enforce file size caps
  - Add replacement prompt when overwriting
- Show **photo count indicator** (`Photos X/6`) so users understand limits.
- Improve **save feedback flow**:
  - Disable buttons while saving
  - Show success/failure toast
  - Navigate only on successful save

## Daily Pick & Standouts
- Add **detail card** explaining why the pick was selected.
- Add **Viewed state** so users know if they already opened the pick.
- Add **empty state message** (`No pick today`) when no pick exists.
- Fix **context handoff**: Selecting a standout candidate from the dashboard navigates to the matching screen but loses the specific candidate context (P1.4). Fix this so the user actually sees the selected candidate.

## Chat UI
- Fix **presence indicator logic** so unknown presence does not show as online.
- Add **typing indicator UI** using `remoteTyping` binding.
- Verify **ChatController input clearing behavior** so the input field clears only after a successful send.
- **Live Real-time Chat Refresh**: Messaging currently only updates when navigating to the screen (P1.2, F-042). Implement a polling or background refresh mechanism in `ChatViewModel`. *Immediate action: comment in the appropriate place and implement a refresh button properly.*
- **"Enter to Send" & Message Constraints**: Pressing "Enter" doesn't send messages (P1.1). Fix this, implement character limits, and provide better error feedback when a message fails to send.

## General UI & GUI Parity
- Replace broken **1×1 default avatar asset** with a proper placeholder.
- Identify and fix **inconsistent visual patterns**, including:
  - Spacing
  - Typography
  - Button styles
- **GUI Parity - Profile Notes**: The backend supports private profile notes, but there is no way to view/edit them in the UI (P3.5). Bridge this CLI feature to the JavaFX GUI.
- **GUI Parity - Unmatch & Friend Request Initiation**: "Unmatch" and "Send Friend Request" exist in CLI/Core but are missing UI controls in JavaFX (P1.5, P1.7). Add these essential social loop features.
- **Persistence of Preferences & Theme**: User filters (age, distance) and theme choices (Light/Dark mode) are not consistently persisted or restored across app restarts (P1.6, BUG-9).

---

# 2. Performance & UX Stability
Improve responsiveness and avoid UI freezes.

- Audit **UI thread blocking calls** in `ui/` and `ui/async/`.
- Scan `LocalPhotoStore.java` for **IO or caching hotspots**.
- Introduce **async loading or throttling** where disk access may stall the UI.
- Audit **image loading flows** for proper error handling and fallback states:
  - Broken photo
  - Missing file
  - Slow disk
- Review **StatsController and StatsViewModel refresh logic** for inefficiencies.
- Ensure **NavigationService screen transitions** maintain consistent history/back behavior.

---

# 3. Architecture & Code Quality
Structural cleanup to reduce technical debt.

- `ProfileService` currently mixes **multiple responsibilities**:
  - User lookup
  - Scoring
  - Tips
  - Achievements
  → Consider splitting into smaller services.

- `TrustSafetyService` has **five constructor overloads**.
  → Replace with a builder or named factory pattern.

- `LifestyleMatcher` reportedly has **multiple duplicate `isAcceptable()` overloads**.
  → Verify and consolidate if necessary.

- `RecommendationService` may **bypass the injected clock**.
  → Verify to maintain deterministic tests.

- Verify whether **StandoutsController requires external context** for filtering or seeding.

---

# 4. Backend & Data Integrity
Ensure backend correctness and reliability.

- **Atomic Relationship Transitions**: High-risk operations like "Like -> Match" or friend transitions are not fully atomic across different storage engines (U-01, F-001). Ensure these dual-writes are handled within a transaction or a compensating rollback strategy (e.g. verify transaction boundaries in `JdbiMatchmakingStorage`).
- Review **REST API authentication strategy**:
  - Either implement authentication
  - Or clearly document that the API is **development-only**
- Add **presence polling hook** (10–15 seconds) using `ViewModelAsyncScope`.

---

# 5. Testing Gaps
Improve test coverage and reliability.

- Add **integration tests for the REST API** by spinning up `RestApiServer` and verifying endpoints.
- Expand **UI-adjacent test coverage**, especially:
  - `StatsHandlerTest`
  - ViewModel tests
  - `src/test/java/datingapp/ui/screen/`
