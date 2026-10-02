<#
.SYNOPSIS
    Publishes the local REST API through Tailscale Funnel on a stable HTTPS URL.

.DESCRIPTION
    Runs `tailscale funnel --bg --https=443 http://127.0.0.1:<Port>` and then reads
    `tailscale funnel status` back to confirm the public URL. Funnel terminates TLS with a
    publicly trusted certificate, so the URL has no port number and needs no domain purchase.

    --bg stores the configuration in the Tailscale service, which resumes it after a reboot or a
    `tailscale down` / `tailscale up` cycle. Re-running this script is safe and idempotent.

    Prerequisites that need the Tailscale admin console (done once by hand): HTTPS certificates,
    MagicDNS, and the `funnel` node attribute. See docs/guides/public-funnel-runbook.md, section A.
    Official reference: https://tailscale.com/kb/1223/funnel

    The REST server must stay bound to 0.0.0.0 with its shared secret. Funnel connects from this
    machine, so a loopback bind would disable the secret check.

.PARAMETER Port
    Local REST API port. Default: 7070.

.PARAMETER PublicUrl
    Expected public URL (https://<machine>.<tailnet>.ts.net). When given, the script fails unless
    `tailscale funnel status` reports exactly that host. When omitted, the URL is read from the
    status output and printed.

.PARAMETER TailscalePath
    Path to tailscale.exe. Default: C:\Program Files\Tailscale\tailscale.exe, then PATH.

.PARAMETER CommandTimeoutSeconds
    Upper bound for each tailscale command. `tailscale funnel` waits for browser approval when
    Funnel is not yet allowed for the tailnet; the timeout turns that into a clear failure.
#>
param(
    [int]$Port = 7070,
    [string]$PublicUrl,
    [string]$TailscalePath,
    [int]$CommandTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Resolve-TailscaleExe {
    param([string]$Explicit)

    if ($Explicit) {
        if (-not (Test-Path -LiteralPath $Explicit)) {
            throw "[TAILSCALE] $Explicit not found."
        }
        return $Explicit
    }
    $default = Join-Path $env:ProgramFiles 'Tailscale\tailscale.exe'
    if (Test-Path -LiteralPath $default) {
        return $default
    }
    $onPath = Get-Command 'tailscale' -ErrorAction SilentlyContinue
    if ($onPath) {
        return $onPath.Source
    }
    throw '[TAILSCALE] tailscale.exe not found. Pass -TailscalePath.'
}

function Invoke-Tailscale {
    param(
        [string]$Exe,
        [string[]]$Arguments,
        [int]$TimeoutSeconds
    )

    $outFile = [System.IO.Path]::GetTempFileName()
    $errFile = [System.IO.Path]::GetTempFileName()
    try {
        $proc = Start-Process -FilePath $Exe -ArgumentList $Arguments -PassThru -NoNewWindow `
            -RedirectStandardOutput $outFile -RedirectStandardError $errFile
        $finished = $proc.WaitForExit($TimeoutSeconds * 1000)
        if ($finished) {
            $proc.WaitForExit()
        } else {
            Stop-Process -InputObject $proc -Force -ErrorAction SilentlyContinue
        }
        $text = ((Get-Content -LiteralPath $outFile -Raw), (Get-Content -LiteralPath $errFile -Raw) |
            Where-Object { $_ }) -join [Environment]::NewLine
        return [pscustomobject]@{
            TimedOut = -not $finished
            ExitCode = if ($finished) { $proc.ExitCode } else { $null }
            Output   = "$text".Trim()
        }
    } finally {
        Remove-Item -LiteralPath $outFile, $errFile -Force -ErrorAction SilentlyContinue
    }
}

$exe = Resolve-TailscaleExe -Explicit $TailscalePath
Write-Output "[TAILSCALE] Using $exe"

$expectedHost = $null
if (-not [string]::IsNullOrWhiteSpace($PublicUrl)) {
    $trimmedUrl = $PublicUrl.Trim().TrimEnd('/')
    if ($trimmedUrl -notmatch '^https://([A-Za-z0-9.-]+\.ts\.net)$') {
        throw "[CONFIG] -PublicUrl must look like https://<machine>.<tailnet>.ts.net with no port and no path. Got: $trimmedUrl"
    }
    $expectedHost = $Matches[1].ToLowerInvariant()
}

# 1. The node must be online before Funnel can be configured.
$status = Invoke-Tailscale -Exe $exe -Arguments @('status') -TimeoutSeconds 30
Write-Output $status.Output
if ($status.TimedOut -or $status.ExitCode -ne 0) {
    throw "[TAILSCALE] 'tailscale status' did not succeed (exit $($status.ExitCode)). Bring the node online with: & '$exe' up"
}

# 2. Configure Funnel (documented ports: 443, 8443, 10000; 443 means the URL needs no port).
Write-Output "[FUNNEL] Publishing http://127.0.0.1:$Port on public HTTPS port 443 ..."
$funnel = Invoke-Tailscale -Exe $exe -Arguments @('funnel', '--bg', '--https=443', "http://127.0.0.1:$Port") -TimeoutSeconds $CommandTimeoutSeconds
if ($funnel.Output) {
    Write-Output $funnel.Output
}
if ($funnel.TimedOut) {
    throw "[FUNNEL] 'tailscale funnel' did not finish within ${CommandTimeoutSeconds}s. It is probably waiting for Funnel to be approved for the tailnet: complete section A of docs/guides/public-funnel-runbook.md (HTTPS certificates, MagicDNS, 'Add Funnel to policy'), then re-run."
}
if ($funnel.ExitCode -ne 0) {
    throw "[FUNNEL] 'tailscale funnel' failed with exit code $($funnel.ExitCode)."
}

# 3. Read the result back instead of assuming it.
$funnelStatus = Invoke-Tailscale -Exe $exe -Arguments @('funnel', 'status') -TimeoutSeconds 30
Write-Output $funnelStatus.Output
if ($funnelStatus.TimedOut -or $funnelStatus.ExitCode -ne 0) {
    throw "[FUNNEL] 'tailscale funnel status' failed (exit $($funnelStatus.ExitCode))."
}

$foundHosts = @([regex]::Matches($funnelStatus.Output, 'https://([A-Za-z0-9.-]+\.ts\.net)') |
    ForEach-Object { $_.Groups[1].Value.ToLowerInvariant() } |
    Select-Object -Unique)
if ($foundHosts.Count -eq 0) {
    throw "[FUNNEL] 'tailscale funnel status' printed no https://*.ts.net URL. Funnel is not active; re-check section A of the runbook."
}

if ($expectedHost) {
    if ($foundHosts -notcontains $expectedHost) {
        throw "[FUNNEL] Expected host $expectedHost but funnel status reports: $($foundHosts -join ', '). The URL is compiled into the app, so do not continue until this matches (see 'Names you must never change' in the runbook)."
    }
    $resolvedHost = $expectedHost
} elseif ($foundHosts.Count -eq 1) {
    $resolvedHost = $foundHosts[0]
} else {
    throw "[FUNNEL] More than one funnel URL reported ($($foundHosts -join ', ')). Re-run with -PublicUrl to say which one the app uses."
}

if ($funnelStatus.Output -notmatch [regex]::Escape("127.0.0.1:$Port")) {
    Write-Warning "[FUNNEL] funnel status does not mention 127.0.0.1:$Port. Check that the proxy target is the REST API port."
}

Write-Output "[FUNNEL] Public URL is active in Tailscale. Public DNS can take up to 10 minutes to appear on a first setup."
Write-Output "PUBLIC_URL=https://$resolvedHost"
