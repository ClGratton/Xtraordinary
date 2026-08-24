param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Begin', 'Restore')]
    [string]$Action,
    [string]$Serial,
    [string]$StatePath
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $repositoryRoot '.tools\android-sdk\platform-tools\adb.exe'
$fallbackTimeoutMs = 1800000L
$workTimeoutMs = 2147483647L
if (-not $StatePath) {
    $StatePath = Join-Path $repositoryRoot 'build\pixel-screen-timeout-state.json'
}

function Get-OnlineSerial {
    param([string]$PreferredSerial)
    $online = @(& $adb devices | Select-String '\tdevice$' | ForEach-Object { $_.ToString().Split("`t")[0] })
    if ($PreferredSerial -and $online -contains $PreferredSerial) { return $PreferredSerial }
    if ($online.Count -eq 1) { return $online[0] }
    if ($online.Count -eq 0) { throw 'No authenticated Pixel is available through repository ADB.' }
    throw 'Multiple Android devices are online; pass -Serial explicitly.'
}

function Set-AndVerifyTimeout {
    param([string]$DeviceSerial, [long]$TimeoutMs)
    & $adb -s $DeviceSerial shell settings put system screen_off_timeout $TimeoutMs
    if ($LASTEXITCODE -ne 0) { throw "Could not set Pixel screen timeout to $TimeoutMs ms." }
    $actual = (& $adb -s $DeviceSerial shell settings get system screen_off_timeout).Trim()
    if ($actual -ne $TimeoutMs.ToString()) {
        throw "Pixel screen timeout verification failed: expected $TimeoutMs, received '$actual'."
    }
}

if (-not (Test-Path -LiteralPath $adb)) {
    throw "Repository ADB was not found at $adb"
}

if ($Action -eq 'Begin') {
    if (Test-Path -LiteralPath $StatePath) {
        $existing = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
        $deviceSerial = Get-OnlineSerial -PreferredSerial ([string]$existing.serial)
        Set-AndVerifyTimeout -DeviceSerial $deviceSerial -TimeoutMs $workTimeoutMs
        Write-Output "Pixel work timeout already active; preserved original timeout $($existing.originalTimeoutMs) ms."
        exit 0
    }

    $deviceSerial = Get-OnlineSerial -PreferredSerial $Serial
    $rawOriginal = (& $adb -s $deviceSerial shell settings get system screen_off_timeout).Trim()
    $parsedOriginal = 0L
    $originalTimeoutMs = if ([long]::TryParse($rawOriginal, [ref]$parsedOriginal) -and $parsedOriginal -gt 0) {
        $parsedOriginal
    } else {
        $fallbackTimeoutMs
    }
    $stateDirectory = Split-Path -Parent $StatePath
    New-Item -ItemType Directory -Path $stateDirectory -Force | Out-Null
    [ordered]@{
        serial = $deviceSerial
        originalTimeoutMs = $originalTimeoutMs
        fallbackTimeoutMs = $fallbackTimeoutMs
        startedAt = [DateTimeOffset]::Now.ToString('o')
    } | ConvertTo-Json | Set-Content -LiteralPath $StatePath -Encoding UTF8
    Set-AndVerifyTimeout -DeviceSerial $deviceSerial -TimeoutMs $workTimeoutMs
    Write-Output "Pixel screen timeout disabled for device work; saved original timeout $originalTimeoutMs ms."
    exit 0
}

$savedState = $null
if (Test-Path -LiteralPath $StatePath) {
    $savedState = Get-Content -LiteralPath $StatePath -Raw | ConvertFrom-Json
}
$preferredSerial = if ($Serial) { $Serial } elseif ($savedState) { [string]$savedState.serial } else { $null }
$restoreTimeoutMs = if ($savedState -and [long]$savedState.originalTimeoutMs -gt 0) {
    [long]$savedState.originalTimeoutMs
} else {
    $fallbackTimeoutMs
}

try {
    $deviceSerial = Get-OnlineSerial -PreferredSerial $preferredSerial
    Set-AndVerifyTimeout -DeviceSerial $deviceSerial -TimeoutMs $restoreTimeoutMs
    if (Test-Path -LiteralPath $StatePath) { Remove-Item -LiteralPath $StatePath -Force }
    Write-Output "Pixel screen timeout restored to $restoreTimeoutMs ms."
} catch {
    try {
        $deviceSerial = Get-OnlineSerial -PreferredSerial $preferredSerial
        Set-AndVerifyTimeout -DeviceSerial $deviceSerial -TimeoutMs $fallbackTimeoutMs
        if (Test-Path -LiteralPath $StatePath) { Remove-Item -LiteralPath $StatePath -Force }
    } catch {
        throw "Pixel screen-timeout restore failed and the 30-minute fallback could not be applied: $($_.Exception.Message)"
    }
    throw "Pixel screen-timeout restore failed; applied the 30-minute fallback instead."
}
