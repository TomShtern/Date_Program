Let me first read the full document to understand its contents before recommending a split strategy.

Read [](file:///c%3A/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/CODEBASE_REVIEW.md#1-1), lines 1 to 100

Read [](file:///c%3A/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/CODEBASE_REVIEW.md#101-101), lines 101 to 200

Read [](file:///c%3A/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/CODEBASE_REVIEW.md#201-201), lines 201 to 300

Read [](file:///c%3A/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/CODEBASE_REVIEW.md#301-301), lines 301 to 400

Read [](file:///c%3A/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/CODEBASE_REVIEW.md#401-401), lines 401 to 500

Read [](file:///c%3A/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/CODEBASE_REVIEW.md#501-501), lines 501 to 592

Now let me also check what existing implementation plans already exist to avoid overlap:

Searched for files matching `**/*implementation-plan*`, 20 matches

Searched for files matching `**/*plan*.md`, 20 matches

Now I have the full picture. Let me also quickly check one existing plan to understand the naming/format convention:

Read [](file:///c%3A/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/docs/plans/implemented/completed-plans/2026-04-06-postgresql-verification-and-rest-contract-consolidation-implementation-plan.md#1-1), lines 1 to 40

Good. I understand both the review document and the plan conventions. Here's my analysis:

---

## Recommended Split for Implementation Plans

The CODEBASE_REVIEW.md contains **18 categories with ~65 issues**. Trying to execute that in one monolith plan would be unwieldy and lose focus. Here's how I'd split it, grouped by **shared subsystems and dependency chains** — each plan should be independently executable without blocking the others:

---

### Plan 1: `chat-viewmodel-async-contracts`
**Priority: HIGH** — Direct user-facing correctness risk.

| Issues                                                             | Theme             |
|--------------------------------------------------------------------|-------------------|
| 5.3 `ChatViewModel` exposes mutable lists                          | Boundary safety   |
| 10.2 `MatchesViewModel` probes JavaFX runtime                      | Testability       |
| 10.4 `ChatViewModel.ensureCurrentUser()` caches session            | Session lifecycle |
| 10.5 `ChatViewModel.setCurrentUser(null)` doesn't reset UI         | Reset behavior    |
| 17.2 `ChatViewModel.reportSendFailure()` lacks visible error state | Error UX          |
| 17.5 Profile-note save token race                                  | Race condition    |
| 17.6 `sendMessage()` boolean misleading                            | Contract clarity  |
| 17.8 `loadMessagesInBackground()` no failure UI                    | Background errors |
| 18.6 Polling path duplicate UI/diff work                           | Performance       |

**Rationale:** All these issues live in `ChatViewModel` + `MatchesViewModel` and form one coherent "tighten chat async" narrative. Fixing them together avoids repeated context-switching into the same two files.

---

### Plan 2: `matching-swipe-concurrency-and-scoring`
**Priority: HIGH** — Core business logic correctness.

| Issues                                                              | Theme                  |
|---------------------------------------------------------------------|------------------------|
| 2.1 Scoring logic duplicated across matching features               | Scoring unification    |
| 2.2 Swipe eligibility validated through too many layers             | Control flow           |
| 11.4 `recordLike` / `processSwipe` don't share concurrency contract | Concurrency            |
| 17.7 Swipe side effects inconsistent across layers                  | Side-effect sequencing |
| 18.7 `processSwipe()` holds lock too long                           | Performance            |
| 12.1 `MatchingUseCases` contradictory no-op fallback                | API clarity            |
| 11.1 Builder hidden side effects                                    | API clarity            |
| 13.5 `getDailyStatus()` bypasses builder seam                       | Configuration          |

**Rationale:** These all sit in `MatchingService` / `MatchingUseCases` / `MatchQualityService` / `DefaultBrowseRankingService` / `DefaultStandoutService`. Untangling concurrency + side effects + scoring in one focused pass is safer than fragmenting it across plans.

---

### Plan 3: `user-model-copy-and-mutation-contracts`
**Priority: MEDIUM-HIGH** — Domain integrity.

| Issues                                                       | Theme                 |
|--------------------------------------------------------------|-----------------------|
| 7.1 `User.java` too many roles                               | Cohesion              |
| 7.3 `User.StorageBuilder` omits `dealbreakers`               | Builder gap           |
| 11.2 `ProfileDraftAssembler` manual field copy               | Drift risk            |
| 11.5 `User.copy()` can lose location state                   | Copy semantics        |
| 13.3 `User` mutator contracts looser than profile-edit flows | Invariant enforcement |
| 17.9 `User.markVerified()` drops verification timeline       | Timeline loss         |

**Rationale:** All集中在 `User.java` + `ProfileDraftAssembler`. These are closely coupled domain-model issues that should be fixed together to avoid introducing new copy/mutation drift while fixing one and not the others.

---

### Plan 4: `rest-api-and-storage-contracts`
**Priority: MEDIUM** — Server-side API cleanliness.

| Issues                                              | Theme                |
|-----------------------------------------------------|----------------------|
| 3.2 UUID parsing duplicated in REST boundary        | Duplication          |
| 3.3 Multiple `ObjectMapper` instances               | Duplication          |
| 4.1 `RestApiServer` owns too many concerns          | SRP                  |
| 8.3 `Optional` as control-flow signaling            | Contract clarity     |
| 9.2 `ConnectionService` maps denial through strings | Type safety          |
| 13.1 `SocialUseCases` too many constructor modes    | API clarity          |
| 13.4 `archiveMatch()` name/behavior mismatch        | Semantic correctness |
| 14.1 Dialect detection repeated across JDBI storage | Duplication          |
| 15.1 `RestApiDtos` too broad                        | Discoverability      |
| 18.2 `saveStandouts()` repeated writes              | Performance          |

**Rationale:** These cluster around the REST adapter layer and storage internals — they share similar test surfaces (`RestApiTestFixture`, JDBI tests) and can be verified independently.

---

### Plan 5: `database-manager-and-migration-safety`
**Priority: MEDIUM** — Runtime safety.

| Issues                                                  | Theme            |
|---------------------------------------------------------|------------------|
| 2.3 Password resolution too indirect                    | Config clarity   |
| 11.6 `resetInstance()` resets more than name promises   | Lifecycle        |
| 11.7 V3 schema cleanup irreversible                     | Migration safety |
| 13.6 `configurePoolSettings()` looks dynamic but isn't  | API clarity      |
| 14.2 `DatabaseManager` mixes static-global and instance | Lifecycle        |
| 16.4 `DatabaseManager` Javadoc reads as H2-only         | Documentation    |
| 16.5 Match/conversation ID length magic number          | Maintainability  |
| 17.10 Startup migration timeout/atomicity weaker        | Correctness      |

**Rationale:** All集中在 `DatabaseManager` + `MigrationRunner` + `SchemaInitializer`. These are interconnected lifecycle and schema issues that should be addressed as one unit.

---

### Plan 6: `viewmodel-and-ui-layer-consistency`
**Priority: MEDIUM** — UI quality and testability.

| Issues                                                   | Theme         |
|----------------------------------------------------------|---------------|
| 5.1 `SafetyViewModel` verification fallback              | Boundary      |
| 5.2 `MatchingViewModel` depends on GeoUtils              | Coupling      |
| 4.3 `TrustSafetyService` spans too many concerns         | SRP           |
| 6.1 `NavigationService` singleton baked into controllers | Coupling      |
| 6.2 `ServiceRegistry.Builder` oversized setter surface   | Complexity    |
| 8.1 ViewModel construction inconsistent                  | Style         |
| 8.2 Inline JavaFX styles vs CSS                          | Style         |
| 10.1 `BaseController.cleanup()` assumes global state     | Testability   |
| 9.1 `ActivityMetricsService` confusing constructor split | API clarity   |
| 16.1 `ActivityMetricsService` misleading Javadoc         | Documentation |
| 14.3 `GeoUtils` nested unnecessarily                     | Organization  |
| 18.3 Hover animation allocations                         | Performance   |
| 18.4 `DefaultCompatibilityCalculator` repeated lookups   | Performance   |
| 18.5 `JdbiTypeCodecs` allocates calendar per row         | Performance   |

**Rationale:** This is a broader "UI + shared infra polish" sweep. It's intentionally the widest plan — these are lower-severity improvements that share overlapping test and build contexts.

---

### Plan 7: `cli-layer-consistency`
**Priority: LOW** — CLI UX, standalone from everything else.

| Issues                                                 | Theme       |
|--------------------------------------------------------|-------------|
| 1.2 CLI feedback formatting inconsistent               | UX          |
| 3.1 Index parsing repeated across CLI handlers         | Duplication |
| 12.3 `copyForProfileEditing()` unnecessary indirection | Dead code   |

**Rationale:** Tiny and self-contained. Could even be combined with Plan 6 if preferred, but it's clean as a standalone "CLI polish" pass.

---

### Not worth a separate plan
- **1.1** (Large files/defer) — explicitly marked "ignore for now"
- **6.3** (Broad core imports) — styling/preference, not action
- **7.2** (`RecommendationService`) — repo memory confirms this is already a coordination facade with intentional compatibility records
- **12.2** (`CandidateFinder` cache no-ops) — repo memory confirms intentional freshness-first design
- **17.1** (Event publication best-effort) — documented repo convention, not a bug
- **17.4** (RATE_LIMITED future mapping) — explicitly called out as future-only gap
- **18.1** (`findCandidates()` default) — explicitly a future adapter guardrail

---

### Suggested execution order

```
Plan 1 (chat-viewmodel) ─────┐
Plan 2 (matching-swipe)  ─────┼── HIGH priority, can run in parallel
Plan 3 (user-model)      ─────┤
                              │
Plan 4 (rest-api-storage) ────┼── MEDIUM priority, after high plans
Plan 5 (database-migration) ──┤
                              │
Plan 6 (vm-ui-polish)     ────┼── LOWER priority, polish pass
Plan 7 (cli)              ────┘
```

Plans 1, 2, and 3 can genuinely run in parallel since they touch different subsystems. Plans 4 and 5 should follow because they touch shared infra that the high-priority plans might reshape. Plans 6 and 7 arePOLISH (polish) and should come last.

Want me to draft any of these into full implementation plans in plans?