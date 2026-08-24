$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$generator = Join-Path $PSScriptRoot 'generate-android-release-notices.ps1'
$sourcePack = Join-Path $repoRoot 'release-notices\android'
$generatedRoot = Join-Path $repoRoot 'app\build\legal\runtime-dependencies'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("xtraordinary-android-notices-{0}" -f [Guid]::NewGuid())
$fixturePack = Join-Path $fixtureRoot 'android'

try {
    Copy-Item -LiteralPath $sourcePack -Destination $fixturePack -Recurse

    # A Windows checkout may materialize CRLF although the reviewed manifest
    # binds canonical LF text bytes. The line-ending-only conversion must pass.
    $fixtureNotice = Join-Path $fixturePack 'CheckerFramework-MIT.txt'
    $lfBytes = [System.IO.File]::ReadAllBytes($fixtureNotice)
    $crlfBytes = [System.Collections.Generic.List[byte]]::new($lfBytes.Length * 2)
    for ($index = 0; $index -lt $lfBytes.Length; $index++) {
        if ($lfBytes[$index] -eq 0x0a -and ($index -eq 0 -or $lfBytes[$index - 1] -ne 0x0d)) {
            $crlfBytes.Add(0x0d)
        }
        $crlfBytes.Add($lfBytes[$index])
    }
    [System.IO.File]::WriteAllBytes($fixtureNotice, $crlfBytes.ToArray())
    & $generator -Check -NoticeRoot $fixturePack

    foreach ($name in @('communityReleaseRuntimeLicenses.tsv', 'playReleaseRuntimeLicenses.tsv')) {
        $generated = Join-Path $generatedRoot $name
        $bytes = [System.IO.File]::ReadAllBytes($generated)
        if ($bytes.Length -lt 3 -or
            $bytes[0] -ne 0xef -or
            $bytes[1] -ne 0xbb -or
            $bytes[2] -ne 0xbf) {
            throw "Generated Android license inventory is not deterministic UTF-8 with BOM: $name"
        }
    }

    [System.IO.File]::AppendAllText($fixtureNotice, "`nchanged fixture content`n", [System.Text.UTF8Encoding]::new($false))
    $contentMismatchRejected = $false
    try {
        & $generator -Check -NoticeRoot $fixturePack
    } catch {
        if ($_.Exception.Message -notmatch 'manifest is stale') {
            throw
        }
        $contentMismatchRejected = $true
    }
    if (-not $contentMismatchRejected) {
        throw 'Changed Android notice fixture unexpectedly passed verification.'
    }

    Write-Host 'Android notice verifier regression checks passed (deterministic UTF-8 BOM, CRLF equivalence, and content mismatch rejection).'
} finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
