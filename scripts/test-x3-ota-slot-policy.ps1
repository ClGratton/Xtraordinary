$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'x3-ota-slot-policy.ps1')

function Assert-Equal {
    param($Expected, $Actual, [string]$Message)
    if ($Expected -ne $Actual) {
        throw "$Message Expected=$Expected Actual=$Actual"
    }
}

function Set-OtaEntry {
    param(
        [Parameter(Mandatory)][byte[]]$Bytes,
        [Parameter(Mandatory)][int]$Entry,
        [Parameter(Mandatory)][uint32]$Sequence,
        [uint32]$State = [uint32]::MaxValue,
        [switch]$CorruptCrc
    )
    $offset = $Entry * 0x1000
    [BitConverter]::GetBytes($Sequence).CopyTo($Bytes, $offset)
    [BitConverter]::GetBytes($State).CopyTo($Bytes, $offset + 24)
    [uint32]$crc = Get-EspOtaSequenceCrc32 -Sequence $Sequence
    if ($CorruptCrc) { $crc = $crc -bxor 1 }
    [BitConverter]::GetBytes($crc).CopyTo($Bytes, $offset + 28)
}

$layout = Get-X3OtaFlashLayout -PartitionTablePath (Join-Path $ProjectRoot 'firmware\partitions.csv')
Assert-Equal 0xE000 $layout.OtaDataOffset 'otadata offset changed.'
Assert-Equal 0x2000 $layout.OtaDataSize 'otadata size changed.'
Assert-Equal 0x10000 $layout.AppPartitions[0].Offset 'ota_0 offset changed.'
Assert-Equal 0x650000 $layout.AppPartitions[1].Offset 'ota_1 offset changed.'

# Independent values from esp_rom_crc32_le(UINT32_MAX, ota_seq, 4).
Assert-Equal ([uint32]0x4743989A) (Get-EspOtaSequenceCrc32 -Sequence 1) 'ota_seq=1 CRC mismatch.'
Assert-Equal ([uint32]0x55F63774) (Get-EspOtaSequenceCrc32 -Sequence 2) 'ota_seq=2 CRC mismatch.'

$slot0Bytes = [byte[]]::new($layout.OtaDataSize)
[Array]::Fill[byte]($slot0Bytes, 0xFF)
Set-OtaEntry -Bytes $slot0Bytes -Entry 0 -Sequence 1
$slot0 = Get-X3SelectedOtaSlotFromBytes -OtaData $slot0Bytes -Layout $layout
Assert-Equal 0 $slot0.Slot 'ota_seq=1 must select app0.'
Assert-Equal 0x10000 $slot0.Offset 'app0 target offset mismatch.'

$slot1Bytes = [byte[]]::new($layout.OtaDataSize)
[Array]::Fill[byte]($slot1Bytes, 0xFF)
Set-OtaEntry -Bytes $slot1Bytes -Entry 0 -Sequence 1
Set-OtaEntry -Bytes $slot1Bytes -Entry 1 -Sequence 2
$slot1 = Get-X3SelectedOtaSlotFromBytes -OtaData $slot1Bytes -Layout $layout
Assert-Equal 1 $slot1.Slot 'Higher ota_seq=2 must select app1.'
Assert-Equal 0x650000 $slot1.Offset 'app1 target offset mismatch.'

$corruptNewer = [byte[]]::new($layout.OtaDataSize)
[Array]::Fill[byte]($corruptNewer, 0xFF)
Set-OtaEntry -Bytes $corruptNewer -Entry 0 -Sequence 3
Set-OtaEntry -Bytes $corruptNewer -Entry 1 -Sequence 4 -CorruptCrc
$fallback = Get-X3SelectedOtaSlotFromBytes -OtaData $corruptNewer -Layout $layout
Assert-Equal 0 $fallback.Slot 'A newer corrupt entry must not replace the valid selected slot.'

$invalidEntries = [byte[]]::new($layout.OtaDataSize)
[Array]::Fill[byte]($invalidEntries, 0xFF)
Set-OtaEntry -Bytes $invalidEntries -Entry 0 -Sequence 1 -State 3
Set-OtaEntry -Bytes $invalidEntries -Entry 1 -Sequence 2 -State 4
$failedClosed = $false
try {
    Get-X3SelectedOtaSlotFromBytes -OtaData $invalidEntries -Layout $layout | Out-Null
} catch {
    $failedClosed = $_.Exception.Message -match 'selected X3 application slot is unproven'
}
if (-not $failedClosed) { throw 'Invalid or aborted otadata entries must fail closed.' }

Write-Host 'X3 OTA selected-slot policy fixtures passed.'
