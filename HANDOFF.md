# Xtraordinary release handoff — 2026-08-13

## Start here

The complete accepted dev26 source and evidence are preserved on `codex/x3-dev25-recovery` through pushed commit `4f25363`. Never compile a new candidate from uncommitted or unpushed source: both canonical wrappers invoke `scripts/assert-pushed-source.ps1` before the policy gate and compiler, and the check verifies a clean named branch, its upstream, matching local/upstream commits, and the live remote ref. Generated build output may be discarded; source checkpoints may not.

At 19:28 on 2026-08-13, installed dev33 exposed a retained ESP32-C3 CPU-lockup report at the ten-minute sleep/final-sync boundary. Android did not crash: its process remained resumed and Android's exit history contains only the install workflow's intentional force-stops. The persistent X3 report has an empty panic string and ends with `BLE client connected`, `PWR Lock already held`, and `Awake / Fast radio policy restored`; raw saved addresses include SD/SPI and FreeRTOS semaphore code but are not a valid backtrace, so they do not establish an SD cause. `scripts/read-x3-diagnostics.ps1` then proved the report came from the preceding panic boot while the latest boundary completed as ordinary deep sleep (`reset=8`). Source tracing found every GATT bootstrap sends `GET_LIBRARY` and the final-sync worker dispatched that SD scan from its BLE teardown task. The current candidate centrally accepts bounded state/control updates during final sync but NACKs library snapshots and bulk book/firmware I/O for replay on the next normal link; future panic reports also persist packet/library checkpoints. This candidate is not deployed or physically accepted yet.

The dev29 firmware source is checkpointed at pushed commit `d1515b6`. It retains ACK-gated persisted Focus commands, USB commands as device activity, and debug-only retained crash retrieval. Dev29 closes the immediate post-**Sleeping** wake gap by waiting for the physical e-ink frame before final sync begins, keeping the Power edge latch armed through the transition, and restoring the destination with one clean full refresh. Dev32 is the current firmware source candidate: dev30 sizes the CDC RX queue from the maximum hex-expanded companion envelope; dev31 replaces blocking per-frame serial parsing and advanced the physical transfer to 26,400 bytes; dev32 makes the shared book transaction explicitly BLE- or USB-owned after Android's bounded idle GATT release was shown to abort the unrelated USB transaction.

Dev33 is now the installed firmware. Physical USB cancellation passed on dev32, but the subsequent two-file queue and a clean-process replay both lost the second chunk reply at offset 240 while a later Abort still ACKed. Dev33 keeps ordinary serial logs non-blocking and moves only command ACK/NACK replies through a bounded reliable CDC transmit-and-drain primitive. It was built from pushed commit `eb334e4`, passed 72 policies plus RV32IMC verification, and was production-phone-flashed with SHA-256 `09CFA412462129697839F889B0682CB89060B19AC6C6AD595934104F29BE9E84`. The preserved two-file queue then cleared and both exact fixture paths returned; individual post-restart frames completed before log capture, so no per-frame timing is claimed.

The Android dev35 app-only recovery correction is checkpointed at pushed commit `c4c273d`. Known phone transport blockers now outrank generic retry state, blocked retries pause, and foreground pending-work recovery caps the no-scan gap at 500 ms without making foreground visibility own GATT or X3 power. Dev42 is the current app source candidate: dev36 added the shared retained-USB drain and diagnostics; dev37 fixed local-firmware action ownership; dev38 gives maintenance exclusive USB ownership; dev39 uses sustained CDC chunks with receive-queue headroom; dev40 adds restored-library readiness to the shared drain; and dev42 removes dev41's disproven 20 ms host-turnaround guard.

The owner resumed the clean-room USB reset sequence. The stock flash, targeted X3 NVS reset, exact Android bond removal, app-data clear, and genuine first-run launch are complete. The authoritative firmware-source, USB-region, reset, reconnect, and recovery contract is [`docs/usb-firmware-maintenance.md`](docs/usb-firmware-maintenance.md).

Current physical/software state:

- Android app package: `com.xteink.companion`
- Android installed/source candidate: `0.2.0-dev42` / code 43 at pushed commit `c9d86e0`; canonical provenance, 71-rule policy gate, protocol/app unit tests, lint, and debug assembly passed; APK SHA-256 `8A255255093EB8C2E6860297C05C4DF87C6DB531B470E2AAA63F7116AB50191E`; installed with app data retained
- Android verification: the 33-rule policy gate, protocol tests, app unit tests, lint, 20 screenshot validations, and debug assembly passed. The first Welcome page fits at 412 x 915, 360 x 800, and 1.3x text scale. The three onboarding illustrations now use transparent, minimalist Material-style PNG bases at the established footprint: a generic hole-punch Android phone, an empty folder, and a plain e-ink reader. Compose overlays add communicating dots, filing books, and pulsing signal arcs only on the active page; preview renders freeze at a deterministic frame. Settings owns Google backup directly and places it immediately before the lower-frequency **Run setup again** action. The real Pixel displayed **Connect Google**, launched the Google authorization UI directly, and returned an explicit canceled state without replaying setup. Focus persists against an absolute deadline: the real Pixel restored a paused `24:54` session exactly and advanced a running session from `24:50` to `24:41` across force-stop/relaunch. Phone presentation settings use a durable centralized store; Quiet survived a real force-stop/relaunch before Expressive was restored.
- Real dev19 transfer/Focus acceptance: `Bidi Test` committed over BLE; active cancellation sent and ACKed `AbortBookUpload`; retry committed `test_tables.epub`; a force-stop after eight chunks restored the durable queue on relaunch and committed from a fresh Begin. Focus Start, bounded-reconnect pause, remaining-time resume snapshot, and Stop were all ACKed with matching Pixel state.
- X3 application partition: `xtraordinary-v0.2.6-dev33-usb-reply-local`, built SHA-256 `09CFA412462129697839F889B0682CB89060B19AC6C6AD595934104F29BE9E84`; production-phone-flashed application-only at `0x10000`, write verified, and restarted with NVS/bond/app data/SD preserved
- USB/JTAG: enumerated on the Pixel as Espressif `303A:1001`
- X3 targeted NVS reset: executed through the production app, ROM-MD5 verified for exactly `0x9000..0xDFFF`, and hard-reset
- Android bond: fresh Secure Connections bond to `XTEINK Companion` / `7C:E8:B1:71:13:3E`; 16-byte AES encryption and `BOND_BONDED` recorded
- Xtraordinary app data: cleared for package `com.xteink.companion`; first-run has since been completed with `/Documents/XtraordinaryTest` linked and two fixture EPUBs indexed
- Xtraordinary firmware restore after stock: **complete**; the current flashed application is dev29; stale device-manager observations from earlier candidates are not current firmware evidence
- SD card: untouched by the firmware-source and app-partition operations

The phone's production flasher initially completed CrossPoint `v1.5.0` after CrossPoint had been mislabeled as stock. It was allowed to finish rather than being killed during erase/write. The picker was then corrected to distinguish genuine XTEINK stock, CrossPoint, and CrossInk. Genuine stock was downloaded, size/SHA-256 checked, written to `0x10000`, ROM-MD5 verified, and restarted.

The reusable **Reset X3 setup** operation writes only NVS `0x9000..0xDFFF` (`0x5000` bytes of `0xFF`), verifies that region by MD5, and restarts. It clears X3 pairing/Wi-Fi/device settings while preserving application firmware and SD-card files. It does not remove Android's bond or clear app data.

Firmware restore, fresh pairing, protocol readiness, and the deterministic Bluetooth-standby loop freeze are complete. The owner confirmed dev29 repeatedly cancels sleep from the visible **Sleeping** frame and returns with a clean render; the visible return is somewhat slow because this path deliberately performs a full e-ink refresh, but exact timing was not measured. The owner also waited about five seconds for established deep sleep, tapped Power, and confirmed the X3 boots and remains awake. After the immediate-wake test, Android Bluetooth was found off while durable settings work remained queued. Re-enabling it preserved the bond; a Home-button wake then produced RSSI -49 discovery and the complete status-0 GATT/services/MTU/notifications/capabilities/status/policy-ACK chain. The app incorrectly hid the known Bluetooth-off blocker behind **Reconnecting…** and its foreground retry left a 15-second no-scan gap. Android dev35 is the app-only correction: typed transport blockers outrank retry text, blocked retries pause, and foreground pending-work recovery has a 500 ms maximum no-scan gap. Foreground visibility alone still does not retain GATT or change X3 power behavior.

Dev35 physical app acceptance is complete for the available boundaries. With Bluetooth off, the real Pixel rendered **Bluetooth off** and did not run a reconnect loop. Turning Bluetooth on with no pending work opened no X3 connection. Reapplying the existing 5-minute setting created one bounded session: scan matched at RSSI -52, GATT status 0, three services, MTU 256, notifications, capabilities/status, and both policy ACKs completed. Android unregistered the GATT client, the LE ACL closed, and the UI returned to **Paired**. The 500 ms failed-scan retry gap passed its unit policy test but was not physically crossed because this run succeeded on the first scan.

The reset-first USB book matrix is complete on dev42/dev33: success, explicit cancel/abort, stale-host/reboot recovery, and sequential two-file durable queueing all pass using only the two repository fixtures. Also open are remaining Static/Live pass removal and persistence; Settings desired-state/ACK behavior; Google Drive create/restore/delete/revoke once production OAuth is valid; provider-backed live flight refresh; trial expiry, non-resettable identity, ads, upgrade/end-trial, and recovery checks; the GitHub ad-free/community-build disclosure; accessibility; and the complete automated regression gate. Do not substitute source inspection for physical evidence and do not claim blocked external OAuth/monetization work complete.

The complete standby-input incident record is `docs/x3-standby-input-incident-2026-08-11.md`.

## Deployed power/ticket baseline before the stock reset

The dev20/dev16 pair described in this section was built, deployed, and observed protocol-ready before the owner requested the clean-room stock/reset sequence. It is historical; corrected dev17 now occupies the X3 application partition.

The previous radio policy incorrectly reused one 0.5-4-second value for two different BLE mechanisms: disconnected advertising and an open GATT connection interval. That made the Home chip's **up to 4 s** text truthful to the implementation but wrong for the requested low-power behavior.

The candidate separates four states:

- fast discovery: 500 ms advertising during the configured Home window and after button activity;
- low-power standby: a 1.5-second advertising pulse every 30/60/120 seconds (default 30), with the chip derived from the applied firmware value and Home invalidated after persistence;
- connected background: a separate 1/2/4-second GATT interval (default 2);
- interactive transaction: the shared owner lease temporarily requests the 15-30 ms fast connection.

Focus and a displayed Live ticket keep X3 awake and pulse-discoverable but no longer justify a permanent GATT connection by themselves. A new control/live-data command reconnects during a pulse; a visible interactive surface or bounded transfer acquires the generic owner lease. Firmware clears the lease on disconnect.

The Android lifecycle is implemented by feature-agnostic `InteractiveTransportCoordinator` ownership. Passes is one long-lived caller and ticket transfer is one scoped caller; the coordinator itself contains no feature or navigation names. The complete contract is in `docs/interactive-transport-lifecycle.md`.

Every canonical build now runs `scripts/check-engineering-policies.ps1` before a compiler. Its manifest is `docs/engineering-policy.json`; the current 27 rules include wrapper use, generic ownership boundaries, reconnect replay, transaction scoping, bond-safe firmware output, capability-gated policy v2, standby/connected cadence separation, applied-value Home refresh, shared ticket hierarchy, genuine OEM-stock identity, bounded NVS reset, and explicit post-flash USB reconnect guidance.

QR/Aztec matrix codes and PDF417/linear barcodes now use one information hierarchy on Android and X3. Android preserves the established perforated pass, magnetic/haptic horizontal pager, 48 dp neighboring-pass reveal, and Static/Live action cards; the pass is taller, passenger/group move inside it, and the separate details block is removed. Only the scanner chamber aspect ratio changes. X3 puts the operational facts above a lower scanner chamber; a wide code retains the Confirm-button rotated fullscreen **Scan** view, while a matrix code remains in portrait.

Radio-policy v2 is capability-gated. New firmware accepts both the legacy six-byte command and the separated eight-byte command, so an old app remains compatible and receives the 30-second standby default. The new app requires `radioPolicyVersion >= 2` before it marks the separated desired values synchronized. When deployment is allowed, flash firmware first and then install Android; neither operation may erase NVS, the bond, or app data.

Build verification for that baseline completed, followed later by deployment:

- Android `0.2.0-dev20` / code 21: 24-rule policy gate, protocol tests, app unit tests, lint, debug assembly, screenshot reference update, and screenshot validation passed. APK SHA-256: `ABEFDAD4556A3D8B80BE49341A4F3FF8EA285C8BDD6F8AE5B8A3C1BC9C59D984`.
- Firmware `xtraordinary-v0.2.6-dev16-local`: 24-rule policy gate, ESP32-C3 RV32IMC verification, and release build passed at 34.8% RAM / 82.9% flash. Firmware SHA-256: `778D8459878CF4FA7478524B105479E4FB5C784320D95FC18FD11576F2FEB55F`.
- A deliberate invalid manifest produced exit code 1 in the preceding candidate, proving the policy checker rejects violations as well as accepting the current manifest.
- The app and firmware were subsequently deployed with the existing bond retained. API 37 GATT, encrypted service setup, notification subscription, and capabilities exchange were observed. Hardware current draw and the remaining visual acceptance items are still not proven.

The current phone/X3 pair is deliberately unpaired as part of the 2026-08-11 clean-room sequence. Do not infer protocol readiness until a new bond and the complete GATT/protocol chain are observed.

## 18:15 Android artifact mismatch resolved

The previous installed dev14 APK was built during a reverted temporary GATT experiment. This is resolved: the full Android gate passed from the current checkout, and `scripts/install-xtraordinary-app.ps1` performed the required `PERIPHERAL_RESET_READY` handshake before installing dev15. Package Manager reports `versionName=0.2.0-dev15` and `versionCode=16`.

The first post-install reconnect saw the X3 advertisement and began a direct GATT open, but the device stopped advertising before the presence probe completed. The app correctly settled to its low-power unavailable state. This is not a completed post-flash handshake; flashing dev12 and the ticket latency/desync acceptance below remain required.

## Ticket desync evidence and fix â€” 2026-08-10

The four user captures establish this order from their filesystem timestamps:

1. 17:39:44 â€” X3 visibly displays the live W4 ticket.
2. 17:40:21 â€” Android briefly shows **Start & send**.
3. 17:40:30 â€” X3 Home still contains the **Ticket** menu item.
4. 17:40:35 â€” Android restores **Live on / Stop live**.

The device never lost the pass. `CompanionViewModel` was replaying the same pre-send Capabilities packet (`ticketPresent=false`) whenever an unrelated link/status field changed. That stale value could overwrite the later `ShowTicket` ACK; a reconnect then received fresh `ticketPresent=true` and repaired the phone state. Capabilities packets now have a monotonic sequence and their ticket bit is reconciled exactly once. Regression tests cover stale-false preservation and fresh reconnect authority.

The confirmation delay was measured rather than inferred. Before the final link flow, the real 4,286-byte PDF417 transfer took 21,857 ms: `BeginTicketBarcode` arrived slowly, and the first data chunk then waited through multiple configured 4-second connection events. A reusable interactive lease now lets any app surface tell X3 once that bounded interactive work is beginning. The wake is fire-and-forget (no ACK wait and no renewal/polling loop), promotes the connection for at most 30 seconds here, and automatically returns to the configured slow interval. `BeginTicketBarcode` also requests the existing 15-30 ms transfer interval and `ShowTicket` restores balanced/slow operation after its ACK.

Settings timing compatibility is now computed from the supplied choice lists. A low-power transition must be strictly earlier than sleep, exactly matching firmware validation. Selecting 5-minute sleep selects the latest compatible offered fast window (1 minute here) and disables/grays the 5- and 10-minute low-power choices.

Linear tickets now use an operational hierarchy instead of side decoration. Android shows route first, status/flight as metadata, departure/gate/terminal/seat as equal facts, then a full-width barcode strip. X3 keeps the excellent route header, preserves the transmitted PDF417 at 340 x 140 px, expands the scan chamber toward both edges, and renders four large facts below it. Matrix formats keep the prior square layout.

Current verification:

- `:protocol:test :app:testDebugUnitTest :app:lintDebug :app:updateDebugScreenshotTest :app:validateDebugScreenshotTest :app:assembleDebug` â€” `BUILD SUCCESSFUL`.
- Impeccable layout detector â€” no findings.
- Android dev17 installed with retained ticket/pass data; APK SHA-256 `2540EE86D6FABC648354B662E2B548C3B3F55483DA8AE0B75E4E847FF194F078`.
- Firmware dev13 built with RV32IMC verification (RAM 34.8%, flash 82.8%), flashed application-only, and hash-verified without erasing NVS or bond data.
- Post-flash bond 12, RSSI -44, GATT status 0, MTU 256, notifications, capabilities/status/library, and policy ACKs were observed.
- The real 4,286-byte / 19-chunk W4 send completed in 1,309 ms and remained **Connected / Live on X3 / Stop live** afterward.

This ticket/desync/latency acceptance is complete. Remaining work is the separately tracked USB book-transfer acceptance and any user visual refinements after viewing the new X3 composition on hardware.

Do not run long build/flash commands as opaque blocking calls. Start them with an early yield and monitor their cell/output. Never rediscover toolchain paths: source `scripts/use-toolchains.ps1`.

## Completed implementation in the checkout

### Real flight-pass barcodes

- Added `BarcodeRasterizer.kt` using ZXing for QR, Aztec, PDF417, Data Matrix, and supported linear formats.
- The same real 1-bit BMP is shown in the Android preview and transferred to the X3; the X3 no longer substitutes a QR code or a demo payload.
- Screenshot/photo import now accepts all ML Kit barcode formats and preserves the raw payload.
- Wizz Air parsing has an exact regression fixture:
  - flight `W4 6762`
  - scheduled departure `13:35`, not the `13:05` gate-close time
  - seat `17B`, not `TBD`
  - a past flight is `Departed`, not `Boarding`
- Added a PDF417 generate/decode round-trip unit test.
- Imported passes persist locally; demo data must not return after a real import.
- Android backup was corrected to `allowBackup=false` so stale demo/application state is not restored from device backup.

### Atomic X3 ticket-image transaction

- Protocol and firmware now use `BeginTicketBarcode`, ordered chunks, `CommitTicketBarcode`, then `ShowTicket`.
- Firmware stages the bitmap and commits it only after complete validation.
- If Android disconnects after bitmap commit but before `ShowTicket`, firmware discards the staged temporary image so new metadata cannot be paired with an old barcode.

### Other release fixes already present

- A stale Android notification-descriptor callback is ignored instead of mutating the current GATT session.
- The library upload action is enabled when USB host is connected even if BLE is not currently connected.
- `ACTION_SEND` image import is supported.
- Settings no longer fabricates stale “None connected” device rows.
- The reading-session Gmail-style swipe work is already in `origin/main` at `8205f99`; do not rework its accepted motion while finishing this release.
- The authoritative power/sync behavior remains in `docs/x3-power-sync-flow.md`.
- Release acceptance tracking is in `docs/x3-takeover-tracker.md`.

## Automated verification already completed

### Canonical firmware build workflow

Use the checked-in wrapper and run it outside the sandbox; do not invoke PlatformIO directly and do not overlap it with Gradle:

```powershell
.\scripts\build-x3-firmware.ps1 -Version xtraordinary-v0.2.6-dev14-local -Jobs 2
```

The wrapper pins the repository-local PlatformIO executable/core and UTF-8 environment. A direct sandboxed invocation repeatedly fails before source compilation with Windows `Error code: 5` / `Failed to get path name`.

### Canonical Android build workflow

Use the checked-in wrapper from any working directory. It resolves the repository root itself, activates `scripts/use-toolchains.ps1`, and invokes the bundled Gradle 9.5 binary directly. This avoids the recurring nonexistent-`android`-subfolder, host-JDK, and Gradle-wrapper download failures:

```powershell
.\scripts\build-xtraordinary-app.ps1
```

For a release gate that also updates screenshot references:

```powershell
.\scripts\build-xtraordinary-app.ps1 -Tasks ':protocol:test', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', ':app:updateDebugScreenshotTest'
```

Before the temporary GATT experiment, the production checkout passed:

```powershell
. .\scripts\use-toolchains.ps1
gradle --no-daemon --console=plain :protocol:test :app:testDebugUnitTest :app:lintDebug :app:validateDebugScreenshotTest :app:assembleDebug
```

Results:

- build successful
- 14/14 screenshot tests
- zero unit-test failures
- zero lint errors (warnings only)
- APK size: 85,091,682 bytes
- APK SHA-256 at that point: `5ED1645956757B89AD83A01247E34EC1648981E71EF2B5D1925CEE32B7045892`

The final X3 firmware build also passed:

- environment: `x3_companion_release`, one job
- firmware: `xtraordinary-v0.2.6-dev11-local`
- ESP32-C3 RV32IMC multilib verified
- RAM: 34.8% / 114,060 bytes
- flash: 82.8% / 5,427,737 bytes
- `firmware.bin`: 5,441,216 bytes
- SHA-256: `E4A8764636BB336BD54D89FD41DE85B6149FB7B10205EAD0F502656F626756D0`

The firmware is flashed on the X3 and its version was confirmed through the live capabilities handshake.

## Bluetooth finding and exact recovery evidence

A temporary Android instrumentation test was used to flash the local firmware from the Pixel. The phone-host ROM flash itself succeeded, including on-device MD5 verification and hard reset, but the test bypassed `CompanionViewModel.prepareForExternalDeviceReset()` and then terminated the target process. That is an invalid deployment workflow because it skips the production GATT disconnect/close/settle contract.

After that test:

- X3 advertising remained visible at RSSI −55 to −35 dBm.
- The bond remained intact.
- Android `connectGatt` produced no callback.
- `dumpsys bluetooth_manager` showed a stale `bta_dm_disc_gatt` direct connection with no X3 ACL.
- One bounded attempt through the legacy auto-connect overload also produced no callback after 25 seconds. It was proven ineffective and reverted from the source; do not add it back.
- A single controller restart through development ADB cleared the Android system client.
- Without deleting the bond, the next direct connection completed in about 0.56 seconds and the full capabilities/settings handshake passed.

The temporary instrumentation package, staged firmware file, and test source have been removed. Do not recreate that test. Test phone-host firmware flashing only through the production app flow, which calls the graceful release before `UsbEspFlasher.flash()`.

The normal app cannot silently toggle Bluetooth. Android officially makes `BluetoothAdapter.enable()` and `disable()` fail for ordinary apps targeting Android 13 or newer; only device-owner, profile-owner, and system apps are exempt. The current “Open Bluetooth controls” fallback is therefore honest. Continue to exhaust app-owned GATT recovery before showing it, but do not promise an automatic adapter restart that Android forbids. Reference: <https://developer.android.com/about/versions/13/behavior-changes-13#bluetooth-adapter>.

## USB book-transfer test: incomplete and currently queued

The real Pixel UI was used to select the phone-only book **Violenza relazionale, femminicidio e prevenzione**. The transport sheet correctly offered:

- USB — “Fastest · cable connected”
- Bluetooth — “Wireless · optimized for bulk transfer”

USB was selected. The UI entered the upload state and exposed **Stop book upload**, proving the chooser and cancellation surface are wired. The transfer later left the active state without adding the book to the X3 library. Current observable state:

- UI: `3 books · 2 on X3`; the selected book remains `Phone only`.
- `xtraordinary_connection.xml` still contains pending USB book ID `b61afc0f0f176b7c64cc5c3b` and method `Usb`.
- No crash appeared in `AndroidRuntime`.
- The exact failed phase/error was not logged, so do not guess whether the cause is USB speed, a chunk timeout, firmware rejection, or commit verification.

The queue must remain durable until a verified commit or explicit user cancellation. Firmware should remove any inactive temporary upload after its 15-second timeout; verify that instead of assuming it.

## Ordered next work

1. **Rebuild and reinstall the source-matching app.** Use the repository install script, then verify the capabilities handshake and firmware version. Because a pending USB upload exists, add useful USB transfer logging before relaunch if needed so an automatic retry is observable.
2. **Diagnose the USB upload with evidence.** Add a focused `XteinkUsbBook` log for permission/device open, begin ACK, periodic byte progress, first failing chunk/offset, commit start/ACK, abort, and the final exception. Keep user-facing text clean. Re-run only the pending test book and record size, elapsed time, throughput, and exact failure/success.
3. **Verify cancellation.** During a real transfer, tap **Stop book upload**. Confirm Android sends abort, firmware removes the temp file, no library entry appears, and the durable queue is cleared only because the user explicitly cancelled.
4. **Verify process death/resume.** Start the same test upload, terminate the app, wait beyond the X3 BLE supervision and 15-second upload timeout, then relaunch. It must retry the whole file from byte zero, never expose a partial library entry, and remove the queue only after commit. Do not relaunch inside the supervision window and recreate a stale GATT race.
5. **Verify multi-selection.** Import/select at least two phone-only supported books, upload sequentially, and confirm each verified commit removes only that book from the durable queue.
6. **Test the two real boarding-pass screenshots on the phone.** The user identified a normal pass at **29 July 00:56** and the Wizz Air pass at **6 August 13:48**. Import through the real UI. For Wizz, confirm `W4 6762`, `13:35`, `17B`, `Departed`, and a real non-QR barcode in both app preview and X3 output. No demo values, `TBD`, stale “Boarding”, or QR-only limitation are acceptable.
7. **Run the complete final gate again.** Run protocol tests, app tests, lint, all screenshot validation, Android assembly, and the single-job firmware release build. Record fresh artifact hashes.
8. **Clean and publish.** Confirm no temporary test package/source, no `.codex-artifacts`, and no personal screenshots are staged. Run `git diff --check`, review the complete diff, commit intentionally on `main`, and push `main` to `ClGratton/Xtraordinary`. The user explicitly requested the direct main push.

## Safety and workflow rules learned here

- Never call a raw X3 reset/flash while Android may own GATT.
- Never use an instrumentation process as a shortcut around the production reset-release handshake.
- Never infer that the user disconnected or unpaired the phone from a callback-less GATT attempt; verify advertisement, bond, ACL, and native client state separately.
- Do not keep a failed speculative recovery. The tested API 37 legacy auto-connect fallback only delayed the correct diagnosis and was reverted.
- Do not delete bonds as a routine recovery step.
- Do not reflash firmware merely to diagnose an Android client problem.
- Keep long build/flash operations observable with yielded execution and regular progress checks.
- After any workflow discovery or mistake, update the relevant Markdown before moving on so the next run does not rediscover it.

## Git state

Expected uncommitted scope is approximately 28 tracked files plus:

- `app/src/main/java/com/xteink/companion/data/BarcodeRasterizer.kt`
- `app/src/test/java/com/xteink/companion/data/FlightPassPhotoImporterTest.kt`
- this `HANDOFF.md`

There should be no `app/src/androidTest` test harness left. Review `git status --short` before staging. Do not discard unrelated user work or reset the worktree.
# 2026-08-13 flight status and monetization core candidate

- Live-flight updates now enter through `FlightStatusProvider`, never a pass-specific Bluetooth path. The proxy request identity is flight number + operating date + origin; responses need a matching identity and `observedAtEpochMs`. Live polling is five minutes with bounded backoff to thirty minutes. Static never polls.
- Ticket payload v2 adds arrival time and signed delay. Capabilities gate Android encoding, X3 migrates saved v1 tickets, and both app/X3 keep those facts above the scanner code. The external HTTPS proxy endpoint is intentionally unset until provisioned; no provider key belongs in either APK.
- The monetization core is centralized in `MonetizationPolicy.kt`: server-evidence states, injected clock, offline grace, and surface/operation-aware banner decisions. `community` and `play` are separate build variants; community contains no billing/ad/consent SDK. Production billing/backend/UMP/AdMob acceptance remains blocked on external accounts and identifiers.
- Canonical Android tasks now compile/test/lint/assemble both distributions. Default installation uses `app-community-debug.apk`.
# 2026-08-13 Passes design/UX contract and implementation handoff

Branch `codex/x3-dev25-recovery` is pushed through `69efd5f`. The worktree was clean when the last canonical build started. Do not reset app data, X3 NVS/SD, or Bluetooth bonds.

Authoritative design/UX knowledge is now `docs/passes-design-ux-contract.md` (commit `e8f3bb8`). It reconciles separate UX, hierarchy, spacing, margins/alignment, typography, color, and shape/affordance reviews; `DESIGN.md`, the tracker, and the 85-rule engineering gate point to it. Reuse the same contract-owner pattern; do not invent a Passes-specific transport lifecycle.

Implementation commits:

- `c3aea0e` — redesigned Passes and hardened the X3 ticket layout.
- `69efd5f` — fixed Compose selection/Kotlin compilation.

Implemented source boundary:

- two-row Back/Import then title/count header;
- magnetic/haptic pager preserved, with resource-backed position and Previous/Next accessibility actions;
- compact details face and equal-bounds full-code face with explicit, reduced-motion-aware vertical turn;
- selected pass/next-send mode separated from acknowledged deployed pass/mode;
- selected/deployed modes persisted under separate keys with legacy migration tests;
- no Passes-visibility Bluetooth owner; send/remove use generic `TicketTransferOwner` only;
- compact radio mode selector plus separate explanation and Start-timer-style send/remove action transformation;
- deployment truth uses a polite live region, names pass/mode, and sample/source/provider freshness is explicit;
- Quiet missing roles are grayscale, fallback primary AA test added, semantic type/shape hierarchy corrected;
- pure native `TicketLayout` owns portrait/rotated safe geometry, centers facts at 108/264/420, excludes mapped hints, and shares integer barcode placement with host tests.

Canonical Android retry at pushed `69efd5f` reached this exact boundary:

- pushed-source provenance passed;
- engineering policy passed (85 rules);
- Community and Play Kotlin/Java compilation passed;
- Community and Play unit tests passed;
- both debug APKs assembled;
- lint analysis ran;
- screenshot validation failed: 21 of 23 previews differ in each flavor, and the three new Passes compact/1.3x/2x fixtures have no reference images.

The broad screenshot diff is not approval evidence. Darkening the global Expressive primary for small-text AA changed many unrelated screens. Next work must first scope the Passes/status color fix without silently redesigning Focus/setup/settings/read, rerun the canonical wrapper, inspect the actual rendered Passes default/compact/1.3x/2x/Quiet images, and update only deliberately accepted references. Reports are under `app/build/reports/screenshotTest/preview/debug/{community,play}/index.html`; renders are under `app/build/outputs/screenshotTest-results/preview/debug/{community,play}/rendered/`.

Still incomplete:

- real Compose bounds/semantics execution assertions beyond screenshot fixtures;
- measured UTF-8 width/truncation helper for X3 dynamic ticket text;
- execution of the new firmware host `TicketLayoutTest`;
- canonical firmware build/production flash of the earlier final-sync crash correction plus this ticket layout;
- phone install and visual/gesture/accessibility acceptance;
- X3 matrix/linear/rotated Scan-Ticket, ghosting, acknowledgement, sleep/pulse, and real scanner acceptance.

Required order: inspect/scoped-fix screenshot blast radius -> commit/push -> canonical Android wrapper -> inspect and deliberately approve Passes evidence -> install/phone acceptance -> commit/push any firmware follow-up -> canonical firmware wrapper outside sandbox and never alongside Gradle -> application-only flash preserving NVS/bond/SD -> physical acceptance.
