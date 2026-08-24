param(
    [string]$Port = "COM7",
    [string]$FirmwarePath = "firmware\.pio\build\x3_companion_release\firmware.bin",
    [string]$AndroidPackage = "com.xteink.companion",
    [int]$BootWaitSeconds = 8,
    [switch]$SkipAndroidRelease,
    [switch]$NoLaunch
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "use-toolchains.ps1")
. (Join-Path $PSScriptRoot "resolve-xtraordinary-deployment-targets.ps1")

$ResolvedFirmware = (Resolve-Path (Join-Path $ProjectRoot $FirmwarePath)).Path
$X3UsbTarget = Get-XtraordinaryX3UsbTarget
if ($null -eq $X3UsbTarget) {
    throw 'No present X3 USB/JTAG composite or serial interface with VID_303A:1001 was found. Do not infer absence from serial ports alone.'
}
if ($Port -ne $X3UsbTarget.Port) {
    throw "Requested X3 serial port $Port does not match the present VID_303A:1001 target $($X3UsbTarget.Port)."
}
$AvailablePorts = [System.IO.Ports.SerialPort]::GetPortNames()
if ($Port -notin $AvailablePorts) {
    throw "X3 serial port $Port is not present. Available ports: $($AvailablePorts -join ', ')"
}

$Adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$Python = Join-Path $env:PLATFORMIO_CORE_DIR "penv\Scripts\python.exe"
$Esptool = Join-Path $env:PLATFORMIO_CORE_DIR "packages\tool-esptoolpy\esptool.py"
foreach ($Tool in @($Adb, $Python, $Esptool)) {
    if (-not (Test-Path -LiteralPath $Tool)) {
        throw "Required project tool is missing: $Tool"
    }
}

$Artifact = Get-Item -LiteralPath $ResolvedFirmware
$Hash = Get-FileHash -LiteralPath $ResolvedFirmware -Algorithm SHA256
Write-Host "Firmware: $($Artifact.FullName)"
Write-Host "Size: $($Artifact.Length) bytes"
Write-Host "SHA-256: $($Hash.Hash)"

if (-not $SkipAndroidRelease) {
    $ConnectedPhones = @(Get-XtraordinaryAdbDevice -Adb $Adb)
    if ($ConnectedPhones.Count -ne 1) {
        throw "Expected exactly one ADB phone after current mDNS discovery before resetting X3; found $($ConnectedPhones.Count)."
    }
    Write-Host "Requesting a graceful Android GATT release..."
    & $Adb logcat -c
    & $Adb shell am start `
        -n "$AndroidPackage/.MainActivity" `
        -a "$AndroidPackage.action.PREPARE_X3_RESET" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not request Android GATT release. X3 was not flashed."
    }
    $ReleaseDeadline = [DateTime]::UtcNow.AddSeconds(10)
    $ReleaseReady = $false
    do {
        Start-Sleep -Milliseconds 250
        $ReleaseLogs = (& $Adb logcat -d -v brief -s XteinkDeploy:I AndroidRuntime:E) -join "`n"
        $ReleaseReady = $ReleaseLogs -match "PERIPHERAL_RESET_READY"
        if ($ReleaseLogs -match "PERIPHERAL_RESET_FAILED") {
            throw "The companion app could not release GATT. X3 was not flashed."
        }
    } while (-not $ReleaseReady -and [DateTime]::UtcNow -lt $ReleaseDeadline)
    if (-not $ReleaseReady) {
        throw "The companion app did not confirm GATT release within 10 seconds. X3 was not flashed."
    }
    Write-Host "Android confirmed GATT release; stopping $AndroidPackage..."
    & $Adb shell am force-stop $AndroidPackage
    if ($LASTEXITCODE -ne 0) {
        throw "Could not stop the Android companion app. X3 was not flashed."
    }
    Start-Sleep -Seconds 1
}

Write-Host "Flashing only the X3 application partition on $Port..."
& $Python $Esptool `
    --chip esp32c3 `
    --port $Port `
    --baud 921600 `
    write-flash `
    --flash-mode dio `
    --flash-freq 80m `
    --flash-size 16MB `
    0x10000 $ResolvedFirmware
if ($LASTEXITCODE -ne 0) {
    throw "esptool failed with exit code $LASTEXITCODE. Do not treat this flash as successful."
}

Write-Host "Flash verified. Waiting $BootWaitSeconds seconds for X3 startup..."
Start-Sleep -Seconds $BootWaitSeconds
if (-not $NoLaunch -and -not $SkipAndroidRelease) {
    & $Adb shell monkey -p $AndroidPackage -c android.intent.category.LAUNCHER 1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Firmware flashed, but the Android app could not be relaunched."
    }
    Write-Host "Companion app launched; verify the GATT handshake before declaring deployment complete."
}
