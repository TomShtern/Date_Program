---
title: Current Issues Register (Final Organized)
source_of_truth: local code only (src/main/java, src/test/java, pom.xml)
total_claims: 162
valid_claims: 97
invalid_or_not_applicable_claims: 65
note: valid_claims include valid + partially-valid
---

# Current Issues Register (Human + AI Optimized)

This document is split for fast triage and deep implementation work:
- **Valid claims**: detailed, implementation-ready context.
- **Invalid / not-applicable claims**: brief claim + exact reason only.

## Quick Skim

- Total claims: **162**
- Valid claims (valid + partially-valid): **97**
- Invalid / not-applicable claims (not-valid + cannot-verify): **65**
- Duplicate heading pass: **no duplicates detected**

### Legend

- In valid claims, `Status note` captures conflicting historical report labels and **does not override** the current `Validation status`.
- 🟠 `IN PROGRESS - NOT STARTED YET (a plan exists)` means the claim is covered by an active implementation plan, but work has not started yet.

### How to use this register

- For fast triage, scan the index first and jump by claim ID or severity.
- For deeper review, open the relevant claim section and expand the collapsed evidence snippets only when needed.
- Treat `Validation status` as the current verdict and `Status note` as historical context.
- The document is ordered for skimming first, then drilling into details without losing the thread.

## Valid Claims Index (Renumbered)

| New ID | Legacy ID | Severity    | Category        | Mentions | Claim                                                                                 |
|--------|----------:|-------------|-----------------|---------:|---------------------------------------------------------------------------------------|
| V001   |         1 | critical    | bug             |       50 | Match entity lacks updatedAt field and mutation timestamping                          |
| V002   |         3 | critical    | security        |       11 | REST mutation endpoints allow acting-user bypass when header missing                  |
| V003   |         4 | critical    | bug             |       10 | User.markDeleted does not update updatedAt metadata                                   |
| V004   |         6 | critical    | ui-ux           |        6 | User.setLocation accepts invalid/unsafe coordinate values                             |
| V005   |         7 | critical    | bug             |        5 | Dealbreakers mapper does not merge all normalized sources                             |
| V006   |         8 | critical    | bug             |        5 | TrustSafetyService auto-ban synchronization is single-instance only                   |
| V007   |        10 | critical    | build           |        1 | Foreign key constraints reliability risk                                              |
| V008   |        11 | high        | ui-ux           |       26 | RelationshipWorkflowPolicy transition coverage concern (marked invalid/intentional)   |
| V009   |        12 | high        | architecture    |        7 | REST routes bypass use-case layer via deliberate exceptions                           |
| V010   |        13 | high        | performance     |        6 | JdbiUserStorage exhibits N+1 profile loading pattern                                  |
| V011   |        15 | high        | performance     |        6 | ProfileService creates legacy DefaultAchievementService per call                      |
| V012   |        16 | high        | maintainability |        4 | AppConfig.Builder deletion refactor not completed                                     |
| V013   |        17 | high        | architecture    |        4 | REST API implementation completeness is unclear/placeholder                           |
| V014   |        21 | high        | security        |        4 | Verification workflows are simulated, not fully integrated                            |
| V015   |        23 | high        | architecture    |        3 | Conversation deletion uses hard delete instead of soft delete                         |
| V016   |        24 | high        | testing         |        3 | Critical utility classes lack direct tests                                            |
| V017   |        25 | high        | maintainability |        3 | Large multi-responsibility units increase change blast radius                         |
| V018   |        26 | high        | architecture    |        3 | Presence feature flag lacks documentation                                             |
| V019   |        27 | high        | bug             |        3 | Profile save and post-save side effects are not transactional                         |
| V020   |        30 | high        | security        |        2 | Authentication beyond user selection not implemented                                  |
| V021   |        32 | high        | ui-ux           |        2 | JavaFX social/friend-request/notifications parity gap                                 |
| V022   |        33 | high        | ui-ux           |        2 | Message sanitizer strips all HTML formatting (possible over-sanitization)             |
| V023   |        35 | high        | architecture    |        2 | Partially constructible services with runtime null-mode switching                     |
| V024   |        36 | high        | maintainability |        2 | ProfileController god-controller bloat                                                |
| V025   |        37 | high        | security        |        2 | REST API lacks authentication strategy/documented production posture                  |
| V026   |        39 | high        | architecture    |        2 | Storage defaults mask incomplete atomic/cleanup behavior                              |
| V027   |        42 | high        | testing         |        1 | Critical components have no direct tests                                              |
| V028   |        43 | high        | ui-ux           |        1 | Dashboard achievement popup load failure is silent to end user                        |
| V029   |        45 | high        | bug             |        1 | Email validation regex excludes internationalized domains                             |
| V030   |        46 | high        | bug             |        1 | Jdbi conversation mapper used stale archive column names                              |
| V031   |        47 | high        | testing         |        1 | Key concurrency/error-path test categories are missing                                |
| V032   |        48 | high        | bug             |        1 | Photo count constraints are not enforced at model layer                               |
| V033   |        49 | high        | security        |        1 | Session management needs production-grade implementation                              |
| V034   |        53 | medium      | security        |        4 | Moderation audit logging is not structured/compliance-grade                           |
| V035   |        54 | medium      | ui-ux           |        4 | Multi-photo UI/gallery parity gap                                                     |
| V036   |        57 | medium      | ui-ux           |        3 | JavaFX standouts parity gap                                                           |
| V037   |        58 | medium      | maintainability |        3 | Profile note deletion is hard-delete and inconsistent                                 |
| V038   |        59 | medium      | ui-ux           |        2 | Accessibility support is limited                                                      |
| V039   |        60 | medium      | bug             |        2 | Age calculation timezone source concern                                               |
| V040   |        61 | medium      | docs            |        2 | Architecture/package documentation divergence (missing packages in architecture docs) |
| V041   |        62 | medium      | maintainability |        2 | Business limits/rules are hardcoded instead of config-driven                          |
| V042   |        63 | medium      | maintainability |        2 | CLI exception paths silently swallow errors                                           |
| V043   |        64 | medium      | maintainability |        2 | CLI login-gate checks are duplicated/inconsistent                                     |
| V044   |        65 | medium      | bug             |        2 | CLI profile inputs lack complete validation                                           |
| V045   |        66 | medium      | bug             |        2 | Config validator does not enforce monotonic threshold ordering                        |
| V046   |        67 | medium      | architecture    |        2 | Configuration sourcing inconsistency and stale defaults guidance                      |
| V047   |        68 | medium      | reliability     |        2 | Connection pool lacks validation query configuration                                  |
| V048   |        71 | medium      | architecture    |        2 | Event system may require expansion                                                    |
| V049   |        72 | medium      | ui-ux           |        2 | JavaFX profile-notes parity gap                                                       |
| V050   |        75 | medium      | ui-ux           |        2 | No real-time chat push/websocket updates                                              |
| V051   |        80 | medium      | ui-ux           |        2 | Super Like UI action is placeholder behavior                                          |
| V052   |        81 | medium      | testing         |        2 | UI controller test coverage still partial                                             |
| V053   |        82 | medium      | bug             |        2 | UndoService time-window and error-handling robustness concerns                        |
| V054   |        83 | medium      | maintainability |        2 | Use-case classes have many backward-compat constructor overloads                      |
| V055   |        85 | medium      | maintainability |        1 | Achievement thresholds duplicated/inconsistent across modules                         |
| V056   |        86 | medium      | docs            |        1 | Architecture documentation references non-existent code paths                         |
| V057   |        87 | medium      | ui-ux           |        1 | Chat message length indicator styling can race with text clear                        |
| V058   |        88 | medium      | ui-ux           |        1 | Chat message-list listener lifecycle may cause update-time instability                |
| V059   |        89 | medium      | testing         |        1 | ChatViewModel test race and nondeterministic ordering                                 |
| V060   |        90 | medium      | bug             |        1 | CleanupScheduler visibility/race risk in running-state reads                          |
| V061   |        91 | medium      | ui-ux           |        1 | CLI input validation is inconsistent for profile and messaging flows                  |
| V062   |        92 | medium      | bug             |        1 | CLI mutates user state without immediate persistence guarantees                       |
| V063   |        95 | medium      | bug             |        1 | Daily limit reset uses start-of-day logic with DST ambiguity                          |
| V064   |       100 | medium      | performance     |        1 | ImageCache preload strategy can exhaust threads under load                            |
| V065   |       105 | medium      | performance     |        1 | Missing standalone index on messages(conversation_id)                                 |
| V066   |       107 | medium      | ui-ux           |        1 | Navigation context can be dropped silently                                            |
| V067   |       109 | medium      | performance     |        1 | Performance monitoring/metrics observability is insufficient                          |
| V068   |       110 | medium      | bug             |        1 | Phone normalization routine validates but does not normalize                          |
| V069   |       111 | medium      | security        |        1 | Photo URL ingestion lacks validation                                                  |
| V070   |       113 | medium      | maintainability |        1 | Profile completion thresholds are hard-coded magic numbers                            |
| V071   |       115 | medium      | maintainability |        1 | ProfileActivationPolicy duplicates completeness logic                                 |
| V072   |       116 | medium      | build           |        1 | Purge cleanup methods default to no-op return values                                  |
| V073   |       117 | medium      | architecture    |        1 | RecommendationService mostly wraps delegated methods                                  |
| V074   |       120 | medium      | ui-ux           |        1 | Report dialog does not propagate description text                                     |
| V075   |       121 | medium      | maintainability |        1 | Social event publishing uses magic state strings                                      |
| V076   |       124 | medium      | bug             |        1 | Soft-delete propagation not cascaded to dependent entities                            |
| V077   |       126 | medium      | ui-ux           |        1 | Standouts selection triggers immediate navigation                                     |
| V078   |       128 | medium      | architecture    |        1 | Storage interface defaults violate efficient contract expectations                    |
| V079   |       133 | medium      | performance     |        1 | UserStorage.findByIds default implementation is inefficient                           |
| V080   |       135 | low         | maintainability |        2 | Archived Java utility code remains in docs folder                                     |
| V081   |       136 | low         | maintainability |        2 | CLI case normalization is inconsistent                                                |
| V082   |       137 | low         | maintainability |        2 | Constraint naming convention is inconsistent                                          |
| V083   |       138 | low         | maintainability |        2 | Event double-publishing concern downgraded (cross-method duplication concern remains) |
| V084   |       140 | low         | maintainability |        2 | profile_views has unused AUTO_INCREMENT surrogate column                              |
| V085   |       141 | low         | performance     |        2 | Storage layer lacks shared query-result caching                                       |
| V086   |       142 | low         | maintainability |        1 | BaseViewModel nullability pattern is inconsistent for error routing                   |
| V087   |       143 | low         | ui-ux           |        1 | CLI messaging command parser has limited command/help ergonomics                      |
| V088   |       144 | low         | maintainability |        1 | Compatibility activity thresholds duplicated across services                          |
| V089   |       147 | low         | bug             |        1 | Email regex is narrower than full RFC-valid address space                             |
| V090   |       148 | low         | maintainability |        1 | Event bus strict handling mode appears unused                                         |
| V091   |       151 | low         | maintainability |        1 | Profile completion scoring logic still partially split                                |
| V092   |       155 | low         | maintainability |        1 | Shared interests preview count is hard-coded                                          |
| V093   |       157 | low         | ui-ux           |        1 | Standouts screen navigation target likely incorrect                                   |
| V094   |       158 | low         | build           |        1 | Transaction timeout configurability is missing                                        |
| V095   |       159 | low         | bug             |        1 | User.isInterestedInEveryone relies on fragile full-set assumption                     |
| V096   |       162 | unspecified | other           |        1 | ML-powered recommendation capability is not yet present                               |
| V097   |       163 | unspecified | ui-ux           |        1 | Mobile application capability is a stated future gap                                  |

## Valid Claim Details (Renumbered IDs)

### V001. Match entity lacks updatedAt field and mutation timestamping
- **Legacy ID:** 1
- **Validation status:** valid
- **Severity / Category:** critical / bug
- **Mentions (total):** 50
- **Cross-report conflict:** yes
- **Status note:** INVALID
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                          | Value                                                                                                                                                             |
|-----------------------|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`             | `24`                                                                                                                                                              |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md`  | `9`                                                                                                                                                               |
| mention_count_by_file | `STATUS_2026-03-17_By_claude_sonnet_4.6.md`                  | `9`                                                                                                                                                               |
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `7`                                                                                                                                                               |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md`     | `1`                                                                                                                                                               |
| section_reference     | `#1`                                                         | 2.1 Key Risks; 2.2/7 Addendum FI-AUD-001                                                                                                                          |
| section_reference     | `#2`                                                         | 2.2 FI-AUD-011; 2.5 Document Drift                                                                                                                                |
| section_reference     | `#3`                                                         | 2.2 Issue Register; 6 Addendum Workspace Audit Fixes                                                                                                              |
| section_reference     | `#4`                                                         | Executive Summary (new issues added)                                                                                                                              |
| section_reference     | `#5`                                                         | Executive Summary; PART 2 > 2.2                                                                                                                                   |
| section_reference     | `#6`                                                         | Feature / UI Status > Genuinely Open Items                                                                                                                        |
| section_reference     | `#7`                                                         | PART 1: CRITICAL ISSUES > 1.4 Match Entity Missing updatedAt Field                                                                                                |
| section_reference     | `#8`                                                         | PART 2 > 2.22; APPENDIX B > 15                                                                                                                                    |
| section_reference     | `#9`                                                         | PART 2 > 2.7                                                                                                                                                      |
| section_reference     | `#10`                                                        | PART 3 > 3.18                                                                                                                                                     |
| section_reference     | `#11`                                                        | PART 3 > 3.38                                                                                                                                                     |
| section_reference     | `#12`                                                        | PART 3 > 3.7                                                                                                                                                      |
| section_reference     | `#13`                                                        | PART 4 > 4.10                                                                                                                                                     |
| section_reference     | `#14`                                                        | PART 4 > 4.11                                                                                                                                                     |
| section_reference     | `#15`                                                        | PART 4 > 4.14                                                                                                                                                     |
| section_reference     | `#16`                                                        | PART 4 > 4.16                                                                                                                                                     |
| section_reference     | `#17`                                                        | PART 4 > 4.5                                                                                                                                                      |
| section_reference     | `#18`                                                        | PART 6: RECOMMENDATIONS BY PRIORITY; Verification Summary                                                                                                         |
| section_reference     | `#19`                                                        | Part 1: CRITICAL Issues > 1.1 Schema Inconsistency - Missing daily_picks Table in V1; Priority Recommendations > Must Fix Before Production                       |
| section_reference     | `#20`                                                        | Part 1: CRITICAL Issues > 1.2 Entity touch() Calls Missing on State Transitions; Priority Recommendations > Must Fix Before Production                            |
| section_reference     | `#21`                                                        | Part 2: HIGH Priority Issues > 2.2 Optional Dependencies Not Null-Checked                                                                                         |
| section_reference     | `#22`                                                        | Part 2: HIGH Priority Issues > 2.4 Match.block() Missing Validation [INVALID]; Part 3: 3.14 RelationshipWorkflowPolicy Block Not in Allowed Transitions [INVALID] |
| section_reference     | `#23`                                                        | Part 3: MEDIUM Priority Issues > 3.16 Memory Leak in Card Cache                                                                                                   |
| section_reference     | `#24`                                                        | Part 6: Inconsistencies Summary > Documentation vs Code                                                                                                           |
| section_reference     | `#25`                                                        | Recommendations > Long-term Vision                                                                                                                                |
| section_reference     | `#26`                                                        | Refactoring Plan Status (Task 2 note) + Code Quality Issues #1                                                                                                    |
| section_reference     | `#27`                                                        | Refactoring Plan Status (Task 7) + Deep-Dive                                                                                                                      |

#### Details and Context
- Match has lifecycle state changes but no `updatedAt` equivalent to User.
- Reduces observability/auditability of match state transitions.
- Nullable optional collaborators alter side-effects (metrics/events/etc.) based on wiring state.
- Behavior can vary silently between environments.
- Method validates user membership but not full transition preconditions.
- Loads all matches then slices (`subList`) rather than pushing pagination to storage.
- Risks memory pressure for heavy users.
- Not all preference combinations are explicitly covered.
- Query patterns involving user/state/deleted_at are under-indexed.
- Individual animation handles are not all tracked on navigation changes.
- Highlights are produced in static order regardless of salience.
- Multiple operations are listed as missing from use-case layer.
- Explicit missing method list captured in source: `MatchingUseCases.getMatchById()`, `MatchingUseCases.getMatchesByState()`, `MatchingUseCases.searchCandidates()`, `MessagingUseCases.searchMessages()`, `MessagingUseCases.exportConversation()`, `MessagingUseCases.markMultipleRead()`, `ProfileUseCases.getProfileById()`, `ProfileUseCases.updatePhotoUrls()`, `ProfileUseCases.verifyProfile()`.
- Marked future-proofing gap with no current impact.
- Constructor guards exist but increment path may not enforce strong bounds.
- Issue was reviewed and noted as handled with `<= 0` guard.
- Unsafe Optional access can trigger runtime failure paths.
- Reported mutable field design may not be safe under concurrent usage.
- Recommendation flags blocking/synchronous fallback in async UI context.
- Report states V1 schema does not create `daily_picks`, but V2 migration does.
- Migration comment claims V1 already includes the table, described as incorrect/misleading.
- Fresh installs still work due to V2 execution order, so concern is correctness of migration documentation/intent signaling.
- Report says `Match` has no `updatedAt` despite multiple mutating transition methods.
- Cited impact includes incomplete audit trail and potentially incorrect modification reporting.
- Optional dependency fields are declared without explicit null checks.
- Report calls out possible NPEs when service graph wiring omits components.
- Report initially raises concern that block transitions bypass standard invalid-transition checks.
- Same report explicitly marks concern INVALID after verification, stating behavior is intentional for safety.
- Cache is only cleared in cleanup; controller reuse without cleanup could accumulate nodes.
- `User.isComplete()` reportedly checks PacePreferences not reflected in documentation.
- Listed as long-term recommendation, indicating current algorithm sophistication is below desired target.
- Initial register reports match quality weights not summing to 1.0.
- Later addendum marks this invalid and provides exact sum evidence.
- Blocking originally did not transition match/messaging visibility state.
- Addendum explains later fix through TrustSafetyService + conversation archive/visibility updates.
- Report calls out mismatch between docs and actual DB password handling.
- It additionally documents environment-variable/default behavior differences across prod/local/mem modes.
- Task 7 is marked not done; Dependencies record still contains raw service/storage fields.
- Direct raw-service calls are documented (analyticsStorage, userStorage, undo pre-checks).
- Use-case layer still lacks some wrapper methods needed to eliminate raw calls.
- Plan said to remove convenience dealbreaker methods, but seven remain.
- Loop-based implementation is present; leftovers are described as noise/plan mismatch.
- Document calls out cross-service non-transactionality (storage + event bus) as design characteristic.
- Storage-level transaction usage exists, but end-to-end atomicity is not global.

#### Recommended Actions
- Add `updatedAt` field and update it in mutating methods.
- Make dependencies explicit and non-null where behavior is required.
- Use capability flags/results rather than null-driven silent degradation.
- Enforce state machine transition guards for block operation.
- Override with SQL-backed `LIMIT/OFFSET` pagination in concrete storage.
- Define exhaustive compatibility matrix and tests.
- Add composite indexes aligned with active-match predicates.
- Track and cancel all active particle animations on teardown.
- Prioritize highlights by relevance/score.
- Add missing use-case methods for parity and boundary consistency, including explicit method-level parity for `getMatchById`, `getMatchesByState`, `searchCandidates`, `searchMessages`, `exportConversation`, `markMultipleRead`, `getProfileById`, `updatePhotoUrls`, and `verifyProfile`.
- Synchronize note state updates on photo navigation.
- Strengthen invariants in mutating counter operations.
- Keep regression test to preserve guard behavior.
- Replace `get()` usage with guarded/functional Optional handling.
- Harden field immutability/publication and review synchronization policy.
- Remove synchronous fallback path.
- Fix missing `daily_picks` table in `SchemaInitializer` (as listed in must-fix recommendations).
- Correct misleading migration comment to match actual schema behavior.
- Add `updatedAt` field to `Match` and update it in all mutators.
- Add null-safety checks/defaulting for optional service dependencies in constructor/wiring.
- No change recommended by report after verification; preserve intentional safety-first blocking behavior.
- Enforce cleanup on reuse or bound cache size/eviction strategy.
- Document pace preferences as part of completeness definition.
- Develop advanced matching algorithms.
- Add MatchingUseCases.recordProfileView(userId, candidateId).
- Expose user lookup by ID or enrich use-case results for CLI paths.
- Expose daily/undo status query methods to remove direct pre-check service calls.
- Remove raw service fields from MatchingHandler.Dependencies once use-cases cover those needs.
- Remove 7 leftover convenience dealbreaker methods.

<details><summary>Evidence snippets</summary>

- `"// ❌ NO updatedAt field"`
- `"if (activityMetricsService != null ...)"`
- `"Can block even if not active (defensive)"`
- `"List<Match> all = getAllMatchesFor(userId);"`
- `"doesn't handle all combinations explicitly"`
- `"No composite index for `(user_a, state, deleted_at)`"`
- `"Memory leak if navigating away mid-animation"`
- `"always in same order"`
- `"Incomplete Use-Case Coverage"`
- `"Future-proofing gap; no current impact"`
- `"incrementMatchCount() check is weak"`
- `"Actually handled with `<= 0` check"`
- `"Optional.get() without isPresent() check in `MatchingUseCases`"`
- `"Thread safety in `MatchingService` (non-final mutable fields)"`
- `"Remove synchronous fallback in MatchesViewModel"`
- `"SchemaInitializer (V1 baseline) does NOT create the `daily_picks` table"`
- `""no-op on fresh databases where V1 already includes it" - this comment is INCORRECT/MISLEADING"`
- `"Match entity has NO `updatedAt` field despite multiple state-changing methods"`
- `"Impact: Audit trail gaps, incorrect "last modified" reporting"`
- `"Optional dependencies ... are declared without null checks, leading to potential NPEs"`
- `"Match.block() Missing Validation [INVALID] ❌"`
- `"Triple-verification confirms this is intentional defensive programming"`
- `"Card caches are only cleared in `cleanup()`"`
- `"PacePreferences not mentioned in documentation but checked in code"`
- `"Long-term Vision: Advanced matching algorithms"`
- `"Match quality weights don't sum to 1.0"`
- `"weights literally sum to 1.0 exactly"`
- `"Blocking a user does not transition matches or messaging state"`
- `"Block does not update match state or conversation visibility"`
- `"Configuration documentation mismatch for DB password"`
- `"requires the DATING_APP_DB_PASSWORD environment variable"`
- `"MatchingHandler raw service cleanup — Not done"`
- `"5 raw service fields still in Dependencies record"`
- `"To fully complete Task 7, MatchingUseCases needs..."`
- `"convenience predicate methods still exist"`
- `"plan said remove them but they remain"`
- `"Like → Match atomicity"`
- `"cross-service (storage + event bus) is by design not transactional"`
</details>

### V002. REST mutation endpoints allow acting-user bypass when header missing
- **Legacy ID:** 3
- **Validation status:** partially-valid
- **Severity / Category:** critical / security
- **Mentions (total):** 11
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                                                                 |
|-----------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`            | `5`                                                                                                                   |
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`             | `2`                                                                                                                   |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                                   |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md`    | `2`                                                                                                                   |
| section_reference     | `#1`                                                        | 13. Security Considerations > Security Gaps; Recommendations > Medium-term Improvements                               |
| section_reference     | `#2`                                                        | 13. Security Gaps                                                                                                     |
| section_reference     | `#3`                                                        | PART 1: CRITICAL ISSUES > 1.1 API Authentication Bypass Vulnerability                                                 |
| section_reference     | `#4`                                                        | Part 1: CRITICAL Issues > 1.5 REST API Optional Authentication; Priority Recommendations > Must Fix Before Production |

#### Details and Context
- Mutation requests can proceed without `X-User-Id` because validation is conditional (`ifPresent`) instead of mandatory.
- Attackers can impersonate other users by omitting the acting-user header and supplying arbitrary sender/actor IDs in payload.
- Security section states real auth is absent.
- Some paths use optional acting user resolution while others require it.
- Report warns this inconsistency can cause accidental insecure endpoint behavior.
- Report states no real authentication system exists.
- Medium-term recommendations call for real auth and session management.

#### Recommended Actions
- Require `X-User-Id` (or equivalent acting user) on all mutation endpoints.
- Introduce `requireActingUserId()` style guard that rejects missing/blank user identity.
- Implement production authentication and identity/session model.
- Audit all endpoints and apply appropriate authentication strictness.
- Implement proper authentication in REST API.
- Add authentication and real session management.

<details><summary>Evidence snippets</summary>

- `"return Optional.empty(); // ← Header optional!"`
- `"ifPresent() skips validation entirely"`
- `"No real authentication system (simulated sessions)"`
- `"resolveActingUserId() method is OPTIONAL"`
- `"Some endpoints use optional authentication ... others require it"`
- `"could lead to security issues if developers pick the wrong method"`
- `"Authentication: No real authentication system (simulated sessions)"`
- `"Medium-term Improvements: Add authentication and real session management"`
</details>

### V003. User.markDeleted does not update updatedAt metadata
- **Legacy ID:** 4
- **Validation status:** valid
- **Severity / Category:** critical / bug
- **Mentions (total):** 10
- **Cross-report conflict:** status-only
- **Status note:** INVALID
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                                                                                                                                   |
|-----------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`            | `7`                                                                                                                                                                                     |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `3`                                                                                                                                                                                     |
| section_reference     | `#1`                                                        | PART 1: CRITICAL ISSUES > 1.3 User.markDeleted() Missing touch() Call                                                                                                                   |
| section_reference     | `#2`                                                        | PART 6: Immediate Recommendations                                                                                                                                                       |
| section_reference     | `#3`                                                        | Part 1: CRITICAL Issues > 1.2 Entity touch() Calls Missing on State Transitions; Part 6: Inconsistencies Summary > Code Patterns; Priority Recommendations > Must Fix Before Production |

#### Details and Context
- Soft-delete mutates entity state but does not call `touch()`, diverging from other mutators.
- This can desynchronize audit/change tracking semantics.
- Recommendation includes `User.ban()` touch fix, while verification states this specific claim was removed/invalid.
- `User.markDeleted(Instant)` is reported as missing `touch()`.
- This is framed as creating audit trail and last-modified inconsistencies.
- Pattern-level inconsistency is reiterated in summary.

#### Recommended Actions
- Call `touch()` in `markDeleted`.
- Align recommendation list with corrected verification findings.
- Add `touch()` to `User.markDeleted()`.

<details><summary>Evidence snippets</summary>

- `"// ❌ MISSING: touch();"`
- `"Add `touch()` calls to `User.ban()`, `User.markDeleted()`"`
- `"Invalid findings removed ... `User.ban()` missing `touch()` - REMOVED"`
- `"markDeleted(Instant) ... // ❌ MISSING: touch();"`
- `"Touch() calls - Inconsistent across User and Match entities"`
</details>

### V004. User.setLocation accepts invalid/unsafe coordinate values
- **Legacy ID:** 6
- **Validation status:** partially-valid
- **Severity / Category:** critical / ui-ux
- **Mentions (total):** 6
- **Cross-report conflict:** yes
- **Status note:** INVALID
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                          |
|-----------------------|-------------------------------------------------------------|--------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`            | `5`                                                                            |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                            |
| section_reference     | `#1`                                                        | PART 1: CRITICAL ISSUES > 1.5 User.setLocation() Missing Coordinate Validation |
| section_reference     | `#2`                                                        | PART 3 > 3.4                                                                   |
| section_reference     | `#3`                                                        | Part 2: HIGH Priority Issues > 2.8 Location Feature Limited to Israel          |

#### Details and Context
- No range checks for latitude/longitude bounds.
- No finite-value checks (`NaN`, `Infinity`) and no policy for `(0,0)` edge case.
- Country-to-city data resolves only IL and returns empty list for others.
- Non-Israel selections are explicitly rejected as future work.

#### Recommended Actions
- Validate range and finiteness before persisting coordinates.
- Expand geography coverage and data source strategy.
- Expand location support beyond Israel.

<details><summary>Evidence snippets</summary>

- `"public void setLocation(double lat, double lon) { ... this.hasLocationSet = true; ... }"`
- `"return COUNTRY_IL.equalsIgnoreCase(countryCode) ? ISRAEL_CITIES : List.of();"`
- `""Please choose Israel for now.""`
</details>

### V005. Dealbreakers mapper does not merge all normalized sources
- **Legacy ID:** 7
- **Validation status:** partially-valid
- **Severity / Category:** critical / bug
- **Mentions (total):** 5
- **Cross-report conflict:** yes
- **Status note:** RESOLVED/VERIFIED (2026-03-22)

#### Report Context
| Type                  | Key                                                         | Value                                                                              |
|-----------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`            | `3`                                                                                |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                |
| section_reference     | `#1`                                                        | PART 3 > 3.25                                                                      |
| section_reference     | `#2`                                                        | PART 6: RECOMMENDATIONS BY PRIORITY                                                |
| section_reference     | `#3`                                                        | Part 2: HIGH Priority Issues > 2.1 Unbounded Query in findCandidates               |
| section_reference     | `#4`                                                        | Part 3: MEDIUM Priority Issues > 3.12 CandidateFinder Cache Fingerprint Incomplete |

#### Details and Context
- Mapper now composes scalar dealbreaker fields with normalized dealbreaker dimensions on read.
- Candidate cache fingerprint now includes dealbreakers/interests/lifestyle/pace state.
- Candidate query keeps DB-side location narrowing for very large radius values via capped bounding-box filtering.
- Added regression coverage for merged dealbreakers, fingerprint sensitivity, and large-radius query narrowing.

#### Recommended Actions
- ✅ Merge legacy and normalized sources deterministically.
- ✅ Include dealbreakers in candidate fingerprint/cache key.
- ✅ Keep bounded query constraints even for large radius values; avoid full-table candidate scans.
- ✅ Include all preference-affecting dimensions in cache fingerprint.

<details><summary>Evidence snippets</summary>

- `"NOT merged with normalized table data"`
- `"Include dealbreakers in CandidateFinder cache key"`
- `"If `maxDistanceKm >= 50_000`, the bounding-box filter is skipped entirely and ALL active users are returned"`
- `"doesn't include `dealbreakers`, `interests`, and lifestyle fields in cache key"`
</details>

### V006. TrustSafetyService auto-ban synchronization is single-instance only
- **Legacy ID:** 8
- **Validation status:** partially-valid
- **Severity / Category:** critical / bug
- **Mentions (total):** 5
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                          | Value                                                              |
|-----------------------|--------------------------------------------------------------|--------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`             | `2`                                                                |
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                                                |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md`  | `1`                                                                |
| section_reference     | `#1`                                                         | 2.2 Issue Register; 6 Addendum Workspace Audit Fixes               |
| section_reference     | `#2`                                                         | APPENDIX B > 4                                                     |
| section_reference     | `#3`                                                         | Part 1: CRITICAL Issues > 1.4 Trust Safety Auto-Ban Race Condition |

#### Details and Context
- In-process synchronization does not protect multi-instance deployments.
- `synchronized(this)` only protects in-process instance scope.
- In clustered deployments, concurrent bans may be triggered across nodes.
- Finding reports same call performing auto-block and auto-ban behavior.
- Addendum later states report() was refactored to conditional branch logic.

#### Recommended Actions
- Use DB-level atomic transitions/locking for distributed safety workflows.
- Use distributed/centralized concurrency control for auto-ban operations in multi-node deployments.

<details><summary>Evidence snippets</summary>

- `"`synchronized(this)` only protects single instance"`
- `"The `synchronized(this)` block only protects against concurrent calls on the same instance"`
- `"In a clustered deployment, multiple nodes could trigger the ban simultaneously"`
- `"report auto-blocks and auto-bans in same call"`
- `"conditionally branch blocking only if applyAutoBanIfThreshold returns false"`
</details>

### V007. Foreign key constraints reliability risk
- **Legacy ID:** 10
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** critical / build
- **Mentions (total):** 1
- **Cross-report conflict:** status-only
- **Status note:** RESOLVED/HISTORICAL (re-open only if flake is reproducible now)

#### Report Context
| Type                  | Key                                                          | Value         |
|-----------------------|--------------------------------------------------------------|---------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `1`           |
| section_reference     | `#1`                                                         | 2.1 Key Risks |

#### Details and Context
- Top-priority risk states data integrity could silently degrade due to FK constraints not reliably applied.
- Marked resolved in this snapshot.

#### Recommended Actions
- No action required — claim is false.

#### ❌ False Positive Proof (verified 2026-03-21)
FK constraints are **comprehensively defined** across all tables in `SchemaInitializer.java`:
- `likes` (lines 131-132): 2 FKs → `users(id)` with `ON DELETE CASCADE`
- `matches` (lines 151-152): 2 FKs → `users(id)` with `ON DELETE CASCADE`
- `swipe_sessions` (line 171): FK → `users(id)` with `ON DELETE CASCADE`
- `user_stats` (line 204): FK → `users(id)` with `ON DELETE CASCADE`
- `conversations` (lines 277-278): 2 FKs with `ON DELETE CASCADE`
- `messages` (lines 293-294): 2 FKs with `ON DELETE CASCADE`
- `friend_requests` (lines 312-313): 2 FKs with `ON DELETE CASCADE`
- `notifications` (line 330): FK with `ON DELETE CASCADE`
- `blocks` (lines 347-348), `reports` (lines 365-366), `profile_notes` (lines 383-384), `profile_views` (lines 396-397), `standouts` (lines 418-419): All have proper FKs with `ON DELETE CASCADE`
- `user_photos`, `user_interests`, `user_interested_in`, all `user_db_*` junction tables (lines 446-509): All FK → `users(id)` with `ON DELETE CASCADE`

**No table is missing FK constraints.** Every table referencing `users` has proper named constraints with appropriate cascade semantics.

<details><summary>Evidence snippets</summary>

- `"Data integrity can silently degrade due to foreign key constraints not being reliably applied"`
</details>

### V008. RelationshipWorkflowPolicy transition coverage concern (marked invalid/intentional)
- **Legacy ID:** 11
- **Validation status:** partially-valid
- **Severity / Category:** high / ui-ux
- **Mentions (total):** 26
- **Cross-report conflict:** yes
- **Status note:** INVALID, RESOLVED/HISTORICAL

#### Report Context
| Type                  | Key                                                          | Value                                                                                                                                                                                     |
|-----------------------|--------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `16`                                                                                                                                                                                      |
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`             | `6`                                                                                                                                                                                       |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md`  | `4`                                                                                                                                                                                       |
| section_reference     | `#1`                                                         | 2.3 Unimplemented Or Partial Features                                                                                                                                                     |
| section_reference     | `#2`                                                         | 4 Un-Actionable/Invalid/Resolved Ledger                                                                                                                                                   |
| section_reference     | `#3`                                                         | Executive Summary (new issues added)                                                                                                                                                      |
| section_reference     | `#4`                                                         | PART 2 > 2.12; PART 4 > 4.18                                                                                                                                                              |
| section_reference     | `#5`                                                         | PART 4 > 4.8                                                                                                                                                                              |
| section_reference     | `#6`                                                         | Part 1: CRITICAL Issues > 1.7 Blocks/Reports Tables Missing Soft-Delete Filtering; Part 6: Inconsistencies Summary > Code Patterns; Priority Recommendations > Must Fix Before Production |
| section_reference     | `#7`                                                         | Part 2: HIGH Priority Issues > 2.5 Report Result Message Inconsistency                                                                                                                    |

#### Details and Context
- Report raises missing states in transition map but later marks concern as invalid/intentional.
- Still indicates potential documentation clarity gap around terminal/unused states.
- Two architecture guards are disabled until WU-14 completion.
- Marked acceptable but still technical debt/risk window.
- Executive summary explicitly flags missing self-reference guard in API.
- Tables have `deleted_at` columns but query filters and soft-delete operations are reportedly missing.
- Behavior is called inconsistent with broader soft-delete strategy.
- Output messaging claims user was blocked regardless of actual branch parameter.
- Feature parity item says trust/safety actions existed in CLI only.
- Later addendum details implementation in Matching and Chat.
- The ledger enumerates additional concern IDs: standouts scoring hardcoded, migration strategy add-columns-only, image cache LRU claim, maxInterests mismatch, EnumSet crash, unsafe constructors, constructor inconsistency, hardcoded minAge check, distance-vs-photo validation mismatch, session listener leak, deleted-user NPE, flow-exception usage, metrics map leak.
- Most are explicitly marked RESOLVED or INVALID but are still listed as prior issues.

#### Recommended Actions
- Document intentional state exclusions and terminal-state rationale.
- Re-enable tests once rollout prerequisites are complete.
- Reject operations where acting user equals target user on like/block/report endpoints.
- Add `deleted_at IS NULL` filtering in queries.
- Use soft-delete behavior for blocks/reports operations.
- Adjust `handleReportResult()` messaging to reflect `blockUser` true/false paths.

<details><summary>Evidence snippets</summary>

- `"[INVALID] ❌ Triple-verification confirms the policy is logically sound"`
- `"PENDING states are not used in the current version"`
- `"Enable after WU-14 completes"`
- `"Missing self-reference validation in REST API (can like/block own profile)"`
- `"tables have `deleted_at` columns but: No queries filter on `deleted_at IS NULL`"`
- `"No soft-delete is performed when deleting blocks/reports"`
- `"`handleReportResult()` always says "has been blocked" regardless of the `blockUser` parameter"`
- `"trust & safety actions ... exists in CLI only"`
- `"Block/Report dialogs added to MatchingController and ChatController"`
- `"Un-Actionable / Invalid / Resolved Items Ledger"`
- `"Image cache eviction not LRU (INVALID)"`
- `"PerformanceMonitor metrics map leak (INVALID)"`
</details>

### V009. REST routes bypass use-case layer via deliberate exceptions
- **Legacy ID:** 12
- **Validation status:** valid
- **Severity / Category:** high / architecture
- **Mentions (total):** 7
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                                          | Value                                                         |
|-----------------------|--------------------------------------------------------------|---------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`             | `4`                                                           |
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `3`                                                           |
| section_reference     | `#1`                                                         | 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-001 |
| section_reference     | `#2`                                                         | PART 2 > 2.13; PART 3 > 3.43; PART 4 > 4.2                    |

#### Details and Context
- Some endpoints intentionally access storage/services directly, bypassing use-case contracts.
- Increases risk of business-rule divergence and duplicated policy.
- Handlers, viewmodels, API, and adapters are described as bypassing core service boundaries.
- ServiceRegistry exposure of storage objects is called out as enabling boundary violations.
- Direct state mutation from upper layers (e.g., interaction storage updates) is cited as a structural risk.

#### Recommended Actions
- Expand use-case layer coverage and migrate deliberate exceptions behind consistent application boundaries.
- Enforce strict boundary rules so handlers and UI adapters depend only on service interfaces, not storage.

<details><summary>Evidence snippets</summary>

- `"Deliberate exception: read-only candidate projection route."`
- `"Layer boundaries are porous and service boundaries are bypassed"`
- `"ServiceRegistry.java exposes storage objects directly to upper layers"`
- `"enforce strict boundary rules"`
</details>

### V010. JdbiUserStorage exhibits N+1 profile loading pattern
- **Legacy ID:** 13
- **Validation status:** valid
- **Severity / Category:** high / performance
- **Mentions (total):** 6
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                                         | Value                                                                 |
|-----------------------|-------------------------------------------------------------|-----------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`            | `3`                                                                   |
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`             | `1`                                                                   |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                   |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md`    | `1`                                                                   |
| section_reference     | `#1`                                                        | 12. Areas for Improvement                                             |
| section_reference     | `#2`                                                        | 12. Code Quality Patterns > Areas for Improvement                     |
| section_reference     | `#3`                                                        | PART 2 > 2.19                                                         |
| section_reference     | `#4`                                                        | PART 3 > 3.1                                                          |
| section_reference     | `#5`                                                        | Part 2: HIGH Priority Issues > 2.10 N+1 Query Pattern in User Storage |

#### Details and Context
- Normalized data assembly causes multiple per-user queries.
- Default implementation iterates conversation IDs and calls single-count API repeatedly.
- Report explicitly notes N+1 query patterns.
- Per-user follow-up queries are issued for photos/interests/preferences/dealbreakers.
- General performance concern calls out N+1 in storage.

#### Recommended Actions
- Batch fetch and join normalized profile data.
- Provide true batched SQL/grouped count implementation.
- Batch and join query paths; profile frequently accessed endpoints.
- Batch-fetch related collections or join/aggregate to reduce query round-trips.
- Eliminate N+1 query patterns in storage layer.

<details><summary>Evidence snippets</summary>

- `"N+1 Query Pattern in User Storage"`
- `"counts.put(id, countMessages(id)); // N+1 query"`
- `"Some N+1 query patterns in storage layer"`
- `"resulting in N+1 queries when loading multiple users"`
- `"Performance: Some N+1 query patterns in storage layer"`
</details>

### V011. ProfileService creates legacy DefaultAchievementService per call
- **Legacy ID:** 15
- **Validation status:** valid
- **Severity / Category:** high / performance
- **Mentions (total):** 6
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                                         | Value                                                                                          |
|-----------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`            | `3`                                                                                            |
| mention_count_by_file | `STATUS_2026-03-17_By_claude_sonnet_4.6.md`                 | `2`                                                                                            |
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                                            |
| section_reference     | `#1`                                                        | Code Quality Issues #2                                                                         |
| section_reference     | `#2`                                                        | PART 2 > 2.8; PART 3 > 3.46                                                                    |
| section_reference     | `#3`                                                        | Part 3: MEDIUM Priority Issues > 3.13 ProfileService Achievement Service Created on Every Call |

#### Details and Context
- New service object is created repeatedly instead of reusing injected singleton.
- Increases allocation churn and deepens dependency ambiguity.
- Method instantiates DefaultAchievementService on every invocation.
- This behavior is used by five methods and is flagged as anti-pattern.
- New `DefaultAchievementService` instances are created per call path, suggesting avoidable allocation/work.

#### Recommended Actions
- Inject and reuse AchievementService.
- Initialize DefaultAchievementService once as a final field in constructor.
- Reuse/inject singleton achievement service instead of per-call construction.

<details><summary>Evidence snippets</summary>

- `"return new DefaultAchievementService(...)"`
- `"Instantiation Anti-Pattern"`
- `"Creates a brand-new DefaultAchievementService on every invocation"`
- `"`legacyAchievementService()` method creates a new `DefaultAchievementService` instance on every call"`
</details>

### V012. AppConfig.Builder deletion refactor not completed
- **Legacy ID:** 16
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** high / maintainability
- **Mentions (total):** 4
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                         | Value                            |
|-----------------------|---------------------------------------------|----------------------------------|
| mention_count_by_file | `STATUS_2026-03-17_By_claude_sonnet_4.6.md` | `4`                              |
| section_reference     | `#1`                                        | Refactoring Plan Status (Task 3) |

#### Details and Context
- Task status says Builder deletion is not done.
- File quantifies remaining builder span and that defaults still route through builder.
- Sub-record defaults factories are still absent.

#### Recommended Actions
- No action required — Builder is essential and cannot be deleted.

#### ❌ False Positive Proof (verified 2026-03-21)
The Builder class (lines 224-670 of `AppConfig.java`) is **essential infrastructure**, not dead code:

1. **Required by `ApplicationStartup` for config loading** (lines 150, 171): `AppConfig.Builder builder = AppConfig.builder();` — Jackson databinding uses `readerForUpdating(builder)` (line 195) to populate config from JSON files.
2. **Required for environment variable overrides** (lines 219-240): Builder setter methods like `builder::dailyLikeLimit` are used as method references for env var overrides.
3. **`AppConfig.defaults()`** (lines 210-212) creates a Builder with hard-coded defaults then calls `build()` — this is the only construction path.
4. **70+ fluent setter methods** (lines 290-588) are actively wired through Jackson mix-in deserialization.
5. **Full test coverage**: `AppConfigTest.java` (lines 43-207) validates Builder construction, default preservation, chainability, and field mapping.

**Deleting Builder would break**: config file loading, env var overrides, `defaults()`, and Jackson deserialization. The sub-record refactor was completed correctly — Builder remains as the assembly layer.

<details><summary>Evidence snippets</summary>

- `"AppConfig.Builder deletion (~447 LOC) — Not done"`
- `"Builder class starts at line 224, ends at line 670"`
</details>

### V013. REST API implementation completeness is unclear/placeholder
- **Legacy ID:** 17
- **Validation status:** partially-valid
- **Severity / Category:** high / architecture
- **Mentions (total):** 4
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                                                                                                             |
|-----------------------|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`          | `2`                                                                                                               |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `2`                                                                                                               |
| section_reference     | `#1`                                                     | 11. Identified Gaps and Incomplete Features                                                                       |
| section_reference     | `#2`                                                     | 11. Identified Gaps and Incomplete Features > Placeholder Implementations; Recommendations > Immediate Priorities |

#### Details and Context
- Report explicitly flags REST API implementation details as unclear/incomplete.
- Report flags REST server as existing but with unclear implementation depth.
- Immediate priority list repeats this as a top completion item.

#### Recommended Actions
- Complete REST API implementation and clarify endpoint behavior/contracts.
- Complete REST API implementation.

<details><summary>Evidence snippets</summary>

- `"REST API: `RestApiServer.java` exists but implementation details unclear"`
- `"REST API ... exists but implementation details unclear"`
- `"Immediate Priorities: Complete REST API implementation"`
</details>

### V014. Verification workflows are simulated, not fully integrated
- **Legacy ID:** 21
- **Validation status:** partially-valid
- **Severity / Category:** high / security
- **Mentions (total):** 4
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                                                                                                             |
|-----------------------|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`          | `2`                                                                                                               |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `2`                                                                                                               |
| section_reference     | `#1`                                                     | 11. Identified Gaps and Incomplete Features                                                                       |
| section_reference     | `#2`                                                     | 11. Identified Gaps and Incomplete Features > Placeholder Implementations; Recommendations > Immediate Priorities |

#### Details and Context
- Email/phone verification is noted as simulated rather than production-integrated.
- Email/phone verification is described as simulated.
- Recommendation reiterates need to integrate verification more fully.

#### Recommended Actions
- Integrate real verification providers/workflows.
- Enhance verification system integration.

<details><summary>Evidence snippets</summary>

- `"Email/phone verification simulated, not integrated"`
- `"Verification System: Email/phone verification simulated, not integrated"`
- `"Immediate Priorities: Enhance verification system integration"`
</details>

### V015. Conversation deletion uses hard delete instead of soft delete
- **Legacy ID:** 23
- **Validation status:** valid
- **Severity / Category:** high / architecture
- **Mentions (total):** 3
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                                                                                                                                 |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `3`                                                                                                                                                                                   |
| section_reference     | `#1`                                                        | Part 1: CRITICAL Issues > 1.6 Conversation Hard-Delete Instead of Soft-Delete; Part 6: Inconsistencies Summary > Code Patterns; Priority Recommendations > Must Fix Before Production |

#### Details and Context
- Conversation rows are hard-deleted even though deletion model elsewhere is soft-delete.
- Schema includes `deleted_at`, but conversation delete path uses physical delete.

#### Recommended Actions
- Convert conversation deletion to soft-delete.

<details><summary>Evidence snippets</summary>

- `"Uses hard DELETE while all other entities ... use soft-delete"`
- `"messagingDao.deleteConversation() uses hard DELETE"`
</details>

### V016. Critical utility classes lack direct tests
- **Legacy ID:** 24
- **Validation status:** partially-valid
- **Severity / Category:** high / testing
- **Mentions (total):** 3
- **Cross-report conflict:** status-only
- **Status note:** INVALID
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `3`           |
| section_reference     | `#1`                                             | PART 2 > 2.21 |

#### Details and Context
- Interest matcher, profile completion support, and sanitizer utility are called out as untested.

#### Recommended Actions
- Add focused unit tests for these classes and edge cases.

<details><summary>Evidence snippets</summary>

- `"No tests ❌"`
</details>

### V017. Large multi-responsibility units increase change blast radius
- **Legacy ID:** 25
- **Validation status:** partially-valid
- **Severity / Category:** high / maintainability
- **Mentions (total):** 3
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                          | Value                                                         |
|-----------------------|--------------------------------------------------------------|---------------------------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `3`                                                           |
| section_reference     | `#1`                                                         | 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-004 |

#### Details and Context
- Large classes with mixed concerns are highlighted (handlers/entities/preferences).
- Broad interfaces are noted as amplifying regression risk and change impact.

#### Recommended Actions
- Split large classes into smaller, focused single-responsibility components/services.

<details><summary>Evidence snippets</summary>

- `"Large multi-responsibility units and broad interfaces create high change blast radius"`
- `"Split large classes into smaller, focused single-responsibility services"`
</details>

### V018. Presence feature flag lacks documentation
- **Legacy ID:** 26
- **Validation status:** partially-valid
- **Severity / Category:** high / architecture
- **Mentions (total):** 3
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                                                                   |
|-----------------------|----------------------------------------------------------|-------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`         | `1`                                                                     |
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`          | `1`                                                                     |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `1`                                                                     |
| section_reference     | `#1`                                                     | 11. Configuration Limitations                                           |
| section_reference     | `#2`                                                     | 11. Identified Gaps and Incomplete Features > Configuration Limitations |
| section_reference     | `#3`                                                     | PART 2 > 2.4                                                            |

#### Details and Context
- Runtime behavior depends on undocumented system property toggle.
- Operational teams may miss or misconfigure presence feature behavior.
- Runtime toggling support is described as limited.
- Feature toggling breadth is reported as constrained.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Document `datingapp.ui.presence.enabled` and expected defaults/impacts.
- Expand feature flag framework and operational docs.
- Increase runtime feature-flag coverage.

<details><summary>Evidence snippets</summary>

- `"Feature controlled by system property `datingapp.ui.presence.enabled` without documentation"`
- `"Limited runtime feature toggling"`
- `"Feature Flags: Limited runtime feature toggling"`
</details>

### V019. Profile save and post-save side effects are not transactional
- **Legacy ID:** 27
- **Validation status:** valid
- **Severity / Category:** high / bug
- **Mentions (total):** 3
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `3`           |
| section_reference     | `#1`                                             | PART 2 > 2.16 |

#### Details and Context
- User save can succeed while achievement unlock/event publication fails and is swallowed.
- Creates partial success and inconsistent outcomes.

#### Recommended Actions
- Wrap save + post-save actions in unified transaction or add compensation/retry.

<details><summary>Evidence snippets</summary>

- `"logger.warn("Post-save action failed...", e); // Swallowed!"`
</details>

### V020. Authentication beyond user selection not implemented
- **Legacy ID:** 30
- **Validation status:** valid
- **Severity / Category:** high / security
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                          | Value                                 |
|-----------------------|--------------------------------------------------------------|---------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                   |
| section_reference     | `#1`                                                         | 2.3 Unimplemented Or Partial Features |

#### Details and Context
- Listed as still valid backlog item after revalidation.
- Identified as active architectural/product gap.

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"Authentication beyond user selection is not implemented"`
- `"Still active as meaningful backlog"`
</details>

### V021. JavaFX social/friend-request/notifications parity gap
- **Legacy ID:** 32
- **Validation status:** valid
- **Severity / Category:** high / ui-ux
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Status note:** RESOLVED/HISTORICAL (verified in current code)

#### Report Context
| Type                  | Key                                                          | Value                                             |
|-----------------------|--------------------------------------------------------------|---------------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                               |
| section_reference     | `#1`                                                         | 2.3 Unimplemented Or Partial Features; 8 Addendum |

#### Details and Context
- Historical report gap said social features existed in CLI only.
- Current code already includes SocialViewModel/SocialController/social.fxml behavior.
- Treat this as closed unless a concrete missing interaction is reproducible.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"friend requests and notifications exists in CLI only"`
- `"SocialViewModel, SocialController, and social.fxml implemented"`
</details>

### V022. Message sanitizer strips all HTML formatting (possible over-sanitization)
- **Legacy ID:** 33
- **Validation status:** partially-valid
- **Severity / Category:** high / ui-ux
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value                        |
|-----------------------|--------------------------------------------------|------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `2`                          |
| section_reference     | `#1`                                             | PART 2 > 2.10; PART 4 > 4.17 |

#### Details and Context
- Current sanitization removes all HTML, reducing formatting capability.
- Security-positive but product capability tradeoff is not clearly documented.

#### Recommended Actions
- Define explicit content policy and, if needed, allowlist safe formatting subset.

<details><summary>Evidence snippets</summary>

- `"content = SanitizerUtils.sanitize(content);"`
- `"OWASP sanitizer strips ALL HTML"`
</details>

### V023. Partially constructible services with runtime null-mode switching
- **Legacy ID:** 35
- **Validation status:** valid
- **Severity / Category:** high / architecture
- **Mentions (total):** 2
- **Cross-report conflict:** status-only
- **Status note:** RESOLVED/HISTORICAL

#### Report Context
| Type                  | Key                                                          | Value                                                         |
|-----------------------|--------------------------------------------------------------|---------------------------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                                           |
| section_reference     | `#1`                                                         | 3.1 High-Level Architecture & Domain Boundaries > FI-ARCH-003 |

#### Details and Context
- Historical constructor patterns left optional dependencies null, toggling behavior at runtime.
- Refactoring improved this with Builder/requireNonNull, but report states mode-switching still persists in MatchingService.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"partially constructible and rely on runtime null-check mode switching"`
- `"some runtime mode switches persist in MatchingService"`
</details>

### V024. ProfileController god-controller bloat
- **Legacy ID:** 36
- **Validation status:** valid
- **Severity / Category:** high / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                                          | Value                                              |
|-----------------------|--------------------------------------------------------------|----------------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                                |
| section_reference     | `#1`                                                         | 3.2 Code Architecture & Organization > FI-CONS-002 |

#### Details and Context
- Controller is described as mixing event handling, style logic, and converter-factory concerns.
- This is kept valid in report.

#### Recommended Actions
- Extract UI utility methods to UiUtils.
- Decompose into sub-controllers/components.

<details><summary>Evidence snippets</summary>

- `"ProfileController is a 'God Controller'"`
- `"Extract UI utility methods to a UiUtils class"`
</details>

### V025. REST API lacks authentication strategy/documented production posture
- **Legacy ID:** 37
- **Validation status:** valid
- **Severity / Category:** high / security
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                         | Value                                                                   |
|-----------------------|---------------------------------------------|-------------------------------------------------------------------------|
| mention_count_by_file | `STATUS_2026-03-17_By_claude_sonnet_4.6.md` | `2`                                                                     |
| section_reference     | `#1`                                        | Feature / UI Status > Genuinely Open Items; Priority Recommendations P6 |

#### Details and Context
- Open item says no auth strategy in RestApiServer.
- Localhost-only binding is noted as safeguard, but security posture is under-documented.

#### Recommended Actions
- Document REST API as dev-only or add minimal authentication.

<details><summary>Evidence snippets</summary>

- `"REST API has no auth strategy"`
- `"document as dev-only or add minimal auth"`
</details>

### V026. Storage defaults mask incomplete atomic/cleanup behavior
- **Legacy ID:** 39
- **Validation status:** valid
- **Severity / Category:** high / architecture
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value                              |
|-----------------------|--------------------------------------------------|------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `2`                                |
| section_reference     | `#1`                                             | PART 2: HIGH SEVERITY ISSUES > 2.1 |

#### Details and Context
- Default interface methods return no-op values (e.g., `0`, `false`) that can silently ship.
- Feature behavior may degrade unless every concrete storage overrides defaults.

#### Recommended Actions
- Enforce concrete overrides for atomic transitions and cleanup methods.
- Fail fast when capability is required but unsupported.

<details><summary>Evidence snippets</summary>

- `"default int purgeDeletedBefore(...) { return 0; }"`
- `"default boolean supportsAtomicRelationshipTransitions() { return false; }"`
</details>

### V027. Critical components have no direct tests
- **Legacy ID:** 42
- **Validation status:** partially-valid
- **Severity / Category:** high / testing
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                 |
|-----------------------|-------------------------------------------------------------|-------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                   |
| section_reference     | `#1`                                                        | Part 5: Test Coverage Gaps > Components with NO Tests |

#### Details and Context
- AppSession is heavily used but listed untested.
- RestApiServer listed with no unit tests.
- StorageFactory listed with no tests.
- DevDataSeeder listed with no tests.

#### Recommended Actions
- Add baseline test suites for each untested critical component.

<details><summary>Evidence snippets</summary>

- `"AppSession - NO TESTS - Used extensively but never tested"`
- `"RestApiServer - NO UNIT TESTS"`
- `"StorageFactory - NO TESTS"`
- `"DevDataSeeder - NO TESTS"`
</details>

### V028. Dashboard achievement popup load failure is silent to end user
- **Legacy ID:** 43
- **Validation status:** valid
- **Severity / Category:** high / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no

- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 2 > 2.18 |

#### Details and Context
- Failure is logged but no user-facing fallback/notification is shown.

#### Recommended Actions
- Show non-blocking UI feedback when popup rendering fails.

<details><summary>Evidence snippets</summary>

- `"No user feedback!"`
</details>

### V029. Email validation regex excludes internationalized domains
- **Legacy ID:** 45
- **Validation status:** valid
- **Severity / Category:** high / bug
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 2 > 2.9 |

#### Details and Context
- ASCII-only TLD regex rejects valid IDN email addresses.

#### Recommended Actions
- Adopt IDN-aware validation path.

<details><summary>Evidence snippets</summary>

- `"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"`
</details>

### V030. Jdbi conversation mapper used stale archive column names
- **Legacy ID:** 46
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** high / bug
- **Mentions (total):** 1
- **Cross-report conflict:** status-only
- **Status note:** RESOLVED/HISTORICAL

#### Report Context
| Type                  | Key                                                          | Value                                 |
|-----------------------|--------------------------------------------------------------|---------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `1`                                   |
| section_reference     | `#1`                                                         | 7 Addendum: Implementation Plan Fixes |

#### Details and Context
- Pre-existing bug: mapper read obsolete archive columns after split schema.
- Fix required reading all per-user archive aliases and using correct constructor.
- Linked to concrete test failures.

#### Recommended Actions
- No action required — column names are correct.

#### ❌ False Positive Proof (verified 2026-03-21)
`JdbiConnectionStorage.java` `ConversationMapper` (lines 526-562) uses **correct, current column names** that match the schema:

- SQL SELECT (lines 267-269) aliases schema columns to mapper names:
  `archived_at_a AS user_a_archived_at, archive_reason_a AS user_a_archive_reason, archived_at_b AS user_b_archived_at, archive_reason_b AS user_b_archive_reason`
- Mapper reads these aliases correctly:
  - Line 538: `readInstant(rs, "user_a_archived_at")`
  - Line 540: `readEnum(rs, "user_a_archive_reason", MatchArchiveReason.class)`
  - Line 541: `readInstant(rs, "user_b_archived_at")`
  - Line 543: `readEnum(rs, "user_b_archive_reason", MatchArchiveReason.class)`
- Schema (`SchemaInitializer.java` lines 269-272) defines: `archived_at_a`, `archive_reason_a`, `archived_at_b`, `archive_reason_b` — exactly what the aliases map from.

**No stale or obsolete column references exist.** This was a historical bug that has been fully fixed.

<details><summary>Evidence snippets</summary>

- `"ConversationMapper read stale column names"`
- `"Root cause of 3 MessagingHandlerTest failures"`
</details>

### V031. Key concurrency/error-path test categories are missing
- **Legacy ID:** 47
- **Validation status:** partially-valid
- **Severity / Category:** high / testing
- **Mentions (total):** 1
- **Cross-report conflict:** status-only
- **Status note:** INVALID

#### Report Context
| Type                  | Key                                                         | Value                                                         |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                           |
| section_reference     | `#1`                                                        | Part 5: Test Coverage Gaps > Specific Missing Test Categories |

#### Details and Context
- AppSession: listener exception and concurrent listener mutation.
- EventBus: REQUIRED-mode throwing handler and registration-time delivery interactions.
- API: malformed UUID, invalid enum values, body-size limits.
- Rate limiter: concurrent access and window expiration timing.
- Configuration: weight-sum edge and timezone parse failures.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Add targeted tests for all listed missing categories.

<details><summary>Evidence snippets</summary>

- `"Specific Missing Test Categories"`
- `"Rate Limiter - Concurrent access, window expiration timing"`
</details>

### V032. Photo count constraints are not enforced at model layer
- **Legacy ID:** 48
- **Validation status:** valid
- **Severity / Category:** high / bug
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                           |
|-----------------------|-------------------------------------------------------------|-----------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                             |
| section_reference     | `#1`                                                        | Part 2: HIGH Priority Issues > 2.9 Photo URL Limit Not Enforced |

#### Details and Context
- `AppConfig.maxPhotos` exists but model mutators reportedly do not enforce it.

#### Recommended Actions
- Enforce max photo count in `User.addPhotoUrl()` / `User.setPhotoUrls()`.

<details><summary>Evidence snippets</summary>

- `"AppConfig has `maxPhotos` ... but no enforcement in User model"`
</details>

### V033. Session management needs production-grade implementation
- **Legacy ID:** 49
- **Validation status:** valid
- **Severity / Category:** high / security
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                             | Value                                      |
|-----------------------|-------------------------------------------------|--------------------------------------------|
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md` | `1`                                        |
| section_reference     | `#1`                                            | Recommendations > Medium-term Improvements |

#### Details and Context
- Medium-term recommendations call for real auth/session management.

#### Recommended Actions
- Implement secure, persistent session lifecycle and invalidation controls.

<details><summary>Evidence snippets</summary>

- `"Add authentication and real session management"`
</details>

### V034. Moderation audit logging is not structured/compliance-grade
- **Legacy ID:** 53
- **Validation status:** partially-valid
- **Severity / Category:** medium / security
- **Mentions (total):** 4
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                                       |
|-----------------------|----------------------------------------------------------|---------------------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md`         | `2`                                         |
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`          | `1`                                         |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `1`                                         |
| section_reference     | `#1`                                                     | 13. Security Considerations > Security Gaps |
| section_reference     | `#2`                                                     | 13. Security Gaps                           |
| section_reference     | `#3`                                                     | PART 3 > 3.32                               |
| section_reference     | `#4`                                                     | PART 3 > 3.33                               |

#### Details and Context
- Audit log lacks standard forensic fields (correlation/session/IP/etc.).
- No robust request lifecycle logging for audit trail.
- Existing audit trails are basic and may miss critical security events.
- Audit trail exists but security-specific event coverage is reported shallow.

#### Recommended Actions
- Implement structured audit schema and dedicated audit sink.
- Add structured request/response logs with redaction policy.
- Enhance security event telemetry and audit detail.
- Expand security event logging and audit telemetry.

<details><summary>Evidence snippets</summary>

- `"No timestamp, IP, session info, correlation ID"`
- `"No request/response logging for audit trail"`
- `"Basic audit trails but limited security event logging"`
- `"Audit Logging: Basic audit trails but limited security event logging"`
</details>

### V035. Multi-photo UI/gallery parity gap
- **Legacy ID:** 54
- **Validation status:** partially-valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 4
- **Cross-report conflict:** yes
- **Status note:** RESOLVED/HISTORICAL

#### Report Context
| Type                  | Key                                                          | Value                                      |
|-----------------------|--------------------------------------------------------------|--------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                        |
| mention_count_by_file | `STATUS_2026-03-17_By_claude_sonnet_4.6.md`                  | `2`                                        |
| section_reference     | `#1`                                                         | 2.2 FI-AUD-008; 2.3 item 8                 |
| section_reference     | `#2`                                                         | Feature / UI Status > Genuinely Open Items |

#### Details and Context
- Original report described one-photo-only behavior despite domain support for multiple photos.
- Current codebase already includes shipped multi-photo behavior and tests; do not re-open as blank parity reimplementation.
- Any follow-on work should be tracked as UX enhancement requests, not parity-gap closure.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Implement photo gallery/carousel for profile/match views.

<details><summary>Evidence snippets</summary>

- `"UI only supports a single profile photo despite domain allowing two"`
- `"Multi-photo profile gallery is not implemented in UI"`
- `"shows only first photo"`
- `"photoUrls().getFirst() hardcoded — no carousel"`
</details>

### V036. JavaFX standouts parity gap
- **Legacy ID:** 57
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 3
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                          | Value                                  |
|-----------------------|--------------------------------------------------------------|----------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `3`                                    |
| section_reference     | `#1`                                                         | 2.2 FI-AUD-004; 2.3 item 4; 8 Addendum |

#### Details and Context
- Standouts were reported as core/CLI feature with missing JavaFX surface.
- Addendum later describes ViewModel/Controller/FXML and dashboard navigation wiring.

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"Standouts ... have no JavaFX UI"`
- `"StandoutsViewModel, StandoutsController, and standouts.fxml implemented"`
</details>

### V037. Profile note deletion is hard-delete and inconsistent
- **Legacy ID:** 58
- **Validation status:** valid
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 3
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                                                                                                          |
|-----------------------|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `3`                                                                                                                                                            |
| section_reference     | `#1`                                                        | Part 1: CRITICAL Issues > 1.8 Profile Note Hard-Delete; Part 6: Inconsistencies Summary > Code Patterns; Priority Recommendations > Must Fix Before Production |

#### Details and Context
- Profile note deletion path is flagged as hard delete and inconsistent with soft-delete pattern expectations.

#### Recommended Actions
- Convert profile note deletion to soft-delete for consistency.

<details><summary>Evidence snippets</summary>

- `"Profile Note Hard-Delete"`
- `"Uses hard DELETE instead of soft-delete, inconsistent with other tables"`
</details>

### V038. Accessibility support is limited
- **Legacy ID:** 59
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                                                    |
|-----------------------|----------------------------------------------------------|----------------------------------------------------------|
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`          | `1`                                                      |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `1`                                                      |
| section_reference     | `#1`                                                     | 11. Identified Gaps and Incomplete Features > UI/UX Gaps |
| section_reference     | `#2`                                                     | 11. UI/UX Gaps                                           |

#### Details and Context
- Screen reader and related accessibility coverage is called out as limited.
- Accessibility gap is explicitly identified for assistive technologies.

#### Recommended Actions
- Improve accessibility semantics, keyboard navigation, and assistive-tech validation.
- Improve accessibility including stronger screen-reader support.

<details><summary>Evidence snippets</summary>

- `"Limited screen reader support"`
- `"Accessibility: Limited screen reader support"`
</details>

### V039. Age calculation timezone source concern
- **Legacy ID:** 60
- **Validation status:** partially-valid
- **Severity / Category:** medium / bug
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                          | Value                      |
|-----------------------|--------------------------------------------------------------|----------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                        |
| section_reference     | `#1`                                                         | 2.2 FI-AUD-006; 7 Addendum |

#### Details and Context
- Issue states age used system default timezone rather than configured timezone.
- Addendum says existing User.getAge already used configured timezone path.

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"Age calculations use system default timezone instead of configured user timezone"`
- `"User.getAge() already calls ... userTimeZone()"`
</details>

### V040. Architecture/package documentation divergence (missing packages in architecture docs)
- **Legacy ID:** 61
- **Validation status:** valid
- **Severity / Category:** medium / docs
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value                            |
|-----------------------|--------------------------------------------------|----------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `2`                              |
| section_reference     | `#1`                                             | APPENDIX A: Verification Summary |

#### Details and Context
- Verification table flags architecture/package mismatch with source-of-truth.

#### Recommended Actions
- Reconcile architecture docs with current package layout.

<details><summary>Evidence snippets</summary>

- `"Missing packages in architecture ... Documentation divergence confirmed"`
</details>

### V041. Business limits/rules are hardcoded instead of config-driven
- **Legacy ID:** 62
- **Validation status:** partially-valid
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                                                                   |
|-----------------------|----------------------------------------------------------|-------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`          | `1`                                                                     |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `1`                                                                     |
| section_reference     | `#1`                                                     | 11. Configuration Limitations                                           |
| section_reference     | `#2`                                                     | 11. Identified Gaps and Incomplete Features > Configuration Limitations |

#### Details and Context
- Some business constraints are embedded in code, reducing runtime flexibility.
- Report calls out embedded rules that reduce runtime tunability.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Move business thresholds/limits to validated configuration.
- Move hardcoded business limits into configuration where appropriate.

<details><summary>Evidence snippets</summary>

- `"Hardcoded Limits: Some business rules embedded in code"`
- `"Hardcoded Limits: Some business rules embedded in code rather than config"`
</details>

### V042. CLI exception paths silently swallow errors
- **Legacy ID:** 63
- **Validation status:** valid
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                                                              |
|-----------------------|-------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                                |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.2 Silent Error Handling in CLI; Part 6: Inconsistencies Summary > Code Patterns |

#### Details and Context
- Multiple catch blocks discard exceptions without user-facing feedback.

#### Recommended Actions
- Surface actionable error messages and log exceptions instead of silent catches.

<details><summary>Evidence snippets</summary>

- `"Multiple catch blocks silently ignore exceptions without user feedback"`
</details>

### V043. CLI login-gate checks are duplicated/inconsistent
- **Legacy ID:** 64
- **Validation status:** partially-valid
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                                                                        |
|-----------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                                          |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.1 Inconsistent requireLogin Usage in CLI; Part 6: Inconsistencies Summary > Code Patterns |

#### Details and Context
- Some handlers manually inspect current user null-state instead of shared require-login utility.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Standardize on centralized `CliTextAndInput.requireLogin()` pattern.

<details><summary>Evidence snippets</summary>

- `"Several methods manually check for `currentUser == null` instead of using centralized `requireLogin()`"`
</details>

### V044. CLI profile inputs lack complete validation
- **Legacy ID:** 65
- **Validation status:** valid
- **Severity / Category:** medium / bug
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                                                            |
|-----------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                              |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.3 Input Validation Missing in CLI; Priority Recommendations > Should Fix Soon |

#### Details and Context
- Bio max length validation missing.
- Photo URL validation missing.
- Name length validation missing.

#### Recommended Actions
- Add input validation in CLI for bio/name/url constraints.

<details><summary>Evidence snippets</summary>

- `"No validation for maximum bio length"`
- `"No URL validation"`
- `"No validation for name length"`
</details>

### V045. Config validator does not enforce monotonic threshold ordering
- **Legacy ID:** 66
- **Validation status:** partially-valid
- **Severity / Category:** medium / bug
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                                                                   |
|-----------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                                     |
| section_reference     | `#1`                                                        | Part 4: LOW Priority Issues > 4.8 Config Validation Doesn't Check Monotonicity; Priority Recommendations > Nice to Have |

#### Details and Context
- Response-time threshold series can be non-monotonic without validation failure.

#### Recommended Actions
- Add monotonicity validation in `AppConfigValidator`.

<details><summary>Evidence snippets</summary>

- `"values should be monotonically increasing but aren't validated"`
</details>

### V046. Configuration sourcing inconsistency and stale defaults guidance
- **Legacy ID:** 67
- **Validation status:** valid
- **Severity / Category:** medium / architecture
- **Mentions (total):** 2
- **Cross-report conflict:** status-only
- **Status note:** RESOLVED/HISTORICAL

#### Report Context
| Type                  | Key                                                          | Value                                       |
|-----------------------|--------------------------------------------------------------|---------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                         |
| section_reference     | `#1`                                                         | 3.5 Configuration & Constants > FI-CONS-010 |

#### Details and Context
- Historical concern about fragmented business thresholds in config.
- Status notes guidance changed: prefer injected runtime config; defaults only at bootstrap/composition/tests.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"Fragmented Configuration Constants / Inconsistent Sourcing"`
- `"historical note about AppConfig.defaults() is stale"`
</details>

### V047. Connection pool lacks validation query configuration
- **Legacy ID:** 68
- **Validation status:** valid
- **Severity / Category:** medium / reliability
- **Mentions (total):** 2
- **Cross-report conflict:** status-only
- **Status note:** RESOLVED/HISTORICAL
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `2`           |
| section_reference     | `#1`                                             | PART 3 > 3.23 |

#### Details and Context
- Pool may return stale/closed connections after DB restart.

#### Recommended Actions
- Configure connection test query/validation strategy.

<details><summary>Evidence snippets</summary>

- `"No `setConnectionTestQuery()` configured"`
</details>

### V048. Event system may require expansion
- **Legacy ID:** 71
- **Validation status:** valid
- **Severity / Category:** medium / architecture
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                                                                     |
|-----------------------|----------------------------------------------------------|---------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_COMPREHENSIVE_ANALYSIS_2026-03-17.md`          | `1`                                                                       |
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `1`                                                                       |
| section_reference     | `#1`                                                     | 11. Identified Gaps and Incomplete Features                               |
| section_reference     | `#2`                                                     | 11. Identified Gaps and Incomplete Features > Placeholder Implementations |

#### Details and Context
- The event system itself exists and is actively used.
- Remaining work is contract-level: extend event payloads/types only when a concrete feature requires them.
- This is not a baseline architecture gap.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Keep current event infrastructure as-is.
- Add or extend event contracts only for concrete feature stories that need additional semantics.

<details><summary>Evidence snippets</summary>

- `"`AppEventBus` and `InProcessAppEventBus` may need expansion"`
- `"Event System: `AppEventBus` and `InProcessAppEventBus` may need expansion"`
</details>

### V049. JavaFX profile-notes parity gap
- **Legacy ID:** 72
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                          | Value                                             |
|-----------------------|--------------------------------------------------------------|---------------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                               |
| section_reference     | `#1`                                                         | 2.3 Unimplemented Or Partial Features; 8 Addendum |

#### Details and Context
- Report states profile notes were CLI-only.
- Later notes dedicated Notes view/screen implementation and inline editing.

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"profile notes exists in CLI only"`
- `"NotesViewModel, NotesController, and notes.fxml"`
</details>

### V050. No real-time chat push/websocket updates
- **Legacy ID:** 75
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                          | Value                                 |
|-----------------------|--------------------------------------------------------------|---------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                                   |
| section_reference     | `#1`                                                         | 2.3 Unimplemented Or Partial Features |

#### Details and Context
- Report marks real-time chat as unimplemented.
- UI is described as relying on manual refresh.

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"Real-time chat (push or WebSocket) is not implemented"`
- `"UI relies on manual refresh"`
</details>

### V051. Super Like UI action is placeholder behavior
- **Legacy ID:** 80
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                                                             |
|-----------------------|-------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                               |
| section_reference     | `#1`                                                        | Part 2: HIGH Priority Issues > 2.6 Super Like Feature Not Implemented; Priority Recommendations > Should Fix Soon |

#### Details and Context
- Super Like button currently calls regular like flow only.
- Feature appears surfaced but non-differentiated.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Implement distinct super-like logic or remove/hide the button.

<details><summary>Evidence snippets</summary>

- `"For now, acts like a regular like (super like logic to be added later)"`
</details>

### V052. UI controller test coverage still partial
- **Legacy ID:** 81
- **Validation status:** valid
- **Severity / Category:** medium / testing
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                          | Value                     |
|-----------------------|--------------------------------------------------------------|---------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `2`                       |
| section_reference     | `#1`                                                         | 2.4 Test And Quality Gaps |

#### Details and Context
- Originally characterized as mostly untested beyond CSS checks.
- Revalidation says coverage improved substantially but some screens remain lighter.

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"UI controllers are mostly untested beyond CSS validation"`
- `"some screens still have lighter direct coverage"`
</details>

### V053. UndoService time-window and error-handling robustness concerns
- **Legacy ID:** 82
- **Validation status:** partially-valid
- **Severity / Category:** medium / bug
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value                           |
|-----------------------|--------------------------------------------------|---------------------------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `2`                             |
| section_reference     | `#1`                                             | Executive Summary; PART 3 > 3.8 |

#### Details and Context
- Clock consistency risk between undo window checks and persisted state.
- Executive summary also flags exception swallowing behavior in UndoService.

#### Recommended Actions
- Add `logger.error()` before returning failure in the `atomicUndoDelete` catch block (lines 139-142).

#### Verification Notes (2026-03-21)
**Clock claim: ❌ FALSE — clock usage is correct.** `UndoService` uses an injected `Clock` (lines 30-31, defaults to `AppClock.clock()`) consistently across all time-sensitive operations:
- Line 50: `Instant.now(clock).plusSeconds(...)` for expiry calculation
- Line 74, 120: `Instant.now(clock)` for expiry checks
- Line 97: `Duration.between(Instant.now(clock), ...)` for remaining time
- Line 161: `Instant.now(clock)` for cleanup

**Exception swallowing claim: ✅ CONFIRMED.** Lines 139-142 catch database operation failures and convert them to a generic user string **without logging**:
```java
} catch (Exception e) {
    return UndoResult.failure("Failed to undo: %s".formatted(e.getMessage()));
}
```
No `logger.error()` call — stack traces from `atomicUndoDelete()` failures (DB errors, constraint violations) are silently discarded. This hides production issues.

<details><summary>Evidence snippets</summary>

- `"Exception swallowing in `UndoService`"`
- `"may not match storage clock"`
</details>

### V054. Use-case classes have many backward-compat constructor overloads
- **Legacy ID:** 83
- **Validation status:** partially-valid
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `2`           |
| section_reference     | `#1`                                             | PART 3 > 3.44 |

#### Details and Context
- Multiple constructors increase API surface and maintenance burden.

#### Recommended Actions
- Deprecate overloads and migrate to builder/factory style.

<details><summary>Evidence snippets</summary>

- `"5 constructors"`
</details>

### V055. Achievement thresholds duplicated/inconsistent across modules
- **Legacy ID:** 85
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.11 |

#### Details and Context
- Enum threshold values diverge from achievement service config values.

#### Recommended Actions
- No action required — thresholds are already centralized.

#### ❌ False Positive Proof (verified 2026-03-21)
Achievement thresholds are **centralized in `AppConfig.SafetyConfig`** (lines 141-151 of `AppConfig.java`), not duplicated:

- `achievementMatchTier1`–`achievementMatchTier5` (defaults: 1, 5, 10, 25, 50)
- `minSwipesForBehaviorAchievement`, `selectiveThreshold`, `openMindedThreshold`, `bioAchievementLength`, `lifestyleFieldTarget`

`DefaultAchievementService` reads **all thresholds dynamically from config** at runtime:
- Lines 108-112: `config.safety().achievementMatchTierX()` for tiers 1-5
- Lines 116, 118: `config.safety().bioAchievementLength()`, `config.safety().lifestyleFieldTarget()`
- Lines 132, 138, 155, 160, 165, 170: behavior thresholds via `config.safety()`

**No hardcoded enum values or secondary definitions exist.** `AppConfigTest.java` (lines 133-145) validates the full mapping. The claim of "divergence" is incorrect — there is exactly one source of truth.

<details><summary>Evidence snippets</summary>

- `"don't match config values in `DefaultAchievementService`"`
</details>

### V056. Architecture documentation references non-existent code paths
- **Legacy ID:** 86
- **Validation status:** partially-valid
- **Severity / Category:** medium / docs
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                   |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                     |
| section_reference     | `#1`                                                        | Part 6: Inconsistencies Summary > Documentation vs Code |

#### Details and Context
- Report says docs mention packages/files not present in current source layout.

#### Recommended Actions
- Update architecture docs to align with actual source tree.

<details><summary>Evidence snippets</summary>

- `"Architecture docs reference non-existent packages (`app/error/`, `core/time/TimePolicy.java`)"`
</details>

### V057. Chat message length indicator styling can race with text clear
- **Legacy ID:** 87
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.42 |

#### Details and Context
- Style class updates may apply out of order around input reset.

#### Recommended Actions
- Serialize style updates and input-clearing state changes.

<details><summary>Evidence snippets</summary>

- `"style class update may race with text clear"`
</details>

### V058. Chat message-list listener lifecycle may cause update-time instability
- **Legacy ID:** 88
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                               |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                 |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.18 Listener Not Properly Removed |

#### Details and Context
- Listener removal only in cleanup raises risk during rapid updates / lifecycle churn.

#### Recommended Actions
- No action required — listener lifecycle is correctly managed.

#### ❌ False Positive Proof (verified 2026-03-21)
`ChatController.java` listener lifecycle is **properly implemented**:

- **Registration** (lines 160-179): Listener stored in `activeMessagesListener` field, uses standard JavaFX `while (change.next())` pattern. The listener body does **not** modify the underlying collection — it only reads added messages and scrolls.
- **Removal** (lines 766-771): `cleanup()` method removes the listener and nulls the field:
  ```java
  if (activeMessagesListener != null) {
      viewModel.getActiveMessages().removeListener(activeMessagesListener);
      activeMessagesListener = null;
  }
  ```
- **Lifecycle guarantee**: `cleanup()` is called by `BaseController` during controller destruction — this is the standard JavaFX controller lifecycle pattern in this codebase.

**ConcurrentModificationException cannot occur** because: (1) the listener uses `change.next()` which is the correct JavaFX ListChangeListener iteration pattern, (2) the listener body never modifies the list being observed, (3) removal is properly lifecycle-managed via `BaseController.cleanup()`.

<details><summary>Evidence snippets</summary>

- `"Could cause ConcurrentModificationException during rapid message updates"`
</details>

### V059. ChatViewModel test race and nondeterministic ordering
- **Legacy ID:** 89
- **Validation status:** partially-valid
- **Severity / Category:** medium / testing
- **Mentions (total):** 1
- **Cross-report conflict:** status-only
- **Status note:** RESOLVED/HISTORICAL

#### Report Context
| Type                  | Key                                                          | Value                                        |
|-----------------------|--------------------------------------------------------------|----------------------------------------------|
| mention_count_by_file | `MASTER_PROJECT_AUDIT_AND_CODE_REVIEW_updated_19_02_2026.md` | `1`                                          |
| section_reference     | `#1`                                                         | 8 Addendum: UI Feature Parity Implementation |

#### Details and Context
- Pre-existing async refresh in setup could post stale list update.
- Equal timestamps produced nondeterministic ordering assumptions in tests.
- No current re-open should be made from historical evidence alone without fresh local reproduction.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"race condition and non-deterministic ordering"`
- `"fixed by ... looking up conversations by otherUser.getId()"`
</details>

### V060. CleanupScheduler visibility/race risk in running-state reads
- **Legacy ID:** 90
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** medium / bug
- **Mentions (total):** 1
- **Cross-report conflict:** status-only
- **Status note:** RESOLVED/HISTORICAL

#### Report Context
| Type                  | Key                                                         | Value                                                               |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                 |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.8 CleanupScheduler Thread Safety |

#### Details and Context
- `isRunning()` read is reported unsynchronized and potentially stale under concurrency.

#### Recommended Actions
- No action required — proper synchronization is already in place.

#### ❌ False Positive Proof (verified 2026-03-21)
`CleanupScheduler.java` uses **correct thread-safe semantics**:

- **Line 27**: `private final AtomicBoolean running = new AtomicBoolean(false);` — `AtomicBoolean` provides volatile-equivalent visibility guarantees.
- **`start()` (lines 35-49)**: `synchronized` method that checks `running.get()` and sets `running.set(true)`.
- **`stop()` (lines 51-61)**: `synchronized` method that safely checks and updates `running`.
- **`isRunning()` (lines 63-65)**:
  ```java
  public boolean isRunning() {
      return running.get();
  }
  ```
  `AtomicBoolean.get()` is internally a volatile read — provides happens-before guarantees without requiring `synchronized`. This is the **standard Java concurrency pattern** for lock-free state queries.

The claim that `isRunning()` "reads without synchronization" is technically true (no `synchronized` keyword) but **irrelevant** — `AtomicBoolean.get()` already provides the required memory visibility. Adding `synchronized` would be wasteful and unnecessary.

<details><summary>Evidence snippets</summary>

- `"`isRunning()` method reads without synchronization, which could cause visibility issues"`
</details>

### V061. CLI input validation is inconsistent for profile and messaging flows
- **Legacy ID:** 91
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** status-only
- **Status note:** INVALID

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.30 |

#### Details and Context
- Birth date invalid input may be silently skipped.
- Message send path lacks length/profanity checks.

#### Recommended Actions
- Add explicit validation feedback and content constraints.

<details><summary>Evidence snippets</summary>

- `"silently skips on invalid input"`
- `"no length/profanity validation"`
</details>

### V062. CLI mutates user state without immediate persistence guarantees
- **Legacy ID:** 92
- **Validation status:** partially-valid
- **Severity / Category:** medium / bug
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                     |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                       |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.5 Inconsistent User Data Saving in CLI |

#### Details and Context
- Some flows rely on later `saveProfile()` calls; interruption can drop modifications.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Persist immediately after mutation or harden transactional flow to prevent interruption loss.

<details><summary>Evidence snippets</summary>

- `"This could cause data loss if flow is interrupted"`
</details>

### V063. Daily limit reset uses start-of-day logic with DST ambiguity
- **Legacy ID:** 95
- **Validation status:** valid
- **Severity / Category:** medium / bug
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 3 > 3.9 |

#### Details and Context
- Local day-boundary calculation may be ambiguous around DST transitions.

#### Recommended Actions
- Use timezone-aware policy with explicit DST handling.

<details><summary>Evidence snippets</summary>

- `"LocalDate.atStartOfDay()"`
</details>

### V064. ImageCache preload strategy can exhaust threads under load
- **Legacy ID:** 100
- **Validation status:** valid
- **Severity / Category:** medium / performance
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.13 |

#### Details and Context
- Preload uses virtual threads but sync image loading path can still create pressure.

#### Recommended Actions
- Bound preload concurrency and use non-blocking loading pipeline.

<details><summary>Evidence snippets</summary>

- `"synchronous"`
</details>

### V065. Missing standalone index on messages(conversation_id)
- **Legacy ID:** 105
- **Validation status:** partially-valid
- **Severity / Category:** medium / performance
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.17 |

#### Details and Context
- Composite indexes do not fully cover common access path.

#### Recommended Actions
- Add standalone/covering index for dominant query shapes.

<details><summary>Evidence snippets</summary>

- `"Missing Index on `messages(conversation_id)`"`
</details>

### V066. Navigation context can be dropped silently
- **Legacy ID:** 107
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.12 |

#### Details and Context
- Unconsumed context is only debug-logged then discarded, leading to wrong target data.

#### Recommended Actions
- Add user-visible fallback and stronger context-consumption guarantees.

<details><summary>Evidence snippets</summary>

- `"Context silently discarded"`
</details>

### V067. Performance monitoring/metrics observability is insufficient
- **Legacy ID:** 109
- **Validation status:** partially-valid
- **Severity / Category:** medium / performance
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                                      | Value                                      |
|-----------------------|----------------------------------------------------------|--------------------------------------------|
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `1`                                        |
| section_reference     | `#1`                                                     | Recommendations > Medium-term Improvements |

#### Details and Context
- Recommendation explicitly requests stronger performance telemetry.

#### Recommended Actions
- Add performance monitoring and metrics.

<details><summary>Evidence snippets</summary>

- `"Medium-term Improvements: Add performance monitoring and metrics"`
</details>

### V068. Phone normalization routine validates but does not normalize
- **Legacy ID:** 110
- **Validation status:** valid
- **Severity / Category:** medium / bug
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                                         |
|-----------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                                           |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.11 ValidationService Phone Normalization Doesn't Normalize |

#### Details and Context
- Method name/intent and behavior diverge; output remains non-normalized.

#### Recommended Actions
- Implement actual canonicalization in `normalizePhone()`.

<details><summary>Evidence snippets</summary>

- `"`normalizePhone()` validates but doesn't actually normalize the phone number"`
</details>

### V069. Photo URL ingestion lacks validation
- **Legacy ID:** 111
- **Validation status:** valid
- **Severity / Category:** medium / security
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 3 > 3.6 |

#### Details and Context
- Any URL string can be added without format/scheme/content checks.

#### Recommended Actions
- Validate URL format, scheme policy, and acceptable hosts/content.

<details><summary>Evidence snippets</summary>

- `"photoUrls.add(url); // No validation"`
</details>

### V070. Profile completion thresholds are hard-coded magic numbers
- **Legacy ID:** 113
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.10 |

#### Details and Context
- Scoring tiers are embedded constants rather than config-driven.

#### Recommended Actions
- No action required — thresholds are properly named constants, not magic numbers.

#### ❌ False Positive Proof (verified 2026-03-21)
`ProfileCompletionSupport.java` (lines 28-36) uses **named `private static final` constants**, not magic numbers:

```java
private static final int TIER_DIAMOND_THRESHOLD = 95;
private static final int TIER_GOLD_THRESHOLD = 85;
private static final int TIER_SILVER_THRESHOLD = 70;
private static final int TIER_BRONZE_THRESHOLD = 40;
```

These are:
- **Centralized** in one class (`ProfileCompletionSupport`)
- **Named with clear intent** (tier name + "THRESHOLD")
- **Used consistently** throughout (lines 223, 226, 229, 232, 343, 346, 349, 352)
- **Not duplicated** elsewhere in the codebase

The claim calls these "magic numbers" but they are properly declared named constants — the standard Java convention. Externalizing UI tier thresholds to config would be over-engineering for values that are inherently domain-specific and unlikely to change at runtime.

<details><summary>Evidence snippets</summary>

- `"Tier thresholds (95, 85, 70, 40) are hard-coded"`
</details>

### V071. ProfileActivationPolicy duplicates completeness logic
- **Legacy ID:** 115
- **Validation status:** valid
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 3 > 3.3 |

#### Details and Context
- `missingFields()` mirrors logic in `User.isComplete()`.

#### Recommended Actions
- Centralize completeness rules in one place.

<details><summary>Evidence snippets</summary>

- `"duplicates validation logic"`
</details>

### V072. Purge cleanup methods default to no-op return values
- **Legacy ID:** 116
- **Validation status:** valid
- **Severity / Category:** medium / build
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 3 > 3.5 |

#### Details and Context
- User/interaction cleanup APIs can silently do nothing, affecting retention/compliance jobs.

#### Recommended Actions
- Implement concrete purge behavior and alert when unsupported.

<details><summary>Evidence snippets</summary>

- `"returns 0"`
</details>

### V073. RecommendationService mostly wraps delegated methods
- **Legacy ID:** 117
- **Validation status:** partially-valid
- **Severity / Category:** medium / architecture
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.45 |

#### Details and Context
- Pass-through layering may add complexity without clear ownership/value.

#### Recommended Actions
- Clarify abstraction purpose or collapse indirection.

<details><summary>Evidence snippets</summary>

- `"pass-through methods without clear benefit"`
</details>

### V074. Report dialog does not propagate description text
- **Legacy ID:** 120
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.47 |

#### Details and Context
- Consumer receives null description despite UI prompt.

#### Recommended Actions
- Pass captured description through dialog callback payload.

<details><summary>Evidence snippets</summary>

- `"always null"`
</details>

### V075. Social event publishing uses magic state strings
- **Legacy ID:** 121
- **Validation status:** valid
- **Severity / Category:** medium / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.31 |

#### Details and Context
- Literal state names are used instead of enums.

#### Recommended Actions
- Use `Match.MatchState` enum values for event payloads.

<details><summary>Evidence snippets</summary>

- `""MATCHED", "UNMATCHED""`
</details>

### V076. Soft-delete propagation not cascaded to dependent entities
- **Legacy ID:** 124
- **Validation status:** ✅ CONFIRMED VALID
- **Severity / Category:** medium / bug
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.27 |

#### Details and Context
- Hard FK cascade exists, but soft-delete (`deleted_at`) propagation is missing.

#### Recommended Actions
- Implement soft-delete cascade/update policy.

#### Verification Notes (2026-03-21)
**Confirmed:** This is a real design gap.

- **Domain model** (`User.java` lines 831-834): `markDeleted(Instant)` sets `deletedAt` timestamp and calls `touch()` — this is a **soft-delete in memory only**. It does not cascade to dependent entities (matches, conversations, messages, notes, etc.).
- **Database schema** (`SchemaInitializer.java`): All 14+ FK constraints use `ON DELETE CASCADE` — this is **hard-delete cascade** only. If a user row is physically `DELETE`d, dependent rows are removed. But `markDeleted()` doesn't trigger a `DELETE` — it sets a timestamp.
- **Result**: A soft-deleted user retains all their conversations, messages, matches, profile notes, and other data. Dependent tables have no `deleted_at` column and no soft-delete propagation mechanism exists anywhere in the codebase.

This is an intentional design gap — the app uses soft-delete for users but hard-delete cascades for the DB. Whether this needs fixing depends on whether soft-deleted users' data should remain queryable (audit/compliance) or be hidden (privacy).

#### Completion status: ✅ Implemented (2026-03-22 batch)

<details><summary>Evidence snippets</summary>

- `"no `deleted_at` propagation"`
</details>

### V077. Standouts selection triggers immediate navigation
- **Legacy ID:** 126
- **Validation status:** valid
- **Severity / Category:** medium / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.40 |

#### Details and Context
- Selection event instantly navigates, preventing browse-before-commit behavior.

#### Recommended Actions
- Decouple selection from navigation with explicit action button.

<details><summary>Evidence snippets</summary>

- `"immediately navigates"`
</details>

### V078. Storage interface defaults violate efficient contract expectations
- **Legacy ID:** 128
- **Validation status:** valid
- **Severity / Category:** medium / architecture
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 3 > 3.29 |

#### Details and Context
- Defaults for pending requests and matched counterpart IDs load full collections then filter/count.

#### Recommended Actions
- Require efficient overrides for potentially large datasets.

<details><summary>Evidence snippets</summary>

- `"default loads ALL then counts"`
</details>

### V079. UserStorage.findByIds default implementation is inefficient
- **Legacy ID:** 133
- **Validation status:** valid
- **Severity / Category:** medium / performance
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 3 > 3.2 |

#### Details and Context
- Default path loops `get()` per id instead of set-based query.

#### Recommended Actions
- Override with batched retrieval.

<details><summary>Evidence snippets</summary>

- `"findByIds() loops calling get() individually"`
</details>

### V080. Archived Java utility code remains in docs folder
- **Legacy ID:** 135
- **Validation status:** valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `2`          |
| section_reference     | `#1`                                             | PART 4 > 4.1 |

#### Details and Context
- Dead/archived code in repository can confuse discoverability and ownership.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Remove or clearly quarantine archived utility code.

<details><summary>Evidence snippets</summary>

- `"docs/archived-utils/"`
</details>

### V081. CLI case normalization is inconsistent
- **Legacy ID:** 136
- **Validation status:** partially-valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                                                                           |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                                             |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.4 Inconsistent Case Conversion in CLI Input; Part 6: Inconsistencies Summary > Code Patterns |

#### Details and Context
- Different handlers use lower/upper transforms in incompatible ways.

#### Recommended Actions
- Standardize case-insensitive command/input normalization across handlers.

<details><summary>Evidence snippets</summary>

- `".toLowerCase(Locale.ROOT) ... .toUpperCase(Locale.ROOT) - Different!"`
</details>

### V082. Constraint naming convention is inconsistent
- **Legacy ID:** 137
- **Validation status:** valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                                                            |
|-----------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                              |
| section_reference     | `#1`                                                        | Part 4: LOW Priority Issues > 4.3 Inconsistent UNIQUE Constraint Naming; Priority Recommendations > Nice to Have |

#### Details and Context
- Schema mixes naming styles (`uk_` vs `unq_`).

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Standardize unique-constraint naming.

<details><summary>Evidence snippets</summary>

- `"Inconsistent naming convention (`uk_` vs `unq_`)"`
</details>

### V083. Event double-publishing concern downgraded (cross-method duplication concern remains)
- **Legacy ID:** 138
- **Validation status:** partially-valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no
- **Completion status:** 🟠 <span style="color: orange">IN PROGRESS - NOT STARTED YET (a plan exists)</span>

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `2`           |
| section_reference     | `#1`                                             | PART 2 > 2.15 |

#### Details and Context
- Report corrects prior claim: no within-method duplicate event publish.
- Still flags consolidation opportunity across separate methods.

#### Recommended Actions
- Consolidate event publication strategy to single authoritative location.

<details><summary>Evidence snippets</summary>

- `"Events are NOT published twice within the same method"`
</details>

### V084. profile_views has unused AUTO_INCREMENT surrogate column
- **Legacy ID:** 140
- **Validation status:** valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                                                                     |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                                       |
| section_reference     | `#1`                                                        | Part 4: LOW Priority Issues > 4.4 Unnecessary AUTO_INCREMENT ID in profile_views; Priority Recommendations > Nice to Have |

#### Details and Context
- Reported as dead schema weight with no query usage.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Remove unnecessary `AUTO_INCREMENT` id from `profile_views`.

<details><summary>Evidence snippets</summary>

- `"Column is defined but not used by queries"`
</details>

### V085. Storage layer lacks shared query-result caching
- **Legacy ID:** 141
- **Validation status:** valid
- **Severity / Category:** low / performance
- **Mentions (total):** 2
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                                                     |
|-----------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `2`                                                                                                       |
| section_reference     | `#1`                                                        | Part 4: LOW Priority Issues > 4.1 No Caching Layer in Storage; Priority Recommendations > Should Fix Soon |

#### Details and Context
- Report states all storage access goes directly to DB with no caching strategy.

#### Recommended Actions
- Add caching for frequently accessed data.

<details><summary>Evidence snippets</summary>

- `"No caching mechanism anywhere in storage layer. Every query hits database directly"`
</details>

### V086. BaseViewModel nullability pattern is inconsistent for error routing
- **Legacy ID:** 142
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** low / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 4 > 4.7 |

#### Details and Context
- `errorSink` nullable behavior conflicts with expected null-supplier conventions.

#### Recommended Actions
- No action required — nullability is a deliberate design with proper fallback.

#### ❌ False Positive Proof (verified 2026-03-21)
The `errorSink` nullability is a **deliberate null-object pattern with fallback logging**, not an inconsistency:

1. **`BaseViewModel`** (lines 40-46): Constructor accepts nullable `ViewModelErrorSink`, passes it to `AsyncErrorRouter` via lazy supplier `() -> errorSink`.
2. **`AsyncErrorRouter`** (lines 48-59): **Gracefully handles null** — when `onError()` is called:
   ```java
   ViewModelErrorSink sink = sinkSupplier.get();
   if (sink != null) { sink.handleError(...); }
   else { fallbackLogger.accept(...); }  // falls back to logging
   ```
3. **ViewModels not extending `BaseViewModel`** (e.g., `StandoutsViewModel` line 203): Explicitly check `if (errorHandler != null)` before dispatching.
4. **`LoginViewModel`** (lines 78-81): Has `setErrorHandler()` with documented comment: "Intentionally retained for API compatibility."

**This is correct architecture**: nullable errorSink with fallback logging is the standard null-object pattern. Every code path handles the null case gracefully — errors are **never silently lost** because `AsyncErrorRouter` falls back to its `fallbackLogger`.

<details><summary>Evidence snippets</summary>

- `"errorSink can be null"`
</details>

### V087. CLI messaging command parser has limited command/help ergonomics
- **Legacy ID:** 143
- **Validation status:** valid
- **Severity / Category:** low / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                               |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                 |
| section_reference     | `#1`                                                        | Part 4: LOW Priority Issues > 4.5 Messaging Command Parsing Limited |

#### Details and Context
- Only four slash commands recognized; no in-chat help command.

#### Recommended Actions
- Add command discovery/help in messaging CLI.

<details><summary>Evidence snippets</summary>

- `"Only four commands recognized ... No help command"`
</details>

### V088. Compatibility activity thresholds duplicated across services
- **Legacy ID:** 144
- **Validation status:** partially-valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 4 > 4.6 |

#### Details and Context
- Same thresholds appear in multiple classes.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Centralize threshold constants.

<details><summary>Evidence snippets</summary>

- `"Same thresholds exist in `RecommendationService`"`
</details>

### V089. Email regex is narrower than full RFC-valid address space
- **Legacy ID:** 147
- **Validation status:** valid
- **Severity / Category:** low / bug
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                                            |
|-----------------------|-------------------------------------------------------------|----------------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                              |
| section_reference     | `#1`                                                        | Part 3: MEDIUM Priority Issues > 3.10 ValidationService Email Validation Limited |

#### Details and Context
- Pattern is reported to exclude some valid RFC 5322 characters.

#### Recommended Actions
- Broaden email validation strategy (or use robust parser) to accept valid formats.

<details><summary>Evidence snippets</summary>

- `"EMAIL_PATTERN regex doesn't handle all valid email RFC 5322 characters"`
</details>

### V090. Event bus strict handling mode appears unused
- **Legacy ID:** 148
- **Validation status:** valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no
- **Completion status:** ✅ Implemented

#### Report Context
| Type                  | Key                                                         | Value                                                               |
|-----------------------|-------------------------------------------------------------|---------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                 |
| section_reference     | `#1`                                                        | Part 4: LOW Priority Issues > 4.7 HandlerPolicy.REQUIRED Never Used |

#### Details and Context
- All subscriptions reported as BEST_EFFORT, leaving REQUIRED mode unexercised.

#### Recommended Actions
- Define and enforce REQUIRED handlers for critical events or remove dead policy branch.

<details><summary>Evidence snippets</summary>

- `"All actual event subscriptions use `BEST_EFFORT`. No events require strict handling"`
</details>

### V091. Profile completion scoring logic still partially split
- **Legacy ID:** 151
- **Validation status:** valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                         | Value                                 |
|-----------------------|---------------------------------------------|---------------------------------------|
| mention_count_by_file | `STATUS_2026-03-17_By_claude_sonnet_4.6.md` | `1`                                   |
| section_reference     | `#1`                                        | Refactoring Plan Status (Task 1 note) |

#### Details and Context
- Task says scorePreferences still uses standalone logic and does not call scoreCategory.
- Only scoreBasicInfo and scoreLifestyle are currently routed through shared scoreCategory logic.

#### Recommended Actions
- No immediate code change required (historical/resolved context).

<details><summary>Evidence snippets</summary>

- `"scorePreferences() ... still has standalone logic"`
- `"does NOT call scoreCategory()"`
</details>

### V092. Shared interests preview count is hard-coded
- **Legacy ID:** 155
- **Validation status:** valid
- **Severity / Category:** low / maintainability
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value        |
|-----------------------|--------------------------------------------------|--------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`          |
| section_reference     | `#1`                                             | PART 4 > 4.4 |

#### Details and Context
- Display count is embedded constant, reducing configurability.

#### Recommended Actions
- Externalize preview count to config/UI policy.

<details><summary>Evidence snippets</summary>

- `"SHARED_INTERESTS_PREVIEW_COUNT = 3"`
</details>

### V093. Standouts screen navigation target likely incorrect
- **Legacy ID:** 157
- **Validation status:** ❌ FALSE POSITIVE
- **Severity / Category:** low / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                                                  |
|-----------------------|-------------------------------------------------------------|------------------------------------------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                                                    |
| section_reference     | `#1`                                                        | Part 4: LOW Priority Issues > 4.9 StandoutsController Navigation Wrong |

#### Details and Context
- Navigation currently points to profile view; report suspects matching screen target is intended.

#### Recommended Actions
- No action required — `PROFILE_VIEW` is the correct navigation target.

#### ❌ False Positive Proof (verified 2026-03-21)
`StandoutsController.java` `handleStandoutSelected()` (lines 72-79) **correctly navigates to `PROFILE_VIEW`**:

```java
private void handleStandoutSelected(StandoutEntry entry) {
    viewModel.markInteracted(entry);
    NavigationService.getInstance().setNavigationContext(
        NavigationService.ViewType.PROFILE_VIEW, entry.userId());
    NavigationService.getInstance().navigateTo(
        NavigationService.ViewType.PROFILE_VIEW);
}
```

**`PROFILE_VIEW` is semantically correct because:**
1. **UX intent**: Standouts shows "top recommended profiles." Clicking one should **view their profile** (read-only), not enter the swiping/matching flow.
2. **Pattern consistency**: `MatchesController.handleViewProfile()` (lines 663-667) uses the **same** `PROFILE_VIEW` target when viewing a match's profile. `MATCHING` is only used for interactive swiping flows (e.g., `DashboardController.viewDailyPick()`).
3. **`ProfileViewController`** (lines 60-63) explicitly consumes `ViewType.PROFILE_VIEW` context — it loads a specific user ID and displays their read-only profile.

The report confuses "viewing a standout" (→ see their profile) with "matching with a standout" (→ enter swiping mode). These are different UX actions.

<details><summary>Evidence snippets</summary>

- `"Navigates to `PROFILE_VIEW` but should likely navigate to `MATCHING`"`
</details>

### V094. Transaction timeout configurability is missing
- **Legacy ID:** 158
- **Validation status:** valid
- **Severity / Category:** low / build
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                         | Value                                   |
|-----------------------|-------------------------------------------------------------|-----------------------------------------|
| mention_count_by_file | `CODEBASE_INVESTIGATION_REPORT_2026-03-17_By_Minimax2.5.md` | `1`                                     |
| section_reference     | `#1`                                                        | Priority Recommendations > Nice to Have |

#### Details and Context
- Listed as a recommendation item without a corresponding earlier section; indicates config/ops gap.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Add transaction timeout configuration.

<details><summary>Evidence snippets</summary>

- `"Add transaction timeout configuration"`
</details>

### V095. User.isInterestedInEveryone relies on fragile full-set assumption
- **Legacy ID:** 159
- **Validation status:** partially-valid
- **Severity / Category:** low / bug
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                              | Value         |
|-----------------------|--------------------------------------------------|---------------|
| mention_count_by_file | `CODEBASE_AUDIT_REPORT_2026-03-17_By_Qwen3.5.md` | `1`           |
| section_reference     | `#1`                                             | PART 4 > 4.12 |

#### Details and Context
- Behavior depends on complete gender enum membership and could break with future changes.

#### Completion status: ✅ Implemented (2026-03-22 batch)

#### Recommended Actions
- Harden invariant and add regression tests around enum evolution.

<details><summary>Evidence snippets</summary>

- `"Logic Fragile"`
</details>

### V096. ML-powered recommendation capability is not yet present
- **Legacy ID:** 162
- **Validation status:** valid
- **Severity / Category:** unspecified / other
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                              |
|-----------------------|----------------------------------------------------------|------------------------------------|
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `1`                                |
| section_reference     | `#1`                                                     | Recommendations > Long-term Vision |

#### Details and Context
- Machine learning integration is listed as aspirational long-term capability.

#### Recommended Actions
- Integrate machine learning for recommendations.

<details><summary>Evidence snippets</summary>

- `"Long-term Vision: Machine learning integration for recommendations"`
</details>

### V097. Mobile application capability is a stated future gap
- **Legacy ID:** 163
- **Validation status:** partially-valid
- **Severity / Category:** unspecified / ui-ux
- **Mentions (total):** 1
- **Cross-report conflict:** no

#### Report Context
| Type                  | Key                                                      | Value                              |
|-----------------------|----------------------------------------------------------|------------------------------------|
| mention_count_by_file | `COMPREHENSIVE_CODEBASE_ANALYSIS_REPORT_By_Grok_code.md` | `1`                                |
| section_reference     | `#1`                                                     | Recommendations > Long-term Vision |

#### Details and Context
- Roadmap includes mobile app development, implying no current mobile product.

#### Recommended Actions
- Plan and implement mobile application development.

<details><summary>Evidence snippets</summary>

- `"Long-term Vision: Mobile application development"`
</details>

## Invalid / Not Applicable Claims (Renumbered)

Brief list only (no source metadata): claim + exact code-grounded reason.

| New ID | Status        | Claim                                                                          | Why invalid / not applicable                                                                                                                                                                                                                              |
|--------|---------------|--------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| I001   | cannot-verify | MigrationRunner builds SQL with string concatenation                           | `MigrationRunner.isVersionApplied(...)` currently executes `"... WHERE version = " + version`, so this statement is present in current code and cannot be classified as not-valid.                                                                        |
| I002   | cannot-verify | Achievement logic split between AchievementService and ProfileService fallback | `ProfileService.checkAndUnlock/getUnlocked` delegate through `legacyAchievementService()` which creates `DefaultAchievementService`, so split/fallback behavior exists in the current implementation.                                                     |
| I003   | not-valid     | ChatViewModel async pattern diverges from BaseViewModel standard               | `ChatViewModel` uses the same shared async primitives (`ViewModelAsyncScope` + `AsyncErrorRouter` + loading-state consumer) used by `BaseViewModel`.                                                                                                      |
| I004   | cannot-verify | TrustSafetyService accepts nullable CommunicationStorage                       | `TrustSafetyService` stores `communicationStorage` as optional, builder wiring allows null, and methods explicitly guard usage with `if (communicationStorage != null)`.                                                                                  |
| I005   | not-valid     | Unbounded lock/cache growth memory leaks                                       | `RecommendationService` has only delegated service references and no mutable lock/cache collections that grow over time.                                                                                                                                  |
| I006   | cannot-verify | Authorization controls are limited                                             | `RestApiServer` has localhost/rate-limit/scoped-identity guards but is also explicitly documented as intentionally unauthenticated, so this qualitative threshold cannot be resolved statically.                                                          |
| I007   | cannot-verify | Activity metrics lock stripe count may bottleneck at scale                     | `ActivityMetricsService` uses fixed `LOCK_STRIPE_COUNT = 256`, but bottleneck behavior at scale requires runtime load characteristics, not static inspection.                                                                                             |
| I008   | cannot-verify | Global mutable singleton state causes hidden coupling                          | Mutable singletons exist (e.g., `AppSession`, `DatabaseManager`), but proving they cause hidden coupling is architectural judgment rather than a static code fact.                                                                                        |
| I009   | not-valid     | Messaging getMessages error-flow inconsistency (throws vs result record)       | `ConnectionService.getMessages(...)` returns `MessageLoadResult` and `MessagingUseCases.loadConversation(...)` maps failures to `UseCaseResult`, so business-flow handling is result-based.                                                               |
| I010   | not-valid     | REST profile update lacks boundary/range validation                            | `RestApiServer.updateProfile(...)` calls `ProfileUseCases.updateProfile(...)`, which validates bio/birthDate/location/distance/age-range/height through `ValidationService` before save.                                                                  |
| I011   | not-valid     | ChatViewModel listener disposal race                                           | `ChatViewModel.dispose()` removes `selectionListener` and disposes async scope, and UI mutation paths additionally gate on disposal/selected-conversation checks.                                                                                         |
| I012   | cannot-verify | Deprecated User APIs have no explicit removal timeline                         | Deprecated methods in `User` already carry explicit `@Deprecated(since = "2026-03", forRemoval = false)`, so whether this constitutes a missing removal timeline is interpretation-dependent.                                                             |
| I013   | not-valid     | User activate/pause/ban transitions do not consistently update timestamps      | `User.activate()`, `pause()`, and `ban()` each call `touch()` on state change, and `touch()` updates `updatedAt` via `AppClock.now()`.                                                                                                                    |
| I014   | not-valid     | No offline mode for critical features                                          | Core execution is local-first (`DatabaseManager` defaults to embedded `jdbc:h2:./data/dating` with in-process services), so critical flows do not depend on external network availability.                                                                |
| I015   | cannot-verify | API/project documentation is not sufficiently comprehensive                    | Documentation comprehensiveness is not decidable from `src/main/java`, `src/test/java`, and `pom.xml` alone.                                                                                                                                              |
| I016   | not-valid     | CSV serialization fragility and queryability limits                            | Current multi-value profile persistence is normalized in `JdbiUserStorage` (`user_photos`, `user_interests`, `user_interested_in`, `user_db_*`) rather than active CSV-backed storage.                                                                    |
| I017   | cannot-verify | Internationalization locale support is limited                                 | The codebase has ResourceBundle-based i18n infrastructure, and the degree of 'limited' support depends on required locale targets, not static structure alone.                                                                                            |
| I018   | cannot-verify | Connection pool maximum lifetime not configured                                | `DatabaseManager` configures Hikari pool size/idle/timeout but does not set `maxLifetime`; whether that is problematic depends on operational constraints and defaults.                                                                                   |
| I019   | not-valid     | Dashboard notifications/unread counts unwired                                  | `DashboardViewModel` loads unread/pending/notification counts from `ConnectionService` and publishes bound properties consumed by `DashboardController` badge logic.                                                                                      |
| I020   | not-valid     | Messaging lastActiveAt not updated concern                                     | Messaging send flow updates conversation activity with `updateConversationLastMessageAt(...)`, and there is no `lastActiveAt` field in `ConnectionService`/`ConnectionModels` to update.                                                                  |
| I021   | cannot-verify | Missing security headers in API hardening priorities                           | `RestApiServer` does not define explicit security-response-header middleware, but 'hardening priorities' is policy-level and not directly encoded as a static source invariant.                                                                           |
| I022   | not-valid     | Spurious user save in sendMessage path                                         | `ConnectionService.sendMessage(...)` reads users but never calls `userStorage.save(sender)`; it persists only conversation/message artifacts through `CommunicationStorage`.                                                                              |
| I023   | not-valid     | Standouts retention/cleanup path concern                                       | Standout cleanup exists in current code: `ActivityMetricsService.runCleanup()` calls `deleteExpiredStandouts(...)`, `JdbiMetricsStorage` executes `DELETE FROM standouts WHERE created_at < :cutoff`, and `ApplicationStartup` starts `CleanupScheduler`. |
| I024   | not-valid     | ViewModel/threading race-condition test gap                                    | Current tests explicitly cover ViewModel race/threading scenarios (for example `DashboardViewModelTest` concurrent refresh, `ChatViewModelTest` stale-load skipping, and `MatchesViewModelTest` concurrent offset-reset race).                            |
| I025   | cannot-verify | Composite index coverage is incomplete for key access patterns                 | Schema defines multiple composite indexes, but this claim depends on unspecified 'key access patterns' and workload/query-plan evidence that static code alone does not provide.                                                                          |
| I026   | cannot-verify | Conversation query shape prevents efficient index use                          | Whether the `getConversationsFor(...)` predicate/order shape is inefficient requires runtime EXPLAIN/query-plan evidence, which cannot be concluded from static source only.                                                                              |
| I027   | not-valid     | Dashboard nested subscription pattern may leak listeners                       | Dashboard listeners are registered via `addSubscription(...)` and are centrally disposed by `BaseController.cleanup()`, so a persistent listener-leak pattern is not present in current code.                                                             |
| I028   | cannot-verify | DatabaseManager test scope is too narrow                                       | Test-scope adequacy is a qualitative judgment; static inspection can show existing tests (`DatabaseManagerThreadSafetyTest`) but cannot prove a 'too narrow' threshold objectively.                                                                       |
| I029   | not-valid     | Friend-request index column order is suboptimal                                | Current friend-request indexes align with current queries: `(from_user_id,to_user_id,status)` for between-users pending lookup and `(to_user_id,status)` for inbox/count pending lookups.                                                                 |
| I030   | cannot-verify | JDBI SQL redundancy and mapper/interface bloat                                 | This is an architectural/style assessment, not a binary static-correctness fact that can be definitively proven or refuted from source alone.                                                                                                             |
| I031   | cannot-verify | Login flow auto-fills defaults without explicit user consent                   | `LoginViewModel.login()` does auto-complete incomplete profiles, but whether that violates 'explicit user consent' is a product/policy interpretation rather than a statically checkable code contract.                                                   |
| I032   | not-valid     | Messaging pagination parameter validation missing                              | Pagination validation is present: `MessagingUseCases` normalizes limit/offset and `ConnectionService.getMessages/getConversations` reject invalid limits and negative offsets.                                                                            |
| I033   | cannot-verify | Multiple subsystems have partial edge-case test coverage                       | This is a broad, qualitative coverage claim across subsystems and cannot be conclusively proven or refuted by static code review alone.                                                                                                                   |
| I034   | not-valid     | Preferences slider labels can display stale values                             | `PreferencesController` updates labels at initialization and on slider/value changes (`updateAgeLabels()`), so stale slider labels are not supported by the current binding logic.                                                                        |
| I035   | not-valid     | Profile unsaved-changes UX gap when save validation fails                      | `ProfileController.handleSave()` blocks invalid saves via `validateProfileForm()`, shows explicit failure status, and keeps unsaved-change confirmation (`confirmDiscardUnsavedChanges`) for navigation.                                                  |
| I036   | not-valid     | Redundant nested transactions in JdbiUserStorage                               | `JdbiUserStorage.save(...)` uses one transaction and calls handle-based private helpers, so it does not nest separate transactions in that save path.                                                                                                     |
| I037   | not-valid     | RelationshipTransitionServiceTest used removed accessors                       | Current `RelationshipTransitionServiceTest` uses existing record/accessor APIs (for example `friendRequest().id()`, `fromUserId()`, `status()`), not removed members.                                                                                     |
| I038   | not-valid     | Social storage integration tests missing                                       | Social storage integration coverage exists in `src/test/java/datingapp/storage/jdbi/JdbiCommunicationStorageSocialTest.java`.                                                                                                                             |
| I039   | not-valid     | Some CLI flows skip active-session state checks                                | CLI dispatch enforces login at menu level (`Main` checks `requiresLogin`) and handlers also guard with `CliTextAndInput.requireLogin(...)`, so active-session checks are present.                                                                         |
| I040   | not-valid     | Standouts service/storage dedicated tests missing                              | Dedicated standouts tests are present (`StandoutsServiceTest`, `StandoutsViewModelTest`, `StandoutsControllerTest`) with additional standouts cleanup coverage in `CleanupServiceTest`.                                                                   |
| I041   | not-valid     | Swipe transition gate uses non-thread-safe primitive flag                      | The gate flag in `MatchingController` is used from JavaFX event/animation callbacks on the UI thread, so cross-thread primitive-safety concerns are not evidenced by current code path.                                                                   |
| I042   | cannot-verify | Typing indicator animations continue when hidden                               | `TypingIndicator.hide()` only toggles visibility/managed state, and whether hidden-node animations continue consuming runtime requires JavaFX runtime behavior verification beyond static source alone.                                                   |
| I043   | not-valid     | Unbounded conversation query in getConversations                               | `JdbiConnectionStorage.getConversationsFor(...)` is explicitly bounded with `LIMIT :limit OFFSET :offset`, and callers pass validated pagination.                                                                                                         |
| I044   | not-valid     | Undo-state primary lookup by user_id lacks index                               | `undo_states.user_id` is declared `PRIMARY KEY` in schema, which provides an index for primary lookups.                                                                                                                                                   |
| I045   | not-valid     | ViewModelFactory logout reset order can race listener callbacks                | `ViewModelFactory.reset()` is synchronized and unbinds the session listener before disposing/rebinding (`unbindSessionListener()` -> `disposeCachedViewModels()` -> `initializeSessionBinding()`).                                                        |
| I046   | not-valid     | Graceful-exit capability exists but is under-exposed in UX                     | Graceful exit is surfaced in CLI (`MatchingHandler` `(G)raceful Exit`), JavaFX (`MatchesController` `handleGracefulExit`), and REST (`/api/users/{id}/relationships/{targetId}/graceful-exit` in `RestApiServer`).                                        |
| I047   | not-valid     | Potential unused import in MainMenuRegistry                                    | All `MainMenuRegistry` imports are referenced in code (`LinkedHashMap`, `List`, `Map`, `Objects`, `Optional`, `Function`).                                                                                                                                |
| I048   | not-valid     | Read-receipt UI surfacing gap                                                  | `ChatController.MessageListCell` computes read state from `Conversation.getLastReadAt(...)` and explicitly toggles `readReceiptIcon` visibility/management.                                                                                               |
| I049   | not-valid     | REST API missing claim (historical/stale)                                      | `src/main/java/datingapp/app/api/RestApiServer.java` exists and registers health/user/matching/social/messaging/profile-note REST route modules.                                                                                                          |
| I050   | not-valid     | Standout generation may ignore/under-validate configured limits                | `DefaultStandoutService.generateStandouts()` applies `.limit(config.validation().maxStandouts())`, and `AppConfigValidator.validateValidation()` enforces `maxStandouts` range `[1,100]`.                                                                 |
| I051   | cannot-verify | REST API rate limiter is local in-memory only                                  | Code shows an in-process `LocalRateLimiter` in `RestApiServer`, but the claim’s absolute `only` scope cannot be conclusively established from repository code without deployment-layer context.                                                           |
| I052   | cannot-verify | Thread-safety risk from mutable shared user/session state                      | `AppSession` uses `AtomicReference`/`CopyOnWriteArrayList`, and proving an actual race risk requires concrete concurrent usage behavior not derivable statically.                                                                                         |
| I053   | cannot-verify | Storage integration test coverage gap                                          | Storage integration tests are present under `src/test/java/datingapp/storage/jdbi`, but whether coverage is a `gap` depends on external adequacy criteria absent from source/pom.                                                                         |
| I054   | not-valid     | CLI transition failure logging does not explain failure cause to users         | CLI transition paths print user-facing failure causes via `result.error().message()` (e.g., `MatchingHandler.logTransitionResult` and related handlers).                                                                                                  |
| I055   | not-valid     | REST error mapping uses generic status handling                                | `RestApiServer.handleUseCaseFailure()` maps typed errors to specific statuses (400/403/404/409/500), and exception handlers also explicitly map 429/400/403/404/409/500.                                                                                  |
| I056   | cannot-verify | Runtime exceptions still used in places instead of result types                | The codebase mixes `UseCaseResult` flows with guardrail runtime exceptions, and proving `instead of result types` as a defect needs a strict policy baseline not encoded in source/pom.                                                                   |
| I057   | cannot-verify | Environment override coverage is incomplete for config surface                 | `ApplicationStartup.applyEnvironmentOverrides()` intentionally defines an explicit env-var subset, and `incomplete for config surface` is a policy judgment not statically decidable.                                                                     |
| I058   | cannot-verify | Known flaky JavaFX test due to thread race                                     | A `known flaky` thread-race claim requires repeated runtime test evidence; static inspection of `src/test/java` and `pom.xml` cannot prove flakiness.                                                                                                     |
| I059   | not-valid     | Notification ordering query lacks supporting index on created_at               | `SchemaInitializer.createAdditionalIndexes()` creates `idx_notifications_created ON notifications(created_at DESC)`, matching notification queries ordered by `created_at DESC`.                                                                          |
| I060   | cannot-verify | Social notifications read-state styling updates with visual lag                | Visual `lag` is a runtime UX behavior and cannot be proven/refuted from static Java source alone.                                                                                                                                                         |
| I061   | cannot-verify | Limited adoption of modern Java concurrency features                           | `Limited adoption` is an architectural maturity judgment with no objective threshold defined in `src/main/java`, `src/test/java`, or `pom.xml`.                                                                                                           |
| I062   | cannot-verify | ConnectionModels record constructors are verbose                               | `Verbose` constructor style in `ConnectionModels` is a subjective design assessment, not a statically falsifiable correctness claim.                                                                                                                      |
| I063   | cannot-verify | Dealbreaker builder range validation clarity is weak                           | `Clarity is weak` is subjective; `MatchPreferences.Dealbreakers` already contains explicit range checks and error messages, but clarity quality is not objectively testable from static code.                                                             |
| I064   | cannot-verify | No explicit active gaps/issues documented                                      | This is a documentation/process assertion, while requested validation scope is limited to `src/main/java`, `src/test/java`, and `pom.xml`.                                                                                                                |
| I065   | cannot-verify | Microservices readiness is identified as a future architectural gap            | This is a roadmap-level architectural statement and is not statically verifiable from current source and build files alone.                                                                                                                               |
