# Phone-Alpha Backend Contract Audit

Date: 2026-04-30

Scope: P0 audit only. This note separates code-implemented status from live deployment or real-phone validation.

## Executive Verdict

The current backend already exposes nearly the full route surface used by the Flutter app today, and the audited route/test slices are passing locally. The backend is still not ready for a phone-test alpha because the missing pieces are foundational rather than cosmetic:

1. Authentication is still developer/header based. There is no real REST signup/login/JWT/refresh/logout flow.
2. Photo support is not mobile-ready. REST read DTOs expose `photoUrls` and derived `primaryPhotoUrl`, but upload/delete/reorder/static serving are not implemented as backend HTTP behavior.
3. Verification is stateful but still a dev-code/manual flow. It is not real SMS or email delivery.
4. Safety, account delete, and migrations are in materially better shape than auth/media. They mostly exist and persist correctly, but a few enforcement seams still need explicit phone-alpha follow-up tests.

Bottom line: keep the current route paths, keep the current migration system, and implement phone-alpha in this order: auth first, media second, then tighten route identity and a few safety semantics.

## Auth Verdict

- Real REST auth is missing. No `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`, or `GET /api/auth/me` route is registered.
- REST identity is currently derived from `X-User-Id`, not `Authorization: Bearer ...`.
- Request guarding is transport/header based: localhost or LAN shared-secret plus scoped `X-User-Id` checks.
- The desktop login model is still user selection plus in-memory session assignment, not credential authentication.

Practical conclusion: login is still dev-style user selection/header identity, not real account auth.

## Endpoint Status Table

Status meanings here are for the current Flutter endpoint list versus code in this repo, not for future phone-alpha auth/media requirements.

| Route                                                       | Status      | Notes                                                                                     |
|-------------------------------------------------------------|-------------|-------------------------------------------------------------------------------------------|
| `GET /api/health`                                           | implemented | Registered and covered in route tests.                                                    |
| `GET /api/users`                                            | implemented | Registered; currently supports dev user-selection style login flows.                      |
| `GET /api/users/{id}`                                       | implemented | Registered; blocked profile-read enforcement exists for other-user reads.                 |
| `PUT /api/users/{id}/profile`                               | implemented | Registered and covered in route tests.                                                    |
| `GET /api/users/{id}/profile-edit-snapshot`                 | implemented | Registered and covered in route tests.                                                    |
| `GET /api/users/{viewerId}/presentation-context/{targetId}` | implemented | Registered and covered in route tests.                                                    |
| `GET /api/users/{id}/browse`                                | implemented | Registered and covered; blocked users are filtered from browse.                           |
| `POST /api/users/{id}/like/{targetId}`                      | implemented | Registered and covered.                                                                   |
| `POST /api/users/{id}/pass/{targetId}`                      | implemented | Registered and covered.                                                                   |
| `POST /api/users/{id}/undo`                                 | implemented | Registered and covered.                                                                   |
| `GET /api/users/{id}/matches`                               | implemented | Registered; active-match list path exists and is exercised in the audited route slice.    |
| `GET /api/users/{id}/match-quality/{matchId}`               | implemented | Registered and covered.                                                                   |
| `GET /api/users/{id}/conversations`                         | implemented | Registered and covered.                                                                   |
| `GET /api/conversations/{conversationId}/messages`          | implemented | Registered and covered; participant checks exist.                                         |
| `POST /api/conversations/{conversationId}/messages`         | implemented | Registered and covered; sender/body identity still relies on `senderId` plus `X-User-Id`. |
| `GET /api/users/{id}/stats`                                 | implemented | Registered and covered.                                                                   |
| `GET /api/users/{id}/achievements`                          | implemented | Registered and covered.                                                                   |
| `GET /api/users/{id}/pending-likers`                        | implemented | Registered and covered.                                                                   |
| `GET /api/users/{id}/standouts`                             | implemented | Registered and covered.                                                                   |
| `GET /api/users/{id}/notifications`                         | implemented | Registered and covered.                                                                   |
| `POST /api/users/{id}/notifications/read-all`               | implemented | Registered and covered.                                                                   |
| `POST /api/users/{id}/notifications/{notificationId}/read`  | implemented | Registered and covered.                                                                   |
| `GET /api/users/{id}/blocked-users`                         | implemented | Registered and covered.                                                                   |
| `POST /api/users/{id}/block/{targetId}`                     | implemented | Registered and covered.                                                                   |
| `DELETE /api/users/{id}/block/{targetId}`                   | implemented | Registered and covered.                                                                   |
| `POST /api/users/{id}/report/{targetId}`                    | implemented | Registered and covered.                                                                   |
| `POST /api/users/{id}/relationships/{targetId}/unmatch`     | implemented | Registered and covered.                                                                   |
| `GET /api/location/countries`                               | implemented | Registered and covered.                                                                   |
| `GET /api/location/cities`                                  | implemented | Registered and covered.                                                                   |
| `POST /api/location/resolve`                                | implemented | Registered and covered.                                                                   |
| `POST /api/users/{id}/verification/start`                   | implemented | Registered and covered, but semantics are dev-code/manual.                                |
| `POST /api/users/{id}/verification/confirm`                 | implemented | Registered and covered, but semantics are dev-code/manual.                                |

Observed extra backend routes not on the current Flutter list: deprecated `GET /api/users/{id}/candidates`, friend-request routes, graceful-exit, conversation archive/delete, message delete, and profile-note routes.

## Missing Or Mismatched Contracts

### P0 blockers

1. Real auth contract is missing.
   - No signup/login/refresh/logout/me REST surface exists.
   - No password hashing/token storage/JWT validation flow exists in the REST layer.
   - Current route identity trusts `X-User-Id`; phone-alpha must move identity to bearer tokens while keeping existing `/api/users/{id}/...` paths.

2. Mobile photo contract is missing.
   - No REST `POST /api/users/{id}/photos`.
   - No REST `DELETE /api/users/{id}/photos/{photoId}`.
   - No REST `PUT /api/users/{id}/photos/order`.
   - No static `/photos/...` serving route.
   - No multipart handling, server-side re-encode, thumbnail generation, or external photo storage root in the REST backend.

3. Verification is not production-style delivery.
   - `startVerification` generates and persists a code.
   - `confirmVerification` validates and marks the user verified.
   - The API intentionally returns `devVerificationCode`, which makes this a developer/manual confirmation flow, not SMS/email delivery.

### Important audited distinctions

1. Existing photo storage does not mean mobile photo upload is done.
   - `LocalPhotoStore` is a desktop-local JavaFX helper writing `file://` URIs under the user home directory.
   - `PhotoMutationCoordinator` persists those URLs through the UI user store.
   - `user_photos` is real backend persistence for ordered photo URLs.
   - `primaryPhotoUrl` is derived from the first real photo URL exposed in REST DTO mapping; it is not a separate stored media object.

2. Safety persistence is real, but enforcement is seam-specific.
   - `block`, `unblock`, and `report` persist through `JdbiTrustSafetyStorage`.
   - `unmatch` persists via relationship transition storage and archives the conversation.
   - Browse/discover enforcement is explicit: blocked users are filtered out.
   - Profile-read enforcement is explicit: blocked viewers get a `403`.
   - Matches enforcement is indirect: block transitions existing matches to `BLOCKED`, and the matches use-case lists only active matches.
   - Chat/message send enforcement is indirect: blocking transitions the match away from messageable state, and sending checks match/messageability. Message reads additionally require conversation participation. I did not inspect a dedicated post-block route test that proves every stored-conversation read case after blocking.

3. Account delete exists and is mostly cleanup-safe.
   - `DELETE /api/users/{id}` is registered and tested.
   - Production wiring uses `JdbiAccountCleanupStorage`, which soft-deletes/hard-deletes the related user graph in one transaction.
   - Noted semantic wrinkle: the use-case mutates the request-local user toward paused/deleted state, while the JDBI row is written as `BANNED` plus `deleted_at`. That is a consistency cleanup item, not a blocker for P0 audit.

4. MigrationRunner should stay.
   - `DatabaseManager` runs `MigrationRunner.runAllPending(...)` at schema initialization.
   - `schema_version` is already the authoritative version ledger.
   - Fresh baseline, idempotence, rollback, and migration-specific tests passed in the audited slice.
   - There is no P0 evidence that Flyway is required. Replacing the current migration system now would add risk without solving the actual alpha blockers.

## Safety Enforcement Summary

| Area                | Result                       | Audit note                                                                                                                                                              |
|---------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Block persistence   | confirmed                    | Stored in `blocks`; unblock is soft-delete aware and revivable.                                                                                                         |
| Report persistence  | confirmed                    | Stored in `reports`; repeat reports are deduplicated/revived.                                                                                                           |
| Unmatch persistence | confirmed                    | Match transitions to `UNMATCHED`; conversation is archived.                                                                                                             |
| Blocked users list  | confirmed                    | REST route returns blocked user summaries.                                                                                                                              |
| Browse/discover     | confirmed                    | Blocked users excluded by candidate filtering and route test coverage.                                                                                                  |
| Profile reads       | confirmed                    | Other-user profile reads reject blocked viewers with `403`.                                                                                                             |
| Matches list        | likely enforced by code path | Existing block transitions match state to `BLOCKED`; active-match listing should drop it. I did not inspect a dedicated route assertion for `GET /matches` after block. |
| Chat/message send   | likely enforced by code path | Blocked matches are no longer messageable; message sends depend on match/messageability.                                                                                |
| Chat/message reads  | partially confirmed          | Participant checks exist; non-messageable reads without a stored conversation are rejected. I did not inspect a dedicated post-block stored-conversation read test.     |

## Recommended Implementation Order

1. Add real auth without changing the existing user-scoped route paths.
   - `POST /api/auth/signup`
   - `POST /api/auth/login`
   - `POST /api/auth/refresh`
   - `POST /api/auth/logout`
   - `GET /api/auth/me`
   - Move identity from `X-User-Id` to bearer token subject enforcement.

2. Add mobile photo upload and serving.
   - Multipart upload endpoint(s), delete/reorder, static serving, and external storage root.
   - Keep existing `photoUrls` / `primaryPhotoUrl` response fields stable.

3. Tighten authenticated route enforcement on the existing REST surface.
   - Require token subject to match `{id}` and sender/author path/body identity.
   - Keep `X-User-Id` only as a migration guard if it matches the token subject.

4. Preserve the current migration stack and add only the migrations needed for auth/media tables.

5. Add explicit safety regression tests for post-block matches/chat behavior.
   - `GET /api/users/{id}/matches` after block
   - `POST /api/conversations/{conversationId}/messages` after block
   - Stored-conversation read behavior after block

6. Keep verification as manual/dev-code for first phone alpha unless you decide it must block account creation.
   - The current flow is good enough for a private alpha if the frontend treats it as manual verification, not real delivery.

7. Clean up delete-state semantics after auth/media land.
   - Align the visible deleted user state between the use-case layer and JDBI row representation.

## Exact Files Inspected

- `2026-04-30-phone-alpha-backend-api-requirements.md`
- `2026-04-30-path-to-first-alpha.md`
- `src/main/java/datingapp/app/api/RestRouteSupport.java`
- `src/main/java/datingapp/app/api/RestApiServer.java`
- `src/main/java/datingapp/app/api/RestApiRequestGuards.java`
- `src/main/java/datingapp/app/api/RestApiIdentityPolicy.java`
- `src/main/java/datingapp/app/api/RestApiDtos.java`
- `src/main/java/datingapp/app/api/RestApiUserDtos.java`
- `src/main/java/datingapp/app/usecase/matching/MatchingUseCases.java`
- `src/main/java/datingapp/app/usecase/social/SocialUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/VerificationUseCases.java`
- `src/main/java/datingapp/app/usecase/profile/ProfileMutationUseCases.java`
- `src/main/java/datingapp/core/AppSession.java`
- `src/main/java/datingapp/core/model/User.java`
- `src/main/java/datingapp/core/matching/TrustSafetyService.java`
- `src/main/java/datingapp/core/matching/MatchingService.java`
- `src/main/java/datingapp/core/matching/CandidateFinder.java`
- `src/main/java/datingapp/core/connection/ConnectionService.java`
- `src/main/java/datingapp/storage/DatabaseManager.java`
- `src/main/java/datingapp/storage/StorageFactory.java`
- `src/main/java/datingapp/storage/schema/MigrationRunner.java`
- `src/main/java/datingapp/storage/jdbi/NormalizedProfileRepository.java`
- `src/main/java/datingapp/storage/jdbi/JdbiTrustSafetyStorage.java`
- `src/main/java/datingapp/storage/jdbi/JdbiAccountCleanupStorage.java`
- `src/main/java/datingapp/ui/LocalPhotoStore.java`
- `src/main/java/datingapp/ui/screen/LoginController.java`
- `src/main/java/datingapp/ui/viewmodel/LoginViewModel.java`
- `src/main/java/datingapp/ui/viewmodel/PhotoMutationCoordinator.java`
- `src/test/java/datingapp/app/api/RestApiIdentityPolicyTest.java`
- `src/test/java/datingapp/app/api/RestApiRequestGuardsTest.java`
- `src/test/java/datingapp/app/api/RestApiReadRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiRelationshipRoutesTest.java`
- `src/test/java/datingapp/app/api/RestApiVerificationRoutesTest.java`
- `src/test/java/datingapp/app/usecase/profile/VerificationUseCasesTest.java`
- `src/test/java/datingapp/app/usecase/profile/ProfileUseCasesTest.java`
- `src/test/java/datingapp/storage/jdbi/JdbiAccountCleanupStorageTest.java`
- `src/test/java/datingapp/storage/schema/SchemaInitializerTest.java`
- `src/test/java/datingapp/ui/LocalPhotoStoreTest.java`

## Verification Commands Run And Results

1. `mvn --% test -Dtest=RestApiIdentityPolicyTest,RestApiRequestGuardsTest,RestApiReadRoutesTest,RestApiRelationshipRoutesTest,RestApiVerificationRoutesTest,VerificationUseCasesTest,LocalPhotoStoreTest,JdbiAccountCleanupStorageTest,SchemaInitializerTest,ConnectionServiceTransitionTest`
   - Result: PASS
   - Summary: 80 tests run, 0 failures, 0 errors, 0 skipped
   - Time: 19.201 s

2. `mvn --% test -Dtest=MigrationRunnerTransactionTest,MigrationRunnerMetadataTest,JdbiUserStorageMigrationTest`
   - Result: PASS
   - Summary: 7 tests run, 0 failures, 0 errors, 0 skipped
   - Time: 13.247 s

## Final P0 Conclusion

Do not start with broad route rewrites or Flyway migration work.

The existing backend already gives you a solid base for phone alpha on routes, safety persistence, account cleanup, and schema evolution. The real missing work is concentrated in three places: real auth, real media handling, and token-based identity enforcement across the already-existing route surface.