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



# Dating App CLI

A command-line based dating application refactored for clean architecture and strict code quality.

## Getting Started

### Prerequisites
- Java 21+
- Maven
- **Windows 11**: UTF-8 console encoding (see below)

### Windows 11 Setup (REQUIRED)

**Enable UTF-8 in PowerShell/CMD** to display emojis correctly:

```powershell
chcp 65001
```

Then run the application. Or set permanently:
1. Run `intl.cpl`
2. Administrative → Change system locale
3. Check "Beta: Use Unicode UTF-8 for worldwide language support"
4. Restart

### Running the Application

**Option 1: Via Maven** (may have input buffering)
```bash
mvn compile exec:java
```

**Option 2: Via JAR** (recommended for better terminal support)
```bash
mvn package
java -jar target/dating-app-1.0.0-shaded.jar
```

### Running Tests
To run the full test suite (99 tests):
```bash
mvn clean test
```

## Code formatting (Spotless) 🔧
We use Spotless (com.diffplug.spotless:spotless-maven-plugin v3.1.0) with `google-java-format` (v1.19.1) to keep code style consistent.

- Format locally: `mvn spotless:apply`
- Verify in CI: `mvn spotless:check` (bound to `verify` by default)
- Optional: install pre-push hook: `mvn spotless:install-git-pre-push-hook`

Note: The first-time run may reformat many files; review `git diff` before committing.

## Important Configuration

### Database
The application uses an embedded H2 database located in `./data/dating`.

**Credentials:**
- **User**: `sa`
- **Password**: `changeit`

> **Note**: The password was added to satisfy security warnings. In a production environment, this would be managed via environment variables.

## Recent Changes
- Fixed H2 database locking issues by removing `AUTO_SERVER=TRUE` during tests.
- Refactored `Main.java` for better resource management.
- Resolved all IDE warnings and compilation errors.




## Agent Changelog (append-only)
---AGENT-LOG-START---
# Format: SEQ|TS|agent|scope|summary|files
# Append-only. Do not edit past entries. If SEQ conflict after 3 tries append ":CONFLICT".
example: 1|2026-01-14 16:42:11|agent:claude_code|UI-mig|JavaFX→Swing; examples regen|src/ui/*
---AGENT-LOG-END---
