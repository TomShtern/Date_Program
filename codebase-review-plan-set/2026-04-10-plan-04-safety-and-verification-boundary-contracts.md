# Plan 04: Safety and Verification Boundary Contracts

> **Date:** 2026-04-10
> **Wave:** 1
> **Priority:** High
> **Parallel-safe with:** none recommended
> **Must run after:** P03
> **Status:** Planned

---

## Objective

Clarify the safety and verification boundary so verification work has one canonical application seam, moderation stays inside the trust/safety boundary, and UI callers stop bypassing the intended path.

## Issues addressed

| Issue ID | Summary                                                                        |
|----------|--------------------------------------------------------------------------------|
| 4.3      | `TrustSafetyService` spans verification, moderation, and relationship controls |
| 5.1      | `SafetyViewModel` retains a fallback that bypasses `VerificationUseCases`      |

## Primary source files and seams

- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`

## Boundary contract

### Primary edit owners

- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java`
- `src/main/java/datingapp/ui/viewmodel/SafetyViewModel.java`

### Supporting read-only seams

- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`

### Escalate instead of expanding scope if

- the change requires editing `MessagingUseCases.java`
- the change requires typed relationship-workflow denial reasons
- the change requires changing REST-side workflow mapping instead of the safety/verification seam itself

## Primary verification slice

- `src/test/java/datingapp/core/TrustSafetyServiceTest.java`
- `src/test/java/datingapp/core/matching/TrustSafetyServiceSecurityTest.java`
- `src/test/java/datingapp/core/matching/TrustSafetyServiceAuditTest.java`
- `src/test/java/datingapp/ui/viewmodel/SafetyViewModelTest.java`

## Execution slices

### Slice A — split the safety surface by responsibility

- keep moderation/blocking/relationship-safety orchestration in `TrustSafetyService` during Slice A while moving verification-code ownership to `VerificationUseCases`; any relationship-control workflow contract changes are deferred to P04B
- ensure verification logic has one canonical use-case seam for downstream consumers

### Slice B — remove bypass paths from the UI

- make the normal `SafetyViewModel` path go through `VerificationUseCases`
- keep compatibility shims only if there are still non-canonical callers that cannot be migrated in the same pass

### Slice C — keep the seam narrow while migrating callers

- keep compatibility shims only where migration cannot happen safely in the same pass
- do not let the plan widen into connection or messaging contract cleanup

## Dependencies and orchestration notes

- Run after P03 because verification-related user-state semantics should already be stable.
- Run before P04B and P05 so the social/messaging and chat layers inherit the final verification boundary.
- Treat this as a narrow boundary plan, not a general workflow cleanup pass.

## Out of scope

- relationship and messaging workflow contracts (P04B); P04A may touch only the existing `TrustSafetyService` orchestration hooks needed to separate verification ownership, not the workflow contracts themselves
- chat-specific ViewModel UX and polling fixes (P05)
- REST-side error mapping for these workflows (P08)
- deferred cross-cutting core-import cleanup (P13)
