param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Begin', 'Restore', 'Run')]
    [string]$Action,
    [string]$Serial,
    [string]$AndroidPackage = 'com.xteink.companion',
    [ValidateRange(1, 600)]
    [int]$Seconds = 600,
    [string]$Command,
    [string[]]$CommandArguments = @()
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $repositoryRoot '.tools\android-sdk\platform-tools\adb.exe'
$pixelTimeout = Join-Path $PSScriptRoot 'manage-pixel-screen-timeout.ps1'
if (-not (Test-Path -LiteralPath $adb)) { throw "Required project ADB is missing: $adb" }
if (-not (Test-Path -LiteralPath $pixelTimeout)) { throw "Pixel timeout lifecycle helper is missing: $pixelTimeout" }
if ([string]::IsNullOrWhiteSpace($Serial)) { throw 'Pass the current authenticated Pixel serial explicitly.' }

function Begin-PixelLifecycle {
    & $pixelTimeout -Action Begin -Serial $Serial
    if ($LASTEXITCODE -ne 0) { throw 'Could not begin Pixel screen lifecycle.' }
}

function Restore-PixelLifecycle {
    & $pixelTimeout -Action Restore -Serial $Serial
    if ($LASTEXITCODE -ne 0) { throw 'Could not restore Pixel screen lifecycle.' }
}

function Invoke-Maintenance([int]$durationSeconds) {
    & $adb -s $Serial shell am start -n "$AndroidPackage/.MainActivity" `
        -a "$AndroidPackage.action.X3_MAINTENANCE_LEASE" --ei seconds $durationSeconds | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not request X3 maintenance lease ($durationSeconds seconds)." }
}

function Restore-Maintenance {
    & $adb -s $Serial shell am start -n "$AndroidPackage/.MainActivity" `
        -a "$AndroidPackage.action.X3_MAINTENANCE_LEASE" --ei seconds 0 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not restore X3 maintenance lifecycle.' }
}

switch ($Action) {
    'Begin' {
        Begin-PixelLifecycle
        try { Invoke-Maintenance $Seconds }
        catch { Restore-PixelLifecycle; throw }
        Write-Output "X3 ephemeral maintenance lease requested for $Seconds seconds; no fast-link or persisted policy change requested."
    }
    'Restore' {
        try { Restore-Maintenance } finally { Restore-PixelLifecycle }
        Write-Output 'X3 ephemeral maintenance lease restored.'
    }
    'Run' {
        if ([string]::IsNullOrWhiteSpace($Command)) { throw 'Run requires -Command and optional -CommandArguments.' }
        try {
            Begin-PixelLifecycle
            Invoke-Maintenance $Seconds
            & $Command @CommandArguments
            if ($LASTEXITCODE -ne 0) { throw "Maintenance command failed with exit code $LASTEXITCODE." }
        }
        finally {
            # A task crash or command failure must not leave a keep-awake lease.
            try { Restore-Maintenance } finally { Restore-PixelLifecycle }
        }
    }
}
