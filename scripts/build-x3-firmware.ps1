param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [ValidateRange(1, 8)]
    [int]$Jobs = 2
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$platformIo = Join-Path $repoRoot '.tools\platformio-venv\Scripts\platformio.exe'
$coreDir = Join-Path $repoRoot '.tools\platformio-core'
$policyCheck = Join-Path $PSScriptRoot 'check-engineering-policies.ps1'
$sourceCheck = Join-Path $PSScriptRoot 'assert-pushed-source.ps1'
$releaseRecordWriter = Join-Path $PSScriptRoot 'write-firmware-release-record.ps1'

if (-not (Test-Path -LiteralPath $platformIo)) {
    throw "Bundled PlatformIO was not found at $platformIo"
}
if (-not (Test-Path -LiteralPath $policyCheck)) {
    throw "Engineering policy gate was not found at $policyCheck"
}
if (-not (Test-Path -LiteralPath $sourceCheck)) {
    throw "Pushed-source gate was not found at $sourceCheck"
}
if (-not (Test-Path -LiteralPath $releaseRecordWriter)) {
    throw "Firmware release-record writer was not found at $releaseRecordWriter"
}

& $sourceCheck
& $policyCheck -Mode FirmwareRelease
if (-not $?) {
    throw "Engineering policy gate failed"
}

$env:PLATFORMIO_CORE_DIR = $coreDir
$env:XTRAORDINARY_VERSION = $Version
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'

Push-Location (Join-Path $repoRoot 'firmware')
try {
    & $platformIo run -e x3_companion_release -j $Jobs
    if ($LASTEXITCODE -ne 0) {
        throw "X3 firmware build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

& $releaseRecordWriter -Version $Version
