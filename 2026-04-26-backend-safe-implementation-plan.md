# Backend-Safe Optimization And Hardening Plan

> **For agentic workers:** Use a task-by-task implementation workflow. Keep one task in progress at a time. This plan is intentionally limited to backend-safe work that must not break the current frontend/backend sync.

**Goal:** Improve backend internals, performance, and maintainability in `Date_Program` without changing any frontend-consumed route, field, enum, nullability rule, response wrapper, or notification data contract.

**Architecture:** The protected surface is concentrated in the REST route registry and DTO records. All work in this plan stays below that transport boundary: use cases, services, storage, internal payload assembly, verification scripts, and documentation. The engineer should treat identical observable behavior as a hard requirement and use targeted regression tests to prove it.

**Tech Stack:** Java, Javalin, JUnit 5, Maven, JDBI, PostgreSQL helper scripts, PowerShell

---

## Scope And Boundaries

### Protected surface: do not change

- Do not change route paths or route meanings in `src/main/java/datingapp/app/api/RestRouteSupport.java`.
- Do not change response record shapes, field names, field nullability, enum values, or wrapper structure in:
  - `src/main/java/datingapp/app/api/RestApiDtos.java`
  - `src/main/java/datingapp/app/api/RestApiUserDtos.java`
- Do not change deterministic pair-id semantics enforced through:
  - `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
  - `src/main/java/datingapp/core/model/Match.java`
  - `src/main/java/datingapp/core/connection/ConnectionModels.java`
- Do not change notification `type` names or existing `data` keys.
- Do not change `primaryPhotoUrl` semantics. It remains authoritative when present and is not equivalent to `photoUrls[0]`.
- Do not change `PresentationContextDto` field names, field meanings, or current tag vocabulary as part of this plan.
- Do not change `ConversationSummary` into a richer payload in this plan.
- Do not add new endpoints, new query parameters, or new frontend-facing DTOs in this plan.

### Files already dirty in the worktree: avoid unless explicitly reconciled first

- `src/main/java/datingapp/core/profile/LocationService.java`
- `src/test/java/datingapp/CheckDbTest.java`
- `src/test/java/datingapp/TestJdbiMapping.java`
- `src/test/java/datingapp/support/LivePostgresqlTestConfig.java`

### Explicitly excluded from this plan

- Adding regression coverage for PostgreSQL verification seams

---

## Execution Order

1. Conversation preview read-path optimization
2. Send-message transactional hardening
3. Notification payload assembly cleanup and notification read-path optimization
4. Profile batch lookup and normalized-profile hydration optimization
5. Matching and match-quality internal optimization
6. Local verification script and documentation cleanup

The first four tasks are the best fit for immediate work because they stay fully inside backend internals and avoid the dirty `LocationService` / live-PostgreSQL-test area.

---

## Task 1: Optimize Conversation Preview Loading

**Objective:** Reduce redundant work in conversation-list loading while preserving the exact current `ConversationSummary` behavior.

**Modify:**

- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`

**Test:**

- `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`
- `src/test/java/datingapp/core/connection/ConnectionServiceTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiCommunicationStorageSocialTest.java`

**Instructions:**

- Trace the current `GET /api/users/{id}/conversations` flow from `RestApiServer.getConversations(...)` into `MessagingUseCases.listConversations(...)` and `ConnectionService`.
- Identify repeated work around:
  - loading conversation previews
  - counting messages
  - loading unread counts
  - resolving the other user
  - loading latest-message metadata
- Prefer batching and reuse over repeated per-conversation lookups.
- Keep the current output identical:
  - same conversation ids
  - same ordering
  - same message counts
  - same `lastMessageAt` behavior
  - same authorization behavior
- Do not add preview text, unread count, photo fields, or new wrapper data to the REST response.

**Verification:**

- Run: `mvn --% -q -Dcheckstyle.skip=true -Dtest=MessagingUseCasesTest,ConnectionServiceTest,JdbiCommunicationStorageSocialTest test`
- Then run: `mvn -q spotless:check`

**Done when:**

- The targeted tests pass.
- The optimized code does not require any DTO or route changes.
- Conversation list payloads remain byte-for-byte shape compatible.

---

## Task 2: Harden The Send-Message Transaction Path

**Objective:** Ensure message save and conversation last-message update stay tightly coupled and well-tested without changing message API behavior.

**Modify:**

- `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/app/usecase/messaging/MessagingUseCases.java`

**Test:**

- `src/test/java/datingapp/core/ConnectionServiceAtomicityTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiConnectionStorageAtomicityTest.java`
- `src/test/java/datingapp/app/usecase/messaging/MessagingUseCasesTest.java`

**Instructions:**

- Review the current send path behind `RestApiServer.sendMessage(...)`.
- Confirm where message persistence and conversation metadata updates are coupled today.
- Tighten the storage/use-case implementation so partial success is less likely and error handling is explicit.
- Preserve all current observable behavior:
  - same request requirements
  - same success status
  - same `MessageDto`
  - same permission checks
  - same failure semantics
- Keep `conversationId` generation and participant validation unchanged.

**Verification:**

- Run: `mvn --% -q -Dcheckstyle.skip=true -Dtest=ConnectionServiceAtomicityTest,JdbiConnectionStorageAtomicityTest,MessagingUseCasesTest test`
- Then run: `mvn -q spotless:check`

**Done when:**

- Atomicity coverage is stronger.
- No frontend-facing payload or behavior contract changed.

---

## Task 3: Clean Up Notification Payload Assembly And Notification Read Path

**Objective:** Reduce duplication and unnecessary reads in notification internals while preserving every current notification contract detail.

**Modify:**

- `src/main/java/datingapp/app/event/handlers/NotificationEventHandler.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/core/storage/CommunicationStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiConnectionStorage.java`

**Test:**

- `src/test/java/datingapp/app/event/handlers/NotificationEventHandlerTest.java`
- `src/test/java/datingapp/app/usecase/social/SocialUseCasesTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiCommunicationStorageSocialTest.java`
- `src/test/java/datingapp/core/ConnectionServiceTransitionTest.java`

**Instructions:**

- Extract or centralize repeated internal payload assembly for existing keys such as:
  - `matchId`
  - `conversationId`
  - `requestId`
  - `senderId`
  - `accepterUserId`
  - any legacy compatibility keys already emitted
- Do not rename keys, drop keys, or introduce new keys in this task.
- Be especially careful with `FRIEND_REQUEST_ACCEPTED` because current behavior already has compatibility sensitivity.
- Review `markNotificationRead(...)` and reduce redundant storage reads only if the response behavior remains the same.
- Keep unknown or unsupported notification types untouched.
- Keep the current notification enum surface unchanged.

**Verification:**

- Run: `mvn --% -q -Dcheckstyle.skip=true -Dtest=NotificationEventHandlerTest,SocialUseCasesTest,JdbiCommunicationStorageSocialTest,ConnectionServiceTransitionTest test`
- Then run: `mvn -q spotless:check`

**Done when:**

- Existing notification tests pass.
- The serialized notification payload contract is unchanged.
- Internal code is simpler and less repetitive.

---

## Task 4: Optimize Profile Batch Lookup And Normalized-Profile Hydration

**Objective:** Reduce allocation churn and duplicate work in user batch lookup and normalized-profile loading without changing hydrated user semantics.

**Modify:**

- `src/main/java/datingapp/core/profile/ProfileService.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileUseCases.java`
- `src/main/java/datingapp/storage/jdbi/JdbiUserStorage.java`
- `src/main/java/datingapp/storage/jdbi/NormalizedProfileRepository.java`
- `src/main/java/datingapp/storage/jdbi/NormalizedProfileHydrator.java`

**Test:**

- `src/test/java/datingapp/core/ProfileServiceBatchLookupTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiUserStorageNormalizationTest.java`

**Instructions:**

- Trace the `getUsersByIds` and `findByIds` paths.
- Remove duplicated filtering, list/set conversion, and repeated user-id lookups where safe.
- Keep result identity and hydration behavior identical:
  - same user ids
  - same normalized fields
  - same null/empty handling
  - same ordering guarantees where already relied on
- Treat this as a storage/service optimization task, not a contract task.
- Do not modify REST DTO mapping as part of this task.

**Verification:**

- Run: `mvn --% -q -Dcheckstyle.skip=true -Dtest=ProfileServiceBatchLookupTest,ProfileUseCasesTest,JdbiUserStorageNormalizationTest test`
- Then run: `mvn -q spotless:check`

**Done when:**

- Batch lookup is leaner internally.
- The user objects seen by upstream layers remain equivalent.

---

## Task 5: Optimize Matching And Match-Quality Internals

**Objective:** Reduce repeated computation in matching and match-quality paths while preserving current candidate selection and quality outputs.

**Modify:**

- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/matching/CandidateFinder.java`
- `src/main/java/datingapp/core/matching/MatchQualityService.java`

**Test:**

- `src/test/java/datingapp/core/MatchingServiceTest.java`
- `src/test/java/datingapp/core/CandidateFinderTest.java`
- `src/test/java/datingapp/core/MatchQualityServiceTest.java`

**Instructions:**

- Review duplicate work in swipe handling, candidate filtering, distance calculations, and match-quality derivation.
- Optimize only when the following stay unchanged:
  - swipe outcomes
  - rejection and idempotency behavior
  - browse candidate order
  - match-quality score range
  - labels
  - highlight generation rules
  - REST-facing payload field values
- Favor hoisting repeated derived values and reusing per-request calculations.
- Do not change ranking policy or recommendation semantics in this task.

**Verification:**

- Run: `mvn --% -q -Dcheckstyle.skip=true -Dtest=MatchingServiceTest,CandidateFinderTest,MatchQualityServiceTest test`
- Then run: `mvn -q spotless:check`

**Done when:**

- Internal computation is cheaper or clearer.
- Current matching and quality behavior is preserved.

---

## Task 6: Clean Up Local Verification Scripts And Related Docs

**Objective:** Make local backend verification easier to run and diagnose without spending time on new PostgreSQL regression-test coverage.

**Modify:**

- `run_postgresql_smoke.ps1`
- `check_postgresql_runtime_env.ps1`
- `start_local_postgres.ps1`
- `README.md`
- `CI_AND_POSTGRESQL_GUIDE.md`

**Instructions:**

- Improve command discovery and failure messages in the local PowerShell helpers.
- Make the distinction clearer between:
  - PostgreSQL startup failure
  - environment/config failure
  - smoke-test failure
  - Maven invocation failure
- Keep the scripts focused on local verification behavior. Do not add broader workstation policy or unrelated cleanup.
- Update the two Markdown guides so they match the actual script behavior and entrypoints.
- Do not add new PostgreSQL regression-test classes in this task.

**Verification:**

- Run: `.\check_postgresql_runtime_env.ps1`
- Run: `.\run_postgresql_smoke.ps1`
- Confirm the docs describe the same commands and failure modes that the scripts actually use.

**Done when:**

- A backend engineer can diagnose local verification failures faster.
- Documentation and script behavior are aligned.

---

## Out Of Scope For This Plan

- Any contract work that would require frontend coordination
- Any route or DTO expansion
- Any change to notification schema
- Any change to person-summary semantics
- Any change to `profile-edit-snapshot` or `presentation-context` payload shape
- Any work that starts by editing the already-dirty files listed above
- Any new PostgreSQL regression-test slice

---

## Final Verification Before Handoff

- Run the targeted tests for each completed task.
- Run `mvn -q spotless:check` after each task or task bundle.
- Before declaring the overall work complete, run:
  - `mvn spotless:apply verify`
- If a task touched local PostgreSQL helper scripts, also run:
  - `.\run_postgresql_smoke.ps1`

The backend engineer should not claim success based on “internal cleanup only.” The final state still needs evidence that behavior stayed compatible with the protected REST contract.
