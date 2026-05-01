# Path to a Usable Build — Dating App

Author context: roadmap for moving from "developer-only Flutter shell + local
Java backend + local Postgres" to "the app actually functions and I can use it
on my phone as a real user, not as a developer."

Today: 2026-04-30.

This is not a launch plan. It does not assume Play Store, public testers,
moderation queues, or legal review. It assumes one human (you) wants the app
to work end-to-end on your own phone like a real product would, and possibly
to share it with one or two trusted people. The bar is "functions," not
"shippable."

## 1. The Goal, In One Sentence

The state we want to reach: **I install the app on my phone, sign up with my
real email and password, upload a real photo, set a location, and use Discover
/ Matches / Chats / Profile / Settings end-to-end against a backend that I do
not have to babysit while I'm using it.**

Not in scope for this goal:

- Distribution to strangers.
- Public privacy policy / ToS.
- Content moderation pipeline.
- Push notifications.
- HTTPS-via-purchased-domain (we'll get HTTPS for free a different way).
- iOS.
- Account scaling beyond a handful of test profiles.

If you later want to invite five friends as a closed test, that's a second
stage and there's a smaller add-on roadmap at the end of this document for
it. **Don't do that work now**; it'll slow down getting to the first goal.

## 2. Current State vs Where We Need to Be

### What already works (verified in this repo)

- A polished, design-locked Flutter frontend covering every dating-app
  surface.
- A working API client layer ([lib/api/](lib/api/)) with centralized headers,
  error mapping, and a `selectedUserGuard` pattern that's easy to repurpose
  into a real auth guard.
- Per [AGENTS.md](AGENTS.md): a Java 25 backend on this machine with REST
  endpoints for users, browse, matches, conversations, messages, stats,
  achievements, notifications, blocked users, location, verification, safety.
- Local PostgreSQL.

### What's blocking "I can use it as a user"

In priority order — these are the actual blockers, nothing else.

1. **No real auth.** The only login is `DevUserPickerScreen` listing rows
  from `GET /api/users`. No signup, no password, no token. The `X-User-Id`
  header is client-supplied — anyone can be anyone. You cannot meaningfully
  "use it as a user" because there are no users in the product sense, only
  seeded rows.
2. **The backend is unreachable from your phone unless you're on the same
  wifi as your laptop, and only over plain HTTP.** This is fine for a
  one-off test but breaks the moment you walk out of the house, and Android
  release builds reject cleartext by default.
3. **No photos.** Fixtures use unreachable `/photos/...jpg` URLs (see
  [test/visual_inspection/fixtures/visual_fixture_catalog.dart](test/visual_inspection/fixtures/visual_fixture_catalog.dart)).
  No upload endpoint, no storage path. A dating app you can't put a real
  photo into doesn't function as the product it claims to be.
4. **No onboarding funnel.** The app drops you straight into a populated
  shell because dev mode skips signup → DOB → gender → location → photo. A
  real user has nothing to do because there's no flow that hands them the
  product.
5. **The DB and the backend live in exactly one place — your laptop.** Not
  "ship it" risk; "lose everything if the disk dies tomorrow" risk.

Everything else (push, moderation, legal, observability, store distribution)
is post-goal.

## 3. Decisions to Make Before Starting

Pin these in week zero. They cascade.

| Decision                             | Recommended default                                                            | Reason                                                                                                                                |
|--------------------------------------|--------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| Auth method                          | Email + password, no email verification yet                                    | Cheapest path. Add verification only once it bites.                                                                                   |
| How does my phone reach the backend? | **Cloudflare Tunnel (free)** pointed at `localhost:7070`                       | Gets you a public HTTPS URL like `dating-dev.trycloudflare.com` with zero infra, zero certs, zero cost. The laptop just has to be on. |
| Where do photos live?                | **Local filesystem on the laptop, served as static files by the Java backend** | Object storage (S3/R2) is overkill for personal use. A `photos/` directory + an Nginx-style static handler is enough.                 |
| Where does Postgres live?            | Stay local for now, add `pg_dump` cron                                         | If you and one trusted person are the only users, local Postgres + a nightly dump to your home folder is enough.                      |
| Mobile distribution?                 | **`flutter build apk --release` + sideload**                                   | No Play Store, no $25 fee. Sign with a self-generated keystore, install via USB or share the APK with a trusted person directly.      |
| Backup of the keystore + the DB dump | A second drive or a private cloud folder (Drive / Dropbox / Proton Drive)      | The keystore is the one item you cannot regenerate. Back it up the day you create it.                                                 |
| Backend code under version control?  | Push to a private GitHub/GitLab repo before phase A1                           | Right now the code lives on your laptop only. That's the biggest non-product risk in the project.                                     |

If something pulls you elsewhere, fine — but if you have no opinion, take the
defaults above.

## 4. Roadmap

Five short phases. Each ends in a state where you can stop and still have a
working system. Don't skip a phase even if it looks small — the gates exist
so you don't drift.

### Phase A1 — Real Authentication (lightweight)

**Why first:** every other piece of work depends on the request shape changing
from "shared secret + selected user id" to "Bearer token issued at login." Do
this once, on the right foundation, and everything else is built on top.

**Backend work (Java repo):**

- New tables: `user_credentials` (user_id, password_hash, password_algo,
  created_at, updated_at), `refresh_tokens` (token_hash, user_id, expires_at,
  revoked_at).
- New endpoints: `POST /api/auth/signup`, `POST /api/auth/login`,
  `POST /api/auth/refresh`, `POST /api/auth/logout`. (Skip
  `forgot-password` and `verify-email` for now.)
- Hash passwords with **Argon2id** (preferred) or **BCrypt cost 12**. Never
  plaintext, never SHA/MD5.
- Issue 15-minute JWT access tokens + 30-day opaque refresh tokens stored
  hashed in DB. Rotate refresh tokens on use.
- The existing `X-User-Id` header injection switches from "client supplies it"
  to "server derives it from the JWT." JWT bearer auth is the real identity
  mechanism. The shared-secret header (`X-DatingApp-Shared-Secret`), if kept,
  is only a temporary LAN/dev transport guard and must not be treated as user
  identity.
- Crude rate limit: 5 login attempts / 15 min / IP, 3 signups / hour / IP.
  In-memory bucket is fine for this scale.

**Flutter work (this repo):**

- Replace [lib/features/auth/](lib/features/auth/) dev-user picker with a real
  `LoginScreen` and `SignupScreen`. Hide the dev picker behind a debug-only
  flag so local development still works.
- Add `flutter_secure_storage` (not `shared_preferences`) for the access +
  refresh tokens.
- Add a Dio interceptor that (a) attaches `Authorization: Bearer <access>`,
  (b) on 401 silently refreshes once + retries, (c) on refresh failure boots
  to login.
- Repurpose `selectedUserGuard` → `requireAuthenticated` backed by token
  presence.
- Collect date of birth at signup and reject < 18 client- AND server-side.
  This is the one piece of "legal" you should do even at this stage.

**Done when:**

- I sign up on a fresh build with my real email + a real password, log in on
  the same device, kill and reopen the app, and I'm still logged in.
- The dev-user picker is unreachable in a release build.
- Inspect the DB and verify password hashes are Argon2id/BCrypt.

**Effort:** 2–3 weeks for one solo dev across both repos.

---

### Phase A2 — Make the Backend Reachable from My Phone, Over HTTPS, For Free

**Why now:** with auth in place, "use it as a user" requires the phone to
reach the backend from anywhere, with HTTPS, without buying a server.

**Ops work:**

- Install `cloudflared` on the laptop. Run a quick tunnel:
  `cloudflared tunnel --url http://localhost:7070`. You get a free HTTPS URL
  like `something-something.trycloudflare.com`.
- For a stable URL across restarts, create a named Cloudflare tunnel (still
  free) bound to a Cloudflare-managed subdomain (you'd need a domain on
  Cloudflare DNS for this — defer if you don't have one yet, the random URL
  works for now).
- Run the Java backend behind the tunnel. Put it under a process supervisor
  so it restarts when it crashes — on Windows that's a scheduled task or
  NSSM, on macOS it's `launchd`, on Linux it's `systemd`.
- Adopt a Postgres migration tool (Flyway is simplest). Every schema change
  from now on goes through versioned SQL — no more ad-hoc DDL.

**Flutter work:**

- Bake the tunnel URL as the release default base URL via dart-define:
  `--dart-define=DATING_APP_API_BASE_URL=https://<tunnel-url>`. Keep the LAN
  URL as the debug default.
- In `android/app/src/main/res/xml/network_security_config.xml`, leave
  cleartext allowed only for the debug variant. Release builds should be
  HTTPS-only — Cloudflare Tunnel provides the cert automatically.

**Done when:**

- I disconnect my phone from home wifi, switch to mobile data, and the app
  still loads my profile, browse list, and conversations from the hosted
  endpoint.
- The Java backend survives a laptop reboot without manual intervention
  (within seconds of login, not minutes).

**Effort:** 2–4 days. Most of it is figuring out the supervisor and
Cloudflare Tunnel — both have well-documented step-by-step guides.

---

### Phase A3 — Photo Upload to Local Disk

**Why now:** dating product without photos isn't testable. We're not setting
up S3 — the laptop's filesystem is fine for this scale.

**Backend work:**

- New endpoints: `POST /api/users/{id}/photos` (multipart upload),
  `DELETE /api/users/{id}/photos/{photoId}`,
  `PUT /api/users/{id}/photos/order`.
- Save the file to a configured directory, e.g. `./data/photos/{userId}/{uuid}.jpg`.
- Store metadata in a `photos` table: id, user_id, file_path, sort_index,
  created_at.
- Re-encode every upload to JPEG, generate a thumbnail (~256 px) and a
  display version (~1080 px), strip EXIF (GPS leaks). Java has ImageIO + the
  metadata-extractor library.
- Cap upload size at 5 MB at the framework level. Reject other content types.
- Add a static-file route that serves `/photos/{userId}/{filename}` from the
  same directory, with `Cache-Control: public, max-age=86400`.
- Make sure the photos directory is **outside the JAR / outside the Git
  repo**. Put it in `pg_dump`'s neighbor directory and back them both up
  together.

**Flutter work:**

- Add `image_picker` (camera + gallery) and a small upload UI in the Profile
  edit flow.
- Replace the photo-grid placeholder with real thumbnails returned by the
  upload endpoint.

**Done when:**

- I upload a real photo from my phone, the file lands in the laptop's
  `data/photos/` directory, the URL surfaces in the app on a different
  device's profile view, and EXIF data is gone (verify with `exiftool`).

**Effort:** 1–2 weeks.

---

### Phase A4 — Onboarding Funnel and Removing the Dev Affordance

**Why now:** the app currently teleports you into a populated shell because
dev mode skips the funnel. A real user has to walk through it.

**Flutter + backend work:**

- Define the funnel: Signup → Date of birth → Display name → Gender +
  interested-in → Location (the location endpoints already exist) → At least
  one photo (uses A3) → Bio → Land in Discover.
- Each step writes to the same `users` row; track completion with a
  `profile_completion_state` enum on the backend (`needs_dob`, `needs_name`,
  `needs_gender`, `needs_location`, `needs_photo`, `needs_bio`, `complete`).
- Block access to Discover/Matches/Chats until `complete`. The Profile screen
  is the only one accessible during onboarding; the funnel resumes there.
- Compile-out dev-user switching from release builds. The "Developer only"
  callouts should not be reachable when `kReleaseMode` is true.

**Done when:**

- A brand-new email signs up on the production tunnel, walks the entire
  funnel without any developer affordance visible, and lands in a working
  Discover.

**Effort:** 1 week. Mostly UX wiring; no new infra.

---

### Phase A5 — Account Delete + Block/Report Truth Check

**Why now:** "use it as a user" includes "if I mess up my profile, I can
delete and start over" and "if the block button doesn't actually block, the
product is a lie."

**Backend work:**

- `DELETE /api/users/{id}`: hard-delete photos from the filesystem, hard-
  delete credentials and refresh tokens, soft-delete the profile (mark
  `deleted_at`), anonymize the sender id on past messages so the other party
  doesn't see "deleted user — message was: …" in a way that leaks identity.
- Audit the existing block / report / unmatch endpoints: do they actually
  persist? Do they actually enforce? Are blocked users excluded from browse,
  matches, message delivery, profile views? If "report" is a no-op, fix it
  — even if all "moderation" amounts to is reading the `reports` table once
  a week.

**Flutter work:**

- Add Settings → Delete account with a confirmation dialog.
- Confirm the existing block/unmatch flows hit the real endpoints (not just
  optimistic-only client state).

**Done when:**

- I delete my own account from inside the app, sign back up with the same
  email, and start fresh.
- A user I block stops appearing in browse and stops being able to send me
  messages.

**Effort:** 1 week.

---

## 5. After Phase A5: You Have It

Once A1–A5 are done, the app **functions as a product**. You can use it as a
user. You can hand the APK to one or two trusted people and they can sign up
on their own phone and you can match and chat with them.

This is the goal. Stop here and live with it for a while before deciding to
do more.

## 6. Optional Stage B — If You Later Want a Small Closed Test

Only do this if Stage A has been working for a few weeks and you actually
want to invite a handful of strangers. Don't pre-build it.

| Stage B add-on                                                                        | Why                                                                                             | Approx effort         |
|---------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|-----------------------|
| Move Postgres to a managed host (Supabase / Neon free tier)                           | Backups stop being your job                                                                     | 2–3 days              |
| Move the Java backend to a real host (Hetzner CX22 ~€5/mo, Railway ~$5–10/mo)         | Laptop doesn't have to be on                                                                    | 3–5 days              |
| Move photos to Cloudflare R2 / Backblaze B2                                           | Object storage scales better than laptop disk; ~$0–1/mo                                         | 3–4 days              |
| Buy a domain (~$12/yr), point it at the backend with a stable HTTPS cert              | Tunnel URL stops being a random subdomain                                                       | 1 day                 |
| Privacy policy + ToS + age-18 gate page                                               | Required by Google Play if you go that route, also basic decency for collecting strangers' data | 1 week incl. drafting |
| Email verification on signup (free tier of Resend / Postmark / SES)                   | Reduces fake signups                                                                            | 2–3 days              |
| Basic content moderation on photo upload (Sightengine / AWS Rekognition pay-per-call) | Prevents the obvious worst-case at upload time                                                  | 2–3 days              |
| Sentry on both sides                                                                  | When testers hit a bug, you see the stack trace                                                 | 1–2 days              |
| Google Play developer account ($25 one-time) + closed-track APK upload                | One-click install for testers                                                                   | 1 week first time     |
| Push notifications via FCM                                                            | Drops 20s polling, makes chat feel real                                                         | 1 week                |

Total Stage B: 3–6 weeks of additional work. Total cost: ~$30 first month,
~$10/mo running.

Specifically defer past Stage B (don't even discuss them now): real-time
WebSocket chat, iOS, in-app purchases, recommendation tuning, localization,
web client.

## 7. Cost Sketch

**Stage A (the goal you actually asked for): ~$0/month.**

- Cloudflare Tunnel: free
- Local Postgres: free
- Local file storage: free
- Self-signed Android keystore + sideload: free
- Existing laptop: free

The only "spend" is your time and a backup drive (anything from a USB stick
upward).

**Stage B (later, if you decide): ~$10–30/month + $25 one-time.**

(See the table in §6.)

## 8. Suggested Sequence and Rough Timeline

If you can give this 10–15 hours/week:

| Phase                                                               | Weeks              |
|---------------------------------------------------------------------|--------------------|
| A0. Decide §3 questions, push backend repo to private GitHub/GitLab | 0–1                |
| A1. Real authentication                                             | 2–3                |
| A2. Cloudflare Tunnel + supervisor + Flyway                         | 0.5                |
| A3. Photo upload to local disk                                      | 1–2                |
| A4. Onboarding funnel + remove dev affordance from release          | 1                  |
| A5. Account delete + safety endpoint truth check                    | 1                  |
| **You can use it as a user**                                        | **End ~6–8 weeks** |

If you can only give it 5 hours/week, double the timeline.

## 9. Open Questions to Pin Before Starting

These don't have right answers — they have your answers.

1. **Will anyone besides you test it during Stage A?** If yes, who, by name?
  That changes nothing technically but shapes how seriously you should take
  even the lightweight "ToS / privacy" conversation in A1.
2. **Does your laptop stay on most of the time, or does it sleep when you
  close the lid?** If it sleeps, the tunnel dies and the app stops working.
  You'll either need to change power settings or accept "it's only up when
  I'm around."
3. **Have you ever signed an Android APK before?** If not, plan for half a
  day of friction the first time. Generate the keystore once, back it up
  immediately, never lose it.
4. **Is the backend repo backed up off this laptop right now?** If no, fix
  that *today*, before phase A1. It's a one-hour task and it's the
  single largest single-point-of-failure in the project.
5. **What's your kill criterion?** What outcome would make you stop the
  project? Naming this up front prevents sunk-cost dragging.

## 10. Backend Verification Audit (Out of Scope of This Doc)

This Flutter repo cannot tell you the state of the Java backend's auth, photo,
moderation, or block/report code. Before A1 starts, do a parallel audit on
the backend:

- Inventory which of the endpoints in [AGENTS.md](AGENTS.md)'s `ApiEndpoints`
  list are actually implemented vs stubbed.
- Confirm the `verification` endpoints are wired or are no-ops.
- Confirm block / report / unmatch endpoints actually mutate state and enforce
  side effects (this feeds A5).
- Check for an existing migration tool. If migrations are ad-hoc DDL, A2 must
  include adopting Flyway first.
- Check for hardcoded secrets in source.

The output of that audit is the input to A1's "backend work" list.

---

## Summary

The five things blocking "I can use it as a user," in priority order:

1. **Real auth.** Signup + login + JWT + refresh + secure-storage on the
  client. The dev-user picker has to go from release builds.
2. **Reachable backend over HTTPS.** Cloudflare Tunnel does this for free.
  No domain, no certs, no server purchase needed.
3. **Photo upload to local disk** with EXIF strip + thumbnails. Object
  storage can wait.
4. **Onboarding funnel.** Signup → DOB → gender → location → photo → bio →
  Discover. Block discovery until it's complete.
5. **Account delete + truthful block/report.** So the product behaves like
  a product, not a demo.

That's the whole list. Six to eight weeks of part-time work. About $0 in
cash. After that, you have a working dating app you can use yourself and
share with one or two trusted people.

Stage B (closed test with real strangers) is real work — about another month
and ~$10–30/month — and you should not start it until you've actually used
the Stage A build for a few weeks. Building Stage B before validating with
Stage A is the easy way to spend three months on infrastructure for a product
you haven't tested.