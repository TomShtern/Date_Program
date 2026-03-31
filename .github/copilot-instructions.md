we are on windows 11, usually using powershell, we are working in VS Code-Insiders (sometimes in IntelliJ). we are using java 25, and using javafx 25, maven, palantir format/style and the java by Red Hat extension.
make sure to leverage the tools you have as an ai coding agent together with the IDE tools and also the tools we have here on this system.

You are operating in an environment where ast-grep is installed. For any code search that requires understanding of syntax or code structure, you should default to using ast-grep --lang [language] -p '<pattern>'. Adjust the --lang flag as needed for the specific programming language. Avoid using text-only search tools unless a plain-text search is explicitly requested.

Deploy multiple parallel subagents for different tasks when needed, and coordinate their work through a parent agent. Use the `agent` tool to invoke subagents with specific instructions and context. For example, you might have one subagent focused on code analysis using ast-grep, while another handles code edits or refactoring. The parent agent can manage the overall workflow, ensuring that each subagent has the information it needs to perform its task effectively while keeping the process organized and efficient.
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

Optimize for: predictable, minimal, contract-driven code
Determinism: same inputs → same outputs. Seed randomness and isolate side effects.
Explicit contracts: types + schemas + pre/post conditions. Use OpenAPI/JSON Schema/Protobuf where relevant.
Small modules & functions: tiny units of behavior that are easy to test and swap.
Idempotent interfaces: safe to retry; clear state transitions.
Observability: structured logs, metrics, clear errors — machines need signals.
Stable public surface: semantic versioning, changelogs, and strict backward-compat rules.
Concrete rules / thresholds (safe defaults)
Max function length: ≤ 85 lines.
Max nesting depth: ≤ 4.
Cyclomatic complexity per function: ≤ 9.
One responsibility per function/module (SRP).


# Dating App - Copilot Instructions

> **Updated:** 2026-03-30
> **Role in the instruction stack:** highest-level repo guidance.
> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`.

## Source of truth

If any instruction file disagrees with the codebase, trust:

- `src/main/java`
- `src/test/java`
- `pom.xml`

Use the lower-level docs only to explain or operationalize what the code already does.

## Environment defaults

- Windows 11
- PowerShell 7.6
- VS Code Insiders (sometimes IntelliJ)
- Java 25 with preview enabled
- JavaFX 25.0.2
- Maven
- Palantir Java Format / Spotless
- Java by Red Hat extension

Use PowerShell-friendly commands. When Maven test selection uses a comma-separated `-Dtest=...,...` list, prefer:

`mvn --% ...`

## Patch and direct-edit policy

- Prefer direct, exact-match edit tools for tiny localized changes such as a single import, one method call, or one small block replacement.
- Reserve `apply_patch` for multi-line structural edits, multi-hunk changes, coordinated multi-file changes, or explicit file add/delete/rename work.
- Keep patches as small and local as possible; avoid broad whole-file rewrites when a narrower edit will do.
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
- Record-typed JDBI parameters should use `@BindMethods`, not `@BindBean`.
- ViewModels should use `BaseViewModel`, `ui/async/*`, and `UiDataAdapters` instead of ad-hoc threading or direct storage coupling.
- When behavior is config-driven, update all config surfaces together: `AppConfig`, `AppConfigValidator`, runtime config files under `config/`, and the relevant config loader/tests.
- When changing behavior that exists in both in-memory test storage and JDBI storage, keep both implementations semantically aligned and verify both paths instead of fixing only one side.
- When changing relationship, matching, or candidate-eligibility behavior, verify the full seam set in one pass when relevant: core service/policy, use-case layer, REST adapter, and the most relevant storage-backed tests.

## Search, edit, and execution defaults

- Prefer `ast-grep` / syntax-aware search when code structure matters.
- Use focused subagents for independent read-only investigation or execution batches.
- If one search/exploration helper path fails because of model or environment instability, switch immediately to a similar available agent instead of retrying the same failing path.
- Make the smallest correct change first.
- Avoid unrelated refactors while implementing a task.

## Planning, review, and meta-workflow defaults

- For planning, documentation, workflow-analysis, and other non-implementation deliverables, prefer exact match to the source code over assumptions.
- Only make simple harmless assumptions when they are directly stated by the user or are explicitly labeled as low-risk assumptions.
- Use a full targeted context pass first: exact seam confirmation, symbol existence, test surfaces, composition-root paths, targeted searching, and selective reads.
- Read full files only for a small number of especially important relevant files when doing so is clearly beneficial.
- Before opening another review loop, first aggregate what else can be checked in that same pass: related blockers, related improvements, adjacent risks, and useful notes that can save time later.
- Distinguish clearly between fixes and improvements. Fixes are critical blockers and should not be postponed. Improvements are optional. If only optional improvements remain, gather them all and present them together at the end instead of opening another review cycle for each one separately.
- Before starting a third independent review pass for a planning, documentation, or meta-workflow deliverable, summarize the reasons and expected value, then ask the user via `vscode_askQuestions` whether to proceed.

## Verification standard

Before claiming work is complete:

1. Check touched files for errors.
2. Run targeted tests for the changed area.
3. Run broader smoke coverage if multiple subsystems changed.
4. Run `mvn spotless:apply verify` before concluding substantial work.

## Canonical commands

```powershell
# Build and run CLI
mvn compile && mvn exec:exec

# Run JavaFX UI
mvn javafx:run

# Run the full test suite
mvn test

# Final repo-wide quality gate
mvn spotless:apply verify
```

## Read next

- `CLAUDE.md` — verified repo map, architecture snapshot, gotchas, and key files
- `AGENTS.md` — execution workflow, tool discipline, and validation order
