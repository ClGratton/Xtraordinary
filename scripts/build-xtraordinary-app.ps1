param(
    [string[]]$Tasks = @(
        ':protocol:test',
        ':app:testCommunityDebugUnitTest',
        ':app:validateCommunityDebugScreenshotTest',
        ':app:lintCommunityDebug',
        ':app:assembleCommunityDebug',
        ':app:testPlayDebugUnitTest',
        ':app:validatePlayDebugScreenshotTest',
        ':app:lintPlayDebug',
        ':app:assemblePlayDebug'
    ),
    [switch]$UiEvidenceCandidate
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot '.tools\gradle-9.5.0\bin\gradle.bat'
$toolchainScript = Join-Path $PSScriptRoot 'use-toolchains.ps1'
$policyCheck = Join-Path $PSScriptRoot 'check-engineering-policies.ps1'
$sourceCheck = Join-Path $PSScriptRoot 'assert-pushed-source.ps1'

if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Bundled Gradle was not found at $gradle"
}
if (-not (Test-Path -LiteralPath $toolchainScript)) {
    throw "Toolchain setup was not found at $toolchainScript"
}
if (-not (Test-Path -LiteralPath $policyCheck)) {
    throw "Engineering policy gate was not found at $policyCheck"
}
if (-not (Test-Path -LiteralPath $sourceCheck)) {
    throw "Pushed-source gate was not found at $sourceCheck"
}

if ($UiEvidenceCandidate) {
    # UiEvidenceCandidate may only update deterministic screenshot evidence after
    # the matching Community/Play unit tests establish motion-state fixtures.
    $requiredEvidenceTasks = @(
        ':app:testCommunityDebugUnitTest',
        ':app:updateCommunityDebugScreenshotTest',
        ':app:testPlayDebugUnitTest',
        ':app:updatePlayDebugScreenshotTest'
    )
    # This exact candidate set may only update deterministic Community/Play screenshot evidence.
    if ($PSBoundParameters.ContainsKey('Tasks')) {
        $isExactTaskSet = $Tasks.Count -eq $requiredEvidenceTasks.Count -and
            @($requiredEvidenceTasks | Where-Object { $_ -notin $Tasks }).Count -eq 0
        if (-not $isExactTaskSet) {
            throw "UiEvidenceCandidate requires the exact Community/Play unit-test and deterministic screenshot-update task set."
        }
    }
    $Tasks = $requiredEvidenceTasks
}

& $sourceCheck
if ($UiEvidenceCandidate) {
    & $policyCheck -Mode UiEvidenceCandidate
} else {
    & $policyCheck -Mode Release
}
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
