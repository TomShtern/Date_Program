# Implementation Plan: Audit Findings Fixes

This document outlines the planned code changes to address the remaining audit findings: FI-AUD-001, FI-AUD-002, FI-AUD-005, FI-AUD-006, FI-AUD-009, and FI-CONS-003.

## Goal Description
The objective is to resolve a series of code logic and architecture issues discovered during the audit. This includes fixing timezone handling for age calculations, managing session inactivity properly during messaging, preventing unbounded growth of standouts data, un-nesting the [ProfileNote](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/User.java#70-158) record, and fully utilizing the conversation archiving and visibility fields during unmatching and blocking. The changes aim for simplicity, do not over-engineer, and follow the project's existing design philosophy (such as strict dependency injection without frameworks and deterministic IDs).

## Proposed Changes

### Domain Layer (Core Models & Config)
#### [NEW] `ProfileNote.java`
- Extract the nested [ProfileNote](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/User.java#70-158) record from [User.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/User.java) into its own top-level file `datingapp.core.model.ProfileNote`.
- This resolves **FI-CONS-003** by promoting the nested entity to a top-level record.

#### [MODIFY] [User.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/User.java)
- Remove the nested [ProfileNote](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/User.java#70-158) record definition.
- Modify the [getAge()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/User.java#526-539) method to use `AppConfig.defaults().safety().userTimeZone()` instead of `ZoneId.systemDefault()`. This resolves **FI-AUD-006**.

#### [MODIFY] [SwipeState.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/metrics/SwipeState.java)
- Add a `recordActivity()` method to `SwipeState.Session` that simply updates `lastActivityAt = AppClock.now();`.

### Service Layer (Core Services)
#### [MODIFY] [ActivityMetricsService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/metrics/ActivityMetricsService.java)
- Inject `Standout.Storage` via the constructor (update [StorageFactory](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/storage/StorageFactory.java#35-133) accordingly).
- In the [runCleanup()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/metrics/ActivityMetricsService.java#134-140) method, add a call to `standoutStorage.cleanup(cutoffDate)`. This gives Standouts a proper cleanup path, resolving **FI-AUD-009**.
- Add a new method `recordActivity(UUID userId)` that fetches the active session and calls `session.recordActivity()`, then saves the session. This is needed for messaging activity.

#### [MODIFY] [ConnectionService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/connection/ConnectionService.java)
- Inject [ActivityMetricsService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/metrics/ActivityMetricsService.java#24-271) into the constructor (update [StorageFactory](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/storage/StorageFactory.java#35-133) accordingly).
- In the [sendMessage()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/connection/ConnectionService.java#52-90) method, call `activityMetricsService.recordActivity(senderId)` to update the session's `lastActivityAt`. This solves **FI-AUD-005**.
- Add a new method [unmatch(UUID initiatorId, UUID targetId)](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/Match.java#145-158):
  - Call `interactionStorage.getByUsers(initiatorId, targetId)` to get the match.
  - Call `match.unmatch(initiatorId)`.
  - Fetch the [Conversation](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/connection/ConnectionModels.java#59-278) between the users, using `communicationStorage.getConversationByUsers`.
  - If a conversation exists, archive it using `communicationStorage.archiveConversation(conversation.getId(), MatchArchiveReason.UNMATCH)`.
  - Save the match state using `interactionStorage.update(match)`.
  - This utilizes the archive fields for unmatching (**FI-AUD-001 & FI-AUD-002**).

#### [MODIFY] [TrustSafetyService.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/TrustSafetyService.java)
- Inject `CommunicationStorage` into the constructor (update [StorageFactory](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/storage/StorageFactory.java#35-133) accordingly).
- In [updateMatchStateForBlock()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/TrustSafetyService.java#167-186), fetch the [Conversation](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/connection/ConnectionModels.java#59-278) between the blocker and blocked users.
- Call `communicationStorage.archiveConversation` with `MatchArchiveReason.BLOCK`.
- Call `communicationStorage.setConversationVisibility(conversation.getId(), blockerId, false)`.
- This fully updates Conversation visibility and archiving fields upon blocking, satisfying **FI-AUD-001 & FI-AUD-002**.

### Storage & App Initialization
#### [MODIFY] [StorageFactory.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/storage/StorageFactory.java)
- Update the instantiation of [ActivityMetricsService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/metrics/ActivityMetricsService.java#24-271), [ConnectionService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/connection/ConnectionService.java#27-494), and [TrustSafetyService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/TrustSafetyService.java#22-300) to provide the newly injected dependencies (e.g., passing `metricsStorage` as `Standout.Storage` to [ActivityMetricsService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/metrics/ActivityMetricsService.java#24-271)).

### View & CLI Layer
#### [MODIFY] [MatchingHandler.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java)
- In [unmatchFromList()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#485-514) and [handleMatchDetailAction()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/MatchingHandler.java#435-476), replace manual `match.unmatch()` and `interactionStorage.update(match)` with a call to the new `ConnectionService.unmatch(initiatorId, targetId)` method, which also handles the conversation archiving safely.

#### [MODIFY] [UserStorage.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/storage/UserStorage.java), [JdbiUserStorage.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java), [ProfileHandler.java](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/app/cli/ProfileHandler.java)
- Update imports of `datingapp.core.model.User.ProfileNote` to `datingapp.core.model.ProfileNote`.

## Verification Plan

### Automated Tests
1. **Compilation Check**: Run `mvn clean compile` to ensure that moving [ProfileNote](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/User.java#70-158) and modifying constructors in [StorageFactory](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/storage/StorageFactory.java#35-133) works perfectly.
2. **Format and Lint check**: Run `mvn spotless:apply` and `mvn verify` to guarantee zero checkstyle/PMD failures and that all existing tests pass (>60% test coverage).
3. **Unit Tests (Modifications/Additions)**:
   - Update any existing tests for [TrustSafetyService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/TrustSafetyService.java#22-300), [ConnectionService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/connection/ConnectionService.java#27-494), and [ActivityMetricsService](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/metrics/ActivityMetricsService.java#24-271) that fail due to missing mock/in-memory dependencies.
   - Add a test in `ConnectionServiceTest` (if it exists) to show [unmatch()](file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/model/Match.java#145-158) correctly modifies the match state and archives the conversation.
   - Add a test in `TrustSafetyServiceTest` to ensure blocking properly updates conversation visibility and sets `archivedReason = BLOCK`.
   - Ensure `ActivityMetricsServiceTest` validates that `recordActivity` successfully updates `lastActivityAt`.

### Manual / Integration Verification
1. Open the application (CLI).
2. Attempt a block and an unmatch action.
3. Validate through logging or direct H2 console/queries that the `conversations` table's `archived_at`, `archive_reason`, and `visible_to_user_a`/`b` fields are correctly modified.
4. Send a message in a conversation and verify `swipe_sessions` table updates the `last_activity_at`.
