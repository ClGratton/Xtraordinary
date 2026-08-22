param(
    [switch]$Update,
    [string]$NoticeRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$noticeRoot = if ([string]::IsNullOrWhiteSpace($NoticeRoot)) {
    Join-Path $repoRoot 'release-notices\firmware'
} else {
    $NoticeRoot
}
$manifestPath = Join-Path $noticeRoot 'MANIFEST.sha256'

function Get-CanonicalTextSha256 {
    param([Parameter(Mandatory)][string]$Path)

    # The fixed firmware notice inventory is text. Git may materialize its LF
    # repository content as CRLF on Windows, so hash a line-ending-normalized
    # byte stream. Do not use this for arbitrary release payloads or binaries.
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $canonical = [System.Collections.Generic.List[byte]]::new($bytes.Length)
    for ($index = 0; $index -lt $bytes.Length; $index++) {
        if ($bytes[$index] -eq 0x0d -and $index + 1 -lt $bytes.Length -and $bytes[$index + 1] -eq 0x0a) {
            continue
        }
        $canonical.Add($bytes[$index])
    }

    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha256.ComputeHash($canonical.ToArray())) -replace '-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

$requiredFiles = @(
    'Apache-NimBLE-Apache-2.0.txt',
    'Apache-NimBLE-NOTICE.txt',
    'ArduinoJson-MIT.txt',
    'ArduinoWebSockets-LGPL-2.1.txt',
    'CrossInk-MIT.txt',
    'CrossPoint-MIT.txt',
    'Expat-MIT.txt',
    'Expat-siphash-CC0-1.0.txt',
    'JPEGDEC-Apache-2.0.txt',
    'libb64-license.txt',
    'NimBLE-Arduino-Apache-2.0.txt',
    'NimBLE-Arduino-NOTICE.txt',
    'NotoSans-OFL-1.1.txt',
    'NotoSansHebrew-OFL-1.1.txt',
    'NotoSerif-OFL-1.1.txt',
    'Open-X4-SDK-MIT.txt',
    'OpenDyslexic-OFL-1.1.txt',
    'PNGdec-Apache-2.0.txt',
    'QRCode-MIT.txt',
    'README.md',
    'SdFat-MIT.md',
    'TinyCrypt-license.txt',
    'Ubuntu-Font-Licence-1.0.txt',
    'uzlib-license.txt',
    'Xtraordinary-MIT.txt'
)

if (-not (Test-Path -LiteralPath $noticeRoot)) {
    throw "Firmware release-notices directory is missing: $noticeRoot"
}

$actualFiles = @(
    Get-ChildItem -LiteralPath $noticeRoot -File |
        Where-Object Name -ne 'MANIFEST.sha256' |
        Sort-Object Name |
        ForEach-Object Name
)
$missing = @($requiredFiles | Where-Object { $_ -notin $actualFiles })
$unexpected = @($actualFiles | Where-Object { $_ -notin $requiredFiles })
if ($missing.Count -gt 0 -or $unexpected.Count -gt 0) {
    throw "Firmware notice inventory mismatch. Missing: $($missing -join ', '); unexpected: $($unexpected -join ', ')"
}

$expectedLines = @(
    $requiredFiles | Sort-Object | ForEach-Object {
        $hash = Get-CanonicalTextSha256 -Path (Join-Path $noticeRoot $_)
        "$hash  $_"
    }
)

if ($Update) {
    Set-Content -LiteralPath $manifestPath -Value $expectedLines -Encoding utf8
}

if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Firmware notice manifest is missing. Run this script once with -Update after reviewing the exact files."
}

$actualLines = @(Get-Content -LiteralPath $manifestPath)
if (Compare-Object -ReferenceObject $expectedLines -DifferenceObject $actualLines) {
    throw "Firmware notice manifest is stale. Review the changed licence payload and run this script with -Update."
}

Write-Host "Firmware release-notices pack passed ($($requiredFiles.Count) files)."
