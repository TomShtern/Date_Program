## Environment defaults
- Windows 11
- PowerShell 7.6
- VS Code Insiders
- Java 25.0.2 with preview enabled
- JavaFX 25.0.2
- Maven
- Palantir Java Format / Spotless
- Java by Red Hat extension

make sure to leverage the tools you have as an ai coding agent together with the IDE tools and also the tools we have here on this system.
Use PowerShell-friendly commands.

Deploy multiple parallel subagents for different tasks when needed, and coordinate their work through a parent agent. Use the `agent` tool to invoke subagents with specific detailed and defined instructions/tasks and context. For example, you might have one subagent focused on code analysis using ast-grep, while another handles code edits or refactoring. The parent agent can manage the overall workflow, ensuring that each subagent has the information it needs to perform its task effectively while keeping the process organized and efficient.
The goal is to have you, the parent agent, orchestrate the work of multiple specialized subagents to achieve complex tasks that require different types of expertise or operations, such as code analysis, refactoring, testing, and documentation. Each subagent can focus on its specific area while you coordinate their efforts to ensure a cohesive and efficient workflow.
Dont forget to use the specialized agents when appropriate, such as the executionSubagent for executing shell commands, or the runSubagent command. For read-only codebase exploration, prefer a similar available agent such as `Explore` first, and `codebase-context-gatherer` second, instead of retrying a flaky helper path.
- If one search/exploration helper path fails because of model or environment instability, switch immediately to a similar available agent instead of retrying the same failing path.

   </system_tools>

# 💻 SYSTEM_TOOL_INVENTORY

### 🛠 CORE UTILITIES: Search, Analysis & Refactoring
- **ast-grep** (`sg`) ≥ v0.42.1
  Structural code search and refactoring.
  Prefer using `ast-grep --lang [language] -p '<pattern>'` for syntax-aware queries. Use LSP/IDE tools when symbol resolution or project context is a better fit. Avoid text-only search unless plain-text matching is explicitly needed.
- **rg** (`ripgrep`) ≥ v14.1.0
  Primary plain-text search.
- **fd** ≥ v10.4.2
  Fast file discovery.
- **fzf** ≥ v0.71.0
  Fuzzy finder for files, commands, history.
- **bat** ≥ v0.26.1
  Cat with syntax-highlighted file viewing.
- **eza** ≥ v0.23.4
  Enhanced `ls` / tree / git status.
- **sd** ≥ v1.1.0
  Simple stream find/replace.
- **jq** ≥ v1.8.1
  JSON parsing/filtering.
- **yq** ≥ v4.52.5
  YAML / TOML / XML processing.
- **tokei** ≥ v12.1.2
  Fast LOC / comments / blanks stats.
- **grex** ≥ v1.4.6
  Regex generation from examples.
- **tldr++** (`tldr`) ≥ v0.6.1
  Short command help with practical examples.

### 🔀 CONFIGURED GIT DIFF SETUP
- **delta** ≥ v0.19.2
  Configured to be default pager for `git diff`, line-level/side-by-side diffs, `git log -p`, and `git show`.
- **difftastic** (`difft`) ≥ v0.68.0
  Structural syntax-aware diff tool for AST-aware comparisons.
- Use `git difftool --tool=difftastic -- <file>` for Git diffs.
- Use `difft file1 file2` directly outside Git.

### 📊 CODE STATISTICS & TAGGING & DIAGNOSTICS
- **scc** ≥ v3.7.0
  Fast code counting, COCOMO estimation, complexity calculation
- **universal-ctags** (`ctags`) ≥ v6.1.0
  Tag files for symbol lookup and navigation for indexing.
- **hyperfine** ≥ v1.20.0
  Command-line benchmarking and timing comparisons analysis.

### 🌐 RUNTIMES & COMPILERS
- **Java** (`java`) `JDK 25.0.2 & JavaFX 25.0.2` - Java Development Kit (OpenJDK LTS).
- **Bun** (`bun`) `v1.3.12` - All-in-one JS runtime, bundler, and test runner.
- **Maven** (`mvn`) `v3.9.14` - Java build and dependency management.

      </system_tools>


   <code_guidelines>

## Patch and direct-edit policy
- On patch failure, re-read with narrower context and retry once; if it still fails, switch to a more precise edit strategy rather than looping.
- Avoid delete-plus-add of the same path in one patch unless no safer alternative exists.
- Trust the tool result payload (not top-level status) to confirm patch/edit success.
- After any meaningful edit, re-read the touched region and validate with appropriate errors/tests for the change scope.

## Code quality and design principles
Optimize for correctness, clarity, and maintainability first. Use structure and constraints as guidance, not rigid rules.

### Prefer
- Clear, readable, correct code over strict style compliance.
- The simplest solution that fully solves the problem, do not limit yourself when broader changes are necessary.
- Enough structure for the task, but no unnecessary complexity.
- Design matched to scope: small fix, refactor, or system change.
- Avoid unrelated refactors while implementing a task.

### Code shape
- Aim for deterministic behavior: same inputs → same outputs.
- Isolate side effects and make them explicit.
- Seed or control randomness when determinism is required.
- Use explicit contracts when they add real value: types, validation, and clear pre/post conditions.
- Prefer strong typing and clear interfaces over implicit assumptions.
- Use schemas only at real boundaries: API, serialization, cross-service communication.
- Prefer small, focused functions and modules, but do not fragment logic excessively.
- Keep related logic together when it improves readability.
- Give each function and module a clear responsibility (SRP).
- Prefer idempotent operations where appropriate.
- Make state transitions explicit and easy to reason about.
- Provide meaningful errors and logs where they help debugging and understanding.
- Avoid excessive or noisy logging.
- Keep public APIs stable and intentional.
- Use semantic versioning and clear change intent when relevant.

### Heuristics, not hard rules
- Function length: ~20–100 lines
- Nesting depth: ≤ 5
- Cyclomatic complexity: ≤ 9

## Operating principle
Optimize for the user's requested outcome first.
- Default to the smallest correct change only when it fully satisfies the request.
- Do not minimize the diff when the task requires redesign, refactor, cleanup, migration, or consistency fixes.
- If a narrow fix would leave the system semantically inconsistent, incomplete, misleading, or fragile, prefer a broader, coherent solution.
- Avoid unrelated refactors, but include all necessary related changes.
- Treat these instructions as defaults, not constraints. Override them when the task clearly requires it.

## Source of truth = the actual code
The codebase is the only reliable source of truth. If any instruction file, documentation, or comment disagrees with the code, trust the code:
- `src/main/java`
- `src/test/java`
- `pom.xml`

## Verification & Conflict resolution
- Always confirm behavior, structure, and logic directly in the code before relying on it.
- Do not rely on documentation without checking the implementation.
- If documentation and code disagree, ignore the documentation.
- Do not guess, and do not assume. Investigate the codebase instead.
-  If the code appears inconsistent or unclear, investigate further in the codebase rather than relying on documentation.

## Documentation
- Documentation is mostly stale or incorrect. Treat it as non-authoritative.
- Use documentation only for high-level context, naming, or intent, never as proof of behavior.

## Failure mode to avoid
Relying on outdated documentation instead of the code will lead to incorrect changes, broken behavior, or invalid assumptions. Avoid this completely.

   > **Break any of these when it improves clarity, readability, or avoids unnecessary fragmentation.**

   </code_guidelines>



  <Java_LSP>
---
description: Unconditionally loaded for all Java projects. Provides Java LSP tools (lsp_java_*) for compiler-accurate code navigation — significantly faster and more precise than grep_search, search_subagent, or read_file for locating Java symbols, callers, implementations, and types.
applyTo: '*'
---

# Java LSP Tools — Mandatory Initialization

These tools return structured results in ~20–100 tokens vs ~500–3000 tokens from `grep_search`, with ZERO FALSE POSITIVIES.

## Step 1: Load Tools (REQUIRED — do this FIRST)
Call `tool_search_tool_regex` **twice** (max 5 per call):
- **Call 1:** `lsp_java_findSymbol|lsp_java_getFileStructure|lsp_java_getFileImports`
- **Call 2:** `lsp_java_getCallHierarchy|lsp_java_getTypeHierarchy|lsp_java_getTypeAtPosition`
All 6 must load before touching `.java` files. Missing? Retry once.

## Step 2: Always Prefer LSP Tools for Java
- **Find definition** → `findSymbol`
- **File outline** → `getFileStructure`
- **Callers** → `getCallHierarchy("incoming")`
- **Implementations** → `getTypeHierarchy("subtypes")`
- **Resolve types** → `getTypeAtPosition`
- **Imports** → `getFileImports`

Always use lsp_java_* tools first for .java navigation. You may additionally use grep_search, semantic_search, search_subagent, or runSubagent ONLY when they offer something the LSP tools clearly cannot (e.g. searching string literals, comments, or non-compilable snippets).

Workflow: **findSymbol → getFileStructure → targeted tool → read_file (specific lines only)**
**Still use `grep_search`** for non-Java files, string literals, and comments.
**Before every `.java` operation:** "Is there an `lsp_java_*` tool for this?" → If yes, use it.
## Fallback
- Empty results → retry shorter keyword, then `grep_search`
- Tool error / jdtls not ready → fall back to `read_file` + `grep_search` (retry once max)

  </Java_LSP>


# Always-on repo rules

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


# GENERAL DEVELOPMENT OBSERVATIONS & DIRECTIONS

## Patterns, Pitfalls, and Practical Guidance Learned During Development:
- Read full files only for a small number of especially important relevant files when doing so is clearly beneficial.
- Do not add append-only "Second Pass", "Corrections", or "Additions" sections to review, audit, or analysis documents. When new findings or corrections appear, revise the relevant existing section in place so the file remains a single coherent canonical document.
-When reviewing, before opening another review loop, first aggregate what else can be checked in that same pass: related blockers, related improvements, adjacent risks, and useful notes that can save time later.
- Distinguish clearly between fixes and improvements. Fixes are critical blockers and should not be postponed. Improvements are optional(but must be mentioned). If only optional improvements remain, gather them all and present them together at the end instead of opening another review cycle for each one separately.
- When Maven test selection uses a comma-separated `-Dtest=...,...` list, prefer:
```powershell
mvn --% test -Dtest=TestClass1,TestClass2
```

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
# Final repo-wide quality gate
mvn spotless:apply verify
# Optional direct local PostgreSQL runtime validation helpers
.\start_local_postgres.ps1
.\run_postgresql_smoke.ps1
.\stop_local_postgres.ps1
```


# Dating App - Copilot Instructions

> **Updated:** 2026-04-14
> **Role in the instruction stack:** Definitive highest-level repo guidance.
> **Hierarchy:** `.github/copilot-instructions.md` → `CLAUDE.md` → `AGENTS.md`.
- `CLAUDE.md` — verified repo map, architecture snapshot, gotchas, and key files
- `AGENTS.md` — execution workflow, tool discipline, and validation order