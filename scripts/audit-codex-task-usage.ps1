param(
    [string]$RolloutPath,
    [string]$ThreadId,
    [switch]$EnforceStageGate,
    [Alias('EnforceBuildGate')]
    [switch]$LegacyBuildGate,
    [switch]$AllowDocumentedWeeklyReserveOverride,
    [int]$MaxTaskWeeklyIncreasePercent = 5,
    [int]$MaxMedianInputTokens = 75000,
    [int]$MaxLastInputTokens = 120000,
    [int]$MaxRecentInputGrowthPercent = 35,
    [int]$MaxWeeklyUsedPercent = 80,
    [int]$MaxProtectedStageWeeklyUsedPercent = 95
)

$ErrorActionPreference = 'Stop'

function Test-DocumentedWeeklyReserveOverride {
    param()

    $ledgerPath = Join-Path (Split-Path -Parent $PSScriptRoot) 'docs\codex-usage-ledger.md'
    if (-not (Test-Path -LiteralPath $ledgerPath -PathType Leaf)) { return $false }
    $ledger = Get-Content -LiteralPath $ledgerPath -Raw
    return $ledger -match '(?s)## 2026-08-22 - Explicit release-debt waiver for build and physical acceptance.*?User authorization: continue.*?80% weekly-reserve advisory.*?Protected operations authorized'
}

function Resolve-RolloutPath {
    param([string]$ExplicitPath, [string]$ExplicitThreadId)

    if ($ExplicitPath) {
        if (-not (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
            throw "Codex rollout not found: $ExplicitPath"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }

    $effectiveThreadId = if ($ExplicitThreadId) { $ExplicitThreadId } elseif ($env:CODEX_THREAD_ID) { $env:CODEX_THREAD_ID } elseif ($env:CODEX_SESSION_ID) { $env:CODEX_SESSION_ID } else { $null }
    if (-not $effectiveThreadId) {
        return $null
    }

    $sessionsRoot = Join-Path $env:USERPROFILE '.codex\sessions'
    if (-not (Test-Path -LiteralPath $sessionsRoot -PathType Container)) {
        return $null
    }

    $latest = Get-ChildItem -LiteralPath $sessionsRoot -Filter "rollout-*-$effectiveThreadId.jsonl" -File -Recurse |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($latest) { return $latest.FullName }
    return $null
}

function Get-PercentileValue {
    param([long[]]$Values, [double]$Percentile)
    if (-not $Values -or $Values.Count -eq 0) { return 0 }
    $ordered = @($Values | Sort-Object)
    $index = [math]::Floor(($ordered.Count - 1) * $Percentile)
    return [long]$ordered[$index]
}

function Get-UsageDelta {
    param([object[]]$Rows, [string]$Label)
    if (-not $Rows -or $Rows.Count -lt 2) { return $null }
    $first = $Rows[0]
    $last = $Rows[-1]
    $inputDelta = [long]($last.input - $first.input)
    $cachedDelta = [long]($last.cached - $first.cached)
    $cacheWriteDelta = [long]($last.cacheWrite - $first.cacheWrite)
    $callInputs = [long[]]@($Rows | Select-Object -Skip 1 | ForEach-Object { $_.lastInput })

    [pscustomobject]@{
        label = $Label
        startedAt = $first.timestamp.ToString('o')
        endedAt = $last.timestamp.ToString('o')
        modelCalls = $Rows.Count - 1
        inputTokens = $inputDelta
        cachedInputTokens = $cachedDelta
        cacheWriteTokens = $cacheWriteDelta
        uncachedInputTokens = $inputDelta - $cachedDelta - $cacheWriteDelta
        outputTokens = [long]($last.output - $first.output)
        reasoningOutputTokens = [long]($last.reasoning - $first.reasoning)
        totalTokens = [long]($last.total - $first.total)
        cacheHitPercent = if ($inputDelta -gt 0) { [math]::Round(100 * $cachedDelta / $inputDelta, 2) } else { 0 }
        averageInputTokensPerCall = if ($callInputs.Count) { [long][math]::Round(($callInputs | Measure-Object -Average).Average) } else { 0 }
        medianInputTokensPerCall = Get-PercentileValue $callInputs 0.5
        p90InputTokensPerCall = Get-PercentileValue $callInputs 0.9
        maxInputTokensPerCall = if ($callInputs.Count) { [long]($callInputs | Measure-Object -Maximum).Maximum } else { 0 }
        weeklyUsedPercentStart = $first.usedPercent
        weeklyUsedPercentEnd = $last.usedPercent
    }
}

function Get-ReplayTrend {
    param([object[]]$Rows)

    $callInputs = [long[]]@($Rows | Select-Object -Skip 1 | ForEach-Object { $_.lastInput })
    $sampleSize = [math]::Min(3, [math]::Floor($callInputs.Count / 2))
    if ($sampleSize -lt 1) {
        return [pscustomobject]@{
            sampleSize = 0
            earlierMedianInputTokens = 0
            recentMedianInputTokens = 0
            inputGrowthPercent = 0
        }
    }

    $earlier = [long[]]@($callInputs | Select-Object -First $sampleSize)
    $recent = [long[]]@($callInputs | Select-Object -Last $sampleSize)
    $earlierMedian = Get-PercentileValue $earlier 0.5
    $recentMedian = Get-PercentileValue $recent 0.5
    [pscustomobject]@{
        sampleSize = $sampleSize
        earlierMedianInputTokens = $earlierMedian
        recentMedianInputTokens = $recentMedian
        inputGrowthPercent = if ($earlierMedian -gt 0) {
            [int][math]::Round(100 * ($recentMedian - $earlierMedian) / $earlierMedian)
        } else { 0 }
    }
}

$resolvedRollout = Resolve-RolloutPath $RolloutPath $ThreadId
if (-not $resolvedRollout) {
    $unavailable = [pscustomobject]@{
        status = 'not-applicable'
        reason = 'No rollout matched the invoking CODEX_THREAD_ID. The audit will not substitute another task rollout.'
    }
    $unavailable | ConvertTo-Json -Depth 5
    exit 0
}

$tokenRows = [System.Collections.Generic.List[object]]::new()
$compactionTimes = [System.Collections.Generic.List[datetime]]::new()
$toolCounts = @{}
$rolloutThreadId = $null

Get-Content -LiteralPath $resolvedRollout -ReadCount 500 | ForEach-Object {
    foreach ($line in $_) {
        try { $item = $line | ConvertFrom-Json -Depth 50 } catch { continue }
        $timestamp = if ($item.timestamp) { [datetime]$item.timestamp } else { $null }

        if (-not $rolloutThreadId -and $item.type -eq 'session_meta') {
            $rolloutThreadId = if ($item.payload.id) { [string]$item.payload.id } elseif ($item.payload.thread_id) { [string]$item.payload.thread_id } else { $null }
        }

        if ($item.type -eq 'event_msg' -and $item.payload.type -eq 'context_compacted' -and $timestamp) {
            $compactionTimes.Add($timestamp)
        }

        if ($item.type -eq 'response_item' -and
            ($item.payload.type -eq 'function_call' -or $item.payload.type -eq 'custom_tool_call')) {
            $toolName = if ($item.payload.name) { [string]$item.payload.name } elseif ($item.payload.tool_name) { [string]$item.payload.tool_name } else { 'unknown' }
            if ($toolCounts.ContainsKey($toolName)) { $toolCounts[$toolName]++ } else { $toolCounts[$toolName] = 1 }
        }

        if ($item.type -ne 'event_msg' -or $item.payload.type -ne 'token_count' -or -not $timestamp) { continue }
        $total = $item.payload.info.total_token_usage
        $last = $item.payload.info.last_token_usage
        $limits = $item.payload.rate_limits
        $meter = if ($limits.primary) { $limits.primary } elseif ($limits.secondary) { $limits.secondary } else { $null }
        $tokenRows.Add([pscustomobject]@{
            timestamp = $timestamp
            input = [long]$total.input_tokens
            cached = [long]$total.cached_input_tokens
            cacheWrite = if ($null -ne $total.cache_write_input_tokens) { [long]$total.cache_write_input_tokens } else { [long]$total.cache_write_tokens }
            output = [long]$total.output_tokens
            reasoning = [long]$total.reasoning_output_tokens
            total = [long]$total.total_tokens
            lastInput = [long]$last.input_tokens
            usedPercent = if ($meter) { [int]$meter.used_percent } else { $null }
            windowMinutes = if ($meter) { [int]$meter.window_minutes } else { $null }
            resetsAt = if ($meter) { [long]$meter.resets_at } else { $null }
        })
    }
}

$expectedThreadId = if ($ThreadId) { $ThreadId } elseif (-not $RolloutPath -and $env:CODEX_THREAD_ID) { $env:CODEX_THREAD_ID } elseif (-not $RolloutPath -and $env:CODEX_SESSION_ID) { $env:CODEX_SESSION_ID } else { $null }
if ($expectedThreadId -and -not $rolloutThreadId) {
    [pscustomobject]@{
        status = 'not-applicable'
        rolloutPath = $resolvedRollout
        expectedThreadId = $expectedThreadId
        routingDecision = 'unbound-worker'
        reason = 'Resolved rollout has no machine-readable thread identity; the audit will not attribute another task rollout.'
    } | ConvertTo-Json -Depth 5
    exit 0
}
if ($expectedThreadId -and $rolloutThreadId -ne $expectedThreadId) {
    throw "Resolved rollout belongs to thread '$rolloutThreadId', not invoking thread '$expectedThreadId'."
}

if ($tokenRows.Count -lt 2) {
    [pscustomobject]@{
        status = 'warming-up'
        rolloutPath = $resolvedRollout
        tokenCountEvents = $tokenRows.Count
        reason = 'Fewer than two token-count samples exist; rerun at the next checkpoint.'
    } | ConvertTo-Json -Depth 5
    exit 0
}

$meterRows = @($tokenRows | Where-Object { $_.windowMinutes -eq 10080 -and $null -ne $_.resetsAt })
$latestMeter = $meterRows | Select-Object -Last 1
$currentReset = if ($latestMeter) { $latestMeter.resetsAt } else { $null }
$currentRows = if ($currentReset) { @($meterRows | Where-Object { $_.resetsAt -eq $currentReset }) } else { @($tokenRows) }
$currentUsage = Get-UsageDelta $currentRows 'current-week-in-this-task'
$lifetimeUsage = Get-UsageDelta @($tokenRows) 'rollout-lifetime'
$currentStart = $currentRows[0].timestamp
$currentEnd = $currentRows[-1].timestamp
$currentCompactions = @($compactionTimes | Where-Object { $_ -ge $currentStart -and $_ -le $currentEnd })
$latestCompaction = $currentCompactions | Select-Object -Last 1
$replayRows = if ($latestCompaction) { @($currentRows | Where-Object { $_.timestamp -gt $latestCompaction }) } else { @($currentRows) }
if ($replayRows.Count -lt 2) { $replayRows = @($currentRows | Select-Object -Last 2) }
$replayUsage = Get-UsageDelta $replayRows 'since-last-compaction'
$replayTrend = Get-ReplayTrend $replayRows
$weeklyIncrease = if ($currentUsage -and $null -ne $currentUsage.weeklyUsedPercentStart -and $null -ne $currentUsage.weeklyUsedPercentEnd) {
    [int]($currentUsage.weeklyUsedPercentEnd - $currentUsage.weeklyUsedPercentStart)
} else { 0 }

$replaySignals = [System.Collections.Generic.List[string]]::new()
$budgetViolations = [System.Collections.Generic.List[string]]::new()
$blockingViolations = [System.Collections.Generic.List[string]]::new()
if ($replayUsage.modelCalls -ge 3 -and $replayUsage.medianInputTokensPerCall -gt $MaxMedianInputTokens) {
    $replaySignals.Add("post-compaction median input $($replayUsage.medianInputTokensPerCall) exceeds $MaxMedianInputTokens tokens")
}
$lastInputTokens = [long]$replayRows[-1].lastInput
if ($lastInputTokens -gt $MaxLastInputTokens) {
    $replaySignals.Add("latest post-compaction input $lastInputTokens exceeds $MaxLastInputTokens tokens")
}
if ($replayTrend.sampleSize -ge 2 -and
    $replayTrend.recentMedianInputTokens -gt $MaxMedianInputTokens -and
    $replayTrend.inputGrowthPercent -gt $MaxRecentInputGrowthPercent) {
    $replaySignals.Add("recent input median grew $($replayTrend.inputGrowthPercent)% to $($replayTrend.recentMedianInputTokens) tokens")
}
if ($weeklyIncrease -gt $MaxTaskWeeklyIncreasePercent) {
    $budgetViolations.Add("task weekly increase ${weeklyIncrease}% exceeds ${MaxTaskWeeklyIncreasePercent}%")
}
if ($latestMeter -and $latestMeter.usedPercent -ge $MaxWeeklyUsedPercent) {
    $budgetViolations.Add("weekly meter $($latestMeter.usedPercent)% reached the ${MaxWeeklyUsedPercent}% warning threshold")
}
if ($latestMeter -and $latestMeter.usedPercent -ge $MaxProtectedStageWeeklyUsedPercent) {
    $blockingViolations.Add("weekly meter $($latestMeter.usedPercent)% reached the ${MaxProtectedStageWeeklyUsedPercent}% protected-stage cutoff")
}

$weeklyReserveViolation = if ($latestMeter) { "weekly meter $($latestMeter.usedPercent)% reached the ${MaxProtectedStageWeeklyUsedPercent}% protected-stage cutoff" } else { $null }
$weeklyReserveOverrideAccepted = $false
if ($AllowDocumentedWeeklyReserveOverride) {
    if ($blockingViolations -contains $weeklyReserveViolation) {
        if (-not (Test-DocumentedWeeklyReserveOverride)) {
            throw 'The explicit weekly-reserve override requires the documented 2026-08-22 user authorization in docs/codex-usage-ledger.md.'
        }
        $weeklyReserveOverrideAccepted = $true
        Write-Host "EXPLICIT WEEKLY-RESERVE OVERRIDE: allowing this protected stage at $($latestMeter.usedPercent)%; replay and task-growth signals remain visible optimization advisories." -ForegroundColor Yellow
    }
}

$history = @($meterRows | Group-Object { [long]([math]::Floor([double]$_.resetsAt / 10) * 10) } | ForEach-Object {
    $usage = Get-UsageDelta @($_.Group) "reset-$($_.Name)"
    if ($usage) {
        [pscustomobject]@{
            resetsAtApprox = [long]$_.Name
            startUsedPercent = $usage.weeklyUsedPercentStart
            endUsedPercent = $usage.weeklyUsedPercentEnd
            modelCalls = $usage.modelCalls
            totalTokens = $usage.totalTokens
            cachedInputTokens = $usage.cachedInputTokens
        }
    }
})

$result = [pscustomobject]@{
    status = if ($weeklyReserveOverrideAccepted) { 'explicit-weekly-reserve-override' } elseif ($blockingViolations.Count) { 'weekly-budget-exhausted' } elseif ($replaySignals.Count) { 'compaction-recommended' } elseif ($weeklyIncrease -gt $MaxTaskWeeklyIncreasePercent) { 'task-budget-checkpoint' } elseif ($budgetViolations.Count) { 'weekly-reserve-warning' } else { 'within-budget' }
    threadId = $rolloutThreadId
    routingDecision = if ($env:CODEX_AGENT_ROLE -match '(?i)sol|root' -or $env:CODEX_MODEL -match '(?i)gpt-5\.6') { 'delegate-required' } else { 'allowed' }
    rolloutPath = $resolvedRollout
    currentResetAt = $currentReset
    currentWeeklyMeter = if ($latestMeter) { $latestMeter.usedPercent } else { $null }
    taskWeeklyIncreasePercent = $weeklyIncrease
    current = $currentUsage
    replay = $replayUsage
    replayTrend = $replayTrend
    lifetime = $lifetimeUsage
    currentCompactions = $currentCompactions.Count
    lifetimeCompactions = $compactionTimes.Count
    topToolCalls = @($toolCounts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 10 | ForEach-Object {
        [pscustomobject]@{ name = $_.Key; count = $_.Value }
    })
    weeklyHistory = $history
    limits = [pscustomobject]@{
        maxTaskWeeklyIncreasePercent = $MaxTaskWeeklyIncreasePercent
        maxMedianInputTokens = $MaxMedianInputTokens
        maxLastInputTokens = $MaxLastInputTokens
        maxRecentInputGrowthPercent = $MaxRecentInputGrowthPercent
        maxWeeklyUsedPercent = $MaxWeeklyUsedPercent
        maxProtectedStageWeeklyUsedPercent = $MaxProtectedStageWeeklyUsedPercent
    }
    replaySignals = @($replaySignals)
    budgetViolations = @($budgetViolations)
    blockingViolations = @($blockingViolations)
    weeklyReserveOverrideAccepted = $weeklyReserveOverrideAccepted
    recommendedAction = if ($blockingViolations.Count -and -not $weeklyReserveOverrideAccepted) {
        'Stop optional work before a compiler or device stage; checkpoint source or use the documented explicit reserve override.'
    } elseif ($replaySignals.Count) {
        'Compact with the owning client when available and shorten the next packet; continue the smallest coherent source stage without creating a task chain.'
    } elseif ($weeklyIncrease -gt $MaxTaskWeeklyIncreasePercent) {
        'Report the five-point change, stop optional parallel work, and finish the smallest coherent stage in the same coordinator.'
    } elseif ($budgetViolations.Count) {
        'Weekly reserve warning: avoid optional research and reviewer loops; preserve room for the final protected stage.'
    } else { 'Continue with bounded commands and re-audit only before the next protected stage.' }
}

$result | ConvertTo-Json -Depth 8
if (($EnforceStageGate -or $LegacyBuildGate) -and $blockingViolations.Count -gt 0 -and -not $weeklyReserveOverrideAccepted) {
    throw 'Codex weekly reserve is below the protected-stage floor. Checkpoint source or use the documented explicit reserve override before compilation or device deployment.'
}
exit 0
