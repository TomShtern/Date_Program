# Codebase Analysis Report
> **Date**: 2026-02-23

> ⚠️ **Alignment status (2026-03-01): Historical snapshot**
> This report captures a prior state and should not be used as a current metrics/source map.
> Current baseline: **116 main + 88 test = 204 Java files**, **56,482 total Java LOC / 43,327 code LOC**, tests: **983/0/0/2**.

## Executive Summary
The codebase is a highly robust, well-structured Java application relying on Java 25 features, Maven, an embedded H2 database (via JDBI), and a JavaFX desktop UI. It strictly enforces a 4-tier layer boundary (`core/`, `storage/`, `app/`, `ui/`). The codebase reflects excellent engineering practices: pure domain logic containing zero framework dependencies, centralized configuration (`AppConfig`), and deterministic data (`AppClock`).

However, as the application has grown to ~150 files and ~40K lines of code, several areas have begun showing friction—specifically monolithic files, UI boilerplate, and in-memory filter complexities—that could be streamlined so AI agents and human developers can navigate and evolve the system more efficiently.

## 1. Structural & Architectural Observations

### 1.1 UI Controller & ViewModel Bloat
**Finding:** The Presentation layer in `ui/` suffers from standard JavaFX MVVM boilerplate, resulting in massive files.
- **`ProfileController.java` (881 lines):** Orchestrates too many distinct responsibilities. It dynamically generates UI components (e.g., `FlowPane` for Interest chips), defines custom string converters, wires event bindings, and handles UI visual state inside a massive `initialize()` block.
- **`ProfileViewModel.java` (854 lines):** Maps over 25 fields bi-directionally between the `User` domain entity and JavaFX `Property` wrappers. It includes verbose methods like `applyLifestyleFields()`, `applyBasicFields()`, and `applySearchPreferences()` purely to manually sync state.
**Recommendation:**
- Break down monolithic controllers into reusable, composite FXML components (e.g., `InterestSelector`, `GenderPreferenceSelector`, `PhotoUploader`).
- Minimize manual mapping by using JavaFX immutable record binding strategies, or wrapping the mutable domain `User` entity better to avoid 800-line sync classes.

### 1.2 "God Class" Configurations and Factories
**Finding:** `AppConfig.java` (900 lines) and `StorageFactory.java` (137 lines).
- `AppConfig` centralizes all "magic numbers," which is fantastic. Recently, it was updated to group configs recursively (`MatchingConfig`, `ValidationConfig`). However, it retains a massive number of legacy root-level delegate getters for backward compatibility, artificially inflating the class size.
- `StorageFactory.java` manually injects 17+ services. As the domain layer expands, this single mega-method becomes a fragile bottleneck for concurrent modifications.
**Recommendation:**
- **Short-term:** Remove the legacy backward-compatible delegate getters in `AppConfig` to cut the file size by 40%. The codebase should natively call `config.safety().autoBanThreshold()`.
- **Long-term:** Subdivide `StorageFactory.java` into discrete factory methods (e.g., `buildDomainServices()`, `buildStorageAdapters()`) or utilize a lightweight compile-time dependency injection library.

### 1.3 `core/` Domain Logic Complexity
**Finding:** `CandidateFinder.java` effectively offloads spatial filtering to the database (`userStorage.findCandidates`), avoiding O(N) memory crashes. However, the subsequent in-memory filtering pipeline uses a hardcoded sequence of `.filter()` chains. Each filter delegates to deeply nested logging helper methods (e.g., `hasMatchingGenderPreferences`, `isWithinDistanceWithLogging`).
**Recommendation:**
- Abstract the filtering criteria into a `CandidateFilter` interface (Strategy/Chain of Responsibility pattern). Using implementations like `AgeFilter`, `GenderFilter`, and `DealbreakerFilter` will modularize the logic and isolate the verbose logging out of the core pipeline orchestrator.

## 2. Infrastructure Layer (`storage/`) Refinement

### 2.1 Raw SQL Strings in JDBI Annotations
**Finding:** Files like `JdbiMatchmakingStorage.java` (890+ lines) and `JdbiUserStorage.java` utilize complex multiline SQL strings inside `@SqlQuery` and `@SqlUpdate` annotations.
- For instance, queries like `getPageOfActiveMatchesFor` or mutual-like verifications involve sub-selects and conditional joins written purely in Java string literals.
- This creates noise, makes syntax errors harder to detect, and breaks IDE SQL assistance.
**Recommendation:**
- Migrate large, complex queries from Java annotation strings to `.sql` files on the classpath using JDBI's `@UseClasspathSqlLocator`. This dramatically improves readability and isolates schema logic.

## 3. Code Duplication & Consistency
**Finding:** I executed PMD CPD (`mvn pmd:cpd`) to scan the entire repository for duplicate lines and copy-paste code. The result was a clean pass showing **0 significant duplications**.
**Conclusion:** The codebase strictly adheres to the DRY principle. Domain logic is successfully centralized rather than copy-pasted across ViewModels or Handlers.

## 4. General Flaws & Enhancements
- **Logging Noise:** Several domains (such as `CandidateFinder`) do extensive string interpolation for TRACE/DEBUG logging inside tight loops (e.g., logging why a candidate was rejected). Ensure these are appropriately guarded by `logger.isDebugEnabled()` or utilize SLF4J deferred execution (lambdas for string format arguments) to avoid unnecessary string allocations when debug logging is disabled.
- **Mutable State Boundaries:** The application frequently passes the mutable `User` entity between `core/` and `ui/`. If `core` requires predictable state boundaries, passing immutable DTOs or Java records into the UI layer (forcing the UI to use explicit `Service` methods to mutate models) would prevent accidental local mutation bugs.

## 5. Maintenance Roadmap
1. **Phase 1 (Immediate Maintenance):** Sweep `AppConfig` to strip away legacy delegation methods and enforce the sub-record hierarchy strictly. Ensure logging in tight loops uses parameterized formatting.
2. **Phase 2 (Architecture Improvement):** Refactor the massive JDBI `@SqlQuery` literals into classpath `.sql` files to simplify `JdbiUserStorage` and `JdbiMatchmakingStorage`.
3. **Phase 3 (UI Refactoring):** Abstract JavaFX layouts inside `ProfileController.java` to reduce controller size to < 300 lines by creating smaller standard widgets.

