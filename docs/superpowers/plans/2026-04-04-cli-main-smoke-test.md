# CLI Main Smoke Test Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one deterministic top-level CLI smoke test that drives `Main.main(...)` through real startup, one explicit menu selection, clean shutdown, and verifies a few stable output markers.

**Architecture:** Extend the existing `MainBootstrapLifecycleTest` integration seam instead of refactoring production code. Feed scripted `System.in` with `0\n`, capture only the `Main` logger at `INFO`, assert a logged-out menu marker plus explicit exit/shutdown behavior, and keep the test intentionally narrow so it validates the top-level CLI without snapshotting the whole menu.

**Tech Stack:** Java 25, Maven, JUnit 5, logback `ListAppender`, existing `MainBootstrapLifecycleTest`, `ApplicationStartup`, `DatabaseManager`.

---

### Task 1: Add a red top-level smoke test
- Modify `src/test/java/datingapp/MainBootstrapLifecycleTest.java`
- Reuse the isolated H2/test-profile setup already present.
- Drive `Main.main(new String[0])` with `System.in = "0\n"`.
- Capture the `datingapp.Main` logger at `INFO` and assert only stable sentinel lines.

### Task 2: Implement the smallest support needed
- Prefer zero production changes.
- If the new test fails because logger capture or lifecycle reset is missing, add only the minimum test-side support in `MainBootstrapLifecycleTest.java`.

### Task 3: Verify
- Run the focused `MainBootstrapLifecycleTest` / `MainLifecycleTest` pack.
- Run the broader CLI smoke-adjacent pack if needed.
- Run `mvn spotless:apply verify`.
