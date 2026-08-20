$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$registryPath = Join-Path $repoRoot 'docs\artwork-provenance.tsv'

if (-not (Test-Path -LiteralPath $registryPath)) {
    throw "Artwork provenance register is missing: $registryPath"
}

$rows = @(Import-Csv -LiteralPath $registryPath -Delimiter "`t")
$registered = @{}
foreach ($row in $rows) {
    if ($registered.ContainsKey($row.path)) {
        throw "Duplicate artwork provenance row: $($row.path)"
    }
    $assetPath = Join-Path $repoRoot ($row.path -replace '/', '\')
    if (-not (Test-Path -LiteralPath $assetPath -PathType Leaf)) {
        throw "Registered artwork is missing: $($row.path)"
    }
    $actualHash = (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualHash -ne $row.sha256) {
        throw "Artwork hash changed without a provenance update: $($row.path)"
    }
    $registered[$row.path] = $true
}

$expected = @(
    Get-ChildItem -LiteralPath (Join-Path $repoRoot 'app\src\main\res\drawable-nodpi') -File -Filter '*.png' |
        ForEach-Object { 'app/src/main/res/drawable-nodpi/' + $_.Name }
    Get-ChildItem -LiteralPath (Join-Path $repoRoot 'docs\assets') -File -Filter 'concept-*-reference.png' |
        ForEach-Object { 'docs/assets/' + $_.Name }
)
foreach ($path in $expected) {
    if (-not $registered.ContainsKey($path)) {
        throw "Artwork has no provenance row: $path"
    }
}
foreach ($path in $registered.Keys) {
    if ($path -notin $expected) {
        throw "Artwork provenance row is outside the controlled production/concept set: $path"
    }
}

$unresolved = @($rows | Where-Object {
    $_.generatorAccount -eq 'MISSING' -or
    $_.promptOrSource -eq 'MISSING' -or
    $_.commercialTermsEvidence -eq 'MISSING' -or
    $_.humanApproval -eq 'MISSING'
})
Write-Host "Artwork provenance inventory matches $($rows.Count) tracked files; $($unresolved.Count) rows still require rights evidence before public release."
