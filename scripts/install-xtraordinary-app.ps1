param(
    [string]$ApkPath = "app\build\outputs\apk\community\debug\app-community-debug.apk",
    [string]$AndroidPackage = "com.xteink.companion",
    [switch]$NoLaunch
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "use-toolchains.ps1")
. (Join-Path $PSScriptRoot "resolve-xtraordinary-deployment-targets.ps1")

$ResolvedApk = (Resolve-Path (Join-Path $ProjectRoot $ApkPath)).Path
$Adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $Adb)) {
    throw "Required project ADB is missing: $Adb"
}

$ConnectedPhones = @(Get-XtraordinaryAdbDevice -Adb $Adb)
if ($ConnectedPhones.Count -ne 1) {
    throw "Expected exactly one ADB phone after current mDNS discovery; found $($ConnectedPhones.Count)."
}

$Artifact = Get-Item -LiteralPath $ResolvedApk
$Hash = Get-FileHash -LiteralPath $ResolvedApk -Algorithm SHA256
Write-Host "APK: $($Artifact.FullName)"
Write-Host "Size: $($Artifact.Length) bytes"
Write-Host "SHA-256: $($Hash.Hash)"

$Installed = (& $Adb shell pm path $AndroidPackage 2>$null) -match '^package:'
if ($Installed) {
    # `adb install -r` terminates the old package. If that package owns a GATT
    # client, a raw replacement can leave Android's native direct-connect
    # holder alive with no app callback. Reuse the same explicit release
    # contract as the safe X3 flasher before Package Manager stops the process.
    Write-Host "Requesting a graceful Android GATT release before replacement..."
    & $Adb logcat -c
    & $Adb shell am start `
        -n "$AndroidPackage/.MainActivity" `
        -a "$AndroidPackage.action.PREPARE_X3_RESET" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not request Android GATT release. APK was not installed."
    }
    $ReleaseDeadline = [DateTime]::UtcNow.AddSeconds(10)
    $ReleaseReady = $false
    do {
        Start-Sleep -Milliseconds 250
        $ReleaseLogs = (& $Adb logcat -d -v brief -s XteinkDeploy:I AndroidRuntime:E) -join "`n"
        $ReleaseReady = $ReleaseLogs -match "PERIPHERAL_RESET_READY"
        if ($ReleaseLogs -match "PERIPHERAL_RESET_FAILED") {
            throw "The installed companion app could not release GATT. APK was not installed."
        }
    } while (-not $ReleaseReady -and [DateTime]::UtcNow -lt $ReleaseDeadline)
    if (-not $ReleaseReady) {
        throw "The installed companion app did not confirm GATT release within 10 seconds. APK was not installed."
    }
    & $Adb shell am force-stop $AndroidPackage
    if ($LASTEXITCODE -ne 0) {
        throw "Could not stop the released companion app. APK was not installed."
    }
    Start-Sleep -Seconds 1
}

Write-Host "Installing APK with app data retained..."
& $Adb install -r $ResolvedApk
if ($LASTEXITCODE -ne 0) {
    throw "ADB install failed with exit code $LASTEXITCODE."
}

$InstalledVersion = (& $Adb shell dumpsys package $AndroidPackage | Select-String 'versionName=' | Select-Object -First 1).Line.Trim()
if (-not $InstalledVersion) {
    throw "APK installed but Package Manager did not report a version."
}
Write-Host "Installed $InstalledVersion"

if (-not $NoLaunch) {
    & $Adb shell monkey -p $AndroidPackage -c android.intent.category.LAUNCHER 1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "APK installed, but the companion app could not be relaunched."
    }
    Write-Host "Companion app launched."
}
