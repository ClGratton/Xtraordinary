# XTRAORDINARY / XTEINK X3 takeover tracker

**Source date:** 2026-07-29
**Tracker status:** Active
**Current scope:** Fix battery drain while reading without changing Bluetooth pairing behavior; add native Focus/ticket surfaces and flight-pass import.

## Status key

- [x] Complete and verified at the stated level
- [ ] Not started or not yet verified
- [~] In progress
- [!] Blocked by a named prerequisite

## Non-negotiable gates

- [ ] Fresh-pair the restored APK and restored firmware before compiling or flashing another firmware.
- [ ] Require advertisement, GATT, service discovery, notification subscription, and HELLO/status exchange before calling the device connected.
- [ ] Preserve the targeted EPUB CSS memory guard and RV32IMC multilib fix.
- [ ] Do not erase all X3 NVS.
- [ ] Do not force-push.
- [ ] Push only after the fresh-pair baseline passes.

2026-08-08 execution note: the user explicitly authorized building, flashing the
connected X3, and installing the Android candidate before repeating the old
fresh-pair baseline. The update preserved NVS and the existing Bluetooth bond;
the installed app subsequently restored a protocol-ready connection.

## Restored baseline

### Pixel 10

- [x] Restored `build/artifacts/installed-xtraordinary-20260726.apk`.
- [x] Recorded APK SHA-256: `8F5BCEFE3643A5AC79142FF1DB50FDD6EC996F5D6833FBEA4A5DA1804E2776CA`.
- [x] Removed the stale Android XTEINK bond.
- [!] Fresh pairing requires physically waking the X3.

### X3

- [x] Restored `build/artifacts/xtraordinary-x3-v0.2.5-dev5-blefix.bin`.
- [x] Recorded firmware SHA-256: `6ADF74FAD2C2B12A1EA409FCDA0F84E07D8646C7B32D233E7FA25B628D5962E2`.
- [x] Verified flashed data with esptool.
- [x] Captured a normal boot, Home render, BLE advertising start, and approximately 55 KiB free heap.
- [ ] Wake the sleeping X3 physically.
- [ ] Complete a fresh Android bond.
- [ ] Confirm full GATT/protocol readiness.

### Fresh-pair procedure

1. [ ] Wake the X3 physically.
2. [ ] Open Xtraordinary.
3. [ ] Open **Devices**.
4. [ ] Select **Connect a device**.
5. [ ] Select **X3**.
6. [ ] Select **Already flashed**.
7. [ ] Select **Search nearby**.
8. [ ] Confirm Android transitions from unbonded to bonding to bonded.
9. [ ] Confirm GATT, service discovery, notifications, and protocol exchange.

If pairing fails:

- [ ] Capture Android Bluetooth logs and X3 serial at the same time.
- [ ] Classify the failure boundary: advertisement, ACL, bonding, GATT, service discovery, notifications, or protocol.
- [ ] If device-side bond cleanup is necessary, use only a verified targeted NimBLE bond removal path.

## Repository rollback

- [x] Local rollback index matches `cfa78232d256a0189482cb2b72325091a6149454`.
- [x] July 29 experimental power-framework source is outside the active firmware tree.
- [x] Restored the EPUB CSS memory guard separately.
- [x] Restored the RV32IMC multilib configuration separately.
- [ ] Reinspect staged and unstaged diffs before committing.
- [ ] Confirm no discarded July 29 clock, advertiser, sleep-screen, or logging experiment is active.
- [ ] Create a normal rollback commit only after hardware validation.
- [ ] Push only after the fresh-pair baseline passes.

## Connection model

Track these dimensions independently:

- [ ] Identity: never paired or bonded/remembered.
- [ ] X3 activity: booting, awake/Home, Focus, Reading, transfer, preparing to sleep, or deep sleep/off.
- [ ] Transport: radio off, advertising, scanning, connecting, GATT connected, protocol ready, intentionally idle, or offline.
- [ ] App presentation: setup required, available, reconnecting, connected, Focus running, Reading low-power sync, sleeping, or offline.

Rules:

- [ ] Never equate a Bluetooth bond with a live connection.
- [ ] Show **Connected** only after the complete protocol-ready chain.
- [ ] During intentional Reading transport idle, show **Reading · low-power sync** and the last-sync age instead of **Connected**.

## Never-paired two-minute behavior

- [ ] Apply the two-minute timer only when no stored phone bond exists.
- [ ] Keep Home and all controls usable throughout the timer.
- [ ] At expiry, stop only first-phone discovery advertising.
- [ ] Do not deep-sleep or replace Home at the timer boundary.
- [ ] Show a subdued bottom pill: **Phone setup paused** / **Sleep and wake X3 to connect a phone**.
- [ ] Never show the pill on a bonded X3.
- [ ] Start a new two-minute setup window only after the required X3 action.

## Bonded but disconnected

- [ ] Do not apply the first-pair two-minute cutoff.
- [ ] Keep Home usable and the awake X3 connectable.
- [ ] Preserve the proven fixed advertising policy.
- [ ] Do not periodically stop and restart advertising.
- [ ] Reconnect immediately when Android enters the foreground.
- [ ] Use the configured retry cadence after the immediate attempt.
- [ ] Default the normal retry interval to two minutes.
- [ ] Present **Reconnecting** or **Available**, not **Connected**.

## Reading battery drain

Measured failure: battery fell from 89% to 81% in one hour.

- [x] Isolate the baseline cause: BLE initialization holds the idle CPU floor at 80 MHz, including intentional Reading transport-idle periods.
- [x] Add a backward-compatible revisioned `Reading / Slow` status handshake.
- [x] Require Android to store the status and acknowledge the exact revision.
- [x] Ignore stale status acknowledgements on the X3.
- [x] Enter Reading radio-quiet behavior only after the matching acknowledgement.
- [x] Preserve the logical Reading state when Android intentionally releases GATT.
- [ ] Validate that leaving Reading immediately restores `Awake / Fast` and connectability.
- [ ] Validate that foregrounding Android causes an immediate connection attempt.
- [ ] Hardware-measure Reading drain after the fresh-pair and build gates pass.
- [ ] Confirm fresh pairing still succeeds after the reading-only change.

## EPUB crash recovery

- [x] Retrieved the persistent `v0.2.5-dev5` crash report from the X3 before flashing.
- [x] Symbolized the application stack to `ParsedText::addWord` called from `ChapterHtmlSlimParser::characterData`.
- [x] Confirmed the failure was a fragmented-heap allocation abort while parallel token vectors grew during a large XML callback.
- [x] Drain completed EPUB lines after 256 buffered tokens instead of waiting until a callback finishes with more than 750 tokens.
- [x] Keep the maximum one-word expansion within the safe 512-entry vector growth boundary.
- [x] Build the corrected reader into `xtraordinary-v0.2.6-dev1`.
- [x] Observe stable post-flash heap for more than six minutes with no reboot or new panic.
- [ ] Reopen the exact book/chapter that produced the old crash and complete a long-page-turn smoke test.

## Focus timer

- [x] Keep the timer as a native X3 surface driven by compact session data from Android.
- [x] Replace `mm:ss` with a large whole-minute number and a smaller `min` label.
- [x] Use only black and white for the low-resolution e-ink design.
- [ ] Visually inspect the timer on X3 hardware after the firmware build gate is cleared.

## Flight passes and tickets

- [x] Add Android import for `.pkpass`, Google Wallet FlightObject JSON, and a shared Google Wallet save-JWT link.
- [x] Keep imported barcode data out of the decorative Android preview.
- [x] Add bounded protocol fields for flight, route, passenger, gate, seat, and barcode payload.
- [x] Add an actual native X3 ticket surface with a large black-and-white QR field.
- [x] Add **Static** mode: deliver the acknowledgement before drawing, release GATT immediately, stop advertising, and retain the e-ink ticket image without replacing it with generic sleep artwork.
- [x] Add **Live** mode: retain the connection so Android can send an updated ticket payload.
- [x] Persist Android's uploaded-ticket state across activity/process recreation instead of treating a deliberate static disconnect as ticket deletion.
- [x] Queue Static removal locally while X3 is radio-quiet; apply it only after Back/wake restores advertising.
- [x] Keep the conditional Home **Ticket** item until the queued or immediate clear is acknowledged.
- [x] Document why arbitrary passes already inside a consumer Google Wallet account cannot be fetched through the issuer REST API.
- [x] Document the quickest external status-update API candidate.
- [ ] Add an API key/proxy and live polling only if external flight-status updates are authorized.
- [ ] Validate the imported barcode at a real airport scanner; QR is supported, while PDF417/Aztec rendering remains future work.
- [ ] Visually inspect the ticket and QR size on X3 hardware after the firmware build gate is cleared.

## Sleep/off

- [ ] If protocol-connected, send revisioned `Sleeping / Off`.
- [ ] Wait less than approximately one second for the exact acknowledgement.
- [ ] Render the existing CrossPoint `SleepActivity`.
- [ ] Enter true ESP32 deep sleep with BLE off.
- [ ] Make long-press power sleep rather than reboot.
- [ ] Sleep immediately even when no phone is connected.
- [ ] When no phone confirmed sleep, show only the last confirmed state and last-seen time in Android.
- [ ] Do not imply that opening Android can wake a sleeping X3.

## Device timing and power settings

## Offline reading statistics

- [x] Register a reading session only after at least three displayed pages.
- [x] Record every completed page's displayed duration and page number on X3.
- [x] Count exact laid-out EPUB words and exact wrapped TXT/Markdown words.
- [x] Mark legacy XTC word pace unavailable because the format contains only page bitmaps.
- [x] Flush page samples to the X3 SD journal without requiring BLE.
- [x] Finalize the journal during reader exit/direct sleep and retain completed sessions across failed phone sync.
- [x] Transfer bounded 32-sample chunks and delete the X3 copy only after Android persists and ACKs the session id.
- [x] Add **Tools → Reading stats** with cumulative and per-session pages, words, time, WPM, and pace graphs.
- [x] Sync the phone UTC clock into the X3 battery-backed RTC for session dates.
- [x] Protocol/Android compilation and release firmware build pass for dev8/dev7.
- [ ] Complete a three-page hardware reading session and observe its ACKed appearance in the app.

### Companion radio policy

- [x] Default Home discovery to fast advertising for 5 minutes, slow advertising at 2 seconds, then stop advertising at 10 minutes.
- [x] Add app controls for fast discovery (1/5/10 minutes), Focus/Live slow interval (1/2/4 seconds), and inactivity stop (5/10/20 minutes).
- [x] Persist the policy in both Android preferences and X3 storage and acknowledge protocol updates.
- [x] Keep Reading radio-quiet after its revisioned acknowledgement.
- [x] Keep Static radio-quiet immediately after the ticket acknowledgement while preserving the ticket image indefinitely.
- [x] Keep active Focus/Pomodoro and Live ticket out of inactivity sleep and on the configured slow BLE interval.
- [x] On leaving Reading or Static Ticket, restore a fresh fast-discovery window without changing bond data.

## Validation record

### Static and automated

- [x] Protocol codec tests pass.
- [x] Android unit tests pass.
- [x] Firmware source review confirms no change to security, bonding, service UUIDs, characteristic UUIDs, or initial advertising policy.
- [x] I18n source generation check passes with all new display strings resolved.
- [x] Firmware `x3_companion_release` build passes and verifies the RV32IMC libstdc++ multilib.
- [x] Android unit tests and `:app:assembleDebug` pass for `0.2.0-dev2`.
- [x] Connection-presentation regression test covers remembered-only **Available**, retrying **Reconnecting**, live **Connected**, and no-device states.

### Hardware

- [ ] Restored baseline fresh-pair test passes.
- [ ] Updated candidate fresh-pair test passes.
- [x] Existing Android bond survives the application-partition-only flash.
- [x] Updated Android app reconnects and displays **X3 · Connected** after a cold foreground launch.
- [x] Devices sheet agrees with Home and displays **X3 · Connected** plus `Firmware: xtraordinary-v0.2.6-dev1`.
- [x] Devices sheet no longer renders **No connected devices** when an X3 is managed.
- [x] A remembered X3 without live transport is presented as **Available**, not **Connected**.
- [ ] Reading entry acknowledgement is observed for the current revision.
- [ ] Android background disconnect leaves X3 in radio-quiet Reading mode.
- [ ] Leaving Reading restores advertising immediately.
- [ ] Page turns and reader controls remain responsive.
- [ ] One-hour Reading battery measurement improves materially from the 8 percentage-point baseline loss.

## 2026-08-08 deployment record

### Reading-statistics release

- [x] Firmware version: `xtraordinary-v0.2.6-dev7`.
- [x] Firmware SHA-256: `1BF256616B45533A58BCF22CDB30954B1F25E6BD2BD6A28D0C09CFC63C510E01`.
- [x] Firmware flashed at application offset `0x10000`; esptool verified the data hash and did not erase NVS/bond state.
- [x] Android version: `0.2.0-dev8` (`versionCode=9`).
- [x] Installed APK SHA-256: `FCD669A71273A28E7E710B882E66B2BB63A52C61F63BB37F56767B39C524AEC3`.
- [x] Final post-test rebuild SHA-256: `5539C4E1BD452E2D8FD641F3C494F1CEDBEC65F50E66905BE2D2FA008BF044BA` (same dev8 source; debug APK packaging/signing is not byte-reproducible).
- [x] APK installed over wireless ADB on Pixel 10.
- [x] Existing bond reconnected without pairing changes; GATT, MTU 256, notification subscription, capabilities/status, clock sync, stats request, library pages, and policy ACKs were observed.
- [x] Live Tools and Reading stats empty-state UI were inspected on the installed app.
- [x] Read and exit at least three real pages to validate end-to-end journal import and X3 deletion after ACK. Pixel storage contains two Project Hail Mary sessions (8 raw page samples total); logcat shows `ReadingStatsChunk`, Android's `AckReadingStats`, and X3's matching ACK. Tools → Reading stats renders 8 pages, 417 words, 1 minute, and 455 raw WPM before page-juggling filters.

- [x] Firmware version: `xtraordinary-v0.2.6-dev1`.
- [x] Firmware image: `firmware/.pio/build/x3_companion_release/firmware.bin`.
- [x] Firmware SHA-256: `72BB3407A758745F936B35A8682B576077AF7A346288A316A372C74087211427`.
- [x] Firmware flashed to COM7 at application offset `0x10000`; esptool verified the written hash.
- [x] No full-chip or NVS erase was performed.
- [x] Android version: `0.2.0-dev2` (`versionCode=3`).
- [x] APK: `app/build/outputs/apk/debug/app-debug.apk`.
- [x] APK SHA-256: `4188959E401A37842B151610D1CA79AD0D2286EF118AF3E7EDA1277D53954D94`.
- [x] APK installed with replacement/data retention over wireless ADB at `192.168.1.61:42807`.
- [x] Android Package Manager readback confirms `versionName=0.2.0-dev2` and `versionCode=3`.

## 2026-08-08 corrected ticket and battery deployment

- [x] Firmware version: `xtraordinary-v0.2.6-dev2`.
- [x] Firmware SHA-256: `1EBD28F9A7A9E6E2F6D63EE8694042DA759651998246B75333FD7AAFCA5C8538`.
- [x] Firmware flashed to COM7; esptool verified the written hash and performed no NVS/full-chip erase.
- [x] Android version: `0.2.0-dev3` (`versionCode=4`).
- [x] APK SHA-256: `425191840D7204B56B3BB33356E05695026C25FD85FA3A300A7C2C9907A0F95A`.
- [x] Android unit tests and debug APK assembly pass; APK installed over wireless ADB at `192.168.1.61:42807`.
- [x] Reproduced and fixed the send acknowledgement race: fresh capabilities are required before commands, stale capabilities are cleared on disconnect, and the e-ink transition is deferred until the acknowledgement can leave over BLE.
- [x] Hardware flow observed: Static send ends at **Available / Pinned on X3 / Remove**; tapping Remove while radio-quiet ends at **Available / Pinned on X3 / Waiting for X3**; wake delivers the queued clear and returns to **Send static**.
- [x] Final device state: Static ticket displayed, BLE disconnected/radio-quiet, Android backgrounded, no removal queued.

## 2026-08-08 menu refresh, reader cadence, and Settings polish

- [x] Rebuild Home after an acknowledged ticket clear even when the user already exited the Ticket activity; the stale **Ticket** menu item is removed by the same deferred post-ACK UI transition.
- [x] Add an app-controlled reader cleanup cadence of 1, 5, 10, 15, or 30 pages and persist it through the existing X3 reader setting.
- [x] Advertise reader-policy support in capabilities so this app remains compatible with older companion firmware and does not send an unknown command to dev2.
- [x] Keep fast discovery, slow Focus/Live link interval, and inactivity sleep independently configurable in Settings.
- [x] Replace the thin Expressive/Quiet chips with substantial selectable preview cards and replace timing chips with full-height segmented choices.
- [x] Inspect the page-turn path: no artificial one-second pre-refresh delay exists. Periodic cleanup uses the X3 HALF waveform and BUSY wait; the separate 200 ms settle happens after the refresh trigger and was retained.
- [x] Inspect image rendering: JPEG coarse-scales before final resize, PNG streams rows, and both cache display-sized 2-bit pixels rather than allocating a full-resolution framebuffer.
- [x] Keep the required multi-pass grayscale path, but choose the largest safe strip buffer from 160/120/80 rows with allocation fallback. On X3, 160 rows reduces a full-page two-plane image from 14 cache walks to 8 without decoding at source resolution.
- [x] Firmware `xtraordinary-v0.2.6-dev3` builds successfully; SHA-256 `A36BAD1E0E670F786651244F8D29ED8FC0E27B4B6B63D4EBA52DABDD59080384`.
- [x] Flash `xtraordinary-v0.2.6-dev3` to X3 on COM7 at application offset `0x10000`; esptool verified the written hash and hard-reset the device without erasing NVS.
- [x] Android `0.2.0-dev3` (`versionCode=4`) protocol tests, unit tests, and debug APK build pass; SHA-256 `D11C7E7416002659791DF16DA97263295C6FE8B8CBE0346EEE31FA8EA469D0E5`.
- [x] Final APK installed with data retention over wireless ADB at `192.168.1.61:42807`; Package Manager confirms `0.2.0-dev3` / `versionCode=4`.
- [x] Settings appearance and reader controls inspected on the installed Pixel build.
- [x] Post-flash serial boot confirms X3 hardware detection, QMI8658, DS3231, SD card, and e-ink initialization with stable free-memory reporting.
- [x] Android still recognizes the intentionally idle X3 as **Available** after the flash; no bond reset or fresh pairing was required.
- [x] Stop ordinary foreground discovery failures from entering an endless **Reconnecting** loop; one-shot idle/status probes now settle back to **Available**, while Live, Focus, firmware transfer, and queued device work retain persistent reconnect behavior. Verified on the installed Pixel after waiting beyond the 15-second discovery timeout.
- [ ] Hardware-check Home menu removal after clear ACK and reader cadence after the X3 USB port returns and dev3 is flashed.

## 2026-08-08 BLE diagnosis and final deployment

- [x] Reproduce the misleading **Available** state: Android saw the bonded X3 at strong RSSI, but `connectGatt` timed out after 30 seconds with status 147 and the firmware never received a client connection.
- [x] Identify the immediate cause in Android Bluetooth diagnostics: a stale system `bta_dm_disc_gatt` direct GATT attempt was holding the X3 connection. Cycling phone Bluetooth cleared it without deleting or recreating the bond.
- [x] Rename the remembered-but-disconnected presentation from **Available** to **Paired** so it cannot be mistaken for an active transport.
- [x] Add a 12-second app connection watchdog, explicit LE 1M PHY, stale-callback rejection, and a foreground reconnect watchdog only for work that requires a persistent link (Live, Focus, transfer, or queued device commands).
- [x] Keep Static radio-quiet after its acknowledgement. A queued Static deletion now says **Exit ticket on X3** until a device wake permits delivery; it is not presented as a pending send acknowledgement.
- [x] Verify both Static and Live ticket delivery on hardware. Live negotiated the configured 2-second slow connection interval; stopping Live delivered `ClearTicket` and restored the app action.
- [x] Confirm the apparent Live disconnects during serial investigation coincided with closing USB serial, whose control-line transition reset the X3; they were not evidence of an autonomous Live sleep.
- [x] Inspect the stored crash report and identify it as the older `xtraordinary-v0.2.5-dev5` image, not the current release. The final build again passes the RV32IMC multilib verification that prevents the earlier incompatible-instruction failure.
- [x] Add Home fast advertising, a visible **Low-power Bluetooth** chip during slow advertising, button-triggered fast wake, and the existing configured timeout to advertising-off/sleep. Reading and Static remain radio-quiet; active Live and Focus remain awake on their configured slow interval.
- [x] Charging-limit audit: firmware reads the BQ27220 fuel gauge for state of charge, but the current board mapping and code expose no verified charger-enable or charge-path switch. A warning at a chosen percentage is implementable; a firmware-enforced charge cutoff is not claimed without a charger schematic/control pin.
- [x] Firmware `xtraordinary-v0.2.6-dev5` built and passed the ESP32-C3 RV32IMC verifier; SHA-256 `CD92512E28E429635ABF938F11D5452C79332887CC415F78E1219CB78E501C48`.
- [x] Flash firmware to COM7 at application offset `0x10000`; esptool verified the written hash and performed no NVS/full-chip erase.
- [x] Android `0.2.0-dev6` (`versionCode=7`) protocol tests, unit tests, and debug APK assembly pass; SHA-256 `6F37BFBBA429FCCD8A10DEBA80A196899C74D9A4E7952B441D17D7636B1BC243`.
- [x] Install the final APK with data retention over wireless ADB at `192.168.1.61:42807`; Package Manager confirms `0.2.0-dev6` / `versionCode=7`.

## 2026-08-08 battery telemetry and acknowledged desired-state flow

- [x] Define the complete app/X3 state machine in [`x3-power-sync-flow.md`](x3-power-sync-flow.md), including labels, commands, replies, timeouts, power floors, pending-work durability, and known Android-process limits.
- [x] Extend revisioned device status with backward-compatible X3 battery percentage and charging state.
- [x] Show the X3 percentage and a drawn charging bolt in the app top bar and device sheet; retain the last percentage but assert charging only on a live transport report.
- [x] Persist phone settings immediately as desired state, display **Saved on phone · waiting for X3**, and display **Synced to X3** only after both radio-policy and reader-policy ACKs for the latest values.
- [x] Reapply phone-authoritative policy once per successful GATT session; a new edit during an in-flight sync causes another send instead of being lost.
- [x] Treat unsynchronized policy, ticket send/remove, library deletion, Focus, Live, and active transfer as pending work that maintains retry behavior rather than surfacing expected Reading silence as an error.
- [x] Persist pending ticket payloads and library deletion paths so process recreation cannot silently discard them.
- [x] Silence advertising immediately on entering Reading when no phone transaction is active; if already connected, drain pending work and then release GATT.
- [x] Offer a bounded 1.2-second fast sync window before direct sleep so queued phone work can land without requiring a Home visit.
- [x] Keep full CPU speed for radio reconfiguration and five seconds after traffic, then use the BLE-safe 80 MHz floor instead of holding 160 MHz throughout fast advertising.
- [x] Protocol tests, Android unit tests, Android assembly, firmware release build, and RV32IMC verifier pass.
- [x] Deploy Android `0.2.0-dev7` (`versionCode=8`) and firmware `xtraordinary-v0.2.6-dev6` without clearing app data, NVS, or the Bluetooth bond.
- [x] Final firmware SHA-256: `8AD51172121F2DF8C92AF8BAA76A72EB2C0512BC0A4D9287B4A5A245B460856D`; final APK SHA-256: `CB16C2F188F0DB24C789D0F18083F1CC642B3948B565839B66A5C4958CA2517F`.
- [x] Hardware readback: GATT connected successfully, X3 returned an 8-byte status payload reporting 99% and charging, Android rendered the percentage/bolt, both policy commands were ACKed, and persisted `sync_pending=false` afterward.

## 2026-08-08 reading-statistics acceptance and filtering

- [x] Verify the installed Pixel 10 is running Android `0.2.0-dev8` (`versionCode=9`) and retains two imported Project Hail Mary sessions in `reading_stats_v1.xml`.
- [x] Verify the live Tools → Reading stats screen renders those sessions and the Bluetooth trace contains the complete chunk/persist/ACK exchange.
- [x] Make Android's session write durable before ACK by using synchronous `SharedPreferences.commit()` on `Dispatchers.IO`; a failed commit leaves the X3 copy unacknowledged for retry.
- [x] Increase the stats event buffer from 8 to 64 chunks, covering the firmware maximum of 2,048 pages at 32 samples per chunk.
- [x] Retain raw samples while filtering page juggling from displayed metrics: configurable minimum page time (Off/5/10/15 seconds, default 5) plus a fixed 1,000 WPM credibility ceiling for text-backed pages.
- [x] Keep bitmap XTC sessions honest: the minimum-time filter applies, but no WPM filter or WPM result is invented without words.
- [x] Correct cumulative WPM so bitmap-only session duration does not dilute text-backed reading pace.
- [x] Diagnose the observed ~2.5-second wait before periodic cleanup goes black: X3 HAL promotes the requested HALF refresh to a forced full resync with power-on wait, one conditioning pass, and a post-full settle. This is a waveform policy issue, not an explicit reader delay; any change needs hardware ghosting/first-differential validation.
- [x] Protocol tests, Android unit tests (including filter behavior), and debug APK assembly pass after these changes.
- [x] Build Android `0.2.0-dev9` (`versionCode=10`) with the project-owned offline Gradle cache and install it over dev8 without clearing app data. The final replacement APK is SHA-256 `C82DDACC7A2C813DF6B23F8AD8966D3F206663A989B4D717D55F332839218E58` and uses the installed app's certificate `BD20B8B8C815CF31277C9366F4417EE40E4E72B3B921D4E3489CEE3162440E6C`.
- [x] Verify dev9 on Pixel: raw storage still contains both sessions, while the default 5-second/1,000-WPM filters render 4 credible pages, 317 words, 1 minute, and 396 average WPM. The Settings sheet exposes Off/5/10/15 seconds and explains that raw history remains reversible.
- [x] Correct the stats subtitle to **Saved offline after X3 sync**. The prior **Saved on both devices** claim contradicted the ACK contract because X3 deletes its queued copy after Android commits it.
- [x] Remove unused Android `BuildConfig` generation. The app has no Java sources or `BuildConfig` references; avoiding that generated class removes a needless Windows ZipFS/Javac failure point from incremental packaging.
- [x] Preserve the Pixel install signature explicitly when packaging from a sandbox: verify the installed certificate first, then sign the replacement with the matching user debug keystore. Never work around `INSTALL_FAILED_UPDATE_INCOMPATIBLE` by uninstalling, because that would destroy imported books, passes, settings, and reading history.
