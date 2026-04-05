# Resume-Ready Project Description Options

Generated from verified evidence in `src/main/java`, `src/test/java`, and `pom.xml` on 2026-04-05.

## Version 1 — Balanced

**Project title:** Dating App

**1-line summary:** Developed a Java-based dating application that shares the same domain and use-case layer across a CLI, JavaFX desktop UI, and a localhost REST API.

**Resume bullets:**
- Implemented profile creation, activation, editing, discovery preferences, private profile notes, soft-delete account handling, and email/phone verification workflows.
- Built matching features including candidate browsing, likes and passes, undo, pending likers, daily status, daily picks, standouts, and per-match quality snapshots.
- Added messaging and social features such as conversation previews, unread counts, message send/archive/delete actions, friend requests, notifications, unmatch/graceful-exit flows, and block/report moderation.
- Structured the codebase into `core`, `app`, `storage`, and `ui` layers with dedicated use-case classes, a centralized `ServiceRegistry`, and in-process event handlers for achievements, metrics, and notifications.
- Backed the application with JDBI-based SQL storage and configuration-driven startup for H2 and PostgreSQL runtime paths, with JUnit 5 tests and Maven quality checks.

**Tech stack:** Java 25, JavaFX 25, Javalin, Jackson, JDBI 3, H2, PostgreSQL JDBC, HikariCP, Maven, JUnit 5, Spotless, Checkstyle, PMD, JaCoCo.

**Polished paragraph:** Developed a multi-interface dating application in Java with shared business logic across CLI, JavaFX, and a localhost REST API. The codebase covers profile management, built-in location selection, matching, messaging, moderation, verification, notes, and stats-related workflows, while keeping domain logic separate from UI, transport, and persistence concerns. Data access is implemented through JDBI-backed SQL storage with H2 and PostgreSQL runtime paths, and the project is supported by automated tests and Maven quality gates.

## Version 2 — Architecture-Focused

**Project title:** Layered Java Dating Application

**1-line summary:** Built a layered Java application that exposes the same dating workflows through interactive CLI menus, a JavaFX desktop client, and a localhost REST layer.

**Resume bullets:**
- Exposed the same app behavior through three adapters: CLI handlers, JavaFX controllers/ViewModels, and a Javalin-based localhost REST API.
- Centralized dependency wiring through explicit composition roots and a `ServiceRegistry` rather than framework-based dependency injection.
- Isolated business flows into focused use-case bundles for profile, matching, messaging, verification, and social/moderation workflows shared across adapters.
- Used an in-process event bus to handle achievement, metrics, and notification side effects from swipes, matches, messages, and relationship transitions.
- Enforced architectural boundaries with tests that keep the `core` package free of JavaFX, JDBI, Javalin, and Jackson imports, and keep ViewModels off direct storage types.

**Tech stack:** Java 25, JavaFX 25, Javalin, Jackson, JDBI 3, H2, PostgreSQL JDBC, HikariCP, Maven, JUnit 5.

**Polished paragraph:** Built a layered Java application with explicit boundaries between domain logic, application use cases, persistence, and presentation. The same workflow layer is reused by a CLI, a JavaFX desktop interface, and a localhost REST adapter, which keeps transport-specific code thin and pushes business rules into shared use-case classes. The project also uses event-driven handlers for cross-cutting concerns and architecture tests to verify boundary rules directly in the codebase.

## Version 3 — Feature-Focused

**Project title:** Multi-Workflow Dating App

**1-line summary:** Implemented a dating application covering onboarding, matching, messaging, moderation, verification, and profile-management flows in a single Java codebase.

**Resume bullets:**
- Delivered onboarding and profile-management flows with activation rules, age and distance preferences, lifestyle fields, built-in location lookup, and private profile notes.
- Implemented recommendation and matching features including browse candidates, likes, passes, undo, pending likers, daily pick status, standouts, and match quality summaries.
- Added messaging and relationship workflows with conversation previews, unread tracking, send/archive/delete actions, friend requests, unmatch handling, and graceful exits.
- Included trust and safety controls such as block/unblock, user reporting, verification-code confirmation, and notification management.
- Reused the same workflow layer across CLI, JavaFX, and REST adapters instead of duplicating business logic per interface.

**Tech stack:** Java 25, JavaFX 25, Javalin, Jackson, JDBI 3, H2, PostgreSQL JDBC, Maven, JUnit 5.

**Polished paragraph:** Implemented a Java dating application with onboarding, profile editing, location selection, matching, conversation management, moderation, and verification flows in shared use-case classes. The same behavior is exposed through CLI, desktop UI, and REST adapters, which makes the project straightforward to present as a multi-interface application rather than a single-surface demo. The repository also shows clear separation between workflow logic, presentation code, and persistence.

## Version 4 — Backend / Platform-Focused

**Project title:** Java Dating App

**1-line summary:** Built a Java application with SQL-backed persistence, a desktop UI, a CLI, and a localhost API for dating and social-interaction workflows.

**Resume bullets:**
- Implemented SQL-backed persistence interfaces and JDBI repositories for users, interactions, conversations, analytics, and trust/safety data.
- Wired runtime storage through configuration-driven bootstrap code that can build H2 compatibility paths and SQL runtime paths with PostgreSQL support.
- Added local REST endpoints for user, matching, messaging, profile notes, verification, and moderation workflows using Javalin and Jackson.
- Built a JavaFX MVVM-style UI with controllers, ViewModels, async UI dispatch helpers, and session-aware navigation.
- Maintained build quality with JUnit 5, Spotless (Palantir format), Checkstyle, PMD, and JaCoCo.

**Tech stack:** Java 25, JavaFX 25, Javalin, Jackson, JDBI 3, H2, PostgreSQL JDBC, HikariCP, Maven, JUnit 5, Spotless, Checkstyle, PMD, JaCoCo.

**Polished paragraph:** Built a Java application around a shared domain and workflow layer, then connected it to a JavaFX desktop client, an interactive CLI, and a localhost REST API. The repository includes JDBI-based SQL persistence, configuration-driven startup, H2 and PostgreSQL runtime paths, and event-handler registration for cross-cutting concerns. It also uses a Maven quality pipeline with tests, formatting, static analysis, and coverage checks.

## Version 5 — API and Shared Workflow Focus

**Project title:** Multi-Interface Dating Application

**1-line summary:** Built a Java dating application with shared workflow logic exposed through a CLI, JavaFX desktop UI, and a localhost REST API.

**Resume bullets:**
- Implemented shared use-case flows for profile management, matching, messaging, verification, notes, and moderation across all three interfaces.
- Added REST routes for users, candidates, matches, conversations, notifications, verification, and profile notes using Javalin and Jackson.
- Kept business logic out of transport and UI layers by routing adapters through dedicated use-case classes and a centralized `ServiceRegistry`.
- Supported H2 and PostgreSQL runtime paths through JDBI-based SQL storage and configuration-driven bootstrap code.

## Version 6 — Layered Architecture Focus

**Project title:** Layered Java Dating App

**1-line summary:** Developed a layered Java application that separates core domain logic from UI, API, and persistence adapters for dating and social-interaction workflows.

**Resume bullets:**
- Organized the codebase into `core`, `app`, `storage`, and `ui` layers, with tests enforcing framework-boundary rules.
- Centralized dependency wiring through explicit composition roots and a `ServiceRegistry` rather than embedding workflow logic in controllers or routes.
- Isolated business flows into dedicated use-case bundles for matching, messaging, profile mutation, profile insights, notes, verification, and social features.
- Used an in-process event bus to handle achievement, metrics, and notification side effects from user actions.

## Version 7 — Feature Breadth Focus

**Project title:** Dating App

**1-line summary:** Implemented a Java dating application with onboarding, location selection, matching, messaging, moderation, verification, and profile-note workflows in one codebase.

**Resume bullets:**
- Delivered profile onboarding with activation rules, discovery preferences, lifestyle fields, and built-in location lookup and resolution.
- Implemented candidate browsing, likes and passes, undo, pending likers, standouts, daily pick status, and match quality scoring.
- Added conversation management, unread tracking, friend requests, notifications, unmatch and graceful-exit handling, and block/report controls.
- Exposed the same workflows through CLI, JavaFX, and localhost REST adapters.

## Version 8 — Engineering Quality Focus

**Project title:** Java Dating App

**1-line summary:** Built a Java application with desktop, CLI, and REST adapters backed by SQL storage and a Maven-based testing and quality pipeline.

**Resume bullets:**
- Implemented JDBI-backed storage for users, interactions, conversations, analytics, and trust/safety workflows with H2 and PostgreSQL runtime paths.
- Reused shared use-case classes across JavaFX ViewModels, CLI handlers, and Javalin REST handlers instead of duplicating business logic per interface.
- Maintained automated verification with JUnit 5 and repository quality gates using JaCoCo, Spotless, Checkstyle, and PMD.
- Verified architectural boundaries in tests to keep core domain code isolated from JavaFX, JDBI, Javalin, and Jackson framework dependencies.

## Definitive tech stack guide

If you want the **definitive** stack for this repository, use the **full source-verified stack** below. That is the most complete accurate answer. The shorter stack lines after it are **presentation variants of the same stack**, not different truths.

### Full source-verified stack

**What this includes:** direct technologies proven by `pom.xml`, plus the actual app surfaces proven in `src/main/java`.

- **Language/runtime:** Java 25 with preview features enabled
- **Application surfaces:** CLI, JavaFX desktop UI, localhost REST API
- **UI libraries:** JavaFX 25.0.2, AtlantaFX 2.1.0, Ikonli 12.4.0
- **API / serialization:** Javalin 6.7.0, Jackson 2.21.0
- **Persistence / data:** JDBI 3.51.0, HikariCP 6.3.0, H2 2.4.240, PostgreSQL JDBC 42.7.8
- **Logging:** SLF4J 2.0.17, Logback 1.5.28
- **Security / support libraries:** OWASP Java HTML Sanitizer, metadata-extractor, JetBrains annotations
- **Build / test / quality:** Maven, JUnit 5.14.2, JaCoCo, Spotless (Palantir Java Format 2.85.0), Checkstyle, PMD

**Longest accurate one-line stack:** Java 25, CLI, JavaFX 25.0.2, AtlantaFX, Ikonli, localhost REST API with Javalin and Jackson, JDBI 3, HikariCP, H2, PostgreSQL JDBC, SLF4J/Logback, OWASP Java HTML Sanitizer, metadata-extractor, Maven, JUnit 5, JaCoCo, Spotless, Checkstyle, and PMD.

### Shorter stack variations

**1. General resume version**
Java 25, JavaFX, REST API (Javalin/Jackson), JDBI, H2/PostgreSQL, HikariCP, Maven, JUnit 5.
**Diff:** keeps the main language, UI, API surface, persistence, database, and test tools; drops theme/icon libraries, logging libraries, support utilities, and quality plugins.

**2. Product-facing version**
Java 25, CLI, JavaFX desktop UI, REST API, JDBI, H2/PostgreSQL.
**Diff:** focuses on what the application exposes and how it stores data; drops implementation-detail libraries like Jackson, HikariCP, logging, and test/quality tooling.

**3. Engineering-focused version**
Java 25, JavaFX, Javalin, Jackson, JDBI, HikariCP, H2/PostgreSQL, Maven, JUnit 5, JaCoCo, Spotless, Checkstyle, PMD.
**Diff:** keeps core frameworks plus build/test/quality tooling; drops UI polish libraries and lower-signal support dependencies.

**4. Backend-leaning version**
Java 25, Javalin, Jackson, JDBI, HikariCP, H2/PostgreSQL, Maven, JUnit 5.
**Diff:** emphasizes REST and persistence; drops JavaFX, CLI, theme/icons, and most support libraries.

**5. UI-leaning version**
Java 25, JavaFX, AtlantaFX, Ikonli, JDBI, H2/PostgreSQL.
**Diff:** emphasizes the desktop client and storage layer; drops the REST/API stack, logging, and quality tooling.

**6. Shortest safe version**
Java 25, JavaFX, Javalin, JDBI, H2/PostgreSQL, Maven, JUnit 5.
**Diff:** this is the shortest version I would still consider accurate for a CV; it keeps only the headline technologies and omits most supporting libraries and engineering tooling.

### Which one to use

- Use **Full source-verified stack** when you want the most complete and defensible answer.
- Use **General resume version** for most CVs.
- Use **Engineering-focused version** if you want testing and quality tooling to show up.
- Use **Backend-leaning version** for backend/API roles.
- Use **UI-leaning version** for desktop/UI roles.
- Use **Shortest safe version** only when space is tight.
