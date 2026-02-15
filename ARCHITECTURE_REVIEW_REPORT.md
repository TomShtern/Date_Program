# Architecture Review Report
Date: 2026-02-14
Scope: High-impact structure and architecture issues only (no minor/style findings, no code changes).

## Executive Summary
The codebase shows clear layering intent, but there are 5 major architectural risks that will increase change cost and regression risk:
1. Layer boundary leakage (handlers/viewmodels/API frequently bypass core service boundaries).
2. Heavy global mutable singleton state.
3. Inconsistent configuration source (runtime config vs hardcoded defaults).
4. Partially constructible services with nullable dependencies and runtime-mode checks.
5. Very large multi-responsibility classes and consolidated interfaces (high blast radius per change).

## Findings

### 1) Layer boundaries are porous and service boundaries are bypassed
Severity: High

Evidence
- `src/main/java/datingapp/core/ServiceRegistry.java:80` exposes storage objects directly to upper layers.
- `src/main/java/datingapp/Main.java:140` injects storages directly into handlers (`InteractionStorage`, `UserStorage`, `AnalyticsStorage`, `CommunicationStorage`).
- `src/main/java/datingapp/app/api/RestApiServer.java:73` stores direct references to `UserStorage`, `InteractionStorage`, and `CommunicationStorage` and uses them in route handlers (for example `src/main/java/datingapp/app/api/RestApiServer.java:137`, `src/main/java/datingapp/app/api/RestApiServer.java:169`, `src/main/java/datingapp/app/api/RestApiServer.java:210`).
- `src/main/java/datingapp/app/cli/matching/MatchingHandler.java:395` directly mutates and persists match state from the CLI layer (`interactionStorage.update(match)`), and also reads/writes notifications directly (`src/main/java/datingapp/app/cli/matching/MatchingHandler.java:817`, `src/main/java/datingapp/app/cli/matching/MatchingHandler.java:828`).
- `src/main/java/datingapp/ui/viewmodel/data/UiDataAdapters.java:44` exposes storage-level operations to ViewModels (including deletion APIs), used by `src/main/java/datingapp/ui/viewmodel/screen/MatchesViewModel.java:361`.

Why this is a big issue
- Business invariants are split across services and adapters, making behavior inconsistent across CLI/UI/API.
- Any policy change (validation, lifecycle rules, audit behavior) must be applied in multiple layers.

### 2) Global mutable singleton state is pervasive
Severity: High

Evidence
- `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java:26` holds process-global mutable service/database state.
- `src/main/java/datingapp/core/AppSession.java:12` is a global mutable session singleton used widely.
- `src/main/java/datingapp/core/AppClock.java:11` is global mutable time state (`setFixed`, `setClock`).
- `src/main/java/datingapp/ui/NavigationService.java:33` is another process-global singleton.
- Multiple entry points initialize the same global startup path: `src/main/java/datingapp/Main.java:131`, `src/main/java/datingapp/ui/DatingApp.java:26`, `src/main/java/datingapp/app/api/RestApiServer.java:416`.

Why this is a big issue
- Hidden coupling across modules and runtimes.
- Parallel test execution and multi-instance hosting in one JVM become fragile.
- Failures are harder to localize because state ownership is implicit.

### 3) Configuration is not consistently sourced from runtime config
Severity: High

Evidence
- Config loader supports file/env overrides in `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java:68` and `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java:162`.
- Many components use `AppConfig.defaults()` directly instead of injected runtime config:
- `src/main/java/datingapp/core/connection/ConnectionService.java:32`
- `src/main/java/datingapp/core/profile/MatchPreferences.java:396`
- `src/main/java/datingapp/core/profile/MatchPreferences.java:608`
- `src/main/java/datingapp/ui/viewmodel/screen/LoginViewModel.java:36`
- `src/main/java/datingapp/ui/viewmodel/screen/PreferencesViewModel.java:25`
- `src/main/java/datingapp/ui/viewmodel/screen/ProfileViewModel.java:47`
- `src/main/java/datingapp/ui/screen/LoginController.java:77`

Why this is a big issue
- Runtime config changes can be applied in one flow and silently ignored in others.
- Behavior diverges across CLI/UI/core even when one canonical config is expected.

### 4) Several services are partially constructible and rely on runtime null-check mode switching
Severity: High

Evidence
- `src/main/java/datingapp/core/recommendation/RecommendationService.java:50` and `src/main/java/datingapp/core/recommendation/RecommendationService.java:83` allow null dependencies by constructor design.
- Runtime dependency mode checks then throw later (`src/main/java/datingapp/core/recommendation/RecommendationService.java:270`, `src/main/java/datingapp/core/recommendation/RecommendationService.java:607`).
- `src/main/java/datingapp/core/connection/ConnectionService.java:38` allows creation without `UserStorage`; runtime guard at `src/main/java/datingapp/core/connection/ConnectionService.java:49`.
- `src/main/java/datingapp/core/matching/MatchingService.java:33` marks key dependencies optional; runtime fallback/guard at `src/main/java/datingapp/core/matching/MatchingService.java:186` and `src/main/java/datingapp/core/matching/MatchingService.java:227`.
- `src/main/java/datingapp/core/safety/TrustSafetyService.java:33` includes constructors with null core dependencies and runtime checks at `src/main/java/datingapp/core/safety/TrustSafetyService.java:143`.

Why this is a big issue
- Object validity is not guaranteed at construction time.
- Call-site behavior depends on hidden internal mode, increasing runtime failure risk and making contracts unclear.

### 5) Large multi-responsibility units and broad interfaces create high change blast radius
Severity: Medium-High

Evidence
- Very large classes with mixed concerns:
- `src/main/java/datingapp/app/cli/profile/ProfileHandler.java:1` (~852 lines)
- `src/main/java/datingapp/app/cli/matching/MatchingHandler.java:1` (~799 lines)
- `src/main/java/datingapp/core/model/User.java:24` (~707 lines)
- `src/main/java/datingapp/core/profile/MatchPreferences.java:12` (~686 lines)
- `src/main/java/datingapp/storage/jdbi/metrics/JdbiMetricsStorage.java:31` (~570 lines)
- Consolidated interfaces cover many subdomains:
- `src/main/java/datingapp/core/storage/InteractionStorage.java:13`
- `src/main/java/datingapp/core/storage/CommunicationStorage.java:14`
- `src/main/java/datingapp/core/storage/AnalyticsStorage.java:15`

Why this is a big issue
- Harder to evolve one capability without touching unrelated behaviors.
- Increases merge conflicts, test setup complexity, and regression surface.

## Overall Assessment
The core problem is not isolated bugs; it is architectural entropy around boundaries and state ownership. The most important improvements should target strict boundary enforcement, singleton/state reduction, and consistent dependency/config injection.