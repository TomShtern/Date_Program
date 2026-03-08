# Implementation Plan: Configuration & Registry Boilerplate

**Status:** ✅ **COMPLETED** (2026-03-09)

**Source Report:** `Generated_Report_Generated_By_GLM5_21.02.2026.md` (Findings F-005, F-016)

## 1. Goal Description
The `AppConfig` class contains over 60 delegating accessor methods (e.g., `dailyLikeLimit() { return matching.dailyLikeLimit(); }`), adding 200+ lines of unnecessary boilerplate which makes maintaining configuration highly cumbersome. Additionally, `ServiceRegistry` acts as a God Object, accepting 16 dependencies in its core constructor and actively suppressing `java:S107` (too many parameters) to bypass quality gates.

**Objective:**
Eradicate the delegate getters from `AppConfig` and restructure `ServiceRegistry` to use a clean Builder pattern (or separate sub-registries) to eliminate the monolithic constructor, improving dependency injection ergonomics and satisfying code quality rules natively.

## 2. Proposed Changes

### `datingapp.core`

#### [MODIFY] `AppConfig.java`
- Delete all 60+ flat delegating accessor methods (e.g., `dailyLikeLimit()`, `minAge()`, `userTimeZone()`).
- The application will rely strictly on the 4 existing modular sub-records (`matching()`, `validation()`, `algorithm()`, `safety()`).

#### [MODIFY] `ServiceRegistry.java`
- Remove the massive 16-parameter `public ServiceRegistry(...)` constructor.
- Remove the `@SuppressWarnings("java:S107")` annotation.
- Implement a `ServiceRegistry.Builder` class:
  ```java
  public static class Builder {
      private AppConfig config;
      private UserStorage userStorage;
      // ... fields

      public Builder config(AppConfig config) { this.config = config; return this; }
      public Builder userStorage(UserStorage userStorage) { this.userStorage = userStorage; return this; }
      // ... setters

      public ServiceRegistry build() {
          return new ServiceRegistry(this);
      }
  }
  ```
- Change the `ServiceRegistry` constructor to take `private ServiceRegistry(Builder builder)` and perform all `Objects.requireNonNull` validation there.

### Widespread Refactoring Sites

#### [MODIFY] Multiple Files
Because `AppConfig` flattening is removed, *every* call site using the flat methods must be updated to use the modular sub-records:
- E.g., `config.dailyLikeLimit()` becomes `config.matching().dailyLikeLimit()`.
- E.g., `config.minAge()` becomes `config.validation().minAge()`.
- Files to heavily modify include:
  - `User.java` (if configs are injected as per previously written plan)
  - `MatchPreferences.java`
  - `ValidationService.java`
  - `ProfileService.java`
  - `ActivityMetricsService.java`
  - `CandidateFinder.java` (geo constants)
  - `RecommendationService.java`
  - UI ViewModels (`ProfileViewModel`, `LoginViewModel`, `PreferencesViewModel`)
  - CLI Handlers

#### [MODIFY] `StorageFactory.java`
- Update `buildH2()` and `buildInMemory()` to use `ServiceRegistry.builder().config(config).userStorage(userStorage)...build();` instead of the 16 parameter constructor.

## 3. Verification Plan

### Automated Tests
1. **Compilation Check:** The most crucial verification is `mvn compile`. Since 60+ delegated methods and a massive constructor are being deleted, if the project compiles successfully, the refactoring is topologically sound.
2. **Quality Gates Check:** Run `mvn pmd:check` and `mvn checkstyle:check`. Verify that no "Too many parameters" warnings are thrown for `ServiceRegistry` now that the Builder is utilized.
3. **Unit Tests:** Run all `ServiceRegistry` and `AppConfig` related tests (`mvn test`) to ensure JSON loading (`AppConfig.load()`) correctly populates the 4 root sub-records without relying on legacy flat setters.

### Manual Verification
- Launch the application (`mvn javafx:run`).
- Navigate to the Preferences screen and change settings (e.g. Distance, Age Range) to verify that `ValidationService` and the ViewModels successfully read configuration thresholds from the newly strictly-nested `AppConfig` structure.

## Completion Notes (2026-03-09)

- ✅ `AppConfig` is already strictly nested by section records (`matching`, `validation`, `algorithm`, `safety`) and no flat delegating getter layer remains.
- ✅ Replaced monolithic public `ServiceRegistry` constructors with `ServiceRegistry.builder()` + private builder-based constructor.
- ✅ Removed the `java:S107` suppression path from constructor overloads by eliminating long public constructors.
- ✅ Updated production wiring in `StorageFactory.buildH2(...)` to use the new builder API.
- ✅ Updated test wiring call sites (`RestApiRelationshipRoutesTest`, `RestApiConversationBatchCountTest`, `RestApiNotesRoutesTest`) to use the new builder API.
- ✅ `ServiceRegistry` marked `final` to satisfy PMD rule `ClassWithOnlyPrivateConstructorsShouldBeFinal` after builder migration.

## Verification Executed

- ✅ Focused tests passed: `ServiceRegistryTest`, `RestApiRelationshipRoutesTest`, `RestApiConversationBatchCountTest`, `RestApiNotesRoutesTest`
- ✅ Full quality gate passed: `mvn spotless:apply verify` (BUILD SUCCESS; tests/checkstyle/PMD/JaCoCo all green)
