param(
    [int]$TimeoutSeconds = 15
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "use-toolchains.ps1")

$Adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $Adb)) {
    throw "Required project ADB is missing: $Adb"
}

$ConnectedPhones = @(& $Adb devices | Select-String "`tdevice$")
if ($ConnectedPhones.Count -ne 1) {
    throw "Expected exactly one ADB phone; found $($ConnectedPhones.Count)."
}

& $Adb logcat -c
& $Adb shell am start `
    -n "com.xteink.companion/.MainActivity" `
    -a "com.xteink.companion.action.READ_X3_DIAGNOSTICS" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "Could not start the X3 diagnostics request."
}

$Deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Milliseconds 250
    $Logs = (& $Adb logcat -d -v brief -s XteinkDeploy:I AndroidRuntime:E) -join "`n"
    if ($Logs -match "X3_DIAGNOSTICS_FAILED") {
        throw $Logs
    }
    if ($Logs -match "X3_DIAGNOSTICS_END") {
        $Logs
        exit 0
    }
} while ([DateTime]::UtcNow -lt $Deadline)

throw "Timed out waiting for X3 diagnostics."
