# Consolidated Codebase Audit — Storage & Database (February 6, 2026)

**Category:** JDBI storage, mapping, database configuration, and query efficiency
**Sources:** Kimmy 2.5, Grok, Opus 4.6 (core + storage/UI), GPT-5 Mini, Raptor Mini, GPT-4.1
**Scope:** Full codebase — ~119 production Java files (~19K LOC), 37 test files
**Total Unique Findings:** 75+
<!-- ChangeStamp: 1|2026-02-06 17:29:51|agent:codex|scope:audit-group-storage|Regroup audit by problem type (storage/database)|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT_PROBLEMS_STORAGE.md -->

---

## Issues

### H-11: N+1 Query Patterns in Multiple Services

**Files:** `core/MatchingService.java`, `core/StandoutsService.java`, `core/MessagingService.java`, `ui/viewmodel/MatchesViewModel.java`
**Source:** Grok, Opus 4.6, Raptor Mini, Storage/UI Audit

Multiple services call `userStorage.get(id)` in loops. For 50 items → 50+ separate DB round-trips.

**Fix:** Add `UserStorage.findByIds(Set<UUID>)` batch method and use it everywhere.

---

### H-16: `JdbiUserStorage.readEnumSet()` — No Try-Catch on `Enum.valueOf`

**File:** `storage/jdbi/JdbiUserStorage.java`
**Source:** Storage/UI Audit

If the database contains a corrupted or legacy enum value, the entire user deserialization fails. Note: `readInterestSet()` in the same file correctly uses try-catch.

---

### M-10: `JdbiUserStorage.Mapper.readPhotoUrls()` Returns Fixed-Size List

`Arrays.asList(...)` creates a fixed-size list. Code calling `.add()` on it gets `UnsupportedOperationException`.

**Fix:** Wrap in `new ArrayList<>(...)`.

---

### M-11: `MapperHelper.readEnum()` Crashes on Invalid Enum Values

Uses `Enum.valueOf()` without try-catch. Will throw for unknown/legacy values.

---

### M-20: `DatabaseManager` — Static Mutable State Without Synchronization

`jdbcUrl` is a static mutable `String` set without synchronization. Race condition if set during connection.

---

### M-24: CSV Parsing Doesn't Trim Entries

`MapperHelper.readCsvAsList()`: `"A, B, C".split(",")` produces `["A", " B", " C"]` with leading spaces.

**Fix:** Use `split("\\s*,\\s*")` or trim elements.

---

### M-28: Foreign Key Enforcement Unreliable

Schema and code use mismatched column names; error handling is too broad, risking silent data integrity loss.

---

### M-29: ResultSet Resource Leaks

Some storage classes do not close ResultSets properly, risking connection exhaustion.

---

## 4. Low-Severity Issues

### L-05: `JdbiMatchStorage.getActiveMatchesFor()` — OR-Based Query

`WHERE (user_a = :userId OR user_b = :userId)` may prevent index usage. A UNION would be faster at scale.

---

### ST-01: N+1 Query in `MessagingService.getConversations()`

For 50 conversations → 150-200 SQL queries (user lookup + last message + unread count per conversation).

**Fix:** Batch lookups with IN clauses → 3-4 queries total.

---

### ST-02: `DailyService.dailyPickViews` In-Memory — Lost on Restart

Users may see the same daily pick as "new" repeatedly after restart.

---

### ST-03: No Connection Pool Sizing in `DatabaseManager`

Uses `DriverManager.getConnection()` per request — no HikariCP. Acceptable for H2 embedded, problematic for remote DB.

---

### ST-04: `MapperHelper.readEnumSet()` Missing `Enum.valueOf()` Error Handling

Invalid enum values crash the entire query. Should catch and skip invalid values.

---

### ST-05: No Database Migration Tooling

Schema changes are manual Java code. No versioned migrations, no rollback capability.

**Fix:** Adopt Flyway or Liquibase.

---

## 8. Strengths & Positive Observations

### Architecture Excellence
- **Clean 3-Layer Design:** Proper separation between core business logic, storage, and UI
- **MVVM Pattern:** Well-implemented JavaFX architecture with proper viewmodel separation
- **Dependency Injection:** Clean service wiring through `ServiceRegistry`

### Code Quality
- **Excellent Test Coverage:** 84% core coverage with 736 passing tests
- **Type Safety:** Extensive use of enums, records, and compile-time validation
- **Error Handling:** Result pattern prevents exceptions from propagating to UI
- **Modern Java:** Proper use of Java 25 features and patterns

### Development Practices
- **Comprehensive Documentation:** Detailed AGENTS.md and CLAUDE.md guides
- **Code Formatting:** Automated formatting with Palantir Java Format
- **Quality Gates:** Checkstyle, PMD, and Spotless integration

### Storage Layer
- **SQL Injection Protection:** All JDBI storage uses `@Bind` / `@BindBean` parameterized queries; zero string concatenation in SQL
- **Bounded Navigation History:** Stack bounded at `MAX_HISTORY_SIZE = 20`
- **Deterministic IDs:** Match/Conversation IDs use canonical ordering consistently

### UI Layer
- **DashboardViewModel / ChatViewModel** correctly use `Thread.ofVirtual()` + `Platform.runLater()` for background DB work
- **ErrorHandler pattern** — Clean ViewModel→Controller error propagation via functional interface
- **BaseController pattern** — Good lifecycle management (when used)

---

## 9. Recommended Fix Priority

### Immediate (This Sprint)

| #  | Finding                                              | Effort |
|----|------------------------------------------------------|--------|
| 1  | C-01: SessionService lock clearing race condition    | 1 hr   |
| 2  | C-02: AppSession deadlock risk                       | 15 min |
| 3  | C-04: MessagingService canMessage inconsistency      | 5 min  |
| 4  | C-06: Reorder cleanup/save in ProfileController      | 2 min  |
| 5  | C-07: Fix handleStartChat missing context            | 5 min  |
| 6  | C-08: Stop all INDEFINITE animations in cleanup      | 30 min |
| 7  | H-01: DailyService memory leak (cleanup never fires) | 10 min |
| 8  | H-07: Daily pick reason dilution                     | 5 min  |
| 9  | H-08: SwipeSession velocity calculation              | 5 min  |
| 10 | H-17: Main.java try-catch around handlers            | 15 min |

### Short-Term (Next Sprint)

| #  | Finding                                             | Effort |
|----|-----------------------------------------------------|--------|
| 11 | C-03/C-05: MatchingService swipe/match atomicity    | 2 hr   |
| 12 | H-03: GeoUtils location checks in 3 services        | 30 min |
| 13 | H-04: Daily pick stability                          | 1 hr   |
| 14 | H-05: Achievement count — use all-time, not active  | 30 min |
| 15 | H-09: SecureRandom for verification codes           | 10 min |
| 16 | H-11: N+1 query fixes (add batch user loading)      | 2 hr   |
| 17 | H-12: Background threads for 3 ViewModels           | 1.5 hr |
| 18 | H-13: Track all listeners via addSubscription       | 30 min |
| 19 | H-14: Fix Achievement visibility, remove reflection | 20 min |
| 20 | H-15: Route all likes through daily limit check     | 15 min |
| 21 | H-16: Enum.valueOf try-catch in readEnumSet         | 10 min |

### Medium-Term (Next Month)

| #  | Finding                                                           | Effort |
|----|-------------------------------------------------------------------|--------|
| 22 | H-06: Consolidate MatchingService constructors                    | 2 hr   |
| 23 | M-01: Validate config weight sum                                  | 30 min |
| 24 | M-05: Make AppConfig injectable in Dealbreakers/ValidationService | 1 hr   |
| 25 | M-07: Cache seeker data in CandidateFinder                        | 30 min |
| 26 | UI-01: ImageCache LRU eviction                                    | 1 hr   |
| 27 | UI-04: ViewModelFactory reset on logout                           | 45 min |
| 28 | API-01-11: API security hardening (auth, rate limiting, etc.)     | 1 week |
| 29 | ST-05: Database migration tooling (Flyway)                        | 1 day  |
| 30 | M-24: CSV parsing trim fix                                        | 10 min |

### Performance Targets

| Metric               | Current     | Target                   |
|----------------------|-------------|--------------------------|
| Candidate Loading    | Unbounded   | <100ms for 50 candidates |
| Message Send         | Synchronous | <50ms, async processing  |
| Daily Pick           | O(n) memory | O(1) with caching        |
| Stats Calculation    | Full recalc | Incremental, <10ms       |
| Database Connections | Per-request | Pooled, max 50           |
| Cache Hit Rate       | 0%          | >80% for hot data        |

---

---

*Consolidated from 7 independent audit reports — February 6, 2026*
*Auditors: Kimmy 2.5, Grok, Opus 4.6, GPT-5 Mini, Raptor Mini, GPT-4.1*

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-02-06 17:29:51|agent:codex|audit-group-storage|Regroup audit by problem type (storage/database)|c:/Users/tom7s/Desktopp/Claude_Folder_2/Date_Program/Audit and Suggestions/AUDIT_PROBLEMS_STORAGE.md
---AGENT-LOG-END---
