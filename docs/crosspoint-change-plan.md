# Exact CrossPoint X3 change plan

Status: implementation blueprint, 2026-07-22
Firmware baseline: CrossPoint `2754a5ff01644d36cf0a17db98f28408666ba518` (`1.4.1`)

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
LOW_BATTERY
PROTOCOL_ERROR
```

Commands are idempotent by `message_id` plus object `revision`. After reconnect, Android must call `GET_STATUS` before mutating a running session. The X3 never starts focus on connect, boot, tilt, or notification receipt.

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
