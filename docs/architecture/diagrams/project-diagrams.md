# Project Architecture Diagrams

> Auto-generated from source code analysis (2026-03-01).
> All diagrams use [Mermaid](https://mermaid.js.org/) syntax — renderable in GitHub, VS Code, IntelliJ, and most markdown viewers.

---

## Table of Contents

1. [Layered Architecture Overview](#1-layered-architecture-overview)
2. [Package Structure](#2-package-structure)
3. [Bootstrap & Initialization Flow](#3-bootstrap--initialization-flow)
4. [ServiceRegistry Composition](#4-serviceregistry-composition)
5. [Storage Layer](#5-storage-layer)
6. [Domain Services (core/)](#6-domain-services)
7. [Use-Case Layer (app/usecase/)](#7-use-case-layer)
8. [Event System](#8-event-system)
9. [CLI Adapter Layer](#9-cli-adapter-layer)
10. [JavaFX UI Layer (MVVM)](#10-javafx-ui-layer-mvvm)
11. [Async ViewModel Pattern](#11-async-viewmodel-pattern)
12. [Key Data Flows](#12-key-data-flows)

---

## 1. Layered Architecture Overview

The project follows a clean architecture with strict inward-only dependencies.

```mermaid
graph TB
    subgraph Presentation ["Presentation Layer"]
        CLI["CLI Handlers<br/>(app/cli/)"]
        REST["REST API<br/>(app/api/)"]
        GUI["JavaFX GUI<br/>(ui/)"]
    end

    subgraph Application ["Application Layer"]
        UC["Use Cases<br/>(app/usecase/)"]
        EVT["Event Bus<br/>(app/event/)"]
        BOOT["Bootstrap<br/>(app/bootstrap/)"]
    end

    subgraph Domain ["Domain Layer (core/)"]
        SVC["Domain Services<br/>matching/ connection/<br/>profile/ metrics/"]
        MDL["Domain Models<br/>User, Match, ProfileNote"]
        STI["Storage Interfaces<br/>UserStorage, InteractionStorage, etc."]
        WF["Workflow Policies<br/>core/workflow/"]
    end

    subgraph Infrastructure ["Infrastructure Layer"]
        JDBI["JDBI Implementations<br/>(storage/jdbi/)"]
        DB["H2 Database<br/>(DatabaseManager)"]
        SCHEMA["Schema Management<br/>(storage/schema/)"]
    end

    CLI --> UC
    REST --> UC
    GUI --> UC
    UC --> SVC
    UC --> EVT
    SVC --> MDL
    SVC --> STI
    JDBI -.->|implements| STI
    JDBI --> DB
    DB --> SCHEMA
    BOOT --> UC
    BOOT --> JDBI

    style Domain fill:#e8f5e9,stroke:#2e7d32
    style Application fill:#e3f2fd,stroke:#1565c0
    style Presentation fill:#fff3e0,stroke:#e65100
    style Infrastructure fill:#fce4ec,stroke:#c62828
```

**Dependency rule:** Arrows point inward. `core/` never imports from `storage/`, `app/`, or `ui/`.

---

## 2. Package Structure

```mermaid
graph LR
    subgraph datingapp
        Main["Main.java<br/>(CLI entry)"]

        subgraph app ["app/"]
            api["api/<br/>RestApiServer"]
            bootstrap["bootstrap/<br/>ApplicationStartup"]
            cli["cli/<br/>6 Handlers +<br/>CliTextAndInput +<br/>MainMenuRegistry"]
            error["error/<br/>AppError, AppResult"]
            event["event/<br/>AppEvent (sealed)<br/>AppEventBus<br/>InProcessAppEventBus"]
            event_h["event/handlers/<br/>Achievement, Metrics,<br/>Notification"]
            usecase["usecase/<br/>matching/ messaging/<br/>profile/ social/"]
        end

        subgraph core ["core/"]
            core_root["AppClock, AppConfig,<br/>AppSession, ServiceRegistry,<br/>PerformanceMonitor, TextUtil,<br/>EnumSetUtil, LoggingSupport"]
            model["model/<br/>User, Match, ProfileNote"]
            connection["connection/<br/>ConnectionService<br/>ConnectionModels"]
            matching["matching/<br/>8 classes"]
            metrics["metrics/<br/>ActivityMetrics<br/>EngagementDomain<br/>SwipeState"]
            profile["profile/<br/>ProfileService<br/>ValidationService<br/>MatchPreferences"]
            storage_if["storage/<br/>5 interfaces + PageData"]
            time["time/<br/>TimePolicy"]
            workflow["workflow/<br/>2 policies + Decision"]
        end

        subgraph storage ["storage/"]
            dbmgr["DatabaseManager"]
            sfactory["StorageFactory"]
            jdbi["jdbi/<br/>6 implementations"]
            schema["schema/<br/>SchemaInitializer<br/>MigrationRunner"]
        end

        subgraph ui ["ui/"]
            datingapp_ui["DatingApp.java<br/>(JavaFX entry)"]
            nav["NavigationService"]
            ui_util["ImageCache, UiAnimations,<br/>UiComponents, UiConstants,<br/>UiFeedbackService, UiUtils"]
            async["async/<br/>ViewModelAsyncScope<br/>TaskHandle, TaskPolicy<br/>AsyncErrorRouter<br/>UiThreadDispatcher"]
            screen["screen/<br/>12 Controllers"]
            viewmodel["viewmodel/<br/>10 ViewModels +<br/>UiDataAdapters +<br/>ViewModelFactory"]
        end
    end
```

---

## 3. Bootstrap & Initialization Flow

Three entry points share a single bootstrap path.

```mermaid
sequenceDiagram
    participant Entry as Entry Point<br/>(Main / DatingApp / RestApiServer)
    participant AS as ApplicationStartup
    participant AC as AppConfig
    participant DM as DatabaseManager
    participant MR as MigrationRunner
    participant SF as StorageFactory
    participant SR as ServiceRegistry

    Entry->>AS: initialize()
    Note over AS: synchronized,<br/>idempotent

    AS->>AC: load()<br/>config/app-config.json<br/>+ DATING_APP_* env vars
    AC-->>AS: AppConfig

    AS->>DM: getInstance()
    Note over DM: HikariCP pool<br/>→ H2 file DB

    DM->>MR: runAllPending(connection)
    Note over MR: schema_version table<br/>tracks migrations

    AS->>SF: buildH2(dbManager, config)

    SF->>SF: Create Jdbi instance<br/>+ SqlObjectPlugin<br/>+ JdbiTypeCodecs

    SF->>SF: Instantiate 5 JDBI storages
    SF->>SF: Instantiate domain services
    SF->>SF: Instantiate use cases
    SF->>SF: Register event handlers

    SF-->>AS: ServiceRegistry
    AS-->>Entry: ServiceRegistry

    alt CLI Path
        Entry->>Entry: Create Handlers<br/>via Dependencies.fromServices()
        Entry->>Entry: MainMenuRegistry<br/>→ input loop
    else JavaFX Path
        Entry->>Entry: new ViewModelFactory(registry)
        Entry->>Entry: NavigationService.initialize(stage)
        Entry->>Entry: navigateTo(LOGIN)
    else REST Path
        Entry->>Entry: Wrap use cases<br/>in Javalin routes
        Entry->>Entry: Javalin.start(7070)
    end
```

---

## 4. ServiceRegistry Composition

What the registry holds — a pure dependency container with no logic.

```mermaid
graph TB
    SR["ServiceRegistry"]

    subgraph Storage Interfaces
        US[UserStorage]
        IS[InteractionStorage]
        CS[CommunicationStorage]
        AS[AnalyticsStorage]
        TS[TrustSafetyStorage]
    end

    subgraph Domain Services
        CF[CandidateFinder]
        MS[MatchingService]
        RS[RecommendationService]
        MQS[MatchQualityService]
        UDS[UndoService]
        TSS[TrustSafetyService]
        PS[ProfileService]
        VS[ValidationService]
        AMS[ActivityMetricsService]
        CNS[ConnectionService]
    end

    subgraph Use Cases ["Use Cases (built in constructor)"]
        MUC[MatchingUseCases]
        MSUC[MessagingUseCases]
        PUC[ProfileUseCases]
        SUC[SocialUseCases]
    end

    subgraph Infrastructure
        AC[AppConfig]
        TP[TimePolicy]
        EB[AppEventBus]
        PAP[ProfileActivationPolicy]
        RWP[RelationshipWorkflowPolicy]
    end

    SR --- US & IS & CS & AS & TS
    SR --- CF & MS & RS & MQS & UDS
    SR --- TSS & PS & VS & AMS & CNS
    SR --- MUC & MSUC & PUC & SUC
    SR --- AC & TP & EB & PAP & RWP

    style SR fill:#1565c0,color:#fff
    style MUC fill:#e3f2fd
    style MSUC fill:#e3f2fd
    style PUC fill:#e3f2fd
    style SUC fill:#e3f2fd
```

---

## 5. Storage Layer

```mermaid
graph TB
    subgraph Interfaces ["Storage Interfaces (core/storage/)"]
        USI["UserStorage"]
        ISI["InteractionStorage"]
        CSI["CommunicationStorage"]
        ASI["AnalyticsStorage"]
        TSI["TrustSafetyStorage"]
    end

    subgraph JDBI ["JDBI Implementations (storage/jdbi/)"]
        JUS["JdbiUserStorage"]
        JMS["JdbiMatchmakingStorage"]
        JCS["JdbiConnectionStorage"]
        JME["JdbiMetricsStorage"]
        JTS["JdbiTrustSafetyStorage"]
        JTC["JdbiTypeCodecs"]
    end

    subgraph Infra ["Infrastructure"]
        DM["DatabaseManager<br/>(HikariCP pool)"]
        JDBI_OBJ["Jdbi Instance<br/>+ SqlObjectPlugin"]
        H2["H2 File Database<br/>./data/dating"]
    end

    subgraph Schema ["Schema (storage/schema/)"]
        SI["SchemaInitializer<br/>(DDL)"]
        MR["MigrationRunner<br/>(versioned migrations)"]
        SV["schema_version table"]
    end

    JUS -->|implements| USI
    JMS -->|implements| ISI
    JMS -.->|also provides| UNDO["Undo.Storage"]
    JCS -->|implements| CSI
    JME -->|implements| ASI
    JME -.->|also implements| STST["Standout.Storage"]
    JTS -->|implements| TSI

    JTC -->|registers codecs on| JDBI_OBJ
    JUS & JMS & JCS & JME --> JDBI_OBJ
    JTS -.->|onDemand proxy| JDBI_OBJ
    JDBI_OBJ --> DM
    DM --> H2
    DM -->|first connection| SI
    DM -->|first connection| MR
    MR --> SV

    style Interfaces fill:#e8f5e9,stroke:#2e7d32
    style JDBI fill:#fff3e0,stroke:#e65100
```

**Key detail:** `JdbiMatchmakingStorage` and `JdbiMetricsStorage` are dual-role — each implements multiple storage interfaces. `StorageFactory` casts them appropriately.

---

## 6. Domain Services

```mermaid
graph TB
    subgraph Matching ["core/matching/"]
        CF["CandidateFinder<br/>geo, age, gender<br/>filtering"]
        MS["MatchingService<br/>swipe processing,<br/>mutual-like detection"]
        RS["RecommendationService<br/>daily picks,<br/>standouts, like budget"]
        MQS["MatchQualityService<br/>match quality scoring"]
        UDS["UndoService<br/>time-windowed undo"]
        TSS["TrustSafetyService<br/>block/report/ban"]
        CSCORE["CompatibilityScoring<br/>scoring algorithm"]
        LM["LifestyleMatcher"]
        IM["InterestMatcher"]
    end

    subgraph Connection ["core/connection/"]
        CNS["ConnectionService<br/>messaging, conversations,<br/>relationship transitions"]
        CM["ConnectionModels<br/>Like, Conversation,<br/>Message, FriendRequest,<br/>Notification, Report"]
    end

    subgraph Profile ["core/profile/"]
        PS["ProfileService<br/>completion scoring,<br/>tier calc, achievements"]
        VS["ValidationService<br/>field validation"]
        MP["MatchPreferences<br/>Interest, Lifestyle,<br/>Dealbreakers enums"]
    end

    subgraph Metrics ["core/metrics/"]
        AMS["ActivityMetricsService<br/>session tracking,<br/>swipe counts"]
        ED["EngagementDomain<br/>Achievement, UserStats"]
    end

    subgraph Workflow ["core/workflow/"]
        PAP["ProfileActivationPolicy<br/>INCOMPLETE → ACTIVE"]
        RWP["RelationshipWorkflowPolicy<br/>valid state transitions"]
    end

    subgraph Model ["core/model/"]
        U["User<br/>+ Gender, UserState,<br/>VerificationMethod (nested)"]
        M["Match<br/>+ MatchState,<br/>MatchArchiveReason (nested)"]
        PN["ProfileNote"]
    end

    CSCORE --> LM & IM
    MS --> CSCORE
    CF --> U
    MS --> M
    CNS --> CM
    PS --> U
    CNS -->|uses| RWP

    style Matching fill:#e8eaf6,stroke:#283593
    style Connection fill:#e0f2f1,stroke:#00695c
    style Profile fill:#fff8e1,stroke:#f57f17
    style Model fill:#f3e5f5,stroke:#6a1b9a
```

---

## 7. Use-Case Layer

All use cases follow a consistent pattern: typed input records → `UseCaseResult<T>` output → events published on success.

```mermaid
graph LR
    subgraph Input
        CMD["Typed Command/Query<br/>(nested static records)"]
        CTX["UserContext<br/>(UUID userId)"]
    end

    subgraph UseCases ["Use Cases"]
        MUC["MatchingUseCases<br/>browseCandidates<br/>processSwipe<br/>undoSwipe<br/>listActiveMatches<br/>pendingLikers<br/>standouts<br/>matchQuality"]
        MSUC["MessagingUseCases<br/>listConversations<br/>openConversation<br/>sendMessage<br/>markConversationRead<br/>totalUnreadCount"]
        PUC["ProfileUseCases<br/>saveProfile<br/>updatePreferences<br/>getAchievements<br/>calculateCompletion<br/>generatePreview"]
        SUC["SocialUseCases<br/>blockUser<br/>reportUser<br/>unmatch<br/>requestFriendZone<br/>gracefulExit<br/>respondToFriendRequest<br/>notifications"]
    end

    subgraph Output
        RES["UseCaseResult&lt;T&gt;<br/>success | error"]
        ERR["UseCaseError<br/>validation | notFound<br/>conflict | dependency<br/>internal | forbidden"]
        EVT["AppEvent<br/>(published on success)"]
    end

    CMD --> MUC & MSUC & PUC & SUC
    CTX --> MUC & MSUC & PUC & SUC
    MUC & MSUC & PUC & SUC --> RES
    RES -.-> ERR
    MUC & MSUC & PUC & SUC -->|on success| EVT
```

---

## 8. Event System

```mermaid
graph TB
    subgraph Events ["AppEvent (sealed interface)"]
        SR_E["SwipeRecorded"]
        MC_E["MatchCreated"]
        PS_E["ProfileSaved"]
        FRA_E["FriendRequestAccepted"]
        RT_E["RelationshipTransitioned"]
        MS_E["MessageSent"]
    end

    BUS["InProcessAppEventBus<br/>(synchronous, in-process)"]

    subgraph Publishers ["Publishers (Use Cases)"]
        MUC_P["MatchingUseCases"]
        MSUC_P["MessagingUseCases"]
        PUC_P["ProfileUseCases"]
        SUC_P["SocialUseCases"]
    end

    subgraph Handlers ["Event Handlers"]
        AEH["AchievementEventHandler<br/>→ ProfileService<br/>(unlock achievements)"]
        MEH["MetricsEventHandler<br/>→ ActivityMetricsService<br/>(update swipe metrics)"]
        NEH["NotificationEventHandler<br/>→ CommunicationStorage<br/>(create notifications)"]
    end

    MUC_P -->|SwipeRecorded, MatchCreated| BUS
    MSUC_P -->|MessageSent| BUS
    PUC_P -->|ProfileSaved| BUS
    SUC_P -->|FriendRequestAccepted,<br/>RelationshipTransitioned| BUS

    BUS --> AEH
    BUS --> MEH
    BUS --> NEH

    SR_E -.-> AEH
    SR_E -.-> MEH
    MC_E -.-> NEH

    style BUS fill:#1565c0,color:#fff
```

**Design choice:** The event bus is synchronous and in-process. Events are a sealed interface, enabling exhaustive `switch` in Java 25 with pattern matching.

---

## 9. CLI Adapter Layer

```mermaid
graph TB
    MAIN["Main.java<br/>main()"]

    subgraph Handlers ["CLI Handlers (app/cli/)"]
        MH["MatchingHandler"]
        PH["ProfileHandler"]
        SH["SafetyHandler"]
        STH["StatsHandler"]
        MSH["MessagingHandler"]
    end

    MMR["MainMenuRegistry<br/>Map&lt;key, action&gt;<br/>input loop"]

    IR["InputReader<br/>(CliTextAndInput)"]

    MAIN --> MMR
    MMR -->|dispatches| MH & PH & SH & STH & MSH
    MH & PH & SH & STH & MSH --> IR

    subgraph Wiring ["Dependencies Pattern"]
        DEP["Dependencies record<br/>(nested in each handler)"]
        FS["fromServices(<br/>ServiceRegistry,<br/>AppSession,<br/>InputReader)"]
    end

    MAIN -->|constructs via| FS
    FS -->|builds| DEP
    DEP -->|injected into| Handlers

    style MAIN fill:#e65100,color:#fff
    style MMR fill:#fff3e0
```

**Key detail:** CLI handlers construct their own local use-case instances from extracted services rather than using `ServiceRegistry.getMatchingUseCases()`. This means CLI and registry hold separate use-case instances.

---

## 10. JavaFX UI Layer (MVVM)

```mermaid
graph TB
    DA["DatingApp.java<br/>JavaFX Application"]
    NS["NavigationService<br/>(singleton)<br/>FXML loading + history stack"]
    VMF["ViewModelFactory<br/>(lazy, synchronized)"]

    subgraph Controllers ["Screen Controllers (ui/screen/)"]
        BC["BaseController<br/>(abstract)"]
        LC["LoginController"]
        DC["DashboardController"]
        PC["ProfileController"]
        MTC["MatchingController"]
        MAC["MatchesController"]
        CC["ChatController"]
        STC["StatsController"]
        PRC["PreferencesController"]
        SDC["StandoutsController"]
        SOC["SocialController"]
    end

    subgraph ViewModels ["ViewModels (ui/viewmodel/)"]
        LVM["LoginViewModel"]
        DVM["DashboardViewModel"]
        PVM["ProfileViewModel"]
        MTVM["MatchingViewModel"]
        MAVM["MatchesViewModel"]
        CVM["ChatViewModel"]
        STVM["StatsViewModel"]
        PRVM["PreferencesViewModel"]
        SDVM["StandoutsViewModel"]
        SOVM["SocialViewModel"]
    end

    subgraph Adapters ["Anti-Corruption Layer"]
        UUS["UiUserStore"]
        UMDA["UiMatchDataAccess"]
        USDA["UiSocialDataAccess"]
    end

    DA -->|init| VMF
    DA -->|init| NS
    NS -->|loads FXML via| VMF
    VMF -->|creates| ViewModels
    VMF -->|injects into| Controllers
    BC -->|extended by| LC & DC & PC & MTC & MAC & CC & STC & PRC & SDC & SOC

    LC --- LVM
    DC --- DVM
    PC --- PVM
    MTC --- MTVM
    MAC --- MAVM
    CC --- CVM
    STC --- STVM
    PRC --- PRVM
    SDC --- SDVM
    SOC --- SOVM

    ViewModels -->|use| Adapters
    Adapters -->|wrap| STOR["core/storage/*<br/>interfaces"]

    style DA fill:#1565c0,color:#fff
    style NS fill:#1976d2,color:#fff
    style Adapters fill:#e8f5e9,stroke:#2e7d32
```

**Navigation mapping:**

| ViewType | FXML | Controller | ViewModel |
|----------|------|------------|-----------|
| LOGIN | login.fxml | LoginController | LoginViewModel |
| DASHBOARD | dashboard.fxml | DashboardController | DashboardViewModel |
| PROFILE | profile.fxml | ProfileController | ProfileViewModel |
| MATCHING | matching.fxml | MatchingController | MatchingViewModel |
| MATCHES | matches.fxml | MatchesController | MatchesViewModel |
| CHAT | chat.fxml | ChatController | ChatViewModel |
| STATS | stats.fxml | StatsController | StatsViewModel |
| PREFERENCES | MatchPreferences.fxml | PreferencesController | PreferencesViewModel |
| STANDOUTS | standouts.fxml | StandoutsController | StandoutsViewModel |
| SOCIAL | social.fxml | SocialController | SocialViewModel |

---

## 11. Async ViewModel Pattern

The `ui/async/` package provides a shared async abstraction for all ViewModels.

```mermaid
sequenceDiagram
    participant Ctrl as Controller<br/>(UI Thread)
    participant VM as ViewModel
    participant Scope as ViewModelAsyncScope
    participant VT as Virtual Thread<br/>(background)
    participant Disp as UiThreadDispatcher<br/>(Platform.runLater)
    participant AER as AsyncErrorRouter

    Ctrl->>VM: user action (e.g. loadMatches())
    VM->>Scope: run("loadMatches", supplier, onSuccess)

    Scope->>Scope: loadingCount.incrementAndGet()
    Note over Scope: loading state → true

    Scope->>VT: Thread.ofVirtual().start()
    VT->>VT: Execute supplier<br/>(DB query, computation)

    alt Success
        VT->>Disp: dispatchIfNeeded(callback)
        Disp->>Ctrl: Platform.runLater(onSuccess)
        Scope->>Scope: loadingCount.decrementAndGet()
    else Error
        VT->>AER: routeError(exception)
        AER->>Disp: dispatch error to UI
        Scope->>Scope: loadingCount.decrementAndGet()
    end

    Note over Scope: Latest-wins variant:<br/>runLatest("key", ...) uses<br/>AtomicLong version tracking<br/>to discard stale results

    Note over Ctrl: On screen exit:<br/>BaseController.cleanup()<br/>→ ViewModel.dispose()<br/>→ Scope.dispose()<br/>cancels all pending tasks
```

**Key primitives:**

| Method | Purpose |
|--------|---------|
| `run(name, supplier, onSuccess)` | Standard async: background work → UI callback |
| `runLatest(key, name, supplier, onSuccess)` | Latest-wins: cancels stale results for same key |
| `runFireAndForget(name, runnable)` | Side-effect only, no result callback |
| `dispose()` | Cancel all tasks (called from ViewModel.dispose()) |

---

## 12. Key Data Flows

### A. User Swipes Right (Like)

```mermaid
sequenceDiagram
    participant User as User (UI/CLI)
    participant UC as MatchingUseCases
    participant MS as MatchingService
    participant IS as InteractionStorage
    participant EB as AppEventBus
    participant AEH as AchievementHandler
    participant MEH as MetricsHandler
    participant NEH as NotificationHandler

    User->>UC: processSwipe(userId, targetId, LIKE)
    UC->>UC: Validate users exist
    UC->>MS: recordLike(user, target)
    MS->>IS: storeLike(like)

    alt Mutual Like Detected
        MS->>IS: storeMatch(match)
        MS-->>UC: Match created
        UC->>EB: publish(MatchCreated)
        EB->>NEH: notify both users
    else One-sided
        MS-->>UC: Like recorded
    end

    UC->>EB: publish(SwipeRecorded)
    EB->>AEH: check achievement unlock
    EB->>MEH: update swipe metrics
    UC-->>User: UseCaseResult.success(...)
```

### B. User Opens a Conversation

```mermaid
sequenceDiagram
    participant Ctrl as ChatController
    participant VM as ChatViewModel
    participant Scope as ViewModelAsyncScope
    participant UC as MessagingUseCases
    participant CNS as ConnectionService
    participant CS as CommunicationStorage

    Ctrl->>VM: loadConversation(matchId)
    VM->>Scope: runLatest("conv", supplier, callback)

    Note over Scope: Background thread

    Scope->>UC: openConversation(userId, matchId)
    UC->>CNS: getConversation(matchId)
    CNS->>CS: findConversation(matchId)
    CS-->>CNS: Conversation + Messages
    CNS-->>UC: conversation
    UC-->>Scope: UseCaseResult.success(conversation)

    Scope->>Ctrl: Platform.runLater(updateUI)
    Ctrl->>Ctrl: Render messages in ListView
```

### C. Profile Completion & Activation

```mermaid
sequenceDiagram
    participant User as User
    participant UC as ProfileUseCases
    participant PS as ProfileService
    participant VS as ValidationService
    participant PAP as ProfileActivationPolicy
    participant US as UserStorage
    participant EB as AppEventBus

    User->>UC: saveProfile(fields)
    UC->>VS: validate(fields, config)

    alt Validation fails
        UC-->>User: UseCaseResult.error(validation)
    else Valid
        UC->>US: save(updatedUser)
        UC->>PS: calculateCompletion(user)
        PS-->>UC: score (0-100)

        UC->>PAP: tryActivate(user)
        alt Profile complete
            PAP->>PAP: INCOMPLETE → ACTIVE
            PAP-->>UC: activated
        else Still incomplete
            PAP-->>UC: no change
        end

        UC->>EB: publish(ProfileSaved)
        UC-->>User: UseCaseResult.success(profile)
    end
```

---

## Appendix: Three Entry Points Summary

```mermaid
graph TB
    subgraph Shared ["Shared Bootstrap"]
        AS["ApplicationStartup.initialize()"]
        SR["ServiceRegistry"]
        AS --> SR
    end

    subgraph CLI ["CLI (Main.java)"]
        M["main()"]
        H["6 Handlers +<br/>MainMenuRegistry"]
        LOOP["stdin loop"]
        M --> AS
        M --> H --> LOOP
    end

    subgraph GUI ["JavaFX (DatingApp.java)"]
        DA["init() / start()"]
        VMF["ViewModelFactory"]
        NS["NavigationService"]
        FX["FXML screens"]
        DA --> AS
        DA --> VMF --> NS --> FX
    end

    subgraph REST ["REST API (RestApiServer.java)"]
        RS["main()"]
        JAV["Javalin (port 7070)"]
        RTS["HTTP Routes"]
        RS --> AS
        RS --> JAV --> RTS
    end

    style Shared fill:#e8f5e9,stroke:#2e7d32
    style CLI fill:#fff3e0,stroke:#e65100
    style GUI fill:#e3f2fd,stroke:#1565c0
    style REST fill:#fce4ec,stroke:#c62828
```
