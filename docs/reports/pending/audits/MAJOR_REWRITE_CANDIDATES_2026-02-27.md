# Major Rewrite Candidates — Retrospective Analysis

Date: 2026-02-27
Scope: Full source code analysis (50+ files, ~22,000 LOC main source)
Purpose: Identify components where a clean rewrite would yield massive gains in clarity, LOC reduction, and maintainability.

## How This Document Is Organized

Each candidate describes:
- **What it is now** — the current state and why it's problematic
- **What it should be** — the clean rewrite target
- **Concrete LOC impact** — estimated current → rewritten size
- **Why this was only visible in retrospect** — why the original decision was reasonable

**New findings** (from source code analysis) are the main body.
**Overlapping findings** (code-level detail for decisions already identified in `RETROSPECTIVE_ARCHITECTURE_DECISIONS_2026-02-27.md`) are in the appendix at the end.

## Implementation Status Snapshot (as of 2026-03-01)

Legend: ✅ Implemented | ⚠️ Partial | ❌ Not implemented

| Finding                                     | Status            | Current evidence summary                                                       |
|---------------------------------------------|-------------------|--------------------------------------------------------------------------------|
| Candidate 1 — ProfileService split          | ⚠️ Partial         | Category scoring refactor exists; achievement switch-based logic still present |
| Candidate 2 — MatchPreferences rewrite      | ✅ Implemented     | Data-driven `Dealbreakers.Evaluator` using `LifestyleDimension` is in place    |
| Candidate 3 — User desync + builder rewrite | ⚠️ Partial         | `synchronized` removed; `StorageBuilder` boilerplate still exists              |
| Candidate 4 — Controller dialog extraction  | ❌ Not implemented | Inline dialog construction remains in controllers                              |
| Candidate 5 — AppConfig.Builder refactor    | ❌ Not implemented | Flat monolithic builder remains                                                |
| Appendix A — Use-case layer                 | ✅ Implemented     | `app/usecase/*` layer exists and is wired via `ServiceRegistry`                |
| Appendix B — Shared async runner            | ⚠️ Partial         | `ui/async` abstraction exists; migration is not uniformly complete             |
| Appendix C — MatchQualityService split      | ❌ Not implemented | Presentation semantics remain in `MatchQualityService`                         |
| Appendix D — JdbiUserStorage cleanup        | ❌ Not implemented | Dual-format parsing and large `UserSqlBindings` still present                  |
| Appendix E — NavigationService split        | ❌ Not implemented | `NavigationService` remains monolithic                                         |

---

## Candidate 1: ProfileService — 5 Services Pretending To Be One

**Status (2026-03-01): ⚠️ Partial**

**Current state: 821 LOC, 5+ distinct responsibilities, 33 inlined constants**

ProfileService currently handles:
1. **Profile completeness scoring** — 4 nearly-identical category scoring methods (~200 LOC)
2. **Achievement tracking** — 2 switch statements with 11 cases each (~150 LOC)
3. **Presentation/tips generation** — emoji strings, improvement suggestions (~60 LOC)
4. **User listing/retrieval** — trivial delegation to storage (~30 LOC)
5. **Compatibility tier labeling** — tier thresholds and display names (~40 LOC)

The 4 scoring methods (`scoreBasicInfo()`, `scoreInterests()`, `scoreLifestyle()`, `scorePreferences()`) are structurally identical:

```java
// This exact pattern repeats 4 times with different field names:
private CategoryBreakdown scoreXxx(User user) {
    List<String> completed = new ArrayList<>();
    List<String> missing = new ArrayList<>();
    int score = 0;
    if (user.getFieldA() != null) { score += POINTS_A; completed.add("Field A"); }
    else { missing.add("Add Field A"); }
    if (user.getFieldB() != null) { score += POINTS_B; completed.add("Field B"); }
    else { missing.add("Add Field B"); }
    // ... 5-10 more fields per category
    return new CategoryBreakdown(categoryName, score, maxScore, completed, missing);
}
```

The achievement checking has 2 parallel switch statements that enumerate all 11 achievements:

```java
// Switch 1: isEarned(achievement, user, stats)
case FIRST_MATCH -> stats.totalMatches() >= 1;
case SOCIAL_BUTTERFLY -> stats.totalMatches() >= 10;
// ... 9 more cases

// Switch 2: getProgressValues(achievement, user, stats)
case FIRST_MATCH -> new int[]{stats.totalMatches(), 1};
case SOCIAL_BUTTERFLY -> new int[]{stats.totalMatches(), 10};
// ... 9 more identical patterns
```

**What it should be:**

Split into 3 focused services:

1. **ProfileCompletenessService** (~120 LOC) — single parameterized scoring method using a field-definition list
2. **AchievementService** (~100 LOC) — achievement definitions as data (enum with condition + progress extractors), not switch statements
3. **ProfileService** (~80 LOC) — thin orchestration facade

```java
// Instead of 4 identical scoring methods:
record FieldCheck(String name, int points, Predicate<User> isComplete) {}
private static final List<FieldCheck> BASIC_FIELDS = List.of(
    new FieldCheck("Name", 5, u -> u.getName() != null),
    new FieldCheck("Bio", 10, u -> u.getBio() != null && !u.getBio().isBlank()),
    // ...
);

private CategoryBreakdown scoreCategory(String name, List<FieldCheck> fields, User user) {
    // ONE method handles all 4 categories
}

// Instead of 2 switch statements with 11 cases each:
enum Achievement {
    FIRST_MATCH(1, s -> s.totalMatches(), "Get your first match!"),
    SOCIAL_BUTTERFLY(10, s -> s.totalMatches(), "Match with 10 people"),
    // ...
    final int threshold;
    final Function<UserStats, Integer> progressExtractor;
    boolean isEarned(UserStats s) { return progressExtractor.apply(s) >= threshold; }
}
```

**Impact:**

| Component                      | Current LOC | After Rewrite | Reduction      |
|--------------------------------|-------------|---------------|----------------|
| ProfileService.java            | 821         | ~300          | -521 (63%)     |
| New ProfileCompletenessService | 0           | ~120          | +120           |
| New AchievementService         | 0           | ~100          | +100           |
| 33 constants → AppConfig       | 33 inline   | 0 inline      | cleaner        |
| **Net**                        | **821**     | **~520**      | **-301 (37%)** |

The 63% reduction in the main file comes from: eliminating 4 identical scoring methods (→ 1 parameterized method), eliminating 2 parallel switch statements (→ enum with data), removing presentation logic (→ adapter layer).

**Why this was only visible in retrospect:** ProfileService started as "the thing that knows about profiles." Each new feature (achievements, tips, scoring tiers) was a natural addition — until 5 unrelated responsibilities ended up in one class. The category scoring started with 1 method; when the second was added, it was "just a copy." By the fourth copy, the pattern was entrenched.

---

## Candidate 2: MatchPreferences.java — 840 LOC, 75% Boilerplate

**Status (2026-03-01): ✅ Implemented**

**Current state: 840 LOC, dominated by repetitive enum patterns and paired evaluation methods**

Three specific bloat sources:

### 2a: 10 identical enum definitions (~150 LOC)

Every lifestyle enum follows the exact same pattern:
```java
public enum Smoking {
    NEVER("Never"), SOMETIMES("Sometimes"), REGULARLY("Regularly");
    private final String displayName;
    Smoking(String d) { this.displayName = d; }
    public String getDisplayName() { return displayName; }
}
// This exact pattern x 10 enums = 150 LOC
```

### 2b: Dealbreakers.Evaluator — 6 pairs of identical methods (~120 LOC)

```java
// These 6 method pairs are structurally identical:
private boolean passesSmoking(User c) {
    return !dealbreakers.hasSmokingDealbreaker()
        || dealbreakers.acceptableSmoking().contains(c.getSmoking());
}
private void addSmokingFailure(User c, List<String> failures) {
    if (dealbreakers.hasSmokingDealbreaker()
        && c.getSmoking() != null
        && !dealbreakers.acceptableSmoking().contains(c.getSmoking()))
        failures.add("Smoking: " + c.getSmoking().getDisplayName());
}

private boolean passesDrinking(User c) { /* identical structure */ }
private void addDrinkingFailure(User c, List<String> failures) { /* identical structure */ }
// ... x 6 lifestyle dimensions
```

### 2c: Dealbreakers record builder + 7 convenience methods (~70 LOC)

```java
public boolean hasSmokingDealbreaker() { return !acceptableSmoking.isEmpty(); }
public boolean hasDrinkingDealbreaker() { return !acceptableDrinking.isEmpty(); }
// ... x 7 (one per dimension)
```

**What it should be:**

```java
// Instead of 6 paired pass/fail methods, one data-driven evaluator:
record DealbreakDimension<E extends Enum<E>>(
    String label,
    Set<E> acceptable,
    Function<User, E> candidateGetter
) {
    boolean passes(User candidate) {
        return acceptable.isEmpty() || acceptable.contains(candidateGetter.apply(candidate));
    }
    Optional<String> failureMessage(User candidate) {
        E value = candidateGetter.apply(candidate);
        if (acceptable.isEmpty() || value == null || acceptable.contains(value))
            return Optional.empty();
        return Optional.of(label + ": " + value);
    }
}

// Evaluator becomes data-driven:
private static final List<DealbreakDimension<?>> DIMENSIONS = List.of(
    new DealbreakDimension<>("Smoking", dealbreakers.acceptableSmoking(), User::getSmoking),
    new DealbreakDimension<>("Drinking", dealbreakers.acceptableDrinking(), User::getDrinking),
    // ... 4 more
);

public List<String> getFailedDealbreakers(User candidate) {
    return DIMENSIONS.stream()
        .map(d -> d.failureMessage(candidate))
        .flatMap(Optional::stream)
        .toList();
}
```

**Impact:**

| Component                   | Current LOC | After Rewrite | Reduction      |
|-----------------------------|-------------|---------------|----------------|
| Enum declarations           | 150         | ~80           | -70            |
| Evaluator paired methods    | 120         | ~30           | -90            |
| Builder/convenience methods | 70          | ~30           | -40            |
| **Net**                     | **840**     | **~540**      | **-300 (36%)** |

**Why this was only visible in retrospect:** Each lifestyle dimension was added one at a time. The first enum was 15 lines — fine. The first evaluator method pair was 20 lines — fine. By the 6th copy-paste of each, the file had grown to 840 LOC with 75% boilerplate, but each individual addition seemed small.

---

## Candidate 3: User.java — Over-Synchronized Mutable Entity + StorageBuilder Bloat

**Status (2026-03-01): ⚠️ Partial**

**Current state: 807 LOC, 62% boilerplate**

Three specific issues:

### 3a: synchronized on ALL 47 methods (~47 LOC of keywords, hidden perf cost)

Every method — including immutable getters like `getId()` and `getCreatedAt()` — is synchronized:

```java
public synchronized UUID getId() { return id; }           // immutable field!
public synchronized Instant getCreatedAt() { return createdAt; } // immutable field!
public synchronized String getName() { return name; }      // simple getter
// ... 44 more synchronized methods
```

This doesn't actually provide thread safety for multi-field mutations (reading `getName()` and `getAge()` in sequence isn't atomic even with per-method sync). It's security theater with a performance cost.

### 3b: StorageBuilder with 29 single-line delegation methods (155 LOC)

```java
public static class StorageBuilder {
    public StorageBuilder bio(String bio) { user.bio = bio; return this; }
    public StorageBuilder birthDate(LocalDate bd) { user.birthDate = bd; return this; }
    public StorageBuilder gender(Gender g) { user.gender = g; return this; }
    // ... 26 more identical one-liner methods
}
```

Each method just sets one field. The builder exists to bypass validation when loading from DB — but there are simpler ways to achieve this.

### 3c: Duplicate validation in setters + constructor

Height validation, age range validation, and null-safe list copying appear twice — once in the public setter and once in the StorageBuilder path.

**What it should be:**

1. Remove all `synchronized` keywords — the entity is not designed for concurrent access, and per-method sync doesn't help anyway. If thread safety is needed, use external locking.

2. Replace 29-method StorageBuilder with a compact record + factory:
```java
// StorageBuilder replacement: record with all DB columns
public record UserSnapshot(UUID id, String name, LocalDate birthDate, ...) {}

// Factory method:
public static User fromSnapshot(UserSnapshot s) {
    User u = new User(s.id(), s.name());
    u.bio = s.bio();
    u.birthDate = s.birthDate();
    // ... all fields, no validation bypass needed because data came from DB
    return u;
}
```

3. Consolidate duplicate validation into private helpers.

**Impact:**

| Component                  | Current LOC       | After Rewrite | Reduction      |
|----------------------------|-------------------|---------------|----------------|
| synchronized keywords      | 47 lines affected | 0             | -47 (clarity)  |
| StorageBuilder             | 155               | ~30           | -125           |
| Duplicate validation       | 40                | 0             | -40            |
| Deprecated method variants | 20                | 0             | -20            |
| **Net**                    | **807**           | **~575**      | **-232 (29%)** |

**Why this was only visible in retrospect:** Synchronization was added defensively "just in case." The StorageBuilder was added to bypass validation, but each new field added one more delegation method. Both decisions were fine for 10 fields — at 50+ fields, the boilerplate dominates.

---

## Candidate 4: Controller Inline Dialog Construction → Dialog Builders

**Status (2026-03-01): ❌ Not implemented**

**Current state: ~380 LOC of inline dialog construction across 5 controllers, plus ~900 LOC of cross-controller duplication**

Every controller that shows a dialog constructs it inline:

```java
// LoginController: 136 LOC for account creation dialog
// ProfileController: 125 LOC for dealbreaker editor dialog
// ProfileController: 50 LOC for interest selection dialog
// MatchingController: 60 LOC for match popup dialog
// MatchingController + ChatController: 58 LOC for report dialog (DUPLICATED)
```

Additionally, these patterns are copy-pasted across controllers:
- Report dialog: 3 controllers, ~90 LOC duplicated
- List cell rendering: LoginController (194 LOC) + ChatController (160 LOC) + MatchesController (card builds)
- Photo navigation controls: ProfileController (40 LOC) + MatchingController (22 LOC)
- Logging guards: 5 controllers x ~10 LOC = 50 LOC
- Animation/overlay patterns: MatchesController (26 LOC) + MatchingController (26 LOC)

**What it should be:**

Extract dialog builders and cell factories as reusable components:

```java
// Instead of 136 LOC inline:
@FXML private void handleCreateAccount() {
    new CreateAccountDialog(viewModel, this::onUserCreated).show();
}

// Instead of duplicated report dialog:
@FXML private void handleReport() {
    ReportDialog.show(reasons, selectedReason -> viewModel.report(selectedReason));
}

// Instead of copy-pasted cell rendering:
conversationList.setCellFactory(ConversationCellFactory.create(currentUserId));
```

**Impact:**

| Component                          | Current LOC | After Rewrite            | Reduction      |
|------------------------------------|-------------|--------------------------|----------------|
| Inline dialogs (5 controllers)     | 380         | ~50 (calls to builders)  | -330           |
| New dialog builder classes         | 0           | ~200                     | +200           |
| Duplicated cell rendering          | 354         | ~50 (calls to factories) | -304           |
| New cell factory classes           | 0           | ~180                     | +180           |
| Other cross-controller duplication | 163         | ~30                      | -133           |
| New shared utilities               | 0           | ~60                      | +60            |
| **Net across controllers**         | **3,367**   | **~2,440**               | **-927 (28%)** |

**Why this was only visible in retrospect:** Each dialog started as "just 30 lines" of inline JavaFX construction. When the second controller needed a similar dialog, copying was faster than extracting. By the fifth controller, there were 380 LOC of inline dialogs and 900 LOC of cross-controller duplication.

---

## Candidate 5: AppConfig.Builder — 57-Field Monolith

**Status (2026-03-01): ❌ Not implemented**

**Current state: 671 LOC total, with Builder spanning ~350 LOC (57 fields, 100+ setter methods)**

The Builder manually assembles 4 sub-records:

```java
public static class Builder {
    // 57 fields with defaults
    private int dailyLikeLimit = 100;
    private int maxSwipesPerSession = 500;
    // ... 55 more fields

    // 57 setter methods
    public Builder dailyLikeLimit(int v) { this.dailyLikeLimit = v; return this; }
    public Builder maxSwipesPerSession(int v) { this.maxSwipesPerSession = v; return this; }
    // ... 55 more setters

    // build() manually constructs 4 sub-records from flat fields
    public AppConfig build() {
        return new AppConfig(
            new MatchingConfig(dailyLikeLimit, maxSwipesPerSession, ...),
            new ValidationConfig(minAge, maxAge, ...),
            new AlgorithmConfig(nearbyDistanceKm, ...),
            new SafetyConfig(autoBanThreshold, ...)
        );
    }
}
```

**What it should be:**

Each sub-record owns its own builder and defaults:

```java
public record AppConfig(MatchingConfig matching, ValidationConfig validation,
                        AlgorithmConfig algorithm, SafetyConfig safety) {

    public static AppConfig defaults() {
        return new AppConfig(
            MatchingConfig.defaults(),
            ValidationConfig.defaults(),
            AlgorithmConfig.defaults(),
            SafetyConfig.defaults()
        );
    }

    public record MatchingConfig(int dailyLikeLimit, int maxSwipesPerSession, ...) {
        public static MatchingConfig defaults() {
            return new MatchingConfig(100, 500, ...);
        }
    }
}
```

Jackson databinding (`readerForUpdating`) would work the same way or better with nested record builders.

**Impact:**

| Component      | Current LOC | After Rewrite | Reduction  |
|----------------|-------------|---------------|------------|
| AppConfig.java | 671         | ~350          | -321 (48%) |

**Why this was only visible in retrospect:** The config started with 10 fields. Each new feature added 2-3 more. At 57 fields, the flat builder is unwieldy, but no single addition was "the one that broke it."

---

## Summary: New Findings

| # | Candidate                    | Current LOC | After      | Net Change       |
|---|------------------------------|-------------|------------|------------------|
| 1 | ProfileService split         | 821         | 520        | **-301 (37%)**   |
| 2 | MatchPreferences rewrite     | 840         | 540        | **-300 (36%)**   |
| 3 | User.java desync + builder   | 807         | 575        | **-232 (29%)**   |
| 4 | Controller dialog extraction | 3,367       | 2,440      | **-927 (28%)**   |
| 5 | AppConfig.Builder refactor   | 671         | 350        | **-321 (48%)**   |
|   | **Subtotal (new findings)**  | **~6,506**  | **~4,425** | **-2,081 (32%)** |

---

## What This Document Is NOT

- This is NOT a list of style fixes, lint violations, or minor optimizations
- This is NOT "rewrite for the sake of rewrite"
- Every candidate targets **massive structural simplification** (28-63% LOC reduction per component)
- Every candidate addresses a **retrospective architecture decision** — something that was reasonable when made but now drives systemic complexity
- The project has NO real users and NO live data constraints — schema changes and breaking changes are acceptable

---
---

## Appendix: Code-Level Detail for Previously Identified Decisions

These candidates overlap with decisions already documented in `RETROSPECTIVE_ARCHITECTURE_DECISIONS_2026-02-27.md`. The content below adds code-verified LOC numbers, specific code examples, and concrete impact tables to those existing abstract findings.

---

### Appendix A: The Missing Application/Use-Case Layer (Retro Decision #3 — APPROVED)

**Status (2026-03-01): ✅ Implemented**

**Current state: ~2,070 LOC of duplicated orchestration across CLI + UI**

The CLI handlers (ProfileHandler 997, MatchingHandler 982, MessagingHandler 431) and their UI counterparts (ProfileViewModel 862, MatchingViewModel 505, ChatViewModel 486) both independently orchestrate the same business flows. Each channel reimplements:

- Profile completion + activation logic (~350 LOC overlap between ProfileHandler and ProfileViewModel)
- Candidate browsing + swipe processing (~175 LOC overlap between MatchingHandler and MatchingViewModel)
- Conversation loading + message sending (~150 LOC overlap between MessagingHandler and ChatViewModel)
- Achievement unlock checking (identical 15 LOC block copied 3 times across handlers)
- Relationship transitions (friend-zone, unmatch, block — orchestrated separately per channel)

**What it should be:**

Explicit use-case commands that both CLI and UI call:

```
BrowseCandidates      — returns candidates, handles daily pick, tracks session
ProcessSwipe          — records like/pass, detects match, triggers achievements
SendMessage           — validates match state, creates conversation if needed, sends
CompleteProfile       — validates fields, checks activation readiness, activates
ManageDealbreakers    — builds dealbreaker set, validates, saves
TransitionRelationship — friend-zone / graceful-exit / unmatch / block (atomic)
```

CLI handlers become thin input→use-case→output mappers (~200-300 LOC each instead of ~500-1000).
ViewModels become thin async wrappers around use-cases + JavaFX property binding (~300-400 LOC each instead of ~500-860).

**Impact:**

| Layer                  | Current LOC | After Rewrite | Reduction        |
|------------------------|-------------|---------------|------------------|
| CLI handlers (5 files) | 2,937       | ~1,400        | -1,537 (52%)     |
| ViewModels (5 major)   | 2,988       | ~1,800        | -1,188 (40%)     |
| New use-case layer     | 0           | ~600          | +600             |
| **Net**                | **5,925**   | **~3,800**    | **-2,125 (36%)** |

**Why this was only visible in retrospect:** When the CLI was the only channel, putting orchestration in handlers was the simplest path. When JavaFX was added, the fast path was to copy the same flows into ViewModels with async wrappers. The duplication was small at first but grew proportionally with every new feature.

---

### Appendix B: Ad-Hoc Async Model in Every ViewModel → Shared UiTaskRunner (Retro Decision #7 — APPROVED)

**Status (2026-03-01): ⚠️ Partial**

**Current state: ~350 LOC of identical async scaffolding repeated across 6 ViewModels**

Every ViewModel independently implements:

```java
// Pattern 1: Virtual thread + epoch guard (25 LOC × 7+ instances = 175 LOC)
Thread.ofVirtual().name("...").start(() -> {
    try {
        var result = fetch...();
        Platform.runLater(() -> {
            if (refreshEpoch.get() == capturedEpoch) { update...(); }
        });
    } catch (Exception e) {
        Platform.runLater(() -> notifyError(...));
    }
});

// Pattern 2: Disposed guard (15-25 LOC × 5 VMs = 75 LOC)
private final AtomicBoolean disposed = new AtomicBoolean(false);

// Pattern 3: Loading state tracking (10 LOC × 3 VMs = 30 LOC)
private final AtomicInteger activeLoads = new AtomicInteger(0);

// Pattern 4: Error notification (9 LOC × 10 VMs = 90 LOC)
private void notifyError(String msg, Exception e) { ... Platform.runLater ... }

// Pattern 5: Logging guards (20 LOC × 10 VMs = 200 LOC)
private void logInfo(...) { if (logger.isInfoEnabled()) logger.info(...); }
```

**What it should be:**

A single `UiTaskRunner` (or `ViewModelAsyncScope`) that all ViewModels use:

```java
// In ViewModel — express intent, not thread mechanics
taskRunner.run("load-candidates", () -> {
    return candidateFinder.findCandidates(user);
}, candidates -> {
    this.candidates.setAll(candidates);
});

// UiTaskRunner handles: virtual thread creation, disposed checks,
// Platform.runLater dispatch, epoch guards, error routing, loading state
```

**Impact:**

| Component                   | Current LOC | After Rewrite | Reduction      |
|-----------------------------|-------------|---------------|----------------|
| Async boilerplate (6 VMs)   | 350         | 0             | -350           |
| Logging guards (10 VMs)     | 200         | 0             | -200           |
| Error notification (10 VMs) | 90          | 0             | -90            |
| New UiTaskRunner class      | 0           | 80            | +80            |
| **Net**                     | **640**     | **80**        | **-560 (88%)** |

Plus: eliminates the ChatViewModel `CountDownLatch` antipattern (15 LOC to read a single property), and the MatchingViewModel fire-and-forget memory leak.

---

### Appendix C: MatchQualityService — Scoring Engine Mixed With Display Formatting (Retro Decision #9 — APPROVED)

**Status (2026-03-01): ❌ Not implemented**

**Current state: 734 LOC, ~200 LOC is presentation logic**

MatchQualityService currently computes compatibility scores AND generates display strings:

```java
// These are PRESENTATION methods inside a SCORING service:
public String getCompatibilityLabel(double score)     // "Excellent Match!"
public String getStarDisplay(double score)            // "★★★★★"
public String getShortSummary(double score)           // "Great compatibility"
public List<String> generateHighlights(User a, User b) // "Lives 2km away", "Total Pace Sync! ⚡"
```

30+ constants are inlined (scoring thresholds, display thresholds, summary max lengths) — mix of algorithmic parameters and formatting parameters in one blob.

InterestMatcher is a nested static class that's also used by RecommendationService — it doesn't belong here.

**What it should be:**

```
MatchQualityService (~350 LOC)   — pure scoring, returns numeric results
MatchQualityPresenter (~150 LOC) — takes scores, produces display strings
InterestMatcher (standalone)     — shared utility for interest/lifestyle comparison
```

**Impact:**

| Component                   | Current LOC | After Rewrite | Reduction            |
|-----------------------------|-------------|---------------|----------------------|
| MatchQualityService.java    | 734         | ~350          | -384 (52%)           |
| New MatchQualityPresenter   | 0           | ~150          | +150                 |
| InterestMatcher (extracted) | 0           | ~60           | +60 (moved from MQS) |
| **Net**                     | **734**     | **~560**      | **-174 (24%)**       |

---

### Appendix D: JdbiUserStorage — Dual-Format Parsing Legacy + 249 LOC UserSqlBindings (Retro Decision #12)

**Status (2026-03-01): ❌ Not implemented**

**Current state: 724 LOC, with two major complexity sources**

#### Dual CSV/JSON format workarounds (~120 LOC)

The migration from CSV to JSON storage was never completed. Every multi-value field read goes through:

```java
// This try-catch-fallback pattern repeats for EVERY multi-value column:
private List<String> parseMultiValueTokens(String raw) {
    if (raw == null) return List.of();
    try {
        return OBJECT_MAPPER.readValue(raw, new TypeReference<>() {});
    } catch (JsonProcessingException e1) {
        try {
            return Arrays.asList(raw.split(","));
        } catch (Exception e2) {
            return Arrays.asList(raw.split("\\|"));
        }
    }
}
```

This is called for: `photo_urls`, `interests`, `interested_in`, and 5 dealbreaker enum set columns. That's 8 columns x dual-format parsing = 10+ try-catch blocks in the mapper.

#### UserSqlBindings boilerplate (249 LOC)

40 getter methods that manually serialize User fields for JDBI `@BindBean`:

```java
public String getInterestsCsv() { return serializeEnumSet(user.getInterests()); }
public String getDealbreakerSmokingCsv() { return serializeEnumSet(dealbreakers.acceptableSmoking()); }
// ... 37 more getters following the same pattern
```

**What it should be:**

1. **One-time data migration** to standardize all multi-value columns to JSON format
2. **Register JDBI ArgumentFactory** for EnumSet serialization instead of 40 manual getters
3. **Single-format reader** — remove all CSV/pipe fallback try-catch blocks

**Impact:**

| Component                   | Current LOC | After Rewrite | Reduction      |
|-----------------------------|-------------|---------------|----------------|
| Dual-format parsing         | 120         | 0             | -120           |
| UserSqlBindings             | 249         | ~80           | -169           |
| Migration script            | 0           | ~30           | +30            |
| ArgumentFactory             | 0           | ~40           | +40            |
| **Net for JdbiUserStorage** | **724**     | **~455**      | **-269 (37%)** |

---

### Appendix E: NavigationService — God Object Doing 8 Things (Retro Decision #6)

**Status (2026-03-01): ❌ Not implemented**

**Current state: 401 LOC, singleton managing FXML loading + controller lifecycle + scene transitions + animation + history + context passing + error handling + logging**

NavigationService is the most coupled file in the UI layer. It:
1. Loads FXML files and instantiates controllers
2. Manages controller lifecycle (cleanup, disposal)
3. Plays transition animations (fade, slide)
4. Maintains navigation history stack
5. Passes context objects between views (type-unsafe `Object` context)
6. Holds references to primary stage, root layout, root stack
7. Wires ViewModelFactory to controllers
8. Handles navigation errors

**What it should be:**

Split into 3-4 focused components:

```
Navigator (~80 LOC)           — typed route commands, delegates to others
SceneLoader (~60 LOC)         — FXML loading + controller wiring
TransitionEngine (~60 LOC)    — animation choreography
NavigationHistory (~40 LOC)   — stack management + typed context passing
```

**Impact:**

| Component              | Current LOC | After Rewrite | Reduction      |
|------------------------|-------------|---------------|----------------|
| NavigationService.java | 401         | 0 (deleted)   | -401           |
| New Navigator          | 0           | ~80           | +80            |
| New SceneLoader        | 0           | ~60           | +60            |
| New TransitionEngine   | 0           | ~60           | +60            |
| New NavigationHistory  | 0           | ~40           | +40            |
| **Net**                | **401**     | **~240**      | **-161 (40%)** |

---

### Appendix Summary

| App. | Candidate                     | Retro Decision | Current LOC | After      | Net Change       |
|------|-------------------------------|----------------|-------------|------------|------------------|
| A    | Use-case layer (CLI+VM dedup) | #3 APPROVED    | 5,925       | 3,800      | **-2,125 (36%)** |
| B    | Shared UiTaskRunner (async)   | #7 APPROVED    | 640         | 80         | **-560 (88%)**   |
| C    | MatchQualityService split     | #9 APPROVED    | 734         | 560        | **-174 (24%)**   |
| D    | JdbiUserStorage cleanup       | #12            | 724         | 455        | **-269 (37%)**   |
| E    | NavigationService split       | #6             | 401         | 240        | **-161 (40%)**   |
|      | **Subtotal (appendix)**       |                | **~8,424**  | **~5,135** | **-3,289 (39%)** |

---

## Combined Total (All Candidates)

| Section               | Current LOC | After      | Net Change       |
|-----------------------|-------------|------------|------------------|
| New findings (1-5)    | ~6,506      | ~4,425     | **-2,081 (32%)** |
| Appendix (A-E)        | ~8,424      | ~5,135     | **-3,289 (39%)** |
| **Grand total (raw)** | **~14,930** | **~9,560** | **-5,370 (36%)** |

**Note:** Some reductions overlap (e.g., Appendix A reduces CLI and VM LOC that Appendix B also reduces). Realistic non-overlapping estimate: **~4,000-4,500 LOC net reduction** across the codebase, which is roughly **10-12% of total project LOC**.
