we are on windows 11, usually using powershell, we are working in VS Code-Insiders. we are using java 25, and using javafx 25, maven, palantir format/style and the java by Red Hat extension.
make sure to leverage the tools you have as an ai coding agent together with the IDE tools and also the tools we have here on this system.

You are operating in an environment where ast-grep is installed. For any code search that requires understanding of syntax or code structure, you should default to using ast-grep --lang [language] -p '<pattern>'. Adjust the --lang flag as needed for the specific programming language. Avoid using text-only search tools unless a plain-text search is explicitly requested.

Deploy multiple parallel subagents for different tasks when needed, and coordinate their work through a parent agent. Use the `agent` tool to invoke subagents with specific detailed and defined instructions/tasks and context. For example, you might have one subagent focused on code analysis using ast-grep, while another handles code edits or refactoring. The parent agent can manage the overall workflow, ensuring that each subagent has the information it needs to perform its task effectively while keeping the process organized and efficient.
The goal is to have you, the parent agent, orchestrate the work of multiple specialized subagents to achieve complex tasks that require different types of expertise or operations, such as code analysis, refactoring, testing, and documentation. Each subagent can focus on its specific area while you coordinate their efforts to ensure a cohesive and efficient workflow.
Dont forget to use the specialized agents when appropriate, such as the executionSubagent for executing shell commands, or the runSubagent command. For read-only codebase exploration, prefer a similar available agent such as `Explore` first, and `codebase-context-gatherer` second, instead of retrying a flaky helper path.

<system_tools>

# 💻 SYSTEM_TOOL_INVENTORY

### 🛠 CORE UTILITIES: Search, Analysis & Refactoring

- **ripgrep** (`rg`) `v14.1.0` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Primary text search engine.
  - **Capabilities:** Ultra-fast regex search, ignores `.gitignore` by default.
- **fd** (`fd`) `v10.3.0`
  - **Context:** File system traversal.
  - **Capabilities:** User-friendly, fast alternative to `find`.
- **tokei** (`tokei`) `v12.1.2` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Codebase Statistics.
  - **Capabilities:** Rapidly counts lines of code (LOC), comments, and blanks across all languages.
- **ast-grep** (`sg`) `v0.40.0` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Advanced Refactoring & Linting.
  You are operating in an environment where ast-grep is installed. For any code search that requires understanding of syntax or code structure, you should default to using ast-grep --lang [language] -p '<pattern>'. Adjust the --lang flag as needed for the specific programming language. Avoid using text-only search tools unless a plain-text search is explicitly requested.
  - **Capabilities:** Structural code search and transformation using Abstract Syntax Trees (AST). Supports precise pattern matching and large-scale automated refactoring beyond regex limitations.
- **bat** (`bat`) `v0.26.0`
  - **Context:** File Reading.
  - **Capabilities:** `cat` clone with automatic syntax highlighting and Git integration.
- **sd** (`sd`) `v1.0.0`
  - **Context:** Text Stream Editing.
  - **Capabilities:** Intuitive find & replace tool (simpler `sed` replacement).
- **jq** (`jq`) `v1.8.1`
  - **Context:** JSON Parsing.
  - **Capabilities:** Command-line JSON processor/filter.
- **yq** (`yq`) `v4.48.2`
  - **Context:** Structured Data Parsing.
  - **Capabilities:** Processor for YAML, TOML, and XML.
- **Semgrep** (`semgrep`) `v1.140.0`
  - **Capabilities:** Polyglot Static Application Security Testing (SAST) and logic checker.

### 🌐 SECONDARY RUNTIMES

- **Node.js** (`node`) `v24.11.1` - JavaScript runtime.
- **Bun** (`bun`) `v1.3.1` - All-in-one JS runtime, bundler, and test runner.
- **Java** (`java`) `JDK 25 & javafx 25` - Java Development Kit.

</system_tools>


## Code quality and design principles
Optimize for correctness, clarity, and maintainability first.
Use structure and constraints as guidance, not rigid rules.

### Core principles
- Prefer clear, readable, and correct code over strict adherence to style rules.
- Keep solutions as simple as possible, but not simpler than the problem requires.
- Avoid unnecessary complexity, but do not under-engineer solutions that require proper structure.
- Match the level of design to the scope of the task (small fix vs. system change).

### Determinism and side effects
- Aim for deterministic behavior where possible: same inputs → same outputs.
- Isolate side effects and make them explicit.
- Seed or control randomness when determinism is required.

### Contracts and boundaries
- Use explicit contracts where they provide real value: types, validation, and clear pre/post conditions.
- Prefer strong typing and clear interfaces over implicit assumptions.
- Use schemas (OpenAPI, JSON Schema, Protobuf) only when there is a real boundary (API, serialization, cross-service communication).

### Modularity and structure
- Prefer small, focused functions and modules, but do not fragment logic excessively.
- Each function or module should have a clear responsibility (SRP).
- Group related logic together when it improves readability and understanding.

### Idempotency and state
- Prefer idempotent operations where appropriate (safe retries, predictable state transitions).
- Make state transitions explicit and easy to reason about.

### Observability
- Provide meaningful errors and logs where they help debugging and system understanding.
- Avoid excessive or noisy logging.

### Public surface and stability
- Keep public APIs stable and intentional.
- When relevant, follow semantic versioning and maintain clear change intent.

### Heuristics (not strict limits)
These are guidelines, not hard constraints. Break them when doing so improves clarity or correctness.

- Function length: aim for ~20–100 lines
- Nesting depth: aim for ≤ 5
- Cyclomatic complexity: aim for ≤ 10

If exceeding these improves readability or avoids unnecessary fragmentation, prefer the clearer solution.


## Operating principle
Optimize for the user's requested outcome first.

-  Default to the smallest correct change ONLY when it fully satisfies the request.
-  Do-NOT minimize the diff when the task requires broader changes such as redesign, refactor, cleanup, migration, or consistency fixes.
-  If a narrow fix would leave the system semantically inconsistent, incomplete, misleading, or fragile, prefer a broader, coherent solution.
-  Do-NOT avoid expanding scope just because a smaller patch is possible.
-  Avoid unrelated refactors, BUT include all necessary related changes required to correctly complete the task.
-  Treat these instructions as defaults, not constraints. Override them when the task clearly requires it.


## Source of truth = the actual code
The codebase is the only reliable source of truth.
If any instruction file, documentation, or comment disagrees with the code, trust the code:
- `src/main/java`
- `src/test/java`
- `pom.xml`

### Rules for using documentation
- Documentation is often stale or incorrect. Treat it as non-authoritative.
- Use documentation only for high-level context, naming, or intent — never as proof of behavior.
- Do not assume documentation is correct unless it is verified against the actual code.

### Verification requirement
- Always confirm behavior, structure, and logic directly in the code before relying on it.
- Do not base decisions solely on documentation without checking the corresponding implementation.

### Conflict resolution
-  If documentation and code disagree, ignore the documentation.
-  Do-NOT attempt to “reconcile” differences by guessing — follow the code EXACTLY.
-  If the code appears inconsistent or unclear, investigate further in the codebase rather than relying on documentation.

### Failure mode to avoid
Relying on outdated documentation instead of the code will lead to incorrect changes, broken behavior, or invalid assumptions. Avoid this completely.


## Environment defaults

- Windows 11
- PowerShell 7.6
- VS Code Insiders
- Java 25 with preview enabled
- JavaFX 25.0.2
- Maven
- Palantir Java Format / Spotless
- Java by Red Hat extension

Use PowerShell-friendly commands.

When Maven test selection uses a comma-separated `-Dtest=...,...` list, prefer:

```powershell
mvn --% test -Dtest=TestClass1,TestClass2
```

# Dating App - Copilot Instructions

> **Updated:** 2026-03-30
> **Role in the instruction stack:** highest-level repo guidance.
> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`.


## Patch and direct-edit policy

- If a patch fails, re-read the file and retry once with narrower context; if it still fails, switch to a more precise edit strategy instead of looping.
- Avoid delete-plus-add of the same path in one patch unless there is no safer alternative.
- Treat the tool result payload as the source of truth for patch/edit success; do not rely on top-level tool status alone.
- After any meaningful patch or direct edit, re-read the touched region and run the appropriate error/test validation for the scope of the change.

## Always-on repo rules

- Keep `core/` framework-agnostic. No UI, database, REST, or Jackson annotations in domain/core types.
- Treat `ServiceRegistry` and `ViewModelFactory` as the production composition roots.
- Prefer `app/usecase/*` as the application boundary for business flows.
- Use `AppClock` as the authoritative time source in domain and service logic: prefer `AppClock.now()` / `AppClock.today()`, or inject `AppClock.clock()` when a service is intentionally clock-based.
- Use deterministic pair IDs via `generateId(UUID a, UUID b)` for two-user aggregates.
- `ProfileNote` is standalone: `datingapp.core.model.ProfileNote`.
- Import nested enums from owner types:
  - `User.Gender`, `User.UserState`, `User.VerificationMethod`
  - `Match.MatchState`, `Match.MatchArchiveReason`
- Use injected `AppConfig` in runtime code; keep `AppConfig.defaults()` at bootstrap/composition/test boundaries.
- PostgreSQL runtime support now goes through `StorageFactory.buildSqlDatabase(...)`; keep `buildH2(...)` and `buildInMemory(...)` as the H2-backed compatibility/test paths.
- Record-typed JDBI parameters should use `@BindMethods`, not `@BindBean`.
- ViewModels should use `BaseViewModel`, `ui/async/*`, and `UiDataAdapters` instead of ad-hoc threading or direct storage coupling.
- When behavior is config-driven, update all config surfaces together: `AppConfig`, `AppConfigValidator`, runtime config files under `config/`, and the relevant config loader/tests.
- When changing behavior that exists in both in-memory test storage and JDBI storage, keep both implementations semantically aligned and verify both paths instead of fixing only one side.
- When changing relationship, matching, or candidate-eligibility behavior, verify the full seam set in one pass when relevant: core service/policy, use-case layer, REST adapter, and the most relevant storage-backed tests.

## Search, edit, and execution defaults

- Prefer `ast-grep` / syntax-aware search when code structure matters.
- Use focused subagents for independent read-only investigation or execution batches.
- If one search/exploration helper path fails because of model or environment instability, switch immediately to a similar available agent instead of retrying the same failing path.
- Make the smallest correct change first when appropriate, but do not limit yourself when broader changes are necessary.
- Avoid unrelated refactors while implementing a task.
- Prefer validating PostgreSQL changes against an existing local PostgreSQL instance first; use Docker only as an optional disposable fallback when no local PostgreSQL server is available(we will use docker as the app matures further).

## Planning, review, and meta-workflow defaults

- For planning, documentation, workflow-analysis, and other non-implementation deliverables, prefer exact match to the source code over assumptions.
- Only make simple harmless assumptions when they are directly stated by the user or are explicitly labeled as low-risk assumptions.
- Use a full targeted context pass first: exact seam confirmation, symbol existence, test surfaces, composition-root paths, targeted searching, and selective reads.
- Read full files only for a small number of especially important relevant files when doing so is clearly beneficial.
- Do not add append-only "Second Pass", "Corrections", or "Additions" sections to review, audit, or analysis documents. When new findings or corrections appear, revise the relevant existing section in place so the file remains a single coherent canonical document.
- Before opening another review loop, first aggregate what else can be checked in that same pass: related blockers, related improvements, adjacent risks, and useful notes that can save time later.
- Distinguish clearly between fixes and improvements. Fixes are critical blockers and should not be postponed. Improvements are optional. If only optional improvements remain, gather them all and present them together at the end instead of opening another review cycle for each one separately.
- Before starting a third independent review pass for a planning, documentation, or meta-workflow deliverable, summarize the reasons and expected value, then ask the user via `vscode_askQuestions` whether to proceed.

## Verification standard

Before claiming work is complete:

1. Check touched files for errors.
2. Run targeted tests for the changed area.
3. Run broader smoke coverage if multiple subsystems changed.
4. Run `mvn spotless:apply verify` before concluding substantial work.
5. Use `.\run_verify.ps1` as the repo-level full local verification path when you need the Maven gate plus routine PostgreSQL smoke validation.

## Canonical commands

```powershell
# Build and run CLI
mvn compile && mvn exec:exec

# Run JavaFX UI
mvn javafx:run

# Run the full test suite
mvn test

# Full local verification (Maven quality gate + PostgreSQL smoke)
.\run_verify.ps1

# Final repo-wide quality gate
mvn spotless:apply verify

# Optional direct local PostgreSQL runtime validation helpers
.\start_local_postgres.ps1
.\run_postgresql_smoke.ps1
.\stop_local_postgres.ps1
```

## Read next

- `CLAUDE.md` — verified repo map, architecture snapshot, gotchas, and key files
- `AGENTS.md` — execution workflow, tool discipline, and validation order
