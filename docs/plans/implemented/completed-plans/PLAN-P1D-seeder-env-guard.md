# Plan P1-D — Guard DevDataSeeder with Environment Variable

> **Phase:** 1 — Finish & Polish (current priority)
> **Effort:** Small (~15 lines across 2 files)
> **Blocked by:** Nothing — fully independent
> **Source-verified:** 2026-03-13 against actual code

---

## Verification & Progress (updated 2026-03-13)

- [x] Plan validated against current source.
- [x] `ApplicationStartup.initialize(...)` now guards seeding behind `DATING_APP_SEED_DATA=true`.
- [x] Existing 3-argument seeder call signature preserved (`UserStorage`, `InteractionStorage`, `CommunicationStorage`).
- [x] `.vscode/launch.json` updated so all 3 debug profiles set `DATING_APP_SEED_DATA=true`.
- [x] Test scan performed: no `src/test/java/**` tests call `ApplicationStartup.initialize(...)`, so no test fixture updates were required.
- [x] Full quality gate run (`mvn spotless:apply verify`) completed and recorded.

Verification result recorded (2026-03-13): `BUILD SUCCESS`, `Tests run: 1100, Failures: 0, Errors: 0, Skipped: 2`.

---

## Goal

Prevent `DevDataSeeder` from running in a future production deployment by guarding
it with an environment variable (`DATING_APP_SEED_DATA=true`). Local development
continues to work by setting this variable in `launch.json`.

---

## Current State

**File:** `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` (around lines 98–106 in current file)

```java
// Seed the database with developer test data if the sentinel user is absent.
// DevDataSeeder.seed() is idempotent — it checks for the sentinel UUID before
// inserting, so this is a fast no-op on any non-empty database.
DevDataSeeder.seed(
  services.getUserStorage(), services.getInteractionStorage(), services.getCommunicationStorage());
```

This runs unconditionally on every startup. In a production deployment with a clean
database, it would seed 30 fake test users — which is undesirable.

The `DevDataSeeder` itself has its own idempotency guard (sentinel UUID check at lines
69–75), so the call is safe to run repeatedly on the same database. But on a fresh
production database it has no gate.

---

## Changes Required

### Change 1 — ApplicationStartup

**File:** `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java`

**Before (lines 95–98):**
```java
// Seed the database with developer test data if the sentinel user is absent.
// DevDataSeeder.seed() is idempotent — it checks for the sentinel UUID before
// inserting, so this is a fast no-op on any non-empty database.
DevDataSeeder.seed(services.getUserStorage());
```

**After:**
```java
// Seed the database with developer test data.
// Only runs when DATING_APP_SEED_DATA=true — never in production.
// DevDataSeeder.seed() is idempotent: it checks for the sentinel UUID first.
if ("true".equalsIgnoreCase(System.getenv("DATING_APP_SEED_DATA"))) {
        DevDataSeeder.seed(
          services.getUserStorage(), services.getInteractionStorage(), services.getCommunicationStorage());
    LOG.info("Dev data seeded (DATING_APP_SEED_DATA=true).");
} else {
    LOG.debug("Dev data seeder skipped (DATING_APP_SEED_DATA not set).");
}
```

---

### Change 2 — VS Code launch.json

**File:** `.vscode/launch.json`

Add `"DATING_APP_SEED_DATA": "true"` to the `"env"` object of **all 3 debug
configurations** (CLI, JavaFX GUI, REST API). If a configuration does not have an
`"env"` key yet, add it.

Example for one configuration:
```json
{
    "name": "Dating App — JavaFX GUI",
    "type": "java",
    "request": "launch",
    "mainClass": "datingapp.ui.DatingApp",
    "env": {
        "DATING_APP_SEED_DATA": "true"
    }
}
```

Apply the same pattern to the CLI and REST API configurations.

---

## Files to Modify

| File                                                            | Change                                                |
|-----------------------------------------------------------------|-------------------------------------------------------|
| `src/main/java/datingapp/app/bootstrap/ApplicationStartup.java` | Wrap lines 95–98 with env var guard                   |
| `.vscode/launch.json`                                           | Add `"DATING_APP_SEED_DATA": "true"` to all 3 configs |

---

## Test Requirements

After this change, any test that calls `ApplicationStartup.initialize()` and then
expects seeded users to exist will fail — because the env var is not set in the
test JVM.

**Search `src/test/java/` for `ApplicationStartup.initialize`** to find affected tests.

For each affected test, choose one of these two remedies:

**Option A — Call the seeder directly in the test setup (preferred):**
```java
@BeforeEach
void setup() {
    services = ApplicationStartup.initialize(AppConfig.defaults());
    DevDataSeeder.seed(services.getUserStorage()); // explicit seeding in test
}
```

**Option B — Set the env var in the test (if the test framework supports it):**
Using a library like `system-stubs` or `EnvironmentVariables` JUnit extension.
This is more complex — prefer Option A.

Also run `mvn spotless:apply && mvn verify` to confirm the 60% JaCoCo gate still passes.

---

## Gotchas

- **`System.getenv()` returns `null` when the variable is not set.**
  `"true".equalsIgnoreCase(null)` returns `false` safely — no null check needed.
  This is the correct form to use.

- **Do NOT use `System.getProperty()`** for this guard. Properties are JVM flags
  (`-Dfoo=bar`), not environment variables. Using an env var makes the guard work
  cleanly in Docker (`-e DATING_APP_SEED_DATA=true`) and CI without modifying JVM args.

- **Do NOT invert the guard** (i.e., "run unless `DATING_APP_SEED_DATA=false"`).
  The correct default is OFF — explicit opt-in is safer for production deployments
  where the variable simply won't be set.

- **The `DATING_APP_` prefix is consistent** with the existing env var pattern in
  `ApplicationStartup` (e.g., `DATING_APP_DB_PASSWORD` already exists). Follow it.

- **After this change, if you start the app without setting the env var**, the DB will
  be empty and the login screen will show no users. Confirm `launch.json` is updated
  before testing.

- **`LOG` usage:** `ApplicationStartup` already has a logger field (`LOG`). Check its
  exact declaration to use the right log level methods (likely SLF4J `logger.info()` /
  `logger.debug()`). Match the existing style in the file.

---

## ROADMAP.md Cross-Reference

This task is documented in `ROADMAP.md` under **Phase 1 → P1-E Quick Wins →
DevDataSeeder — Disable in Non-Dev Mode**.
