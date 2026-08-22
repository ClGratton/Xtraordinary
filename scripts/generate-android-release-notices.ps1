param(
    [switch]$Check,
    [string]$NoticeRoot
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot '.tools\gradle-9.5.0\bin\gradle.bat'
$toolchainScript = Join-Path $PSScriptRoot 'use-toolchains.ps1'
$generatedRoot = Join-Path $repoRoot 'app\build\legal\runtime-dependencies'
$noticeRoot = if ([string]::IsNullOrWhiteSpace($NoticeRoot)) {
    Join-Path $repoRoot 'release-notices\android'
} else {
    $NoticeRoot
}
$expectedFiles = @(
    'communityReleaseRuntimeClasspath.tsv',
    'communityReleaseRuntimeLicenses.tsv',
    'playReleaseRuntimeClasspath.tsv',
    'playReleaseRuntimeLicenses.tsv'
)
$staticNoticeFiles = @(
    'Apache-2.0.txt',
    'CheckerFramework-MIT.txt',
    'GOOGLE-COMPONENT-TERMS.md',
    'license-overrides.tsv',
    'README.md'
)
$manifestName = 'MANIFEST.sha256'

function Write-LicenseMetadataReport {
    param(
        [Parameter(Mandatory)] [string]$DependencyReport,
        [Parameter(Mandatory)] [string]$Destination
    )

    $gradleModuleCache = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1'
    $rows = Import-Csv -LiteralPath $DependencyReport -Delimiter "`t"
    $lines = @("group`tmodule`tversion`tlicenseNames`tlicenseUrls`tpomSha256")
    foreach ($row in $rows) {
        $versionRoot = Join-Path $gradleModuleCache "$($row.group)\$($row.module)\$($row.version)"
        $pom = Get-ChildItem -LiteralPath $versionRoot -Recurse -File -Filter '*.pom' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        $licenseNames = @()
        $licenseUrls = @()
        $pomHash = 'POM_NOT_FOUND'
        if ($null -ne $pom) {
            [xml]$pomXml = Get-Content -LiteralPath $pom.FullName -Raw
            $pomHash = (Get-FileHash -LiteralPath $pom.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            foreach ($license in @($pomXml.SelectNodes("/*[local-name()='project']/*[local-name()='licenses']/*[local-name()='license']"))) {
                $nameNode = $license.SelectSingleNode("*[local-name()='name']")
                $urlNode = $license.SelectSingleNode("*[local-name()='url']")
                if ($null -ne $nameNode -and -not [string]::IsNullOrWhiteSpace($nameNode.InnerText)) {
                    $licenseNames += ($nameNode.InnerText -replace '[\t\r\n]+', ' ').Trim()
                }
                if ($null -ne $urlNode -and -not [string]::IsNullOrWhiteSpace($urlNode.InnerText)) {
                    $licenseUrls += ($urlNode.InnerText -replace '[\t\r\n]+', ' ').Trim()
                }
            }
        }
        if ($licenseNames.Count -eq 0) {
            $licenseNames = @('UNDECLARED_IN_POM')
        }
        if ($licenseUrls.Count -eq 0) {
            $licenseUrls = @('UNDECLARED_IN_POM')
        }
        $lines += "$($row.group)`t$($row.module)`t$($row.version)`t$(($licenseNames | Sort-Object -Unique) -join ' | ')`t$(($licenseUrls | Sort-Object -Unique) -join ' | ')`t$pomHash"
    }
    Set-Content -LiteralPath $Destination -Value $lines -Encoding utf8
}

function Assert-UndeclaredPomOverrides {
    param([Parameter(Mandatory)] [string[]]$LicenseReports)

    $overridePath = Join-Path $noticeRoot 'license-overrides.tsv'
    if (-not (Test-Path -LiteralPath $overridePath)) {
        throw "Android POM override register is missing: $overridePath"
    }
    $overrides = @(Import-Csv -LiteralPath $overridePath -Delimiter "`t")
    $overrideByCoordinate = @{}
    foreach ($override in $overrides) {
        $coordinate = "$($override.group):$($override.module):$($override.version)"
        if ($overrideByCoordinate.ContainsKey($coordinate)) {
            throw "Duplicate Android POM override: $coordinate"
        }
        if ([string]::IsNullOrWhiteSpace($override.resolution) -or
            -not $override.evidenceUrl.StartsWith('https://')) {
            throw "Android POM override lacks a resolution or HTTPS evidence URL: $coordinate"
        }
        $overrideByCoordinate[$coordinate] = $override
    }

    $undeclared = @{}
    foreach ($report in $LicenseReports) {
        foreach ($row in @(Import-Csv -LiteralPath $report -Delimiter "`t" |
                Where-Object { $_.licenseNames -eq 'UNDECLARED_IN_POM' })) {
            $coordinate = "$($row.group):$($row.module):$($row.version)"
            $undeclared[$coordinate] = $true
            if (-not $overrideByCoordinate.ContainsKey($coordinate)) {
                throw "Resolved Android dependency has no explicit POM-license override: $coordinate"
            }
        }
    }
    foreach ($coordinate in $overrideByCoordinate.Keys) {
        if (-not $undeclared.ContainsKey($coordinate)) {
            throw "Android POM override is stale or does not match the current release graphs: $coordinate"
        }
    }
}

function Get-PackManifestLines {
    Get-ChildItem -LiteralPath $noticeRoot -File |
        Where-Object { $_.Name -ne $manifestName } |
        Sort-Object Name |
        ForEach-Object {
            $hash = Get-CanonicalTextSha256 -Path $_.FullName
            "$hash *$($_.Name)"
        }
}

function Get-CanonicalTextSha256 {
    param([Parameter(Mandatory)][string]$Path)

    # This fixed Android notice inventory contains text reports and notice
    # documents. Git may materialize repository LF bytes as CRLF on Windows,
    # so only this text pack hashes canonical LF bytes. Do not apply this to
    # APKs, firmware images, or other binary release payloads.
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

function Assert-PackManifest {
    $manifestPath = Join-Path $noticeRoot $manifestName
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        throw "Android release-notices manifest is missing: $manifestPath"
    }
    $expected = @(Get-PackManifestLines)
    $actual = @((Get-Content -LiteralPath $manifestPath) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if (($expected -join "`n") -ne ($actual -join "`n")) {
        throw "Android release-notices manifest is stale. Regenerate and review release-notices/android."
    }
}

if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Bundled Gradle was not found at $gradle"
}
. $toolchainScript

Push-Location $repoRoot
try {
    & $gradle --no-daemon --no-configuration-cache --console=plain --quiet :app:writeLegalRuntimeDependencyReports
    if ($LASTEXITCODE -ne 0) {
        throw "Android legal dependency resolution failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-LicenseMetadataReport `
    -DependencyReport (Join-Path $generatedRoot 'communityReleaseRuntimeClasspath.tsv') `
    -Destination (Join-Path $generatedRoot 'communityReleaseRuntimeLicenses.tsv')
Write-LicenseMetadataReport `
    -DependencyReport (Join-Path $generatedRoot 'playReleaseRuntimeClasspath.tsv') `
    -Destination (Join-Path $generatedRoot 'playReleaseRuntimeLicenses.tsv')

foreach ($name in $staticNoticeFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $noticeRoot $name))) {
        throw "Required Android release notice is missing: $name"
    }
}
Assert-UndeclaredPomOverrides -LicenseReports @(
    (Join-Path $generatedRoot 'communityReleaseRuntimeLicenses.tsv'),
    (Join-Path $generatedRoot 'playReleaseRuntimeLicenses.tsv')
)

if ($Check) {
    foreach ($name in $expectedFiles) {
        $generated = Join-Path $generatedRoot $name
        $tracked = Join-Path $noticeRoot $name
        if (-not (Test-Path -LiteralPath $tracked)) {
            throw "Tracked Android dependency inventory is missing: $tracked"
        }
        if ((Get-CanonicalTextSha256 -Path $generated) -ne
            (Get-CanonicalTextSha256 -Path $tracked)) {
            throw "Android dependency inventory is stale for $name. Regenerate and review release-notices/android."
        }
    }
    Assert-PackManifest
    Write-Host "Android release notices match the resolved Community and Play graphs and manifest."
    exit 0
}

New-Item -ItemType Directory -Path $noticeRoot -Force | Out-Null
foreach ($name in $expectedFiles) {
    Copy-Item -LiteralPath (Join-Path $generatedRoot $name) -Destination (Join-Path $noticeRoot $name) -Force
}
$manifestPath = Join-Path $noticeRoot $manifestName
Set-Content -LiteralPath $manifestPath -Value @(Get-PackManifestLines) -Encoding utf8
Write-Host "Updated Android Community and Play runtime dependency inventories and manifest."
