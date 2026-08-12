# X3 standby input freeze — 2026-08-11

## Outcome

Open. `xtraordinary-v0.2.6-dev22-local` is deployed and protocol-ready, but it failed physical manual-sleep acceptance.

Deployed application image SHA-256: `B937AE2757D993C979DC07DF7CFD95DB676BB2D98F3761234C50A77A37C6AD76`.

## Reproduction

1. Boot the X3 and leave it on Home.
2. Confirm the buttons respond before standby.
3. Wait until Home shows **Bluetooth standby · up to 30 s**.
4. Ordinary left, right, and confirm presses still move Home during Bluetooth standby on dev19 and later.
5. Leave Home idle until the configured deep-sleep deadline.
6. On dev19, the retained Home frame remained visible, no ordinary button worked, and holding Power did not wake the USB-powered unit.

The failure occurred while USB-powered as well as in normal use. It was not caused by the phone connection or a lost Bluetooth bond.

## Disproved hypothesis

Dev18 reduced the idle-loop delay from 50 ms to 25 ms on the theory that standby polling was missing short presses. Physical testing showed no improvement. That change and its policy/documentation claim were reverted before the root-cause fix.

## Proven defect: unsupported idle clock

After three idle seconds, `HalPowerManager` requested its historical 10 MHz low-power clock. The installed Arduino ESP32 core supports 40 MHz XTAL and 20 MHz XTAL/2 modes on ESP32-C3, but exposes the 10 MHz XTAL/4 divider only for the original ESP32.

`setCpuFrequencyMhz(10)` therefore failed deterministically. The main loop retried the same rejected transition every 50 ms. On each rejection, the core's `getSupportedCpuFrequencyMhz()` allocated a 256-byte supported-frequency string that its error path did not free. Repeated failure exhausted heap until the main loop could no longer process input. The e-ink Home image remained visible, which made the failure look like a button-polling or display-navigation problem.

## Proven fix

- Set the ESP32-C3 idle floor to its supported 20 MHz XTAL/2 clock.
- Cache a rejected clock target for the lifetime of the boot so a future unsupported request fails once rather than on every loop.
- Preserve the original 50 ms idle delay; scan cadence was not the cause.
- Add build-blocking engineering policies for the supported floor and bounded rejection behavior.

## Why dev19 was not acceptance

The initial dev19 check happened after the standby label appeared but before the configured 20-minute deep-sleep deadline. It proved the 20 MHz fix kept Home responsive during standby; it did not exercise sleep entry or wake. The incident was incorrectly marked resolved and was reopened when the owner reproduced the later failure.

## Dev20 failed attempt

Dev20 tried to release the NimBLE controller before deep sleep and added an explicit **Sleeping** / hold-Power status band to the retained e-ink frame. At the real five-minute deadline, the owner still saw the unchanged Home frame and no button worked. Because `ActivityManager::goToSleep()` renders synchronously, the unchanged frame proves execution stopped before the sleep activity ran. The only new blocking operation in that interval was NimBLE's synchronous host shutdown before `goToSleep()`.

Dev20 therefore did not prove deep sleep and must not be restored.

## Dev21 failed attempt

- Render the sleep activity before starting radio teardown, so the physical frame records whether execution reached the sleep lifecycle.
- Run advertising stop, disconnect, and `NimBLEDevice::deinit(false)` in a disposable worker.
- Bound the main thread's wait for the entire vendor teardown to 750 ms. If NimBLE does not finish, log the timeout and continue into deep sleep rather than freezing Home.
- Keep `deinit(false)` so the controller can be released without deleting bond storage.
- Keep the explicit **Sleeping** / hold-Power status band for Quick Resume.

Dev21 passed its automated checks and protocol handshake, but at the real five-minute deadline the owner again saw the unchanged Home frame. Moving `goToSleep()` before the bounded NimBLE teardown therefore did not reach the render. The remaining pre-render operation was `CompanionService::syncBeforeSleep()`, which restarted advertising and could block inside vendor BLE calls.

Dev21 did not prove sleep and must not be restored as the fix.

## Dev22 failed manual-sleep acceptance

Dev22 removed the last-chance pre-sleep radio restart. Durable phone work remains queued and replays after wake instead. The intended order is now:

1. enter `SleepActivity` and render the explicit **Sleeping** band;
2. bound radio shutdown to 750 ms in a disposable worker;
3. enter deep sleep.

Deployment evidence:

- Engineering policy gate: 37 rules passed.
- Canonical `x3_companion_release` build passed with ESP32-C3 RV32IMC verified; RAM 34.8%, flash 82.9%.
- Embedded version: `xtraordinary-v0.2.6-dev22-local`.
- Application-only flash at `0x10000` completed with `Hash of data verified`; NVS, Bluetooth bond, app state, and SD contents were preserved.
- The bonded Pixel reopened status-0 GATT, subscribed notifications, received fresh capabilities and all four library pages, and ACKed radio and reader policies on 2026-08-11 at 23:57 local time.
- The owner then held Power for about one second and observed absolutely no change: Home remained visible and the **Sleeping** band did not appear.
- Android recorded no `XteinkBle` state change or disconnect after the press, consistent with the sleep transition never starting.

This disproves dev22 as a complete fix. Because even the first visible action in the ordered sleep path did not occur, tomorrow's first task is to instrument and trace the Power-button event path itself: raw GPIO state, debounce/hold recognition, event dispatch, and the call into `ActivityManager`. Do not change frequency, BLE teardown, or deep-sleep configuration until that event is proven to reach the lifecycle entry point. After fixing entry, acceptance still requires the visible sleep band, BLE disconnect, Power wake while USB-powered, a fresh post-wake handshake, and responsive ordinary buttons.

## 2026-08-12 retained-trace diagnosis

Dev24 added an 80-byte RTC-retained loop checkpoint without changing runtime
behavior. After the frozen X3 ignored repeated serial commands, one intentional
`USB_UART_CHIP_RESET` recovered the prior snapshot:

`boot=1 loops=2289 checkpoint=COMPANION_ADVERTISING_START_ENTER checkpoint_ms=92444 raw=0x00 debounced=0x00 power_held_ms=0`

The next boot independently reproduced the same boundary. It remained healthy
through the first standby pulse at 62.423 seconds and answered trace requests
through 90 seconds, then stopped before the 95-second request. This proves the
loop blocks in the synchronous NimBLE advertising restart for the second
standby pulse. It does not prove an input or debounce failure: the loop can no
longer sample any button once blocked.

The differentiating lifecycle state is CPU frequency. The first standby pulse
starts while the BLE-safe 80 MHz floor is still active. After that pulse stops,
the idle policy permits 20 MHz. The second pulse then enters `advertising_->start()`
at 20 MHz and never returns. Dev25 centralizes the reusable
`standbyPulseStartDue()` predicate and makes a due restart require full CPU
speed before the vendor call. A build-blocking policy keeps that lifecycle
requirement attached to future advertising-pulse changes.

Dev25 passed the canonical build and artifact checks, was application-only
flashed at `0x10000`, and esptool reported `Hash of data verified`; NVS, the
Bluetooth bond, Android data, and SD files were preserved. Firmware SHA-256 is
`6D058C4A9385FE12CE91BE9D2CCEE2FAABFC3E6F9D0F7C6FCB4FD16043DDB09B`.

The first automated hardware acceptance kept one COM7 session open for 155
seconds and requested the RTC trace every five seconds. The formerly fatal
second-pulse boundary returned normally: loop count was 1,884 at 90 seconds,
1,978 at 95 seconds, 2,533 at 125 seconds, and 2,993 at 150 seconds, with the
checkpoint returning to `COMPANION_LOOP_EXIT` each time. This proves the main
loop survives repeated standby advertising restarts. A fresh bonded Android
handshake also passed before the serial run: API 37 GATT, encrypted service
setup, notifications, and capabilities.

Physical button response and deep-sleep render/wake still require a separately
announced owner-visible acceptance sequence. Do not silently start a timed
manual test or ask for an input after its useful window has already passed.

After that automated run, the owner accidentally pressed a side button while
the X3 was in standby and observed that navigation worked. This is direct
physical confirmation that dev25 cleared the prior all-buttons-locked symptom.
The sleep screen was not expected during the 155-second run: the saved policy
is fast discovery 1 minute, standby every 30 seconds, and automatic sleep after
5 minutes; any accepted button event restarts the inactivity deadline.

## Regression rule

Do not treat visible e-ink immobility as proof of an input-scan defect or sleep. Trace the entire loop, power transition, allocation/logging path, input state, activity dispatch, render request, controller teardown, sleep entry, and wake source. Any deterministic hardware-configuration rejection must be bounded. Every third-party teardown call on the power-off path must also be behind a deadline, and physical acceptance must cross the configured deep-sleep deadline. A failed manual Power hold with no render and no BLE disconnect must be diagnosed at the input/event boundary before changing downstream sleep code.
