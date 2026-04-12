# Issue Manifest for the Codebase Review Plan Set

> **Rule:** Every issue ID from `CODEBASE_REVIEW.md` must have exactly one home.

---

| Issue ID | Home                                                             | Wave | Status type | Note                                                  |
|----------|------------------------------------------------------------------|-----:|-------------|-------------------------------------------------------|
| 1.1      | P13 — deferred backlog                                           |    5 | deferred    | Explicitly marked ignore/defer for now                |
| 1.2      | P11 — CLI handler seams and shared helpers                       |    4 | executable  | CLI formatting ownership                              |
| 1.3      | P08 — REST boundary, DTOs, and request guards                    |    4 | executable  | REST adapter organization                             |
| 2.1      | P01 — matching scoring and recommendation contracts              |    1 | executable  | Shared scoring aggregation seam                       |
| 2.2      | P02 — swipe concurrency and side-effect sequencing               |    1 | executable  | Swipe-flow validation contract                        |
| 2.3      | P09 — database runtime lifecycle and configuration contracts     |    2 | executable  | Runtime password precedence                           |
| 3.1      | P11 — CLI handler seams and shared helpers                       |    4 | executable  | Shared CLI index parsing                              |
| 3.2      | P08 — REST boundary, DTOs, and request guards                    |    4 | executable  | Shared UUID parsing seam                              |
| 3.3      | P10 — JDBI storage plumbing, serialization, and batching         |    2 | executable  | Storage JSON policy ownership                         |
| 4.1      | P08 — REST boundary, DTOs, and request guards                    |    4 | executable  | `RestApiServer` responsibility split                  |
| 4.2      | P09B — migration and schema safety                               |    2 | executable  | `MigrationRunner` decomposition                       |
| 4.3      | P04 — safety and verification boundary contracts                 |    1 | executable  | `TrustSafetyService` boundary split                   |
| 5.1      | P04 — safety and verification boundary contracts                 |    1 | executable  | Canonical verification path in UI                     |
| 5.2      | P06 — ViewModel seams and async-policy standardization           |    3 | executable  | UI dependency on nested matching utility              |
| 5.3      | P05 — chat ViewModel async and state contracts                   |    3 | executable  | Read-only observable state                            |
| 6.1      | P07 — controllers, navigation, theming, and UI micro-performance |    3 | executable  | Navigation seam for controllers                       |
| 6.2      | P12 — service construction and contract clarity                  |    2 | executable  | Composition-root builder surface                      |
| 6.3      | P13 — deferred backlog                                           |    5 | deferred    | Broad cross-cutting boundary cleanup                  |
| 7.1      | P03 — user/profile invariants and copy semantics                 |    1 | executable  | `User` responsibility and cohesion                    |
| 7.2      | P13 — deferred backlog                                           |    5 | deferred    | Intentional compatibility baggage to preserve for now |
| 7.3      | P03 — user/profile invariants and copy semantics                 |    1 | executable  | `StorageBuilder` completeness                         |
| 8.1      | P06 — ViewModel seams and async-policy standardization           |    3 | executable  | ViewModel construction consistency                    |
| 8.2      | P07 — controllers, navigation, theming, and UI micro-performance |    3 | executable  | CSS vs inline style ownership                         |
| 8.3      | P08 — REST boundary, DTOs, and request guards                    |    4 | executable  | Explicit route-result signaling                       |
| 9.1      | P12 — service construction and contract clarity                  |    2 | executable  | `ActivityMetricsService` construction modes           |
| 9.2      | P04B — relationship, social, and messaging workflow contracts    |    1 | executable  | Typed denial reasons in connection workflow           |
| 10.1     | P07 — controllers, navigation, theming, and UI micro-performance |    3 | executable  | `BaseController.cleanup()` test seam                  |
| 10.2     | P06 — ViewModel seams and async-policy standardization           |    3 | executable  | Async behavior should be policy-driven                |
| 10.3     | P01 — matching scoring and recommendation contracts              |    1 | executable  | Deterministic standout timestamp contract             |
| 10.4     | P05 — chat ViewModel async and state contracts                   |    3 | executable  | Session refresh semantics                             |
| 10.5     | P05 — chat ViewModel async and state contracts                   |    3 | executable  | Null-user reset semantics                             |
| 11.1     | P01 — matching scoring and recommendation contracts              |    1 | executable  | Builder precedence and auto-fill behavior             |
| 11.2     | P03 — user/profile invariants and copy semantics                 |    1 | executable  | Canonical copy path ownership                         |
| 11.3     | P03 — user/profile invariants and copy semantics                 |    1 | executable  | Profile stats failure contract                        |
| 11.4     | P02 — swipe concurrency and side-effect sequencing               |    1 | executable  | Shared concurrency contract                           |
| 11.5     | P03 — user/profile invariants and copy semantics                 |    1 | executable  | Preserve raw location state in copies                 |
| 11.6     | P09 — database runtime lifecycle and configuration contracts     |    2 | executable  | `resetInstance()` contract mismatch                   |
| 11.7     | P09B — migration and schema safety                               |    2 | executable  | Irreversible V3 schema cleanup                        |
| 12.1     | P01 — matching scoring and recommendation contracts              |    1 | executable  | Matching fallback semantics                           |
| 12.2     | P13 — deferred backlog                                           |    5 | deferred    | Freshness-first cache no-op is intentional for now    |
| 12.3     | P11 — CLI handler seams and shared helpers                       |    4 | executable  | Remove trivial wrapper indirection                    |
| 13.1     | P04B — relationship, social, and messaging workflow contracts    |    1 | executable  | Social use-case construction modes                    |
| 13.2     | P02 — swipe concurrency and side-effect sequencing               |    1 | executable  | Best-effort storage default isolation contract        |
| 13.3     | P03 — user/profile invariants and copy semantics                 |    1 | executable  | Domain mutator invariants                             |
| 13.4     | P01 — matching scoring and recommendation contracts              |    1 | executable  | Archive vs delete behavior                            |
| 13.5     | P01 — matching scoring and recommendation contracts              |    1 | executable  | Daily-status seam consistency                         |
| 13.6     | P09 — database runtime lifecycle and configuration contracts     |    2 | executable  | Startup-only pool settings contract                   |
| 14.1     | P10 — JDBI storage plumbing, serialization, and batching         |    2 | executable  | Shared dialect resolution                             |
| 14.2     | P09 — database runtime lifecycle and configuration contracts     |    2 | executable  | Static vs instance ownership                          |
| 14.3     | P06 — ViewModel seams and async-policy standardization           |    3 | executable  | Extract nested geo utility seam                       |
| 15.1     | P08 — REST boundary, DTOs, and request guards                    |    4 | executable  | DTO discoverability split                             |
| 15.2     | P11 — CLI handler seams and shared helpers                       |    4 | executable  | CLI helper decomposition                              |
| 16.1     | P12 — service construction and contract clarity                  |    2 | executable  | `ActivityMetricsService` constructor docs             |
| 16.2     | P12 — service construction and contract clarity                  |    2 | executable  | Validation message tolerance clarity                  |
| 16.3     | P03 — user/profile invariants and copy semantics                 |    1 | executable  | `deleteAccount()` side-effect contract                |
| 16.4     | P09 — database runtime lifecycle and configuration contracts     |    2 | executable  | Update `DatabaseManager` runtime docs                 |
| 16.5     | P09B — migration and schema safety                               |    2 | executable  | Shared pair-ID length constant                        |
| 17.1     | P04B — relationship, social, and messaging workflow contracts    |    1 | executable  | Explicit messaging event semantics                    |
| 17.2     | P05 — chat ViewModel async and state contracts                   |    3 | executable  | Visible failure state in chat                         |
| 17.3     | P08 — REST boundary, DTOs, and request guards                    |    4 | executable  | Monotonic rate-limiter clock                          |
| 17.4     | P13 — deferred backlog                                           |    5 | deferred    | Future-only throttle mapping gap                      |
| 17.5     | P05 — chat ViewModel async and state contracts                   |    3 | executable  | Profile-note token race handling                      |
| 17.6     | P05 — chat ViewModel async and state contracts                   |    3 | executable  | Async-send acceptance vs completion                   |
| 17.7     | P02 — swipe concurrency and side-effect sequencing               |    1 | executable  | One side-effect sequencing rule                       |
| 17.8     | P05 — chat ViewModel async and state contracts                   |    3 | executable  | Explicit background-load failure state                |
| 17.9     | P03 — user/profile invariants and copy semantics                 |    1 | executable  | Preserve verification timeline                        |
| 17.10    | P09B — migration and schema safety                               |    2 | executable  | Bootstrap timeout/atomicity parity                    |
| 18.1     | P13 — deferred backlog                                           |    5 | deferred    | Future production-adapter guardrail                   |
| 18.2     | P10 — JDBI storage plumbing, serialization, and batching         |    2 | executable  | Batch standout persistence                            |
| 18.3     | P07 — controllers, navigation, theming, and UI micro-performance |    3 | executable  | Hover-animation allocation hot path                   |
| 18.4     | P01 — matching scoring and recommendation contracts              |    1 | executable  | Scoring hot-path reuse                                |
| 18.5     | P10 — JDBI storage plumbing, serialization, and batching         |    2 | executable  | UTC-calendar allocation reuse                         |
| 18.6     | P05 — chat ViewModel async and state contracts                   |    3 | executable  | Polling diff/UI batching                              |
| 18.7     | P02 — swipe concurrency and side-effect sequencing               |    1 | executable  | Reduce lock scope safely                              |
