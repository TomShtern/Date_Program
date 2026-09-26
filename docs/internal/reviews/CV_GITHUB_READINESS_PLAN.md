# CV and GitHub readiness plan

Review dates: 14-15 September 2026. Local baseline: `d9f3cd9`.

This is a dated review and implementation proposal. Its paths and findings describe the review baseline, not necessarily the current tree. The repository organization work has since moved the paths listed below; other recommendations remain unverified until separately checked against current source. The review itself is retained as public historical material.

## Assessment

The first cleanup should make the project easier to understand, run, and assess. The repository already contains a substantial Java implementation and automated tests. Its public presentation currently makes a reader work through development history before discovering that implementation.

I recommend presenting this as a Java dating backend for a separate Flutter client, while explicitly explaining that this repository also contains CLI and JavaFX adapters. That matches the stated project direction without pretending the desktop code is absent. Keep those adapters unless a separate product decision retires them.

The strongest portfolio result would be a concise README, one maintained documentation path, a reproducible backend demonstration, a passing verification path, and a few well-explained code examples. A broad rewrite or a framework migration is unnecessary for that result. This review cannot predict an employer's reaction; it identifies concrete obstacles to understanding and trusting the work.

## What was checked

The review inspected the tracked file inventory, repository instructions, README and setup documentation, build configuration, CI definitions, representative Java application and storage boundaries, API protections, and selected tests. Historical audits were treated as leads, not evidence that an issue still exists.

Inventory before creating this document:

| Item | Verified count or state | Interpretation |
|---|---|---|
| Tracked files | 825 | The local folder contains additional ignored files that GitHub will not show. |
| Root files | 45, including 26 Markdown files | Too many competing entry points for a new reader. |
| Markdown files | 333; 280 tracked files under `docs/` | Much of the documentation describes previous reviews or implementation plans. |
| Java source files | 201 main, 222 test | README's 140 main and 107 test snapshot is stale. These counts say nothing by themselves about quality or authorship. |
| Runtime photo | One tracked JPEG under `data/photos/`, 386,443 bytes | Its presence is verified; its origin and permission to publish are not. |
| Maven wrapper | No tracked `mvnw`, `mvnw.cmd`, or wrapper configuration | A new contributor must currently supply Maven. |
| Documentation entry point | No `docs/README.md`; no root `architecture.md` | README names a nonexistent architecture file. |
| License | No tracked license file found | Decide the intended reuse terms before adding one. |

The review did not inspect the current public GitHub page, remote CI results, or the separate Flutter repository. A local checkout is evidence about these files, not proof of what a recruiter currently sees online. No production server or real-user workflow was exercised.

## Recommended order

| Order | Work package | Priority | Completion evidence |
|---|---|---|---|
| 1 | Resolve the tracked photo's provenance and review publication-sensitive content | Before sharing publicly | Approved fixture or runtime upload removed from tracking; secret/history review recorded. |
| 2 | Establish a truthful build and test baseline | Before claiming verification | Exact command, result, skipped coverage, and CI status recorded. |
| 3 | Rewrite the README around the backend and a working demo | Highest presentation value | A new reader can explain the project and follow one tested setup path. |
| 4 | Consolidate current docs and archive historical material | Highest organization value | Small root, one docs index, working links, no competing current specifications. |
| 5 | Repair helper portability and align API documentation | Correctness and onboarding | Helpers work from another checkout path; examples match current authentication and configuration. |
| 6 | Make CI and toolchain setup reproducible | Reliability | Fresh checkout verifies using documented prerequisites and required checks. |
| 7 | Resolve the signup failure contract and photo-access policy, then select source extractions | Correctness work before optional restructuring | Failure-injection and photo-access tests establish the intended contracts; each extraction preserves them. |
| 8 | Prepare CV wording and the final public repository metadata | After evidence exists | Claims match the delivered behavior and your contribution. |

Treat these as small reviewable changes. Do not combine document moves, runtime changes, and large refactors in one commit. Any eventual commits or pushes require your explicit instruction.

## 1. Make the README a useful front page

**Evidence.** `README.md:1-11` contains agent update instructions before the project title. The `ARCHIVE` markers are individual HTML comments, so the content between them remains in the document, including old storage information and repeated startup blocks. Lines 23-25 contain stale source and test snapshots. Line 197 names missing `architecture.md`. Lines 199-213 contain an agent change diary. `pom.xml` also describes a JavaFX/CLI application rather than the current backend focus.

**Action.** Replace the README's body with this outline, using current source and a freshly verified demo:

1. Project name and a two-sentence explanation of the backend, its purpose, and its current maturity.
2. A small set of implemented capabilities, each traceable to code: accounts and authentication, profiles, matching, conversations, and safety controls. Describe limitations beside the relevant capability.
3. One short demonstration. Prefer a reproducible API walkthrough with synthetic users; a short recording is useful after the walkthrough works. Link the separate Flutter client only after confirming its repository and readiness.
4. Exact prerequisites and one primary startup path. State Java 25 and preview requirements, PostgreSQL setup, environment configuration, the backend command, health endpoint, and shutdown instructions. Put CLI/desktop startup in a secondary section.
5. A package-level architecture diagram and a short code reading tour.
6. Test and verification commands, with links to the actual CI job. Avoid manually maintained file counts and undated pass claims.
7. Known limitations, selected engineering decisions, license/asset information, and links into `docs/`.

A proposed opening, to refine when the demo is verified:

> A Java backend for a dating application with a separate Flutter client. It implements account authentication, profile management, matching, messaging, and safety workflows using Javalin, JDBI, and PostgreSQL. This repository also includes CLI and JavaFX adapters that share the application and domain logic.

State the current development/alpha status explicitly. Do not add "production-ready", "secure", "scalable", a user count, or a performance claim without corresponding evidence.

Move agent workflow rules into the existing agent guidance. Preserve useful history through Git and the archive, rather than reproducing old text in the public README. Keep tool-assistance disclosures factual where useful; the objective is a maintained document, not hiding the development process.

**Done when.** Someone unfamiliar with the project can identify its purpose, backend/client relationship, supported run path, and limitations in a few minutes. Every local link resolves, there is one current command per purpose, and the demo has been followed from a fresh checkout. GitHub's [README guidance](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-readmes) explains how this file is presented to repository visitors.

## 2. Consolidate documentation without breaking the project

Start with document moves. Leave the root PowerShell runtime helpers in place during this pass because other scripts, tests, instructions, and IDE tasks may depend on their locations.

Proposed reader-facing structure:

```text
README.md
pom.xml
.env.example
AGENTS.md / CLAUDE.md                 concise contributor and agent guidance
.github/                             CI and repository guidance
config/                              checked-in, non-secret defaults
src/main/                            implementation and resources
src/test/                            automated tests and fixtures
docs/
  README.md                          current documentation index
  architecture.md                    package responsibilities and key flows
  getting-started.md                  supported backend setup
  api.md                             current HTTP contract and examples
  testing.md                         gates, test categories, limitations
  development.md                     contributor workflow
  decisions/                         a few maintained engineering decisions
  demo/                              synthetic demo and asset attribution
  archive/                           explicitly historical material
```

These are proposed destinations, not a claim that they already exist. Use one naming convention for new maintained docs. Existing names can remain as short compatibility pointers while links migrate.

| Current material | Exact disposition to propose and review |
|---|---|
| `docs/guides/ci-and-postgresql.md`, `docs/guides/postgresql-powershell.md`, `docs/archive/planning-history/POSTGRESQL_NEXT_STEPS.md`, `docs/guides/lan-backend-startup.md` | Proposed categorization completed; content freshness and source accuracy remain separate review work. |
| `docs/archive/flutter-history/FLUTTER_FRONTEND_AGENT_GUIDE.md`, `docs/archive/flutter-history/FLUTTER_PROJECT_HANDOFF.md`, `docs/archive/flutter-history/2026-04-30-phone-alpha-backend-api-requirements.md`, `docs/api/API-SPECIFICATION.md` | Handoffs and requirements are archived as historical. The API specification remains legacy/unverified until checked against source; do not merge planned endpoints into implemented behavior. |
| Root dated reports, `BACKEND_CODE_AUDIT_2026-05-06.md`, `PHONE_ALPHA_BACKEND_READINESS_REPORT.md`, `frontend-ui-overhaul-contract-response-2026-04-25.md`, `implementation_plan_By_opus_antigravity.md` | Moved to categorized paths under `docs/archive/`; their findings and recommendations remain historical. |
| `docs/archive/audits/legacy-audit-and-suggestions/`, `docs/archive/issues/current-issues/`, `docs/archive/planning-history/codebase-review-plan-set/`, `docs/reference/` | Reorganized under history/internal categories. Do not mechanically promote old findings to current bugs. |
| `2026-04-05-resume-ready-project-description-options.md` and its namesake under `docs/archive/nonrelevant/` | The copies were byte-identical and both are retained under `docs/archive/cv/` with distinct names. Neither is the project's front page. |
| `docs/archive/planning-history/ROADMAP.md` | Archived roadmap; its contents are not presented as current commitments. |
| `scripts/postgresql-public-schema-snapshot.sql` | Retained at the repository root. The reviewed statement inventory contains DDL only (28 `CREATE TABLE` statements and no `INSERT`, `COPY`, `UPDATE`, or `MERGE` statements); this is not a complete security or provenance review. |
| `.gemini/`, `.aiassistant/`, agent-specific reports | Keep settings actually needed for collaboration; archive workstation repair notes. Do not remove working instruction files merely because an assistant uses them. |
| This review document | Retained in `docs/internal/reviews/` as historical review material; this directory is public and does not provide access control. |

Before moving a file, search its exact basename across tracked docs, scripts, workflow files, and tests. Update those references in the same change. Preserve history with a normal move, not a repository-history rewrite. This proposal does not authorize deletion of old material.

**Done when.** The root primarily contains build/configuration files, supported entry-point helpers, and a small amount of contributor guidance. `docs/README.md` distinguishes current documents from the archive. A link check finds no broken internal references. There is one current API contract and one current setup guide.

## 3. Resolve runtime assets and publication hygiene

**Evidence.** Git tracks `data/photos/fdd515cb-9b26-4ee9-9f6d-b0e727acfc76/4e58a1c3-a416-49cf-91c2-a7ec32a6877f.jpg`. `config/app-config.json` uses `data/photos` as runtime photo storage. This is a reason to investigate provenance, not proof that the file is a real person's private image.

**Action.** Establish whether the JPEG is an intentional, publishable fixture. If so, relocate it to a named demo/test fixture location, document its origin and permitted use, and update references. If it is a runtime upload, propose removing it from Git tracking while preserving the local file, and ignore future runtime uploads. Handle any sensitive history separately with an explicit plan. Use synthetic demo accounts and images that you own or have permission to publish.

The current `.env`, `CheckDb.class`, `server_out.log`, `target/`, `.idea/`, and `.vscode/` are ignored. They are local housekeeping, not evidence that those files clutter the current GitHub tree. Avoid spending the first cleanup pass on them.

A narrow current-content scan found no private-key headers, AWS access-key IDs of the checked format, or classic GitHub token signatures. A path-based local history check found no commits for `.env` or the checked common key-store extensions. This does **not** cover every credential format, renamed file, branch on the remote, or past file content. Development placeholders in tracked configuration are not, by themselves, evidence of leaked production credentials.

Before making a previously private repository public, run a proper secret scan over the intended Git history and review demo assets and personal paths. Do not rewrite history just to make it look tidy. If a real credential is found, revoke/rotate it first and then plan any cleanup, following [GitHub's sensitive-data guidance](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

**Done when.** Every tracked runtime/demo asset has a known purpose and publishing permission; future user uploads stay outside Git; the secret/history review has a recorded outcome. No credentials or personal user data appear in examples or screenshots.

## 4. Repair setup and helper contracts

**Verified problems.** `scripts/run_test.ps1:1-2` changes to your absolute workstation path and invokes `InterestMatcherTest`; that test name is absent from the current tracked tree. Current matching tests include `PreferencesMatcherTest` and `PreferencesMatcherLifestyleTest`. `scripts/run_event_tests.ps1:4` also hardcodes the checkout path, runs two Maven processes, and finishes without propagating their failures explicitly. `scripts/run_imagecache_test.ps1` already uses `$PSScriptRoot` and exits with Maven's exit code, so use it as a local convention rather than inventing another wrapper style.

**Action.** Either retire the redundant single-test wrappers through an approved cleanup, or make them supported helpers. A retained wrapper must resolve its own repository location, select existing tests, fail immediately or aggregate failures explicitly, and return a nonzero exit code if Maven fails. Do not hide failing tests by adding skip flags. For `scripts/run_event_tests.ps1`, one Maven invocation selecting both handler tests is simpler if there is no deliberate isolation requirement.

If scripts later move under `scripts/`, introduce one consistent repository-root resolution rule and update every `$PSScriptRoot`-relative dependency, caller, README example, IDE task, and PowerShell script test. Root compatibility wrappers are a reasonable migration step. This is optional after the document cleanup, not a prerequisite to a neat repository.

The primary setup guide should explain PostgreSQL prerequisites, `.env` creation without overwriting an existing file, secret configuration, port use, the distinction between loopback and LAN startup, and how to stop what it starts. Validate the backend helper itself before advertising "one command" onboarding. Do not add Docker solely for appearance; a disposable alternative is useful only if it makes the supported setup easier to reproduce.

**Done when.** Copy the checkout to a different path, including one with spaces, and run each retained helper. Verify a deliberately failing Maven selection produces a failing process exit code. For root PowerShell changes, run the matching tests under `src/test/powershell/` before broader verification.

## 5. Keep the public API description accurate

This review's earlier comparison of `docs/guides/lan-backend-startup.md` and `docs/api/API-SPECIFICATION.md` is a historical finding. The specification remains marked legacy/unverified in the documentation index; compare both against current code before using them as an integration contract.

The API spec's password table at line 81 says the default minimum is eight characters; tracked `config/app-config.json` specifies twelve. State the effective shipped configuration and distinguish it from any code-level fallback.

**Action.** Build an endpoint table directly from current route registration. For each route, document authentication, ownership, method/path, accepted body, success status, and common failure responses. Cover matching, messaging, and safety as well as the existing auth/photo section. Include a complete synthetic request sequence that demonstrates signup/login, a protected request, a forbidden request, and the appropriate cleanup in a disposable demo database.

A machine-readable OpenAPI document can follow once the contract is consolidated. It is useful when kept accurate and validated; adding a large stale specification would repeat the current documentation problem.

**Done when.** Every example is exercised against the documented development mode, bearer identity and any LAN shared-secret requirements are explicit, and auth/ownership tests agree with the documented status codes. Preserve compatibility behavior unless a separately reviewed change retires it.

## 6. Establish a reliable verification baseline

### Results from this review

The source-preserving gate used was `mvn -B verify`. The usual local `spotless:apply` command was deliberately not used because this is a review-only task.

| Check | Actual result | Meaning |
|---|---|---|
| Initial `mvn -B -o verify` | Stopped before compilation because JaCoCo `0.8.14` was missing from the local cache | An offline dependency-cache problem, not a source failure. The subsequent online attempt resolved it. |
| Checkstyle in the online attempt | Passed, zero violations | Fresh formatting/style evidence for Checkstyle only. |
| Main and test compilation | Compiled 201 main and 222 test source files and reached Surefire | No compile failure in this run. A test source emitted a deprecated-API notice. |
| Test stage | **1,941 run; 1 failure; 5 errors; 2 skipped** | The verification command failed. Do not describe this checkout as passing its full gate. |
| Final Maven result | **BUILD FAILURE**, completed at `2026-09-15T00:06:19+03:00`, elapsed 9:03 | A terminal result, not an unfinished process or an older test snapshot. |
| Verify-phase plugins after tests | Not reached | No fresh passing claim for Spotless, PMD, SpotBugs reporting, or the JaCoCo gate. |
| PostgreSQL startup/smoke and `scripts/run_verify.ps1` | Not invoked | Starting or modifying the local database and applying formatting were outside this review. |

Detailed logs are local generated artifacts at `target/cv-readiness-validation/mvn-offline-verify.log` and `target/cv-readiness-validation/mvn-verify.log`. Individual fresh failure reports are under `target/surefire-reports/`. Keep these out of Git; the findings and command are sufficient evidence for this plan.

### Exact follow-up work for the failed checks

All paths below are relative to `src/test/java/`.

| Result | Evidence | Action and acceptance |
|---|---|---|
| PostgreSQL connection refused | `datingapp/CheckDbTest.java:16`, `datingapp/TestJdbiMapping.java:22`, `datingapp/storage/jdbi/FindAllDiagnosticTest.java:33`, and the startup smoke at `datingapp/storage/PostgresqlSchemaBootstrapSmokeTest.java:44` | Verify a disposable test database using the repo's PostgreSQL preflight, then run these tests against it. Document why configured PostgreSQL tests participate in the suite. All four must pass when the test database is available. |
| Theme persistence assertion | `datingapp/ui/screen/PreferencesControllerTest.java:130`, expected `LIGHT`, loaded `DARK` | Reproduce this test alone, then with the UI suite. Trace `PreferencesViewModel`, `UiThemeService`, `UiPreferencesStore`, and async completion. Distinguish a test timing/permissions problem from a production persistence bug. Assert the completed persistence operation, not just an earlier ViewModel state. |
| JavaFX action timeout | `datingapp/ui/screen/ChatControllerTest.java:182` while loading FXML | Reproduce alone and after adjacent UI tests. Inspect `JavaFxTestSupport`, controller initialization, blocked FX work, and fixture disposal. Ensure cleanup happens on failed assertions. Do not simply raise the timeout without identifying why the FX action stalls. |

The four PostgreSQL failures are an environment prerequisite failure in this run. `LivePostgresqlTestConfig` already has configuration-based opt-in; its `load()` accepts application database environment settings as well as `datingapp.pgtest.*` properties. It also runs schema migrations through `initializeSchema()`. Do not misdiagnose this as four independently broken storage implementations, and do not point these checks at valuable application data.

A useful improvement is a clearly named test-only opt-in and target, independent of normal application `.env` configuration. If adopted, update all live-PostgreSQL tests and CircleCI together. Tests should explicitly skip when that category is not requested and fail clearly when it is requested but its test database is unavailable. The integration job must remain required; blanket test exclusions would conceal the contract.

For naming and discoverability, move retained database diagnostics into the storage test package and name them after the invariant they test. `CheckDbTest` currently asserts a count exists and is nonnegative; `TestJdbiMapping` and `FindAllDiagnosticTest` inspect whatever rows happen to exist. Strengthen retained integration tests with controlled fixtures and exact mapped values. Remove or merge duplicates only after comparing their coverage and receiving cleanup authorization.

After a future fix, useful focused commands from the repository root are:

```powershell
mvn --% -B -Dtest=PreferencesControllerTest,ChatControllerTest test
mvn --% -B -Dtest=CheckDbTest,TestJdbiMapping,FindAllDiagnosticTest,PostgresqlSchemaBootstrapSmokeTest test
mvn -B verify
```

The second command requires a configured disposable PostgreSQL target. Review-only work has not run those retries or changed settings to make the failures disappear. The JavaFX failures are confirmed test results; their underlying causes remain unresolved.

### Explain the existing CI split

There is already meaningful verification infrastructure:

- `.github/workflows/verify.yml:49-59` deliberately runs fast feedback with Xvfb and `spotless:check test`.
- `.github/workflows/build.yml:44-49` runs `verify` plus Sonar analysis. Its configured execution does not establish that the remote job currently passes.
- `.circleci/config.yml:12-32,121-194` provisions PostgreSQL, runs the suite and verify plugins, and has an explicit PostgreSQL runtime smoke job. The lack of PostgreSQL in the fast GitHub workflow is therefore not a missing integration strategy by itself.
- `pom.xml:378-397` generates a SpotBugs report by default; the `spotbugs-strict` profile adds its blocking check. Do not advertise every SpotBugs finding as a default build failure.
- `pom.xml:425-449` enforces 60% line coverage for the configured bundle, excluding UI, CLI, and `Main`. Describe the scope accurately; it is not a whole-project 60% claim.

**Action.** Document which checks are authoritative, verify that the remote services actually run for the published branch, and require the relevant checks before merging. Keep ordinary build/test verification independent of a Sonar credential where practical. Avoid duplicating the same expensive suite merely to add more badges.

Add a Maven wrapper with a pinned, tested Maven version if reducing onboarding friction is a priority. Update `.gitignore:43-44`, which currently ignores wrapper configuration, and commit the generated wrapper scripts/configuration through the normal review process. Validate Windows and the Linux CI command. Retain Java 25 preview flags unless a separate compatibility change proves they can be removed.

`scripts/run_verify.ps1` remains the canonical supported local workflow, but explain its side effects: it starts PostgreSQL, applies formatting, runs verification and smoke checks, and stops PostgreSQL. Distinguish that workflow from a non-formatting check such as `mvn verify`; do not silently redefine the existing helper during a README cleanup.

## 7. Address two backend contracts before claiming real-user readiness

These findings deserve more attention than package cosmetics. They do not require hiding the project from a CV indefinitely, but they should be fixed or explicitly recorded as current limitations before describing it as ready for real users.

### Signup can partially persist when a later write fails

**Verified structure.** `src/main/java/datingapp/app/usecase/auth/AuthUseCases.java:45-66` saves a user, then a password hash, then creates a session. Lines 152-155 insert the refresh token. `JdbiUserStorage.java:106-110` wraps its own save in a transaction, whereas `JdbiAuthStorage.savePasswordHash()` and `insertRefreshToken()` use separate handles. There is no single account-creation transaction in this path.

**Failure scenario.** If the credential write fails after the user save, a row can remain for an email that cannot log in. A retry reaches the existing-email conflict. If the refresh-token write fails, account creation may have succeeded while the caller receives a failure instead of a session. The write boundaries are verified; a fault-injection reproduction was not run during this review.

**Action.** Prefer a storage transaction operation that atomically creates the account, credentials, and initial refresh token, exposed through an application-facing interface. Keep JDBI handles out of framework-free domain types. Perform expensive password hashing before opening the transaction where appropriate. Define the failure contract for initial session issuance explicitly. Do not bolt on a generic transaction framework or an unsafe "delete on any exception" workaround.

**Done when.** Storage-backed tests inject failure at credential creation and refresh-token insertion, verify the agreed rollback state, and prove retry with the same email behaves correctly. Keep successful signup, duplicate-email handling, login, and refresh behavior covered in both test storage and PostgreSQL paths.

### Photo URLs bypass API authentication guards

**Verified behavior from source.** `src/main/java/datingapp/app/api/RestApiRequestGuards.java:57-66` applies guards only to `/api/` paths. `RestApiServer.java:433-469` serves `/photos/*` separately without bearer identity, ownership, or the LAN shared-secret check. UUID and filename/path checks exist, so this finding is about access policy, not an established path-traversal vulnerability.

**Consequence.** Someone who can reach the server and knows a valid photo URL can request it without those API credentials. Treat this as a confirmed design limitation whose severity depends on the intended photo policy. A hard-to-guess filename is not an access policy.

**Action.** Decide whether profile photos are intentionally public by URL. If they are private, use authenticated reads or a bounded signed-URL mechanism and update the Flutter image-loading contract. If they are intentionally public, document that policy and the lifecycle behavior for deletion or account removal. An owner-only check alone would prevent legitimate matching users from seeing each other's photos, so define the allowed audience before implementation.

**Done when.** Tests cover missing/invalid credentials or signatures, permitted viewers, unavailable/deleted photos, and malformed paths. LAN documentation explicitly states which resources the shared secret protects. Use only synthetic images for the public demo until provenance and access expectations are resolved.

## 8. Refactor only the source boundaries that improve understanding

The following are maintainability improvements, not evidence that the current architecture needs replacement. Preserve the existing package separation and application use cases. No multi-module conversion, microservices split, dependency-injection framework, or language migration is needed to make this portfolio readable.

| Recommendation | Evidence and bounded action | Acceptance | Priority |
|---|---|---|---|
| Extract API routes by feature | `src/main/java/datingapp/app/api/RestApiServer.java` combines lifecycle, registration, and endpoint handlers across roughly 1,900 lines. Its `registerAuthRoutes`, `registerProfileRoutes`, and other groups already provide seams. Keep server setup, common guards, and error mapping central; first extract one cohesive group into `AuthRoutes` or `ProfileRoutes`, then repeat only when useful. Inject that group's actual dependencies instead of passing the entire registry or creating another service graph. | Existing route, auth, photo, ownership, and request-guard tests preserve methods, paths, payloads, statuses, and protections. The server reads as lifecycle and registration. | Best optional code-organization improvement after contract fixes. |
| Clarify registry ownership | `src/main/java/datingapp/core/ServiceRegistry.java:3-13` imports application use cases, and lines 46-183 hold and assemble a broad service graph. Its placement makes a simple "core has no app dependency" description inaccurate. Document it as the current composition exception. Gradually give adapters only the feature capabilities they need. Consider moving the registry into bootstrap/wiring only as a separate, caller-checked refactor. | Domain services remain independent of transport/storage frameworks; adapters do not gain arbitrary registry access; architecture tests and all composition callers agree with the chosen ownership. | Later, because dependency reach is broad. |
| Extract configuration loading from startup | `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java:54-168` owns lifecycle and rollback, while lines 182-235 expose JSON/environment loading and production-secret checks. Move that existing parsing/validation behavior into an `AppConfigLoader` in the bootstrap package. Keep current loading entry points delegating during migration. | Config precedence, malformed-input errors, production-secret checks, startup rollback, shutdown, and reset remain unchanged and tested. | Optional; useful if configuration changes continue. |
| Defer JavaFX factory restructuring | `src/main/java/datingapp/ui/viewmodel/ViewModelFactory.java` centralizes feature construction and disposal. If desktop work continues, extract private feature providers while retaining one lifecycle owner. | Controllers still obtain ViewModels through the factory; reset/disposal and async behavior stay covered. | Low for a backend-focused CV. Do not do this merely to shorten a file. |

Avoid mechanical class splitting by line count, renaming every package, or introducing one-method abstractions everywhere. A successful extraction lets a reader follow one feature without losing sight of its dependencies or error behavior.

## 9. Preserve and explain the project's strengths

These are useful engineering examples already present in source:

- Shared application use cases behind REST, CLI, and JavaFX adapters. Keep a package diagram honest about composition exceptions such as `ServiceRegistry`.
- `src/main/java/datingapp/storage/StorageFactory.java` separates persistence assembly, domain-service construction, event-handler registration, and registry construction. Its component records make the wiring explicit.
- `src/test/java/datingapp/architecture/` contains adapter, location, time-policy, and event-coverage checks. Show what each protects instead of just displaying a test count.
- Auth uses BCrypt, hashed opaque refresh tokens, and bearer identity checks. Auth route tests cover banned/deleted users and spoofed identity. These are concrete safeguards, not a security certification.
- `ui/async/` and `ViewModelFactory` provide an established threading and lifecycle approach for the desktop adapter. Preserve that approach during any cleanup.

A useful README code-reading tour should link directly to five or six files, in this order:

1. `src/main/java/datingapp/app/api/RestApiServer.java` for the backend entry point and route groups.
2. `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java` for identity and ownership decisions.
3. `src/main/java/datingapp/app/usecase/auth/AuthUseCases.java` for one complete application flow, including its documented transaction improvement.
4. `src/main/java/datingapp/core/storage/AuthStorage.java` and `src/main/java/datingapp/storage/jdbi/JdbiAuthStorage.java` for the persistence boundary.
5. `src/test/java/datingapp/app/api/RestApiAuthRoutesTest.java` for externally observable behavior.
6. `src/main/java/datingapp/storage/StorageFactory.java` for how the pieces are assembled.

After route extraction, update the first link to the extracted feature as appropriate. Keep the diagram at package level so routine class additions do not stale the README again.

## CV wording and interview preparation

After the build and demonstration are verified, a defensible starting bullet is:

> Built a Java dating backend with Javalin, JDBI, and PostgreSQL, implementing account authentication, profile management, matching, messaging, and safety workflows through shared application and domain layers.

Adapt "built" to your actual contribution. The existence of code or a passing test does not establish how much you personally designed or implemented. Add a second bullet about testing or a specific technical decision only when you can explain the implementation and its limitations. Do not use line counts as the main achievement.

Prepare to explain a real request from route to use case to storage; an authorization rejection; a transaction or race-condition decision; why PostgreSQL and JDBI were chosen; how a test catches a meaningful regression; and what remains incomplete. Use the strongest examples from the code tour, rather than claiming equal expertise in every subsystem.

When publication is separately authorized, verify the actual remote README, links, branch, and CI result. Set a concise repository description and accurate topics, then link the repository from the CV. Decide licensing and third-party asset attribution deliberately. Neither adding a license nor publishing a repository is part of this review.

## Stopping point

The repository is ready for the proposed CV link when the publication-sensitive content is resolved, the README accurately describes the backend and adapters, the supported demo works from a fresh checkout, the required checks have an explained passing result, and the selected code reading path is understandable. Optional refactoring should not postpone sharing indefinitely once those conditions are met.
