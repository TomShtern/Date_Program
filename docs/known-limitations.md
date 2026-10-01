# Known limitations

This project began as a phone-alpha backend for a separate Flutter client. The
points below are things I know are not production-ready. Each one was checked
against the source when this page was written, and the file or class to look at
is named so you can check it yourself.

## Security

- **Photo files are served without auth.** `RestApiServer.registerStaticPhotoRoute`
  serves `GET /photos/<userId>/<file>` with no token check, and the request
  guards only cover paths under `/api/`. Anyone who has a photo URL can fetch
  the image.
- **Verification codes are not delivered.** `POST /api/users/{id}/verification/start`
  returns the generated code in the response as `devVerificationCode`. I found
  no email or SMS sender in `src/main/java`. The flow works for development and
  proves nothing about who owns the address.
- **Identity fallback when auth is not wired.** `RestApiIdentityPolicy.resolveActingUserId`
  trusts a bare `X-User-Id` header when it was built without auth use cases
  (the no-argument constructor). A server built that way has no real caller
  identity, so the fallback should go before any public deployment.
- **Plain HTTP.** The server has no TLS setup in `app/api`, and the LAN scripts
  and docs use `http://`. The LAN shared secret travels in a header over that
  connection, so use it only on a network you trust.
- **Rate limiting lives in one process.** `RestApiRequestGuards.LocalRateLimiter`
  keeps counters in a `ConcurrentHashMap`. They reset on restart and are not
  shared between instances.
- **Placeholder database password in `.env.example`.** The file ships the
  development password `datingapp` for the local helper scripts. Nothing in
  `src/main/java` rejects it, so a deployment that copies the file unchanged
  runs with it.
- **A JWKS outage looks like a bad token.** `ClerkJwtVerifier` logs a warning and
  returns "not verified" when it cannot fetch Clerk's public keys, so clients get
  401 instead of 503. Keys are cached, so only tokens signed by a key the server
  has not seen yet are affected.
- **Deleting an account does not delete the Clerk user.** `softDeleteAccount`
  removes the `clerk_identities` row, but nothing calls Clerk, and there is no
  Clerk webhook. The same Clerk user can sign in again and gets a fresh profile.

## Correctness

- **Provisioning is two writes, not one transaction.** `AuthUseCases.provisionSession`
  saves the user, then inserts the `clerk_identities` row. A per-subject lock
  stops two requests in one process from racing. If a second server process
  wins the insert, the loser re-reads the winner's profile and leaves one unused
  `INCOMPLETE` user row behind. The server runs as one process today.
- **Only Israel is a selectable location.** `LocationService.resolveSelection`
  rejects every other country, even though the dataset lists more.

## Scope

- The Flutter client is not in this repository. `docs/api/API-SPECIFICATION.md`
  covers only the auth and photo routes, so for everything else the route
  registration in `RestApiServer` is the reference.
- The JaCoCo gate (line coverage 0.60) excludes `datingapp/ui/**`,
  `datingapp/app/cli/**` and `datingapp/Main.class`, so the two desktop
  adapters have no coverage requirement.
- Runtime storage is PostgreSQL. H2 and in-memory storage exist for tests and
  are not a supported way to run the app.
