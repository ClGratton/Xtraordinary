param(
    [Parameter(Mandatory)] [string]$Version,
    [string]$BinaryPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($BinaryPath)) {
    $BinaryPath = Join-Path $repoRoot 'firmware\.pio\build\x3_companion_release\firmware.bin'
}
$binary = (Resolve-Path -LiteralPath $BinaryPath).Path
$repoCommit = (& git -c "safe.directory=$($repoRoot.Replace('\', '/'))" -C $repoRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $repoCommit -notmatch '^[0-9a-f]{40}$') {
    throw 'Could not bind the firmware artifact to the repository source commit.'
}
$noticeManifest = Join-Path $repoRoot 'release-notices\firmware\MANIFEST.sha256'
$mapPath = Join-Path (Split-Path -Parent $binary) 'firmware.map'
$webSocketObjects = @()
if (Test-Path -LiteralPath $mapPath) {
    $mapContent = Get-Content -LiteralPath $mapPath -Raw
    $webSocketObjects = @(
        [regex]::Matches($mapContent, 'WebSockets[/\\][^\s()]+\.cpp\.o') |
            ForEach-Object { $_.Value.Replace('\', '/') } |
            Sort-Object -Unique
    )
}
$unresolvedReleaseObligations = @('Final reviewer/date approval for public distribution')
if ($webSocketObjects.Count -gt 0) {
    $unresolvedReleaseObligations = @(
        'ArduinoWebSockets LGPL-2.1 static-link compliance decision and corresponding relinkable/source material'
    ) + $unresolvedReleaseObligations
}
$record = [ordered]@{
    schemaVersion = 1
    provider = 'Xtraordinary'
    model = 'X3'
    version = $Version
    sourceCommit = $repoCommit
    sourceUrl = "https://github.com/ClGratton/Xtraordinary/tree/$repoCommit"
    correspondingSourceUrl = "https://github.com/ClGratton/Xtraordinary/tree/$repoCommit/firmware"
    upstream = [ordered]@{
        project = 'CrossPoint Reader'
        version = '1.4.1'
        commit = '2754a5ff01644d36cf0a17db98f28408666ba518'
        sourceUrl = 'https://github.com/crosspoint-reader/crosspoint-reader/tree/2754a5ff01644d36cf0a17db98f28408666ba518'
    }
    artifact = [ordered]@{
        name = [IO.Path]::GetFileName($binary)
        sizeBytes = (Get-Item -LiteralPath $binary).Length
        sha256 = (Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    notices = [ordered]@{
        manifestSha256 = (Get-FileHash -LiteralPath $noticeManifest -Algorithm SHA256).Hash.ToLowerInvariant()
        sourcePath = 'release-notices/firmware'
    }
    linkedLgplEvidence = [ordered]@{
        arduinoWebSocketsObjects = $webSocketObjects
        mapPath = if ($webSocketObjects.Count -gt 0) { 'firmware.map' } else { $null }
    }
    modificationSummary = @(
        'docs/crosspoint-change-plan.md',
        'docs/x3-takeover-tracker.md'
    )
    unresolvedReleaseObligations = $unresolvedReleaseObligations
    generatedAtUtc = [DateTime]::UtcNow.ToString('o')
}
$destination = Join-Path (Split-Path -Parent $binary) 'xtraordinary-release-record.json'
$record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $destination -Encoding utf8
Write-Host "Wrote source-bound firmware release record: $destination"
