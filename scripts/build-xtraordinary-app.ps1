param(
    [string[]]$Tasks = @(
        ':protocol:test',
        ':app:testDebugUnitTest',
        ':app:lintDebug',
        ':app:assembleDebug'
    )
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot '.tools\gradle-9.5.0\bin\gradle.bat'
$toolchainScript = Join-Path $PSScriptRoot 'use-toolchains.ps1'
$policyCheck = Join-Path $PSScriptRoot 'check-engineering-policies.ps1'

if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Bundled Gradle was not found at $gradle"
}
if (-not (Test-Path -LiteralPath $toolchainScript)) {
    throw "Toolchain setup was not found at $toolchainScript"
}
if (-not (Test-Path -LiteralPath $policyCheck)) {
    throw "Engineering policy gate was not found at $policyCheck"
}

& $policyCheck
if (-not $?) {
    throw "Engineering policy gate failed"
}

. $toolchainScript

Push-Location $repoRoot
try {
    & $gradle --no-daemon --console=plain @Tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Android build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
