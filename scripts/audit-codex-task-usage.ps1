param(
    [string]$RolloutPath,
    [switch]$EnforceStageGate,
    [Alias('EnforceBuildGate')]
    [switch]$LegacyBuildGate,
    [int]$MaxTaskWeeklyIncreasePercent = 5,
    [int]$MaxModelCalls = 20,
    [int]$MaxMedianInputTokens = 75000,
    [int]$MaxLastInputTokens = 120000,
    [int]$MaxCompactions = 0,
    [int]$MaxWeeklyUsedPercent = 80
)

$ErrorActionPreference = 'Stop'

function Resolve-RolloutPath {
    param([string]$ExplicitPath)

    if ($ExplicitPath) {
        if (-not (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
            throw "Codex rollout not found: $ExplicitPath"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }

    $sessionsRoot = Join-Path $env:USERPROFILE '.codex\sessions'
    if (-not (Test-Path -LiteralPath $sessionsRoot -PathType Container)) {
        return $null
    }

    $latest = Get-ChildItem -LiteralPath $sessionsRoot -Filter 'rollout-*.jsonl' -File -Recurse |
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

$resolvedRollout = Resolve-RolloutPath $RolloutPath
if (-not $resolvedRollout) {
    $unavailable = [pscustomobject]@{
        status = 'unavailable'
        reason = 'No Codex rollout was found under the normal-user sessions directory.'
    }
    $unavailable | ConvertTo-Json -Depth 5
    if ($EnforceStageGate -or $LegacyBuildGate) { exit 2 }
    exit 0
}

$tokenRows = [System.Collections.Generic.List[object]]::new()
$compactionTimes = [System.Collections.Generic.List[datetime]]::new()
$toolCounts = @{}

Get-Content -LiteralPath $resolvedRollout -ReadCount 500 | ForEach-Object {
    foreach ($line in $_) {
        try { $item = $line | ConvertFrom-Json -Depth 50 } catch { continue }
        $timestamp = if ($item.timestamp) { [datetime]$item.timestamp } else { $null }

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
            cacheWrite = [long]$total.cache_write_tokens
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

if ($tokenRows.Count -lt 2) {
    throw "The rollout does not contain enough top-level token_count events: $resolvedRollout"
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
$weeklyIncrease = if ($currentUsage -and $null -ne $currentUsage.weeklyUsedPercentStart -and $null -ne $currentUsage.weeklyUsedPercentEnd) {
    [int]($currentUsage.weeklyUsedPercentEnd - $currentUsage.weeklyUsedPercentStart)
} else { 0 }

$violations = [System.Collections.Generic.List[string]]::new()
if ($currentUsage.modelCalls -gt $MaxModelCalls) {
    $violations.Add("model calls $($currentUsage.modelCalls) exceed $MaxModelCalls")
}
if ($currentUsage.modelCalls -ge 3 -and $currentUsage.medianInputTokensPerCall -gt $MaxMedianInputTokens) {
    $violations.Add("median input $($currentUsage.medianInputTokensPerCall) exceeds $MaxMedianInputTokens tokens")
}
if ($currentCompactions.Count -gt $MaxCompactions) {
    $violations.Add("compactions $($currentCompactions.Count) exceed $MaxCompactions")
}
$lastInputTokens = [long]$currentRows[-1].lastInput
if ($lastInputTokens -gt $MaxLastInputTokens) {
    $violations.Add("last input $lastInputTokens exceeds $MaxLastInputTokens tokens")
}
if ($weeklyIncrease -gt $MaxTaskWeeklyIncreasePercent) {
    $violations.Add("task weekly increase ${weeklyIncrease}% exceeds ${MaxTaskWeeklyIncreasePercent}%")
}
if ($latestMeter -and $latestMeter.usedPercent -ge $MaxWeeklyUsedPercent) {
    $violations.Add("weekly meter $($latestMeter.usedPercent)% reached the ${MaxWeeklyUsedPercent}% reserve cutoff")
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
    status = if ($violations.Count) { 'handoff-required' } else { 'within-budget' }
    rolloutPath = $resolvedRollout
    currentResetAt = $currentReset
    currentWeeklyMeter = if ($latestMeter) { $latestMeter.usedPercent } else { $null }
    taskWeeklyIncreasePercent = $weeklyIncrease
    current = $currentUsage
    lifetime = $lifetimeUsage
    currentCompactions = $currentCompactions.Count
    lifetimeCompactions = $compactionTimes.Count
    topToolCalls = @($toolCounts.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 10 | ForEach-Object {
        [pscustomobject]@{ name = $_.Key; count = $_.Value }
    })
    weeklyHistory = $history
    limits = [pscustomobject]@{
        maxTaskWeeklyIncreasePercent = $MaxTaskWeeklyIncreasePercent
        maxModelCalls = $MaxModelCalls
        maxMedianInputTokens = $MaxMedianInputTokens
        maxLastInputTokens = $MaxLastInputTokens
        maxCompactions = $MaxCompactions
        maxWeeklyUsedPercent = $MaxWeeklyUsedPercent
    }
    violations = @($violations)
}

$result | ConvertTo-Json -Depth 8
if (($EnforceStageGate -or $LegacyBuildGate) -and $violations.Count) { exit 3 }
