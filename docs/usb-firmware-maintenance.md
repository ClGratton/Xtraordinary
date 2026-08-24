# X3 USB firmware and setup maintenance

This is the authoritative maintenance contract for firmware selection, application-only flashing, and targeted setup reset from the Android app. `HANDOFF.md` records the current execution state; this document defines the reusable behavior.

Before diagnosing Android BLE lifecycle behavior, first check whether the X3 is powered and observable: inspect current BLE advertisement/GATT presence and Pixel USB `connected/configured` state after the configured standby interval. Simultaneous absence indicates powered-off/sleep or physical transport absence; wake/power the X3 and reacquire the existing interactive/fast communication lease before attributing the result to scan lifecycle.

## Firmware sources

The firmware picker must keep these sources distinct:

| App label | Meaning | Resolution policy |
| --- | --- | --- |
| Xtraordinary | This repository's companion firmware | Resolve the latest compatible asset from the Xtraordinary release manifest and verify its declared size and SHA-256. |
| XTEINK stock | Original international XTEINK firmware | Use the pinned X3 `XT V5.1.6 EN` image below and verify its fixed size and SHA-256. |
| CrossPoint | Open-source CrossPoint community firmware | Resolve the latest compatible GitHub release and require its SHA-256 digest. |
| CrossInk | CrossPoint-derived community fork | Resolve the latest compatible GitHub release and require its SHA-256 digest. |

“Stock” is reserved for XTEINK's OEM firmware. CrossPoint and CrossInk must never be presented as stock firmware.

Pinned XTEINK X3 stock release:

- Version: `XT V5.1.6 EN`
- Asset: `V5.1.6-X3-EN-PROD-0304_.bin`
- URL: `https://overseas-upload-file-api.oss-ap-southeast-1.aliyuncs.com/uploads/b9dced8c-45d7-4a2b-a13a-aed22e9e0bc0/2026/03/19/V5.1.6-X3-EN-PROD-0304_.bin`
- Size: `6,412,240` bytes
- SHA-256: `49926E09526A0201688EA6AC1936A8E62588F66DCD06297F2A011114EDE42525`

## USB lifecycle

The phone recognizes the ESP32-C3 USB/JTAG serial interface as USB VID/PID `303A:1001`. A firmware operation follows one shared lifecycle:

1. Detect the USB/JTAG interface and request Android USB permission when required.
2. Before changing reset lines, request the live `RUNTIME_TRACE_OTA` record and require identical running and next-boot OTA partitions. Firmware without this evidence fails closed and must be bootstrapped through the guarded Windows path.
3. Enter the ROM bootloader and synchronize. The guarded Windows path reads `otadata` before any application write and resolves the same selected slot from a CRC-valid, bootable sequence entry.
4. Disable the USB watchdogs and configure flash access.
5. Erase and write only the proven selected application region.
6. Verify the written region with the ROM MD5 command.
7. Hard-reset the X3.
8. If the USB/JTAG interface disappears, preserve `ReconnectRequired` and show **Disconnect and reconnect X3 USB to continue** until it re-enumerates.

Releasing Bluetooth/GATT before step 2 is preparation only. The app must say that
USB maintenance is being prepared; it must not say flashed, installed, verified,
ready to reboot, or restarting until the corresponding write, ROM MD5 verification,
and hard-reset phase has actually succeeded. A failure during stub upload or slot
selection therefore remains a pre-write failure in both protocol state and user copy.

Do not replace step 8 with a generic disconnected state. OEM firmware may stop exposing USB/JTAG after boot even while the cable remains physically attached. The instruction tells the user which physical action is required and prevents a successful flash from looking like an unexplained failure.

On Windows, preflight the same identity through `scripts/resolve-xtraordinary-deployment-targets.ps1`: inspect present `VID_303A:1001` composite/interface PnP records before selecting the associated serial interface. A ports-only scan is not evidence that X3 is absent, and discovery must not open the serial port.

When X3 is attached to the Pixel, Android's `host_manager.devices` inventory is
authoritative. The Pixel's separate gadget/device-manager `connected=false` state
describes whether the phone itself is attached as a USB peripheral; it must never
negate a present host-side `303A:1001` X3.

### Application firmware regions

- `app0` / `ota_0`: offset `0x10000`, maximum size `0x640000`.
- `app1` / `ota_1`: offset `0x650000`, maximum size `0x640000`.
- `otadata`: offset `0xE000`, size `0x2000`; this region is read to select the application target and is never rewritten by the raw application-only workflow.
- Input must be a non-empty ESP32-C3 application image beginning with byte `0xE9`.
- The downloaded length and SHA-256 must match the selected release before flashing.

Never assume `app0`. The reusable selected-slot policy must prove the current running/next-boot pair before Android flashing, or decode the two CRC-checked `otadata` entries before a guarded Windows flash. A missing, corrupt, invalid, aborted, unsupported, or contradictory selection blocks the write. `otadata` is selected-boot evidence, not proof of the partition that actually ran after fallback; final firmware truth still comes from fresh BLE `Capabilities` after reboot.

An application-only flash to either selected OTA application partition does not erase NVS, Android's bond, app data, `otadata`, or SD-card files.

### Reset X3 setup

**Reset X3 setup** is a targeted NVS reset implemented through the same region-aware USB writer:

- Offset: `0x9000`
- Size: `0x5000`
- Data: the complete region is written as `0xFF` and MD5-verified.
- Cleared: X3 Bluetooth pairing, Wi-Fi, and device settings held in NVS.
- Preserved: the application firmware partition and all SD-card files.

This action does not remove Android's remembered bond and does not clear the Xtraordinary app's data. Those are separate phone-side operations. The app must require explicit confirmation before writing the NVS region.

## Clean-room setup sequence

Use this only when the owner explicitly requests a complete reset. Stop between steps if the device state cannot be verified.

1. Flash **XTEINK stock** through the production Android USB flasher and wait for on-device MD5 verification.
2. If the app says **Disconnect and reconnect X3 USB to continue**, unplug and reconnect the USB cable. If required, reset/power-cycle the X3 so `303A:1001` re-enumerates.
3. Run **Reset X3 setup** and wait for its MD5 verification and reset.
4. In Android Bluetooth device details, verify the exact target before removing the remembered X3 bond. The device observed on 2026-08-11 was `XTEINK Companion` at `7C:E8:B1:71:13:3E`; do not assume this address for another unit.
5. Clear package data for `com.xteink.companion` only after confirming the owner still wants a first-run app state.
6. Launch Xtraordinary and complete first-run setup.
7. Reconnect USB and flash the current Xtraordinary candidate through the production flasher.
8. Pair from scratch and verify advertisement, bond, GATT connection, service discovery, notifications, capabilities, status, library exchange, and policy acknowledgements.

The stock flash, NVS reset, bond removal, and app-data clear are four independent state changes. Never infer one from another.

## Current 2026-08-11 execution record

- The production phone flasher first completed CrossPoint `v1.5.0` after that source had been selected under misleading wording. It was allowed to finish rather than being interrupted during erase/write.
- The source labels were corrected and genuine XTEINK stock `XT V5.1.6 EN` was then downloaded, size/SHA-256 checked, written at `0x10000`, ROM-MD5 verified, and restarted.
- After stock boot, the USB/JTAG interface stopped enumerating while the cable remained attached. Disconnect/reconnect restored `303A:1001`; source preserves the explicit reconnect-required state described above.
- Android `0.2.0-dev25` / code 26 is installed in genuine first-run state. APK SHA-256 is `44EBB263F3641539EF2F9606D7E4D54D73A7E5EF603C5A1FC8727C149E8C8C0A`.
- The X3 currently runs XTEINK stock `XT V5.1.6 EN`.
- The bounded NVS reset was executed, ROM-MD5 verified for `0x9000..0xDFFF`, and followed by a hard reset.
- The exact Android bond `XTEINK Companion` / `7C:E8:B1:71:13:3E` was removed and `BOND_NONE` observed.
- Xtraordinary app data was cleared and the first-run Welcome page was launched.
- Xtraordinary firmware has **not** been restored after the stock flash.
- The SD card was not modified by these operations.

## Build and recovery

Build Android only with:

```powershell
.\scripts\build-xtraordinary-app.ps1
```

Build firmware only with the canonical wrapper outside the sandbox, never concurrently with Gradle:

```powershell
.\scripts\build-x3-firmware.ps1 -Version <version> -Jobs 2
```

If a USB write or MD5 check fails, keep the cable attached, record the exact phase/message, and retry only after confirming the target still enumerates as `303A:1001`. Never use a full-chip erase as recovery. A failed application write is recovered by rewriting the bounded application region; a failed setup reset is recovered by rewriting only the bounded NVS region.
