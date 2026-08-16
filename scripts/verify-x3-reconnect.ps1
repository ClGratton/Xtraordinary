param(
    [int]$Iterations = 5,
    [int]$TimeoutSeconds = 20,
    [int]$ExpectedDiscoveryIntervalSeconds = 30,
    [int]$DisconnectTimeoutSeconds = 12,
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
if ($ExpectedDiscoveryIntervalSeconds -lt 1) { throw "ExpectedDiscoveryIntervalSeconds must be at least 1." }
if ($DisconnectTimeoutSeconds -lt 5) { throw "DisconnectTimeoutSeconds must be at least 5." }
if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB is missing: $Adb" }

$ConnectedPhones = @(& $Adb devices | Select-String "`tdevice$")
if ($ConnectedPhones.Count -ne 1) {
    throw "Expected exactly one ADB phone; found $($ConnectedPhones.Count)."
}

function Test-X3AclConnected {
    $BluetoothDump = (& $Adb shell dumpsys bluetooth_manager) -join "`n"
    $X3DeviceLine = ($BluetoothDump -split "`n" |
        Where-Object { $_ -match 'XTEINK Companion' } |
        Select-Object -First 1)
    return $null -ne $X3DeviceLine -and $X3DeviceLine -match '\[\s*ACL BR/EDR:[YN] LE:Y\]'
}

for ($Iteration = 1; $Iteration -le $Iterations; $Iteration++) {
    Write-Host "Reconnect cycle $Iteration/$Iterations ($Mode)"
    if ($Mode -eq "Background") {
        & $Adb shell input keyevent HOME
        if ($LASTEXITCODE -ne 0) { throw "Could not background the app in cycle $Iteration." }
        # The ViewModel owns a 1.5-second idle grace, but Android may keep the
        # underlying ACL alive for several more seconds while its GATT channels
        # close. Starting a second direct connection during that teardown can
        # produce a connect request with no callback. Observe the real X3 ACL
        # boundary instead of guessing a fixed delay.
        $DisconnectDeadline = [DateTime]::UtcNow.AddSeconds($DisconnectTimeoutSeconds)
        while ((Test-X3AclConnected) -and [DateTime]::UtcNow -lt $DisconnectDeadline) {
            Start-Sleep -Milliseconds 250
        }
        if (Test-X3AclConnected) {
            throw "Cycle $Iteration did not release the X3 LE ACL within $DisconnectTimeoutSeconds seconds. Bond and Bluetooth state were left unchanged."
        }
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

    # A background/process restart must remain valid after X3 has left its fast
    # discovery window. Never make the verifier's deadline shorter than one
    # configured standby pulse plus bounded GATT setup headroom.
    $EffectiveTimeoutSeconds = [Math]::Max($TimeoutSeconds, $ExpectedDiscoveryIntervalSeconds + 10)
    $Deadline = [DateTime]::UtcNow.AddSeconds($EffectiveTimeoutSeconds)
    $Connected = $false
    do {
        Start-Sleep -Milliseconds 500
        $Logs = (& $Adb logcat -d -v brief -s XteinkBle:V AndroidRuntime:E) -join "`n"
        $Connected =
            $Logs -match "using API 37 GATT connection settings auto=false" -and
            $Logs -match "GATT state status=0 state=2" -and
            $Logs -match "notifications subscribed status=0" -and
            $Logs -match "received type=Capabilities"
    } while (-not $Connected -and [DateTime]::UtcNow -lt $Deadline)

    if (-not $Connected) {
        Write-Host $Logs
        throw "Cycle $Iteration did not complete the capabilities handshake within $EffectiveTimeoutSeconds seconds. Bond and Bluetooth state were left unchanged."
    }

    Write-Host "Cycle $Iteration passed: API 37 GATT, encrypted service setup, notifications, and capabilities."
}

Write-Host "All $Iterations $Mode reconnect cycles passed without deleting the bond or toggling Bluetooth."
