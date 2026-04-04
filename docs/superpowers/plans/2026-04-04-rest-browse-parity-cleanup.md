# REST Browse Parity Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align `GET /api/users/{id}/candidates` with the canonical browse flow so REST browse-related routes use the same app-layer ranking and eligibility behavior.

**Architecture:** Keep `/api/users/{id}/browse` as the rich canonical browse endpoint that returns metadata, and keep `/api/users/{id}/candidates` as the simpler array-shaped endpoint for compatibility. Internally, route both through `MatchingUseCases.browseCandidates(...)` and map only the candidates list for `/candidates`, removing the deliberate direct-read exception.

**Tech Stack:** Java 25, Javalin, Maven, JUnit 5, existing `RestApiServer`, `MatchingUseCases`, `RestApiDtos`, `RestApiReadRoutesTest`.

---

### Task 1: Add failing REST parity tests
- Modify `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- Assert `/candidates` preserves array shape while matching `/browse` candidate IDs and order.
- Replace the old direct-read-exception expectation with parity expectations.

### Task 2: Implement route parity
- Modify `src/main/java/datingapp/app/api/RestApiServer.java`
- Reuse `matchingUseCases.browseCandidates(...)` for `/candidates` and map only the candidate summaries.
- Remove the raw direct-read helper and stale comment.

### Task 3: Verify
- Run focused REST read-route tests.
- Run broader browse/matching REST regression tests.
- Run `mvn spotless:apply verify`.
