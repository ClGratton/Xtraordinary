param(
    [int]$Iterations = 5,
    [int]$TimeoutSeconds = 20,
    [string]$AndroidPackage = "com.xteink.companion",
    [ValidateSet("Background", "ProcessDeath")]
    [string]$Mode = "Background"
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "use-toolchains.ps1")
$Adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"

if ($Iterations -lt 1) { throw "Iterations must be at least 1." }
if ($TimeoutSeconds -lt 5) { throw "TimeoutSeconds must be at least 5." }
if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB is missing: $Adb" }

$ConnectedPhones = @(& $Adb devices | Select-String "`tdevice$")
if ($ConnectedPhones.Count -ne 1) {
    throw "Expected exactly one ADB phone; found $($ConnectedPhones.Count)."
}

for ($Iteration = 1; $Iteration -le $Iterations; $Iteration++) {
    Write-Host "Reconnect cycle $Iteration/$Iterations ($Mode)"
    if ($Mode -eq "Background") {
        & $Adb shell input keyevent HOME
        if ($LASTEXITCODE -ne 0) { throw "Could not background the app in cycle $Iteration." }
        # ViewModel uses a 1.5-second idle grace before clean GATT disconnect.
        Start-Sleep -Seconds 3
    } else {
        & $Adb shell am force-stop $AndroidPackage
        if ($LASTEXITCODE -ne 0) { throw "Could not stop the app in cycle $Iteration." }
        # An abrupt process death may leave the peripheral link alive until its
        # ten-second supervision timeout. Do not race a new direct connection.
        Start-Sleep -Seconds 12
    }
    & $Adb logcat -c
    & $Adb shell monkey -p $AndroidPackage -c android.intent.category.LAUNCHER 1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not launch the app in cycle $Iteration." }

    $Deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $Connected = $false
    $Retained = $false
    do {
        Start-Sleep -Milliseconds 500
        $Logs = (& $Adb logcat -d -v brief -s XteinkBle:V AndroidRuntime:E) -join "`n"
        $Connected =
            $Logs -match "using API 37 GATT connection settings auto=false" -and
            $Logs -match "GATT state status=0 state=2" -and
            $Logs -match "notifications subscribed status=0" -and
            $Logs -match "received type=Capabilities"
        if (-not $Connected -and $Mode -eq "Background") {
            $BluetoothDump = (& $Adb shell dumpsys bluetooth_manager) -join "`n"
            $EscapedPackage = [Regex]::Escape($AndroidPackage)
            $Retained = $BluetoothDump -match "(?s)appName:\s+$EscapedPackage.*?Connection\(connId="
        }
    } while (-not $Connected -and -not $Retained -and [DateTime]::UtcNow -lt $Deadline)

    if (-not $Connected -and -not $Retained) {
        Write-Host $Logs
        throw "Cycle $Iteration did not complete the capabilities handshake within $TimeoutSeconds seconds. Bond and Bluetooth state were left unchanged."
    }

    if ($Connected) {
        Write-Host "Cycle $Iteration passed: API 37 GATT, encrypted service setup, notifications, and capabilities."
    } else {
        Write-Host "Cycle $Iteration passed: the required background GATT transport remained active."
    }
}

Write-Host "All $Iterations $Mode reconnect cycles passed without deleting the bond or toggling Bluetooth."
