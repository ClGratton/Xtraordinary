$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$checker = Join-Path $PSScriptRoot 'check-firmware-release-notices.ps1'
$sourcePack = Join-Path $repoRoot 'release-notices\firmware'
$fixtureRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("xtraordinary-firmware-notices-{0}" -f [Guid]::NewGuid())
$fixturePack = Join-Path $fixtureRoot 'firmware'

try {
    Copy-Item -LiteralPath $sourcePack -Destination $fixturePack -Recurse

    # A Windows checkout may use CRLF even though the approved manifest hashes
    # canonical LF repository content. This conversion must leave verification valid.
    $fixtureNotice = Join-Path $fixturePack 'Apache-NimBLE-Apache-2.0.txt'
    $lfBytes = [System.IO.File]::ReadAllBytes($fixtureNotice)
    $crlfBytes = [System.Collections.Generic.List[byte]]::new($lfBytes.Length * 2)
    for ($index = 0; $index -lt $lfBytes.Length; $index++) {
        if ($lfBytes[$index] -eq 0x0a -and ($index -eq 0 -or $lfBytes[$index - 1] -ne 0x0d)) {
            $crlfBytes.Add(0x0d)
        }
        $crlfBytes.Add($lfBytes[$index])
    }
    [System.IO.File]::WriteAllBytes($fixtureNotice, $crlfBytes.ToArray())
    & $checker -NoticeRoot $fixturePack

    [System.IO.File]::AppendAllText($fixtureNotice, "`nchanged fixture content`n", [System.Text.UTF8Encoding]::new($false))
    $contentMismatchRejected = $false
    try {
        & $checker -NoticeRoot $fixturePack
    } catch {
        if ($_.Exception.Message -notmatch 'manifest is stale') {
            throw
        }
        $contentMismatchRejected = $true
    }
    if (-not $contentMismatchRejected) {
        throw 'Changed notice fixture unexpectedly passed verification.'
    }

    Write-Host 'Firmware notice verifier regression checks passed (CRLF equivalence and content mismatch rejection).'
} finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
    }
}
