$ErrorActionPreference = 'Stop'

function ConvertFrom-X3PartitionNumber {
    param([Parameter(Mandatory)][string]$Value)

    $trimmed = $Value.Trim()
    if ($trimmed -notmatch '^0x[0-9a-fA-F]+$') {
        throw "Unsupported X3 partition value: $Value"
    }
    return [Convert]::ToInt32($trimmed.Substring(2), 16)
}

function Get-X3OtaFlashLayout {
    param([Parameter(Mandatory)][string]$PartitionTablePath)

    if (-not (Test-Path -LiteralPath $PartitionTablePath)) {
        throw "X3 partition table is missing: $PartitionTablePath"
    }

    $partitions = @(Get-Content -LiteralPath $PartitionTablePath |
        Where-Object { $_ -notmatch '^\s*(#|$)' } |
        ForEach-Object {
            $fields = @($_ -split ',' | ForEach-Object { $_.Trim() })
            if ($fields.Count -lt 5) { throw "Malformed X3 partition row: $_" }
            [pscustomobject]@{
                Label = $fields[0]
                Type = $fields[1]
                Subtype = $fields[2]
                Offset = ConvertFrom-X3PartitionNumber $fields[3]
                Size = ConvertFrom-X3PartitionNumber $fields[4]
            }
        })

    $otaData = @($partitions | Where-Object { $_.Type -eq 'data' -and $_.Subtype -eq 'ota' })
    if ($otaData.Count -ne 1 -or $otaData[0].Size -lt 0x2000) {
        throw 'X3 partition table must contain one two-sector otadata partition.'
    }

    $otaApps = @($partitions |
        Where-Object { $_.Type -eq 'app' -and $_.Subtype -match '^ota_(\d+)$' } |
        ForEach-Object {
            [pscustomobject]@{
                Slot = [int]([regex]::Match($_.Subtype, '^ota_(\d+)$').Groups[1].Value)
                Label = $_.Label
                Subtype = $_.Subtype
                Offset = $_.Offset
                Size = $_.Size
            }
        } |
        Sort-Object Slot)
    if ($otaApps.Count -ne 2 -or $otaApps[0].Slot -ne 0 -or $otaApps[1].Slot -ne 1) {
        throw 'X3 selected-slot policy requires exactly ota_0 and ota_1 application partitions.'
    }
    if (@($partitions | Where-Object { $_.Type -eq 'app' -and $_.Subtype -eq 'factory' }).Count -ne 0) {
        throw 'X3 selected-slot policy does not support a factory application partition.'
    }

    return [pscustomobject]@{
        OtaDataOffset = $otaData[0].Offset
        OtaDataSize = $otaData[0].Size
        AppPartitions = $otaApps
    }
}

function Get-EspOtaSequenceCrc32 {
    param([Parameter(Mandatory)][uint32]$Sequence)

    # esp_rom_crc32_le(UINT32_MAX, little-endian ota_seq, 4) starts its
    # reflected CRC accumulator at zero and complements it on return.
    [uint32]$crc = 0
    [uint32]$polynomial = [Convert]::ToUInt32('EDB88320', 16)
    foreach ($byte in [BitConverter]::GetBytes($Sequence)) {
        $crc = [uint32]($crc -bxor [uint32]$byte)
        for ($bit = 0; $bit -lt 8; $bit++) {
            if (($crc -band 1) -ne 0) {
                $crc = [uint32](($crc -shr 1) -bxor $polynomial)
            } else {
                $crc = [uint32]($crc -shr 1)
            }
        }
    }
    return [uint32]($crc -bxor [uint32]::MaxValue)
}

function Get-X3SelectedOtaSlotFromBytes {
    param(
        [Parameter(Mandatory)][byte[]]$OtaData,
        [Parameter(Mandatory)]$Layout
    )

    if ($OtaData.Length -ne $Layout.OtaDataSize) {
        throw "Expected $($Layout.OtaDataSize) otadata bytes; received $($OtaData.Length)."
    }

    $entries = @(0, 1 | ForEach-Object {
        $entryIndex = $_
        $entryOffset = $entryIndex * 0x1000
        [uint32]$sequence = [BitConverter]::ToUInt32($OtaData, $entryOffset)
        [uint32]$state = [BitConverter]::ToUInt32($OtaData, $entryOffset + 24)
        [uint32]$storedCrc = [BitConverter]::ToUInt32($OtaData, $entryOffset + 28)
        $calculatedCrc = Get-EspOtaSequenceCrc32 -Sequence $sequence
        [pscustomobject]@{
            Entry = $entryIndex
            Sequence = $sequence
            State = $state
            StoredCrc = $storedCrc
            CalculatedCrc = $calculatedCrc
            Valid = $sequence -ne [uint32]::MaxValue -and
                $sequence -ne 0 -and
                $storedCrc -eq $calculatedCrc -and
                $state -notin @(3, 4)
        }
    })

    $validEntries = @($entries | Where-Object Valid | Sort-Object Sequence -Descending)
    if ($validEntries.Count -eq 0) {
        throw 'No CRC-valid, bootable otadata entry exists; selected X3 application slot is unproven.'
    }

    $activeEntry = $validEntries[0]
    $slotIndex = [int](($activeEntry.Sequence - 1) % $Layout.AppPartitions.Count)
    $partition = @($Layout.AppPartitions | Where-Object Slot -eq $slotIndex)
    if ($partition.Count -ne 1) {
        throw "Selected OTA slot $slotIndex does not map to exactly one X3 application partition."
    }

    return [pscustomobject]@{
        Entry = $activeEntry.Entry
        Sequence = $activeEntry.Sequence
        State = $activeEntry.State
        Slot = $partition[0].Slot
        Label = $partition[0].Label
        Subtype = $partition[0].Subtype
        Offset = $partition[0].Offset
        Size = $partition[0].Size
    }
}

function Read-X3SelectedOtaSlot {
    param(
        [Parameter(Mandatory)][string]$Port,
        [Parameter(Mandatory)][string]$Python,
        [Parameter(Mandatory)][string]$Esptool,
        [Parameter(Mandatory)]$Layout
    )

    $temporaryPath = [IO.Path]::GetTempFileName()
    try {
        Write-Host 'Reading X3 otadata before any application write...'
        & $Python $Esptool `
            --chip esp32c3 `
            --port $Port `
            --baud 921600 `
            read-flash `
            ('0x{0:X}' -f $Layout.OtaDataOffset) `
            ('0x{0:X}' -f $Layout.OtaDataSize) `
            $temporaryPath
        if ($LASTEXITCODE -ne 0) {
            throw "Read-only X3 otadata inspection failed with exit code $LASTEXITCODE. No application write was attempted."
        }
        $bytes = [IO.File]::ReadAllBytes($temporaryPath)
        return Get-X3SelectedOtaSlotFromBytes -OtaData $bytes -Layout $Layout
    } finally {
        if (Test-Path -LiteralPath $temporaryPath) {
            Remove-Item -LiteralPath $temporaryPath -Force
        }
    }
}
