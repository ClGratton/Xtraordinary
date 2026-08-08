# X3 power, Bluetooth, synchronization, and app-state flow

**Canonical behavior for:** Android `0.2.0-dev9` and X3 firmware `xtraordinary-v0.2.6-dev7`
**Defaults:** 5-minute fast discovery, 2-second slow BLE interval, 10-minute inactivity sleep, full reader cleanup every 15 pages.

## Terms and sources of truth

The X3 is the source of truth for what is physically displayed, its battery state, its current activity, and its library. The phone is the source of truth for phone-managed radio timing and reader-cleanup preferences.

The app keeps two distinct concepts:

- **Desired state:** what the user selected on the phone. It is saved immediately and may still be waiting for delivery.
- **Acknowledged device state:** a command the X3 accepted and persisted. The app calls settings synchronized only after the matching acknowledgements arrive.

The X3 is a BLE peripheral. It cannot initiate a GATT connection or relaunch an Android process. “X3 asks the phone” therefore means that X3 opens a fast advertising window and publishes status/capabilities; a running app with pending work is scanning and connects to it.

## What the app says

| App text | Exact meaning |
|---|---|
| **No device** / **None connected** | No managed X3 is stored in the app. |
| **Paired** | The app remembers the bonded X3, but no GATT transport is open and no retry has failed into the pending-work loop. This does not mean Bluetooth is currently connected. |
| **Reconnecting…** | The app has work that still requires the X3 and is scanning/retrying after a failed attempt. Reading, a static ticket, or deep sleep can make this expected. It is not presented as a command failure. |
| **Connected** | An encrypted GATT transport is open and event notifications are subscribed; capabilities/status follow immediately in the handshake. |
| **Saved on phone · waiting for X3** | At least one phone-managed setting has not yet received both required X3 acknowledgements. |
| **Synced to X3** | The exact current radio policy and reader refresh value were ACKed in this GATT session. |
| **Waiting for X3** on Send | The complete ticket payload is durably queued on the phone and will be sent when X3 becomes reachable. |
| **Exit ticket on X3** | A static-ticket deletion is queued. Static mode has intentionally silenced BLE; Back on X3 returns to Home and opens the delivery window. |

The top bar shows the last X3 battery percentage received. A lightning mark is shown only while the current live transport reports USB charging; after disconnection the percentage remains as the last known value, but charging is no longer asserted.

## Connection handshake

When Android finds the advertised X3:

1. Android opens encrypted GATT on the LE 1M PHY, discovers the companion service, negotiates MTU 247/256, and subscribes to events.
2. Android sends `Hello`, `GetStatus`, `GetLibrary`, the phone's UTC clock, and `GetReadingStats`.
3. X3 returns:
   - `Capabilities`: model, firmware version, library revision, ticket presence, firmware-update support, and reader-policy support;
   - `StatusChanged`: activity revision, Awake/Reading, Fast/Slow mode, battery percentage, and charging flag;
   - revisioned `LibraryPage` messages.
4. Android sends `AckStatus` for the exact status revision.
5. Once per successful GATT session Android reapplies the phone-authoritative policy:
   - `SetRadioPolicy(fast window, slow interval, sleep timeout)`;
   - `SetReaderPolicy(full-refresh page count)`.
6. X3 validates and persists each command, then sends an `Ack` containing that command's message ID.
7. Only after both ACKs for the latest selected values does Android clear `sync_pending` and display **Synced to X3**.

If the user changes another setting while an older write is in flight, the older ACK cannot mark the newer values synchronized. The sync loop takes a new snapshot, sends the new values, and waits for their ACKs.

## Default power policy

| Time/state | BLE behavior | CPU/power behavior | App behavior |
|---|---|---|---|
| Boot or Home, 0–5 minutes | Fast legacy advertising every 500 ms. Any Home button activity restarts this window. | Full speed while the radio is being restarted and for five seconds after BLE traffic; otherwise the BLE-safe 80 MHz floor. | A foreground app can connect. Pending work keeps retrying. |
| Home, 5–10 minutes | Slow advertising every 2 seconds. Home shows **Low-power Bluetooth — Press any button for fast connection**. | BLE-safe 80 MHz floor. | Connection can take up to the slow advertising interval plus Android scan/connect time. |
| Home, 10 minutes without activity | Advertising stops and X3 enters its normal deep-sleep path. | Hardware deep sleep/power latch behavior from CrossPoint. | App shows **Paired** if idle, or continues to hold durable pending work for the next wake. |
| Connected control traffic | GATT is active; after three seconds X3 requests the configured slow connection interval (2 seconds by default). | 160 MHz for five seconds after traffic, then 80 MHz while BLE remains required. | **Connected**. Commands and status are revisioned/ACKed. |
| Radio quiet | No advertising and no GATT. | After three seconds idle the CPU may fall to 10 MHz. | Last battery percentage remains visible as cached data; status is **Paired** unless work is waiting. |

Configurable app choices:

- Fast discovery: 1, 5, or 10 minutes; default 5.
- Focus/Live slow advertising and connection interval: 1, 2, or 4 seconds; default 2.
- Inactivity sleep: 5, 10, or 20 minutes; default 10 and always longer than the fast window.
- Reader full cleanup: every 1, 5, 10, 15, or 30 pages; default 15.

## Home

- Boot, leaving Reading, leaving a static ticket, and Home button activity arm a fresh fast-discovery window.
- Fast advertising transitions to the visible low-power 2-second advertising state at the configured fast-window deadline.
- At the inactivity deadline, Home sleeps.
- A phone connection does not alter pairing. After the app goes to the background, idle Home GATT is released after a 1.5-second grace period unless durable work, Focus, Live, or firmware transfer still needs it.

## Reading

- Entering Reading with no connected phone stops advertising immediately. Page buttons do not wake BLE.
- If GATT was already connected, X3 sends the Reading/Slow status. Android lets already-queued desired-state commands finish and then releases GATT; X3 remains radio-quiet.
- A phone setting, ticket send, ticket clear, Focus command, or library deletion made while Reading is saved/queued and does not produce an error toast merely because X3 is silent.
- The running app retries with backoff while work exists. When the user exits Reading, X3 changes to Awake/Fast and advertises; Android connects and drains the pending work.
- Reading still follows the configured inactivity sleep timeout. Page/button/tilt activity resets that timeout.
- Reader cleanup uses the last value ACKed and persisted on X3. A phone choice of 30 made while Reading continues affects the X3 only after exit/direct-sleep synchronization and ACK; the app clearly remains **waiting for X3** until then.

## Direct power-off without visiting Home

- Before the normal sleep activity and power-latch sequence, X3 leaves Reading's radio-quiet state and offers a 1.2-second fast synchronization window.
- A running Android app with queued work is already scanning, so it can connect, receive capabilities/status, send queued commands, and receive ACKs.
- If no phone connects, sleep continues after the bounded window; X3 does not wait indefinitely.
- If Android has killed the app process, X3 cannot relaunch it under the current non-CDM pairing design. Durable work remains on the phone and resumes on the next app launch/wake.

## Reading statistics

- Opening an EPUB, TXT/Markdown, or XTC reader starts a local candidate session. It is discarded on exit when fewer than three displayed pages were completed.
- A normal page turn closes the outgoing page sample before the next page is rendered. The sample stores its displayed duration, page number, and the words actually present in the laid-out page.
- EPUB words come from the rendered `PageLine` text blocks; TXT/Markdown words come from the exact wrapped lines sent to the display. Existing XTC pages are bitmap-only, so they retain page/time history but explicitly report word pace as unavailable.
- Each completed page sample is appended and flushed to `/.crosspoint/reading-stats/active.bin`; it does not depend on a phone connection.
- On reader exit or direct sleep, sessions of three or more pages receive a footer and are atomically renamed into the pending-session queue. Up to 128 completed sessions and 2,048 pages per session are bounded on X3; when that session bound is exhausted, the oldest pending session is retired.
- On connection Android requests the pending queue. X3 transfers at most 32 page samples per notification. Android assembles by session id, rejects out-of-order chunks, persists the complete session, then sends `AckReadingStats(sessionId)`.
- Only that persisted-session ACK deletes the X3 copy. A disconnect before the ACK leaves the completed session intact for an idempotent resend.
- Android shows cumulative pages, words, active reading time, average WPM, session pace, longest session, and per-session page pace under **Tools → Reading stats**. The app copy remains available offline.
- Android retains every raw page sample, but presentation totals exclude page visits shorter than the user-selected minimum (Off, 5, 10, or 15 seconds; default 5 seconds). EPUB/TXT samples above the fixed 1,000 WPM credibility ceiling are also hidden. XTC has no word count, so only the minimum-time rule applies. Changing the setting recomputes the view without destroying history.
- Android commits the complete session synchronously on an IO dispatcher before authorizing `AckReadingStats`. Its inbound queue holds all 64 chunks of the maximum-size session, preventing a persistence pause from dropping a middle chunk.
- Android sets the X3's battery-backed UTC RTC during each handshake so new sessions carry real timestamps. A session recorded before a valid clock existed is dated at first successful phone import while preserving its measured active duration.

### Periodic reader cleanup timing

The configured N-page cleanup currently reaches `ReaderUtils::displayWithRefreshCycle()`, which asks for `HALF_REFRESH`. On X3, `HalDisplay::displayBuffer()` immediately calls `requestResync(1)` for every half refresh. The driver therefore promotes that request to a full resync, waits for panel power-on before the first visible display-refresh command, then runs one normal conditioning pass and a no-op post-full fast settle. This forced sequence, not EPUB pagination or an application `delay()`, explains the observed roughly 2.5-second pause before the panel visibly goes black. Do not tune the delay in isolation: changing this path requires a physical ghosting and first-fast-after-cleanup test because the extra passes were added to avoid residue and a garbled first differential update.

## Static ticket

- Android queues and persists the complete payload before attempting transport. The button reads **Waiting for X3** until `ShowTicket` is ACKed.
- X3 validates and persists the ticket, ACKs first, renders it, disables advertise-on-disconnect, and keeps the e-ink image.
- Android keeps the first bonded session for five seconds to avoid Android's stale post-bond service-discovery GATT, then disconnects.
- X3 has no BLE polling in this state and runs at its radio-quiet CPU floor.
- Remove is also durable. While the ticket is still open, the app says **Exit ticket on X3**. Back on X3 clears the static quiet guard, returns Home, and opens fast advertising so `ClearTicket` can be delivered and ACKed.
- The Home **Ticket** item disappears only after X3 ACKs the clear and firmware rebuilds Home from the authoritative ticket state.

## Live ticket

- `ShowTicket(Live)` is queued and ACKed like Static, but X3 retains low-duty BLE instead of becoming radio-quiet.
- Live prevents inactivity sleep and uses the configured slow connection interval.
- The app says **Live on X3 / Stop live** only after the ShowTicket ACK.
- Flight/status changes use the same live transport. Stop sends `ClearTicket`; the button and X3 menu change only after ACK.
- If GATT drops, the app says **Reconnecting…** and keeps retrying while Live is active.

## Focus/Pomodoro

- Start/Pause/Resume/Stop carry unique message IDs and receive individual ACKs.
- Active or paused Focus prevents X3 inactivity sleep and keeps the configured slow BLE link available.
- A dropped link enters **Reconnecting…** rather than pretending to be Connected.
- Reading-to-Focus is treated as pending work: Android waits for X3 to advertise, sends the desired session, and X3 switches to the Focus activity.

## Durable pending work

The following survives Android activity recreation and process restart:

- radio policy and reader page-refresh policy, including the unsynchronized flag;
- a pending complete ticket payload;
- static/live ticket removal state;
- queued X3 library-deletion paths;
- the fact that an already-ACKed Live ticket should maintain its link.

Active Focus timing and an in-progress firmware byte transfer are process/session work rather than resumable transactions. X3 continues its local Focus countdown if the phone process dies, but the current phone timer state is not reconstructed from X3. An interrupted firmware transfer must be restarted rather than resumed at an arbitrary byte.

## Battery telemetry

- X3 reads state of charge from the BQ27220 fuel gauge and charging state from USB detection.
- The status packet carries percentage and charging alongside activity/sync mode; older six-byte status packets remain decodable with battery shown as unknown.
- X3 sends battery state during handshake, immediately after a connected USB plug/unplug edge, and when the one-minute connected sample detects a change.
- Android persists the last valid 0–100% value. It never substitutes the phone's battery value for the X3 value.
- This is monitoring only. No verified charger-enable pin is exposed, so there is no firmware-enforced charge-percentage cutoff.

## Failure behavior

- Ordinary idle discovery failure returns to **Paired**; it does not loop forever.
- A failure while durable work, Focus, Live, or transfer is pending shows **Reconnecting…** and retries after 1, 3, 8, then 15 seconds (15 seconds thereafter), with the scan/connect time in addition.
- Expected radio silence in Reading/Static does not produce a destructive action or clear the queued desired state.
- Every durable device mutation is removed from the pending set only after its matching ACK. Disconnects, timeouts, or process interruption leave durable commands pending and idempotently resend them.
- Pairing keys, service UUIDs, and bond data are not changed by these transitions or application-partition firmware flashes.
