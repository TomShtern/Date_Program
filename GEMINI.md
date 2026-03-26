> 🚀 **VERIFIED & UPDATED: 2026-03-25**
> This document is the definitive, line-by-line source of truth for the project's logic, architecture, and constraints. **Do not deviate from these patterns.**

# GEMINI.md (App Architecture & AI Agent Cookbook)

## 0. Verified Environment Snapshot

- **OS:** Windows 11
- **Shell:** PowerShell 7.x
- **IDE:** VS Code Insiders (Java by Red Hat extension)
- **Java:** 25 (preview enabled)
- **JavaFX:** 25.0.2
- **Build:** Maven

## 1. Verified Codebase Snapshot (from source)

- **Total Java files:** **296**
- `tokei` (Java only):
  - **Total lines:** ~80,000+
  - **Code lines:** ~60,000+

*This is a massive repository.* If a markdown doc and code diverge, always trust current code in `src/main/java`, `src/test/java`, and build config in `pom.xml`. To search this codebase efficiently, **prioritize using `sg` (ast-grep) and `rg`** rather than attempting to read entire files.

## 2. Required Build / Test Commands

```bash
# Build and run CLI
mvn compile && mvn exec:exec

# Build and run JavaFX UI
mvn javafx:run

# Tests
mvn test
mvn -Ptest-output-verbose test
mvn -Ptest-output-verbose -Dtest="MatchingUseCasesTest#processSwipe_mutuallyLiked_createsMatch" test

# Quality gate (MUST run before concluding feature work)
mvn spotless:apply verify
```

## 3. Architecture Overview (Code-Verified)

```text
datingapp/
  app/
    api/RestApiServer.java
    bootstrap/ApplicationStartup.java
    cli/         # Console navigation and input handling
    event/       # InProcessAppEventBus and AppEvent sealed records
    usecase/     # Business logic orchestrators. Returns UseCaseResult<T>
  core/
    AppClock, AppConfig, AppSession, ServiceRegistry
    model/       # Pure Domain POJOs (User, Match, ProfileNote)
    connection/  # Connection models & services
    matching/    # Recommendations, Scoring, Standouts, Constraints
    profile/     # Preferences, ValidationService
  storage/
    jdbi/        # H2 / JDBC implementations. MERGE INTO algorithms.
    schema/      # Migration runner
  ui/
    async/       # ViewModelAsyncScope, TaskPolicy, UiThreadDispatcher
    screen/      # JavaFX Controllers
    viewmodel/   # UI Logic and Virtual Thread dispatching
```

## 4. Entry Points and Wiring

*   **Shared Bootstrap:**
    ```java
    ServiceRegistry services = ApplicationStartup.initialize();
    AppSession session = AppSession.getInstance();
    ```
*   **CLI (`Main.java`):** Bootstraps `ServiceRegistry`, constructs handlers (e.g., `ProfileHandler`, `MatchingHandler`), and kicks off the `MainMenuRegistry`.
*   **JavaFX (`DatingApp.java`):** Bootstraps `ViewModelFactory` injected with `ServiceRegistry`, and initializes `NavigationService.getInstance().initialize(primaryStage)`.

## 5. Core Rules (Do / Don’t)

### DO
- **Domain Purity:** Keep `core/` completely free from `javafx.*`, `java.sql.*`, `io.javalin.*`, or Jackson UI imports.
- **Time Management:** Use `AppClock.now()` for testing determinism.
- **Unique Edges:** Use deterministic pair IDs via `Match.generateId(UUID a, UUID b)` so order does not matter.
- **Envelopes:** Return properly typed `UseCaseResult<T>` containing data or `UseCaseError` from `app/usecase/`.
- **Async UI:** In ViewModels, use the shared async abstraction: `launch(TaskPolicy.LATEST_WINS, () -> { ... })` inherited from `ViewModelAsyncScope`. Virtual threads handle the blocking.
- **Enum Mutability:** Use `EnumSetUtil.safeCopy(...)` to return defensive copies from entity getters.

### DON'T
- **Don’t** use `Instant.now()`, `LocalDate.now()`, or `new Date()`.
- **Don’t** publish `AppEvent` messages *inside* `JDBI.inTransaction(...)` closures (to avoid phantom events during rollbacks).
- **Don’t** use JavaFX's native `javafx.concurrent.Task` or raw `Thread.start()`.
- **Don’t** forget to invoke `.touch()` inside mutating setters on `User` and `Match` models to update timestamps.

## 6. AppEvent Ledger (Pub/Sub Truth)

All telemetry and side-effects must plug into `InProcessAppEventBus` listening for these exact `sealed` records from `datingapp.app.event.AppEvent`:

*   **`SwipeRecorded`**: `(UUID swiperId, UUID targetId, String direction, boolean resultedInMatch, Instant occurredAt)`
*   **`MatchCreated`**: `(String matchId, UUID userA, UUID userB, Instant occurredAt)`
*   **`ProfileSaved`**: `(UUID userId, boolean activated, Instant occurredAt)`
*   **`ProfileCompleted`**: `(UUID userId, Instant occurredAt)`
*   **`ProfileNoteSaved`**: `(UUID authorId, UUID subjectId, int contentLength, Instant occurredAt)`
*   **`ProfileNoteDeleted`**: `(UUID authorId, UUID subjectId, Instant occurredAt)`
*   **`ConversationArchived`**: `(String conversationId, UUID archivedByUserId, Instant occurredAt)`
*   **`LocationUpdated`**: `(UUID userId, double latitude, double longitude, Instant occurredAt)`
*   **`DailyLimitReset`**: `(UUID userId, Instant occurredAt)`
*   **`MatchExpired`**: `(String matchId, UUID userA, UUID userB, Instant occurredAt)`
*   **`AccountDeleted`**: `(UUID userId, DeletionReason reason, Instant occurredAt)`
*   **`FriendRequestAccepted`**: `(UUID requestId, UUID fromUserId, UUID toUserId, String matchId, Instant occurredAt)`
*   **`RelationshipTransitioned`**: `(String matchId, UUID initiatorId, UUID targetId, String fromState, String toState, Instant occurredAt)`
*   **`MessageSent`**: `(UUID senderId, UUID recipientId, UUID messageId, Instant occurredAt)`
*   **`UserBlocked`**: `(UUID blockerId, UUID blockedUserId, Instant occurredAt)`
*   **`UserReported`**: `(UUID reporterId, UUID reportedUserId, String reason, boolean blockedUser, Instant occurredAt)`

## 7. Domain State Macros (The Enums)

Agents must utilize these exact enums when mutating core model states. **Do not hallucinate string literals.**

### `datingapp.core.model.User`
*   **`Gender`**: `MALE`, `FEMALE`, `OTHER`
*   **`UserState`**: `INCOMPLETE`, `ACTIVE`, `PAUSED`, `BANNED`.
*   **`VerificationMethod`**: `EMAIL`, `PHONE`

### `datingapp.core.model.Match`
*   **`MatchState`**: `ACTIVE` (Standard match), `FRIENDS` (Platonic transition), `UNMATCHED` (Terminal), `GRACEFUL_EXIT` (Terminal), `BLOCKED` (Terminal).
*   **`MatchArchiveReason`**: `FRIEND_ZONE`, `GRACEFUL_EXIT`, `UNMATCH`, `BLOCK`.

### `datingapp.core.matching.ModerationAuditEvent`
*   **`Action`**: `REPORT`, `BLOCK`, `UNBLOCK`, `AUTO_BAN`.
*   **`Outcome`**: `SUCCESS`, `FAILURE`.

## 8. AI Agent Cookbook: Code Synthesis Patterns

When generating new features, **strictly copy these boilerplate patterns**. Do not invent new dispatch engines, use case wrappers, or transaction builders.

### Pattern 1: Writing a UseCase (The Only Way to Expose Logic)

```java
public UseCaseResult<MyDto> doAction(MyCommand command) {
    try {
        // 1. Validation (Defensive)
        if (!validationService.isValid(command.input())) {
            return UseCaseResult.failure(UseCaseError.validation("Invalid input"));
        }

        // 2. Application Logic + Storage
        MyEntity entity = storage.executeWrite(command.input());

        // 3. Side-Effects (AFTER storage succeeds)
        eventBus.publish(new AppEvent.MyActionOccurred(entity.id(), AppClock.now()));

        // 4. Return Data Payload
        return UseCaseResult.success(new MyDto(entity));
    } catch (IllegalArgumentException e) {
        return UseCaseResult.failure(UseCaseError.validation(e.getMessage()));
    } catch (Exception e) {
        logger.error("Failed action", e);
        return UseCaseResult.failure(UseCaseError.internal("Unexpected error"));
    }
}
```

### Pattern 2: Dispatching from JavaFX UI (Virtual Threading)

```java
public class MyViewModel extends ViewModelAsyncScope {
    
    public void onSaveClicked(String input) {
        // 'LATEST_WINS' aborts previous save clicks if spammed.
        // 'STANDARD' blocks new clicks until done.
        launch(TaskPolicy.LATEST_WINS, () -> {
            var result = myUseCase.doAction(new MyCommand(input));
            
            // UI Thread continuation is automatic simply by returning or throwing
            if (result.success()) {
                viewState.update(result.data());
            } else {
                errorSink.notifyError(result.error().message());
            }
        });
    }
}
```

### Pattern 3: JDBI Transaction Boundaries

```java
import datingapp.storage.jdbi.JdbiConnectionStorage;

public MyEntity executeWrite(String input) {
    return jdbi.inTransaction(handle -> {
        // 1. Write Parent
        long id = handle.createUpdate("INSERT INTO parent (val) VALUES (:v)")
            .bind("v", input)
            .executeAndReturnGeneratedKeys()
            .mapTo(Long.class).one();

        // 2. Write Child (Atomically safe inside `inTransaction`)
        handle.createUpdate("INSERT INTO child (pid) VALUES (:pid)")
            .bind("pid", id)
            .execute();
            
        return fetchById(handle, id);
    });
}
```

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries.
1|2026-01-30 18:50:00|agent:antigravity|docs|Update GEMINI.md to reflect current tech stack (JDBI, Jackson, JavaFX 25) and architecture|GEMINI.md
2|2026-03-25 15:58:00|agent:antigravity|docs-source-truth-sync|MASSIVE EXHAUSTIVE EXPANSION: Created the ultimate definitive source of truth file mapped via an AST extraction script|GEMINI.md
3|2026-03-25 16:15:00|agent:antigravity|docs-ai-optimization|TRANSFORMATION: Recognized 1000-line AST dump as bloat for AI agents. Replaced it with structural copy-paste Cookbooks, Event Ledgers, and Domain Constants. Reduced parsing context load and supercharged synthesis speed.|GEMINI.md
---AGENT-LOG-END---
