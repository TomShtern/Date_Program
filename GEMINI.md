<!--AGENT-DOCSYNC:ON-->
# ChangeStamp format: SEQ|YYYY-MM-DD HH:MM:SS|agent:<id>|scope:<tag>|summary|files
# SEQ: file-local increasing int. If collision after 3 retries append "<SEQ>:CONFLICT".
# Agents MAY NOT use git. After code changes they MUST:
# 1) pick SEQ = highestSEQ+1 (recheck before write),
# 2) locate affected doc fragment using prioritized search (see below),
# 3) archive replaced text with <!--ARCHIVE:SEQ:agent:scope-->...<!--/ARCHIVE-->,
# 4) apply minimal precise edits (edit only nearest matching fragment),
# 5) append one ChangeStamp line to the file-end changelog and inside the edited fragment (immediately after the edited paragraph or code fence),
# 6) if uncertain to auto-edit, append TODO+ChangeStamp next to nearest heading.
<!--/AGENT-DOCSYNC-->



# GEMINI.md - AI Agent Operational Context (The Gold Standard)

## 🧠 Project Persona & Philosophy
This is a **High-Integrity Clean Architecture** project. We prioritize domain purity over framework convenience.
- **Pure Core:** The `core/` package is a "POJO-only" zone. No JDBC, no Jackson, no JavaFX.
- **Fail-Fast:** Validate inputs in constructors using `Objects.requireNonNull` and state logic using `IllegalStateException`.
- **Deterministic:** Logic (IDs, Daily Picks, Scores) must be reproducible.

## 🏗 Architectural Layers

### 1. Domain Models (`core/`)
- **Immutables:** Use `record` for Value Objects (e.g., `Like`, `MatchQuality`).
- **Entities:** Use `class` for stateful objects (e.g., `User`, `Match`).
- **State Machines:**
  - `User`: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
  - `Match`: `ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED`

### 2. Storage & Persistence (`storage/`)
- **JDBC Pattern:** Manual JDBC with `try-with-resources`. Wrap `SQLException` in `StorageException`.
- **Schema:** Managed in `DatabaseManager.initSchema()` using `IF NOT EXISTS`.
- **Testing:** **NO Mockito.** Write `InMemory*Storage` implementations for unit tests.

### 3. Dependency Injection (The Wiring Mandate)
We use a manual Registry pattern. **CRITICAL:** If adding a new component, you MUST update:
1. `ServiceRegistry.java`: Define the field and getter.
2. `ServiceRegistryBuilder.java`: Instantiate in `buildH2()` and `buildInMemory()`.
3. `Main.java` (CLI) or `ViewModelFactory.java` (JavaFX): Wire the new service to the UI.

## 📖 Project Dictionary
- **Candidate:** A potential match found by `CandidateFinder`.
- **Match:** A mutual like between two users. Deterministic ID: `minID_maxID`.
- **Dealbreaker:** A strict filter (lifestyle/age) that disqualifies a candidate immediately.
- **Interest:** One of 37 predefined enums across 6 categories.
- **Daily Pick:** A single, seeded-random daily recommendation.

## 🛠 Coding Standards

- **Temporal Logic:** Use `java.time.Instant` for all timestamps. Never use `Date` or `Calendar`.
- **Spatial Logic:** Use `GeoUtils.calculateDistance()` (Haversine formula).
- **Naming:** Follow standard Java conventions. Storage interfaces end in `Storage`, implementations start with `H2`.
- **Logging:** Use `slf4j` placeholders. `logger.info("Match created: {}", id);`
- **UI Architecture:**
  - **CLI:** Handlers (`cli/`) use `InputReader` and `CliConstants`.
  - **JavaFX:** Models -> ViewModels (`ui/viewmodel`) -> Controllers (`ui/controller`).

## 🚫 Anti-Patterns (NEVER Do These)
- **NO Framework Annotations:** Do NOT use `@Service`, `@Repository`, or `@Autowired`.
- **NO Star Imports:** Always import specific classes.
- **NO Hardcoded Config:** Use `AppConfig` for logic variables (distance, limits, etc.).
- **NO System.out:** Use the logger.
- **NO Manual SQL Migrations:** Use `DatabaseManager` schema initialization.

## 🛠 Feature Implementation Algorithm
1. **Model:** Create record/enum/class in `core/`.
2. **Storage:** Add interface in `core/` and `H2*` implementation in `storage/`.
3. **Schema:** Add SQL to `DatabaseManager`.
4. **Service:** Create logic in `core/` using constructor injection.
5. **Registry:** Wire in `ServiceRegistryBuilder` (both H2 and InMemory paths).
6. **UI:** Add menu option in `Main.java` and logic in a `*Handler` (CLI) or `ViewModel` (JavaFX).
7. **Verify:** Run `mvn spotless:apply && mvn verify`.

## 🧪 Testing Guidelines
- **Unit Tests:** Grouped by `@Nested`. Use in-memory storage mocks.
- **Integration Tests:** Use a real H2 test database.
- **Location:** `src/test/java/datingapp/core/` (logic) and `src/test/java/datingapp/storage/` (DB).

## 💻 Environment & Tools
- **OS:** Windows 11 (PowerShell `chcp 65001` for UTF-8).
- **IDE:** Antigravity (VS Code Fork).
- **Search:** Prefer `ast-grep` (sg) for structural code changes and `ripgrep` (rg) for text.
- **Validation:** `mvn verify` runs Checkstyle, PMD, Spotless, and Tests.




## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
---AGENT-LOG-END---
