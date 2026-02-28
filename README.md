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

# Dating App

A Java 25 dating application with shared domain logic and three adapters:

- CLI (`Main.java` + `app/cli/*`)
- JavaFX desktop UI (`ui/*`)
- REST API (`app/api/RestApiServer.java`)

## Verified snapshot (source-only)

- Java files: **102 main + 77 test = 179 total**
- Java LOC (`tokei`): **55,616 total / 42,154 code / 11,776 blank / 1,686 comments**
- Latest full gate run in this repo state: **BUILD SUCCESS**, tests **899/0/0/0**

## Tech stack

- Java 25 (preview enabled)
- JavaFX 25.0.2
- Maven
- H2 + JDBI
- SLF4J + Logback
- Spotless (Palantir Java Format), Checkstyle, PMD, JaCoCo

## Run locally

```bash
# CLI
mvn compile && mvn exec:exec

# JavaFX GUI
mvn javafx:run

# Tests
mvn test
mvn -Ptest-output-verbose test

# Full quality gate
mvn spotless:apply verify
```

## Architecture (code-verified)

```text
datingapp/
  Main.java
  app/
    api/RestApiServer.java
    bootstrap/ApplicationStartup.java
    cli/{CliTextAndInput,MainMenuRegistry,MatchingHandler,MessagingHandler,ProfileHandler,SafetyHandler,StatsHandler}.java
    usecase/
      common/{UseCaseError,UseCaseResult,UserContext}.java
      matching/MatchingUseCases.java
      messaging/MessagingUseCases.java
      profile/ProfileUseCases.java
      social/SocialUseCases.java
  core/
    AppClock,AppConfig,AppSession,EnumSetUtil,LoggingSupport,PerformanceMonitor,ServiceRegistry,TextUtil
    model/{User,Match,ProfileNote}
    connection/{ConnectionModels,ConnectionService}
    matching/{CandidateFinder,CompatibilityScoring,LifestyleMatcher,MatchingService,MatchQualityService,RecommendationService,Standout,TrustSafetyService,UndoService}
    metrics/{ActivityMetricsService,EngagementDomain,SwipeState}
    profile/{MatchPreferences,ProfileService,ValidationService}
    storage/{AnalyticsStorage,CommunicationStorage,InteractionStorage,PageData,TrustSafetyStorage,UserStorage}
  storage/
    DatabaseManager.java
    StorageFactory.java
    jdbi/{JdbiConnectionStorage,JdbiMatchmakingStorage,JdbiMetricsStorage,JdbiTrustSafetyStorage,JdbiTypeCodecs,JdbiUserStorage}.java
    schema/{MigrationRunner,SchemaInitializer}.java
  ui/
    DatingApp,NavigationService,ImageCache,UiAnimations,UiComponents,UiConstants,UiFeedbackService,UiUtils
    async/{AsyncErrorRouter,JavaFxUiThreadDispatcher,TaskHandle,TaskPolicy,UiThreadDispatcher,ViewModelAsyncScope}
    popup/{MatchPopupController,MilestonePopupController}
    screen/{BaseController,ChatController,DashboardController,LoginController,MatchesController,MatchingController,MilestonePopupController,PreferencesController,ProfileController,SocialController,StandoutsController,StatsController}
    viewmodel/{ChatViewModel,DashboardViewModel,LoginViewModel,MatchesViewModel,MatchingViewModel,PreferencesViewModel,ProfileViewModel,SocialViewModel,StandoutsViewModel,StatsViewModel,UiDataAdapters,ViewModelErrorSink,ViewModelFactory}
```

## Entrypoint wiring

```java
// shared bootstrap
ServiceRegistry services = ApplicationStartup.initialize();
AppSession session = AppSession.getInstance();

// CLI
InputReader inputReader = new CliTextAndInput.InputReader(scanner);
MatchingHandler matching = new MatchingHandler(MatchingHandler.Dependencies.fromServices(services, session, inputReader));
ProfileHandler profile = ProfileHandler.fromServices(services, session, inputReader);
SafetyHandler safety = SafetyHandler.fromServices(services, session, inputReader);
StatsHandler stats = StatsHandler.fromServices(services, session, inputReader);
MessagingHandler messaging = MessagingHandler.fromServices(services, session, inputReader);

// JavaFX
ViewModelFactory vmFactory = new ViewModelFactory(services);
NavigationService nav = NavigationService.getInstance();
nav.setViewModelFactory(vmFactory);
nav.initialize(primaryStage);
```

## Build constraints (`pom.xml`)

- Java release 25 + preview flags enabled
- Spotless check in `verify`
- Checkstyle in `validate`
- PMD in `verify`
- JaCoCo line coverage check in `verify` with minimum `0.60`

## Core domain ownership rules

- Use nested enums from owner models:
  - `User.Gender`, `User.UserState`, `User.VerificationMethod`
  - `Match.MatchState`, `Match.MatchArchiveReason`
- `ProfileNote` is standalone: `datingapp.core.model.ProfileNote`
- Use `AppClock.now()` in domain/service code, not `Instant.now()`
- Use deterministic pair IDs (`generateId(UUID a, UUID b)`) for two-user aggregates

## Related docs

- `AGENTS.md` - development standards
- `CLAUDE.md` - coding and architecture guardrails
- `.github/copilot-instructions.md` - Copilot repository guidance
- `architecture.md` - detailed architecture overview

## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-01-30 20:45:00|agent:antigravity|docs|Complete README rewrite: updated title, tech stack, architecture, test count (99→576), formatting tool (Google→Palantir), added GUI docs, removed stale Recent Changes|README.md
2|2026-02-08 11:15:00|agent:claude_code|docs|Fixed stale CLI commands (removed shade/fat JAR), updated stats (182 files, 820 tests, 8 handlers), Checkstyle+PMD now blocking|README.md
3|2026-02-19 20:30:00|agent:gemini|docs|Updated README to reflect Java 25, Phase 2.1 architecture, and latest file counts|README.md
4|2026-02-28 13:35:00|agent:github_copilot|docs-source-truth-sync|Rewrote README from current source: 179 Java files, ui/async + app/usecase layers, updated entry wiring and quality gates|README.md
---AGENT-LOG-END---
