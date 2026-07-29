# X3 firmware build and hardware-test runbook

The [X3 companion connection and power contract](companion-connection-power-contract.md)
is authoritative for connection-state and sleep semantics. A build or flash
that implements a conflicting state transition is not a valid candidate.

Use this runbook for every `x3_companion` or `x3_companion_release` build.
The build must fail if it links a C++ runtime for an ISA the ESP32-C3 does not
implement.

## Non-negotiable architecture gate

The X3 uses an ESP32-C3 implementing RV32IMC. It does **not** implement the
RISC-V `A` atomic extension.

The pioarduino toolchain's default multilib is RV32IMAC. If the final linker
selects the root `libstdc++.a`, reference-count operations such as
`std::shared_ptr` execute `amoadd.w` and the X3 panics with `Illegal
instruction`.

`firmware/platformio.ini` sets:

```text
-march=rv32imc_zicsr_zifencei
-mabi=ilp32
```

`firmware/scripts/esp32c3_multilib.py` also:

1. carries those flags into the final link;
2. prepends the matching multilib directory; and
3. fails the build unless `firmware.map` proves that `atomicity.o` came from:

```text
rv32imc_zicsr_zifencei/ilp32/libstdc++.a
```

Do not remove or bypass that post-link gate.

## Power-managed framework build

The first `x3_companion` build after a power configuration change is a
framework build, not only an application build. `custom_sdkconfig` rebuilds the
ESP32-C3 Arduino/ESP-IDF libraries with Bluetooth modem sleep and the ESP-IDF
power-management infrastructure. Later builds reuse that generated framework
until the configuration changes.

After the build, verify the generated sdkconfig contains:

```text
CONFIG_PM_ENABLE=y
CONFIG_FREERTOS_USE_TICKLESS_IDLE=y
CONFIG_BT_CTRL_MODEM_SLEEP=y
CONFIG_BT_CTRL_LPCLK_SEL_MAIN_XTAL=y
CONFIG_ESP_PHY_MAC_BB_PD=y
```

`HalPowerManager` must keep `esp_pm_config_t.light_sleep_enable` false on X3.
Bluetooth modem sleep and dynamic frequency scaling may be enabled, but global
automatic light sleep is not hardware-qualified.

## Failure ledger and no-repeat rules

Update this section whenever a new build-path failure is found. Before retrying
a failed framework build, record:

1. the first fatal line (not the last warning);
2. the root cause;
3. how to detect it before another long build; and
4. the permanent prevention in source or in this runbook.

Do not retry by making a speculative configuration change. Verify the relevant
generated file or dependency edge first.

### 2026-07-29 automatic-light-sleep hardware regression

The `xtraordinary-pm-20260729` candidate passed compilation, link-map,
artifact-hash, app-unit-test, flash, and install checks, but it was handed back
before completing the hardware regression matrix. On the Pixel 10, a bounded
post-reboot scan ended with:

```text
No advertising XTEINK companion found within 15 seconds
```

The same candidate made power-button shutdown appear to reboot. The explicit
deep-sleep code and its wait-for-button-release loop were unchanged from the
previous revision; the common new platform behavior was global automatic light
sleep, enabled while booting and while disconnected advertising.

Permanent rules:

1. Do not enable `esp_pm_config_t.light_sleep_enable` on X3 until it has a
   dedicated board-level qualification change.
2. Keep automatic light sleep separate from BLE controller modem sleep. The
   latter can be tested without changing the board's application sleep state.
3. A successful build, flash, or first boot is never a hardware pass.
4. Before calling a power candidate ready or pushing it, test in this order:
   cold boot, 15-second Pixel advertisement discovery, encrypted GATT
   connection, app-triggered command, power-button shutdown, power-button wake,
   post-wake reconnect, reader entry/exit, and timed battery measurement.
5. Record every new failure in this ledger before the next retry, including the
   first observable failure and which test exposed it.

### 2026-07-29 COM7 bootloader-handshake failure

The first diagnostic-candidate upload stopped before erase or write with:

```text
A fatal error occurred: Failed to set baud rate 115200.
```

Device Manager visibly showed `USB Serial Device (COM7)` while the X3 showed
Home. A sandboxed `Get-PnpDevice -PresentOnly` query did not return that device;
that query was therefore not a valid presence gate and must not be used to
label a user-visible port stale. Device Manager presence proves enumeration,
but not that the firmware-backed CDC endpoint can perform the ESP32-C3 reset
handshake.

The subsequent failures established two separate boundaries before any erase or
write:

```text
PermissionError(13, 'A device attached to the system is not functioning.',
                None, 31)
A fatal error occurred: Failed to connect to ESP32-C3:
No serial data received.
```

Direct esptool could eventually open COM7 with both `default-reset` and
`usb-reset`, but neither reset strategy received a ROM bootloader response.

Before every upload:

1. use Device Manager or the user's direct observation for physical presence;
2. treat successful port open, reset handshake, erase, write, and verification
   as separate gates;
3. never infer a working CDC channel from enumeration alone;
4. never rebuild merely to retry a pre-write transport failure;
5. after both serial-reset strategies fail before erase, use the ESP32-C3
   built-in USB-JTAG interface instead of repeating serial retries.

Do not combine PlatformIO's `nobuild` and `upload` targets for this pioarduino
environment. On 2026-07-29 it launched esptool without the required
`0x10000 firmware.bin` pair and failed before opening the port. Use the normal
verified upload target or an explicitly captured esptool command.

The pioarduino registry URL ending in
`openocd-v0.12.0-esp32-20251215.zip` is a metadata package, not the Windows
OpenOCD binary archive. Installing it directly creates only `package.json` and
`tools.json`. Resolve the `win64` artifact recorded in `tools.json`, verify its
SHA-256, and then invoke the extracted `openocd.exe`. Do not attempt a flash
until that executable exists.

On Windows, a healthy-looking generic `winusb.inf` binding is still insufficient
when OpenOCD reports `LIBUSB_ERROR_NOT_FOUND`: it does not provide the
device-interface GUID used to enumerate the JTAG endpoint. Install Espressif's
signed `USB_JTAG_debug_unit.inf`, verify that it is the best-ranked match, then
re-enumerate only the JTAG interface. A non-administrator
`pnputil /restart-device` attempt returned `Access is denied`; do not repeat it.
A physical USB reconnect is safe, but on the 2026-07-29 host it preserved the
already-selected generic driver. Use an elevated device rebind or Device
Manager's explicit driver selection. Confirm that the live driver changes from
`winusb.inf` to the Espressif/libwdi INF before retrying OpenOCD.

### 2026-07-29 power-framework rebuild

The power change exposed a pioarduino Windows build-system defect, plus several
avoidable operator mistakes:

- `custom_sdkconfig` rebuilds the whole Arduino/ESP-IDF framework, including
  managed components that CrossPoint never links. This is why a power-only
  change can take several minutes on a cold cache.
- Pioarduino's CMake-to-SCons bridge treated the generated certificate assembly
  from ESP Insights and ESP RainMaker as ordinary source files. SCons then
  formed duplicated build paths and failed with `Source ... .crt.S not found`.
- Removing only the managed RainMaker component was incomplete: Arduino's
  `RainMaker` wrapper sources still compiled and then failed on the removed
  `esp_rmaker_core.h`. The permanent configuration must exclude both the IDF
  components with `custom_component_remove` and their Arduino wrappers with
  `CONFIG_ARDUINO_SELECTIVE_COMPILATION`.
- `sdkconfig.defaults` is not proof of the build configuration. A stale
  `sdkconfig.<environment>` can override it. Delete the untracked generated
  environment sdkconfig when the framework configuration changes, then verify
  `.pio/build/<environment>/config/sdkconfig.h`.
- The Codex sandbox denies the ESP compiler's absolute-path lookup on Windows
  with `Failed to get path name. Error code: 5`. A short compiler probe proved
  the same command succeeds outside the sandbox. Run the yielded PlatformIO
  build with the required approval; do not misdiagnose this as a toolchain or
  source-code failure.
- Always set the project-local `PLATFORMIO_CORE_DIR`, `PYTHONUTF8=1`, and
  `PYTHONIOENCODING=utf-8`. Missing the first writes to an inaccessible
  user-level lock; missing UTF-8 can kill PlatformIO's output thread while it
  prints translated language names.
- After the custom framework succeeded, the normal application link failed on
  `undefined reference to __wrap_log_printf`. The copied
  `pioarduino-build.py` retained `-Wl,--wrap=log_printf`, but the newly built
  diagnostics archive did not export that wrapper. The permanent
  `src/platform/log_printf_wrap.c` implementation preserves the link contract
  and forwards to Arduino's `log_printfv`; do not remove it while the generated
  link script contains that wrap flag.
- Enabling `CONFIG_FREERTOS_USE_TICKLESS_IDLE` adds
  `.text.prvGetExpectedIdleTime` and `.text.vTaskStepTick`, but the pinned
  pioarduino ESP32-C3 linker script omits them from its explicit
  `libfreertos.a:tasks.*` IRAM list. Orphan placement made `elf2image` success
  depend on the preceding application size and failed with
  `Invalid .text.prvGetExpectedIdleTime segment length ... not multiple of 4`.
  `scripts/patch_esp32c3_tickless_ld.py` patches the pinned dependency
  idempotently before link, and the post-link verifier rejects any map that
  still contains either orphan output section.
- In PowerShell, `$HOME` is a reserved read-only automatic variable. A
  2026-07-29 inspection reused `$home` (case-insensitive) and stopped before
  reading its target file. Use task-specific names such as `$homeLines`;
  never repurpose automatic or system variables.

For a power build, the final generated header—not replacement messages in the
log—must define:

```text
CONFIG_ARDUINO_SELECTIVE_COMPILATION
CONFIG_PM_ENABLE
CONFIG_BT_CTRL_MODEM_SLEEP
CONFIG_FREERTOS_USE_TICKLESS_IDLE
```

It must not define `CONFIG_ARDUINO_SELECTIVE_RainMaker` or
`CONFIG_ARDUINO_SELECTIVE_Insights`.

Never run `pio device list` in this workflow. It can block on device discovery.
Use a bounded, explicit port check, and run every long build as a yielded job
whose log and child processes are polled at least once per minute.

## Fast edit-build loop

Do not change `XTRAORDINARY_VERSION` for every local attempt. It is a
preprocessor definition; changing it invalidates every C/C++ object and turns
an incremental build into a full build.

Keep one stable development version while iterating:

```powershell
$env:XTRAORDINARY_VERSION = 'xtraordinary-local'
$env:PLATFORMIO_CORE_DIR = "$PWD\.tools\platformio-core"
$env:PLATFORMIO_SETTING_ENABLE_TELEMETRY = 'no'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
& .\.tools\platformio-venv\Scripts\pio.exe run `
  -d firmware `
  -e x3_companion_release `
  -j 4
```

Rules:

- Never run `pio run --target clean` for an ordinary source edit.
- Keep `.pio` and the configured `.cache` intact.
- Use `-j 4` on this workstation. A single worker needlessly lengthens the
  full build.
- Bump the version once, after the fix and hardware test are ready to become a
  named artifact.
- Run long builds in a background process and poll the log. Do not leave a
  blocking serial monitor attached.

## Candidate verification

A successful PlatformIO exit is necessary but not sufficient. Before flashing:

1. Confirm the post-link message:

   ```text
   Verified ESP32-C3 RV32IMC libstdc++ multilib
   ```

2. Confirm the map manually when diagnosing toolchain changes:

   ```powershell
   rg -n "rv32imc_zicsr_zifencei.+libstdc\+\+\.a\(atomicity\.o\)" `
     firmware\.pio\build\x3_companion_release\firmware.map
   ```

3. Copy the candidate to `build/artifacts/` with a model and version in its
   filename.
4. Record byte length and SHA-256.
5. Confirm the binary contains the intended version string and not the prior
   one.

Never flash an image whose map points to the root toolchain
`riscv32-esp-elf/lib/libstdc++.a`.

## Flashing

The sleeping X3 tears down USB CDC, so COM7 disappearing is expected. Wake the
X3 once before flashing; do not diagnose sleep as a missing cable or disabled
USB debugging.

Use esptool without its Windows Unicode progress renderer:

```powershell
$env:PYTHONUTF8 = '1'
& "$env:USERPROFILE\.platformio\penv\Scripts\esptool.exe" `
  --chip esp32c3 `
  --port COM7 `
  --baud 921600 `
  write-flash `
  --no-progress `
  0x10000 `
  .\build\artifacts\xtraordinary-x3-version.bin
```

Accept the flash only when esptool reports both:

```text
Wrote ... bytes
Hash of data verified.
```

### USB-JTAG recovery when COM7 cannot enter the ROM loader

Use this path only after the X3 is visibly awake, COM7 is enumerated, and both
esptool reset strategies fail before erase with `No serial data received`.
COM7 and JTAG are separate interfaces of the same ESP32-C3 composite device.

1. Confirm `USB JTAG/serial debug unit` is present with hardware ID
   `USB\VID_303A&PID_1001&MI_02`.
2. Install Espressif's signed `USB_JTAG_debug_unit.inf`. Generic
   `winusb.inf` can show status `OK` while OpenOCD still fails with
   `LIBUSB_ERROR_NOT_FOUND` because the required device-interface GUID is
   absent.
3. Confirm the live JTAG interface is actually bound to the Espressif/libwdi
   INF, not merely that the package is installed or best-ranked. A cable
   reconnect did not replace an already-selected generic driver on the
   2026-07-29 recovery host. Use an elevated device rebind or Device Manager's
   explicit driver selection; do not repeat an unelevated `pnputil` restart
   after `Access is denied`.
4. Run a non-writing OpenOCD probe first. It must identify the ESP32-C3 target
   before `program_esp` is attempted.
5. Program the app image at `0x10000`, request verification, and reset only
   after verification:

   ```powershell
   openocd.exe `
     -s <openocd>\share\openocd\scripts `
     -f interface/esp_usb_jtag.cfg `
     -f target/esp32c3.cfg `
     -c "adapter speed 5000" `
     -c "program_esp {<candidate.bin>} 0x10000 verify" `
     -c "reset run; shutdown"
   ```

The bad automatic-light-sleep image can gate the USB/APB clocks before OpenOCD
attaches. After the correct driver was bound, a 60-second cold-boot listener
progressed from `descriptor -9 / DTM -1` to `Programming Started`, proving that
USB reconnect is the required wake boundary. Its next failure was:

```text
Failed to get flash maps (-9)
Failed to probe flash, size 0 KB
Clock configuration set failed
```

That attempt did not find a flash bank and wrote nothing. Do not repeat the same
command. The next bounded recovery uses `adapter speed 1000` and appends
`no_clock_boost` to `program_esp`, bypassing the failing optional stub clock
change while preserving erase, write, and verification gates.

The adjusted attempt removed the clock-configuration error and again reached
`Programming Started`, but automatic flash-size probing still returned
`flash size 0 KB` and `auto_probe failed`; it also wrote nothing. X3 flash size
is fixed and already declared as 16 MB by the board configuration. The next
recovery therefore sets `ESP_FLASH_SIZE 0x1000000` before sourcing
`target/esp32c3.cfg`, while retaining the 1 MHz adapter rate and
`no_clock_boost`. Do not revert to automatic probing for this recovery image.

OpenOCD `v0.12.0-esp32-20251215` contains a defect in
`target/esp_common.cfg` when an explicit size is supplied:

```tcl
set _SIZE SIZE
```

This passes the literal word `SIZE` to `flash bank`, fails before target
initialization, and can terminate OpenOCD with an access violation. Patch it to
`set _SIZE $SIZE` before using the 16 MB override. The failed unpatched command
did not initialize JTAG and wrote nothing.

Even with that correction, the pinned OpenOCD flash stub could not probe the
running image's flash mappings. The successful recovery therefore split target
control from flashing:

1. Attach with built-in JTAG at 1 MHz.
2. Halt after reset.
3. Set the ESP32-C3 ROM's documented temporary force-download bit:

   ```tcl
   mmw 0x600080f4 0x1 0x0
   ```

   `0x600080F4` is `RTC_CNTL_OPTION1_REG`; bit 0 is
   `RTC_CNTL_FORCE_DOWNLOAD_BOOT` in the pinned Espressif headers.
4. Reset and run, which enters the ROM download loader.
5. Hand COM7 to esptool without another pre-connection reset:

   ```powershell
   esptool.exe `
     --chip esp32c3 `
     --port COM7 `
     --baud 921600 `
     --before no-reset `
     --after hard-reset `
     --connect-attempts 20 `
     write-flash --no-progress `
     0x10000 <verified-candidate.bin>
   ```

On 2026-07-29 this identified ESP32-C3 revision v0.4, wrote 5,437,632
bytes at `0x10000`, reported `Hash of data verified`, and hard-reset through
RTS. This is the proven recovery path for an application whose automatic light
sleep makes both ordinary USB reset and the OpenOCD flash stub unusable.

No OpenOCD error before target identification is permission to fall back to
serial retries. Record the first failing boundary and fix that boundary.

## Device-mutation and log-capture safety

A reset performed for logging is still a device mutation. Never issue a
USB-JTAG reset, esptool reset, flash, or bootloader transition merely to obtain
startup logs unless the user has been told exactly what will happen and has
approved that specific mutation.

Opening ESP32-C3 USB CDC can toggle host control lines on some toolchains.
Treat the first serial open as a reset risk even when DTR and RTS are disabled.
Do not open COM7 while the user is interacting with the X3 without explicit
approval.

Every build, install, or capture helper must have:

1. one stated purpose and one bounded timeout;
2. its process ID or direct command result tracked;
3. output polled rather than an unattended blocking monitor; and
4. an explicit cleanup check proving no helper remains.

On 2026-07-26 two apparent X3 reboots were caused by unannounced USB-JTAG reset
attempts made to capture BLE startup logs. The second coincided with opening the
Crash Log screen. Do not attribute either reboot to firmware or the Crash Log
activity; reproduce that activity without any host-side reset before making a
crash claim.

## Hardware test

For power-managed companion builds, also follow
`docs/diagnostic-state-matrix.md`. It is the authority for Home, Reading,
disconnected, intentional-sleep, wake, and settings round-trip checks.

For ordinary crash work, start a bounded background serial capture immediately
after flashing only when the user has approved the reset risk. For companion
power qualification, do not open serial; use the bounded BLE diagnostics in the
state matrix instead. Then:

1. wait for BLE initialization and the settled heap report;
2. open the exact cached EPUB that previously failed;
3. require the reader page to render;
4. turn at least two pages;
5. exit and reopen the book;
6. confirm there is no `Guru Meditation`, reboot, or new crash report.

For the Project Hail Mary regression, the expected low-memory log is:

```text
[CSS] Skipping optional CSS cache
```

That message must be followed by normal reader operation. It is not itself a
passing result.

## BLE connection regression gate

Do not collapse the Android connection states:

- **Managed** means the app remembers a previously paired X3 and may retry or
  queue work.
- **Reconnecting** means a scan or GATT connection is in progress.
- **Connected** means encrypted GATT service discovery, MTU negotiation, and
  notification-descriptor subscription have completed.

The app must not label a remembered device `Connected`, disable its reconnect
button as if it were connected, or open the connected-device tabs before the
notification descriptor succeeds. A connected device opens the Status and
Firmware tabs; a remembered device with no transport opens the offline/retry
surface.

The 2026-07-26 battery experiment dropped the ESP32-C3 from normal speed to the
80 MHz BLE floor five seconds after a client attached. Pixel Bluetooth history
then showed the link terminating at that same boundary. Until ESP-IDF
coordinated power management is owned and tested, keep the CPU clock stable for
the full connected lifetime. The phone disconnects after backgrounding, after
which the X3 may return to the proven disconnected advertising floor. Actual X3
sleep still uses ESP32-C3 deep sleep.

Every connection-affecting firmware or Android change must pass:

1. foreground the app with an already bonded X3;
2. require direct reconnect through service, MTU, and notification setup;
3. keep the link alive for at least two minutes, crossing the old five-second
   failure boundary;
4. start Focus and confirm the phone timer starts and the X3 acknowledges the
   session;
5. background the app and confirm it intentionally disconnects;
6. foreground it and confirm reconnect without re-pairing;
7. tap the device card while offline and while connected, verifying the
   offline/retry surface and connected Status/Firmware tabs respectively.

For battery-state changes, also require:

8. open a book with the app foregrounded; require `Reading/Slow` on Android
   before accepting the firmware's slow connection-parameter request;
9. background the app and verify Android retains `Reading - low-power sync`
   rather than showing `Disconnected`;
10. reopen the app and verify one immediate user-driven reconnect, then wait
    longer than the configured slow interval before an absent check-in becomes
    offline;
11. leave the reader and verify the next connected status is `Awake/Fast`;
12. let the X3 auto-sleep and verify Android shows `Sleeping`, with no retry
    loop;
13. boot with no stored companion bond, keep using Home across the two-minute
    first-search boundary, and verify only first-discovery advertising stops:
    the nonmodal pairing-paused chip appears, controls and reader remain usable,
    and Android does not claim Sleeping;
14. boot with a stored companion bond and leave the phone absent beyond two
    minutes; verify Home remains usable and connectable, no unpaired chip
    appears, and reconnect succeeds without pairing again;
15. invoke the configured inactivity timeout and a long power press separately;
    require the normal CrossPoint sleep screen and true deep sleep in both cases;
16. start companion book transfer, cancel the Android network chooser, and
    verify the X3 automatically leaves the bounded transfer mode without a
    panic or orphaned partial EPUB.

Use bounded `adb logcat -d` reads for the `XtraordinaryBLE` tag and a bounded
serial capture. Do not leave either monitor blocking the terminal.

### 2026-07-26 advertiser isolation

The Android client must scan first, including for an already bonded X3. A bond
record proves identity; it does not prove that a connectable advertiser is
currently present. Do not call `connectGatt()` directly from the bond cache.

The scan-first Play debug build passed unit tests and was installed on the
Pixel 10. Two bounded 15-second low-latency scans produced no X3 callback. The
second scan also accepted the exact bonded X3 address, so a missing name or
service UUID could not hide a real packet. Android received hundreds of other
BLE results during the windows, proving the phone scanner was operating.
Windows simultaneously exposed the X3 USB CDC endpoint as COM7; this firmware
closes CDC before deep sleep, so the X3 was powered and running.

The current firmware's advertiser construction is byte-for-byte unchanged from
the last committed source. The candidate changes its connected clock policy,
library-transfer routing, and RV32IMC link selection, but not the startup
advertising payload or `start()` call. Therefore the bounded evidence is:

- the current X3 is not emitting a discoverable advertisement;
- direct GATT status 147 was a pre-GATT timeout, not a service-handshake bug;
- the Android direct-bond shortcut was wrong and has been removed; and
- the runtime reason for the silent advertiser is not yet established.

Do not flash or add an advertiser restart watchdog based only on this result.
The next firmware gate must obtain startup/advertiser runtime status without an
unannounced reset, or explicitly roll back to the SHA-256-verified known-good
v0.2.3 image after approval. The Android app was force-stopped after each test
so it did not continue scanning.

## 2026-07-26 failure history

- dev2 crashed while the cached CSS selector map rehashed.
- dev3 crashed while a containment attempt pre-reserved the same map.
- dev4 correctly skipped optional CSS, then exposed the underlying toolchain
  failure when `unique_ptr<Epub>` became `shared_ptr<Epub>`.
- The dev4 panic PC decoded to `amoadd.w` in
  `__gnu_cxx::__atomic_add`. The linked root `libstdc++` was RV32IMAC, but the
  X3 is RV32IMC.
- The permanent fix is the enforced compile-and-link multilib selection above,
  not another heap threshold.

## 2026-07-29 r2 retained-frame failure and recovery

The r2-only reconnect wording appeared on the physical X3 after the two-minute
timeout. That is conclusive runtime evidence that r2 booted and reached its new
Home timeout path. The mixed retained frame and unresponsive controls are
therefore r2 firmware failures, not stale firmware, an old binary, or an
unrefreshed Android cache. A successful flash write and hash check prove only
that the requested bytes reached flash; they are not a runtime acceptance
test.

The rollback source of truth is the published `xtraordinary-v0.2.3` release:

- asset: `xtraordinary-x3.bin`;
- size: `5,418,336` bytes;
- SHA-256:
  `66F46CB5CEB63FD3F07F92DF47467EDF06C20BD961FD890E265A00793687CA59`;
- tag commit: `475ae946eb0ee9382a46e5bb751198ac5a7fecb5`.

Recovery required more than the apparent esptool success:

1. Force ROM download over built-in USB JTAG and flash v0.2.3 at `0x10000`.
2. Require esptool's data hash verification.
3. Probe with a no-reset esptool command. In this incident the chip reported
   that it was staying in the bootloader because the force-download latch was
   still set.
4. Clear `RTC_CNTL_FORCE_DOWNLOAD_BOOT` and explicitly run the target.
5. If a later JTAG inspection halts the core, perform the full system reset and
   finish with `reset run`. A reset requested while halted can restart into a
   halted debug state; `reset` alone is not proof that the application is
   running.
6. Detach JTAG and do not halt the target again before the display, controls,
   and BLE validation window.

The v0.2.3 application image was written and hash-verified, and the final JTAG
sequence explicitly left the core running. This is not yet a completed
recovery gate: a person must still confirm a clean v0.2.3 screen and responsive
controls, and Android must discover and connect to the advertiser.

Three independent mistakes were found while isolating the incident:

- The firmware incorrectly treated the two-minute unbonded first-phone search
  timeout as a device-sleep trigger. That timeout must stop only first-discovery
  advertising and leave Home and its controls fully usable.
- The firmware bypassed the normal CrossPoint sleep presentation by preserving
  Home and its reconnect notice as the powered-off frame. Actual inactivity or
  long-press sleep must render the existing `SleepActivity` screen before true
  deep sleep. A Home connection chip is never a sleep screen.
- An Android delayed scan timeout was keyed only to the global `Scanning`
  phase. A timeout belonging to an older scan cancelled a newer scan after
  roughly 750 ms. Scan results, failures, and timeout closures must first prove
  that their callback is still the active callback.

The Android callback-identity correction passed the protocol tests, Play debug
unit tests, and Play debug Kotlin compilation offline. Firmware r4 was built,
flashed, and digest-verified, but it is rejected as a product candidate: it
keeps Home awake at the two-minute boundary yet still keys that boundary to
`connectedOnceSinceBoot` instead of the persisted bond state and does not
implement the required one-way first-search radio shutdown. Firmware r5 was
built but was not written to the X3. Neither candidate may be tagged or
published.

| Candidate | Artifact | Size | SHA-256 | Hardware state |
| --- | --- | ---: | --- | --- |
| r4 | `build/artifacts/xtraordinary-x3-debug-r4-2623FBBC.bin` | 5,437,728 bytes | `2623FBBCC0F7B0A8B8422E2E970FFC0D6535E0762BC6B1503AB3D805EFE409C6` | Written and digest-verified; currently on the X3, but rejected by the contract above |
| r5 | `build/artifacts/xtraordinary-x3-debug-r5-F823E981.bin` | 5,438,080 bytes | `F823E981939426286E4E9CF17FDB1086AEC7220F4C0B2E1C0FA375C01C1AEB92` | Build only; ROM mode was exited and r5 was not written |

The current correction order is documentation, diagnostics, persisted-bond
branching, one-way first-search shutdown, one bounded build, then the complete
runtime matrix. Do not combine this correction with new clock, reader, or UI
experiments, and do not build another candidate until the previous candidate's
result is recorded.

### Bounded-build correction

A command timeout is not a build-process timeout on this Windows setup. One
PlatformIO invocation outlived the timed command wrapper and left its Python,
PlatformIO, and RISC-V compiler descendants running. Before retrying any timed
firmware build:

1. inspect processes by exact executable path, command line, and build start
   time;
2. stop only descendants belonging to the timed-out build;
3. prove that no matching build process remains; and
4. do not launch another build until that proof is complete.

The sandboxed PlatformIO failure with Windows error 5 occurs in the host
toolchain wrapper before the changed source is compiled. It is an environment
boundary, not evidence that the firmware patch compiles or fails to compile.
Never convert that ambiguity into a passing build result, and never use a long
foreground build as the workaround.

After any failed flash, boot, hardware gate, or build-control attempt, add the
observed mistake and corrected recovery step to this runbook before the next
candidate is built or flashed. The note must distinguish direct device evidence
from inference and must not call a write, reset, or build successful until its
separate runtime gate passes.
