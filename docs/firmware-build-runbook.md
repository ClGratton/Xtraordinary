# X3 firmware build and hardware-test runbook

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

Start a bounded background serial capture immediately after flashing. Then:

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
13. boot to Home with the phone unavailable, avoid input for two minutes, and
    verify the gray retained-frame sleep pill appears before deep sleep;
14. start companion book transfer, cancel the Android network chooser, and
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
