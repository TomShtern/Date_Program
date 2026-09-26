# Plan 13: Deferred Backlog and Not-Now Items

> **Date:** 2026-04-10
> **Wave:** 5
> **Priority:** Track only
> **Execution mode:** Non-executable ledger; revisit under explicit trigger only
> **Status:** Planned backlog ledger

---

## Objective

Keep all review findings that should **not** become active implementation work right now in one explicit ledger, with the reason for deferral and the trigger that should cause the item to be re-opened later.

## Issues held here

| Issue ID | Summary                                                                     | Why it is deferred now                                                                                      | Revisit trigger                                                                       |
|----------|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------|
| 1.1      | Large files still mix responsibilities                                      | The review explicitly says to ignore/defer until adjacent feature work forces a split                       | Re-open when a listed hot file must change for product work or grows again            |
| 6.3      | Direct `core` imports still create broad coupling outside core              | Too cross-cutting for a safe seam-local pass right now                                                      | Re-open after P04, P06, and P12 if direct-core calls still bypass real use-case seams |
| 7.2      | `RecommendationService` still mixes coordination with compatibility baggage | Current repo posture intentionally keeps the façade and compatibility records                               | Re-open only if P01 makes the adapter/mapping layer the clear next bottleneck         |
| 12.2     | `CandidateFinder.invalidateCacheFor` / `clearCache` are misleading no-ops   | Current freshness-first behavior is intentional and should not be “fixed” accidentally                      | Re-open only if a real mutable cache is introduced or the public API becomes harmful  |
| 17.4     | No explicit future `RATE_LIMITED` mapping in `handleUseCaseFailure()`       | Future-gap only; current throttling is handled earlier by request guards                                    | Re-open if the use-case layer begins emitting a throttle-specific error code          |
| 18.1     | Default `findCandidates()` fallback loads active users in memory            | The current runtime adapter overrides it; this is a future adapter guardrail, not a present runtime failure | Re-open when adding a new production-grade storage adapter                            |

## Rules for future promotion out of the backlog

An item may move out of this ledger only when one of the following becomes true:

1. it blocks an active executable plan
2. a new production path makes the issue operationally real
3. the code no longer matches the “preserve for now” rationale documented here
4. a user explicitly asks to prioritize the deferred work

## Coordinator guidance

- Do not silently absorb backlog items into active plans.
- If one must move, update `ISSUE_MANIFEST.md` first so the ownership change is explicit.
- If a deferred item becomes active because of adjacent work, create a new dedicated executable plan or add it to the nearest seam-owned plan with a documented rationale.

## Notes

This file is intentionally not a dumping ground for unfinished thinking.

Every item here was reviewed and placed here on purpose because it is:

- explicitly deferred by the review,
- broad enough to cause harmful churn if done too early,
- intentionally preserved behavior for now, or
- only relevant to a future extension point.