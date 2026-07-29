# Exact CrossPoint X3 change plan

Status: first functional X3 vertical slice implemented, 2026-07-22
Firmware baseline: CrossPoint `2754a5ff01644d36cf0a17db98f28408666ba518` (`1.4.1`)

Implemented in the first vertical slice: the bounded v1 envelope and payload codecs, fixed FreeRTOS command queue, bonded/encrypted BLE control and data writes, generic local session engine and focus activity, bounded SD library scan/delete reconciliation, streamed firmware staging with manifest SHA-256 plus existing ESP image validation, inactive-slot flashing, Android GATT client, and GitHub Release manifest polling. Scene/card transport, physical passkey UI, reboot-persistent sessions, and X4-family builds remain later gates; they are not represented as complete.

## The firmware feature being added

CrossPoint does **not** get a Pomodoro app, ticket app, notification app, or companion launcher. It gets one optional X3-only companion runtime that can:

- accept a phone-rendered monochrome scene;
- update byte-aligned regions without replacing the scene model;
- run a generic deadline/session locally;
- show immediate overlays and a bounded deferred-card digest;
- turn logical button presses into generic action events;
- keep an interactive scene awake while BLE is required; and
- pin the already-rendered framebuffer and power off without replacing it with CrossPoint's normal sleep screen.

The Android app supplies the product meaning. A focus screen, a boarding pass, and a flight-status pane are all different scene payloads using the same firmware code.

## Build isolation

### Modify `firmware/platformio.ini`

Add an `x3_companion` environment rather than enabling BLE in the existing default and release builds immediately:

```ini
[env:x3_companion]
extends = base
lib_deps =
  ${base.lib_deps}
  h2zero/NimBLE-Arduino @ 2.5.0
build_flags =
  ${base.build_flags}
  -DENABLE_X3_COMPANION=1
  -DENABLE_SERIAL_LOG
  -DLOG_LEVEL=2
```

The code is also runtime-gated by `gpio.deviceIsX3()`. The normal CrossPoint targets remain unchanged until the memory, power, reconnect, and reader-regression gates pass. NimBLE is the intended stack because this is BLE-only and Espressif recommends it for the lower code and runtime-memory footprint.

## Files to add

### Core runtime under `firmware/src/companion/`

| New file | Exact responsibility | Must not do |
|---|---|---|
| `CompanionTypes.h` | Fixed-width wire/storage types, limits, scene/session/card/action IDs, rectangle and power-policy enums. | No `String`, unbounded vectors, rendering, or BLE calls. |
| `CompanionProtocol.h/.cpp` | Decode and validate the binary envelope; check magic, version, type, payload length, sequence, and CRC32; encode ACK/NACK/status/events. | No SD, renderer, activity, or NimBLE access. |
| `CompanionCommandQueue.h` | Fixed-capacity ring buffers between BLE callbacks and the main loop. Separate small control records from scene data descriptors. | No allocation after `begin()` and no blocking in callbacks. |
| `BleTransport.h/.cpp` | One NimBLE GATT peripheral, advertising, one bonded central, MTU-aware writes, notifications, connection state, passkey display events, and bond deletion. | No rendering, SD access, session mutation, or command interpretation inside callbacks. |
| `SceneStore.h/.cpp` | Stream scene chunks to a temporary SD file, verify expected length/CRC, atomically rename, load into the existing framebuffer, and patch aligned regions. | Never hold another full 52,272-byte framebuffer in RAM. |
| `SceneRenderer.h/.cpp` | Copy an active stored frame into `GfxRenderer`, draw the small supported dynamic fields, overlays, selection state, and button hints. | Never decode product-specific objects or regenerate barcodes. |
| `SessionEngine.h/.cpp` | Generic `RUNNING/PAUSED/FINISHED/CANCELLED` state, absolute UTC deadline, minute-boundary invalidation, idempotent transitions, and reboot recovery. | No `Pomodoro` naming or focus-specific duration presets. |
| `CardQueue.h/.cpp` | Fixed-count, fixed-byte-cap immediate/deferred cards with expiry and session association; render-order selection. | No arbitrary JSON or unbounded notification history. |
| `CompanionService.h/.cpp` | The only coordinator: drain commands on the main loop, call storage/session/card components, request activities/renders, emit events, reconcile reconnect state, and expose auto-sleep/pin requests to `main.cpp`. | No work from NimBLE callbacks and no direct panel-driver commands. |

Starting limits are deliberately finite and negotiated in `CAPABILITIES`: one active scene, eight dynamic fields, eight actions, four immediate overlays, 32 deferred cards, a 64 KiB scene-object maximum, and a total deferred-card byte cap. These are prototype limits, not promises; hardware measurements can lower them before protocol v1 is frozen.

### Activities under `firmware/src/activities/companion/`

| New file | Exact responsibility |
|---|---|
| `CompanionSceneActivity.h/.cpp` | The sole visible companion surface. It asks `SceneRenderer` to paint, maps logical buttons to local or phone actions, prevents normal auto-sleep only for an active session/live scene, and reports the rendered scene revision back to `CompanionService`. |
| `CompanionPairingActivity.h/.cpp` | Shows the six-digit display-only passkey, phone name/address when available, timeout, and cancel path. It is available only during a time-limited pairing window opened physically from CrossPoint Settings. |
| `CompanionSettingsActivity.h/.cpp` | Minimal recovery surface inside CrossPoint Settings: enabled/build status, advertising/connected state, paired-phone identity, `Pair new phone`, and `Forget phone`. It is not a companion-app launcher. |

The pairing activity uses a display-only X3/passkey-entry-on-phone flow. BLE bonding, MITM protection, and Secure Connections are required. An unbonded central is rejected outside the physical pairing window, and protocol writes are rejected until the link is encrypted and the central matches the one retained bond.

### Host tests

Add:

```text
firmware/test/companion_protocol/
  CMakeLists.txt
  CompanionProtocolTest.cpp
firmware/test/companion_session/
  CMakeLists.txt
  SessionEngineTest.cpp
firmware/test/companion_store/
  CMakeLists.txt
  SceneStoreFormatTest.cpp
```

Modify `firmware/test/CMakeLists.txt` to register all three. Protocol tests consume the same binary fixtures as Kotlin from repository-level `protocol/test-vectors/`.

## Existing files to modify

| Existing file | Exact change | Phase |
|---|---|---|
| `firmware/platformio.ini` | Add isolated X3 companion build and pinned NimBLE dependency. | 0 |
| `firmware/src/main.cpp` | Construct `CompanionService`; call `begin()` after hardware, SD, settings, display, and initial activity setup; call `loop()` before auto-sleep evaluation; include `companionService.preventAutoSleep()` in activity detection; process a verified preserve-frame sleep request after the activity/render loop. | 0-1 |
| `firmware/src/activities/Activity.h` | Add `virtual bool isCompanionActivity() const` returning false. | 1 |
| `firmware/src/activities/ActivityManager.h/.cpp` | Add `showCompanionScene()`, `showCompanionPairing()`, and `isCompanionActivity()`. Push the scene over the current activity so Back restores the reader/menu; never push a duplicate companion activity. | 1 |
| `firmware/src/activities/settings/SettingsActivity.h/.cpp` | Add `SettingAction::Companion`, append one `Companion` action to System settings only in the companion build on X3, and open `CompanionSettingsActivity`. | 0 |
| `firmware/lib/I18n/translations/english.yaml` | Add the small set of pairing, connection, forget-phone, degraded, pin, and local-action strings. Other locales inherit English until translated. | 0-2 |
| `firmware/lib/hal/HalClock.h/.cpp` | Add full UTC date/time read and write for all DS3231 registers; make NTP sync persist the date as well as time; retain `getTime()` as a compatibility wrapper. | 2 |
| `firmware/lib/hal/HalDisplay.h/.cpp` | Expose `displayWindow()` through the HAL only when the lower driver reports real support; until then companion rendering requests a normal fast full-frame refresh. | 5 |
| `firmware/open-x4-sdk/libs/display/EInkDisplay/include/EInkDisplay.h` | Add an explicit capability such as `supportsWindowRefresh()`; return false for X3 until the driver task below passes physical tests. | 1 |
| `firmware/open-x4-sdk/libs/display/EInkDisplay/src/EInkDisplay.cpp` | Later replace the X3 `displayWindow()` full-frame fallback with a measured PTL (`0x91/0x90/0x92`) implementation that preserves DTM1/DTM2 differential state. Keep the existing fallback if validation fails. | 5 |

### `main.cpp` power-off change

The existing `enterDeepSleep()` always opens `SleepActivity`, which would overwrite a ticket just before the MCU powers off. Refactor it to accept an internal presentation policy:

```text
CrossPointSleep   -> existing SleepActivity path
PreserveFrame     -> do not render; persist companion state; shut radios/panel down
```

`PIN_SCENE_AND_SLEEP` is acknowledged only after `CompanionSceneActivity` reports that the requested scene revision was physically rendered. Then `CompanionService` exposes a one-shot preserve-frame sleep request, which `main.cpp` consumes after `activityManager.loop()`. The normal long-power and timeout paths continue to use `CrossPointSleep`.

Do not modify the user's `/sleep.bmp`, `/.sleep/`, sleep-mode setting, or `/.crosspoint/sleep_frame.bin` behavior.

## Storage layout and atomicity

All new files live under the existing CrossPoint namespace:

```text
/.crosspoint/companion/
  active_scene.bin
  active_scene.tmp
  active_session.bin
  active_session.tmp
  deferred_cards.bin
  deferred_cards.tmp
```

Each persisted object starts with `formatVersion`, `headerLength`, `payloadLength`, `revision`, and `crc32`. The write sequence is `open tmp -> stream -> close -> reopen/verify -> remove stale target -> rename tmp`. All access goes through `HalStorage`, so its recursive mutex continues to serialize SD operations.

The scene payload is the physical X3 792 x 528, one-bit framebuffer order already used by `EInkDisplay`, not a BMP. A committed scene is therefore exactly 52,272 payload bytes for the current X3 capability. Region patches require `x` and `width` to be multiples of eight in protocol v0.

## BLE service and protocol v0

Use one vendor-specific service with four characteristics:

| Characteristic | Properties | Purpose |
|---|---|---|
| `control_rx` | encrypted write with response | Commands and small payloads. |
| `scene_rx` | encrypted write with response | Ordered scene chunks. Reliability first; write-without-response can be added after measurement. |
| `events_tx` | encrypted notify | ACK/NACK, action, session, low-battery, pairing, and error events. |
| `status` | encrypted read + notify | Capabilities and reconciled current state. |

Envelope:

```text
magic | protocol_version | message_type | flags
message_id | sequence | payload_length | payload_crc32
payload
```

Required phone-to-X3 messages:

```text
HELLO
GET_CAPABILITIES
GET_STATUS
GET_LIBRARY
LIBRARY_PAGE
DELETE_LIBRARY_ENTRIES
BEGIN_SCENE
SCENE_CHUNK
COMMIT_SCENE
ACTIVATE_SCENE
UPDATE_REGION
START_SESSION
PAUSE_SESSION
RESUME_SESSION
END_SESSION
SHOW_OVERLAY
DISMISS_OVERLAY
QUEUE_CARD
REMOVE_CARD
PIN_SCENE_AND_SLEEP
RETURN_TO_READER
```

Required X3-to-phone events:

```text
ACK / NACK
STATUS_CHANGED
ACTION_INVOKED
SESSION_FINISHED
SCENE_RENDERED
LIBRARY_CHANGED
LOW_BATTERY
PROTOCOL_ERROR
```

Commands are idempotent by `message_id` plus object `revision`. After reconnect, Android must call `GET_STATUS` before mutating a running session. The X3 never starts focus on connect, boot, tilt, or notification receipt.

### Device library authority

CrossPoint's native reader does not maintain a master catalog. `FileBrowserActivity` enumerates the live SD directory when a folder opens, while per-book metadata and rendering caches live under `/.crosspoint/`. The current web client follows the same model through `/api/files`, `/upload`, and `/delete`.

The companion app therefore treats the X3 filesystem as authoritative for `On X3`:

- `GET_LIBRARY` requests a bounded recursive scan of supported book files, excluding hidden and system directories. `LIBRARY_PAGE` streams path, format, byte size, cached title and author when available, and a cheap device fingerprint in pages rather than allocating one unbounded manifest.
- Android merges that snapshot with its phone-side imports. A book copied to the SD card from a PC appears after the next connection or refresh even if the phone has never imported it.
- The device increments a persisted library revision after app upload or delete, native deletion, web upload or delete, and SD remount. `LIBRARY_CHANGED` invalidates the phone snapshot; manual PC changes are discovered by the remount scan.
- `DELETE_LIBRARY_ENTRIES` carries normalized paths plus the snapshot fingerprint and is rejected on mismatch. Android keeps rows visible until the X3 acknowledges deletion and returns the new library revision.
- Inventory and deletion use bonded BLE because they are small control operations. EPUB transfer may reuse the existing authenticated Wi-Fi and WebSocket path after an explicit, time-limited escalation. Making the web server the only library client would require Wi-Fi setup and would not provide reliable background reconciliation.

## Rendering behavior

### Focus

- Android sends a complete base scene and a generic countdown field with a UTC deadline.
- `SessionEngine` invalidates the field at minute changes and state boundaries, not every second.
- `CompanionSceneActivity` remains the current activity and returns true from `preventAutoSleep()`.
- If BLE disconnects, the deadline and already-persisted deferred cards continue locally.

### Static ticket

- Android sends a complete ticket scene with a regenerated barcode.
- The X3 renders it once, confirms the scene revision, and follows `PreserveFrame` power-off.
- No remote update is possible while powered off. Physical power-on returns to normal CrossPoint; Android can then reconnect and resend.

### Ticket with live pane

- The barcode is part of the immutable base scene; status text is a separate byte-aligned region.
- Android sends `UPDATE_REGION` only when gate, time, boarding status, or freshness changes.
- The X3 remains awake and connected, so this mode consumes more power than static pinning.
- In protocol v0 the framebuffer region changes but the X3 performs a full fast refresh because current X3 `displayWindow()` refreshes the full screen. True window refresh is a later driver optimization, not a prerequisite for the layout.

## Deliberately unchanged files

- `HomeActivity.*`: no companion launcher item is added.
- `CrossPointState.*`: reader state remains separate from companion state.
- `CrossPointSettings.*`: no Pomodoro duration, flight, or app-feature settings are stored in firmware.
- `SleepActivity.*`: normal CrossPoint sleep screens keep their current behavior.
- `CrossPointWebServer.*`: it is not auto-started or reused for v0 traffic.

## Implementation order and gates

### Phase 0 - BLE/memory spike

Add the build profile, `BleTransport`, a minimal `CompanionService`, settings/pairing recovery, `HELLO`, `GET_CAPABILITIES`, and `GET_STATUS`. Measure binary size, boot heap, minimum heap, maximum allocation, reconnect behavior, connected idle current, and advertising current.

Gate: X3 only, one bonded phone reconnects reliably, reader behavior is unchanged, and minimum free heap stays above the project's 50 KiB human-review threshold during stress.

#### BLE power and reconnect safety gate

The 2026-07-25 hardware test established two constraints that must be treated as implementation gates:

- NimBLE reporting its advertiser as active is not proof that a phone can discover the X3 over the air. Verify discovery from a disconnected phone at 3, 30, and 120 seconds, and again after every disconnect.
- Calling the NimBLE advertiser stop/start path after manually lowering the ESP32-C3 CPU to 10 MHz caused an interrupt-watchdog reset loop. A connection happened to cancel the pending restart and therefore stopped that loop; it did not make the restart safe.

Do not add an advertising restart watchdog at the manually lowered clock, and do not replace it with an Android timer that scans/connects every few seconds. The peripheral must first be discoverable, and periodic phone wakeups would spend phone and X3 battery without repairing a silent advertiser.

The 2026-07-26 follow-up also rejected dynamic advertiser mutation at the interim 80 MHz floor. Stopping the active advertiser at the 60-second fast-to-slow boundary blocked the firmware main loop: physical buttons stopped responding and the phone could not discover the X3. The phone then restored the SHA-256-verified v0.2.3 image and recovered both BLE and controls. Until the project owns a power-management-enabled framework build, the safe interim policy is one fixed 500 ms advertising interval, one start during BLE initialization, NimBLE's normal advertise-on-disconnect behavior, an 80 MHz disconnected BLE floor, and normal speed while connected. There is no runtime advertiser stop, restart watchdog, or interval mutation.

That interim build was discovered 82 seconds after disconnect and reached GATT Connected in 1.36 seconds from scan start, past the previous failure boundary. Android was separately verified to transition Connected -> Disconnected when backgrounded and Scanning -> Connected when foregrounded, without periodic polling. Physical controls remain a mandatory hardware check before release.

The next bounded battery step does not pretend that the precompiled framework has modem sleep. It keeps the proven 80 MHz BLE floor, requests a 60-100 ms connection interval with one interval of peripheral latency, and holds 160 MHz only for the first five seconds after a connection or BLE traffic. BLE callbacks merely stamp activity and enqueue data; the main loop restores full speed before it handles a command or renders. Android separately treats a paired XTEINK as a logical managed device while the transport is intentionally idle, queues focus/deletion intent, and only labels the device `Reconnecting` after an unexpected link failure. Library pages advance only when NimBLE accepts the notification, so transmit congestion cannot silently turn a non-empty SD library into a zero-book snapshot. SD inventory scans also rewind every directory, close skipped entries, filter generated diagnostics, and construct root paths with the filename argument rather than accidentally reducing every root file to `//`.

The 2026-07-26 dev2-dev4 hardware runs failed the reader-stability gate and therefore must not be treated as release candidates. NimBLE initialization reduced free heap from 137,256 bytes to roughly 72 KiB; the normal post-boot UI then settled near 55 KiB. Dev2 crashed while the cached CSS selector map rehashed, and dev3 crashed while a containment attempt pre-reserved that map. Dev4 correctly skipped optional CSS at low heap, then exposed the actual architecture error when the loaded EPUB transitioned from `unique_ptr` to `shared_ptr`.

The dev4 panic instruction was `amoadd.w` inside `__gnu_cxx::__atomic_add`. The X3's ESP32-C3 implements RV32IMC and has no RISC-V `A` extension, but PlatformIO had linked the toolchain's root RV32IMAC `libstdc++`. The permanent fix is to compile and link with `-march=rv32imc_zicsr_zifencei -mabi=ilp32`, prepend the matching multilib directory, and fail the build unless the link map proves that `atomicity.o` came from `rv32imc_zicsr_zifencei/ilp32/libstdc++.a`. The exact procedure and fast incremental-build rules are in [the X3 firmware runbook](firmware-build-runbook.md).

Low-memory CSS remains optional: cached CSS is skipped when BLE leaves insufficient reader headroom. That containment avoids spending the reader's safety reserve, but it is not a substitute for the ISA fix and does not make the BLE memory or power audit complete.

The first architecture-correct dev5 hardware run opened Project Hail Mary, rendered the reader, completed multiple display refreshes, returned to the library, and remained alive at roughly 55 KiB free. It also exposed a separate nonfatal memory boundary: library cover regeneration declined to start because the JPEG decoder required 53,248 bytes while 53,228 bytes were free. Keep that thumbnail failure in the BLE memory backlog; do not confuse it with the resolved reader panic or claim the complete memory audit has passed.

### Battery-aware device state contract

The companion protocol now separates the physical BLE transport from the X3's
operational state:

- `Awake`, `Focus`, and `Transfer` request normal sync.
- `Reading` requests slow sync.
- `Sleeping` requests no sync.

Each status carries a monotonically changing boot-local revision. Android
updates its UI first and confirms that exact revision; the firmware ignores
stale confirmations. Only a confirmed `Reading` revision requests the longer
200-300 ms BLE connection parameters. Any transition out of Reading
immediately returns to the normal 60-100 ms parameters. The CPU remains at the
proven normal clock for the lifetime of an attached GATT client; this handshake
does not reintroduce the failed mid-link clock drop.

Android stores separate normal and reading check-in intervals. They control
foreground reconnect cadence and the grace period before a missing Reading
check-in is shown as offline. They are not presented as Bluetooth connection
intervals and do not pretend to enable ESP32 modem sleep. Backgrounding the app
still intentionally closes GATT. The last confirmed `Reading` or `Sleeping`
state remains visible instead of being collapsed into `Disconnected`.
Foregrounding the app is an explicit user action and performs one immediate
reconnect attempt even when the cached state is slow or sleeping.

The following edge cases are deterministic:

| Event | Firmware behavior | Android behavior |
| --- | --- | --- |
| Reader opens while connected | Sends revisioned `Reading/Slow`; applies slow BLE parameters only after confirmation | Shows `Reading - low-power sync`, confirms the revision |
| Reader opens while the app is absent | Continues locally; never waits indefinitely for a phone | Learns the state on the next foreground connection |
| Reader closes while connected | Sends a new `Awake/Fast` revision and restores normal parameters | Replaces Reading immediately |
| Reader closes while Android is backgrounded | Advertises using the existing fixed safe policy | Learns Awake on the next user-driven connection; Android cannot be woken for free |
| X3 enters deep sleep while connected | Sends `Sleeping/Off` before display and modem teardown | Caches Sleeping and stops automatic retries |
| X3 is already off | Cannot transmit from deep sleep | Keeps Sleeping; a user wake plus foreground reconnect refreshes state |
| No phone connects for two inactive minutes after boot | Paints a retained gray Home pill, then deep-sleeps without replacing that frame | Reconnects normally after the user wakes the X3 |
| App-configured idle timeout | Clamped and persisted to 1-5 minutes | Offers 1, 2, 3, or 5 minutes |
| Transfer permission or Wi-Fi preflight fails | Never enters transfer mode | Shows a product message, never a raw permission exception |
| Transfer mode receives no upload | Leaves after 90 seconds; a stalled upload leaves after 60 seconds; a completed batch gets an 8-second grace | Releases the temporary network and reconciles the library on reconnect |

The remaining active-reading drain cannot be honestly solved by Android
polling alone: the current precompiled framework still holds an 80 MHz BLE-safe
floor while disconnected advertising is active. Runtime advertiser stop/start
remains prohibited by the hardware failures above. The owned-framework modem
sleep milestone is still required before claiming the reader-idle battery audit
complete.

The target behavior is:

1. A physical wake or first-time setup opens a bounded fast-advertising window.
2. A bonded, awake, disconnected X3 uses a measured slow connectable-advertising interval.
3. The Android app connects when foregrounded or when the user starts a device operation; it keeps the link only for a transaction or an active live feature.
4. Focus timing remains local on the X3, so the phone may disconnect without interrupting the session.
5. CrossPoint's configured inactivity timeout remains the authority for powering the device off. Physical wake reopens the appropriate advertising state.

Before changing the policy, replace the raw companion-idle frequency switch with ESP-IDF-coordinated power management/modem sleep, or prove a safe minimum clock on hardware. BLE callbacks may only record state; any advertising transition runs on the main loop at a proven-safe clock. Measure fast advertising, slow advertising, connected idle, transaction, focus, and reader-idle current before choosing intervals. Keep the last known-good released image as the rollback baseline until the complete reconnect matrix passes without watchdog resets.

The current pioarduino framework is precompiled with both `CONFIG_PM_ENABLE` and Bluetooth controller modem sleep disabled. Defining those names only in application build flags does not rebuild the linked controller and power-management libraries and must not be presented as enabling the feature. Track a separate framework milestone that either:

- rebuilds the Arduino framework libraries from an owned `sdkconfig`, or
- moves the X3 companion target to Arduino as an ESP-IDF component so the project owns those settings.

The stock ESP32-C3 framework also enables NimBLE central, peripheral, broadcaster, and observer roles, three simultaneous connections, a 5,120-byte host task stack, and the default MSYS pools. Xtraordinary uses one peripheral connection. The owned framework milestone must reduce those roles, connection slots, and buffers only after the 247-byte ATT MTU protocol path and reconnect matrix pass with the smaller configuration.

That milestone is a measured replacement for the interim 80 MHz BLE-safe floor, not a speculative cleanup. Compare it against the known-good firmware and the 80 MHz implementation for reader boot, EPUB open, page turn, rendering, pairing, encrypted service discovery, library transfer, focus updates, input latency, reconnect reliability, watchdog resets, heap, and current draw. Coordinated dynamic frequency scaling should be allowed to return to 160 MHz for active work and lower the clock only when idle; keep it only if the hardware results preserve or improve interactive performance while reducing energy use.

### Phase 1 - scene/action vertical slice

Add protocol fixtures, streaming scene storage, scene activity/rendering, activity push/pop, and one action event.

Gate: a 52,272-byte frame survives disconnect/retry without a second framebuffer; invalid length/CRC/sequence is rejected; Back restores the exact previous reader/menu surface.

### Phase 2 - generic session

Extend `HalClock`, add `SessionEngine`, local minute updates, completion scene, pause/resume, persistence, and reconnect reconciliation.

Gate: deadline completion works with the phone out of range; midnight, duplicate commands, reboot-after-wake, and RTC failures have deterministic results.

### Phase 3 - notification cards

Add bounded cards, immediate overlay, deferred digest, dismiss/open-on-phone actions, and overflow status.

Gate: no BLE callback renders or writes SD; priority and overflow policies are deterministic; a render during a BLE burst does not lose input.

### Phase 4 - pin and power off

Add preserve-frame sleep handshake and static ticket validation.

Gate: the correct scene remains on the panel after complete X3 power-off; normal reader sleep screens and quick resume are unchanged.

### Phase 5 - live pane and true partial-refresh investigation

Ship live pane first on full fast refresh, triggered only by changed status. In parallel, implement and physically test the X3 PTL path behind a runtime capability.

Gate for advertising partial refresh support: at least 100 mixed-region updates with correct unchanged pixels, no DTM desynchronization, bounded ghosting, and safe recovery through full/half refresh. If it fails, keep reporting `partial_refresh=false` and retain full refresh.

## Why this is the exact boundary

This plan changes CrossPoint only where the companion runtime crosses an existing responsibility: build configuration, the main loop, visible activity routing, a recovery setting, UTC RTC access, and eventually the display HAL. Everything feature-specific remains either in new isolated firmware files or in Android. That keeps the reader stable and makes removal of the experiment straightforward.

## Primary references

- [Espressif: NimBLE is recommended for BLE-only, resource-constrained ESP32-C3 applications](https://docs.espressif.com/projects/esp-idf/en/latest/esp32c3/api-reference/bluetooth/index.html)
- [NimBLE-Arduino repository and PlatformIO setup](https://github.com/h2zero/NimBLE-Arduino)
