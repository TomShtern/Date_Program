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
