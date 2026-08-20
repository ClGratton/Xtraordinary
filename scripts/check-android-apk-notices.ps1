param(
    [Parameter(Mandatory)] [string]$ApkPath
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$noticeRoot = Join-Path $repoRoot 'release-notices'
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($resolvedApk)
try {
    $entries = @{}
    foreach ($entry in $archive.Entries) {
        $entries[$entry.FullName] = $entry
    }
    foreach ($file in Get-ChildItem -LiteralPath $noticeRoot -Recurse -File) {
        $relative = $file.FullName.Substring($noticeRoot.Length + 1).Replace('\', '/')
        $entryName = "assets/$relative"
        if (-not $entries.ContainsKey($entryName)) {
            throw "APK omits required release notice: $entryName"
        }
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try {
            $stream = $entries[$entryName].Open()
            try {
                $apkHash = [Convert]::ToHexString($sha.ComputeHash($stream)).ToLowerInvariant()
            } finally {
                $stream.Dispose()
            }
        } finally {
            $sha.Dispose()
        }
        $sourceHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($apkHash -ne $sourceHash) {
            throw "APK release notice differs from the reviewed source pack: $entryName"
        }
    }
} finally {
    $archive.Dispose()
}

Write-Host "APK contains the complete hash-identical Android and firmware release-notice packs: $resolvedApk"
