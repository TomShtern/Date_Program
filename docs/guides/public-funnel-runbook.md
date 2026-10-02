# Public HTTPS backend via Tailscale Funnel (runbook)

Goal: a stable, publicly trusted `https://` URL for the REST backend, reachable from phones on
mobile data, with no domain purchase and no payment. The URL is compiled into the Flutter
release build, so it must not change across restarts or reboots.

How it works: the backend keeps running on this Windows laptop, bound to `0.0.0.0:7070` with
the mandatory shared secret. Tailscale Funnel accepts public HTTPS on port 443, terminates TLS
with a publicly trusted certificate, and forwards plain HTTP to `http://127.0.0.1:7070`.

> **Never bind the server to loopback behind Funnel.** A loopback bind sets
> `restrictToLoopbackClients`, which passes a null secret to the guards
> (`RestApiServer.java:249,256`). The localhost guard trusts the socket peer
> (`RestApiRequestGuards.java:70-75`), and Funnel connects from `127.0.0.1`, so internet
> requests would then pass with no shared-secret check. All scripts here keep `--host=0.0.0.0`.

Two documents are in play: this runbook for the laptop, and
[`lan-backend-startup.md`](lan-backend-startup.md) for the LAN-only path.

## The URL

```
https://<machine>.<tailnet>.ts.net
```

For this laptop the machine part is `tomslaptop`, so the URL is
`https://tomslaptop.<tailnet>.ts.net`, where `<tailnet>` is the tailnet DNS name shown in the
Tailscale admin console under **DNS** (either `tail` + hex digits, or two random words).
There is **no port number**: Funnel serves on port 443.
`scripts\setup_tailscale_funnel.ps1` prints the exact value as `PUBLIC_URL=...`. Record it once
and use that exact string everywhere.

### Names you must never change

| Part | Never do this | Why |
|---|---|---|
| `tomslaptop` (machine name) | Rename the machine in the admin console; rename Windows if "Auto-generate from OS hostname" is still on; delete the machine and re-add it; run `tailscale logout` and sign in as a new node | The machine name is the URL. A name collision or re-registration produces `tomslaptop-1`, and the old name stops working. ([machine names](https://tailscale.com/kb/1098/machine-names)) |
| `<tailnet>.ts.net` (tailnet DNS name) | Use "generate randomized name" or switch between the default and randomized name | Tailscale warns that switching "may break existing links to devices in your tailnet". ([tailnet name](https://tailscale.com/kb/1217/tailnet-name)) |
| Port 443 | Move Funnel to 8443 or 10000 | Anything but 443 puts `:8443` / `:10000` in the URL. |
| HTTPS certificates / MagicDNS | Turn them off in the admin console | Funnel needs both. |

Section A pins the machine name and turns off key expiry, so the URL survives an OS rename and
the 180-day default node-key expiry.

## A. My GUI steps (browser and Windows tray only)

Do these once, in this order, before the laptop agent runs section B.

1. **Admin console, DNS page** (<https://console.tailscale.com/admin/dns>)
   1. Confirm **MagicDNS** is enabled.
   2. Under **HTTPS Certificates**, click **Enable HTTPS** and acknowledge the notice. Tailscale
      publishes the machine name and tailnet name in public certificate-transparency logs, so
      `tomslaptop.<tailnet>.ts.net` becomes publicly visible. Nothing else is published.
2. **Admin console, Access controls page** (<https://console.tailscale.com/admin/acls>): expand the
   **Funnel** section and click **Add Funnel to policy**, then save. This adds a `nodeAttrs`
   entry with `"attr": ["funnel"]` for `autogroup:member`.
3. **Admin console, Machines page:** open the menu at the right of the `tomslaptop` row.
   1. **Edit machine name**: untick **Auto-generate from OS hostname**, keep the name `tomslaptop`, save.
   2. **Disable Key Expiry** (the menu entry of that name). The default expiry is 180 days, after
      which the node drops off the tailnet and the URL goes dead.
4. **Windows tray:** right-click the Tailscale icon, **Preferences**, tick **Run unattended**.
   (Section B step B1 sets the same thing from the CLI; do the tray click only if that command fails.)
5. If `tailscale up` in step B1 prints a login URL, open it in a browser and approve this device.
6. **At the end, on the phone** (after section B passes):
   1. Turn Wi-Fi off. In the phone's browser open `<URL>/api/health`. Expect a small JSON body with `"status":"ok"`.
   2. Install the release build built with the values from "Build the app" below, sign in with Clerk,
      and tell the agent when you are signed in so it can run check B-9.

Official references: [Funnel](https://tailscale.com/kb/1223/funnel),
[enabling HTTPS](https://tailscale.com/kb/1153/enabling-https),
[key expiry](https://tailscale.com/kb/1028/key-expiry),
[unattended mode](https://tailscale.com/kb/1088/run-unattended).

### Build the app

The Flutter app reads `DATING_APP_API_BASE_URL` and `DATING_APP_SHARED_SECRET`. The secret is
read from its file into a variable, so it is never displayed:

```powershell
$secret = (Get-Content -LiteralPath "$env:LOCALAPPDATA\DateProgram\rest-shared-secret.txt" -Raw).Trim()
flutter build apk --release `
  --dart-define=DATING_APP_API_BASE_URL=https://tomslaptop.<tailnet>.ts.net `
  --dart-define=DATING_APP_SHARED_SECRET=$secret
```

Never paste the secret into chat, a file in the repo, a log, or a screenshot. The secret lives in
one place: `%LOCALAPPDATA%\DateProgram\rest-shared-secret.txt`.

## B. Laptop agent commands

Run in PowerShell 7 from the repository root (`cd` to the checkout; the scripts also `Set-Location`
there themselves). Do not print, echo or log the shared secret at any step. In the commands below
`$ts` is the Tailscale CLI:

```powershell
$ts = 'C:\Program Files\Tailscale\tailscale.exe'
```

"Expect" lines show the shape of the output. Tailscale's exact wording can differ by version
(1.102.4 is installed); the pass/fail condition is stated in each step. If a step fails, stop and
report the full output of that step (it contains no secret).

### B0. Prerequisites

```powershell
git status --short        # expect: clean (do not edit .env)
& $ts version             # expect: first line 1.102.4 (any version is fine if the later steps pass)
java -version             # expect: 25
mvn -v                    # expect: a Maven version and Java 25
Get-Command pwsh, pg_ctl, pg_isready, psql | Select-Object Name   # expect: four names, no error
Select-String -Path .env -Pattern '^DATING_APP_AUTH_CLERK_ISSUER=.+' -Quiet   # expect: True
```

The last line checks only that an issuer value exists (it does not print it). Keep it unchanged.
It must be the Clerk **dev** instance (publishable key `pk_test_...`). Leave
`DATING_APP_AUTH_CLERK_AUTHORIZED_PARTIES` unset.

### B1. Bring the Tailscale node online

```powershell
& $ts up
& $ts set --unattended=true
& $ts status
```

Expect: `tailscale up` returns (a login URL means section A step 5). `tailscale status` lists
`tomslaptop` with its address `100.127.155.15` and no `offline` marker for this machine.
**Fail** if this machine is shown offline or stopped; do not continue.

### B2. Start PostgreSQL (directly, not via the preflight)

```powershell
.\scripts\start_local_postgres.ps1
Test-NetConnection localhost -Port 55432 | Select-Object TcpTestSucceeded
```

Expect: `pg_isready` output `localhost:55432 - accepting connections`, then `TcpTestSucceeded : True`.
**Fail** if `False`. The preflight in `start_phone_alpha_backend.ps1` can report ready while
nothing listens, which is why this step does not rely on it.

### B3. Create the shared secret (once)

```powershell
.\scripts\new_rest_shared_secret.ps1
icacls "$env:LOCALAPPDATA\DateProgram\rest-shared-secret.txt"
.\scripts\new_rest_shared_secret.ps1     # second run must refuse
```

Expect: `[SECRET] Created ... The value was not printed.`; `icacls` lists exactly one account (the
current user) with `(F)` and no `Users`, `Everyone` or `Authenticated Users`; the second run
fails with `already exists; refusing to overwrite it`. If the file already existed before this
step, the first command fails the same way, which is correct: keep it.
**Fail** if `icacls` lists any other account. Never run `Get-Content` on the file without
assigning the result to a variable.

### B4. Publish through Funnel and record the URL

```powershell
$out = .\scripts\setup_tailscale_funnel.ps1
$out
$publicUrl = (($out | Select-String '^PUBLIC_URL=(\S+)$').Matches[0].Groups[1].Value)
$publicUrl
& $ts funnel status
```

Expect: the script prints the `tailscale funnel` result, the `funnel status` text, and finally
`PUBLIC_URL=https://tomslaptop.<tailnet>.ts.net`. `funnel status` shows that URL as Funnel on, proxying
`http://127.0.0.1:7070`.

**Fail and stop** if:
- the script says it timed out: Funnel is not approved for the tailnet, redo section A steps 1 and 2;
- `$publicUrl` is `https://tomslaptop-1....` or any machine name other than `tomslaptop`: the machine was
  renamed or re-registered. Do not build an app against it; report it;
- the URL contains a port.

Public DNS for a first-time setup can take up to 10 minutes (Tailscale docs), so B7 may need a wait.

### B5. Verify the real-client-IP header (decides rate limiting)

Tailscale's published documentation does not name the header Funnel uses to pass the client
address. Its open-source code (`ipn/ipnlocal/serve.go`) sets `X-Forwarded-For` to the original
client address, replacing any value the client sent, and adds `Tailscale-Funnel-Request: ?1`.
That is source code, not documentation, so verify on the installed version. Use a spare Funnel
port; never the backend's.

```powershell
# Terminal 1: throwaway listener (prints headers, redacts secrets)
.\scripts\probe_funnel_headers.ps1 -MaxRequests 1

# Terminal 2:
& $ts funnel --bg --https=8443 http://127.0.0.1:7071
# From a device NOT on this tailnet or this Wi-Fi (the phone on mobile data, or any external host):
#   curl -s -H "X-Forwarded-For: 203.0.113.99" https://tomslaptop.<tailnet>.ts.net:8443/probe
# Then clean up:
& $ts funnel --bg --https=8443 http://127.0.0.1:7071 off
& $ts funnel status        # expect: only the port-443 entry remains
```

Expect in terminal 1: `socket peer: 127.0.0.1`, `Tailscale-Funnel-Request: ?1`, and an
`X-Forwarded-For:` line holding the sender's real public address, **not** `203.0.113.99`.

- If that holds: the header is `X-Forwarded-For`. Use `-ClientIpHeader X-Forwarded-For` in B6 and B8.
- If the header is missing, equals `203.0.113.99`, or the listener never gets a request: leave
  `ClientIpHeader` unset. Rate limiting then keeps one shared bucket (240 requests/minute per
  method), which is acceptable while only the owner uses the app. Report what was seen.

If you only have the laptop, the probe still shows the header names, but a request from the
laptop to its own Funnel name may travel over the tailnet instead of the public path, so
treat the `X-Forwarded-For` value as unverified in that case and say so.

### B6. Start the backend (the one start command)

```powershell
$hdr = $null            # set to 'X-Forwarded-For' only if B5 confirmed it
$startArgs = @('-NoProfile', '-File', (Resolve-Path .\scripts\start_public_backend.ps1).Path, '-PublicUrl', $publicUrl)
if ($hdr) { $startArgs += @('-ClientIpHeader', $hdr) }
Start-Process pwsh -WindowStyle Minimized -ArgumentList $startArgs
```

The same command typed in a normal terminal, blocking, is the documented start path:

```powershell
.\scripts\start_public_backend.ps1 -PublicUrl https://tomslaptop.<tailnet>.ts.net [-ClientIpHeader X-Forwarded-For]
```

It starts PostgreSQL, applies the Funnel configuration (idempotent, retried), then runs the
backend. Logs: `%LOCALAPPDATA%\DateProgram\logs\boot-*.log` (script transcript),
`backend.out.log` and `backend.err.log` (server).

```powershell
Get-Content "$env:LOCALAPPDATA\DateProgram\logs\boot-*.log" -Tail 40
```

Expect `[VERIFY] localhost health OK (200).`, `[VERIFY] Public HTTPS health OK (200).` (or a warning
that DNS is still propagating; retry B7 after a few minutes), and the banner
`Phone-alpha backend is ready!` with `--dart-define=DATING_APP_API_BASE_URL=https://...`.
`backend.out.log` contains `REST API server started on 0.0.0.0:7070 with LAN shared-secret
protection` (it must say `0.0.0.0`). **Fail** if it says `localhost-only`.

### B7. Acceptance checks

```powershell
$u = $publicUrl
$f = "$env:LOCALAPPDATA\DateProgram\rest-shared-secret.txt"
$s = (Get-Content -LiteralPath $f -Raw).Trim()      # held in a variable, never printed
function Code { param([string[]]$CurlArgs) (& curl.exe -s -o NUL -w '%{http_code}' @CurlArgs | Out-String).Trim() }
```

1. **Public health, 200.** This must go over the public path. From the phone on mobile data, open
   `<url>/api/health` (section A step 6). From the laptop, a request to its own Funnel hostname can
   resolve to the tailnet address through MagicDNS and then does not prove public reachability, so
   force the public address instead:
   ```powershell
   $hostName = ([uri]$u).Host
   $publicIp = (Resolve-DnsName $hostName -Server 1.1.1.1 -Type A).IPAddress | Select-Object -First 1
   $publicIp                                   # expect: a public address, NOT 100.x.x.x
   Code '--resolve', "${hostName}:443:$publicIp", "$u/api/health"    # expect: 200
   ```
   **Fail** if `$publicIp` starts with `100.` or if the code is not `200`.
2. **No secret, 403** (expect `403` for each):
   ```powershell
   Code "$u/api/users"
   Code "$u/api/location/countries"
   Code '-X','POST',"$u/api/auth/session"
   ```
3. **Secret, no Clerk token, 401** on user routes (expect `401` for each):
   ```powershell
   $h = "X-DatingApp-Shared-Secret: $s"
   Code '-H',$h,"$u/api/users"
   Code '-H',$h,"$u/api/users/00000000-0000-0000-0000-000000000001"
   Code '-H',$h,'-X','POST',"$u/api/auth/session"
   ```
4. **Secret, no Clerk token, 401 on `/api/location/*`** (new in this change; expect `401` for each):
   ```powershell
   Code '-H',$h,"$u/api/location/countries"
   Code '-H',$h,"$u/api/location/cities?countryCode=IL&query=tel"
   Code '-H',$h,'-H','Content-Type: application/json','-X','POST','-d','{"countryCode":"IL"}',"$u/api/location/resolve"
   ```
5. **Health needs neither** (expect `200`): `Code "$u/api/health"`.
6. **Photo URLs are `https://`.** Needs a real Clerk token and an existing user with a profile that
   can take a photo. Follow "Trying a real session token without a client" in
   [`lan-backend-startup.md`](lan-backend-startup.md) (Clerk CLI on the **dev** instance; keep the JWT in a
   variable, delete the test user afterwards), then:
   ```powershell
   # $jwt = <session token>  (variable only, never printed)
   $me = Invoke-RestMethod -Method Post "$u/api/auth/session" -Headers @{ 'X-DatingApp-Shared-Secret' = $s; Authorization = "Bearer $jwt" }
   # any small image file; the form field must be named "photo"
   $r = curl.exe -s -H $h -H "Authorization: Bearer $jwt" -F "photo=@C:\path\to\small.jpg;type=image/jpeg" "$u/api/users/$($me.id)/photos" | ConvertFrom-Json
   $r.photo.url          # expect: starts with https://tomslaptop.<tailnet>.ts.net/photos/
   ```
   (An upload can be rejected for a user whose state is not photo-eligible; the response then
   says so. If so, report it. Photo URLs also appear in `GET /api/users/{id}` responses.) Also
   fetch the photo without any secret: `Code $r.photo.url` should print `200`, because
   `/photos/*` is intentionally a capability link (random UUID file name).
7. **Rate limiting keys on the real client IP** (only if a header was configured in B6):
   `backend.out.log` contains `Rate limiting keys on the X-Forwarded-For header when the request arrives from a loopback peer`.

### B8. Auto-start

```powershell
.\scripts\install_public_backend_autostart.ps1 -PublicUrl $publicUrl [-ClientIpHeader X-Forwarded-For] -StartNow
Get-ScheduledTask -TaskName 'DateProgram Public Backend' | Select-Object TaskName, State
Get-ScheduledTaskInfo -TaskName 'DateProgram Public Backend' | Select-Object LastRunTime, LastTaskResult
```

Expect: `[TASK] Registered 'DateProgram Public Backend' to run at logon of the current user.`,
state `Running`, `LastTaskResult` `267009` (task is currently running). Stop any manual instance
from B6 first (close its window, or `Get-NetTCPConnection -LocalPort 7070 -State Listen` shows
nothing before this step); only one server can hold port 7070.

Default trigger is **at logon of the current user**, with the user's own environment, so it
behaves like running the command by hand. For start **before anyone signs in**, run an elevated
PowerShell and add `-AtBoot` (runs as the same user without a stored password, S4U). That mode
is unverified on this machine; the reboot test below, done without signing in, is what verifies it.
If it does not come back, fall back to the default trigger and enable Windows auto sign-in or
sign in after a reboot.

To remove: `.\scripts\install_public_backend_autostart.ps1 -Remove`.

### B9. Real Clerk sign-in reaches `POST /api/auth/session`

After the phone owner reports they signed in on the release build:

```powershell
$env:PGPASSWORD = '<DATING_APP_DB_PASSWORD from .env, typed by hand; do not echo it>'
psql "host=localhost port=55432 dbname=datingapp user=datingapp" -w -X -tAc "SELECT count(*) FROM clerk_identities"
Remove-Item Env:PGPASSWORD
```

Expect a count of at least 1 more than before the phone signed in (a new `clerk_identities`
row is created by `POST /api/auth/session`). If your table name differs, check
`src/main/java/datingapp/storage` for `clerk_identities`. **Fail** if the count does not change;
then read `backend.out.log` for `auth.expired` / `azp not authorized` lines (an `azp` rejection
would mean `AUTHORIZED_PARTIES` got set; leave it unset).

### B10. Reboot test

1. Note `$publicUrl` and run `Restart-Computer`.
2. After the reboot, **only if the task uses the default logon trigger**, sign in to Windows. Do not run any
   script by hand. Wait about 4 minutes.
3. Then (expect in order):
   ```powershell
   & $ts status                                    # node online
   & $ts funnel status                              # the same URL string as before the reboot
   Get-ScheduledTaskInfo -TaskName 'DateProgram Public Backend' | Select-Object LastTaskResult   # 267009
   $hostName = ([uri]$publicUrl).Host
   $publicIp = (Resolve-DnsName $hostName -Server 1.1.1.1 -Type A).IPAddress | Select-Object -First 1
   curl.exe -s -o NUL -w '%{http_code}' --resolve "${hostName}:443:$publicIp" "$publicUrl/api/health"   # 200
   ```
   And from the phone on mobile data: `<url>/api/health` returns `ok`.
4. **Pass** only if the URL is byte-for-byte identical and health is 200 without manual commands.
   For an `-AtBoot` task, repeat once *without* signing in (check from the phone) to prove it.

Also run once: `& $ts down; & $ts up; & $ts funnel status`. The Tailscale docs say Funnel started
with `--bg` resumes after `tailscale down` / `up` and after a reboot; the reboot test above
is what proves that on Windows with the service installed.

## C. What each claim rests on

| Claim | Source | Status |
|---|---|---|
| Funnel listens only on ports 443, 8443, 10000; URL needs no port on 443 | [Funnel](https://tailscale.com/kb/1223/funnel), [CLI](https://tailscale.com/docs/reference/tailscale-cli/funnel) | Documented |
| Requires HTTPS certificates, MagicDNS, and the `funnel` node attribute | [Funnel](https://tailscale.com/kb/1223/funnel) | Documented |
| Menu names: DNS page, Enable HTTPS; Access controls, Funnel section, Add Funnel to policy | [HTTPS](https://tailscale.com/kb/1153/enabling-https), [Funnel](https://tailscale.com/kb/1223/funnel) | Documented |
| Funnel only uses DNS names in the tailnet domain; TLS only | [Funnel](https://tailscale.com/kb/1223/funnel) | Documented |
| `--bg` makes the config persistent and it resumes after a reboot or `down`/`up` | [CLI](https://tailscale.com/docs/reference/tailscale-cli/funnel), [Funnel CLI](https://tailscale.com/kb/1311/tailscale-funnel) | Documented; **Windows service behavior verified only by B10** |
| Proxy target must be `http://127.0.0.1` | [Funnel CLI](https://tailscale.com/kb/1311/tailscale-funnel) | Documented |
| Free plan: Funnel is available on all plans; traffic has "non-configurable bandwidth limits" | [Funnel](https://tailscale.com/kb/1223/funnel) | Documented; **no numeric bandwidth or request-rate limit is published on the pages checked** |
| Public DNS can take up to 10 minutes; Let's Encrypt rate limits apply to frequent re-issue | [Funnel](https://tailscale.com/kb/1223/funnel) | Documented |
| Machine name is the URL and follows the OS hostname unless "Auto-generate" is unticked | [Machine names](https://tailscale.com/kb/1098/machine-names) | Documented |
| Switching the tailnet DNS name can break links | [Tailnet name](https://tailscale.com/kb/1217/tailnet-name) | Documented |
| Default key expiry is 180 days; "Disable Key Expiry" is on the Machines page, all plans | [Key expiry](https://tailscale.com/kb/1028/key-expiry) | Documented |
| Run unattended: tray, Preferences, Run unattended / `tailscale set --unattended=true` | [Unattended](https://tailscale.com/kb/1088/run-unattended) | Documented |
| Funnel traffic carries no Tailscale identity headers | [Serve](https://tailscale.com/kb/1312/serve) | Documented |
| Funnel sets `X-Forwarded-For` to the real client address (replacing client-sent values) | Tailscale source, `ipn/ipnlocal/serve.go` | **Not in the docs.** Verified by B5 before use |
| `tailscale funnel status` prints the `https://<host>.ts.net` URL | Assumed output shape | The scripts fail closed if no such URL is found; B4 shows it |
| S4U scheduled task at boot has the user's PATH and profile | Not verified | B10 without signing in |

## D. Limits and trade-offs

- The laptop must be on, awake and online. Sleep, a closed lid or a Wi-Fi drop takes the URL down; it
  comes back by itself when the machine does. Set the power plan to not sleep on AC.
- Funnel has bandwidth limits you cannot configure. Photo-heavy traffic from many testers is the
  likely way to hit them; move to a VPS then (the URL would change, which means an app update).
- `/photos/*` stays unauthenticated by design: anyone holding a photo URL can fetch it.
  File names are random UUIDs, so the URLs are not guessable.
- PostgreSQL listens on `localhost` only and is never published through Funnel. Its default
  development password is the repository default; do not expose port 55432.
- Rate limiting: without a verified client-IP header all internet clients share one bucket per
  method (240 requests/minute). With the header (B5), each client is limited separately; the
  header is honored **only** when the connection comes from loopback, so a LAN client sending its
  own `X-Forwarded-For` cannot pick its bucket (`RestApiRequestGuards.clientIp`, tested in
  `RestApiRequestGuardsTest`).
- To take the backend off the internet: `& $ts funnel reset` (clears the whole Funnel
  configuration), `.\scripts\install_public_backend_autostart.ps1 -Remove`, and stop the server.
- If `PUBLIC_URL` ever changes, every installed build needs an update. That is why section
  "Names you must never change" exists.
