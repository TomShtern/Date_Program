# Codebase Analysis Report - Thinking Potato
## Project Overview
The codebase is a Java 25 dating application following clean architecture principles with 4-tier design (core/storage/app/ui). It uses Maven, H2 database (JDBI), and JavaFX UI. The project is well-structured but shows signs of complexity due to growth.

## Major Issues

### 1. God Classes and Oversized Components
#### Issue:
- [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java): 42KB - Combines candidate browsing, standouts, notifications, and requests into one massive class
- [`JdbiMatchmakingStorage.java`](src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java): 37KB - Mixes DAO, transaction, undo, and mapping concerns
- [`NavigationService.java`](src/main/java/datingapp/ui/NavigationService.java): God object handling enum registry, FXML navigation, transition handling, history stack, and navigation context
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java) and [`ProfileViewModel.java`](src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java): >850 lines each

#### Why It's Bad:
- Hard to understand and maintain
- Changes affect multiple unrelated features
- High cyclomatic complexity
- Difficult to test in isolation

#### Suggested Solution:
- Split each god class into smaller, focused components
- Extract separate classes for each responsibility
- Use composition over inheritance
- Follow single responsibility principle

#### Impact:
- Improved maintainability
- Easier testing
- Faster debugging
- Reduced risk of breaking unrelated features

### 2. Code Duplication
#### Issue:
- **REST pagination validation**: Copy-pasted across endpoints in [`RestApiServer.java`](src/main/java/datingapp/app/api/RestApiServer.java:181-188, 238-245, 261-268)
- **Distance calculations**: Duplicated in [`CandidateFinder.java`](src/main/java/datingapp/core/matching/CandidateFinder.java:85) and [`MatchQualityService.java`](src/main/java/datingapp/core/matching/MatchQualityService.java:366) using `GeoUtils.distanceKm()`
- **CLI input handling**: Similar index parsing and invalid-input handling patterns duplicated across all handlers
- **List rendering/selection**: Similar loop structures for displaying and selecting items duplicated in CLI handlers

#### Why It's Bad:
- Violates DRY (Don't Repeat Yourself) principle
- Changes require updating multiple places
- Increases risk of inconsistencies
- Larger codebase than necessary

#### Suggested Solution:
- Create shared utility classes for common functionality
- Extract CLI utility with index parsing and list rendering
- Create REST pagination utility
- Create distance calculation utility
- Centralize error handling patterns

#### Impact:
- Reduced LOC
- Easier maintenance
- Consistent behavior
- Faster development

### 3. Architectural Flaws
#### Issue:
- **Schema drift risk**: Fresh schema creation (`SchemaInitializer`) and incremental migration (`MigrationRunner`) maintained separately
- **Navigation duplication**: ViewType enum and controller factory map must stay synchronized
- **Storage interface bloat**: `UserStorage` mixes user CRUD with profile-note persistence
- **Memory pagination**: Default pagination in `InteractionStorage` loads all rows first then slices in memory

#### Why It's Bad:
- Inconsistent database state between fresh installs and upgrades
- High risk of navigation bugs when adding new views
- Storage interfaces violate single responsibility principle
- Memory inefficiency with large datasets

#### Suggested Solution:
- Unify schema evolution into versioned migrations
- Create single source of truth for navigation configuration
- Split storage interfaces by responsibility (UserStorage, ProfileNoteStorage)
- Implement proper SQL-level pagination

#### Impact:
- Reduced schema drift risk
- Simplified navigation maintenance
- Clean storage architecture
- Improved performance with large datasets

### 4. Testing Issues
#### Issue:
- Handler tests manually rebuild service wiring repeatedly
- `TestStorages` duplicates production candidate-filtering behavior
- UI ViewModel tests use `Thread.sleep` which causes flaky tests
- `AppSession` singleton requires explicit resets in every test

#### Why It's Bad:
- Redundant test setup code
- Tests fail randomly due to timing issues
- Difficult to maintain test consistency
- High test setup overhead

#### Suggested Solution:
- Create shared test fixtures and service wiring
- Reuse production logic in test implementations
- Use CompletableFuture or TestFX asynchronous testing
- Refactor AppSession to be injectable instead of singleton

#### Impact:
- Faster test writing
- More reliable tests
- Reduced test maintenance
- Better test coverage

### 5. Configuration & Logging
#### Issue:
- [`AppConfig.java`](src/main/java/datingapp/core/AppConfig.java): 59 delegate methods and 57 builder setters for backward compatibility
- Raw SQL strings in JDBI annotations lack syntax highlighting and error detection
- Tight loop logging without proper `isDebugEnabled()` checks causes unnecessary string allocations

#### Why It's Bad:
- Configuration class is overly complex and hard to maintain
- SQL errors detected only at runtime
- Performance impact from unnecessary logging
- Backward compatibility baggage

#### Suggested Solution:
- Simplify configuration with proper Jackson annotations
- Use SQL template files or query builders
- Add `isDebugEnabled()` checks around debug logging
- Remove unnecessary backward compatibility code

#### Impact:
- Simplified configuration
- Early SQL error detection
- Improved performance
- Easier maintenance

### 6. Navigation Service Complexity
#### Issue:
- [`NavigationService.java`](src/main/java/datingapp/ui/NavigationService.java) is responsible for:
  - ViewType enum registry
  - FXML file resolution
  - Controller instantiation
  - Transition animation handling
  - History stack management
  - Navigation context passing

#### Why It's Bad:
- Single class has too many responsibilities
- Complex state management
- Hard to test and debug

#### Suggested Solution:
- Split into separate classes:
  - ViewRegistry: manages ViewType to FXML/Controller mapping
  - NavigationController: handles navigation history and context
  - TransitionManager: handles animation transitions
  - FXMLoaderHelper: simplifies controller instantiation

#### Impact:
- Clearer responsibilities
- Easier to test
- Faster debugging
- Extensible architecture

### 7. UI Layer Complexity
#### Issue:
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java): >850 lines - handles profile display, editing, photo management, preferences, and notes
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java): 31KB - handles match display, filtering, and messaging
- ViewModel classes have extensive business logic

#### Why It's Bad:
- Controllers are overloaded with responsibilities
- Hard to maintain and extend UI features
- Testing UI logic is difficult
- High coupling between UI and business logic

#### Suggested Solution:
- Split large controllers into smaller, feature-focused controllers
- Move business logic from ViewModels to core services
- Create separate controllers for different profile sections
- Implement Presenter pattern for UI logic

#### Impact:
- Simplified UI components
- Better separation of concerns
- Easier UI testing
- Faster feature development

## Compilation Status
✅ **Build successful**: All 89 source files compile with Java 25 preview features  
✅ **Checkstyle passing**: 0 violations  
✅ **PMD analysis clean**: No major issues detected  

## Recommended Refactor Order (Lowest Risk First)
1. Centralize duplicated validation/parsing utilities (CLI index parsing, REST pagination)
2. Unify metadata registries (CLI menu options, navigation registry)
3. Split oversized classes at seam points
4. Consolidate schema evolution into versioned migrations
5. Reduce test wiring duplication with shared fixtures
6. Simplify configuration and logging
7. Refactor navigation service architecture
8. Split large UI controllers and ViewModels

## Summary


#### Why It's Bad:
- Hard to understand and maintain
- Changes affect multiple unrelated features
- High cyclomatic complexity
- Difficult to test in isolation

#### Suggested Solution:
- Split each god class into smaller, focused components
- Extract separate classes for each responsibility
- Use composition over inheritance
- Follow single responsibility principle

#### Impact:
- Improved maintainability
- Easier testing
- Faster debugging
- Reduced risk of breaking unrelated features

### 2. Code Duplication
#### Issue:
- **REST pagination validation**: Copy-pasted across endpoints in [`RestApiServer.java`](src/main/java/datingapp/app/api/RestApiServer.java:181-188, 238-245, 261-268)
- **Distance calculations**: Duplicated in [`CandidateFinder.java`](src/main/java/datingapp/core/matching/CandidateFinder.java:85) and [`MatchQualityService.java`](src/main/java/datingapp/core/matching/MatchQualityService.java:366) using `GeoUtils.distanceKm()`
- **CLI input handling**: Similar index parsing and invalid-input handling patterns duplicated across all handlers
- **List rendering/selection**: Similar loop structures for displaying and selecting items duplicated in CLI handlers

#### Why It's Bad:
- Violates DRY (Don't Repeat Yourself) principle
- Changes require updating multiple places
- Increases risk of inconsistencies
- Larger codebase than necessary

#### Suggested Solution:
- Create shared utility classes for common functionality
- Extract CLI utility with index parsing and list rendering
- Create REST pagination utility
- Create distance calculation utility
- Centralize error handling patterns

#### Impact:
- Reduced LOC
- Easier maintenance
- Consistent behavior
- Faster development

### 3. Architectural Flaws
#### Issue:
- **Schema drift risk**: Fresh schema creation (`SchemaInitializer`) and incremental migration (`MigrationRunner`) maintained separately
- **Navigation duplication**: ViewType enum and controller factory map must stay synchronized
- **Storage interface bloat**: `UserStorage` mixes user CRUD with profile-note persistence
- **Memory pagination**: Default pagination in `InteractionStorage` loads all rows first then slices in memory

#### Why It's Bad:
- Inconsistent database state between fresh installs and upgrades
- High risk of navigation bugs when adding new views
- Storage interfaces violate single responsibility principle
- Memory inefficiency with large datasets

#### Suggested Solution:
- Unify schema evolution into versioned migrations
- Create single source of truth for navigation configuration
- Split storage interfaces by responsibility (UserStorage, ProfileNoteStorage)
- Implement proper SQL-level pagination

#### Impact:
- Reduced schema drift risk
- Simplified navigation maintenance
- Clean storage architecture
- Improved performance with large datasets

### 4. Testing Issues
#### Issue:
- Handler tests manually rebuild service wiring repeatedly
- `TestStorages` duplicates production candidate-filtering behavior
- UI ViewModel tests use `Thread.sleep` which causes flaky tests
- `AppSession` singleton requires explicit resets in every test

#### Why It's Bad:
- Redundant test setup code
- Tests fail randomly due to timing issues
- Difficult to maintain test consistency
- High test setup overhead

#### Suggested Solution:
- Create shared test fixtures and service wiring
- Reuse production logic in test implementations
- Use CompletableFuture or TestFX asynchronous testing
- Refactor AppSession to be injectable instead of singleton

#### Impact:
- Faster test writing
- More reliable tests
- Reduced test maintenance
- Better test coverage

### 5. Configuration & Logging
#### Issue:
- [`AppConfig.java`](src/main/java/datingapp/core/AppConfig.java): 59 delegate methods and 57 builder setters for backward compatibility
- Raw SQL strings in JDBI annotations lack syntax highlighting and error detection
- Tight loop logging without proper `isDebugEnabled()` checks causes unnecessary string allocations

#### Why It's Bad:
- Configuration class is overly complex and hard to maintain
- SQL errors detected only at runtime
- Performance impact from unnecessary logging
- Backward compatibility baggage

#### Suggested Solution:
- Simplify configuration with proper Jackson annotations
- Use SQL template files or query builders
- Add `isDebugEnabled()` checks around debug logging
- Remove unnecessary backward compatibility code

#### Impact:
- Simplified configuration
- Early SQL error detection
- Improved performance
- Easier maintenance

### 6. Navigation Service Complexity
#### Issue:
- [`NavigationService.java`](src/main/java/datingapp/ui/NavigationService.java) is responsible for:
  - ViewType enum registry
  - FXML file resolution
  - Controller instantiation
  - Transition animation handling
  - History stack management
  - Navigation context passing

#### Why It's Bad:
- Single class has too many responsibilities
- Complex state management
- Hard to test and debug

#### Suggested Solution:
- Split into separate classes:
  - ViewRegistry: manages ViewType to FXML/Controller mapping
  - NavigationController: handles navigation history and context
  - TransitionManager: handles animation transitions
  - FXMLoaderHelper: simplifies controller instantiation

#### Impact:
- Clearer responsibilities
- Easier to test
- Faster debugging
- Extensible architecture

### 7. UI Layer Complexity
#### Issue:
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java): >850 lines - handles profile display, editing, photo management, preferences, and notes
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java): 31KB - handles match display, filtering, and messaging
- ViewModel classes have extensive business logic

#### Why It's Bad:
- Controllers are overloaded with responsibilities
- Hard to maintain and extend UI features
- Testing UI logic is difficult
- High coupling between UI and business logic

#### Suggested Solution:
- Split large controllers into smaller, feature-focused controllers
- Move business logic from ViewModels to core services
- Create separate controllers for different profile sections
- Implement Presenter pattern for UI logic

#### Impact:
- Simplified UI components
- Better separation of concerns
- Easier UI testing
- Faster feature development

## Compilation Status
✅ **Build successful**: All 89 source files compile with Java 25 preview features  
✅ **Checkstyle passing**: 0 violations  
✅ **PMD analysis clean**: No major issues detected  

## Recommended Refactor Order (Lowest Risk First)
1. Centralize duplicated validation/parsing utilities (CLI index parsing, REST pagination)
2. Unify metadata registries (CLI menu options, navigation registry)
3. Split oversized classes at seam points
4. Consolidate schema evolution into versioned migrations
5. Reduce test wiring duplication with shared fixtures
6. Simplify configuration and logging
7. Refactor navigation service architecture
8. Split large UI controllers and ViewModels

## Summary
The codebase is well-maintained with excellent engineering practices but shows signs of growth-related complexity in key areas. The major issues identified are oversized god classes, code duplication, architectural flaws, testing problems, and configuration complexity. Addressing these issues will significantly improve the codebase's readability, maintainability, and extensibility.

## Project Overview
The codebase is a Java 25 dating application following clean architecture principles with 4-tier design (core/storage/app/ui). It uses Maven, H2 database (JDBI), and JavaFX UI. The project is well-structured but shows signs of complexity due to growth.

## Major Issues

### 1. God Classes and Oversized Components
#### Issue:
- [`MatchingHandler.java`](src/main/java/datingapp/app/cli/MatchingHandler.java): 42KB - Combines candidate browsing, standouts, notifications, and requests into one massive class
- [`JdbiMatchmakingStorage.java`](src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java): 37KB - Mixes DAO, transaction, undo, and mapping concerns
- [`NavigationService.java`](src/main/java/datingapp/ui/NavigationService.java): God object handling enum registry, FXML navigation, transition handling, history stack, and navigation context
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java) and [`ProfileViewModel.java`](src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java): >850 lines each

#### Why It's Bad:
- Hard to understand and maintain
- Changes affect multiple unrelated features
- High cyclomatic complexity
- Difficult to test in isolation

#### Suggested Solution:
- Split each god class into smaller, focused components
- Extract separate classes for each responsibility
- Use composition over inheritance
- Follow single responsibility principle

#### Impact:
- Improved maintainability
- Easier testing
- Faster debugging
- Reduced risk of breaking unrelated features

### 2. Code Duplication
#### Issue:
- **REST pagination validation**: Copy-pasted across endpoints in [`RestApiServer.java`](src/main/java/datingapp/app/api/RestApiServer.java:181-188, 238-245, 261-268)
- **Distance calculations**: Duplicated in [`CandidateFinder.java`](src/main/java/datingapp/core/matching/CandidateFinder.java:85) and [`MatchQualityService.java`](src/main/java/datingapp/core/matching/MatchQualityService.java:366) using `GeoUtils.distanceKm()`
- **CLI input handling**: Similar index parsing and invalid-input handling patterns duplicated across all handlers
- **List rendering/selection**: Similar loop structures for displaying and selecting items duplicated in CLI handlers

#### Why It's Bad:
- Violates DRY (Don't Repeat Yourself) principle
- Changes require updating multiple places
- Increases risk of inconsistencies
- Larger codebase than necessary

#### Suggested Solution:
- Create shared utility classes for common functionality
- Extract CLI utility with index parsing and list rendering
- Create REST pagination utility
- Create distance calculation utility
- Centralize error handling patterns

#### Impact:
- Reduced LOC
- Easier maintenance
- Consistent behavior
- Faster development

### 3. Architectural Flaws
#### Issue:
- **Schema drift risk**: Fresh schema creation (`SchemaInitializer`) and incremental migration (`MigrationRunner`) maintained separately
- **Navigation duplication**: ViewType enum and controller factory map must stay synchronized
- **Storage interface bloat**: `UserStorage` mixes user CRUD with profile-note persistence
- **Memory pagination**: Default pagination in `InteractionStorage` loads all rows first then slices in memory

#### Why It's Bad:
- Inconsistent database state between fresh installs and upgrades
- High risk of navigation bugs when adding new views
- Storage interfaces violate single responsibility principle
- Memory inefficiency with large datasets

#### Suggested Solution:
- Unify schema evolution into versioned migrations
- Create single source of truth for navigation configuration
- Split storage interfaces by responsibility (UserStorage, ProfileNoteStorage)
- Implement proper SQL-level pagination

#### Impact:
- Reduced schema drift risk
- Simplified navigation maintenance
- Clean storage architecture
- Improved performance with large datasets

### 4. Testing Issues
#### Issue:
- Handler tests manually rebuild service wiring repeatedly
- `TestStorages` duplicates production candidate-filtering behavior
- UI ViewModel tests use `Thread.sleep` which causes flaky tests
- `AppSession` singleton requires explicit resets in every test

#### Why It's Bad:
- Redundant test setup code
- Tests fail randomly due to timing issues
- Difficult to maintain test consistency
- High test setup overhead

#### Suggested Solution:
- Create shared test fixtures and service wiring
- Reuse production logic in test implementations
- Use CompletableFuture or TestFX asynchronous testing
- Refactor AppSession to be injectable instead of singleton

#### Impact:
- Faster test writing
- More reliable tests
- Reduced test maintenance
- Better test coverage

### 5. Configuration & Logging
#### Issue:
- [`AppConfig.java`](src/main/java/datingapp/core/AppConfig.java): 59 delegate methods and 57 builder setters for backward compatibility
- Raw SQL strings in JDBI annotations lack syntax highlighting and error detection
- Tight loop logging without proper `isDebugEnabled()` checks causes unnecessary string allocations

#### Why It's Bad:
- Configuration class is overly complex and hard to maintain
- SQL errors detected only at runtime
- Performance impact from unnecessary logging
- Backward compatibility baggage

#### Suggested Solution:
- Simplify configuration with proper Jackson annotations
- Use SQL template files or query builders
- Add `isDebugEnabled()` checks around debug logging
- Remove unnecessary backward compatibility code

#### Impact:
- Simplified configuration
- Early SQL error detection
- Improved performance
- Easier maintenance

### 6. Navigation Service Complexity
#### Issue:
- [`NavigationService.java`](src/main/java/datingapp/ui/NavigationService.java) is responsible for:
  - ViewType enum registry
  - FXML file resolution
  - Controller instantiation
  - Transition animation handling
  - History stack management
  - Navigation context passing

#### Why It's Bad:
- Single class has too many responsibilities
- Complex state management
- Hard to test and debug

#### Suggested Solution:
- Split into separate classes:
  - ViewRegistry: manages ViewType to FXML/Controller mapping
  - NavigationController: handles navigation history and context
  - TransitionManager: handles animation transitions
  - FXMLoaderHelper: simplifies controller instantiation

#### Impact:
- Clearer responsibilities
- Easier to test
- Faster debugging
- Extensible architecture

### 7. UI Layer Complexity
#### Issue:
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java): >850 lines - handles profile display, editing, photo management, preferences, and notes
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java): 31KB - handles match display, filtering, and messaging
- ViewModel classes have extensive business logic

#### Why It's Bad:
- Controllers are overloaded with responsibilities
- Hard to maintain and extend UI features
- Testing UI logic is difficult
- High coupling between UI and business logic

#### Suggested Solution:
- Split large controllers into smaller, feature-focused controllers
- Move business logic from ViewModels to core services
- Create separate controllers for different profile sections
- Implement Presenter pattern for UI logic

#### Impact:
- Simplified UI components
- Better separation of concerns
- Easier UI testing
- Faster feature development

## Compilation Status
✅ **Build successful**: All 89 source files compile with Java 25 preview features  
✅ **Checkstyle passing**: 0 violations  
✅ **PMD analysis clean**: No major issues detected  

## Recommended Refactor Order (Lowest Risk First)
1. Centralize duplicated validation/parsing utilities (CLI index parsing, REST pagination)
2. Unify metadata registries (CLI menu options, navigation registry)
3. Split oversized classes at seam points
4. Consolidate schema evolution into versioned migrations
5. Reduce test wiring duplication with shared fixtures
6. Simplify configuration and logging
7. Refactor navigation service architecture
8. Split large UI controllers and ViewModels

## Summary


#### Why It's Bad:
- Hard to understand and maintain
- Changes affect multiple unrelated features
- High cyclomatic complexity
- Difficult to test in isolation

#### Suggested Solution:
- Split each god class into smaller, focused components
- Extract separate classes for each responsibility
- Use composition over inheritance
- Follow single responsibility principle

#### Impact:
- Improved maintainability
- Easier testing
- Faster debugging
- Reduced risk of breaking unrelated features

### 2. Code Duplication
#### Issue:
- **REST pagination validation**: Copy-pasted across endpoints in [`RestApiServer.java`](src/main/java/datingapp/app/api/RestApiServer.java:181-188, 238-245, 261-268)
- **Distance calculations**: Duplicated in [`CandidateFinder.java`](src/main/java/datingapp/core/matching/CandidateFinder.java:85) and [`MatchQualityService.java`](src/main/java/datingapp/core/matching/MatchQualityService.java:366) using `GeoUtils.distanceKm()`
- **CLI input handling**: Similar index parsing and invalid-input handling patterns duplicated across all handlers
- **List rendering/selection**: Similar loop structures for displaying and selecting items duplicated in CLI handlers

#### Why It's Bad:
- Violates DRY (Don't Repeat Yourself) principle
- Changes require updating multiple places
- Increases risk of inconsistencies
- Larger codebase than necessary

#### Suggested Solution:
- Create shared utility classes for common functionality
- Extract CLI utility with index parsing and list rendering
- Create REST pagination utility
- Create distance calculation utility
- Centralize error handling patterns

#### Impact:
- Reduced LOC
- Easier maintenance
- Consistent behavior
- Faster development

### 3. Architectural Flaws
#### Issue:
- **Schema drift risk**: Fresh schema creation (`SchemaInitializer`) and incremental migration (`MigrationRunner`) maintained separately
- **Navigation duplication**: ViewType enum and controller factory map must stay synchronized
- **Storage interface bloat**: `UserStorage` mixes user CRUD with profile-note persistence
- **Memory pagination**: Default pagination in `InteractionStorage` loads all rows first then slices in memory

#### Why It's Bad:
- Inconsistent database state between fresh installs and upgrades
- High risk of navigation bugs when adding new views
- Storage interfaces violate single responsibility principle
- Memory inefficiency with large datasets

#### Suggested Solution:
- Unify schema evolution into versioned migrations
- Create single source of truth for navigation configuration
- Split storage interfaces by responsibility (UserStorage, ProfileNoteStorage)
- Implement proper SQL-level pagination

#### Impact:
- Reduced schema drift risk
- Simplified navigation maintenance
- Clean storage architecture
- Improved performance with large datasets

### 4. Testing Issues
#### Issue:
- Handler tests manually rebuild service wiring repeatedly
- `TestStorages` duplicates production candidate-filtering behavior
- UI ViewModel tests use `Thread.sleep` which causes flaky tests
- `AppSession` singleton requires explicit resets in every test

#### Why It's Bad:
- Redundant test setup code
- Tests fail randomly due to timing issues
- Difficult to maintain test consistency
- High test setup overhead

#### Suggested Solution:
- Create shared test fixtures and service wiring
- Reuse production logic in test implementations
- Use CompletableFuture or TestFX asynchronous testing
- Refactor AppSession to be injectable instead of singleton

#### Impact:
- Faster test writing
- More reliable tests
- Reduced test maintenance
- Better test coverage

### 5. Configuration & Logging
#### Issue:
- [`AppConfig.java`](src/main/java/datingapp/core/AppConfig.java): 59 delegate methods and 57 builder setters for backward compatibility
- Raw SQL strings in JDBI annotations lack syntax highlighting and error detection
- Tight loop logging without proper `isDebugEnabled()` checks causes unnecessary string allocations

#### Why It's Bad:
- Configuration class is overly complex and hard to maintain
- SQL errors detected only at runtime
- Performance impact from unnecessary logging
- Backward compatibility baggage

#### Suggested Solution:
- Simplify configuration with proper Jackson annotations
- Use SQL template files or query builders
- Add `isDebugEnabled()` checks around debug logging
- Remove unnecessary backward compatibility code

#### Impact:
- Simplified configuration
- Early SQL error detection
- Improved performance
- Easier maintenance

### 6. Navigation Service Complexity
#### Issue:
- [`NavigationService.java`](src/main/java/datingapp/ui/NavigationService.java) is responsible for:
  - ViewType enum registry
  - FXML file resolution
  - Controller instantiation
  - Transition animation handling
  - History stack management
  - Navigation context passing

#### Why It's Bad:
- Single class has too many responsibilities
- Complex state management
- Hard to test and debug

#### Suggested Solution:
- Split into separate classes:
  - ViewRegistry: manages ViewType to FXML/Controller mapping
  - NavigationController: handles navigation history and context
  - TransitionManager: handles animation transitions
  - FXMLoaderHelper: simplifies controller instantiation

#### Impact:
- Clearer responsibilities
- Easier to test
- Faster debugging
- Extensible architecture

### 7. UI Layer Complexity
#### Issue:
- [`ProfileController.java`](src/main/java/datingapp/ui/screen/ProfileController.java): >850 lines - handles profile display, editing, photo management, preferences, and notes
- [`MatchesController.java`](src/main/java/datingapp/ui/screen/MatchesController.java): 31KB - handles match display, filtering, and messaging
- ViewModel classes have extensive business logic

#### Why It's Bad:
- Controllers are overloaded with responsibilities
- Hard to maintain and extend UI features
- Testing UI logic is difficult
- High coupling between UI and business logic

#### Suggested Solution:
- Split large controllers into smaller, feature-focused controllers
- Move business logic from ViewModels to core services
- Create separate controllers for different profile sections
- Implement Presenter pattern for UI logic

#### Impact:
- Simplified UI components
- Better separation of concerns
- Easier UI testing
- Faster feature development

## Compilation Status
✅ **Build successful**: All 89 source files compile with Java 25 preview features  
✅ **Checkstyle passing**: 0 violations  
✅ **PMD analysis clean**: No major issues detected  

## Recommended Refactor Order (Lowest Risk First)
1. Centralize duplicated validation/parsing utilities (CLI index parsing, REST pagination)
2. Unify metadata registries (CLI menu options, navigation registry)
3. Split oversized classes at seam points
4. Consolidate schema evolution into versioned migrations
5. Reduce test wiring duplication with shared fixtures
6. Simplify configuration and logging
7. Refactor navigation service architecture
8. Split large UI controllers and ViewModels

## Summary
The codebase is well-maintained with excellent engineering practices but shows signs of growth-related complexity in key areas. The major issues identified are oversized god classes, code duplication, architectural flaws, testing problems, and configuration complexity. Addressing these issues will significantly improve the codebase's readability, maintainability, and extensibility.


3. Split oversized classes at seam points
4. Consolidate schema evolution into versioned migrations
5. Reduce test wiring duplication with shared fixtures
6. Simplify configuration and logging
7. Refactor navigation service architecture
8. Split large UI controllers and ViewModels

## Summary
The codebase is well-maintained with excellent engineering practices but shows signs of growth-related complexity in key areas. The major issues identified are oversized god classes, code duplication, architectural flaws, testing problems, and configuration complexity. Addressing these issues will significantly improve the codebase's readability, maintainability, and extensibility.

