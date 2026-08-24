$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'resolve-xtraordinary-deployment-targets.ps1')

$bonjour = @(
    'List of discovered mdns services',
    'adb-pixel._adb-tls-connect._tcp 192.168.1.61:36221'
)
$endpoints = @(Get-AdbTlsConnectEndpoints -MdnsLines $bonjour)
if ($endpoints.Count -ne 1 -or $endpoints[0] -ne '192.168.1.61:36221') {
    throw 'Bonjour TLS endpoint parsing did not retain the current endpoint.'
}
if (@(Get-AdbTlsConnectEndpoints -MdnsLines @('List of discovered mdns services')).Count -ne 0) {
    throw 'Empty mDNS discovery must not invent an ADB endpoint.'
}

$x3 = Get-X3UsbTargetFromPnpRecords -PnpRecords @(
    [pscustomobject]@{ Status = 'OK'; Class = 'USB'; FriendlyName = 'USB Composite Device'; InstanceId = 'USB\VID_303A&PID_1001\X3' },
    [pscustomobject]@{ Status = 'OK'; Class = 'USBDevice'; FriendlyName = 'USB JTAG debug unit'; InstanceId = 'USB\VID_303A&PID_1001&MI_02\X3' },
    [pscustomobject]@{ Status = 'OK'; Class = 'Ports'; FriendlyName = 'USB Serial Device (COM7)'; InstanceId = 'USB\VID_303A&PID_1001&MI_00\X3' }
)
if ($null -eq $x3 -or $x3.Port -ne 'COM7') {
    throw 'X3 composite/serial discovery did not select the associated COM port.'
}
if ($null -ne (Get-X3UsbTargetFromPnpRecords -PnpRecords @())) {
    throw 'Absent X3 PnP records must remain absent.'
}

$hostDump = @(
    '  host_manager={',
    '    devices={',
    '      name=/dev/bus/usb/001/002',
    '      vendor_id=12346',
    '      product_id=4097',
    '      manufacturer_name=Espressif',
    '    }',
    '  }',
    '  connected=false'
)
$hostTarget = Get-X3AndroidHostTargetFromUsbDump -DumpLines $hostDump
if ($null -eq $hostTarget -or $hostTarget.Path -ne '/dev/bus/usb/001/002') {
    throw 'Pixel host_manager X3 inventory fixture did not resolve the Android USB target.'
}
if ($null -ne (Get-X3AndroidHostTargetFromUsbDump -DumpLines @('connected=false', 'configured=false'))) {
    throw 'Pixel gadget disconnected state must not invent a host X3 target.'
}

Write-Host 'Deployment target discovery fixtures passed.'
