# MASTER CODEBASE CONSOLIDATION PLAN
**Consolidated Strategy** | **Status:** Executable | **Source Files:** 3

This document outlines the unified strategy to significantly reduce the project's file footprint (by ~60-75%) while maintaining High-Integrity Clean Architecture. It combines the "Domain-Component" philosophy with specific implementation plans.

---

## 🎯 Executive Summary & Philosophy

**The Goal:** Reduce file sprawl and context switching without coupling loosely related logic.
**The Constraint:** Keep individual files under **1,000 LOC**.
**The Method:** **"Domain-Cohesive Aggregation"** (Component-Based Files).

Instead of separating classes by "Technical Layer" (Entity vs Storage vs Service), we will group them by **Domain Component** using **Static Nested Classes** and **Container Classes**.

### Core Techniques
1.  **Generic Repository Pattern:** Eliminate 15+ boilerplate `*Storage` files by creating a `GenericH2Repository<T, ID>` or `AbstractH2Storage<T>` that handles standard CRUD.
2.  **Container Classes:** Group related small classes (Records, Enums, Interfaces) into a single file named after the Feature (e.g., `Matching.java` contains `MatchingService`, `MatchQuality`, `CandidateFinder`).
3.  **Service Aggregation:** Merge micro-services (1-2 methods) into cohesive System Services.

---

## 🏗️ Consolidation Plan: The New Structure

We will transition the codebase from ~40 core/storage files to ~15 high-density Domain Modules.

### 1. Domain & Logic Consolidation (`src/main/java/datingapp/features/`)

These consolidated files will live in `features` or `core` and contain the Logic, Entities, and Interfaces.

| Domain | Proposed Container File | Merged Components (Inner Classes/Records) |
| :--- | :--- | :--- |
| **Match System** | **`MatchingSystem.java`** | • `MatchingService` (Orchestrator)<br>• `MatchQualityCalculator` (Logic)<br>• `CandidateFinder` (Query)<br>• `record MatchConfig` (Tunables) |
| **Identity** | **`IdentityDomain.java`** | • `User` (Entity)<br>• `UserProfileService` (Logic)<br>• `UserPreferences` (Entity)<br>• `Dealbreakers` (Entity) |
| **Relationships** | **`RelationshipDomain.java`** | • `record Match` (Entity)<br>• `record Like`, `Pass`<br>• `RelationshipTransitionService` (Logic)<br>• `TrustSafetyService` (Logic) |
| **Safety** | **`SafetyDomain.java`** | • `record Block`, `Report`<br>• `BlockStorage`, `ReportStorage` (Interfaces)<br>• `SafetyService` (Logic) |
| **Engagement** | **`EngagementSystem.java`** | • `DailyPicks` (Logic & State)<br>• `Achievements` (Logic & Data)<br>• `UndoHistory` (Logic)<br>• `SwipeSession` (State) |
| **Social** | **`SocialMessaging.java`** | • `MessagingService` (Logic)<br>• `Conversation`, `Message` (Entities)<br>• `NotificationService` |

### 2. Storage Implementation Consolidation (`src/main/java/datingapp/storage/`)

These files implements the interfaces defined in the Domain Containers using H2/JDBC.

| Storage Group | Proposed Container File | Merged Components |
| :--- | :--- | :--- |
| **Identity DB** | **`H2IdentityStorage.java`** | • `H2UserStorage`<br>• `H2ProfileViewStorage`<br>• `H2ProfileNoteStorage`<br>• `H2PreferencesStorage` |
| **Relations DB** | **`H2RelationStorage.java`** | • `H2MatchStorage`<br>• `H2LikeStorage`<br>• `H2BlockStorage`<br>• `H2ReportStorage` |
| **Content DB** | **`H2ContentStorage.java`** | • `H2MessageStorage`<br>• `H2ConversationStorage`<br>• `H2NotificationStorage` |
| **Analytics DB** | **`H2AnalyticsStorage.java`** | • `H2UserStatsStorage`<br>• `H2PlatformStatsStorage`<br>• `H2SessionStorage`<br>• `H2AchievementStorage` |

### 3. Infrastructure & Bootstrapping

| Category | Proposed Container File | Merged Components |
| :--- | :--- | :--- |
| **Bootstrap** | **`AppBootstrap.java`** | • `ServiceRegistry` (Wiring)<br>• `AppConfig` (Configuration)<br>• `DatabaseManager` (Schema Init) |
| **CLI** | **`CliHandlers.java`** | • `ProfileHandler`<br>• `MatchingHandler`<br>• `SystemHandler`<br>(Grouped as static inner classes) |

### 4. Alternative Grouping Variations
*Note: Depending on file size, some components may be grouped differently.*

*   **Safety Location:** `Safety` logic can be merged into `SocialMessaging.java` if `SafetyDomain.java` is too small (<200 LOC).
*   **Interaction Services:** `UndoHistory` and `SwipeSession` can be grouped into a generic `InteractionService.java` if they don't fit naturally in `EngagementSystem`.
*   **Storage consolidation:** `H2RelationshipStorage` (Plan A) combines Matches/Likes/Blocks. Alternatively, `H2InteractionStorage` (Plan B) can strictly hold directed edges (Like/Block) while `H2MatchStorage` remains separate. Plan A is preferred for reduction.

---

## 📉 Impact Analysis

| Metrics | Current State | Consolidated State | Change |
| :--- | :---: | :---: | :---: |
| **Core Files** | ~25 | ~6 | **-76%** |
| **Storage Files** | ~20 | ~5 | **-75%** |
| **CLI Files** | ~10 | ~1 | **-90%** |
| **Total Major Files** | **~55** | **~12** | **Significant Reduction** |
| **Avg. File Size** | ~150 LOC | ~600-900 LOC | Higher Density |

---

## 👣 Implementation Workflow

1.  **Phase 1: Foundation**
    *   Create `GenericH2Repository<T>` to handle standard SQL `INSERT`/`SELECT`.
    *   Setup the `datingapp.features` package structure.

2.  **Phase 2: Migration (Iterative)**
    *   **Pick a Domain:** Start with **Safety** (Smallest).
    *   **Create Container:** `SafetyDomain.java`.
    *   **Move & Embed:** Move `Block`, `Report` records inside. Move `TrustSafetyService` logic inside.
    *   **Consolidate Storage:** Create `H2RelationStorage` and implement `BlockStorage` methods there.
    *   **Rewire:** Update `ServiceRegistry` to point to new references.
    *   **Delete:** Remove the old isolated files.
    *   *Repeat for Matching, Identity, etc.*

3.  **Phase 3: Cleanup**
    *   Refactor `ServiceRegistry` into `AppBootstrap`.
    *   Consolidate CLI handlers.

## 🛡️ Safeguards & Rules

1.  **Strict Size Limit:** If a Container File exceeds **1,200 LOC**, it MUST be split (e.g., `Identity.java` -> `IdentityProfile.java` + `IdentitySettings.java`).
2.  **Public Interface:** Inner classes can be `public static` if needed by other domains, but prefer package-private or interface-based access where possible.
3.  **Testing:** Unit tests should target the *Inner Classes* directly (e.g., `new MatchingSystem.TargetFinder(...)`). Don't treat the Container as a monolith to be tested at once.
