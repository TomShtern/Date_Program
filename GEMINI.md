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
This is a **High-Integrity Clean Architecture** project. We prioritize domain purity over framework convenience, while being pragmatic about wiring and persistence.
- **Pure Domain Models:** The `core/` package focuses on POJOs and Records. Business logic lives here.
- **Fail-Fast:** Validate inputs in constructors using `Objects.requireNonNull` and state logic using `IllegalStateException`.
- **Deterministic:** Logic (IDs, Daily Picks, Scores) must be reproducible.
- **Centralized Config:** All logic thresholds and limits are in `AppConfig`.

## 🏗 Architectural Layers

### 1. Domain Models (`core/`)
- **Immutables:** Use `record` for Value Objects (e.g., `Like`, `PacePreferences`).
- **Entities:** Use `class` for stateful objects (e.g., `User`, `Match`).
- **State Machines:**
  - `User`: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
  - `Match`: `ACTIVE → FRIENDS | UNMATCHED | GRACEFUL_EXIT | BLOCKED`
- **Interfaces:** Storage interfaces (e.g., `UserStorage`) reside here to invert dependencies.

### 2. Storage & Persistence (`storage/`)
- **JDBI Pattern:** Use JDBI 3 Declarative SQL (`@SqlUpdate`, `@SqlQuery`) in `datingapp.storage.jdbi`.
- **Jackson:** Used ONLY in `storage/` layer for JSON serialization of complex data (e.g., `notifications.data_json`).
- **Schema:** Managed in `DatabaseManager.initializeSchema()` using `IF NOT EXISTS`.
- **Testing:** Integration tests use H2 in-memory mode.

### 3. Dependency Injection (The Wiring Mandate)
We use a manual Registry pattern. **CRITICAL:** If adding a new component, you MUST update:
1. `ServiceRegistry.java` (in `core`): Define the field and getter.
2. `ServiceRegistry.Builder` (inner class): Instantiate the service/storage in `buildH2()`.
3. `ViewModelFactory.java` (JavaFX): Wire the new service to the UI.

## 📖 Project Dictionary
- **Candidate:** A potential match found by `CandidateFinder`.
- **Match:** A mutual like between two users. Deterministic ID: `minID_maxID`.
- **Dealbreaker:** A strict filter (lifestyle/age) that disqualifies a candidate immediately.
- **Interest:** One of 39 predefined enums across 6 categories.
- **Daily Pick:** A single, seeded-random daily recommendation.
- **PacePreferences:** User preferences for dating speed (Messaging Frequency, Time to First Date, etc.).

## 🛠 Coding Standards

- **Temporal Logic:** Use `java.time.Instant` for all timestamps. Never use `Date` or `Calendar`.
- **Spatial Logic:** Use `GeoUtils.calculateDistance()` (Haversine formula).
- **Naming:** Follow standard Java conventions. Storage interfaces end in `Storage`, implementations start with `Jdbi` or `H2`.
- **Logging:** Use `slf4j` placeholders. `logger.info("Match created: {}", id);`
- **Configuration:** Use `AppConfig` for all magic numbers (timeouts, limits, weights).
- **UI Architecture:**
  - **JavaFX 25:** Modern UI using AtlantaFX and Ikonli.
  - **Structure:** MVVM Pattern (`ui/viewmodel` provides state to `ui/controller`).
  - **CLI:** Handlers reside in `app/cli/` (e.g., `ProfileHandler`).

## 🚫 Anti-Patterns (NEVER Do These)
- **NO Framework Annotations:** Do NOT use Spring's `@Service`, `@Repository`, or `@Autowired`.
- **NO Star Imports:** Always import specific classes.
- **NO Hardcoded Config:** Use `AppConfig` for logic variables (distance, limits, etc.).
- **NO System.out:** Use the logger.
- **NO Manual SQL Migrations:** Use `DatabaseManager` schema initialization.

## 🛠 Feature Implementation Algorithm
1. **Model:** Create record/enum/class in `core/`.
2. **Storage Interface:** Add `*Storage` interface in `core/storage`.
3. **Storage Impl:** Add `Jdbi*Storage` interface (SQLObject) in `storage/jdbi`.
4. **Schema:** Add/Update SQL in `DatabaseManager.initializeSchema()`.
5. **Service:** Create logic in `core/` using constructor injection.
6. **Registry:** Wire in `ServiceRegistry.Builder` (update `buildH2`).
7. **UI:** Add/Update `ViewModel` and `Controller` (JavaFX) or `*Handler` (CLI).
8. **Verify:** Run `mvn spotless:apply && mvn verify`.

## 🧪 Testing Guidelines
- **Unit Tests:** Grouped by `@Nested`. NO Mockito. Use real instances or manual fakes.
- **Integration Tests:** Use real H2 database via `DatabaseManager`.
- **Location:** `src/test/java/datingapp/core/` (logic) and `src/test/java/datingapp/storage/` (DB).

## 💻 Environment & Tools
- **OS:** Windows 11 (PowerShell `chcp 65001` for UTF-8).
- **IDE:** Antigravity (VS Code Fork).
- **Search:** Prefer `ast-grep` (sg) for structural code changes and `ripgrep` (rg) for text.
- **Verification:** `mvn verify` runs Checkstyle, PMD, Spotless, and Tests (JUnit 5).
- **Code Style:** Spotless enforced (Palantir Java Format).
- **Quality Gates:** JaCoCo (60% core coverage). Checkstyle/PMD are non-blocking.
- **Secrets:** `DATING_APP_DB_PASSWORD` environment variable used for DB password.
- **Concurrency:** Java 25 Virtual Threads enabled (`--enable-preview`) but not yet adopted in core logic.




## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-01-30 18:50:00|agent:antigravity|docs|Update GEMINI.md to reflect current tech stack (JDBI, Jackson, JavaFX 25) and architecture|GEMINI.md
---AGENT-LOG-END---
