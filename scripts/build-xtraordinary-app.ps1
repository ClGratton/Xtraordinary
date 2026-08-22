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
$apkNoticeCheck = Join-Path $PSScriptRoot 'check-android-apk-notices.ps1'
$usageAudit = Join-Path $PSScriptRoot 'audit-codex-task-usage.ps1'

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
if (-not (Test-Path -LiteralPath $apkNoticeCheck)) {
    throw "APK release-notices verifier was not found at $apkNoticeCheck"
}
if (-not (Test-Path -LiteralPath $usageAudit)) {
    throw "Codex task-usage audit was not found at $usageAudit"
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

& $usageAudit -EnforceStageGate
if ($LASTEXITCODE -ne 0) {
    throw "Codex usage requires compaction or a fresh history-free bounded agent before another compiler run. An explicit user override must be recorded in docs/codex-usage-ledger.md before changing the protected thresholds."
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
    $gradleArguments = @('--no-daemon', '--console=plain')
    if ($UiEvidenceCandidate) {
        $gradleArguments += '--rerun-tasks'
    }
    & $gradle @gradleArguments @Tasks
    if ($LASTEXITCODE -ne 0) {
        throw "Android build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$apkChecks = @{
    ':app:assembleCommunityDebug' = Join-Path $repoRoot 'app\build\outputs\apk\community\debug\app-community-debug.apk'
    ':app:assemblePlayDebug' = Join-Path $repoRoot 'app\build\outputs\apk\play\debug\app-play-debug.apk'
    ':app:assembleCommunityRelease' = Join-Path $repoRoot 'app\build\outputs\apk\community\release\app-community-release-unsigned.apk'
    ':app:assemblePlayRelease' = Join-Path $repoRoot 'app\build\outputs\apk\play\release\app-play-release-unsigned.apk'
}
foreach ($task in $apkChecks.Keys) {
    if ($task -in $Tasks) {
        & $apkNoticeCheck -ApkPath $apkChecks[$task]
    }
}
