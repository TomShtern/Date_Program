# First Execution Recommendation for the Codebase Review Plan Set

> **Purpose:** Give future execution agents a safe starting point: which plan to run first, which plans must wait, and which verification slice should be used for the first-wave plans.

---

## Executive recommendation

### Start with `P01`

The recommended first implementation plan is:

- `2026-04-10-plan-01-matching-scoring-and-recommendation-contracts.md`

### Why `P01` should go first

1. It targets the highest-value hotspot still inside the `CODEBASE_REVIEW.md` issue bank.
2. It stabilizes the `MatchingUseCases` contract before `P02` tries to refine concurrency and side-effect sequencing.
3. Its verification slice is strong and already concentrated around the matching seam.
4. It reduces the risk that `P02` turns into “fix concurrency and redefine business semantics at the same time.”

### What not to do first

Do **not** start with:

- `P05` — user-visible, but it depends on settled workflow semantics from `P04B`
- `P08` — outer transport layer; it should mirror a settled interior
- `P11` — CLI adapters should come after copy/workflow semantics stop moving
- `P09B` — migration safety before `P09` risks mixing lifecycle and schema concerns again

## Recommended first-pass execution order

For the **first execution pass**, use a strict serial order even where later safe parallelism might exist.

1. `P01` — matching scoring and recommendation contracts
2. `P02` — swipe concurrency and side-effect sequencing
3. `P03` — user/profile invariants and copy semantics
4. `P04` — safety and verification boundary contracts
5. `P04B` — relationship, social, and messaging workflow contracts

After that, pause and review the seam stability before opening Wave 2.

## Wave definitions

- Wave 1 = `P01`, `P02`, `P03`, `P04`, `P04B`
- Wave 2 = `P09`, `P09B`, `P10`, `P12`
- Wave 3 = `P05`, `P06`, `P07`
- Wave 4 = `P08`, `P11`
- Wave 5 = `P13`

## Explicit wait list

| Plan   | Wait until                | Gate type   | Why                                                                                  |
|--------|---------------------------|-------------|--------------------------------------------------------------------------------------|
| `P02`  | `P01` complete            | Hard        | It depends on the scoring/result and `MatchingUseCases` contract being settled first |
| `P03`  | `P02` complete            | Recommended | Finishing the matching campaign first reduces context churn across the top hotspot   |
| `P04`  | `P03` complete            | Hard        | Verification behavior should inherit final `User` semantics                          |
| `P04B` | `P04` complete            | Hard        | Workflow semantics should inherit the settled verification boundary                  |
| `P09`  | Wave 1 complete           | Hard        | Runtime/storage work should not begin while core workflow semantics are still moving |
| `P09B` | `P09` complete            | Hard        | Migration safety needs the runtime lifecycle/configuration contract first            |
| `P10`  | `P09` and `P09B` complete | Hard        | JDBI cleanup should consume settled runtime/bootstrap contracts                      |
| `P12`  | Wave 1 complete           | Hard        | Builder/contract clarity should describe stabilized seams, not moving targets        |
| `P05`  | Wave 2 complete           | Hard        | Chat ViewModel should consume settled workflow and storage/runtime behavior          |
| `P06`  | `P05` complete            | Recommended | Factory/async-policy cleanup is easier after the most volatile chat seam stabilizes  |
| `P07`  | `P05` and `P06` complete  | Recommended | Controller/navigation work is safer after ViewModel conventions settle               |
| `P08`  | Waves 1–3 complete        | Hard        | REST should mirror the settled interior, not define it early                         |
| `P11`  | `P03` and `P04B` complete | Hard        | CLI handlers should inherit settled copy and workflow semantics                      |
| `P13`  | Never auto-start          | Hard        | It is a ledger, not a normal execution plan                                          |

## First-wave verification matrix

Use the following **recommended verification slices** for the first-wave plans.

### `P01` — matching scoring and recommendation contracts

**Must-pass slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=RecommendationServiceTest,DefaultCompatibilityCalculatorTest,StandoutsServiceTest,MatchingUseCasesTest test
```

**Secondary confidence slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=MatchQualityServiceTest,MatchingFlowIntegrationTest test
```

**Only if touched unexpectedly**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=MatchingServiceTest test
```

**Stop instead of widening if**

- `MatchingService.java` must be edited for lock scope or transaction semantics
- `InteractionStorage` or `JdbiMatchmakingStorage` becomes a required edit

### `P02` — swipe concurrency and side-effect sequencing

**Must-pass slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=MatchingServiceTest,MatchingTransactionTest,InteractionStorageAtomicityTest,JdbiMatchmakingStorageTransitionAtomicityTest test
```

**Secondary confidence slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=MatchingUseCasesTest,MatchingFlowIntegrationTest test
```

**Only if touched unexpectedly**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=RecommendationServiceTest,StandoutsServiceTest test
```

**Stop instead of widening if**

- `MatchingUseCases.java` needs semantic edits instead of black-box verification
- scoring/result-shape behavior starts changing again

### `P03` — user/profile invariants and copy semantics

**Must-pass slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=UserTest,ProfileMutationUseCasesTest,ProfileUseCasesTest test
```

**Secondary confidence slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ProfileNormalizationSupportTest,ProfileViewModelTest test
```

**Only if touched unexpectedly**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ProfileHandlerTest test
```

**Stop instead of widening if**

- the change starts requiring controller-level fixes
- the safety/verification workflow itself needs to move in the same pass

### `P04` — safety and verification boundary contracts

**Must-pass slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=TrustSafetyServiceTest,TrustSafetyServiceSecurityTest,SafetyViewModelTest test
```

**Secondary confidence slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=TrustSafetyServiceAuditTest test
```

**Only if touched unexpectedly**

None. If `ConnectionService`, `MessagingUseCases`, or REST workflow mapping need edits, stop and move that work into `P04B` or `P08`.

### `P04B` — relationship, social, and messaging workflow contracts

**Must-pass slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ConnectionServiceTest,SocialUseCasesTest,MessagingUseCasesTest test
```

**Secondary confidence slice**

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=ConnectionServiceTransitionTest,ConnectionServiceAtomicityTest,JdbiCommunicationStorageSocialTest test
```

**Only if touched unexpectedly**

None. If `ChatViewModel.java` or REST-side transport/result mapping needs to move in the same pass, stop and hand that work to `P05` or `P08`.

## Recommended execution posture for the first pass

For the first pass through the plan set:

- use **one coordinator**
- use **one edit owner**
- optionally use **one read-only helper** for usages/test-surface lookup
- do **not** parallelize Wave 1

The point of the first pass is to stabilize the interior seams, not to maximize throughput.

## When to reconsider this recommendation

Revisit this recommendation only if one of these becomes true:

1. `P01` reveals a much larger hidden seam and has to be split again
2. `P03` becomes clearly lower-risk and higher-leverage than the matching pair after source inspection
3. user-driven product priorities override the default interior-first cleanup order
4. a newly discovered failing verification slice shows a different seam is actually the more urgent blocker
