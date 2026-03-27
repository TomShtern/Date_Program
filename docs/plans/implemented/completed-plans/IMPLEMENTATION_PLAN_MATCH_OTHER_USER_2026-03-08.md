# Implementation Plan: Centralizing "Other User" Calculation

**Status:** ✅ **COMPLETED** (2026-03-08)

## Description
Currently, calculating the ID of the "other user" in a match given the current user's ID is duplicated across the application. `MatchingService.java` explicitly contains `private static UUID otherUserId(Match match, UUID currentUserId)`. This calculation belongs to the `Match` object domain logic to adhere to Object-Oriented encapsulation principles and the DRY (Don't Repeat Yourself) rule.
`Match.java` actually already implements this gracefully as `public UUID getOtherUser(UUID userId)`. The duplication simply needs to be cleaned up.

## Proposed Changes

### 1. Update `MatchingService`
- Remove the static nested helper method `otherUserId(...)`.
- Change all calls in `MatchingService` from `otherUserId(match, currentUserId)` to `match.getOtherUser(currentUserId)`.

#### [MODIFY] `MatchingService.java`(file:///c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/src/main/java/datingapp/core/matching/MatchingService.java)
- Remove `otherUserId(Match match, UUID currentUserId)` on line 223.
- Update line 188 (in `findPendingLikersWithTimes`) from `matched.add(otherUserId(match, currentUserId));` to `matched.add(match.getOtherUser(currentUserId));`.

### 2. Search and Replace in Other Classes
- Identify other classes that are repeating this `if/else` ternary logic instead of relying on `Match.getOtherUser`. Candidates include:
    - `JdbiMatchmakingStorage.java`
    - `RestApiServer.java`
    - CLI Handlers (e.g., `MatchingHandler.java`, `MessagingHandler.java`)

#### [MODIFY] Identify and fix remaining duplicates
- Search the `src/` directory for `.getUserA().equals(` or `.getUserB().equals(` and replace logic with `.getOtherUser(userId)`.

## Verification Plan

### Automated Tests
- Run matching service tests: `mvn test -Dtest=MatchingServiceTest`
- Run candidate finding and UI listing tests: `mvn test -Dtest=CandidateFinderTest` and `LikerBrowserHandlerTest`
- Perform full test suite run to ensure no regressions across API or CLI handlers:
  `mvn test`

### Manual Verification
- Build and run the app.
- Like a user on one account and switch to the other account to verify the match resolves correctly and UI displays the proper names and data for the counterparts.

## Completion Notes (2026-03-08)

- ✅ `MatchingService` uses centralized counterpart derivation (no duplicate `otherUserId(...)` helper remains).
- ✅ `RestApiServer` now uses `match.getOtherUser(currentUserId)` for match listing/summary projection and local helper duplication was removed.
- ✅ `InteractionStorage` default `getMatchedCounterpartIds(UUID)` now delegates to `match.getOtherUser(userId)`.

## Verification Executed

- ✅ Plan-targeted tests passed: `MatchingServiceTest`, `CandidateFinderTest`, `LikerBrowserHandlerTest`
- ✅ API route tests passed: `RestApiRoutesTest`, `RestApiRelationshipRoutesTest`
- ✅ Full quality gate passed: `mvn spotless:apply verify` (BUILD SUCCESS)
