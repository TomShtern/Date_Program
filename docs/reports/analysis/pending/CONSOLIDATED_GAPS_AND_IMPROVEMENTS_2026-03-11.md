# Consolidated Gaps & Improvements List
**Original generated:** 2026-03-11
**Re-verified against current source:** 2026-03-12
**Verification basis:** `src/main/java`, `src/test/java`, `pom.xml`, current JavaFX/UI files, current JDBI schema/storage code
**Notes:** invalid or already-resolved claims from the original draft were removed; wording was tightened where the original report overstated the issue.

---

## 🔴 Security

| #   | Item                                                                                                                                                                                 |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| S2  | REST API endpoints are intentionally unauthenticated and suitable only for localhost/local-IPC use; production or network exposure still requires real authentication/authorization. |
| S3  | `GET /api/conversations/{id}/messages` accepts a conversation ID without API-layer ownership checks; the handler validates existence but not caller identity.                        |
| S4  | REST API has no authentication layer by design; current code comments explicitly warn not to expose it publicly without security middleware.                                         |
| S5  | No API rate limiting is implemented; acceptable for localhost-only development, but required before broader deployment.                                                              |
| S12 | `CandidateFinder` logging no longer prints raw coordinates, but it still logs user references around location-related decisions; keep those logs at debug/trace only.                |

---

## 🔴 Concurrency & Thread Safety

**Status:** the original broad concurrency section was mostly resolved before this re-audit. The remaining entries are narrowed residual risks, not the original data-structure bugs.

| #  | Item                                                                                                                                                                                           |
|----|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| C1 | ✅ Resolved — the originally reported non-thread-safe `HashMap` counter issue in `ActivityMetricsService` no longer exists.                                                                     |
| C2 | ✅ Resolved — `InProcessAppEventBus` now uses thread-safe subscriber handling (`CopyOnWriteArrayList`).                                                                                         |
| C3 | `MatchingViewModel` still has a residual swipe-timing race during rapid repeated interaction before the next candidate is fully advanced on the FX thread.                                     |
| C4 | `TrustSafetyService.applyAutoBanIfThreshold()` has been narrowed and partially hardened; the old “ban state committed before save failure” wording is no longer accurate in its original form. |
| C5 | `MatchingService.processSwipe()` no longer appears vulnerable to duplicate persistence in the originally reported way, but concurrent success-path side effects still merit caution.           |

---

## 🟠 Resource & Memory Leaks

The original `R1`–`R5` claims did **not** survive verification against the current snapshot:

- `DatabaseManager.shutdown()` exists and closes `HikariDataSource`
- `ChatViewModel` explicitly cancels both polling handles
- `ImageCache` has a hard upper bound via `UiConstants.IMAGE_CACHE_MAX_SIZE`
- the reported `LocalPhotoStore.savePhoto()` leak claim did not match current code

This section is intentionally left without active items after verification.

---

## 🟠 Runtime Bugs & Silent Failures

| #  | Item                                                                                                                                                                                                                       |
|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| B1 | `ConnectionService.markAsRead()` still returns silently on missing/unauthorized conversations; callers receive no success/failure signal.                                                                                  |
| B3 | `TrustSafetyService.updateMatchStateForBlock()` no longer fails silently in the original sense, but it still degrades via logging/early return instead of result-based propagation when optional collaborators are absent. |
| B5 | `JdbiUserStorage` age filtering still uses `DATEDIFF('YEAR', ...)`, which is calendar-year based and can be off by one around birthdays.                                                                                   |
| B6 | `RecommendationService.formatDuration()` still formats durations above 24 hours as raw hours (for example, `25:30:00`) rather than day-aware text.                                                                         |
| B7 | `ProfileCompletionSupport` still treats `(0.0, 0.0)` as “no location” because it checks literal coordinates instead of `hasLocationSet`.                                                                                   |

---

## 🟠 Database & Data Integrity

| #  | Item                                                                                                                                                                                                          |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| D2 | No composite candidate-discovery index exists for the main search dimensions; candidate queries still rely on a simpler index strategy plus in-memory filtering.                                              |
| D3 | Match-ending flows archive conversations rather than cascade-delete them; this is current behavior by design, but it means archival/visibility lifecycle must stay aligned with relationship lifecycle rules. |
| D4 | `ConnectionService.getConversations()` bulk-loads counterpart users, but it still performs per-conversation last-message reads, so a partial N+1 pattern remains.                                             |
| D6 | `UserStorage.findCandidates()` still does not apply DB-level `LIMIT`/`OFFSET`; qualifying candidates are loaded and filtered/scored further up the pipeline.                                                  |
| D7 | `TrustSafetyStorage.getBlockedUserIds()` still returns the full blocked set without pagination.                                                                                                               |
| D9 | Soft-delete columns exist across several tables, but dedicated `deleted_at` indexes are still missing from the schema initializer.                                                                            |

---

## 🟠 Event System Gaps

| #  | Item                                                                                                                                                        |
|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| E1 | `AppEvent.MatchCreated` is published but still has no subscriber handling it.                                                                               |
| E3 | `AppEvent.MessageSent` is published and consumed by metrics, but there is still no user-facing notification handler for new-message notifications.          |
| E4 | `SocialUseCases.blockUser()` still fires no event.                                                                                                          |
| E5 | `SocialUseCases.reportUser()` still fires no event, which blocks event-driven achievement/audit hooks such as `GUARDIAN`.                                   |
| E6 | There is still no dedicated `FriendRequestReceived` event; `requestFriendZone()` currently emits `RelationshipTransitioned(FRIEND_ZONE_REQUESTED)` instead. |
| E8 | JavaFX ViewModels still do not subscribe to `AppEventBus`; they mostly rely on polling or manual refresh.                                                   |

---

## 🟠 Missing Features

| #   | Item                                                                                                                                                                                   |
|-----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| F1  | Super Like remains under-modeled: UI can initiate it, but there is still no separate persistence/validation model enforcing it as a first-class interaction type.                      |
| F2  | Presence/typing indicators remain effectively no-op because `ViewModelFactory` still wires `NoOpUiPresenceDataAccess`.                                                                 |
| F3  | `GUARDIAN` achievement remains unreachable because reporting still publishes no event.                                                                                                 |
| F4  | `SELECTIVE` / `OPEN_MINDED` achievement checks are still not evaluated on every swipe action; they are evaluated only on narrower event paths.                                         |
| F5  | `ProfileSaved` is fired from `ProfileUseCases.saveProfile()`, but the achievement pipeline still does not consume it, leaving profile-completion achievements effectively unreachable. |
| F6  | `dailySuperLikeLimit` exists in config but is still not enforced in the JavaFX swipe flow.                                                                                             |
| F7  | Daily like/pass limit behavior remains inconsistent: UI paths still bypass or under-enforce parts of the configured daily-limit policy, especially for passes.                         |
| F8  | Pace preferences are still not editable from the main JavaFX profile editor.                                                                                                           |
| F9  | Profile verification still lacks a JavaFX flow and a REST endpoint.                                                                                                                    |
| F12 | Soft-delete retention config and purge methods exist, but there is still no scheduler wiring them into an automatic lifecycle.                                                         |
| F13 | Cleanup routines exist for multiple subsystems, but there is still no scheduler or startup wiring to execute them automatically.                                                       |
| F14 | There is still no user-initiated account deletion flow.                                                                                                                                |
| F15 | “Who Liked You” data loads, but there is still no premium gating/blur treatment.                                                                                                       |
| F16 | Undo still has no visible countdown timer in the JavaFX UI.                                                                                                                            |
| F17 | Standout/daily-pick cards still show score/rank without explaining *why* someone was chosen.                                                                                           |
| F18 | `CandidateFinder` still returns an empty result when the current user has no location, with no user-facing explanation.                                                                |
| F19 | `MatchesController` still hardcodes offline presence styling.                                                                                                                          |
| F20 | Delete message — no use case.                                                                                                                                                          |
| F21 | Delete conversation — no use case.                                                                                                                                                     |
| F22 | Mark all notifications read — no use case.                                                                                                                                             |
| F23 | Archive match — no use case.                                                                                                                                                           |
| F24 | No shared caching layer exists for hot matching/recommendation paths beyond local in-memory helpers.                                                                                   |
| F25 | Sensitive actions still lack dedicated audit logging.                                                                                                                                  |

---

## 🟠 Missing REST Endpoints

| #   | Endpoint                                                                                                                                                                                                                           | Backed by                                   |
|-----|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| A1  | `PUT /api/users/{id}/profile`                                                                                                                                                                                                      | `ProfileUseCases.saveProfile()`             |
| A2  | `GET /api/users/{id}/notifications`                                                                                                                                                                                                | `SocialUseCases.notifications()`            |
| A3  | `POST /api/notifications/{id}/read`                                                                                                                                                                                                | `SocialUseCases.markNotificationRead()`     |
| A4  | `POST /api/users/{id}/undo`                                                                                                                                                                                                        | `MatchingUseCases.undoSwipe()`              |
| A5  | `GET /api/users/{id}/standouts`                                                                                                                                                                                                    | `MatchingUseCases.standouts()`              |
| A6  | `POST /api/users/{id}/standouts/{id}/interact`                                                                                                                                                                                     | `MatchingUseCases.markStandoutInteracted()` |
| A9  | `GET /api/users/{id}/stats`                                                                                                                                                                                                        | stats/query layer                           |
| A10 | `GET /api/users/{id}/achievements`                                                                                                                                                                                                 | achievement/query layer                     |
| A12 | `GET /api/users/{id}/pending-likers`                                                                                                                                                                                               | `MatchingUseCases.pendingLikers()`          |
| A13 | `GET /api/users/{id}/match-quality/{matchId}`                                                                                                                                                                                      | `MatchingUseCases.matchQuality()`           |
| A14 | Several existing endpoints still bypass the use-case layer directly: `GET /api/users`, `GET /api/users/{id}`, `GET /api/users/{id}/candidates`, `GET /api/users/{id}/matches`, `GET /api/conversations/{conversationId}/messages`. |                                             |

**Verified note:** the friend-request endpoint already exists as `POST /api/users/{id}/friend-requests/{targetId}` (plural), and block/report endpoints already exist.

---

## 🟡 UI/UX Gaps

| #   | Item                                                                                                                                                |
|-----|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| U1  | `ChatController` send button still has no explicit loading/disable state for normal sends, allowing rapid duplicate submissions.                    |
| U2  | Profile note save/delete feedback is still inconsistent and text-only.                                                                              |
| U3  | Interest selection still relies mostly on validation after the fact; the dialog still does not proactively hard-stop checkbox selection at the cap. |
| U4  | `ProfileController` still has no unsaved-changes warning on navigation.                                                                             |
| U5  | `ProfileController` still does not clearly reset transient form state on navigation away.                                                           |
| U6  | Profile save failures still do not map cleanly to field-specific UI errors.                                                                         |
| U7  | Height validation still depends on config sanity; a bad config floor could still permit unrealistic values.                                         |
| U8  | `SafetyController` still lacks an inline unblock action in the main interaction flow.                                                               |
| U9  | Notes/profile-note UX still lacks a clean “new note” flow outside an already-open conversation.                                                     |
| U11 | Match-card data still has no match-quality field, so JavaFX cards show no quality score.                                                            |
| U12 | Stats UI still shows unlocked achievements without progress percentages/bars.                                                                       |
| U13 | `MatchesController` still has no visible indicator for graceful-exit state changes.                                                                 |
| U14 | Photo upload still validates by extension rather than true MIME/content inspection.                                                                 |
| U15 | Some async actions now disable controls, but several longer-running UI flows still have no spinner/progress affordance.                             |
| U16 | Profile data is still reloaded on each visit; no invalidation-aware cache is wired into the profile screen flow.                                    |

---

## 🟡 Achievement System Disconnect

| #   | Item                                                                                                                                                         |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AV3 | Only a small generic `ui.achievement.*` key set exists; achievement popup localization is still placeholder-oriented rather than fully achievement-specific. |

**Removed after verification:** the older duplicate-enum/orphaned-method claims no longer match the current milestone popup implementation.

---

## 🟡 Validation Gaps

| #  | Item                                                                                                                                   |
|----|----------------------------------------------------------------------------------------------------------------------------------------|
| V2 | Email format is still not validated before saving.                                                                                     |
| V3 | Age sanity checks exist in the validation layer, but tighter birth-date/data-layer guarantees would still help.                        |
| V4 | `AppConfigValidator` still validates matching weights more strictly than standout weights; standout sums are not checked the same way. |

---

## 🟡 Code Quality & Architecture

| #   | Item                                                                                                                                                   |
|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| Q1  | `ProfileController` is still very large and mixes multiple concerns.                                                                                   |
| Q2  | `MatchingHandler` is still very large and mixes CLI presentation with orchestration concerns.                                                          |
| Q3  | `RestApiServer` remains monolithic and would still benefit from route/resource extraction.                                                             |
| Q4  | `NavigationService` still combines routing, history, and screen lifecycle concerns.                                                                    |
| Q6  | `ViewModelFactory` is improved, but it still mixes some controller-level direct service injection with cleaner use-case/view-model injection patterns. |
| Q7  | `ProfileActivationPolicy.canActivate()` still partially duplicates `User.isComplete()` logic, and the two checks are not perfectly aligned.            |
| Q8  | JDBI enum decoding is centralized in helpers, but mapper code still repeats the calls inline across storages.                                          |
| Q9  | `MatchingUseCases` and `ProfileUseCases` still carry several overloaded constructors; a builder/factory would be cleaner.                              |
| Q10 | The storage/service layer uses `Optional<T>` heavily but still mixes return styles across interfaces.                                                  |
| Q11 | Global mutable singletons still exist (`AppSession`, `NavigationService`, `AppClock`).                                                                 |
| Q13 | Policy logic is still concentrated inside services; there are still no dedicated `SwipePolicy`, `UndoPolicy`, `MessagePolicy`, etc. classes.           |
| Q14 | Cross-layer logging correlation IDs are still absent.                                                                                                  |
| Q18 | `EnumSetUtil.safeCopy(...)` exists, but the codebase still mixes that utility with direct `EnumSet.copyOf(...)` calls.                                 |
| Q24 | Distance-calculation/filtering logic is still duplicated across multiple layers.                                                                       |
| Q27 | `AppSession` singleton reset behavior is still a test-isolation footgun.                                                                               |
| Q32 | Some standout ranking/scoring limits remain hardcoded rather than config-driven.                                                                       |
| Q35 | FXML controllers still use `@SuppressWarnings("unused")`; this is mostly legitimate FXML-wiring suppression rather than a code smell by itself.        |

---

## 🟡 Configuration Issues

| #   | Item                                                                                                                               |
|-----|------------------------------------------------------------------------------------------------------------------------------------|
| CF1 | `SafetyConfig.sessionTimeoutMinutes` is still misleadingly named; it governs swipe/metrics sessions rather than login sessions.    |
| CF2 | `ValidationConfig.maxPhotos` still defaults to 2 in code, even though runtime JSON commonly overrides it higher.                   |
| CF3 | `maxPhotos` is config-driven, but other related limits such as `Interest.MAX_PER_USER` remain hardcoded rather than config-driven. |

---

## 🟡 i18n / Hardcoded Strings

| #  | Item                                                                                                                                         |
|----|----------------------------------------------------------------------------------------------------------------------------------------------|
| I1 | `CliTextAndInput` still contains many hardcoded English string constants.                                                                    |
| I2 | `TextUtil.formatTimeAgo()` still uses hardcoded English phrasing.                                                                            |
| I3 | `LoginController` still contains many hardcoded user-facing strings.                                                                         |
| I4 | `Main.java` still contains the stale `"Phase 0.5"` label.                                                                                    |
| I5 | `DatingApp` window title is still hardcoded rather than localized.                                                                           |
| I6 | GUI controllers still need a substantial additional i18n pass; the original “~120 keys” is best treated as an estimate, not a precise count. |
| I7 | FXML files still contain hardcoded display strings despite existing i18n infrastructure.                                                     |

---

## 🟡 Testing Gaps

| #   | Item                                                                                                   |
|-----|--------------------------------------------------------------------------------------------------------|
| T1  | `ProfileHandler` still has no dedicated test file.                                                     |
| T2  | `MatchingHandler` still has no dedicated test file.                                                    |
| T3  | `ConnectionService` still has no direct dedicated test file.                                           |
| T4  | `DefaultCompatibilityCalculator` still has no dedicated test file.                                     |
| T5  | `DefaultAchievementService` still lacks focused direct test coverage for its achievement-switch logic. |
| T6  | `LifestyleMatcher.areKidsStancesCompatible()` still lacks focused direct test coverage.                |
| T7  | `ApplicationStartup` still lacks a dedicated direct test file.                                         |
| T8  | `AppConfigValidator` still lacks a dedicated direct test file.                                         |
| T9  | `SocialController` still has no dedicated test file.                                                   |
| T10 | `StandoutsController` still has no dedicated test file.                                                |
| T11 | `UiPreferencesStore` still has no dedicated test file.                                                 |
| T12 | There is still no meaningful end-to-end/integration test layer across the full app flow.               |
| T13 | REST API endpoint coverage remains thin relative to the surface area.                                  |
| T14 | No dedicated load/performance test suite is present.                                                   |
| T15 | No dedicated security test suite is present.                                                           |
| T16 | Several ViewModel tests still use `Thread.sleep(...)` instead of deterministic async helpers.          |

---

## 🟢 Dead Code

| #    | Item                                                         |
|------|--------------------------------------------------------------|
| DC4  | `UiAnimations.createFadeIn()` — no callers found.            |
| DC5  | `UiAnimations.createFadeOut()` — no callers found.           |
| DC6  | `UiAnimations.createPulse()` — no callers found.             |
| DC7  | `UiAnimations.playBounceIn()` — no callers found.            |
| DC8  | `UiAnimations.createSlide()` — no callers found.             |
| DC9  | `UiAnimations.addParallaxEffect()` — no callers found.       |
| DC10 | `AppEvent.MatchCreated` is still fired without any consumer. |

---

## 🟢 Quality-of-Life

| #     | Item                                                                                                                                 |
|-------|--------------------------------------------------------------------------------------------------------------------------------------|
| QOL1  | Some magic numbers have moved into `UiConstants`, but several still remain hardcoded in controllers/viewmodels.                      |
| QOL2  | Deprecated `User.getAge()` (no timezone arg) still exists and should still be phased out.                                            |
| QOL3  | Chat polling intervals are still hardcoded as defaults and constructor-injectable rather than sourced from `AppConfig` by default.   |
| QOL4  | `PerformanceMonitor` is still referenced in docs/instructions even though the class does not exist in the current codebase.          |
| QOL5  | Accessibility/keyboard support is still uneven across screens.                                                                       |
| QOL6  | Stats screen still presents numbers and lists, not charts/visualizations.                                                            |
| QOL7  | Standout cards still lack a “Why we matched” explanation.                                                                            |
| QOL8  | There is still no dedicated read-only “view other profile” screen.                                                                   |
| QOL9  | A preferences screen exists, but there is still no fuller general settings surface for broader account/privacy/notification options. |
| QOL10 | There is still no onboarding/tutorial flow.                                                                                          |
| QOL11 | Conversation search/filtering is still absent.                                                                                       |
| QOL12 | Photo upload still lacks EXIF orientation correction.                                                                                |
| QOL13 | Photo upload still lacks real MIME/content validation.                                                                               |

---

## 🔵 Strategic / Future (Scope Decisions)

| #    | Item                                                                            |
|------|---------------------------------------------------------------------------------|
| ST1  | Real-time presence via WebSocket/SSE                                            |
| ST2  | Push notifications (external delivery)                                          |
| ST3  | Super Like full implementation                                                  |
| ST4  | Profile Boost / Premium tier                                                    |
| ST5  | Stories / Reels                                                                 |
| ST6  | Group chats                                                                     |
| ST7  | Message attachments, voice, video                                               |
| ST8  | Message reactions, edit, delete-for-both                                        |
| ST9  | In-app video calls                                                              |
| ST10 | Full-text bio search                                                            |
| ST11 | Social media profile linking                                                    |
| ST12 | Content moderation (profanity, image scanning)                                  |
| ST13 | GDPR data export                                                                |
| ST16 | Shadow banning / spam detection                                                 |
| ST18 | Missing profile fields: Occupation, Religion, Body Type, Languages, Last Active |
| ST19 | Advanced analytics: response time, conversation depth, retention/cohort         |
| ST20 | Image compression, thumbnail generation, progressive loading                    |
