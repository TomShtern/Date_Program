<#
.SYNOPSIS
    Prints the request headers a Tailscale Funnel connection delivers to a local backend, so the
    real-client-IP header can be verified instead of assumed.

.DESCRIPTION
    Starts a throwaway listener on 127.0.0.1:<Port> (default 7071), answers every request with
    "ok", and prints each request's socket peer and headers. Secret-bearing headers
    (Authorization, Cookie, X-DatingApp-Shared-Secret) are redacted. Stops after -MaxRequests
    requests or -TimeoutSeconds, whichever comes first.

    Use it on a spare Funnel port, never on the port the real backend uses. The steps are in
    docs/guides/public-funnel-runbook.md, section B, "Verify the client IP header".
#>
param(
    [int]$Port = 7071,
    [int]$MaxRequests = 3,
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$redacted = @('authorization', 'cookie', 'x-datingapp-shared-secret')
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Output "[PROBE] Listening on 127.0.0.1:$Port for up to $MaxRequests request(s) / ${TimeoutSeconds}s."

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
$seen = 0
try {
    while ($seen -lt $MaxRequests -and (Get-Date) -lt $deadline) {
        $pending = $listener.GetContextAsync()
        while (-not $pending.Wait(500)) {
            if ((Get-Date) -ge $deadline) {
                break
            }
        }
        if (-not $pending.IsCompleted) {
            break
        }
        $context = $pending.Result
        $seen++
        Write-Output "----- request $seen : $($context.Request.HttpMethod) $($context.Request.RawUrl)"
        Write-Output "socket peer: $($context.Request.RemoteEndPoint.Address)"
        foreach ($name in $context.Request.Headers.AllKeys) {
            $value = if ($redacted -contains $name.ToLowerInvariant()) { '<redacted>' } else { $context.Request.Headers[$name] }
            Write-Output "  ${name}: $value"
        }
        $bytes = [System.Text.Encoding]::UTF8.GetBytes('ok')
        $context.Response.StatusCode = 200
        $context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        $context.Response.Close()
    }
} finally {
    $listener.Stop()
    $listener.Close()
}
Write-Output "[PROBE] Done after $seen request(s)."
