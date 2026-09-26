# Date_Program Source-of-Truth Audit (Code-Only)

_Date: 2026-03-25_

## Scope and method

This audit intentionally used **only current source code and build outputs** as source of truth:

- `src/main/java/**`
- `src/test/java/**`
- `pom.xml`
- current diagnostics + build/test gate output

No project docs were used for conclusions.

I also ran parallel code exploration across architecture, complexity, testing gaps, and blockers, then manually validated key claims in code.

---

## Current project state at a glance

### Build and quality-gate status

- `mvn spotless:apply verify` => **BUILD SUCCESS**
- Tests run: **1361**, Failures: **0**, Errors: **0**, Skipped: **2**
- Spotless: clean
- Checkstyle: 0 violations
- PMD: pass
- JaCoCo gate: pass

### Codebase size (current)

From live source scan:

- Java files total: **296**
  - `src/main/java`: **144**
  - `src/test/java`: **152**
- `tokei src` (Java):
  - Total lines: **85,949**
  - Code lines: **68,880**
  - Comments: **4,875**
  - Blanks: **12,194**

### Marker debt (TODO/FIXME/HACK)

- Scan in `src/main/java/**`: **no TODO/FIXME/HACK markers found**

---

## Executive assessment

The project is in a **technically mature but architecturally strained** state:

- ✅ Strong engineering hygiene: green verify pipeline, broad tests, formatting/linting/coverage gates.
- ✅ Clear domain richness and significant functionality breadth.
- ⚠️ Core architectural boundaries are **partially violated/inverted** in composition/wiring.
- ⚠️ Several critical classes are oversized and multi-responsibility (delivery velocity and change-risk tax).
- ⚠️ API/internal error handling leaks implementation details through `e.getMessage()` propagation.
- ⚠️ Some quality gates provide confidence, but key risk zones are currently outside strong enforcement (UI/CLI coverage exclusions, missing infra/storage adapter tests).

Bottom line: **not broken, but carrying structural debt that will slow future development and increase regression risk**.

---

## What is done well (and should be preserved)

1. **Quality pipeline discipline is real**
   - Full verify gate passes with large suite.
   - Spotless + Checkstyle + PMD + JaCoCo all active in CI lifecycle.

2. **Meaningful architectural guardrails already exist**
   - `AdapterBoundaryArchitectureTest` enforces:
     - `core` should not import JavaFX/JDBI/Javalin/Jackson frameworks
     - viewmodels should not import `core.storage` directly
   - Evidence: `src/test/java/datingapp/architecture/AdapterBoundaryArchitectureTest.java`

3. **Good use of async UI abstractions exists**
   - ViewModels are using `ViewModelAsyncScope`, dispatchers, and error-routing patterns.

4. **Functional breadth is impressive**
   - CLI + JavaFX + REST + use-case layer + storage adapters + event bus + moderation + metrics + matching + messaging.

---

## What is missing / not done yet (high impact)

## 1) Architectural boundary completion is not done

### Why
Current design still mixes composition responsibilities across layers in ways that violate clean directional architecture.

### Evidence

- `core` imports and constructs `app` use-case layer:
  - `src/main/java/datingapp/core/ServiceRegistry.java`
  - imports `datingapp.app.usecase.*`
  - constructs `MessagingUseCases`, `MatchingUseCases`, `ProfileUseCases`, `SocialUseCases`

- `storage` factory wires app event handlers (composition concern leak into storage module):
  - `src/main/java/datingapp/storage/StorageFactory.java`
  - imports `datingapp.app.event.*` and registers handlers

### Consequence
- Inverted/blurred dependency direction.
- Harder to reason about module ownership and future refactors.

### Missing piece
- A single explicit **composition root** (app/bootstrap layer), with domain/core and storage kept free of app wiring logic.

---

## 2) Error-contract hygiene is incomplete

### Why
Internal exception text is widely propagated as user-facing/internal use-case messages.

### Evidence
Pattern search found **45** occurrences of:

`UseCaseError.internal("..." + e.getMessage())`

Across:

- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`

`RestApiServer` then returns error messages directly in multiple paths:

- `handleUseCaseFailure(...)` returns `error.message()` in JSON
- global exception handlers often return `e.getMessage()`
- File: `src/main/java/datingapp/app/api/RestApiServer.java`

### Consequence
- Potential information leakage and unstable API error semantics.
- Client behavior tied to internal text.

### Missing piece
- Central sanitized error mapping (`internal_code`, safe message, correlation id), and structured server-side logging of raw cause.

---

## 3) API security model is intentionally local-only but not future-safe

### Why
API explicitly runs unauthenticated with user identity provided in header/query.

### Evidence

- Localhost-only bind + explicit warning:
  - `app.start(LOCALHOST_HOST, port)` and startup warning text
  - `src/main/java/datingapp/app/api/RestApiServer.java`

- Auth note says routes are intentionally unauthenticated
- Acting user resolved from header/query (`X-User-Id` / `userId`)

### Consequence
- Okay for strict local IPC assumptions.
- Dangerous if later exposed via proxy/tunnel/remote host without auth hardening.

### Missing piece
- Optional pluggable auth middleware path + explicit environment guard to prevent accidental non-local deployment without auth enabled.

---

## 4) Shared infrastructure testing is not complete

### Missing test suites confirmed
No direct tests found for:

- `ViewModelFactoryTest`
- `BaseViewModelTest`
- `JdbiTrustSafetyStorage*Test`
- `JdbiConnectionStorage*Test`

Yet these are critical shared/integration-heavy surfaces.

### Existing strength (for context)
There are many strong tests in domain and API areas, but these specific infrastructure chokepoints remain under-protected.

---

## 5) Coverage gate leaves key risk zones under-enforced

### Evidence (`pom.xml`)
JaCoCo check excludes:

- `datingapp/ui/**/*`
- `datingapp/app/cli/**/*`
- `datingapp/Main.class`

Gate enforces line coverage only (bundle line ratio minimum 0.60).

### Consequence
- Most timing-sensitive UI orchestration and user-facing CLI behavior can regress without coverage-gate impact.

### Missing piece
- Risk-weighted coverage strategy (especially for core ViewModel infra and high-risk adapters), and optionally branch coverage on critical modules.

---

## What is wrong / flawed logic (concrete findings)

## High severity

1. **Internal error leakage pattern is systemic**
   - 45 occurrences across use-case layer + REST propagation.
   - Risk: security/info leakage + unstable contracts.

2. **Layer inversion in composition/wiring**
   - `core.ServiceRegistry` and `storage.StorageFactory` perform app-layer wiring responsibilities.
   - Risk: architecture erosion and increasing change friction.

## Medium severity

3. **Business rule mapped via generic exception class**
   - Example: inactive user browse check throws `IllegalStateException`, globally mapped to `409`.
   - Risk: conflating true illegal states with domain conflicts.
   - Evidence: `ensureActiveCandidateBrowser(...)` and exception handler in `RestApiServer`.

4. **Chat note UX + default wiring mismatch risk**
   - `ChatController` exposes note controls.
   - `ViewModelFactory#getChatViewModel()` uses constructor path that applies `ChatUiDependencies.noOp()`.
   - `NoOpUiProfileNoteDataAccess.upsertProfileNote(...)` throws `UnsupportedOperationException`; delete returns false.
   - `ChatViewModel.deleteSelectedProfileNote()` currently ignores returned boolean and always shows “Private note deleted.”
   - Risk: misleading success UX and broken persistence expectations in default runtime.

5. **Non-deterministic streak logic in stats UI**
   - `StatsViewModel.computeLoginStreak(...)` uses `ZoneId.systemDefault()` and `LocalDate.now()`.
   - Risk: timezone/environment-dependent behavior.

## Low/quality severity

6. **Current IDE/Sonar warnings in `StatsViewModel`**
   - loop break/continue complexity warning
   - variable shadowing warning (`session` lambda name)

---

## Places that are more complex than they should be

Using both structural review and file-size hotspot evidence.

### Largest hotspot files by LOC (selected)

- `src/main/java/datingapp/ui/screen/ProfileController.java` — 1437
- `src/main/java/datingapp/ui/viewmodel/ProfileViewModel.java` — 1244
- `src/main/java/datingapp/app/api/RestApiServer.java` — 1190
- `src/main/java/datingapp/app/cli/ProfileHandler.java` — 1110
- `src/main/java/datingapp/storage/DevDataSeeder.java` — 1013
- `src/main/java/datingapp/ui/viewmodel/ChatViewModel.java` — 940
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java` — 911
- `src/main/java/datingapp/storage/jdbi/JdbiMatchmakingStorage.java` — 850
- `src/main/java/datingapp/app/cli/MatchingHandler.java` — 805
- `src/main/java/datingapp/ui/screen/ChatController.java` — 778
- `src/main/java/datingapp/ui/viewmodel/MatchesViewModel.java` — 769
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java` — 665

### Complexity themes

1. **God classes in orchestration layers**
   - `RestApiServer`, `ChatViewModel`, `ProfileViewModel`, CLI handlers, large controllers.

2. **Mixed concerns in single classes**
   - transport + mapping + policy + error semantics bundled together.

3. **Cross-cutting behavior duplicated**
   - validation + error translation + user-context checks repeated per route/use-case.

4. **Persistence classes doing too much**
   - query composition + caching + mapping + normalization in one adapter (`JdbiUserStorage`).

---

## What is blocking you (real blockers vs latent blockers)

## Current hard blockers (delivery can still proceed, but with tax)

1. **Architectural ownership ambiguity**
   - Teams/features touching composition risk layer-coupling drift.

2. **Error-contract inconsistency**
   - Hard to stabilize external API behavior while internal messages leak through.

3. **Large hotspot classes**
   - Every change in these files has high regression surface and slow review velocity.

4. **Missing infra adapter tests**
   - Storage-layer refactors are riskier than they need to be.

## Latent blockers (will block future scale)

5. **No strong migration failure-path testing strategy**
   - Fresh + idempotence is good; partial failure/recovery scenarios need stronger coverage.

6. **Local-only security model not production-evolution ready**
   - If product requirements change to remote API access, auth redesign becomes urgent.

---

## Is architecture fully complete?

**Short answer: no — it is functional and coherent, but not fully complete or cleanly layered.**

### What is correct

- Strong domain/application/storage/UI separation intent.
- Existing architectural tests and adapter seams.
- Clear use-case layer concept.

### What is lacking/wrong

- Composition root split and partially inverted (`core` and `storage` owning app wiring concerns).
- Direct adapter exception path in REST (`candidateFinder` read route) rather than consistently via use-cases.
- Error semantics are not standardized/sanitized end-to-end.

---

## What this project should have (but doesn’t yet)

1. **Single authoritative composition root** in app/bootstrap.
2. **Strict dependency-direction tests** (e.g., forbid `core -> app`, constrain `storage -> app`).
3. **Sanitized error-contract framework** (typed internal codes + safe external messages).
4. **Dedicated tests for shared infra/adapters** (`ViewModelFactory`, `BaseViewModel`, key JDBI adapters).
5. **Risk-focused coverage policy** for UI/viewmodel orchestration and critical storage boundaries.
6. **Optional auth middleware path** for API evolution beyond localhost-only use.
7. **Class-size/complexity budget enforcement** (to stop new god classes from forming).

---

## What to focus on next (prioritized roadmap)

## Phase 0 (1–2 weeks): safety and contract stabilization

1. **Stop raw internal message propagation**
   - Introduce central `UseCaseError -> ApiError` mapping.
   - Keep raw exception detail in logs only.

2. **Fix chat note default behavior mismatch**
   - Either wire real profile-note adapter for chat, or hide note actions when unsupported.
   - Respect delete boolean result and show accurate UX feedback.

3. **Fix deterministic time usage in stats streak**
   - Use app-configured clock/zone path.

4. **Address current `StatsViewModel` warnings**
   - Simplify loop and remove shadowing.

## Phase 1 (2–4 weeks): architecture hardening

5. **Move composition ownership to app/bootstrap**
   - Refactor `ServiceRegistry` responsibilities and `StorageFactory` app-handler wiring.

6. **Add dependency-direction architecture tests**
   - enforce module boundaries that currently slip.

7. **Unify route/use-case handling patterns in REST**
   - isolate route modules, shared pagination validators, shared error translation.

## Phase 2 (3–6 weeks): complexity reduction and testing depth

8. **Decompose top hotspot classes**
   - Start with `RestApiServer`, `ChatViewModel`, `ProfileViewModel`, `ProfileController`.

9. **Add missing infra/storage adapter tests**
   - Prioritize `JdbiConnectionStorage`, `JdbiTrustSafetyStorage`, `ViewModelFactory`, `BaseViewModel`.

10. **Revisit coverage gates**
   - Keep existing global gate; add targeted gate/profile for critical excluded surfaces.

---

## Concrete issue list (ready to turn into tickets)

1. **ARCH-001**: Move use-case construction out of `core.ServiceRegistry`.
2. **ARCH-002**: Move app event-handler registration out of `storage.StorageFactory`.
3. **SEC-001**: Sanitize `UseCaseError.internal` and API error output; remove raw `e.getMessage()` exposure.
4. **API-001**: Replace generic `IllegalStateException` domain conflicts with explicit domain/use-case errors.
5. **UI-001**: Fix chat profile-note feature wiring and success/failure truthfulness.
6. **TIME-001**: Make `StatsViewModel` streak computation deterministic with configured time policy.
7. **TEST-001**: Add `ViewModelFactoryTest` + `BaseViewModel` behavior tests.
8. **TEST-002**: Add adapter-level tests for `JdbiConnectionStorage` and `JdbiTrustSafetyStorage`.
9. **GATE-001**: Add architecture test rules for dependency direction.
10. **GATE-002**: Add focused coverage profile for critical UI/viewmodel infrastructure.

---

## Final judgment

This project is **not failing**; it is a strong, feature-rich codebase with real engineering discipline.

But it is **not finished architecturally**. The most important next work is not adding more features first — it is paying down structural debt in composition boundaries, error-contract hygiene, and hotspot decomposition.

If you execute the prioritized roadmap above, you should see:

- faster and safer feature iteration,
- fewer regression surprises in orchestration-heavy code,
- cleaner architecture evolution,
- better API safety and long-term maintainability.
