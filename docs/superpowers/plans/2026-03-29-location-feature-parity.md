# Location Feature Parity Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the location feature end to end across JavaFX, CLI, and REST API using the current architecture.

**Architecture:** Keep the current `LocationService` and lat/lon persistence model, extract JavaFX dialog behavior into a focused helper, and use `LocationService` as the shared parity engine across JavaFX, CLI, and API.

**Tech Stack:** Java 25, JavaFX 25, Maven, JUnit 5, existing REST adapter and CLI handler patterns.

---

## Chunk 1: Shared location behavior

### Task 1: Add failing core tests

**Files:**
- Modify: `src/test/java/datingapp/core/profile/LocationServiceTest.java`
- Modify: `src/test/java/datingapp/core/ValidationServiceTest.java`

- [ ] Write failing tests for reverse-selection seed behavior
- [ ] Write failing tests for valid-but-unsupported ZIP approximate fallback
- [ ] Run the focused tests and verify they fail for the expected reason

### Task 2: Implement shared service behavior

**Files:**
- Modify: `src/main/java/datingapp/core/profile/LocationService.java`
- Modify: `src/main/java/datingapp/core/model/LocationModels.java` if a small new record is needed

- [ ] Add shared selection-resolution APIs needed by JavaFX, CLI, and API
- [ ] Keep public behavior deterministic and data-backed by the built-in dataset
- [ ] Run focused location tests until green

## Chunk 2: JavaFX and ViewModel parity

### Task 3: Add failing JavaFX/ViewModel tests

**Files:**
- Modify: `src/test/java/datingapp/ui/viewmodel/ProfileViewModelTest.java`
- Modify: `src/test/java/datingapp/ui/screen/ProfileControllerTest.java`

- [ ] Add failing tests for friendly location labels in preview/display
- [ ] Add failing tests for extracted location dialog flow or its controller-facing contract
- [ ] Run focused tests and verify red

### Task 4: Implement JavaFX completion

**Files:**
- Create: `src/main/java/datingapp/ui/screen/LocationSelectionDialog.java`
- Modify: `src/main/java/datingapp/ui/screen/ProfileController.java`
- Modify: `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java`
- Modify: `src/main/resources/fxml/profile.fxml`

- [ ] Extract location dialog behavior into a focused helper
- [ ] Add prepopulation, fallback, and friendly preview behavior
- [ ] Remove remaining raw-coordinate UI copy
- [ ] Run focused JavaFX/ViewModel tests until green

## Chunk 3: CLI parity

### Task 5: Add failing CLI parity tests

**Files:**
- Modify: `src/test/java/datingapp/app/cli/ProfileHandlerTest.java`

- [ ] Add failing tests for unsupported ZIP fallback and friendly display behavior
- [ ] Run focused CLI tests and verify red

### Task 6: Implement CLI parity behavior

**Files:**
- Modify: `src/main/java/datingapp/app/cli/ProfileHandler.java`

- [ ] Mirror JavaFX location behavior in CLI flow
- [ ] Add approximate fallback option for valid-but-unsupported ZIPs
- [ ] Run focused CLI tests until green

## Chunk 4: API parity

### Task 7: Add failing API parity tests

**Files:**
- Modify: `src/test/java/datingapp/app/api/RestApiPhaseTwoRoutesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java` if needed
- Create or modify additional REST API test file if route coverage is cleaner there

- [ ] Add failing tests for location metadata/resolve endpoints
- [ ] Add failing tests for profile update using location-selection input
- [ ] Add failing tests for friendly location labels in API responses
- [ ] Run focused API tests and verify red

### Task 8: Implement API parity behavior

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/api/RestApiDtos.java`
- Modify: `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` only if required for a clean compatibility boundary

- [ ] Add location metadata/resolve routes
- [ ] Support selection-based profile update input while preserving raw lat/lon compatibility
- [ ] Replace coarse coordinate display output with friendly labels
- [ ] Run focused API tests until green

## Chunk 5: Verification

### Task 9: Final verification

**Files:**
- Verify touched files only, then broader regression

- [ ] Run `get_errors` on all touched files
- [ ] Run focused location/CLI/UI/API tests
- [ ] Run a broader Maven regression command appropriate for touched areas
- [ ] Summarize behavior parity achieved and note any intentional compatibility decisions
