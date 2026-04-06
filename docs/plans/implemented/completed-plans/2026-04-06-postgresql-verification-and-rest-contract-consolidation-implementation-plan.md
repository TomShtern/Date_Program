# PostgreSQL Verification Routine and REST Browse Contract Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make PostgreSQL runtime verification part of normal local and CI validation, and consolidate the duplicated REST browse/candidates surface around one canonical browse contract without breaking existing callers.

**Architecture:** Keep the existing local-first PostgreSQL smoke path (`start_local_postgres.ps1` + `run_postgresql_smoke.ps1` + `PostgresqlRuntimeSmokeTest`) as the foundation, but promote it into the repo’s normal verification flow through script orchestration and CI. For REST, treat `/api/users/{id}/browse` as the canonical browse contract, keep `/api/users/{id}/candidates` only as a temporary compatibility alias, and refactor both endpoints to derive from one response-building path and one candidate-summary projection.

**Tech Stack:** Java 25, Maven, JUnit 5, PowerShell 7, Javalin 6, JDBI 3, PostgreSQL JDBC, GitHub Actions.

---

## ✅ Execution status (2026-04-06)

- ✅ Task 1 completed — `run_verify.ps1` now runs the Maven gate plus PostgreSQL smoke with cleanup and exit-code propagation.
- ✅ Task 2 completed — `.github/workflows/verify.yml` added and repo guidance updated to treat `./run_verify.ps1` as the full local verification path.
- ✅ Task 3 completed — `/api/users/{id}/browse` is now the canonical browse contract and `/api/users/{id}/candidates` is a compatibility projection with deprecation headers.
- ✅ Task 4 completed — duplicate REST DTO coverage was consolidated so `RestApiDtosTest` remains the test home.
- ✅ Task 5 completed — focused PowerShell/REST verification, Maven quality gate, YAML validation, PostgreSQL smoke, and repo-level `./run_verify.ps1` all passed in this implementation session.

---

## Source of truth used for this plan

This plan is based on the current source only:

- `pom.xml`
- `run_verify.ps1`
- `run_postgresql_smoke.ps1`
- `start_local_postgres.ps1`
- `stop_local_postgres.ps1`
- `config/app-config.json`
- `config/app-config.postgresql.local.json`
- `src/main/java/datingapp/app/api/RestRouteSupport.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/app/api/RestApiUserDtos.java`
- `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- `src/test/java/datingapp/app/api/RestApiRoutesTest.java`
- `src/test/powershell/RunPostgresqlSmokeScriptTest.ps1`
- `src/test/powershell/StartLocalPostgresScriptTest.ps1`

Validated starting point before this plan:

- `mvn spotless:apply verify` is green.
- `PostgresqlRuntimeSmokeTest` exists and is real, but self-skips unless `datingapp.pgtest.*` system properties are set.
- `run_verify.ps1` currently runs Maven only; it does **not** include PostgreSQL smoke.
- `.github/workflows/` does not exist yet.
- `/api/users/{id}/browse` and `/api/users/{id}/candidates` both exist and both use `MatchingUseCases.browseCandidates(...)`, but they duplicate final response projection logic.
- `RestApiRoutesTest.java` duplicates DTO test coverage already present in `RestApiDtosTest.java`.

---

## Decisions locked in by this plan

1. **Local PostgreSQL verification becomes routine via the repo-level verify wrapper, not by forcing live PostgreSQL into every plain Maven `verify` invocation.**
   - Keep `mvn spotless:apply verify` as the pure Maven quality gate.
   - Make `run_verify.ps1` the canonical repo-level full verification entrypoint.
   - Make CI run both the Maven gate and the PostgreSQL smoke path.

2. **`/api/users/{id}/browse` is the canonical browse contract.**
   - It already carries the full envelope: `candidates`, `dailyPick`, `dailyPickViewed`, and `locationMissing`.
   - `/api/users/{id}/candidates` remains temporarily for compatibility, but only as a thin projection of the canonical browse response.

3. **Do not remove `/candidates` in this plan.**
   - Deprecate its role in code/tests/docs.
   - Preserve compatibility while eliminating internal duplication.

4. **Do not reopen completed PostgreSQL migration work.**
   - Runtime support, dialect wiring, and smoke coverage already exist.
   - This plan is about orchestration, routine verification, and contract cleanup.

---

## Non-goals

- Do **not** start Kotlin migration.
- Do **not** replace H2-backed compatibility tests wholesale.
- Do **not** change the business behavior of candidate selection or ranking.
- Do **not** remove `/candidates` outright unless a separate follow-up plan explicitly authorizes a breaking REST change.
- Do **not** broaden location support or network exposure in this plan.

---

## File map

### Create

- `.github/workflows/verify.yml` — GitHub Actions workflow that runs the normal Maven quality gate and the PostgreSQL smoke path against a PostgreSQL service.
- `src/test/powershell/RunVerifyScriptTest.ps1` — script-level contract test for the new `run_verify.ps1` orchestration.

### Modify

- `run_verify.ps1` — make the repo-level full verification path run Maven quality checks **and** PostgreSQL smoke, with cleanup and exit-code propagation.
- `run_postgresql_smoke.ps1` — add lifecycle switches only if needed to avoid awkward double-start / cleanup behavior when called by `run_verify.ps1`.
- `src/test/powershell/RunPostgresqlSmokeScriptTest.ps1` — update script tests if `run_postgresql_smoke.ps1` gains lifecycle switches or new contract behavior.
- `README.md` — update the local verification instructions if `run_verify.ps1` becomes the canonical full-gate command.
- `AGENTS.md` — align the verification order with the new routine PostgreSQL smoke path.
- `CLAUDE.md` — align canonical verification commands with the new routine PostgreSQL smoke path.
- `.github/copilot-instructions.md` — align the verification guidance with the new routine PostgreSQL smoke path.
- `src/main/java/datingapp/app/api/RestApiServer.java` — centralize browse-response construction and make `/candidates` a compatibility projection of the canonical browse response.
- `src/main/java/datingapp/app/api/RestApiDtos.java` — remove duplicate candidate-summary mapping from `BrowseCandidatesResponse.from(...)` if a shared mapper/helper is introduced.
- `src/main/java/datingapp/app/api/RestApiUserDtos.java` — add the canonical list-mapping helper for `UserSummary` so browse and candidates share one projection path.
- `src/main/java/datingapp/app/api/RestRouteSupport.java` — document `/candidates` as a compatibility alias if that improves clarity at the registration seam.
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java` — pin canonical `/browse` behavior, `/candidates` compatibility projection behavior, and any deprecation headers.
- `src/test/java/datingapp/app/api/RestApiDtosTest.java` — keep DTO coverage in one place and add helper coverage if new list-projection helpers are introduced.
- `src/test/java/datingapp/app/api/RestApiRoutesTest.java` — delete or trim this file so duplicated DTO tests do not remain after consolidation.

### Verify only

- `src/test/java/datingapp/storage/PostgresqlRuntimeSmokeTest.java` — existing live smoke test that must remain green.
- `config/app-config.json` — already points runtime config at PostgreSQL (`localhost:55432`), which CI must respect.
- `config/app-config.postgresql.local.json` — local PostgreSQL config helper.

---

## Task 1: Make local full verification include PostgreSQL smoke

**Files:**
- Create: `src/test/powershell/RunVerifyScriptTest.ps1`
- Modify: `run_verify.ps1`
- Modify: `run_postgresql_smoke.ps1` (only if lifecycle switches are needed)
- Modify: `src/test/powershell/RunPostgresqlSmokeScriptTest.ps1` (if smoke-script contract changes)

- [ ] **Step 1: Write the failing script-level orchestration test for `run_verify.ps1`**

Create `src/test/powershell/RunVerifyScriptTest.ps1` following the same sandbox/stub style already used in `RunPostgresqlSmokeScriptTest.ps1`.

The test should prove all of the following:

1. `run_verify.ps1` invokes the Maven quality gate.
2. `run_verify.ps1` invokes the PostgreSQL smoke path.
3. PostgreSQL cleanup runs in a `finally`-style path even when smoke fails.
4. The overall script exits non-zero if either the Maven gate or PostgreSQL smoke fails.

Suggested test harness shape:

```powershell
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# sandbox contains:
# - stub mvn.cmd
# - stub run_postgresql_smoke.ps1
# - stub stop_local_postgres.ps1
# - copied run_verify.ps1
# - count/status files

# Assert 1: happy path => mvn once, smoke once, stop once, exit 0
# Assert 2: mvn failure => smoke not required, script exits with mvn code
# Assert 3: smoke failure => stop still invoked, script exits with smoke code
```

- [ ] **Step 2: Run the PowerShell script tests and confirm red state**

Run:

```powershell
pwsh -File src/test/powershell/RunPostgresqlSmokeScriptTest.ps1
pwsh -File src/test/powershell/RunVerifyScriptTest.ps1
```

Expected:
- Existing smoke-script test passes.
- New verify-script test fails because `run_verify.ps1` does not yet call PostgreSQL smoke or cleanup.

- [ ] **Step 3: Implement `run_verify.ps1` as the canonical full verification wrapper**

Refactor `run_verify.ps1` so it:

1. runs the current Maven quality gate,
2. runs PostgreSQL smoke after the Maven gate succeeds,
3. always attempts PostgreSQL cleanup afterward,
4. surfaces the right failing exit code.

Target script structure:

```powershell
Set-Location "<repo-root>"
$overallExitCode = 0

try {
    & mvn spotless:apply verify
    if ($LASTEXITCODE -ne 0) {
        $overallExitCode = $LASTEXITCODE
        return
    }

    & pwsh -NoProfile -ExecutionPolicy Bypass -File .\run_postgresql_smoke.ps1
    if ($LASTEXITCODE -ne 0) {
        $overallExitCode = $LASTEXITCODE
    }
}
finally {
    & pwsh -NoProfile -ExecutionPolicy Bypass -File .\stop_local_postgres.ps1 | Out-Null
}

exit $overallExitCode
```

Implementation notes:
- Preserve human-readable status output.
- Do **not** swallow failures.
- Keep the script PowerShell-friendly on Windows.
- If this wrapper causes awkward double-start behavior, then extend `run_postgresql_smoke.ps1` with a focused switch such as `-StopWhenDone` or `-SkipStart`, and cover that contract in `RunPostgresqlSmokeScriptTest.ps1`.

- [ ] **Step 4: Re-run the PowerShell script tests until green**

Run:

```powershell
pwsh -File src/test/powershell/RunPostgresqlSmokeScriptTest.ps1
pwsh -File src/test/powershell/RunVerifyScriptTest.ps1
pwsh -File src/test/powershell/StartLocalPostgresScriptTest.ps1
```

Expected:
- All script tests pass.

- [ ] **Step 5: Verify the real local wrapper behavior once, end-to-end**

Run:

```powershell
.\run_verify.ps1
```

Expected:
- Maven quality gate passes.
- PostgreSQL smoke runs automatically.
- Local PostgreSQL is stopped at the end.
- Overall script exits `0`.

---

## Task 2: Make PostgreSQL verification routine in CI

**Files:**
- Create: `.github/workflows/verify.yml`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `.github/copilot-instructions.md`

- [ ] **Step 1: Create the failing CI workflow file**

Create `.github/workflows/verify.yml` with one job that:

1. checks out the repo,
2. installs Java 25,
3. runs `mvn spotless:apply verify`,
4. runs `PostgresqlRuntimeSmokeTest` against a PostgreSQL service.

Use a PostgreSQL service container mapped to **port `55432`**, because `PostgresqlRuntimeSmokeTest#applicationStartupLoadUsesDefaultPostgresqlRuntimePath()` expects `ApplicationStartup.load()` to resolve `jdbc:postgresql://localhost:55432/datingapp` from `config/app-config.json`.

Suggested workflow skeleton:

```yaml
name: verify

on:
  push:
    branches: [main]
  pull_request:

jobs:
  verify:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:17
        env:
          POSTGRES_USER: datingapp
          POSTGRES_PASSWORD: datingapp
          POSTGRES_DB: datingapp
        ports:
          - 55432:5432
        options: >-
          --health-cmd "pg_isready -U datingapp -d datingapp"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
          cache: maven
      - name: Maven quality gate
        run: mvn spotless:apply verify
      - name: PostgreSQL smoke
        run: >-
          mvn -Dcheckstyle.skip=true
          -Dtest=PostgresqlRuntimeSmokeTest
          -Ddatingapp.pgtest.url=jdbc:postgresql://localhost:55432/datingapp
          -Ddatingapp.pgtest.username=datingapp
          -Ddatingapp.pgtest.password=datingapp
          test
```

- [ ] **Step 2: Validate workflow syntax locally**

Run:

```powershell
yq '.' .github/workflows/verify.yml | Out-Null
```

Expected:
- No YAML parse error.

- [ ] **Step 3: Update repo guidance so humans and agents use the new routine path**

Update these docs in one small, consistent pass:

- `README.md`
- `AGENTS.md`
- `CLAUDE.md`
- `.github/copilot-instructions.md`

What to change:
- Keep `mvn spotless:apply verify` documented as the Maven quality gate.
- Add `.\run_verify.ps1` as the repo-level **full local verification** path.
- State that PostgreSQL smoke is now part of the routine verification path rather than a purely optional manual step.
- Keep the existing local-first / Docker-fallback stance.

Suggested README wording fragment:

```markdown
# Full local verification (includes PostgreSQL smoke)
.\run_verify.ps1

# Maven quality gate only
mvn spotless:apply verify
```

- [ ] **Step 4: Re-read the touched docs and confirm they agree**

Run a focused search:

```powershell
rg -n "run_verify\.ps1|run_postgresql_smoke\.ps1|spotless:apply verify" README.md AGENTS.md CLAUDE.md .github/copilot-instructions.md
```

Expected:
- Guidance is consistent.
- No stale “optional only” wording remains where the new routine path should be described.

---

## Task 3: Refactor browse/candidates response building into one canonical path

**Files:**
- Modify: `src/main/java/datingapp/app/api/RestApiServer.java`
- Modify: `src/main/java/datingapp/app/api/RestApiDtos.java`
- Modify: `src/main/java/datingapp/app/api/RestApiUserDtos.java`
- Modify: `src/main/java/datingapp/app/api/RestRouteSupport.java` (only if route-level comments are improved)
- Modify: `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Modify: `src/test/java/datingapp/app/api/RestApiDtosTest.java`

- [ ] **Step 1: Add the failing route-level contract test for canonical browse projection**

In `RestApiReadRoutesTest.java`, add a focused test proving that `/candidates` is a compatibility projection of `/browse`, not an independently shaped implementation.

Suggested test shape:

```java
@Test
@DisplayName("/candidates returns the canonical /browse candidate projection and marks the route deprecated")
void candidatesRouteReturnsCanonicalBrowseProjectionAndDeprecationHeader() throws Exception {
    // arrange seeker + ranked candidates
    // call /browse and /candidates
    // assert same candidate ids/order
    // assert /browse has envelope fields
    // assert /candidates is array-only
    // assert deprecation/link header exists on /candidates
}
```

Also keep one existing test that proves `/browse` still reports `locationMissing`, because that is the added semantic value of the canonical contract.

- [ ] **Step 2: Run the focused REST contract tests and confirm red state**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=RestApiReadRoutesTest,RestApiDtosTest test
```

Expected:
- FAIL because no deprecation/compatibility header exists yet and browse/candidates still duplicate projection logic separately.

- [ ] **Step 3: Add a single candidate-summary list projection helper**

Put the shared list-mapping helper in `RestApiUserDtos.java`, because `UserSummary` already owns single-user projection.

Suggested helper:

```java
static record UserSummary(UUID id, String name, int age, String state) {
    static UserSummary from(User user, ZoneId userTimeZone) {
        UserDtoMapper.UserFields fields = UserDtoMapper.map(user, userTimeZone, null);
        return new UserSummary(user.getId(), user.getName(), fields.age(), fields.state());
    }

    static List<UserSummary> fromUsers(List<User> users, ZoneId userTimeZone) {
        return users.stream().map(user -> from(user, userTimeZone)).toList();
    }
}
```

Then update `BrowseCandidatesResponse.from(...)` in `RestApiDtos.java` to use that helper instead of re-implementing the stream mapping inline.

- [ ] **Step 4: Centralize browse response construction inside `RestApiServer`**

Add one helper in `RestApiServer.java` that produces the canonical `BrowseCandidatesResponse`.

Suggested structure:

```java
private Optional<BrowseCandidatesResponse> loadBrowseResponse(Context ctx, UUID userId, User user) {
    Optional<MatchingUseCases.BrowseCandidatesResult> result = loadBrowseCandidates(ctx, userId, user);
    if (result.isEmpty()) {
        return Optional.empty();
    }
    return Optional.of(BrowseCandidatesResponse.from(result.get(), userTimeZone));
}
```

Then refactor the handlers to share it:

```java
void browseCandidates(Context ctx) {
    UUID id = parseUuid(ctx.pathParam("id"));
    User user = loadUser(ctx, id);
    if (user == null) {
        return;
    }
    ensureActiveCandidateBrowser(user);

    Optional<BrowseCandidatesResponse> response = loadBrowseResponse(ctx, id, user);
    if (response.isEmpty()) {
        return;
    }
    ctx.json(response.get());
}

void getCandidates(Context ctx) {
    UUID id = parseUuid(ctx.pathParam("id"));
    User user = loadUser(ctx, id);
    if (user == null) {
        return;
    }
    ensureActiveCandidateBrowser(user);

    Optional<BrowseCandidatesResponse> response = loadBrowseResponse(ctx, id, user);
    if (response.isEmpty()) {
        return;
    }
    markCandidatesCompatibilityAlias(ctx, id);
    ctx.json(response.get().candidates());
}
```

Add a small helper for deprecation metadata, for example:

```java
private void markCandidatesCompatibilityAlias(Context ctx, UUID userId) {
    ctx.header("Deprecation", "true");
    ctx.header("Link", "</api/users/" + userId + "/browse>; rel=\"successor-version\"");
}
```

If you prefer not to emit deprecation headers yet, then update the route-level comment and tests to make `/candidates` a documented compatibility alias in code. However, the preferred path is to emit the headers now so the canonical contract is machine-visible.

- [ ] **Step 5: Re-run the focused REST pack until green**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=RestApiReadRoutesTest,RestApiDtosTest test
```

Expected:
- `/browse` stays the rich envelope.
- `/candidates` returns the exact `browse.candidates()` projection.
- Contract tests pass.

---

## Task 4: Remove duplicated REST DTO test coverage and leave one source of truth

**Files:**
- Modify: `src/test/java/datingapp/app/api/RestApiDtosTest.java`
- Delete or heavily trim: `src/test/java/datingapp/app/api/RestApiRoutesTest.java`

- [ ] **Step 1: Compare the overlapping DTO tests and preserve only unique coverage**

Current overlap already visible in source:
- `RestApiDtosTest` has `UserSummary uses provided timezone for age`.
- `RestApiRoutesTest` repeats the same timezone mapping assertions.

Keep the authoritative DTO tests in `RestApiDtosTest.java`.

If any assertion exists only in `RestApiRoutesTest.java`, move it first.

- [ ] **Step 2: Delete or trim `RestApiRoutesTest.java` so it stops duplicating DTO coverage**

Preferred result:
- delete the file entirely if it contributes no unique behavior, or
- leave only unique route-focused assertions if any remain after consolidation.

- [ ] **Step 3: Run the DTO/REST test pack and confirm no coverage was lost**

Run:

```powershell
mvn --% -Dcheckstyle.skip=true -Dtest=RestApiDtosTest,RestApiReadRoutesTest test
```

Expected:
- PASS with one clear DTO test home and no duplicated timezone-mapping coverage.

---

## Task 5: Final verification and handoff

**Files:**
- Verify only: all touched files above

- [ ] **Step 1: Run the focused script and REST packs**

Run:

```powershell
pwsh -File src/test/powershell/RunPostgresqlSmokeScriptTest.ps1
pwsh -File src/test/powershell/RunVerifyScriptTest.ps1
pwsh -File src/test/powershell/StartLocalPostgresScriptTest.ps1
mvn --% -Dcheckstyle.skip=true -Dtest=RestApiReadRoutesTest,RestApiDtosTest test
```

Expected:
- All focused tests pass.

- [ ] **Step 2: Run the full Maven quality gate**

Run:

```powershell
mvn spotless:apply verify
```

Expected:
- PASS.

- [ ] **Step 3: Run the repo-level full verification path**

Run:

```powershell
.\run_verify.ps1
```

Expected:
- Maven gate passes.
- PostgreSQL smoke runs automatically.
- Local PostgreSQL is stopped afterward.
- Overall exit code is `0`.

- [ ] **Step 4: Sanity-check the new CI workflow file one last time**

Run:

```powershell
yq '.' .github/workflows/verify.yml | Out-Null
```

Expected:
- No YAML parse error.

- [ ] **Step 5: Final handoff notes**

The implementation handoff should explicitly state:

- `mvn spotless:apply verify` remains the Maven-only quality gate.
- `.\run_verify.ps1` is now the canonical repo-level full local verification path.
- `/api/users/{id}/browse` is the canonical browse contract.
- `/api/users/{id}/candidates` remains available only as a compatibility alias returning the canonical browse candidate projection.
- No Kotlin work was started.

---

## Suggested implementation order rationale

1. **Script-level verification first** — it gives fast red/green feedback before touching Java code.
2. **CI second** — it makes the PostgreSQL smoke path routine beyond one machine.
3. **REST production code third** — once verification orchestration is reliable, clean up the browse/candidates seam.
4. **REST test dedup last** — remove duplicated tests only after the canonical projection path is stable.
5. **Full verification at the end** — prove both the Maven gate and the PostgreSQL smoke path are routine and green.

## Risks and mitigations

- **Risk:** `run_verify.ps1` becomes annoying for contributors who only want a fast Maven check.
  **Mitigation:** keep `mvn spotless:apply verify` documented as the Maven-only gate and `run_verify.ps1` as the full local gate.

- **Risk:** CI PostgreSQL port mismatch breaks `PostgresqlRuntimeSmokeTest#applicationStartupLoadUsesDefaultPostgresqlRuntimePath()`.
  **Mitigation:** map the service to `55432:5432` so the existing config-backed assertion remains valid.

- **Risk:** removing `/candidates` behavior outright would be a breaking API change.
  **Mitigation:** keep it as a thin compatibility alias in this plan; removal is a separate follow-up.

- **Risk:** DTO/helper cleanup accidentally changes candidate ordering.
  **Mitigation:** keep the ordering assertions in `RestApiReadRoutesTest` and compare `/candidates` directly against `/browse.candidates`.

## Self-review

- Spec coverage: both requested workstreams are fully covered — PostgreSQL verification becomes routine locally and in CI, and REST browse/candidates duplication is consolidated without an API break.
- Placeholder scan: no `TODO`/`TBD` steps remain; every task names exact files, commands, and the expected behavior.
- Type/contract consistency: the plan consistently treats `BrowseCandidatesResponse` as the canonical REST browse envelope and `UserSummary.fromUsers(...)` as the shared candidate-summary projection seam.
