# Centralized Codebase Union Report (Deduplicated)

**Generated:** 2026-03-01
**Repository:** `TomShtern/Date_Program`
**Purpose:** Single, centralized union of the six supplied audit/analysis documents, preserving all meaningful content while removing cross-document duplication.

---

## Included source documents

1. `Combined_Report_By_5-agents_24.02.2026.md`
2. `Combined_Report_By_8-agents_21.02.2026.md`
3. `Codebase_Analysis_Report_2026-02-23.md`
4. `CODEBASE_MAINTAINABILITY_FINDINGS_2026-02-22.md`
5. `ARCHITECTURE_REVIEW_REPORT.md`
6. `CODEBASE_MAINTENANCE_ANALYSIS_2026-02-23.md`

---

## How this merged report is organized

This file is intentionally split into two layers:

1. **Deduplicated core (Sections 1–10)**
   Canonical, merged understanding of architecture, strengths, risks, contradictions, and priorities.
2. **Source-preserving inventories (Sections 11–16)**
   Full issue ID inventories and source-specific lists so no significant source content is lost.

### Deduplication rules used

- **Exact duplicates** were collapsed into one canonical item.
- **Near-duplicates** were merged under one canonical root cause with multi-source references.
- **Conflicting metrics** were retained and reconciled explicitly.
- **Historical snapshot differences** were preserved, but a current baseline was labeled clearly.

---

## 1) Executive synthesis

Across all six reports, the same high-confidence pattern emerges:

- **Architecture intent is strong** (clear layered boundaries, service abstractions, strong testing culture).
- **Implementation complexity is high** (god objects, monolithic handlers/controllers, configuration boilerplate, duplicated patterns).
- **Most remediation is feasible incrementally** (many low-risk/mechanical refactors before deeper architectural work).
- **Critical risks** center on:
  - cross-storage atomicity,
  - large mutable domain aggregates,
  - singleton/state management,
  - storage/schema drift,
  - missing security and operational hardening features.

---

## 2) Baseline and metric reconciliation

The source docs were produced over several days and include snapshot variance. All materially relevant values are preserved below.

### 2.1 Canonical baseline used for present-state references

- **Java files:** 116 main + 88 test = **204 total**
- **Java LOC (`tokei`)**: 56,482 total / 43,327 code / 8,502 blank / 4,653 comments
- **Tests (latest referenced baseline):** 983 run, 0 failures, 0 errors, 2 skipped

### 2.2 Conflicting snapshot metrics retained

| Metric           | Reported values across sources | Reconciled interpretation                                               |
|------------------|--------------------------------|-------------------------------------------------------------------------|
| Java file count  | ~87, ~89, 154, ~135, 204       | Different audit dates/snapshots; codebase grew/refactored during period |
| Total LOC        | ~48K, ~49.6K, ~51K, ~56.5K     | Same as above; use latest baseline for current decisions                |
| `User.java` size | ~550 lines vs ~807 lines       | Snapshot/measurement variance; treat as “large god object” regardless   |
| Test count       | 802+, 800+, 899, 983           | Different points in refactor timeline                                   |

### 2.3 Architecture grade card snapshot (preserved from Architecture Review)

| Dimension             | Score           | Snapshot interpretation                             |
|-----------------------|-----------------|-----------------------------------------------------|
| Architecture Quality  | 90/100          | Strong layering and generally clear boundaries      |
| Code Clarity          | 85/100          | Good naming, but large hotspots reduce readability  |
| Maintainability       | 88/100          | Good patterns, but rising complexity debt           |
| AI-Agent Friendliness | 82/100          | Rich docs, but oversized files hinder navigation    |
| Simplicity            | 78/100          | Over-engineering in selected areas                  |
| Documentation         | 92/100          | Strong coverage and historical context              |
| **Overall**           | **B+ (87/100)** | Strong foundation with notable maintainability debt |

---

## 3) Preserved architecture strengths (cross-source consensus)

1. **Layering quality is strong** (UI/app/core/storage separation is generally understandable).
2. **Result-oriented service style exists** (business outcomes often modeled as result records).
3. **Testing posture is strong** (large test suite, in-memory test storages, deterministic time practices present).
4. **Config domain model exists** (`AppConfig` with sub-record organization).
5. **Deterministic pair-ID strategy exists** for two-user aggregates.
6. **Documentation volume is high** (multiple architecture/audit docs, gotchas, guidance files).

### 3.1 Preserved key dataflow walkthroughs (source-compressed)

These walkthroughs were repeatedly emphasized in source reports and are kept here for implementation clarity.

#### Flow A — Like → Match creation (high-risk flow)

1. Swipe is processed in matching orchestration.
2. Like is persisted.
3. Mutual-like check can transition state to match.
4. Related side effects (metrics/undo/notifications/friend transitions) may touch different storage contracts.

**Risk called out across sources:** if one write succeeds and a downstream write fails, state can become inconsistent (canonical risk `U-01`, source ID `F-001`).

#### Flow B — Message send → conversation read-state update

1. Message write occurs.
2. Conversation unread/read markers should update symmetrically.

**Risk called out in source:** sender-side unread indicator drift when last-read marker is not advanced (`F-042`).

#### Flow C — Candidate discovery pipeline

1. Storage-side prefiltering (age/location/etc.)
2. In-memory filtering/scoring/ranking pipeline
3. Recommendation constraints (limits/activity thresholds)

**Risk called out in sources:** duplicated filter logic and unbounded list loading (`F-022`, `F-060`, multiple duplication clusters).

#### Flow D — Config and bootstrap wiring

1. Bootstrap initializes config, storage, and services.
2. CLI and JavaFX entrypoints consume shared composition artifacts.

**Risk called out in sources:** defaults/static config coupling and registry over-centralization (`F-016`, `F-017`, `U-03`, `U-04`).

---

## 4) Deduplicated unified risk register

The table below merges repeated findings into canonical root causes.

| Unified ID | Canonical root cause                                                                                                 | Highest severity seen | Primary source anchors                                      |
|------------|----------------------------------------------------------------------------------------------------------------------|-----------------------|-------------------------------------------------------------|
| U-01       | Cross-storage operations not atomic                                                                                  | CRITICAL              | F-001, Architecture §2.1, 5-agent critical cluster          |
| U-02       | [MOVED/SPLIT - sync issue resolved; residual aggregate-size work tracks via U-05/U-07] `User` aggregate is oversized | CRITICAL              | 5-agent Critical #1/#2, Architecture §2.2, Maintenance #9   |
| U-03       | `ServiceRegistry` / service-locator over-centralization                                                              | HIGH/CRITICAL         | 5-agent Critical #3, Maintenance #2, Architecture §5.1      |
| U-04       | `AppConfig` builder/shape complexity and defaults coupling                                                           | HIGH/CRITICAL         | 5-agent Critical #6, F-016/F-017, Maintenance #1            |
| U-05       | Monolithic orchestration classes (`MatchingHandler`, `ProfileHandler`, etc.)                                         | HIGH/CRITICAL         | 5-agent Critical #5, Maintainability #4, Maintenance #3     |
| U-06       | Storage implementation bloat + mapper/column drift risk                                                              | HIGH/CRITICAL         | 5-agent pending #4/#11, F-047/F-049, Maintainability #5/#16 |
| U-07       | UI layer bloat (controllers/viewmodels too large)                                                                    | HIGH                  | 5-agent High #8, Codebase Analysis §1.1, Maintenance #7     |
| U-08       | Widespread duplication patterns (CLI/REST/scoring/helpers)                                                           | HIGH                  | 5-agent High #7, F-016..F-029, Maintainability #9/#10/#17   |
| U-09       | Inconsistent error/null handling contract                                                                            | HIGH                  | F-036/F-049, 5-agent High #10b & Low #18                    |
| U-10       | Singleton/global mutable state hazards (`AppSession`, navigation, db manager)                                        | CRITICAL/HIGH         | F-045/F-054, maintainability singleton reset findings       |
| U-11       | Schema/versioning and migration discipline gaps                                                                      | HIGH                  | F-010, Maintainability #1, Architecture §3.5                |
| U-12       | Missing security foundations (authN/authZ, verification/rate limiting)                                               | HIGH/CRITICAL         | F-002/F-003/F-004/F-011                                     |
| U-13       | Test infrastructure reliability issues (monolith test utils, sleeps, fixture duplication)                            | MEDIUM/HIGH           | Maintainability #6/#7/#20/#21, Maintenance #4               |
| U-14       | Magic constants / tuning spread across services                                                                      | MEDIUM                | F-020, Maintenance #10, 5-agent Medium #12                  |
| U-15       | Navigation and mapping registry duplication / complexity                                                             | HIGH/MEDIUM           | Maintainability #2/#8, Architecture §3.4, Maintenance #11   |

### 4.1 Evidence anchors (high-signal references preserved)

| Unified ID | Example source anchors (file/section references retained from source docs)                                                          |
|------------|-------------------------------------------------------------------------------------------------------------------------------------|
| U-01       | `ConnectionService` transactional paths, interaction+communication dual writes (F-001 references)                                   |
| U-02       | `User.java` [MOVED/SPLIT] synchronization-heavy accessor/setter surface removed; residual aggregate-size work tracks via U-05/U-07. |
| U-03       | `ServiceRegistry` constructor/getter concentration (16–17 dependency profile)                                                       |
| U-04       | `AppConfig` defaults usage spread + builder/delegate breadth (`F-016`, `F-017`)                                                     |
| U-05       | `MatchingHandler` and peers with mixed concerns/dependency breadth                                                                  |
| U-06       | `JdbiMatchmakingStorage` concern mixing + mapper/column consistency risk (`F-047`, pending #4/#11)                                  |
| U-07       | `ProfileController`/`ProfileViewModel` and peers with oversized responsibilities                                                    |
| U-08       | CLI parsing duplication, REST pagination duplication, scoring duplication clusters                                                  |
| U-09       | Result/exception/null inconsistency (`F-036`, `F-049`)                                                                              |
| U-10       | Singleton/global mutable state issues (`F-045`, `F-054`)                                                                            |

---

## 5) Critical and high-priority canonical actions

### 5.1 Immediate critical track

1. **U-01:** introduce transaction/compensation safety for cross-storage flows.
2. **U-12:** establish basic auth + verification hardening + endpoint rate limiting.
3. **U-10:** harden singleton/thread model or move to injected lifecycle-managed instances.

### 5.2 High-impact refactor track

4. **U-05/U-07 (absorbs former U-02 scope):** split large entities/handlers/controllers into focused components.
5. **U-04/U-14:** simplify config construction and centralize tuneable thresholds.
6. **U-06/U-11:** decompose storage classes and establish versioned migration discipline.
7. **U-08/U-09:** eliminate duplicate patterns and standardize null/error/result contracts.

### 5.3 Implementation reference cards (problem → failure mode → fix path)

#### Card A — U-01 Cross-storage atomicity

- **Observed failure mode:** write A succeeds, write B fails, aggregate state diverges.
- **Short-term fix:** compensating rollback path where safe.
- **Preferred fix:** transactional unit-of-work boundary for multi-write operations.
- **Source IDs:** `F-001`, Architecture Critical §2.1.

#### Card B — U-02 User aggregate + synchronization [MOVED/SPLIT]

- **Observed status:** [OUTDATED] Synchronization has been removed. Remaining aggregate-size work now tracks via U-05/U-07.
- **Short-term fix:** [VERIFIED] Unnecessary synchronization has been removed.
- **Preferred fix:** split into focused value objects/components.
- **Source IDs:** 5-agent Critical #1/#2, `F-046`, Maintenance #9.

#### Card C — U-04 AppConfig complexity

- **Observed failure mode:** many defaults call sites + large builder/delegate surface, high change friction.
- **Short-term fix:** reduce delegate noise and defaults coupling hotspots.
- **Preferred fix:** bounded config modules + cleaner construction path.
- **Source IDs:** `F-016`, `F-017`, 5-agent Critical #6, Maintenance #1.

#### Card D — U-05 Monolithic orchestration classes

- **Observed failure mode:** handlers/controllers contain orchestration, formatting, validation, and flow control together.
- **Short-term fix:** extract utility helpers (input parsing, pagination validation, list rendering).
- **Preferred fix:** split by user-flow domain into focused handlers/controllers.
- **Source IDs:** 5-agent Critical #5, Maintainability #4, Maintenance #3.

#### Card E — U-06 Storage complexity and drift risk

- **Observed failure mode:** oversized storage classes + mapper parsing debt + schema/column drift exposure.
- **Short-term fix:** isolate mappers/codecs and normalize known dual-format data.
- **Preferred fix:** decompose storage responsibilities and enforce migration/version governance.
- **Source IDs:** pending #4/#11, `F-047`, `F-049`, Maintainability #1/#5/#16.

#### Card F — U-12 Security baseline gaps

- **Observed failure mode:** missing auth and verification hardening permits impersonation/abuse vectors.
- **Short-term fix:** endpoint/API guardrails + verification throttling.
- **Preferred fix:** complete authN/authZ model and secure verification integrations.
- **Source IDs:** `F-002`, `F-003`, `F-004`, `F-011`.

---

## 6) Roadmap synthesis (merged from all reports)

### Phase A — Quick wins (hours to ~2 weeks)

- Centralize CLI parsing and REST pagination validation.
- Consolidate redundant utility methods (`EnumSetUtil`, repeated wrappers).
- Replace fragile `Thread.sleep()` UI tests with deterministic async waits.
- Fix obvious consistency issues (soft-delete filtering parity, sender unread-state update).
- Remove explicit clock bypasses in factory wiring where deterministic clocking is required.

#### Practical first-step checklist for Phase A

| Task                        | First concrete step                                                          | Typical source anchors |
|-----------------------------|------------------------------------------------------------------------------|------------------------|
| CLI parsing duplication     | Extract a single `parseOneBasedIndex` utility and replace repeated callsites | Maintainability #9/#10 |
| REST pagination duplication | Create one shared validator helper and rewire endpoint checks                | Maintainability #17    |
| Test sleep flakiness        | Replace `Thread.sleep` waits with deterministic async synchronization helper | Maintainability #20    |
| Soft-delete parity          | Align test-storage filtering behavior with production semantics              | `F-063`                |
| Sender unread drift         | Update conversation read-marker flow after message send                      | `F-042`                |
| Clock consistency           | Remove hardcoded explicit clock wiring bypasses                              | `F-062`                |

### Phase B — Refactoring sprint (weeks)

- Break down monolithic CLI handlers and service classes.
- Simplify `AppConfig` builder surface and defaults flow.
- Remove unnecessary synchronization and clarify concurrency contract.
- Standardize error/null handling and reduce direct storage coupling from UI/orchestration code.

### Phase C — Strategic architecture (multi-week)

- Implement robust cross-storage transaction coordination pattern.
- Add authentication/authorization strategy and secure verification pipeline.
- Adopt migration framework/version-table discipline.
- Split service registry into bounded registries or explicit constructor DI.

#### Practical first-step checklist for Phase C

| Task                    | First concrete step                                                             | Typical source anchors |
|-------------------------|---------------------------------------------------------------------------------|------------------------|
| Transaction safety      | Identify all multi-storage write paths and classify compensation feasibility    | `F-001`, U-01          |
| Authentication baseline | Introduce an adapter boundary for identity/session checks before endpoint logic | `F-002`, `F-011`       |
| Migration discipline    | Introduce schema version tracking and migration ordering convention             | `F-010`, U-11          |
| Registry decomposition  | Slice registry by bounded domain responsibilities                               | U-03                   |

### Phase D — Longer-term platform improvements

- Optional DI framework adoption if complexity warrants.
- Observability/monitoring hardening and API contract documentation.
- Feature-oriented strategic additions (notifications, media pipeline, experimentation tooling, etc.).

---

## 7) Strategic option sets preserved (from 8-agent synthesis)

### 7.1 Configuration strategy options

- Jackson databinding-centric
- properties-file-first
- env-var-first overlays
- framework-driven config (higher coupling)

### 7.2 API security strategy options

- API key middleware
- JWT token model
- IP whitelisting for local-only mode
- mTLS (high complexity)

### 7.3 Persistence simplification options

- Continue with JDBI SQL-object improvements
- Query DSL style generation
- heavier ORM paths (higher architecture impact)

### 7.4 Concurrency strategy options

- Thread confinement and immutable boundaries first
- RW-lock/atomic patterns where needed
- avoid premature complex actor models unless justified

### 7.5 Product/value-add option backlog retained

- realtime notifications
- photo/media infrastructure + moderation
- analytics and experimentation
- social/login extensions
- i18n/multi-region expansion

---

## 8) Contradictions and ambiguity handling

### 8.1 What was contradictory

- Size/count snapshots differ by date.
- Severity labels differ between reports for similar issues.
- Some reports describe the same issue using different scopes (e.g., “god object” vs “synchronization overhead”).

### 8.2 How this document resolves contradictions

- Keeps all values historically, marks latest baseline separately.
- Uses **highest-severity observed** for canonical risk ranking.
- Merges same-root-cause findings into one canonical item with source references.

---

## 9) Preserved commands, quality gates, and constraints

### Build/test commands retained

- `mvn compile && mvn exec:exec`
- `mvn javafx:run`
- `mvn test`
- `mvn -Ptest-output-verbose test`
- `mvn spotless:apply verify`
- `mvn verify`
- `tokei src/main/java src/test/java`

### Quality constraints retained

- Java 25 (preview)
- Spotless + Palantir format
- Checkstyle, PMD
- JaCoCo line gate (0.60 minimum)

---

## 10) Source-to-union coverage map

| Source section family                               | Where preserved in this union file |
|-----------------------------------------------------|------------------------------------|
| 5-agent critical/high/medium/low clusters           | §§4, 11, 12                        |
| 5-agent resolved + pending verified findings        | §12                                |
| 8-agent F-001..F-068 full finding inventory         | §11                                |
| 8-agent architecture/dataflow + strategy options    | §§3, 7, 13                         |
| Codebase Analysis observations/roadmap              | §§4, 6, 14                         |
| Maintainability Findings (21 items)                 | §14                                |
| Architecture Review score/strengths/issues          | §§2, 3, 15                         |
| Maintenance Analysis (12 issues + phased estimates) | §16                                |

---

## 11) Full inventory — Combined 8-agent report (F-001 to F-068)

> Preserved as concise ID catalog to retain all finding identifiers and categories.

### 11.1 Category A — Missing implementations/features (F-001..F-015)

| ID    | Title                                           | Severity |
|-------|-------------------------------------------------|----------|
| F-001 | Cross-Storage Atomic Transactions Missing       | CRITICAL |
| F-002 | Authentication & Authorization Layer Missing    | HIGH     |
| F-003 | Real Email/SMS Verification Not Implemented     | HIGH     |
| F-004 | Verification Code Rate Limiting Not Implemented | HIGH     |
| F-005 | Persistent Undo State Missing                   | MEDIUM   |
| F-006 | No Caching Layer for Hot Paths                  | MEDIUM   |
| F-007 | Notification Delivery Mechanism Missing         | MEDIUM   |
| F-008 | Image Upload/Processing Infrastructure Missing  | MEDIUM   |
| F-009 | Geographic Location Validation Missing          | MEDIUM   |
| F-010 | Schema Migration Strategy Missing               | HIGH     |
| F-011 | REST API Rate Limiting Missing                  | HIGH     |
| F-012 | User Presence Tracking Not Implemented          | MEDIUM   |
| F-013 | Missing Audit Logging                           | MEDIUM   |
| F-014 | Incomplete Achievement System                   | MEDIUM   |
| F-015 | Missing Cleanup Logic / Data Retention          | LOW      |

### 11.2 Category B — Duplication/redundancy/simplification (F-016..F-029)

| ID    | Title                                                           | Severity |
|-------|-----------------------------------------------------------------|----------|
| F-016 | Static `AppConfig.defaults()` in Domain Models (45+ call sites) | HIGH     |
| F-017 | AppConfig Excessive Boilerplate (904 lines)                     | MEDIUM   |
| F-018 | Deterministic ID Generation Duplicated                          | MEDIUM   |
| F-019 | Redundant Date Calculation in RecommendationService             | LOW      |
| F-020 | Hardcoded Scoring Constants Across Services                     | MEDIUM   |
| F-021 | Manual JDBI Enum Mapping Redundancy                             | MEDIUM   |
| F-022 | In-Memory vs SQL Filtering Duplication                          | MEDIUM   |
| F-023 | Repetitive Builder Pattern Across Services                      | LOW      |
| F-024 | TestStorages 1000+ Lines of Repetitive CRUD                     | LOW      |
| F-025 | Multiple Daily Limit Check Implementations                      | MEDIUM   |
| F-026 | Achievement Display Logic Duplicated Across Handlers            | LOW      |
| F-027 | Repeated EnumSet Copying Pattern                                | LOW      |
| F-028 | Hardcoded Standout Rank Limits                                  | LOW      |
| F-029 | Repeated `safeExecute` Error Handling                           | LOW      |

### 11.3 Category C — Logic/architecture flaws (F-030..F-044)

| ID    | Title                                                  | Severity |
|-------|--------------------------------------------------------|----------|
| F-030 | ServiceRegistry God Object (16 parameters)             | HIGH     |
| F-031 | Potential Negative Age / Timezone Handling             | HIGH     |
| F-032 | StorageBuilder Bypasses Sync and Validation            | HIGH     |
| F-033 | Race Condition in Daily Pick Caching                   | HIGH     |
| F-034 | Match.transitionToFriends Missing End Metadata         | MEDIUM   |
| F-035 | Optional Dependencies Leading to Runtime Errors        | MEDIUM   |
| F-036 | Inconsistent Error Handling Strategy                   | HIGH     |
| F-037 | Match.restoreDeletedAt Bypasses Invariants             | MEDIUM   |
| F-038 | Tight Coupling Between CLI and Domain Logic            | MEDIUM   |
| F-039 | `Optional.get`/null anti-pattern in ConnectionService  | MEDIUM   |
| F-040 | Static Initialization Order Dependency in Main         | LOW      |
| F-041 | Missing State Validation in `User.pause()`             | LOW      |
| F-042 | Conversation Not Updated After Message Send            | MEDIUM   |
| F-043 | `maxDistanceKm` Default Discrepancy                    | LOW      |
| F-044 | ProfileService Large Class / Multiple Responsibilities | MEDIUM   |

### 11.4 Category D — Clear problems/issues/mistakes (F-045..F-068)

| ID    | Title                                              | Severity |
|-------|----------------------------------------------------|----------|
| F-045 | Thread-Unsafe Singletons                           | CRITICAL |
| F-046 | [NOT VALID] Excessive Synchronization in User.java | HIGH     |
| F-047 | SQL Column Drift Risk (`ALL_COLUMNS`)              | HIGH     |
| F-048 | Missing DB Indexes on `deleted_at` Columns         | HIGH     |
| F-049 | Inconsistent Null Returns vs Optional              | HIGH     |
| F-050 | Virtual Thread Misuse in ChatViewModel             | MEDIUM   |
| F-051 | Unclosed Resources / Memory Leak Risks             | HIGH     |
| F-052 | CandidateFinder Location Logging May Leak PII      | MEDIUM   |
| F-053 | Swallowed Exceptions in AppSession Listeners       | MEDIUM   |
| F-054 | DatabaseManager Static Mutable State               | MEDIUM   |
| F-055 | Hardcoded Database URL                             | MEDIUM   |
| F-056 | SwipeState.LockStripes Custom Complexity           | LOW      |
| F-057 | Coarse-Grained Lock in Auto-Ban                    | MEDIUM   |
| F-058 | Nested Exception Handling in Main.java             | LOW      |
| F-059 | Hardcoded String Constants in Main switch          | LOW      |
| F-060 | Unbounded Candidate/Likers Lists (OOM risk)        | MEDIUM   |
| F-061 | Hardcoded Magic Number in CandidateFinder          | LOW      |
| F-062 | StorageFactory Clock Inconsistency                 | HIGH     |
| F-063 | TestStorages Interaction Soft-Delete Mismatch      | MEDIUM   |
| F-064 | FXML Controllers Excessive SuppressWarnings        | LOW      |
| F-065 | ProfileNote Validation Scattered in Constructor    | LOW      |
| F-066 | Missing Input Validation in CLI Handlers           | MEDIUM   |
| F-067 | Inconsistent Logging Patterns                      | LOW      |
| F-068 | Missing Javadoc on User Setters                    | LOW      |

---

## 12) Full inventory — Combined 5-agent report

### 12.1 Main issue groups

| Group | Title                                              | Severity |
|-------|----------------------------------------------------|----------|
| #1    | User Entity God Object                             | CRITICAL |
| #2    | [NOT VALID] Excessive Synchronization              | CRITICAL |
| #3    | ServiceRegistry God Object                         | CRITICAL |
| #4    | Massive Service Classes                            | CRITICAL |
| #5    | CLI Handler God Classes                            | CRITICAL |
| #6    | AppConfig Builder Complexity                       | CRITICAL |
| #7    | Pervasive Code Duplication (7a–7j sub-issues)      | HIGH     |
| #8    | UI Layer Bloat                                     | HIGH     |
| #9    | Storage Layer Complexity (9a–9e sub-issues)        | HIGH     |
| #10   | Architectural Inconsistencies (10a–10h sub-issues) | HIGH     |
| #11   | Testing Infrastructure Issues                      | MEDIUM   |
| #12   | Magic Numbers / Excessive Constants                | MEDIUM   |
| #13   | Complex Conditional Logic in Services              | MEDIUM   |
| #14   | Nested Types Organization                          | MEDIUM   |
| #15   | Deprecated / Dead Code in User Entity              | MEDIUM   |
| #16   | Configuration Coupling                             | MEDIUM   |
| #17   | Inconsistent Naming Conventions                    | LOW      |
| #18   | Missing Null Safety                                | LOW      |
| #19   | Logging Approach Inconsistency                     | LOW      |
| #20   | UI Magic Constants                                 | LOW      |
| #21   | Complex Nested Record Types                        | LOW      |
| #22   | StorageBuilder Pattern Complexity                  | LOW      |

### 12.2 Previously resolved findings (16, retained for historical completeness)

| #  | Finding                                                       | Status     |
|----|---------------------------------------------------------------|------------|
| 1  | `AppConfig.defaults()` per-request static factory usage issue | ✅ FIXED    |
| 2  | N+1 query in `RestApiServer.toMatchSummary()`                 | ✅ FIXED    |
| 3  | AppConfig delegate-method bloat (58 methods)                  | ✅ FIXED    |
| 5  | Conversation mutability inconsistency                         | ✅ RESOLVED |
| 6  | NavigationService deprecated API concern                      | ✅ RESOLVED |
| 7  | Duplicate `SwipeResult` naming collision                      | ✅ RESOLVED |
| 8  | `processSwipe()` null-check after requireNonNull              | ✅ RESOLVED |
| 9  | Two-phase candidate filtering semantic gap                    | ✅ RESOLVED |
| 10 | `countMatchesFor()` loads-all default inefficiency            | ✅ RESOLVED |
| 12 | `findPendingLikersWithTimes()` unnecessary full-call path     | ✅ RESOLVED |
| 13 | Deprecated constructor timezone leak                          | ✅ RESOLVED |
| 14 | TOCTOU race in default save-like/match path                   | ✅ RESOLVED |
| 15 | `MatchingService` not final (older concern)                   | ✅ RESOLVED |
| 16 | Hardcoded Main menu numbering                                 | ✅ RESOLVED |
| 17 | `toConversationSummary()` N+1 count query                     | ✅ RESOLVED |
| 18 | Conversation constructor invariant trap                       | ✅ RESOLVED |

### 12.3 Still pending verified findings

| #  | Finding                                                  | Status    |
|----|----------------------------------------------------------|-----------|
| 4  | `JdbiMatchmakingStorage` combines 5 concerns (896 lines) | ⏳ Pending |
| 11 | `JdbiUserStorage.Mapper` dual-format parsing debt        | ⏳ Pending |

---

## 13) Full inventory — Architecture Review report

### 13.1 Grade card (preserved)

| Dimension             | Score           |
|-----------------------|-----------------|
| Architecture Quality  | 90/100          |
| Code Clarity          | 85/100          |
| Maintainability       | 88/100          |
| AI-Agent Friendliness | 82/100          |
| Simplicity            | 78/100          |
| Documentation         | 92/100          |
| **Overall**           | **B+ (87/100)** |

### 13.2 Numbered issue sets retained

- **Critical:** non-atomic cross-storage ops, `User` god object, `RestApiServer` SRP violation.
- **High:** AppConfig parameter explosion, match-quality overengineering, lock striping complexity, navigation service breadth, schema drift risk.
- **Medium/Low:** result type naming inconsistency, nested-type overuse, deprecation cleanup, magic numbers, constructor bloat, FXML complexity, optional DI framework discussion.

### 13.3 AI-agent/documentation section retained

Preserved needs:
- service dependency diagram
- state-machine diagrams
- ER diagram
- API contract docs (OpenAPI/Swagger)
- worked examples for complex scoring algorithms

---

## 14) Full inventory — Maintainability Findings (2026-02-22)

### High-severity findings

| # | Finding                                                           |
|---|-------------------------------------------------------------------|
| 1 | Schema evolution split (initializer vs migration path drift risk) |
| 2 | NavigationService UI god object                                   |
| 3 | Main CLI menu duplicated definitions                              |
| 4 | MatchingHandler oversized/multi-flow                              |
| 5 | JdbiMatchmakingStorage oversized/multi-concern                    |
| 6 | Handler tests duplicate wiring/composition                        |
| 7 | TestStorages duplicate production filtering logic                 |

### Medium-severity findings

| #  | Finding                                                     |
|----|-------------------------------------------------------------|
| 8  | Navigation surface declared twice (enum + mapping registry) |
| 9  | CLI index parsing duplication                               |
| 10 | CLI list/render loop duplication                            |
| 11 | DI semantics unclear + inline defaults                      |
| 12 | AppConfig backward-compat and builder bloat                 |
| 13 | UserStorage mixes CRUD and notes concerns                   |
| 14 | Distance calculations duplicated/coupled                    |
| 15 | InteractionStorage pagination loads-all behavior            |
| 16 | JdbiUserStorage mapper legacy+json complexity               |
| 17 | REST pagination validation duplication                      |
| 18 | `SwipeResult` reused for different semantics                |
| 19 | ProfileController orchestration bloat                       |
| 20 | UI viewmodel tests rely on `Thread.sleep`                   |
| 21 | AppSession singleton reset leakage in tests                 |

---

## 15) Full inventory — Codebase Analysis report (2026-02-23)

Preserved unique findings/themes:

1. UI controller + viewmodel bloat (notably profile stack).
2. AppConfig + StorageFactory centralization complexity.
3. Core-domain complexity concentration in candidate/scoring paths.
4. JDBI raw SQL annotation readability/maintainability concerns; recommendation to externalize SQL.
5. PMD CPD positive note (low direct copy-paste by that lens).
6. Logging-in-tight-loop caution and mutable entity boundary caution.

Preserved phased guidance:

- Immediate: remove obvious config/logging overhead.
- Short-term: externalize SQL text and improve infra readability.
- Medium-term: UI decomposition into reusable components.

---

## 16) Full inventory — Maintenance Analysis report (2026-02-23)

### 16.1 The 12 maintainability issues retained

| #  | Issue                                           |
|----|-------------------------------------------------|
| 1  | AppConfig Builder Bloat                         |
| 2  | ServiceRegistry as God Object                   |
| 3  | Monolithic CLI Handler Classes                  |
| 4  | TestStorages Monolith                           |
| 5  | EnumSetUtil Redundant Methods                   |
| 6  | LoggingSupport Interface Anti-Pattern           |
| 7  | UI Controller Complexity                        |
| 8  | RecommendationService Multiple Responsibilities |
| 9  | [NOT VALID] User Model Synchronization Overhead |
| 10 | Inlined Constants from Deleted ScoringConstants |
| 11 | ViewModelFactory Manual Controller Mapping      |
| 12 | PerformanceMonitor Timer Uses AppClock          |

### 16.2 Prioritization preserved

- **Phase 1 (quick wins):** utility cleanup, constants centralization, timer fix, split test storages.
- **Phase 2:** logging approach replacement, recommendation service split, user sync review.
- **Phase 3:** major refactors (`AppConfig`, `ServiceRegistry`, CLI/UI decomposition).

---

## 17) Consolidated implementation matrix (actionable union)

| Priority | Action                                                                    | Merged IDs (examples)                      |
|----------|---------------------------------------------------------------------------|--------------------------------------------|
| P0       | Make cross-storage operations atomic/compensated                          | U-01, F-001                                |
| P0       | Add baseline auth + verification/rate-limit hardening                     | U-12, F-002/F-003/F-004/F-011              |
| P1       | Split remaining `User` aggregate concerns and formalize state transitions | U-02 (moved), U-05/U-07, F-031/F-041/F-046 |
| P1       | Decompose monolithic handlers/controllers/services                        | U-05/U-07, #3/#7/#8/#19, issue sets 4/7/8  |
| P1       | Simplify AppConfig and centralize scoring thresholds                      | U-04/U-14, F-016/F-017/F-020, Issue #1/#10 |
| P1       | Decompose storage classes and normalize mapping/schema constants          | U-06/U-11, F-047/F-049, pending #4/#11     |
| P2       | Standardize error/null/result contracts                                   | U-09, F-036/F-049                          |
| P2       | Eliminate registry/mapping duplication and singleton coupling             | U-10/U-15, F-045/F-054, issues #8/#11/#21  |
| P2       | Stabilize tests and fixture architecture                                  | U-13, F-024/F-063, findings #6/#7/#20      |

### 17.1 Impact assessment snapshots (preserved as decision aids)

| Change area                        | Current state                                       | Expected state after refactor                                   |
|------------------------------------|-----------------------------------------------------|-----------------------------------------------------------------|
| User aggregate and synchronization | Large mutable object with [OUTDATED] sync surface   | Smaller bounded objects + clearer thread model                  |
| Config surface                     | Many defaults touchpoints + broad builder/delegates | Fewer touchpoints, cleaner config boundaries                    |
| Handler/controller complexity      | Multi-flow classes with broad dependency surfaces   | Focused flow components, lower test setup cost                  |
| Storage and schema drift risk      | Mixed responsibilities + mapper/column coupling     | Separated responsibilities + controlled migration/version model |
| Test reliability                   | sleep-based waits + monolithic test utility classes | deterministic async tests + focused test fixtures/utilities     |

| Operational quality area     | Current risk                                   | Target risk profile                                |
|------------------------------|------------------------------------------------|----------------------------------------------------|
| Cross-storage consistency    | High inconsistency exposure on partial failure | bounded transactional/compensating strategy        |
| Auth/security baseline       | Missing guardrails in key paths                | baseline auth + verification + rate-limit controls |
| Error/null contracts         | Mixed conventions                              | consistent, documented layer policy                |
| Navigation/registry coupling | duplicated mappings and hidden dependencies    | clearer mappings and explicit dependencies         |

---

## 18) Notes for future readers/agents

1. These sources are **historical snapshots** and not all metrics agree.
2. Use the reconciled baseline in Section 2 for current-state comparisons.
3. Treat this report as the centralized index; if a question needs historical wording, consult the source file listed in the coverage map.

---

## 19) Completion summary

This document provides:

- one deduplicated canonical view,
- full preservation of all major source issue inventories,
- explicit contradiction handling,
- retained strategic options and roadmap content,
- and source coverage mapping so nothing meaningful is orphaned.

---

## 20) Source-fidelity expansion annex (restored detail)

This annex restores high-value details that were previously compressed, while keeping deduplication intact.

### 20.1 Critical issue deep-dives (expanded)

#### C-1) `User` entity god object + [OUTDATED] synchronization overhead

**Source threads:** 5-agent Critical #1/#2, Architecture Review Critical, 8-agent `F-046`, Maintenance issue #9.

Preserved detailed points:

- `User` is repeatedly identified as oversized (snapshot values vary by report: ~550 to ~807 lines).
- Concern cluster is twofold:
   1. **Responsibility concentration** (identity/profile/location/preferences/lifestyle/verification/state-related behavior in one aggregate),
   2. **broad synchronized surface** (large synchronized accessor/mutator footprint, increased complexity and contention risk).
- Maintainers and auditors converged on decomposition as the preferred trajectory:
   - `User` identity/state core,
   - profile/value-object components,
   - preference/lifestyle blocks,
   - verification subcomponent,
   - explicit mutation boundaries.
- **STATUS UPDATE (2026-03-09):** [NOT VALID] Synchronization has been removed from `User.java`. The aggregate remains a high priority for decomposition (God Object), but the concurrency concerns are outdated.

#### C-2) `ServiceRegistry` dependency concentration

**Source threads:** 5-agent Critical #3, 8-agent `F-030`, Maintenance issue #2.

Preserved detailed points:

- Registry constructor/dependency breadth was repeatedly flagged (16–17 dependency scale in source snapshots).
- Main risks:
   - ripple effects from constructor changes,
   - hidden dependency discovery cost,
   - harder unit test setup and isolation.
- Source-consistent mitigation path:
   - split into domain-oriented registries (matching/profile/messaging/metrics/safety),
   - keep composition root explicit,
   - evaluate DI framework only if post-split complexity still exceeds tolerance.

#### C-3) Massive service classes and orchestration bloat

**Source threads:** 5-agent Critical #4/#5, Architecture Review high-priority service findings, Maintenance issues #3/#8.

Preserved detailed points:

- Monolithic concerns are present in multiple layers:
   - `ProfileService` (large scoring and validation surface),
   - `RecommendationService` (multi-role logic),
   - large CLI handler orchestration (especially matching flows),
   - UI controller/viewmodel concentration.
- Reported smell pattern: mixed concerns (business rules + parsing/rendering + state transitions + infrastructure interactions).
- Preferred split seam from sources:
   - flow-based decomposition (browse/matches/requests/standouts/notifications),
   - small helper abstractions for shared parsing/validation,
   - extracted scoring primitives for repeated category methods.

#### C-4) `AppConfig` complexity and defaults coupling

**Source threads:** 5-agent Critical #6, 8-agent `F-016`/`F-017`, Maintenance issue #1.

Preserved detailed points:

- Repeated concerns:
   - large builder/setter/delegate surface,
   - defaults/static coupling pressure,
   - positional complexity and change friction.
- Repeatedly endorsed direction:
   - keep logical grouping semantics,
   - reduce delegation noise,
   - prefer straightforward databinding + bounded construction interfaces.

---

### 20.2 High issue #7 duplication sub-issues (7a–7j)

This table restores the full 7a–7j breakdown that was previously abbreviated.

| Sub-issue | Source-preserved detail                                          | Impact                                  | Low-risk first step                            |
|-----------|------------------------------------------------------------------|-----------------------------------------|------------------------------------------------|
| 7a        | REST pagination validation repeated in multiple endpoints        | Drift and inconsistent error behavior   | Extract one shared pagination validator/helper |
| 7b        | CLI input/index parsing loops repeated across handlers           | Bug-prone UX divergence                 | Centralize one-based index parsing utility     |
| 7c        | Distance computation logic appears in multiple services          | Inconsistent distance semantics risk    | Consolidate into shared geo-distance utility   |
| 7d        | Profile scoring methods follow repeated structural template      | Large method bodies and tuning friction | Introduce category-scoring abstraction         |
| 7e        | Dealbreaker evaluator pattern repeated across categories         | Verbose maintenance cost                | Introduce generic evaluator mapping pattern    |
| 7f        | ViewModel logging wrappers repeated                              | Boilerplate, style divergence           | Unify logging helper strategy                  |
| 7g        | JavaFX async/toolkit checks repeated across viewmodels           | Repeated safety boilerplate             | Centralize UI-thread utility path              |
| 7h        | Enum-set serialization/parsing repeated in storage classes       | Format drift risk                       | Shared enum-set codec/helper                   |
| 7i        | SQL column constant sets (`ALL_COLUMNS`-style) duplicated        | Schema-change drift risk                | Per-entity schema constants source-of-truth    |
| 7j        | Null-to-empty normalization repeated in preferences/dealbreakers | Inconsistent defensive behavior         | Single normalization helper method             |

---

### 20.3 High issue #9/#10 full taxonomy (9a–9e, 10a–10h)

#### 20.3.1 Storage complexity sub-issues (9a–9e)

| ID | Source-preserved detail                                                                   | Representative risk                 | Suggested first move                             |
|----|-------------------------------------------------------------------------------------------|-------------------------------------|--------------------------------------------------|
| 9a | `JdbiMatchmakingStorage` mixes multiple concerns (likes/matches/undo/notifications paths) | regression coupling                 | Split by concern boundary                        |
| 9b | SQL binding/delegation boilerplate surface is large                                       | high change friction                | remove repetitive binding helpers                |
| 9c | Mapper logic carries broad domain reconstruction complexity                               | parse inconsistency                 | isolate mapper responsibilities                  |
| 9d | Dual-format parsing debt (legacy + modern formats)                                        | silent parse divergence             | migrate to one canonical format                  |
| 9e | Candidate-query responsibilities blend SQL + geometry/filter logic                        | maintainability + tuning complexity | separate query construction from filtering logic |

#### 20.3.2 Architecture inconsistency sub-issues (10a–10h)

| ID  | Source-preserved detail                                       | Representative risk                   | Suggested first move                                    |
|-----|---------------------------------------------------------------|---------------------------------------|---------------------------------------------------------|
| 10a | Record/class ownership policy not consistently documented     | model inconsistency drift             | codify model-type policy                                |
| 10b | Error handling style differs by layer/use case                | caller confusion                      | layer-level error contract guideline                    |
| 10c | Multiple singleton/global-state patterns                      | test isolation + concurrency concerns | lifecycle-managed dependencies                          |
| 10d | Multiple entrypoint initialization paths                      | bootstrap drift                       | shared bootstrap contract                               |
| 10e | Some UI flows bypass service abstractions                     | business rule bypass risk             | enforce service mediation in UI paths                   |
| 10f | Storage interface scope mixing unrelated concerns             | interface bloat                       | segregate contracts by domain                           |
| 10g | Immutable-style type with mutable builder semantics confusion | API ambiguity                         | simplify/standardize construction pattern               |
| 10h | Navigation service carries many responsibilities              | UI change blast radius                | split router/history/context/animation responsibilities |

---

### 20.4 Additional quantitative detail tables (source snapshots)

#### 20.4.1 Size/complexity snapshot table (as reported across audits)

| Area          | Representative largest artifact (source snapshots)                                 | Why it matters                            |
|---------------|------------------------------------------------------------------------------------|-------------------------------------------|
| Core model    | `User.java` (~807 lines in later snapshot)                                         | aggregate complexity and mutation surface |
| Core service  | `ProfileService` (reported ~800+ lines in one snapshot family)                     | mixed scoring/validation responsibilities |
| CLI handler   | `MatchingHandler` (reported as very large in both chars/lines depending on report) | orchestration concentration               |
| UI layer      | `ProfileController` / `ProfileViewModel` repeatedly flagged as oversized           | UI orchestration complexity               |
| Storage layer | `JdbiMatchmakingStorage` (896 lines in verified pending finding)                   | multi-concern persistence coupling        |

#### 20.4.2 Concern-level metric table (preserved)

| Metric type                  | Source-preserved signal                             |
|------------------------------|-----------------------------------------------------|
| Largest class concern        | high/critical concern repeatedly assigned           |
| Handler dependency breadth   | high dependency count repeatedly flagged            |
| Config construction breadth  | builder/delegate size repeatedly flagged            |
| Singleton/global usage       | repeatedly flagged for testing and concurrency risk |
| Duplicative utility patterns | repeated low-to-medium effort cleanup opportunities |

---

### 20.5 Detailed phased roadmap with effort/value/risk

This section restores effort-aware planning tables that several source reports included explicitly.

#### 20.5.1 Phase 1 — quick wins

| Action family                                                                  | Effort class | Value class | Risk class |
|--------------------------------------------------------------------------------|--------------|-------------|------------|
| Utility extraction (CLI parsing, pagination validation, normalization helpers) | Low          | High        | Low        |
| Constant/threshold centralization cleanups                                     | Low–Medium   | Medium–High | Low        |
| Test parity fixes (`Thread.sleep` reduction, soft-delete parity checks)        | Low–Medium   | High        | Low        |
| Clock-consistency cleanup                                                      | Low          | Medium      | Low        |

#### 20.5.2 Phase 2 — refactor sprint

| Action family                                           | Effort class | Value class | Risk class |
|---------------------------------------------------------|--------------|-------------|------------|
| Handler/service decomposition                           | Medium–High  | Very High   | Medium     |
| `AppConfig` builder simplification                      | Medium–High  | Very High   | Medium     |
| [VERIFIED COMPLETED/OUTDATED] Synchronization reduction | Medium       | High        | Medium     |
| Error/null/result contract standardization              | Medium       | High        | Medium     |

#### 20.5.3 Phase 3 — strategic work

| Action family                                  | Effort class | Value class | Risk class |
|------------------------------------------------|--------------|-------------|------------|
| Cross-storage transaction coordination         | High         | Critical    | High       |
| Auth + verification hardening                  | High         | Critical    | High       |
| Schema migration governance                    | Medium–High  | High        | Medium     |
| Registry decomposition / DI boundary evolution | Medium–High  | High        | Medium     |

**Effort synthesis preserved from source planning:** multiple reports converge on a multi-sprint effort envelope (including one source estimate near ~120 hours for the maintainability-focused plan scope).

---

### 20.6 Cross-cutting concerns (restored)

#### Concern A — schema constant duplication

- Repeated warning: duplicated column bundles/constants in storage classes increase schema drift risk.
- Recommended remedy: single per-entity schema constant sources.

#### Concern B — error-contract inconsistency

- Repeated warning: mixed null/optional/exception/result patterns increase caller complexity.
- Recommended remedy: layer-specific error contract policy.

#### Concern C — hidden singleton/session dependencies

- Repeated warning: singleton lookups in execution paths reduce transparency and testability.
- Recommended remedy: explicit dependency passing or constructor injection boundaries.

---

### 20.7 Detailed contradiction reconciliation (expanded)

The source set includes concrete contradiction classes; this subsection preserves the detailed reconciliation intent.

| Contradiction class        | What conflicted                                     | Reconciled truth                                                             |
|----------------------------|-----------------------------------------------------|------------------------------------------------------------------------------|
| Measurement units          | chars vs lines in some size claims                  | preserve units explicitly and treat older claims as snapshot-bound           |
| Concurrency remedy         | [OUTDATED] “remove sync” vs “RW lock”               | [VERIFIED 2026-03-09] Synchronization already removed; focus on thread model |
| Config simplification      | flatten vs keep grouped/nested config               | preserve grouped model semantics; reduce construction boilerplate            |
| DI strategy                | immediate framework adoption vs staged manual split | prefer staged split first, framework only if complexity remains high         |
| Historical snapshot counts | file/LOC/test counts vary across dates              | preserve historical numbers, anchor current baseline separately              |

---

### 20.8 Methodology and scope notes (restored)

#### 20.8.1 Code-only scope and exclusions

- Several source audits explicitly stated code-first analysis (`src/main/java`, `src/test/java`, `pom.xml`) with differing documentary scope.
- This merged document preserves those findings while normalizing into one canonical narrative.

#### 20.8.2 Duplication-analysis nuance

- One source explicitly recorded a clean PMD-CPD style duplication signal while still reporting structural repetition patterns.
- Reconciliation: “low literal copy-paste” and “high structural repetition” can both be true.

### 20.9 Granular effort estimates (source-preserved detail)

This subsection restores hour-granularity planning from the maintainability-focused source analysis.

| Phase                                 | Representative tasks                                                           | Approximate effort (source-style granularity) |
|---------------------------------------|--------------------------------------------------------------------------------|-----------------------------------------------|
| Phase 1 (quick wins)                  | enum/util cleanup, constants centralization, timer fix, test-storage split     | ~5.5 hours                                    |
| Phase 2 (medium effort / high impact) | logging approach cleanup, recommendation-service split, synchronization review | ~16 hours                                     |
| Phase 3 (major refactors)             | AppConfig redesign, ServiceRegistry decomposition, CLI/UI decomposition        | ~96 hours                                     |
| **Total (A+B+C)**                     | maintainability-focused plan scope                                             | **~120 hours**                                |

> Note: These estimates are preserved as historical planning signals from the source reports, not guaranteed delivery forecasts.

### 20.10 Evidence-anchor addendum (artifact-level)

To strengthen traceability, this addendum preserves high-signal artifact anchors repeatedly cited in source documents.

| Theme                     | Representative artifact anchors (as cited across sources)                       |
|---------------------------|---------------------------------------------------------------------------------|
| REST duplication          | repeated pagination validation blocks in `RestApiServer`                        |
| CLI duplication           | repeated index-parse/list-render patterns across CLI handlers                   |
| User complexity           | oversized `User` aggregate + synchronized accessor/mutator surface              |
| Storage drift risk        | duplicated column-set definitions (`ALL_COLUMNS`-style) in JDBI storage classes |
| Storage complexity        | `JdbiMatchmakingStorage` multi-concern concentration                            |
| UI complexity             | profile-oriented controller/viewmodel bloat hotspots                            |
| Message-state consistency | sender unread-state update path mismatch (`F-042` family)                       |

### 20.11 Representative code-pattern examples (normalized)

The source reports included many detailed code snippets; these normalized examples preserve their intent without duplicating every snippet verbatim.

#### Pattern A — duplicated pagination validation

- **Observed pattern:** repeated endpoint-local pagination guards.
- **Canonical normalization:** one shared validator used by all endpoints.

#### Pattern B — repeated CLI index selection loops

- **Observed pattern:** repeated parse/range-check/retry loops.
- **Canonical normalization:** one reusable one-based index parsing helper.

#### Pattern C — repeated category scoring structures

- **Observed pattern:** similar scoring method shells repeated by category.
- **Canonical normalization:** generic category scorer + field descriptors.

#### Pattern D — repeated enum-set serialization/parsing

- **Observed pattern:** per-storage class duplication.
- **Canonical normalization:** shared enum-set codec utility.

#### Pattern E — oversized orchestration classes

- **Observed pattern:** handlers/controllers/services mixing unrelated concerns.
- **Canonical normalization:** split by feature/flow and keep small adapter boundaries.

### 20.12 Expanded architectural dataflow anchors (restored)

The union already includes compressed flows in §3.1. This subsection restores the additional source-style flow specificity.

| Flow                         | Source-preserved detail anchor                                           | Principal risk preserved                            |
|------------------------------|--------------------------------------------------------------------------|-----------------------------------------------------|
| Like → Match creation        | multi-step orchestration through matching + persistence + side effects   | partial-failure consistency (`U-01`)                |
| Send Message pipeline        | validation → conversation resolution → persistence → state marker update | sender unread-state drift (`F-042`)                 |
| Candidate discovery pipeline | SQL prefilter + in-memory filter/scoring stages                          | duplication/unbounded-list risks (`F-022`, `F-060`) |
| Report → Auto-ban flow       | report ingest + thresholded moderation transition                        | coarse-lock contention concerns (`F-057`)           |
| Undo path                    | bounded undo eligibility + persistence reversal path                     | reversal consistency and expiry-window correctness  |

**Source note:** detailed line-range walkthroughs are preserved in original reports; this section keeps their workflow semantics and risk mappings in canonical form.

### 20.13 Appendix-style audit metadata (restored)

#### 20.13.1 Representative high-signal artifacts reviewed across source reports

| Area                  | Representative artifacts repeatedly analyzed                                                                         |
|-----------------------|----------------------------------------------------------------------------------------------------------------------|
| Core models           | `User.java`, `Match.java`, `ProfileNote.java`                                                                        |
| Core services         | `ProfileService`, `MatchingService`, `RecommendationService`, `ConnectionService`, `MatchQualityService`             |
| CLI adapters          | `MatchingHandler`, `ProfileHandler`, `MessagingHandler`, `SafetyHandler`, `StatsHandler`                             |
| UI adapters           | `NavigationService`, profile-oriented controllers/viewmodels                                                         |
| Storage layer         | `JdbiUserStorage`, `JdbiMatchmakingStorage`, `JdbiConnectionStorage`, `JdbiMetricsStorage`, `JdbiTrustSafetyStorage` |
| Bootstrap/composition | `Main.java`, `ApplicationStartup`, `ServiceRegistry`, `StorageFactory`                                               |

#### 20.13.2 Tooling and analysis methods (as reflected in sources)

| Tool/method family        | Preserved purpose                                              |
|---------------------------|----------------------------------------------------------------|
| `tokei`/snapshot counts   | LOC and file-count baselining                                  |
| Maven quality gates       | test/verify/spotless/checkstyle/pmd/jacoco validation context  |
| PMD CPD duplication check | literal duplication signal assessment                          |
| structural/manual review  | architecture, dependency, and maintainability pattern analysis |

### 20.14 Explicit PMD-CPD finding preservation

One source explicitly records a clean PMD-CPD duplication signal (no significant literal duplication), which complements—not contradicts—the structural repetition findings (e.g., duplicated patterns in CLI/REST/scoring helpers).

Reconciliation preserved:

- **Literal duplication check:** low/clean signal in PMD-CPD context.
- **Structural repetition:** still meaningful and repeatedly observed in workflow/helper patterns.
- **Practical implication:** prioritize low-risk pattern extractions even when copy-paste duplication metrics appear low.

---

## 21) Coverage-closure checklist (delta from prior version)

This checklist records what was explicitly expanded in this revision to address “missing data” concerns.

- ✅ Restored deep-detail narrative for critical clusters (not only inventory IDs)
- ✅ Restored full 7a–7j duplication taxonomy
- ✅ Restored full 9a–9e and 10a–10h taxonomy sections
- ✅ Restored effort/value/risk phase-planning tables
- ✅ Restored additional quantitative context tables
- ✅ Restored cross-cutting concern section
- ✅ Restored expanded contradiction reconciliation
- ✅ Restored methodology/scope nuance and duplication-analysis reconciliation

---

## 22) Source-complete restoration — Combined 5-agent report details

This section restores detailed quantitative and planning content from `Combined_Report_By_5-agents_24.02.2026.md` that was previously compressed.

### 22.1 Detailed metrics tables (5-agent source)

#### 22.1.1 Summary statistics (source snapshot table)

| Category       | Count | Largest File                               |
|----------------|------:|--------------------------------------------|
| Core Models    |     3 | `User.java` (807 lines)                    |
| Core Services  |    15 | `ProfileService.java` (~34K chars)         |
| CLI Handlers   |     7 | `MatchingHandler.java` (~42K chars)        |
| UI Controllers |    12 | `ProfileController.java` (~32K chars)      |
| ViewModels     |    13 | `ProfileViewModel.java` (~29K chars)       |
| Storage JDBI   |     6 | `JdbiMatchmakingStorage.java` (~37K chars) |

#### 22.1.2 Key metrics table (source snapshot)

| Metric                       | Value                             | Concern level |
|------------------------------|-----------------------------------|---------------|
| Largest class                | `User.java` (807 lines)           | 🔴 High       |
| Most dependencies            | `MatchingHandler` (14)            | 🔴 High       |
| Largest builder              | `AppConfig.Builder` (50+ setters) | 🔴 High       |
| Singleton count              | 4                                 | 🟠 Medium     |
| Deprecated methods in `User` | 4                                 | 🟡 Low        |
| Avg ViewModel size           | 600+ lines                        | 🟠 Medium     |

#### 22.1.3 LOC impact estimate table (source snapshot)

| File                       | Current LOC | Estimated after | Reduction |
|----------------------------|------------:|----------------:|----------:|
| `User.java`                |         807 |             400 |       407 |
| `AppConfig.java`           |         671 |             200 |       471 |
| `ProfileService.java`      |         821 |             600 |       221 |
| `MatchPreferences.java`    |         839 |             700 |       139 |
| `MatchQualityService.java` |         734 |             600 |       134 |
| `JdbiUserStorage.java`     |         724 |             500 |       224 |
| **Total**                  |   **4,596** |       **3,000** | **1,596** |

#### 22.1.4 Current-vs-target table (source snapshot)

| Metric                                  | Current                | Target after fixes |
|-----------------------------------------|------------------------|--------------------|
| Largest class (lines)                   | 821 (`ProfileService`) | < 300              |
| Classes > 500 lines                     | 6                      | 0                  |
| Boilerplate getters (`UserSqlBindings`) | 40+                    | 0                  |
| Magic constants per class (avg)         | 30+                    | < 10               |
| Record/class consistency                | Mixed                  | Clear policy       |

### 22.2 Expected benefits and effort profile (source-preserved)

The 5-agent report explicitly estimates the following outcomes after refactoring:

- LOC reduction: **2,000–3,000 lines**
- Complexity reduction: **~60–70%** (qualitative source estimate)
- Faster feature work due to smaller component boundaries
- Lower bug rate from reduced duplication and clearer contracts

Source-estimated delivery envelope:

- Estimated effort: **3–6 months**
- Risk level: **Medium** (mostly mechanical, but broad impact)
- Expected ROI: **High**

### 22.3 Expanded refactor order (full 5-agent sequence)

#### Immediate (high impact, low risk)

1. Remove inline ViewModel logging wrappers.
2. Consolidate UI async execution helper usage.
3. Extract `ProfileService` scoring pattern into generic helper.
4. Centralize duplicated CLI parsing/validation utilities.
5. Centralize REST pagination validation.

#### Short-term (high impact, medium risk)

6. Split `MatchingHandler` into focused handlers.
7. Refactor `AppConfig.Builder`.
8. Move scoring constants to configuration.
9. Reduce `UserSqlBindings` boilerplate.
10. [VERIFIED COMPLETED/OUTDATED] Reduce/remove unnecessary `User` synchronization.
11. Remove remaining deprecated `User` methods.

#### Medium-term (architectural, higher risk)

12. Split `User` into focused value-object composition.
13. Decompose `JdbiMatchmakingStorage` concerns.
14. Normalize legacy dual-format persisted data to single format.
15. Replace/split `ServiceRegistry` with domain-focused registries or explicit DI.
16. Split oversized UI controllers and viewmodels.
17. Re-evaluate nested enum/type placement where used cross-package.

#### Long-term (foundational)

18. Formalize class-vs-record architecture policy.
19. Reduce singleton/global mutable access paths.
20. Centralize schema/column constants strategy.
21. Continue test infrastructure hardening (shared fixtures, no sleep-based async tests).

### 22.4 5-agent contradictions appendix (fully restored)

| Contradiction                               | Source conflict                                                  | Resolution preserved in this union                                                            |
|---------------------------------------------|------------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| File-size unit mismatch                     | Some entries used line labels where values were character counts | Treat those values as char-count snapshots; keep line counts when explicitly reported as such |
| [OUTDATED] Synchronization strategy options | remove sync vs RW-lock/atomic alternatives                       | [VERIFIED] Sync removed from User as of 2026-03-09 review                                     |
| AppConfig simplification approach           | flattening vs grouped sub-record model                           | Preserve grouped semantics; reduce builder/delegation complexity                              |
| DI strategy depth                           | split registries manually vs framework adoption                  | Prefer staged manual split first; framework only if complexity remains high                   |

---

## 23) Source-complete restoration — Combined 8-agent roadmap and contradiction tables

This section restores detailed plan matrices and conflict-resolution items from `Combined_Report_By_8-agents_21.02.2026.md`.

### 23.1 Phase 1 quick wins table (8-agent source)

| # | Action                                                         | Value  | Effort | Risk | Anchor |
|---|----------------------------------------------------------------|--------|--------|------|--------|
| 1 | Add coordinate validation in `User.setLocation()`              | High   | Low    | Low  | F-009  |
| 2 | Guard negative/future age calculations                         | High   | Low    | Low  | F-031  |
| 3 | Add indexes for `deleted_at` query paths                       | High   | Low    | Low  | F-048  |
| 4 | Use existing config field in `User.getAge()` path              | High   | Low    | Low  | F-016  |
| 5 | Remove explicit hardcoded clock wiring in storage factory path | High   | Low    | Low  | F-062  |
| 6 | Extract deterministic pair-ID generator                        | Medium | Low    | Low  | F-018  |
| 7 | Document/align `maxDistanceKm` default discrepancy             | Low    | Low    | Low  | F-043  |
| 8 | Fix soft-delete parity in in-memory test storage               | Medium | Low    | Low  | F-063  |
| 9 | Fix sender unread indicator update after send                  | Medium | Low    | Low  | F-042  |

### 23.2 Phase 2 refactor table (8-agent source)

| #  | Action                                               | Value    | Effort | Risk   | Anchor |
|----|------------------------------------------------------|----------|--------|--------|--------|
| 1  | Make singleton/session patterns thread-safe          | Critical | Medium | Medium | F-045  |
| 2  | [NOT VALID] Reduce excessive `User` synchronization  | High     | Medium | Medium | F-046  |
| 3  | Standardize error handling contract                  | High     | Medium | Medium | F-036  |
| 4  | Replace null returns with `Optional`-style semantics | High     | Medium | Medium | F-049  |
| 5  | Fix daily-pick cache race behavior                   | High     | Medium | Low    | F-033  |
| 6  | Reduce AppConfig delegate boilerplate                | Medium   | Medium | Low    | F-017  |
| 7  | Replace static defaults coupling in domain paths     | High     | Medium | Low    | F-016  |
| 8  | Move scoring constants to configuration model        | Medium   | Medium | Low    | F-020  |
| 9  | Add verification-code throttling/rate limit          | High     | Medium | Low    | F-004  |
| 10 | Improve lock granularity in auto-ban path            | Medium   | Low    | Low    | F-057  |

### 23.3 Phase 3 strategic table (8-agent source)

| # | Action                                                    | Value    | Effort | Risk   | Anchor |
|---|-----------------------------------------------------------|----------|--------|--------|--------|
| 1 | Implement cross-storage transaction coordinator pattern   | Critical | High   | High   | F-001  |
| 2 | Add baseline authN/authZ layer                            | Critical | High   | High   | F-002  |
| 3 | Introduce versioned migration framework                   | High     | High   | Medium | F-010  |
| 4 | Integrate real verification channel providers (email/SMS) | High     | High   | Low    | F-003  |
| 5 | Add robust caching for hot candidate/recommendation paths | High     | Medium | Low    | F-006  |
| 6 | Add pagination/streaming to large list paths              | High     | Medium | Low    | F-060  |
| 7 | Implement proactive notification delivery path            | Medium   | High   | Medium | F-007  |
| 8 | Split `ServiceRegistry` into bounded registries           | Medium   | Medium | Medium | F-030  |

### 23.4 8-agent contradiction/audit-resolution table (fully restored)

| # | Conflict                                                                                  | Resolution preserved                                                                                       |
|---|-------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| 1 | Reported claims about missing UI/navigation artifacts by one source                       | Treat as source-specific hallucination; keep consensus of remaining sources and code-verified architecture |
| 2 | Reference to non-existent daily-limit file path in one source                             | Re-map concern to actual recommendation/matching limit flow                                                |
| 3 | Suggestion to manually override record equality/hash behavior                             | Suppressed; default record semantics are correct unless intentionally deviating                            |
| 4 | API security stance differences (intentional unauth local API vs security hardening need) | Not contradictory: both can hold; preserve threat-model-dependent recommendations                          |
| 5 | Included “Qwen” artifact that was template-like rather than findings                      | Treat as non-finding source artifact; keep only actionable finding sources                                 |

### 23.5 8-agent source-state note (restored)

- The 8-agent report itself notes that one included artifact was a prompt/template rather than findings.
- Effective findings were synthesized from the remaining substantive source analyses.

---

## 24) Source-complete restoration — architecture/analysis appendices

### 24.1 Architecture Review Appendix A (files reviewed snapshot table)

| Category                  | Scope (source snapshot)                    |
|---------------------------|--------------------------------------------|
| Domain models             | `User`, `Match`, `ProfileNote` families    |
| Services                  | core service layer set                     |
| Storage interfaces/impls  | interface contracts + JDBI implementations |
| UI controllers/viewmodels | screen + viewmodel families                |
| Configuration/bootstrap   | `AppConfig`, startup/factory wiring        |
| REST/API and schema       | route handlers + schema/migration files    |
| Tests                     | broad test class inventory                 |

### 24.2 Architecture Review Appendix B (tools/methods snapshot)

| Tool/method         | Purpose in source review    |
|---------------------|-----------------------------|
| `ast-grep`          | structural code search      |
| `ripgrep`           | targeted text search        |
| `tokei`             | LOC/file baseline snapshot  |
| Maven config review | build/quality-gate behavior |

### 24.3 AI-agent documentation gap expansion (architecture report)

The architecture report explicitly called out these missing high-value artifacts:

1. service dependency graph (for wiring visibility),
2. user/match state-machine visualizations,
3. schema relationship/ER diagrams,
4. API request/response contract documentation,
5. worked examples for complex scoring logic.

### 24.4 Codebase analysis nuance restoration

From `Codebase_Analysis_Report_2026-02-23.md`, the following detailed nuances are explicitly preserved:

- Strong PMD-CPD literal-duplication signal can coexist with high structural repetition concerns.
- Candidate filtering logic should be modularized via strategy-style filters (`AgeFilter`, `GenderFilter`, `DealbreakerFilter`) to improve readability and isolation.
- Complex inline SQL annotation blocks should be externalized where query complexity is high for maintainability and IDE support.

---

## 25) Source-complete restoration — maintainability and maintenance-analysis detail

### 25.1 21-finding maintainability matrix (failure mode + first step)

This restores the practical “how it fails” and “first move” detail from `CODEBASE_MAINTAINABILITY_FINDINGS_2026-02-22.md`.

| #  | Finding theme                           | Representative failure mode                        | Practical first step                                      |
|----|-----------------------------------------|----------------------------------------------------|-----------------------------------------------------------|
| 1  | Schema evolution split                  | Fresh-install and upgrade paths drift              | Move new DDL into ordered migrations                      |
| 2  | NavigationService concern-mixing        | Transition changes can destabilize history/context | Extract history/context collaborators                     |
| 3  | CLI menu duplication                    | Display, guard, and dispatch can desync            | Introduce single menu option registry                     |
| 4  | Oversized `MatchingHandler`             | Flow edits can affect unrelated branches           | Extract one vertical flow first                           |
| 5  | Oversized `JdbiMatchmakingStorage`      | Transaction/mapping changes collide                | Move DAO interfaces out first                             |
| 6  | Test wiring duplication                 | Constructor changes cause broad test churn         | Create shared test service assembly helper                |
| 7  | Test filtering duplication              | Runtime/test behavior drift                        | Share common filter helper                                |
| 8  | Dual navigation registries              | New screen wired in one place only                 | Introduce single view-definition table                    |
| 9  | CLI index parsing duplication           | Inconsistent invalid-input handling                | Add shared one-based index parser                         |
| 10 | CLI list/select loop duplication        | UX divergence across handlers                      | Add reusable list-selection helper                        |
| 11 | Ambiguous DI semantics/default literals | Role confusion and config drift                    | Extract defaults/constants and role-specific abstractions |
| 12 | AppConfig delegation/builder bloat      | Every config change has multi-point edits          | Migrate call sites to nested access style                 |
| 13 | `UserStorage` mixed concerns            | CRUD consumers depend on note APIs                 | Introduce `ProfileNoteStorage` interface                  |
| 14 | Distance utility duplication            | Scoring and filtering can diverge                  | Create shared geo-distance utility                        |
| 15 | In-memory pagination defaults           | Hidden O(n) memory behavior                        | Deprecate default pagination fallbacks                    |
| 16 | Legacy+JSON mapper complexity           | Silent parse inconsistency risks                   | Extract tested parser/codec components                    |
| 17 | REST pagination duplication             | Endpoint behavior drifts                           | Centralize query-param validator                          |
| 18 | Reused `SwipeResult` naming             | Semantic confusion/import mixups                   | Rename one variant for clarity                            |
| 19 | Controller orchestration bloat          | UI interaction changes cause broad coupling        | Move one interaction set into VM commands                 |
| 20 | `Thread.sleep` async tests              | CI flakiness                                       | Introduce deterministic FX await helper                   |
| 21 | Global session reset coupling           | Test-order dependent failures                      | Add test-scoped session harness                           |

### 25.2 Maintenance-analysis issue impact restoration (12-issue set)

This subsection restores issue-level qualitative impact framing from `CODEBASE_MAINTENANCE_ANALYSIS_2026-02-23.md`.

| Issue family                         | Current impact                 | Target after refactor             |
|--------------------------------------|--------------------------------|-----------------------------------|
| AppConfig builder bloat              | high edit friction             | lower change surface              |
| ServiceRegistry god object           | hidden dependencies            | explicit dependencies             |
| Monolithic CLI handlers              | high cognitive load            | focused flow classes              |
| TestStorages monolith                | poor locality/compile coupling | split focused test utilities      |
| EnumSetUtil redundancy               | unclear API                    | one canonical copy helper         |
| LoggingSupport anti-pattern          | inconsistent logging approach  | single clear logging strategy     |
| UI controller complexity             | mixed concerns in controllers  | cleaner UI composition boundaries |
| RecommendationService multi-role     | difficult to isolate logic     | bounded recommendation components |
| User synchronization overhead        | [OUTDATED] lock-heavy access   | [VERIFIED] sync already removed   |
| Inlined constants                    | tuning friction                | centralized tuning points         |
| ViewModelFactory mapping boilerplate | manual registration churn      | streamlined wiring/registry       |
| PerformanceMonitor timing basis      | coarse measurement path        | explicit measurement intent       |

### 25.3 Maintenance-analysis effort table (restored)

| Phase     | Representative actions                                                       | Source effort estimate |
|-----------|------------------------------------------------------------------------------|------------------------|
| Phase 1   | EnumSetUtil cleanup, constants centralization, timer fix, TestStorages split | ~5.5h                  |
| Phase 2   | Logging approach replacement, recommendation split, sync review              | ~16h                   |
| Phase 3   | AppConfig redesign, ServiceRegistry refactor, CLI/UI decomposition           | ~96h                   |
| **Total** | Combined roadmap envelope                                                    | **~120h**              |

---

## 26) Delta checklist for this pass

This pass specifically added underrepresented source detail that was previously compressed:

- ✅ Restored 5-agent detailed metrics/LOC/current-vs-target tables
- ✅ Restored 5-agent explicit expected-benefit/effort profile
- ✅ Restored 5-agent contradiction appendix details
- ✅ Restored full 8-agent Phase 1/2/3 action matrices
- ✅ Restored 8-agent contradiction/audit-resolution table
- ✅ Restored architecture appendices and documentation-gap expansion
- ✅ Restored maintainability finding-level failure-mode/first-step matrix
- ✅ Restored maintenance-analysis issue-impact and effort detail

---

## 27) Restored architecture blueprint details (from Architecture Review)

This section restores implementation-blueprint level detail that was present in `ARCHITECTURE_REVIEW_REPORT.md`.

### 27.1 Clean architecture pattern example (restored)

```java
// Domain layer - interface contract
public interface UserStorage {
   void save(User user);
   Optional<User> get(UUID id);
}

// Infrastructure layer - implementation detail
public final class JdbiUserStorage implements UserStorage {
   private final Jdbi jdbi;
   private final Dao dao;
}
```

### 27.2 Result-pattern service example (restored)

```java
public static record SendResult(
   boolean success,
   Message message,
   String errorMessage,
   ErrorCode errorCode
) {
   public static SendResult success(Message m) { ... }
   public static SendResult failure(String err, ErrorCode code) { ... }
}
```

### 27.3 `User` split blueprint (restored)

```java
public class User {
   private final UserProfile profile;
   private final UserLocation location;
   private final UserPreferences preferences;
   private final UserLifestyle lifestyle;
   private final UserVerification verification;
   private UserState state;
}
```

This mirrors the architecture report recommendation to split the aggregate into focused value-object components.

### 27.4 `RestApiServer` extraction blueprint (restored)

```java
public final class UserRoutes {
   public void register() {
      app.get("/api/users", this::listUsers);
      app.get("/api/users/{id}", this::getUser);
      app.get("/api/users/{id}/candidates", this::getCandidates);
   }
}
```

### 27.5 AppConfig grouping blueprint (restored)

```java
public record AlgorithmConfig(
   DistanceThresholds distance,
   AgeThresholds age,
   ResponseTimeThresholds response,
   StandoutWeights standout,
   int paceCompatibilityThreshold
) {}
```

### 27.6 Match-quality simplification blueprint (restored)

```java
double weightedScore =
   distanceScore * 0.30 +
   interestScore * 0.40 +
   lifestyleScore * 0.30;
```

### 27.7 Lock-striping simplification blueprint (restored)

```java
private final Map<UUID, Object> userLocks = new ConcurrentHashMap<>();

Object lock = userLocks.computeIfAbsent(userId, k -> new Object());
synchronized (lock) {
   // critical section
}
```

---

## 28) Restored maintainability implementation blueprints (from Maintenance Analysis)

### 28.1 Explicit-DI blueprint

```java
// Instead of passing ServiceRegistry everywhere:
public MatchingHandler(ServiceRegistry services) { ... }

// Prefer explicit dependencies:
public MatchingHandler(MatchingService matchingService, UserStorage userStorage) {
   this.matchingService = matchingService;
   this.userStorage = userStorage;
}
```

### 28.2 Monolith decomposition blueprint

```java
public class CandidateBrowser { ... }
public class MatchDisplayer { ... }
public class SwipeProcessor { ... }
public class MatchingHandler { /* orchestration only */ }
```

### 28.3 Test utility split blueprint

```text
src/test/java/datingapp/core/testutil/
  TestUserStorage.java
  TestInteractionStorage.java
  TestCommunicationStorage.java
  TestAnalyticsStorage.java
  TestTrustSafetyStorage.java
```

### 28.4 EnumSet utility consolidation blueprint

```java
public static <E extends Enum<E>> EnumSet<E> safeCopy(Collection<E> source, Class<E> enumClass) {
   Objects.requireNonNull(enumClass, "enumClass cannot be null");
   if (source == null || source.isEmpty()) {
      return EnumSet.noneOf(enumClass);
   }
   return source instanceof EnumSet
      ? EnumSet.copyOf((EnumSet<E>) source)
      : EnumSet.copyOf(source);
}
```

### 28.5 LoggingSupport replacement options (restored)

- Option A: thin static utility class for logging guards.
- Option B: annotation-based logger field generation (where project policy allows).

### 28.6 Timer precision blueprint (`PerformanceMonitor`)

```java
public static Timer startTimer(String operationName) {
   return new Timer(operationName, System.nanoTime());
}

@Override
public void close() {
   long durationNanos = System.nanoTime() - startNanos;
   record(operationName, TimeUnit.NANOSECONDS.toMillis(durationNanos));
}
```

---

## 29) Restored high/critical evidence anchors (from 8-agent report)

To reduce evidence-loss from inventory compression, this table restores direct high-signal anchors for high/critical findings.

| Finding | Source anchor (file/section)                      | Evidence summary                                                      |
|---------|---------------------------------------------------|-----------------------------------------------------------------------|
| F-001   | `ConnectionService` relationship-transition paths | Non-atomic multi-storage writes with compensating rollback limitation |
| F-002   | `Main` selection flow + `RestApiServer` auth note | Identity selection without auth guard + unauthenticated API surface   |
| F-003   | `User` verification note/method usage             | Verification flow simulated; no external provider delivery            |
| F-004   | `TrustSafetyService` code generation path         | Verification code generation without robust throttling policy         |
| F-010   | `SchemaInitializer` + `MigrationRunner`           | Versioned migration discipline gap                                    |
| F-011   | REST route handlers                               | No endpoint rate-limiting middleware                                  |
| F-030   | `ServiceRegistry` construction surface            | High dependency concentration                                         |
| F-031   | `User.getAge` behavior                            | Future-date/ambiguous age handling concerns                           |
| F-032   | `User.StorageBuilder`                             | Direct field reconstitution bypassing normal setter guardrails        |
| F-033   | `RecommendationService` daily-pick cache flow     | cache validation race window                                          |
| F-036   | service-layer error handling patterns             | mixed result/exception/null styles                                    |
| F-045   | `AppSession`/navigation singleton patterns        | unsafe singleton/global mutable-state risks                           |
| F-046   | [NOT VALID] `User` synchronization surface        | synchronization removed as of 2026-03-09 review                       |
| F-047   | JDBI column constants + mapping                   | schema/column drift exposure                                          |
| F-048   | schema indexing profile                           | `deleted_at` filter/index mismatch concern                            |
| F-049   | null vs optional contracts                        | inconsistent storage/service nullability semantics                    |
| F-051   | resource lifecycle hotspots                       | potential listener/resource cleanup leaks                             |
| F-062   | storage factory clock wiring                      | explicit clock path inconsistency with deterministic-clock strategy   |

---

## 30) Restored source conclusion narratives

### 30.1 Architecture review conclusion restoration

The architecture review explicitly concludes that the codebase has strong foundations but recommends targeted simplification/refactoring to reduce complexity hotspots and improve AI/developer navigation speed.

### 30.2 Maintenance-analysis conclusion restoration

The maintenance analysis concludes that incremental cleanup of large classes, duplicated patterns, and wiring complexity yields substantial maintainability gain, with an estimated roadmap envelope around **120 hours**.

### 30.3 Cross-report effort synthesis (restored)

- Maintainability roadmap estimate: ~120 hours (issue-phased breakdown preserved in §25.3).
- 5-agent strategic envelope: roughly multi-month phased delivery (3–6 month estimate in source planning language).
- Architecture review tactical envelope: medium-size focused refactor budget (source estimate range preserved as historical guidance).

### 30.4 Developer/agent impact synthesis (source-preserved intent)

Repeated expected outcomes across sources:

- lower cognitive load due to smaller focused classes,
- fewer regressions due to reduced duplication and clearer contracts,
- improved test determinism and reduced flaky async behavior,
- faster onboarding and better AI-agent navigation through explicit architecture artifacts.

---

## 31) Architecture critical walkthrough restoration (code-level)

### 31.1 Non-atomic cross-storage operation walkthrough

Restored from architecture review critical section:

```java
public TransitionResult acceptFriendZoneTransition(String matchId) {
   Match match = interactionStorage.getMatch(matchId).orElseThrow(...);

   // write 1
   match.transitionToFriends();
   interactionStorage.saveMatch(match);

   // write 2 (not atomic with write 1)
   FriendRequest request = FriendRequest.create(...);
   communicationStorage.saveFriendRequest(request);

   return TransitionResult.success(...);
}
```

Primary risk preserved: state can partially transition when downstream write fails.

### 31.2 Match quality complexity walkthrough

Restored representative 6-factor weighted scoring pattern:

```java
double weightedScore =
   distanceScore * config.distanceWeight() +
   ageScore * config.ageWeight() +
   interestScore * config.interestWeight() +
   lifestyleScore * config.lifestyleWeight() +
   paceScore * config.paceWeight() +
   responseScore * config.responseWeight();
```

This is preserved to explain why sources categorized current scoring as high-complexity.

### 31.3 NavigationService concern-mix walkthrough

Restored concern breakdown from architecture review:

- history stack mutation,
- controller lifecycle hooks,
- FXML loading,
- transition animation logic,
- context payload passing.

This clarifies why multiple source reports classify navigation flow as multi-responsibility.

### 31.4 Schema complexity walkthrough

The architecture review explicitly frames user schema complexity as a large denormalized row surface (identity/profile/preferences/location/lifestyle/dealbreakers/verification/pace/timestamps), tied to migration/version drift risk.

### 31.5 Architecture simplification opportunity table (restored)

| Area                  | Current source snapshot                           | Simplification direction              | Effort | Impact |
|-----------------------|---------------------------------------------------|---------------------------------------|--------|--------|
| `User.java`           | large multi-concern aggregate                     | split into value-object composition   | Medium | High   |
| `RestApiServer`       | route handlers in one class                       | extract route-focused classes         | Low    | High   |
| Match-quality scoring | 6-factor weighted pipeline                        | reduce/clarify factors                | Medium | Medium |
| `AppConfig`           | broad parameter surface                           | grouped thresholds and cleaner access | Low    | Medium |
| lock striping         | [OUTDATED] manual lock-stripe complexity          | [VERIFIED] removed/simplified         | Low    | Low    |
| `NavigationService`   | mixed transition/history/context/loading concerns | split concern collaborators           | Medium | Medium |

---

## 32) Maintenance-analysis per-issue prioritization table (fully restored)

Restored from `CODEBASE_MAINTENANCE_ANALYSIS_2026-02-23.md` summary table:

| #  | Issue                                     | Severity | Effort | Impact |
|----|-------------------------------------------|----------|--------|--------|
| 1  | AppConfig Builder Bloat                   | High     | Medium | High   |
| 2  | ServiceRegistry God Object                | High     | High   | High   |
| 3  | Monolithic CLI Handlers                   | High     | High   | High   |
| 4  | TestStorages Monolith                     | Medium   | Low    | Medium |
| 5  | EnumSetUtil Redundancy                    | Low      | Low    | Low    |
| 6  | LoggingSupport Anti-Pattern               | Medium   | Medium | Medium |
| 7  | UI Controller Complexity                  | High     | High   | High   |
| 8  | RecommendationService SRP                 | High     | Medium | High   |
| 9  | [NOT VALID] User Synchronization Overhead | Medium   | Medium | Medium |
| 10 | Inlined Constants                         | Medium   | Low    | Medium |
| 11 | ViewModelFactory Mapping                  | Low      | Medium | Low    |
| 12 | PerformanceMonitor Timer                  | Low      | Low    | Low    |

---

## 33) Maintainability findings evidence-anchor matrix (line-level)

This matrix restores representative file:line anchors from `CODEBASE_MAINTAINABILITY_FINDINGS_2026-02-22.md` for rapid navigation.

| Finding # | Representative evidence anchors                                                                                                  |
|-----------|----------------------------------------------------------------------------------------------------------------------------------|
| 1         | `SchemaInitializer.java:36`, `MigrationRunner.java:59/136/191`                                                                   |
| 2         | `NavigationService.java:66/170/240/359/374`                                                                                      |
| 3         | `Main.java:90/93/102/148`                                                                                                        |
| 4         | `MatchingHandler.java:125/147/806`                                                                                               |
| 5         | `JdbiMatchmakingStorage.java:36/156/591/621/727`                                                                                 |
| 6         | `LikerBrowserHandlerTest.java:28/38/52`, `StorageFactory.java:39`                                                                |
| 7         | `TestStorages.java:91`, `CandidateFinder.java:122/179`                                                                           |
| 8         | `NavigationService.java:66`, `ViewModelFactory.java:103`                                                                         |
| 9         | `ProfileHandler.java:411/938`, `SafetyHandler.java:146`, `MatchingHandler.java:327`, `MessagingHandler.java:145`                 |
| 10        | `ProfileHandler.java:463/807`, `SafetyHandler.java:247`, `MatchingHandler.java:840`                                              |
| 11        | `MatchingHandler.java:54/62/125`, `ProfileHandler.java:463/485`                                                                  |
| 12        | `AppConfig.java:197/447/490/553/833`                                                                                             |
| 13        | `UserStorage.java:148/156/165/173/182`                                                                                           |
| 14        | `UserStorage.java:90`, `CandidateFinder.java:73/92`, `MatchQualityService.java:366`                                              |
| 15        | `InteractionStorage.java:172/182/186/214/219`                                                                                    |
| 16        | `JdbiUserStorage.java:275/342/351/405/452`                                                                                       |
| 17        | `RestApiServer.java:177/180/228/231/245/248`                                                                                     |
| 18        | `MatchingService.java:265`, `ActivityMetricsService.java:268`                                                                    |
| 19        | `ProfileController.java:63/78/189/390/663/808`, `ProfileViewModel.java:133`                                                      |
| 20        | `SocialViewModelTest.java:98`, `ChatViewModelTest.java:89`, `DashboardViewModelTest.java:124`, `StandoutsViewModelTest.java:113` |
| 21        | `AppSession.java:13/70`, `SocialViewModelTest.java:80`, `ChatViewModelTest.java:99`                                              |

---

## 34) Codebase analysis tactical restoration

Restored tactical details from `Codebase_Analysis_Report_2026-02-23.md` that are frequently used during implementation planning:

### 34.1 Snapshot hotspot specifics (report-time values)

- `ProfileController.java` reported at ~881 lines (report snapshot)
- `ProfileViewModel.java` reported at ~854 lines (report snapshot)
- `AppConfig.java` reported around ~900 lines (report snapshot)
- `StorageFactory.java` reported around ~137 lines with broad wiring role

### 34.2 Tactical recommendations restored

1. Extract JavaFX profile UI into reusable composite components.
2. Reduce manual UI/domain mapping boilerplate in profile viewmodel flows.
3. Externalize complex JDBI SQL annotation literals to dedicated SQL resources for readability/tooling support.
4. Guard expensive debug/trace logging in tight loops.
5. Keep mutable-entity boundary explicit between UI and domain flows.

---

## 35) Full 5-agent issue-group deep-dive restoration (#1–#22)

This appendix restores implementation-level context from `Combined_Report_By_5-agents_24.02.2026.md` for every issue group, so the union is not limited to title-only coverage.

| Issue                                     | Detailed context restored                                                                                                                                     | Representative failure mode                                                   | First practical refactor move                                                 |
|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| #1 `User` Entity God Object               | `User` combines identity/profile/preferences/location/lifestyle/verification/state and many mutation paths, increasing cognitive load and change blast radius | edits in one concern can regress unrelated concerns                           | split into focused value-object components while keeping aggregate invariants |
| #2 Excessive Synchronization              | [NOT VALID] broad synchronized access patterns on `User` removed.                                                                                             | lock contention risk is outdated                                              | [VERIFIED] unnecessary synchronization removed as of 2026-03-09 review.       |
| #3 `ServiceRegistry` God Object           | broad constructor dependency surface and centralized service lookup hide coupling and complicate test setup                                                   | constructor/ripple breakage on dependency changes                             | split registry by bounded domain (matching/profile/messaging/etc.)            |
| #4 Massive Service Classes                | services like profile/recommendation/match quality accumulate multiple responsibilities                                                                       | business-rule edits trigger regressions in scoring/validation unrelated flows | extract focused scoring/validation/planning components                        |
| #5 CLI Handler God Classes                | handlers mix parsing, orchestration, rendering, and navigation                                                                                                | simple UX changes require risky edits across large methods                    | extract parsing/selection/render helpers and flow-specific handler slices     |
| #6 AppConfig Builder Complexity           | large builder/delegate/defaults surface increases edit burden and construction risk                                                                           | config changes require touching many builder/delegate pathways                | preserve grouped config model, reduce builder/delegate noise                  |
| #7a REST pagination duplication           | repeated endpoint-local validation rules                                                                                                                      | endpoints drift in limit/offset behavior                                      | one shared pagination validator utility                                       |
| #7b CLI index parsing duplication         | repeated parse/range-check/retry loops                                                                                                                        | inconsistent invalid-input behavior                                           | one shared one-based index parser                                             |
| #7c Distance calculation duplication      | distance/geo logic repeated across components                                                                                                                 | recommendation/filter inconsistency                                           | single shared geo-distance helper                                             |
| #7d Profile scoring duplication           | near-identical scoring method shells by category                                                                                                              | scoring edits duplicated across methods                                       | generic category scoring abstraction                                          |
| #7e Dealbreaker evaluator duplication     | repeated evaluator logic by category                                                                                                                          | rule drift and duplicated tests                                               | evaluator strategy map with shared rule engine                                |
| #7f ViewModel logging duplication         | repeated wrapper/helper logging paths                                                                                                                         | style and guard inconsistency                                                 | unify logging helper path and conventions                                     |
| #7g Async/toolkit checks duplication      | repeated JavaFX thread-safety checks                                                                                                                          | inconsistent UI-thread guards                                                 | central async/UI-thread helper usage                                          |
| #7h Enum-set parsing duplication          | repeated enum-set conversion/parsing in storages                                                                                                              | parse/format drift                                                            | shared enum-set codec/helper                                                  |
| #7i Column constant duplication           | repeated `ALL_COLUMNS`-style bundles across storages                                                                                                          | schema-change drift                                                           | single schema constant source per entity                                      |
| #7j Null normalization duplication        | repeated null-to-empty defensive normalization                                                                                                                | inconsistent null behavior across flows                                       | shared normalization helpers                                                  |
| #8 UI Layer Bloat                         | controllers/viewmodels are oversized and mix state/orchestration/presentation concerns                                                                        | UI feature changes become high-risk and hard to test                          | extract reusable UI components and command-style VM actions                   |
| #9 Storage Layer Complexity               | storage classes mix query logic, mapping, business assumptions, and compatibility code                                                                        | persistence edits ripple across unrelated concerns                            | split mapper/query/DAO concerns first                                         |
| #9a Multi-concern storage classes         | one class owns many persistence workflows                                                                                                                     | hard to isolate regressions                                                   | split by concern boundary                                                     |
| #9b Binding/boilerplate density           | repetitive binding and conversion code                                                                                                                        | high maintenance friction                                                     | centralize shared binding helpers                                             |
| #9c Mapper reconstruction complexity      | mappers contain broad domain rehydration logic                                                                                                                | inconsistent object reconstruction                                            | isolate codecs/parsers with focused tests                                     |
| #9d Legacy+new format coexistence         | dual-format parsing retained                                                                                                                                  | silent data inconsistency                                                     | normalize to one canonical stored format                                      |
| #9e Query/filter mixing                   | SQL retrieval and business filtering coupled                                                                                                                  | tuning difficulty and unclear ownership                                       | separate query construction from filtering/scoring                            |
| #10 Architectural inconsistencies         | inconsistent class/record policy, error/null handling, singleton usage, and entrypoint wiring styles                                                          | uneven conventions slow review and refactoring                                | define and enforce architecture conventions                                   |
| #10a Type ownership policy drift          | mixed type placement conventions                                                                                                                              | import/API confusion                                                          | codify class-vs-record ownership rules                                        |
| #10b Error handling contract drift        | mixed result/exception/null patterns                                                                                                                          | caller complexity and bugs                                                    | layer-level error contract standardization                                    |
| #10c Singleton/global-state reliance      | hidden runtime coupling                                                                                                                                       | test flakiness and concurrency risk                                           | explicit dependency/lifecycle control                                         |
| #10d Entrypoint init divergence           | different initialization paths                                                                                                                                | environment-specific drift                                                    | shared bootstrap contract                                                     |
| #10e UI bypass of service boundaries      | business logic spread into adapters                                                                                                                           | rule bypass risk                                                              | enforce service/use-case mediation                                            |
| #10f Storage interface scope mixing       | broad interfaces with unrelated operations                                                                                                                    | low cohesion and churn                                                        | segregate interfaces by responsibility                                        |
| #10g Mutable/immutable semantic ambiguity | builder + mutable semantics confusion                                                                                                                         | API misuse                                                                    | simplify construction/update semantics                                        |
| #10h Navigation concern overreach         | router/history/context/animation merged                                                                                                                       | high UI change blast radius                                                   | split navigation collaborators                                                |
| #11 Testing Infrastructure Issues         | fixture duplication, monolithic test storage utility, and async sleep patterns                                                                                | flaky CI and expensive test updates                                           | shared test builders + deterministic async wait helpers                       |
| #12 Magic numbers/constants sprawl        | hardcoded thresholds and weights spread across classes                                                                                                        | inconsistent tuning and hidden behavior changes                               | centralize tunables in config/constants modules                               |
| #13 Complex conditional service logic     | deeply nested branch logic in service flows                                                                                                                   | low readability and regression risk                                           | extract branch predicates into named helpers                                  |
| #14 Nested type overuse                   | deeply nested records/enums reduce discoverability                                                                                                            | import friction and unclear ownership                                         | move reusable nested types to top-level where appropriate                     |
| #15 Deprecated/dead code in `User`        | old compatibility paths remain in active model                                                                                                                | accidental usage and confusion                                                | remove or isolate deprecation paths with migration notes                      |
| #16 Configuration coupling                | direct/default config coupling in runtime paths                                                                                                               | brittle testability and hidden defaults                                       | inject config where needed and reduce static default use                      |
| #17 Naming inconsistencies                | equivalent concepts named differently across layers                                                                                                           | cognitive overhead and mistake risk                                           | naming normalization pass across key APIs                                     |
| #18 Null-safety inconsistency             | mixed null/optional usage                                                                                                                                     | defensive coding overhead and bugs                                            | enforce nullability policy + return-contract consistency                      |
| #19 Logging inconsistency                 | varied log patterns and levels                                                                                                                                | uneven diagnostics and noise                                                  | standard logging policy and wrappers                                          |
| #20 UI magic constants                    | scattered spacing/sizing constants in UI layer                                                                                                                | visual inconsistency and retuning effort                                      | centralize UI spacing/size constants                                          |
| #21 Complex nested records                | deeply nested result/DTO shapes hinder readability                                                                                                            | awkward navigation and API clutter                                            | flatten reusable result types                                                 |
| #22 StorageBuilder complexity             | broad builder paths with compatibility concerns                                                                                                               | hard to reason about invariants                                               | narrow builder scope and isolate compatibility adapters                       |

### 35.1 5-agent deep-dive restoration checklist

- ✅ Restored detailed narrative context for #1–#6
- ✅ Restored full 7a–7j duplication taxonomy
- ✅ Restored full 9a–9e storage taxonomy
- ✅ Restored full 10a–10h architecture inconsistency taxonomy
- ✅ Restored detailed context for #11–#22 (testing, constants, naming, null safety, logging, UI constants, nested types, builder complexity)

---

## 36) Final precision addendum (line-anchored and implementation-atomic)

### 36.1 SQL externalization guidance (restored with concrete pattern)

From `Codebase_Analysis_Report_2026-02-23.md` detailed recommendations: complex JDBI inline SQL should be externalized to dedicated `.sql` resources for readability, IDE SQL assistance, and reduced annotation-string drift.

Representative pattern:

```java
// DAO method stays concise
@SqlQuery
@UseClasspathSqlLocator
List<MatchRow> getPageOfActiveMatchesFor(@Bind("userId") UUID userId,
                               @Bind("limit") int limit,
                               @Bind("offset") int offset);
```

### 36.2 Logging performance guard pattern (restored)

From codebase analysis guidance on tight-loop logging:

- prefer parameterized logging placeholders,
- guard expensive debug formatting with `logger.isDebugEnabled()`,
- avoid eager string interpolation in hot paths.

Representative pattern:

```java
if (logger.isDebugEnabled()) {
   logger.debug("Candidate {} distance={} score={}", candidateId, distanceKm, score);
}
```

### 36.3 Atomic first-step checklists (fully explicit)

#### Phase A atomic starts

1. Extract `parseOneBasedIndex(...)` utility and replace duplicated CLI parse loops.
2. Extract one `validatePagination(limit, offset)` helper and replace endpoint-local checks.
3. Replace one flaky UI test (`Thread.sleep`) with deterministic async await helper; then propagate pattern.
4. Align one in-memory test-storage soft-delete filter path with production semantics.
5. Patch sender unread-state update in message-send pipeline and add regression test.

#### Phase B atomic starts

1. Split `MatchingHandler` by one vertical flow first (e.g., candidate browse flow).
2. Move one scoring category in `ProfileService` to generic scoring helper.
3. Replace one null-return storage path with explicit `Optional` contract and update callers.
4. Remove one AppConfig delegate cluster by switching to grouped nested accessor usage.

#### Phase C atomic starts

1. Inventory all multi-storage write paths and classify by transactional vs compensating strategy.
2. Introduce one auth gate boundary at API adapter edge before business orchestration.
3. Add schema version table and one ordered migration baseline.
4. Split one domain slice out of central registry as proof-of-pattern.

### 36.4 Dataflow trace anchors (source-snapshot line precision)

The 8-agent report provided line-anchored flow tracing. Preserved representative anchors (as source-snapshot references):

| Flow                                        | Source-snapshot anchor                                                                |
|---------------------------------------------|---------------------------------------------------------------------------------------|
| Like → Match transition                     | `MatchingService.processSwipe()` (source-reported range around lines 124–147)         |
| Swipe path helper logic                     | `MatchingService` helper branch (source-reported mid-file branch around 240+)         |
| Message send pipeline                       | `ConnectionService.sendMessage(...)` (source-reported mid-class flow range)           |
| Daily pick cache flow                       | `RecommendationService` daily-pick/caching path (source-reported race window section) |
| Transition to friends + request persistence | `ConnectionService` friend-zone transition path (non-atomic write sequence)           |

> Note: these line anchors are preserved as report-snapshot references; source files may shift as code evolves.

### 36.5 Architecture strengths walkthrough expansion

To preserve depth from the architecture review's strengths section, each strength is expanded below:

1. **Layering quality** — domain/storage separation exists with interface contracts that keep persistence concerns out of `core`.
2. **Result modeling discipline** — result records in service flows encode success/failure in structured contracts.
3. **Testing strategy** — broad suite + in-memory test doubles + deterministic clock guidance.
4. **Configuration model** — grouped config records provide semantic organization despite builder complexity.
5. **Deterministic identity conventions** — sorted pair-id style avoids asymmetric aggregate identity.
6. **Documentation posture** — extensive report corpus enables architecture traceability and agent guidance.



