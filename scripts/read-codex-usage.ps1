param(
    [int]$ResponseTimeoutMilliseconds = 5000
)

$ErrorActionPreference = 'Stop'

$nodeCommand = Get-Command node -ErrorAction Stop
$codexJs = Join-Path $env:APPDATA 'npm\node_modules\@openai\codex\bin\codex.js'
if (-not (Test-Path -LiteralPath $codexJs -PathType Leaf)) {
    throw "The signed-in Codex installation was not found at $codexJs. Run this script in the normal signed-in user context, not the filesystem sandbox."
}

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = $nodeCommand.Source
$startInfo.ArgumentList.Add($codexJs)
$startInfo.ArgumentList.Add('app-server')
$startInfo.ArgumentList.Add('--stdio')
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.RedirectStandardOutput = $true
$startInfo.RedirectStandardError = $true

$process = [System.Diagnostics.Process]::Start($startInfo)
try {
    $process.StandardInput.WriteLine('{"id":1,"method":"initialize","params":{"clientInfo":{"name":"xtraordinary-usage-check","version":"1.0"},"capabilities":{"experimentalApi":true}}}')
    $process.StandardInput.Flush()

    $initializeTask = $process.StandardOutput.ReadLineAsync()
    if (-not $initializeTask.Wait($ResponseTimeoutMilliseconds)) {
        throw 'Timed out waiting for the Codex app-server initialize response.'
    }
    $initializeResponse = $initializeTask.Result | ConvertFrom-Json
    if ($initializeResponse.id -ne 1 -or -not $initializeResponse.result) {
        throw 'The Codex app server did not accept the usage-check client.'
    }

    $process.StandardInput.WriteLine('{"method":"initialized"}')
    $process.StandardInput.WriteLine('{"id":2,"method":"account/rateLimits/read","params":null}')
    $process.StandardInput.Flush()

    $rateLimitResponse = $null
    for ($attempt = 0; $attempt -lt 8; $attempt++) {
        $responseTask = $process.StandardOutput.ReadLineAsync()
        if (-not $responseTask.Wait($ResponseTimeoutMilliseconds)) {
            break
        }
        $line = $responseTask.Result
        if ($null -eq $line) {
            break
        }
        $response = $line | ConvertFrom-Json
        if ($response.id -eq 2) {
            $rateLimitResponse = $response
            break
        }
    }

    if (-not $rateLimitResponse -or -not $rateLimitResponse.result.rateLimits.primary) {
        throw 'The signed-in Codex rate-limit snapshot was not returned.'
    }

    $rateLimits = $rateLimitResponse.result.rateLimits
    [pscustomobject]@{
        usedPercent = [int]$rateLimits.primary.usedPercent
        windowDurationMins = [int]$rateLimits.primary.windowDurationMins
        resetsAt = [long]$rateLimits.primary.resetsAt
        planType = [string]$rateLimits.planType
        hasCredits = [bool]$rateLimits.credits.hasCredits
    } | ConvertTo-Json -Compress
} finally {
    $process.StandardInput.Close()
    if (-not $process.WaitForExit(2000)) {
        $process.Kill()
    }
}
