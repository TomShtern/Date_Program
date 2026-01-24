we are on windows 11, usually using powershell, we are working in VS Code-Insiders. we are using java 25, and using javafx 25.
make sure to leverage the tools you have as an ai coding agent and also the tools we have here on this system.

<system_tools>

# 💻 SYSTEM_TOOL_INVENTORY

### 🛠 CORE UTILITIES: Search, Analysis & Refactoring

- **ripgrep** (`rg`) `v14.1.0` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Primary text search engine.
  - **Capabilities:** Ultra-fast regex search, ignores `.gitignore` by default.
- **fd** (`fd`) `v10.3.0`
  - **Context:** File system traversal.
  - **Capabilities:** User-friendly, fast alternative to `find`.
- **fzf** (`fzf`) `v0.67.0`
  - **Context:** Interactive filtering.
  - **Capabilities:** General-purpose command-line fuzzy finder.
- **tokei** (`tokei`) `v12.1.2` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Codebase Statistics.
  - **Capabilities:** Rapidly counts lines of code (LOC), comments, and blanks across all languages.
- **ast-grep** (`sg`) `v0.40.0` - SUPER IMPORTANT AND USEFUL!
  - **Context:** Advanced Refactoring & Linting.
  You are operating in an environment where ast-grep is installed. For any code search that requires understanding of syntax or code structure, you should default to using ast-grep --lang [language] -p '<pattern>'. Adjust the --lang flag as needed for the specific programming language. Avoid using text-only search tools unless a plain-text search is explicitly requested.
  - **Capabilities:** Structural code search and transformation using Abstract Syntax Trees (AST). Supports precise pattern matching and large-scale automated refactoring beyond regex limitations.
- **bat** (`bat`) `v0.26.0`
  - **Context:** File Reading.
  - **Capabilities:** `cat` clone with automatic syntax highlighting and Git integration.
- **sd** (`sd`) `v1.0.0`
  - **Context:** Text Stream Editing.
  - **Capabilities:** Intuitive find & replace tool (simpler `sed` replacement).
- **jq** (`jq`) `v1.8.1`
  - **Context:** JSON Parsing.
  - **Capabilities:** Command-line JSON processor/filter.
- **yq** (`yq`) `v4.48.2`
  - **Context:** Structured Data Parsing.
  - **Capabilities:** Processor for YAML, TOML, and XML.
- **Semgrep** (`semgrep`) `v1.140.0`
  - **Capabilities:** Polyglot Static Application Security Testing (SAST) and logic checker.

### 🌐 SECONDARY RUNTIMES

- **Node.js** (`node`) `v24.11.1` - JavaScript runtime.
- **Bun** (`bun`) `v1.3.1` - All-in-one JS runtime, bundler, and test runner.
- **Java** (`java`) `JDK 25 & javafx 25` - Java Development Kit.

</system_tools>





# Copilot Instructions - Dating App CLI

## Architecture Overview

**Two-Layer Clean Architecture** with strict separation:

```
core/       Pure Java business logic - NO framework/database imports allowed
storage/    H2 database implementations of core interfaces
```

### Critical Rule
The `core/` package must have **zero** framework or database imports. Storage interfaces are defined in `core/` but implemented in `storage/`. This enables easy testing with mocks and swappable backends.

### Key Patterns

- **Domain Models**: `User` (mutable entity with state machine), `Like`/`Block`/`Report` (immutable records), `Match` (mutable with deterministic ID from sorted UUIDs)
- **State Machines**: `User`: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`. `Match`: `ACTIVE → UNMATCHED | BLOCKED`
- **Dependency Wiring**: Use `ServiceRegistryBuilder` to construct `ServiceRegistry` - never instantiate services directly in production code

### Data Flow

1. `CandidateFinder.findCandidates()` - 7-stage filter pipeline (self, state, interactions, gender, age, distance, dealbreakers)
2. `MatchingService.recordLike()` - Saves like, creates Match on mutual interest
3. `UndoService` - In-memory state tracking with 30s expiration window

## Build & Test Commands

```bash
mvn compile                          # Compile
mvn exec:java                        # Run app
mvn test                             # All tests
mvn test -Dtest=MatchingServiceTest  # Single test class
mvn test -Dtest=MatchingServiceTest#mutualLikesCreateMatch  # Single method
mvn package                          # Fat JAR
```

## Testing Conventions

- Tests use **JUnit 5 with nested `@Nested` classes** for logical grouping
- Core tests use **in-memory mock implementations** defined inline in test files (see `src/test/java/datingapp/core/MatchingServiceTest.java` line 115+ for pattern)
- Storage tests use real H2 database

### Mock Storage Pattern
```java
private static class InMemoryLikeStorage implements LikeStorage {
    private final Map<String, Like> likes = new HashMap<>();
    // Implement interface methods with simple in-memory logic
}
```

## Code Conventions

### Records vs Classes
- **Immutable data**: Use Java `record` (see `Like.java`, `Block.java`, `MatchQuality.java`)
- **Mutable entities**: Use class with explicit state transitions (see `User.java`, `Match.java`)

### Match ID Generation
Match IDs are deterministic: `userA_userB` where userA is lexicographically smaller UUID. Use `Match.generateId(a, b)` to compute.

### Validation
- All entity constructors validate with `Objects.requireNonNull()`
- Domain-specific validation throws `IllegalArgumentException` (e.g., "Cannot like yourself")
- State transition violations throw `IllegalStateException`

## Known Limitations (Don't Try to Fix)

- **No transactions**: Like/Match deletions in undo flow are not atomic
- **In-memory undo state**: Lost on restart - Phase 0 constraint
- **Interest score placeholder**: `MatchQualityService` interest calculation returns 0.5

## File Structure Reference

| Purpose            | Location                                                    |
|--------------------|-------------------------------------------------------------|
| Domain models      | `src/main/java/datingapp/core/{User,Like,Match,Block}.java` |
| Storage interfaces | `src/main/java/datingapp/core/*Storage.java`                |
| Services           | `src/main/java/datingapp/core/*Service.java`                |
| H2 implementations | `src/main/java/datingapp/storage/H2*.java`                  |
| Service wiring     | `src/main/java/datingapp/core/ServiceRegistryBuilder.java`  |
| Configuration      | `src/main/java/datingapp/core/AppConfig.java`               |
