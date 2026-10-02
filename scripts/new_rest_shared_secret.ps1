<#
.SYNOPSIS
    One-time generator for the REST API shared secret. Never prints the secret.

.DESCRIPTION
    Writes a random 256-bit secret (URL-safe base64, no padding) to
    %LOCALAPPDATA%\DateProgram\rest-shared-secret.txt, outside the git checkout, and restricts
    the file to the current Windows user. scripts\start_phone_alpha_backend.ps1 reads it when
    neither -SharedSecret nor DATING_APP_REST_SHARED_SECRET is supplied.

    The script refuses to overwrite an existing file, because rotating the secret breaks every
    installed app build that has the old value compiled in. To rotate deliberately, delete the
    file yourself first.

.PARAMETER Path
    Secret file location. Default: %LOCALAPPDATA%\DateProgram\rest-shared-secret.txt.

.NOTES
    To hand the value to a Flutter build without displaying it:
      $secret = (Get-Content -LiteralPath "$env:LOCALAPPDATA\DateProgram\rest-shared-secret.txt" -Raw).Trim()
      flutter build apk --release --dart-define=DATING_APP_SHARED_SECRET=$secret
#>
param(
    [string]$Path = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'DateProgram\rest-shared-secret.txt')
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (Test-Path -LiteralPath $Path) {
    throw "[SECRET] $Path already exists; refusing to overwrite it. Delete it yourself first if you really want a new secret (every app build compiled with the old value will stop working)."
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null

# CreateNew fails if another process created the file between the check above and here.
$stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
$stream.Dispose()

try {
    # Restrict the (still empty) file to the current user before any secret is written.
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
    $acl = Get-Acl -LiteralPath $Path
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($rule in @($acl.Access)) {
        [void]$acl.RemoveAccessRule($rule)
    }
    $acl.AddAccessRule((New-Object System.Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'Allow')))
    Set-Acl -LiteralPath $Path -AclObject $acl

    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    $secret = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    [System.IO.File]::WriteAllText($Path, $secret, (New-Object System.Text.UTF8Encoding($false)))
    $secret = $null
    [Array]::Clear($bytes, 0, $bytes.Length)
} catch {
    # Do not leave an empty or half-protected secret file behind.
    Remove-Item -LiteralPath $Path -Force -ErrorAction SilentlyContinue
    throw
}

Write-Output "[SECRET] Created $Path (readable only by $identity). The value was not printed."
