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

A modern dating application built with **Clean Architecture** principles, featuring both a JavaFX graphical interface and a command-line interface.

## ✨ Features

- **User Profiles** — Complete profile management with state machine (Incomplete → Active ↔ Paused → Banned)
- **Smart Matching** — Bidirectional preference filtering with distance, age, and dealbreaker support
- **Match System** — Deterministic match IDs, match quality scoring, and relationship transitions
- **Messaging** — Real-time chat between matched users with read receipts and archival support
- **Connection Models** — Unified handling of Messages, Likes, Blocks, Reports, and Friend Requests
- **Statistics & Achievements** — Track activity metrics and unlock achievements
- **Undo Actions** — Reverse accidental swipes within a configurable window
- **Daily Picks** — Seeded-random daily recommendations

## 🛠 Tech Stack

| Category            | Technology                 | Version                       |
|---------------------|----------------------------|-------------------------------|
| **Language**        | Java                       | 25 (preview features enabled) |
| **UI Framework**    | JavaFX                     | 25.0.2                        |
| **UI Theme**        | AtlantaFX (Primer-based)   | 2.1.0                         |
| **Icons**           | Ikonli (Material Design 2) | 12.4.0                        |
| **Database Access** | JDBI 3 (Declarative SQL)   | 3.51.0                        |
| **Database**        | H2 (embedded)              | 2.4.240                       |
| **JSON**            | Jackson                    | 2.21.0                        |
| **Testing**         | JUnit 5                    | 5.14.2                        |
| **Logging**         | SLF4J + Logback            | 2.0.17 / 1.5.28               |
| **Build**           | Maven                      | 3.9+                          |

## 🚀 Getting Started

### Prerequisites

- **Java 25** (JDK with preview features support)
- **Maven 3.9+**
- **Windows 11**: Enable UTF-8 encoding (see [Windows Setup](#-windows-setup))

### Running the Application

#### GUI Mode (JavaFX) — *Recommended*

```bash
mvn javafx:run
```

#### CLI Mode

```bash
mvn compile && mvn exec:exec
```

## 🧪 Testing

Run the full test suite:

```bash
mvn clean test
```

**800+ tests** covering core domain, services, and storage layers.

## 🛡 Code Quality

| Tool           | Purpose                                | Enforcement          |
|----------------|----------------------------------------|----------------------|
| **Spotless**   | Code formatting (Palantir Java Format) | Blocking on `verify` |
| **Checkstyle** | Style validation                       | Blocking on `verify` |
| **PMD**        | Bug & code smell detection             | Blocking on `verify` |
| **JaCoCo**     | Test coverage (60% min on `core/`)     | Blocking on `verify` |

### Commands

```bash
# Format code
mvn spotless:apply

# Run all checks + tests
mvn verify
```

## 🏗 Architecture

```
datingapp/
├── core/           # Pure domain models & business logic (no framework deps)
│   ├── connection/ # ConnectionModels (Message, Like, Block, etc.)
│   └── storage/    # Storage interfaces (dependency inversion)
├── storage/        # JDBI-based persistence (H2 database)
│   └── jdbi/       # SQLObject implementations
├── ui/             # JavaFX GUI (MVVM pattern)
│   ├── controller/ # FXML controllers
│   └── viewmodel/  # UI state management
└── app/cli/        # Command-line interface handlers
```

**Key Principles:**
- **Core stays pure** — Only `java.*` imports in domain layer.
- **Manual DI** — `ServiceRegistry` pattern (no Spring/framework annotations).
- **Fail-fast validation** — Constructor validation with `Objects.requireNonNull`.
- **Phase 2.1 Architecture** — Consolidated domain models and streamlined storage interfaces.

## 💾 Database

Embedded H2 database stored at `./data/dating.mv.db`

| Setting      | Value                                                           |
|--------------|-----------------------------------------------------------------|
| **User**     | `sa`                                                            |
| **Password** | Environment variable `DATING_APP_DB_PASSWORD` or default: `dev` |

> **Note:** In production, use the environment variable for password management.

## 📚 Documentation

| Document               | Description                                     |
|------------------------|-------------------------------------------------|
| [GEMINI.md](GEMINI.md) | AI agent operational context & coding standards |
| [STATUS.md](STATUS.md) | Implementation status vs Product Requirements   |
| [docs/](docs/)         | Additional documentation and completed plans    |

## 📈 Project Statistics

| Metric            | Value                            |
|-------------------|----------------------------------|
| **Java Files**    | ~139 (Main + Test)               |
| **Lines of Code** | ~45K total (~34K code)           |
| **Test Cases**    | ~802                             |
| **Core Services** | 9 (+ CandidateFinder utility)    |
| **GUI Views**     | 10 FXML screens                  |
| **CLI Handlers**  | 8 command handlers               |

## 🪟 Windows Setup

Enable UTF-8 encoding in PowerShell/CMD for proper emoji display:

```powershell
chcp 65001
```

**Permanent fix:**
1. Run `intl.cpl`
2. Administrative → Change system locale
3. Check "Beta: Use Unicode UTF-8 for worldwide language support"
4. Restart




## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
1|2026-01-30 20:45:00|agent:antigravity|docs|Complete README rewrite: updated title, tech stack, architecture, test count (99→576), formatting tool (Google→Palantir), added GUI docs, removed stale Recent Changes|README.md
2|2026-02-08 11:15:00|agent:claude_code|docs|Fixed stale CLI commands (removed shade/fat JAR), updated stats (182 files, 820 tests, 8 handlers), Checkstyle+PMD now blocking|README.md
3|2026-02-19 20:30:00|agent:gemini|docs|Updated README to reflect Java 25, Phase 2.1 architecture, and latest file counts|README.md
---AGENT-LOG-END---
