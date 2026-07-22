param(
    [switch]$SkipFirmwareBuild
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$ToolsRoot = Join-Path $ProjectRoot ".tools"
$Downloads = Join-Path $ToolsRoot "downloads"
$AndroidSdk = Join-Path $ToolsRoot "android-sdk"
$JdkHome = Join-Path $ToolsRoot "jdk-17"
$GradleHome = Join-Path $ToolsRoot "gradle-9.5.0"
$PlatformIoVenv = Join-Path $ToolsRoot "platformio-venv"
$PlatformIoCore = Join-Path $ToolsRoot "platformio-core"

New-Item -ItemType Directory -Force -Path $Downloads | Out-Null

function Get-VerifiedFile {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$Destination,
        [string]$Sha256
    )

    if (-not (Test-Path $Destination)) {
        Write-Host "Downloading $Uri"
        Invoke-WebRequest -Uri $Uri -OutFile $Destination
    }

    if ($Sha256) {
        $Actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $Destination).Hash.ToLowerInvariant()
        if ($Actual -ne $Sha256.ToLowerInvariant()) {
            throw "Checksum mismatch for $Destination. Expected $Sha256, got $Actual."
        }
    }
}

function Expand-ToolArchive {
    param(
        [Parameter(Mandatory = $true)][string]$Archive,
        [Parameter(Mandatory = $true)][string]$Destination,
        [Parameter(Mandatory = $true)][string]$ExpectedDirectoryName
    )

    if (Test-Path $Destination) {
        return
    }

    $Staging = Join-Path $ToolsRoot ("staging-" + [System.IO.Path]::GetRandomFileName())
    New-Item -ItemType Directory -Force -Path $Staging | Out-Null
    try {
        Expand-Archive -LiteralPath $Archive -DestinationPath $Staging
        $Expanded = Join-Path $Staging $ExpectedDirectoryName
        if (-not (Test-Path $Expanded)) {
            throw "Archive $Archive did not contain $ExpectedDirectoryName."
        }
        Move-Item -LiteralPath $Expanded -Destination $Destination
    }
    finally {
        if (Test-Path $Staging) {
            Remove-Item -LiteralPath $Staging -Recurse -Force
        }
    }
}

# Android Gradle Plugin 9.x requires JDK 17 or newer. Pinning 17 keeps builds reproducible.
$JdkArchive = Join-Path $Downloads "temurin-jdk17.zip"
if (-not (Test-Path (Join-Path $JdkHome "bin\java.exe"))) {
    Get-VerifiedFile `
        -Uri "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk" `
        -Destination $JdkArchive

    $Staging = Join-Path $ToolsRoot ("staging-" + [System.IO.Path]::GetRandomFileName())
    New-Item -ItemType Directory -Force -Path $Staging | Out-Null
    try {
        Expand-Archive -LiteralPath $JdkArchive -DestinationPath $Staging
        $ExpandedJdk = Get-ChildItem -LiteralPath $Staging -Directory | Select-Object -First 1
        if (-not $ExpandedJdk) {
            throw "The JDK archive did not contain a top-level directory."
        }
        Move-Item -LiteralPath $ExpandedJdk.FullName -Destination $JdkHome
    }
    finally {
        if (Test-Path $Staging) {
            Remove-Item -LiteralPath $Staging -Recurse -Force
        }
    }
}

$env:JAVA_HOME = $JdkHome
$env:Path = "$JdkHome\bin;$env:Path"
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$env:GIT_CONFIG_COUNT = "2"
$env:GIT_CONFIG_KEY_0 = "safe.directory"
$env:GIT_CONFIG_VALUE_0 = $ProjectRoot.Replace("\", "/")
$env:GIT_CONFIG_KEY_1 = "safe.directory"
$env:GIT_CONFIG_VALUE_1 = (Join-Path $ProjectRoot "firmware").Replace("\", "/")

# Latest official Android command-line tools package listed by Android Studio downloads.
$CommandLineToolsArchive = Join-Path $Downloads "commandlinetools-win-15859902_latest.zip"
$CommandLineToolsSha256 = "90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a"
$SdkManager = Join-Path $AndroidSdk "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $SdkManager)) {
    Get-VerifiedFile `
        -Uri "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip" `
        -Destination $CommandLineToolsArchive `
        -Sha256 $CommandLineToolsSha256

    $Staging = Join-Path $ToolsRoot ("staging-" + [System.IO.Path]::GetRandomFileName())
    New-Item -ItemType Directory -Force -Path $Staging | Out-Null
    try {
        Expand-Archive -LiteralPath $CommandLineToolsArchive -DestinationPath $Staging
        $Latest = Join-Path $AndroidSdk "cmdline-tools\latest"
        New-Item -ItemType Directory -Force -Path (Split-Path $Latest -Parent) | Out-Null
        Move-Item -LiteralPath (Join-Path $Staging "cmdline-tools") -Destination $Latest
    }
    finally {
        if (Test-Path $Staging) {
            Remove-Item -LiteralPath $Staging -Recurse -Force
        }
    }
}

$env:ANDROID_HOME = $AndroidSdk
$env:ANDROID_SDK_ROOT = $AndroidSdk

Write-Host "Accepting Android SDK licenses"
1..100 | ForEach-Object { "y" } | & $SdkManager --sdk_root=$AndroidSdk --licenses | Out-Host

Write-Host "Installing Android SDK packages"
$RequiredSdkArtifacts = @(
    (Join-Path $AndroidSdk "platform-tools\adb.exe"),
    (Join-Path $AndroidSdk "platforms\android-36\android.jar"),
    (Join-Path $AndroidSdk "platforms\android-37.1\android.jar"),
    (Join-Path $AndroidSdk "build-tools\36.0.0\aapt2.exe")
)

for ($Attempt = 1; $Attempt -le 3; $Attempt++) {
    & $SdkManager --sdk_root=$AndroidSdk `
        "platform-tools" `
        "platforms;android-36" `
        "platforms;android-37.1" `
        "build-tools;36.0.0"

    $MissingSdkArtifacts = @($RequiredSdkArtifacts | Where-Object { -not (Test-Path $_) })
    if ($MissingSdkArtifacts.Count -eq 0) {
        break
    }

    if ($Attempt -eq 3) {
        throw "Android SDK installation is incomplete. Missing: $($MissingSdkArtifacts -join ', ')"
    }

    Write-Warning "Android SDK download was incomplete; clearing only partial package data before retry $($Attempt + 1)."
    $IncompletePlatform = Join-Path $AndroidSdk "platforms\android-36"
    if ((Test-Path $IncompletePlatform) -and -not (Test-Path (Join-Path $IncompletePlatform "android.jar"))) {
        Remove-Item -LiteralPath $IncompletePlatform -Recurse -Force
    }
    $IncompletePlatform37 = Join-Path $AndroidSdk "platforms\android-37.1"
    if ((Test-Path $IncompletePlatform37) -and -not (Test-Path (Join-Path $IncompletePlatform37 "android.jar"))) {
        Remove-Item -LiteralPath $IncompletePlatform37 -Recurse -Force
    }
    $SdkTemp = Join-Path $AndroidSdk ".temp"
    if (Test-Path $SdkTemp) {
        Remove-Item -LiteralPath $SdkTemp -Recurse -Force
    }
}

# AGP 9.3 requires Gradle 9.5.0.
$GradleArchive = Join-Path $Downloads "gradle-9.5.0-bin.zip"
if (-not (Test-Path (Join-Path $GradleHome "bin\gradle.bat"))) {
    $GradleShaUri = "https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256"
    $GradleShaResponse = Invoke-WebRequest -Uri $GradleShaUri
    if ($GradleShaResponse.Content -is [byte[]]) {
        $GradleSha = ([System.Text.Encoding]::UTF8.GetString($GradleShaResponse.Content)).Trim()
    }
    else {
        $GradleSha = ([string]$GradleShaResponse.Content).Trim()
    }
    Get-VerifiedFile `
        -Uri "https://services.gradle.org/distributions/gradle-9.5.0-bin.zip" `
        -Destination $GradleArchive `
        -Sha256 $GradleSha
    Expand-ToolArchive `
        -Archive $GradleArchive `
        -Destination $GradleHome `
        -ExpectedDirectoryName "gradle-9.5.0"
}

$BundledPython = "C:\Users\cla20\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe"
if (-not (Test-Path $BundledPython)) {
    throw "Codex bundled Python was not found at $BundledPython."
}

if (-not (Test-Path (Join-Path $PlatformIoVenv "Scripts\python.exe"))) {
    & $BundledPython -m venv $PlatformIoVenv
}

$VenvPython = Join-Path $PlatformIoVenv "Scripts\python.exe"
$PlatformIo = Join-Path $PlatformIoVenv "Scripts\pio.exe"
$env:PLATFORMIO_CORE_DIR = $PlatformIoCore
New-Item -ItemType Directory -Force -Path $PlatformIoCore | Out-Null
& $VenvPython -m pip install --upgrade pip
& $VenvPython -m pip install platformio -r (Join-Path $ProjectRoot "firmware\requirements.txt")

if (-not $SkipFirmwareBuild) {
    Write-Host "Building the unmodified CrossPoint firmware baseline"
    & $PlatformIo run --project-dir (Join-Path $ProjectRoot "firmware") -j 1
}

Write-Host "Toolchains are ready. Run scripts\use-toolchains.ps1 in a new shell."
