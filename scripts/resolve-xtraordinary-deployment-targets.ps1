param(
    [string]$AdbPath,
    [switch]$RequireAdbDevice,
    [switch]$RequireX3Usb
)

$ErrorActionPreference = 'Stop'

function Get-AdbTlsConnectEndpoints {
    param([string[]]$MdnsLines)

    return @($MdnsLines |
        ForEach-Object {
            if ($_ -match '_adb-tls-connect\._tcp.*?(?<endpoint>(?:\d{1,3}\.){3}\d{1,3}:\d+)') {
                $Matches.endpoint
            }
        } |
        Select-Object -Unique)
}

function Get-X3UsbTargetFromPnpRecords {
    param([object[]]$PnpRecords)

    $x3Records = @($PnpRecords | Where-Object {
        $_.Status -eq 'OK' -and $_.InstanceId -match 'VID_303A&PID_1001'
    })
    if ($x3Records.Count -eq 0) { return $null }

    $serialRecord = @($x3Records | Where-Object {
        $_.Class -eq 'Ports' -and $_.FriendlyName -match '\((COM\d+)\)'
    } | Select-Object -First 1)
    if ($serialRecord.Count -ne 1) {
        throw 'Found present X3 VID_303A:1001 USB interfaces, but no associated serial interface. Disconnect/reconnect X3 USB and re-check Device Manager; do not report X3 absent.'
    }

    $port = [regex]::Match($serialRecord[0].FriendlyName, '\((COM\d+)\)').Groups[1].Value
    return [pscustomobject]@{
        Port = $port
        SerialInstanceId = $serialRecord[0].InstanceId
        Interfaces = $x3Records
    }
}

function Get-XtraordinaryX3UsbTarget {
    $records = @(Get-PnpDevice -PresentOnly | Select-Object Status, Class, FriendlyName, InstanceId)
    return Get-X3UsbTargetFromPnpRecords -PnpRecords $records
}

function Get-XtraordinaryAdbDevice {
    param([Parameter(Mandatory)][string]$Adb)

    $connected = @(& $Adb devices | Select-String "`tdevice$")
    if ($connected.Count -gt 0) { return $connected }

    $mdnsLines = @(& $Adb mdns services)
    $endpoints = Get-AdbTlsConnectEndpoints -MdnsLines $mdnsLines
    if ($endpoints.Count -eq 0 -and $env:ADB_MDNS_OPENSCREEN -ne '0') {
        # OpenScreen can return no current TLS service on Windows even while
        # Bonjour can see one. The backend is selected when the server starts.
        $env:ADB_MDNS_OPENSCREEN = '0'
        & $Adb kill-server
        & $Adb start-server
        $mdnsLines = @(& $Adb mdns services)
        $endpoints = Get-AdbTlsConnectEndpoints -MdnsLines $mdnsLines
    }

    foreach ($endpoint in $endpoints) {
        & $Adb connect $endpoint | Out-Host
    }
    return @(& $Adb devices | Select-String "`tdevice$")
}

if ($RequireAdbDevice) {
    if ([string]::IsNullOrWhiteSpace($AdbPath) -or -not (Test-Path -LiteralPath $AdbPath)) {
        throw "Required project ADB is missing: $AdbPath"
    }
    $adbDevices = @(Get-XtraordinaryAdbDevice -Adb $AdbPath)
    if ($adbDevices.Count -ne 1) {
        throw "Expected exactly one ADB phone after current mDNS discovery; found $($adbDevices.Count)."
    }
}

if ($RequireX3Usb) {
    $x3Target = Get-XtraordinaryX3UsbTarget
    if ($null -eq $x3Target) {
        throw 'No present X3 USB/JTAG composite or serial interface with VID_303A:1001 was found. Do not report X3 absent from ports alone; verify the present PnP interfaces first.'
    }
    Write-Host "X3 USB/JTAG target: $($x3Target.Port) ($($x3Target.SerialInstanceId))"
}
