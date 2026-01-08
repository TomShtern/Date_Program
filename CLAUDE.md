# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Compile
mvn compile

# Run application
mvn exec:java

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=CandidateFinderTest

# Run a single test method
mvn test -Dtest=CandidateFinderTest#excludesSelf

# Build fat JAR (includes all dependencies)
mvn package

# Run the packaged JAR
java -jar target/dating-app-1.0.0.jar
```

## Architecture

This is a **Phase 0** console-based dating app using Java 21, Maven, and H2 embedded database.

### Two-Layer Design

```
core/       Pure Java business logic (NO framework imports, NO database code)
storage/    H2 database implementations of storage interfaces
```

**Rule**: The `core` package must have ZERO framework or database imports. Storage interfaces are defined in `core` but implemented in `storage`.

### Key Components

**Domain Models** (in `core/`):
- `User` - Mutable entity with state machine: `INCOMPLETE → ACTIVE ↔ PAUSED → BANNED`
- `Like` - Immutable record with `LIKE` or `PASS` direction
- `Match` - Mutable entity with state machine: `ACTIVE → UNMATCHED | BLOCKED`. Deterministic ID (sorted UUID concatenation)
- `Block` - Immutable record for bidirectional blocking (when A blocks B, neither can see the other)
- `Report` - Immutable record with reason enum (SPAM, HARASSMENT, FAKE_PROFILE, etc.)

**Services** (in `core/`):
- `CandidateFinder` - Filters candidates by: not-self, must be ACTIVE, not-already-interacted, mutual gender preferences, mutual age preferences, within distance. Sorts by distance.
- `MatchingService` - Records likes and creates matches when mutual likes exist
- `ReportService` - Files reports with auto-blocking and configurable auto-ban threshold

**Storage Interfaces** (defined in `core/`, implemented in `storage/`):
- `UserStorage`, `LikeStorage`, `MatchStorage`, `BlockStorage`, `ReportStorage`

**Utilities** (in `core/`):
- `GeoUtils` - Haversine distance calculation
- `AppConfig` - Immutable configuration record (autoBanThreshold, dailyLikeLimit, etc.)
- `ServiceRegistry` - Dependency container holding all services and storage
- `ServiceRegistryBuilder` - Factory for wiring H2 or in-memory implementations

**Database** (in `storage/`):
- `DatabaseManager` - Singleton managing H2 connections and schema initialization
- `H2*Storage` classes - JDBC implementations of storage interfaces
- Data persists to `./data/dating.mv.db`

### Data Flow

1. User created → state = `INCOMPLETE`
2. Profile completed → state = `ACTIVE`
3. `CandidateFinder.findCandidates()` returns filtered/sorted potential matches
4. `MatchingService.recordLike()` saves like and creates `Match` if mutual
5. `ReportService.report()` files report, auto-blocks reporter→reported, auto-bans at threshold

## Testing

Tests are in `src/test/java/datingapp/`:
- `core/` - Unit tests for domain models and services (pure Java, no DB)
- `storage/` - Integration tests for H2 storage implementations

Tests use JUnit 5.
