# Project Findings - January 27, 2026

<!--ARCHIVE:1:agent:codex:findings-refresh-->
# Project Analysis Report - January 27, 2026

This report summarizes the identified issues, flaws, and missing features in the Dating App project based on a comprehensive code review.

## 1. Critical Issues

### 1.1 Broken CLI String Constants (CRIT-01)
- **File:** `src/main/java/datingapp/cli/MatchingHandler.java`
- **Description:** At line 127, the code uses a string literal instead of the constant reference:
  `logger.info("\nCliConstants.HEADER_BROWSE_CANDIDATES\n");`
- **Impact:** The CLI displays the name of the constant instead of the actual header text, degrading the user experience.

### 1.2 Missing CASCADE DELETE on Multiple Tables (CRIT-04)
- **Files:** `DatabaseManager.java`, `H2SocialStorage.java`, `H2ModerationStorage.java`, `H2ConversationStorage.java`, `H2MessageStorage.java`
- **Description:** Several tables lack `ON DELETE CASCADE` on foreign keys referencing `users(id)`.
- **Affected Tables:**
  - `friend_requests`
  - `notifications`
  - `blocks`
  - `reports`
  - `user_achievements`
  - `daily_pick_views`
  - `conversations` (Missing FK constraints entirely)
  - `messages` (Missing FK for `sender_id`)
  - `profile_notes`
  - `profile_views`
- **Impact:** Users cannot be deleted from the system without violating foreign key constraints, or orphan data is left behind if constraints are missing.

## 2. Technical Flaws & Design Issues

### 2.1 Hardcoded Algorithm Thresholds (MED-01)
- **Files:** `DailyService.java`, `MatchQualityService.java`
- **Description:** Thresholds for "nearby" distance (5km, 10km), "similar" age (2yr, 5yr), and "many" shared interests (3) are hardcoded in logic instead of being pulled from `AppConfig`.
- **Impact:** Tuning the matching algorithm requires code changes and recompilation.

### 2.2 Fragmented Configuration
- **File:** `MatchQualityService.java`
- **Description:** `MatchQualityService` defines its own `MatchQualityConfig` record for weights. This configuration is not integrated into the central `AppConfig`.
- **Impact:** Developers must look in multiple places to configure the application behavior.

### 2.3 Schema Initialization Sprawl
- **Description:** Table creation is split between `DatabaseManager.initializeSchema()` and individual `H2*Storage.ensureSchema()` methods.
- **Impact:** Uncertain initialization order and fragmented schema management make migrations and debugging more difficult.

### 2.4 Inconsistent Input Validation (MED-02)
- **File:** `ProfileHandler.java`
- **Description:** Some inputs like height and distance lack rigorous bounds checking (e.g., negative values) or rely on silent failure.
- **Impact:** Inconsistent data quality in the database.
- SOLUTION: CREATE VALIDATION LAYER OR CLASS OR SOMETHING THAT WILL DO ALL OF THE VALIDATING.

## 3. Missing Features & Functions

### 3.1 Media/Photo Handling
- **Description:** Users can add `photoUrls`, but the application does not verify if these URLs are valid or actually lead to images. In CLI mode, they are just strings.

### 3.2 Multi-language Support (i18n)
- **Description:** All UI strings (CLI and FXML) are hardcoded in English.
- **Impact:** Difficult to localize for different regions.

### 3.3 Unmatch and Unblock Functionality
- **Description:** While users can be matched and blocked, the corresponding "unmatch" or "unblock" functionality is either missing from the CLI/UI or the underlying storage interfaces lack the necessary delete methods (MED-08).

## 4. Recommendations
1. **Consolidate Schema:** Move all table creation to `DatabaseManager` or a dedicated migration tool.
2. **Standardize Configuration:** Ensure all services use `AppConfig` for their thresholds and weights.
3. **Refactor User.java:** Extract `DatabaseRecord` and nested storage interfaces into separate files.
4. **Fix Foreign Keys:** Apply `ON DELETE CASCADE` to all tables referencing `users` and `conversations`.
5. **Implement Validation:** Add a centralized validation layer for profile updates.
<!--/ARCHIVE-->

<!--ARCHIVE:2:agent:codex:findings-reorg-->
Scope: Findings below are based on current code paths and schema definitions, not documentation.

## Critical Issues

### CRIT-01: CLI header renders literal constant name
- **Evidence:** `src/main/java/datingapp/cli/MatchingHandler.java:127` logs `"\nCliConstants.HEADER_BROWSE_CANDIDATES\n"` instead of the constant value.
- **Impact:** Users see the constant name instead of the intended header text.
- **Suggestion:** Replace the string literal with `CliConstants.HEADER_BROWSE_CANDIDATES`.

### CRIT-02: Missing FK/CASCADE for user-owned data
- **Evidence:** `src/main/java/datingapp/storage/DatabaseManager.java:256`, `src/main/java/datingapp/storage/DatabaseManager.java:269`,
  `src/main/java/datingapp/storage/H2SocialStorage.java:65`, `src/main/java/datingapp/storage/H2SocialStorage.java:266`,
  `src/main/java/datingapp/storage/H2ModerationStorage.java:60`, `src/main/java/datingapp/storage/H2ModerationStorage.java:228`,
  `src/main/java/datingapp/storage/H2ProfileDataStorage.java:66`, `src/main/java/datingapp/storage/H2ProfileDataStorage.java:198`,
  `src/main/java/datingapp/storage/H2ConversationStorage.java:30`, `src/main/java/datingapp/storage/H2MessageStorage.java:32`.
- **Impact:** Deleting a user can leave orphan rows or fail due to FK constraints, and some relations (messages, conversations) have no FK to users at all.
- **Suggestion:** Add explicit foreign keys with `ON DELETE CASCADE` for all tables referencing `users(id)` and `conversations(id)`, including `messages.sender_id`.

## High Severity Issues

### HIGH-01: Unread counts include sender’s own messages after first read
- **Evidence:** `src/main/java/datingapp/core/MessagingService.java:185` counts all messages after `lastReadAt` with no sender filter.
- **Impact:** Unread badge can grow from the user’s own messages, showing incorrect unread counts.
- **Suggestion:** Count only messages where `sender_id != userId`, or update read timestamps on send.

### HIGH-02: Daily pick ignores preference filters
- **Evidence:** `src/main/java/datingapp/core/DailyService.java:95` draws from `findActive()` and filters only self/blocked/liked.
- **Impact:** Daily pick can violate gender, age range, distance, and dealbreaker preferences.
- **Suggestion:** Reuse candidate filtering logic (gender/age/distance/dealbreakers) before random selection.

### HIGH-03: Invalid browse inputs default to PASS and consume picks
- **Evidence:** `src/main/java/datingapp/cli/MatchingHandler.java:183` and `src/main/java/datingapp/cli/MatchingHandler.java:541` map any non-"l" input to `PASS`.
- **Impact:** Typos silently pass on candidates or daily picks and can mark the pick viewed.
- **Suggestion:** Validate input and re-prompt on invalid choices.

## Medium Severity Issues

### MED-01: Config thresholds exist but are not wired to services
- **Evidence:** `src/main/java/datingapp/core/DailyService.java:136`, `src/main/java/datingapp/core/MatchQualityService.java:25`,
  `src/main/java/datingapp/core/AchievementService.java:144`.
- **Impact:** Matching/achievement tuning requires code edits despite `AppConfig` having dedicated fields.
- **Suggestion:** Inject `AppConfig` into these services and replace hardcoded thresholds.

### MED-02: CLI can throw on out-of-range inputs
- **Evidence:** `src/main/java/datingapp/cli/ProfileHandler.java:384` calls `setMaxDistanceKm` without catching `IllegalArgumentException`;
  `src/main/java/datingapp/cli/ProfileHandler.java:419` calls `setHeightCm` without catching range errors.
- **Impact:** Entering negative or extreme values can crash the CLI flow instead of showing a friendly error.
- **Suggestion:** Add a shared validation layer (CLI input validators or a reusable profile validation service) and funnel all CLI profile edits through it.

### MED-03: Unblock path is missing
- **Evidence:** `src/main/java/datingapp/core/UserInteractions.java:194` has no delete method in `BlockStorage`, and CLI/UI only expose block actions.
- **Impact:** Blocks are permanent without manual DB edits.
- **Suggestion:** Add a `delete` method to `BlockStorage` and a CLI/UI unblock flow.
<!--/ARCHIVE-->

Scope: Findings below are based on current code paths and schema definitions, not documentation.
2|2026-01-27 02:01:32|agent:codex|scope:findings-reorg|Reorganize sections, apply comments, renumber|FINDINGS_JAN_2026.md

## 1. Critical Issues

### 1.1 CLI header renders literal constant name (CRIT-01)
- **Evidence:** `src/main/java/datingapp/cli/MatchingHandler.java:127` logs `"\nCliConstants.HEADER_BROWSE_CANDIDATES\n"` instead of the constant value.
- **Impact:** Users see the constant name instead of the intended header text.
- **Suggestion:** Replace the string literal with `CliConstants.HEADER_BROWSE_CANDIDATES`.

### 1.2 Missing FK/CASCADE for user-owned data (CRIT-02)
- **Evidence:** `src/main/java/datingapp/storage/DatabaseManager.java:256`, `src/main/java/datingapp/storage/DatabaseManager.java:269`,
  `src/main/java/datingapp/storage/H2SocialStorage.java:65`, `src/main/java/datingapp/storage/H2SocialStorage.java:266`,
  `src/main/java/datingapp/storage/H2ModerationStorage.java:60`, `src/main/java/datingapp/storage/H2ModerationStorage.java:228`,
  `src/main/java/datingapp/storage/H2ProfileDataStorage.java:66`, `src/main/java/datingapp/storage/H2ProfileDataStorage.java:198`,
  `src/main/java/datingapp/storage/H2ConversationStorage.java:30`, `src/main/java/datingapp/storage/H2MessageStorage.java:32`.
- **Impact:** Deleting a user can leave orphan rows or fail due to FK constraints, and some relations (messages, conversations) have no FK to users at all.
- **Suggestion:** Add explicit foreign keys with `ON DELETE CASCADE` for all tables referencing `users(id)` and `conversations(id)`, including `messages.sender_id`.

## 2. High Severity Issues

### 2.1 Unread counts include sender’s own messages after first read (HIGH-01)
- **Evidence:** `src/main/java/datingapp/core/MessagingService.java:185` counts all messages after `lastReadAt` with no sender filter.
- **Impact:** Unread badge can grow from the user’s own messages, showing incorrect unread counts.
- **Suggestion:** Count only messages where `sender_id != userId`, or update read timestamps on send.

### 2.2 Daily pick ignores preference filters (HIGH-02)
- **Evidence:** `src/main/java/datingapp/core/DailyService.java:95` draws from `findActive()` and filters only self/blocked/liked.
- **Impact:** Daily pick can violate gender, age range, distance, and dealbreaker preferences.
- **Suggestion:** Reuse candidate filtering logic (gender/age/distance/dealbreakers) before random selection.

### 2.3 Invalid browse inputs default to PASS and consume picks (HIGH-03)
- **Evidence:** `src/main/java/datingapp/cli/MatchingHandler.java:183` and `src/main/java/datingapp/cli/MatchingHandler.java:541` map any non-"l" input to `PASS`.
- **Impact:** Typos silently pass on candidates or daily picks and can mark the pick viewed.
- **Suggestion:** Validate input and re-prompt on invalid choices.

## 3. Medium Severity Issues

### 3.1 Config thresholds exist but are not wired to services (MED-01)
- **Evidence:** `src/main/java/datingapp/core/DailyService.java:136`, `src/main/java/datingapp/core/MatchQualityService.java:25`,
  `src/main/java/datingapp/core/AchievementService.java:144`.
- **Impact:** Matching/achievement tuning requires code edits despite `AppConfig` having dedicated fields.
- **Suggestion:** Inject `AppConfig` into these services and replace hardcoded thresholds.

### 3.2 CLI can throw on out-of-range inputs (MED-02)
- **Evidence:** `src/main/java/datingapp/cli/ProfileHandler.java:384` calls `setMaxDistanceKm` without catching `IllegalArgumentException`;
  `src/main/java/datingapp/cli/ProfileHandler.java:419` calls `setHeightCm` without catching range errors.
- **Impact:** Entering negative or extreme values can crash the CLI flow instead of showing a friendly error.
- **Suggestion:** Add a shared validation layer (CLI input validators or a reusable profile validation service) and funnel all CLI profile edits through it.

### 3.3 Unblock path is missing (MED-03)
- **Evidence:** `src/main/java/datingapp/core/UserInteractions.java:194` has no delete method in `BlockStorage`, and CLI/UI only expose block actions.
- **Impact:** Blocks are permanent without manual DB edits.
- **Suggestion:** Add a `delete` method to `BlockStorage` and a CLI/UI unblock flow.


1|2026-01-27 01:34:32|agent:codex|scope:findings-refresh|Refresh findings from code audit|FINDINGS_JAN_2026.md
2|2026-01-27 02:01:32|agent:codex|scope:findings-reorg|Reorganize sections, apply comments, renumber|FINDINGS_JAN_2026.md

## Changelog
1|2026-01-27 01:34:32|agent:codex|scope:findings-refresh|Refresh findings from code audit|FINDINGS_JAN_2026.md
2|2026-01-27 02:01:32|agent:codex|scope:findings-reorg|Reorganize sections, apply comments, renumber|FINDINGS_JAN_2026.md
