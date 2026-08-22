$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$generator = Join-Path $PSScriptRoot 'generate-android-release-notices.ps1'
$sourcePack = Join-Path $repoRoot 'release-notices\android'
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

    Write-Host 'Android notice verifier regression checks passed (CRLF equivalence and content mismatch rejection).'
} finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
