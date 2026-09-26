# Execution Red Flags Checklist for the Codebase Review Plan Set

> **Purpose:** Give future execution agents a fast stop/go checklist for detecting when a plan is drifting into an adjacent seam before the work turns into a dumpster fire.

---

## How to use this checklist

Read this checklist **before starting a plan** and again:

- after the first source read
- before the first edit
- after the first failing verification run
- before widening scope to “just fix one more thing”

If any item below trips, the default action is:

1. stop editing
2. compare the planned scope to `ISSUE_MANIFEST.md`
3. decide whether to narrow the fix or open a follow-on plan

## Universal stop signals

### 1. A hot file from another plan needs editing

If the implementation suddenly requires editing one of these files and the current plan does not own it, stop:

- `MatchingUseCases.java`
- `MatchingService.java`
- `User.java`
- `TrustSafetyService.java`
- `ConnectionService.java`
- `SocialUseCases.java`
- `MessagingUseCases.java`
- `ChatViewModel.java`
- `ViewModelFactory.java`
- `NavigationService.java`
- `RestApiServer.java`
- `DatabaseManager.java`
- `MigrationRunner.java`
- `SchemaInitializer.java`
- `StorageFactory.java`
- `ServiceRegistry.java`

**Default action:** re-check the plan’s boundary contract. If the file is only needed for black-box verification, keep it read-only. If it must be edited, stop and amend the owning plan instead of silently co-owning it.

### 2. The plan starts changing business semantics and infrastructure semantics at the same time

Examples:

- scoring and concurrency in one pass
- verification ownership and messaging semantics in one pass
- runtime lifecycle and migration safety in one pass
- UI state handling and use-case success semantics in one pass

**Default action:** split the work and finish the more interior seam first.

### 3. Verification fails outside the owned seam

Examples:

- a matching plan breaks unrelated UI tests
- a runtime plan breaks unrelated REST behavior
- a UI plan breaks storage bootstrap tests

**Default action:** treat that as a scope warning, not as permission to widen the plan automatically.

### 4. The “supporting read-only seam” wants to become an edit seam

Every executable plan distinguishes between primary edit owners and supporting read-only seams.

If a read-only seam starts demanding edits, that is a structural warning that:

- the plan boundary is too wide,
- the fix is touching a different seam than expected, or
- the prerequisite plan did not actually settle the contract.

**Default action:** stop and decide whether the prerequisite plan must be amended first.

## Plan-family specific red flags

### Matching plans (`P01`, `P02`)

Stop immediately if:

- `P01` starts changing lock scope, transaction boundaries, undo persistence, or `InteractionStorage`
- `P02` starts redefining scoring/result-shape behavior instead of concurrency/side-effect ordering
- either plan starts treating `MatchingUseCases.java` and `MatchingService.java` as co-equal edit owners in the same pass

**Why:** that is the fastest way to turn the top hotspot into a multi-seam rewrite.

### User/profile plan (`P03`)

Stop immediately if:

- `User` invariant work starts requiring controller or CLI behavior changes to “make the tests pass”
- the plan starts changing verification workflow semantics instead of only the model/profile seam
- profile copy cleanup expands into unrelated persistence or UI refactors

**Why:** `P03` should stabilize domain truth, not absorb adapter cleanup.

### Safety/workflow plans (`P04`, `P04B`)

Stop immediately if:

- `P04` starts editing `MessagingUseCases.java`
- `P04B` starts editing `ChatViewModel.java`
- typed workflow-denial reasons force a broader `User` or REST redesign in the same pass

**Why:** these plans were split specifically to avoid safety/verification and relationship/messaging semantics colliding again.

### Runtime/storage plans (`P09`, `P09B`, `P10`, `P12`)

Stop immediately if:

- `P09` starts restructuring `MigrationRunner.java` or `SchemaInitializer.java`
- `P09B` starts turning into a general `DatabaseManager` lifecycle redesign
- `P10` starts changing broader runtime lifecycle/configuration behavior instead of JDBI wiring/plumbing
- `P12` starts requiring `StorageFactory` or `ApplicationStartup` edits

**Why:** these are adjacent but intentionally separated runtime seams.

### UI/controller plans (`P05`, `P06`, `P07`)

Stop immediately if:

- `P05` needs to redefine messaging use-case semantics instead of consuming them
- `P06` starts turning into a matching-core cleanup campaign rather than a UI seam cleanup
- `P07` starts requiring `ViewModelFactory` or ViewModel edits rather than controller/navigation/theming edits

**Why:** outer UI seams should consume settled interior contracts.

### Adapter plans (`P08`, `P11`)

Stop immediately if:

- `P08` starts inventing business rules because the underlying use-case contract is still unclear
- `P11` starts fixing use-case semantics from inside CLI handlers

**Why:** REST and CLI should mirror the interior, not define it.

## Smell list: phrases that usually mean the plan is drifting

If you catch yourself thinking or writing one of these, pause:

- “While we’re here...”
- “This other file is basically related...”
- “The tests only pass if I also change...”
- “It would be cleaner if I just refactor the whole...”
- “This helper really belongs in a different layer, so I’ll move it now too...”
- “The route layer can just handle this until the use-case is cleaned up later...”
- “I know the plan says read-only, but...”

Those phrases are often how a bounded plan becomes an unbounded cleanup campaign.

## Fast pre-edit checklist

Before editing, confirm all of the following:

- [ ] I can name the plan that owns this file.
- [ ] I know which files are primary edit owners versus supporting read-only seams.
- [ ] The first verification slice I will run is already chosen.
- [ ] I know which adjacent plan I should stop and consult if the scope widens.
- [ ] I am not relying on “I’ll clean that up later” to justify a widened diff.

If any box is unchecked, do more context work before editing.

## Fast post-failure checklist

After a failing test or compile run, ask:

- [ ] Did the failure happen inside the owned seam?
- [ ] Did the failure happen in a dependent seam that was expected to reflect this contract?
- [ ] Or did the failure expose that I am actually doing a different plan than the one I opened?

If it is the third case, stop and re-scope.

## Bottom line

The default safe behavior is:

> **Narrow the seam, finish the owned work, and open a follow-on plan if the fix keeps leaking outward.**

That is slower than improvising for ten minutes and much faster than discovering three hours later that the plan turned into a repo-wide fire drill.
