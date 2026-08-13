# Full app acceptance matrix — 2026-08-11 clean-room run

This is the evidence ledger for the current reset-first acceptance run. A check is complete only when the listed observable evidence exists; source presence or an enabled button is not enough.

## Test layers

- Unit: protocol, validation, reconciliation, filtering, and error-policy logic.
- Integration: Android repository/storage/auth boundaries using controlled inputs.
- Visual: deterministic Compose screenshots at default, compact, and large-text configurations.
- Device E2E: real Pixel + X3 behavior, including process death and physical reset windows.

## Current state

- Android: `0.2.0-dev42` / code 43 installed with app data and bond retained; APK SHA-256 `8A255255093EB8C2E6860297C05C4DF87C6DB531B470E2AAA63F7116AB50191E`.
- X3: `xtraordinary-v0.2.6-dev32-usb-owner-local`, production-phone-flashed application-only with write verification; SHA-256 `7F28384C7C41798EDF09A3BDA9CCA20D6D0196EAF215D05D52E151A16F7A25EF`.
- X3 NVS reset and Android bond removal: complete.
- Bluetooth bond: fresh Secure Connections bond to `XTEINK Companion`; 16-byte AES key recorded.
- Linked test folder: `/Documents/XtraordinaryTest`, containing two repository EPUB fixtures.
- USB: Pixel host sees Espressif `303A:1001`; dev16 and the corrected dev17 were both selected by embedded version/full SHA and flashed through the production app.

## Acceptance matrix

| Area | Required evidence | Layer | Status |
|---|---|---|---|
| Setup Welcome | Google control fully visible at 412×915, 360×800, and font scale 1.3; no `More`, arrow, duplicated folder copy, or `Start setup` | Visual + real Pixel | Pass |
| Setup pager | Header/status remain visible on Library and Device; illustrations stay consistent | Visual + real Pixel | Pass: transparent minimalist PNG bases plus deterministic preview frames for dots, moving books, and signal pulses at the established footprint |
| Folder link | SAF grant to dedicated folder; both fixture EPUBs indexed | Device E2E | Pass |
| Folder relaunch | Process death/relaunch retains setup and re-indexes both fixtures | Device E2E | Pass |
| Google authorization entry | Disconnected Settings launches Google authorization without replaying setup; cancel returns an explicit state | Device E2E | Pass on dev32; Google authorization UI opened directly and cancel state rendered |
| Google authorization token | Selected account returns access token for `drive.appdata` | Device E2E | Blocked: Google Play services `DEVELOPER_ERROR`; production OAuth client pending |
| Google error truth | Developer configuration error is not shown as user cancellation | Unit + device log | Unit pass; live recheck pending after OAuth configuration |
| Drive initial sync | `xtraordinary-reading-v1.json` created in `appDataFolder`; state shows account and timestamp | Integration + device E2E | Blocked by OAuth |
| Drive restore | Remote-only reading sessions merge once without duplicates | Unit/integration + device E2E | Pending |
| Drive delete/revoke | Cloud file deleted, local reading retained, access revoked, reconnect required | Device E2E | Blocked by OAuth |
| Firmware source | Public latest is identified as old v0.2.4 and is not silently substituted | Integration + live GitHub API | Pass |
| Local firmware validation | ESP magic, partition bound, embedded Xtraordinary version, size, and SHA-256 checked | Unit + real Pixel | Pass |
| Firmware wake state | Powered-off/not-enumerated state says `Turn on or wake X3`; recovery helper mentions cable reconnect | Visual + real Pixel | Pass on installed dev30 production flow |
| USB flash | Local candidate is flashed application-only, verified, reset, and preserves SD/NVS | Device E2E | Pass: dev22 `B937AE27…7C6AD76` was written at `0x10000` and hash-verified by the repository PC flasher; NVS, SD, and existing bond retained |
| Fresh discovery | X3 advertises after firmware boot and Pixel discovers it without an old bond | Device E2E | Pass: expected address/name/UUID at RSSI -39 |
| Fresh pairing | Android bond reaches bonded, encrypted GATT opens, notifications subscribe | Device E2E | Pass: Secure Connections, 16-byte AES key, `BOND_BONDED`; dev17 notification subscription succeeds |
| Protocol ready | MTU, capabilities, status, clock, library pages, and policy acknowledgements observed | Device E2E | Pass on dev22: preserved bond opened status-0 GATT, notifications subscribed, fresh capabilities and four LibraryPage packets arrived, and radio/reader policies were ACKed |
| Standby physical input and sleep/wake | Ordinary controls work in standby; sleep visibly renders before radio teardown; a second Power tap during final sync cancels sleep; configured instant Power tap wakes from established deep sleep; post-wake protocol is fresh | Device E2E | Reopened: dev26 crossed repeated standby pulses, rendered **Sleeping**, and accepted a genuinely fast tap after deep sleep was established, followed by a fresh protocol handshake. A newly tested fast second tap during the bounded final-sync/teardown interval is lost until deep sleep finishes. Dev27 must prove both transition cancellation and established-sleep wake without deleting the bond. |
| Reading library transfer | One fixture sends by USB/BLE, commit is ACKed, X3 library reflects it, queue clears only after commit | Device E2E | Pass over BLE on dev19: `Bidi Test` sent with Begin, pipelined ACK-backed chunks, Commit ACK, and refreshed LibraryPage sequence; UI changed from `Phone only` to `/Books/RTL_test.epub`, X3 count 2 → 3, and showed `1 book uploaded to X3` |
| Transfer cancellation | Explicit cancel sends abort, temp data disappears, no partial library entry remains | Device E2E | Pass on dev19 BLE: Begin ACKed, four chunks were in flight, Stop sent `AbortBookUpload`, firmware ACKed it, UI retained `Phone only`, and no partial X3 library entry appeared. Retrying the same 68 KB fixture completed from a fresh Begin through Commit and `/Books/test_tables.epub`. |
| Transfer process death | Durable queue retries from byte zero after supervision/firmware timeout | Device E2E | Pass on dev19 BLE: app PID 28638 was force-stopped after Begin plus eight chunks and before Commit; after the 15-second stale window, relaunch PID 32048 restored the queue without user input, issued fresh Begin id 8, retransmitted the complete fixture, Commit id 310 was ACKed, and UI showed `1 book uploaded to X3`. |
| Focus | Start, pause, resume, end, reset, and X3 synchronization are correct across recreation | Unit + device E2E | Pass: paused relaunched at `24:54`; running advanced `24:50` → `24:41` across force-stop. On dev19, Start ACKed, pause persisted at `24:34` and delivered after the bounded standby reconnect, resume sent an idempotent remaining-time Start snapshot and resumed at `24:09`, and Stop ACKed with UI reset to `25:00`. |
| Passes | Import, static/live send, barcode render, removal, and persistence | Unit + device E2E | Partial: the pending SFO→JFK static pass resumed after dev20 boot; 57 barcode chunks, Commit, and ShowTicket were ACKed in 4.96 s and the X3 pinned the pass. Live mode, removal, and persistence still require acceptance. |
| Settings policy | Desired state persists immediately and says synced only after current ACK | Unit + device E2E | Presentation persistence pass; fresh dev17 radio and reader policy ACK chain passed |
| Trial/ads/upgrade | Trial elapses, same identity cannot reset it, ads appear only on allowed surfaces, purchase restores | Unit/integration/device E2E | Not implemented; design and external decisions in `monetization-entitlement-design.md` |
| Community build | Ad/billing SDKs absent and build remains ad-free | Build contract | Not implemented |
| Accessibility | 48dp+ actions, screen-reader semantics, contrast, reflow/large text | Unit/visual/manual | Setup and Settings large-text visual coverage pass; full manual sweep pending |
| Regression gate | Policy, protocol tests, app unit tests, lint, 20 screenshots, APK assembly | Automated | Canonical dev26 firmware build passed all 49 engineering policies and ESP32-C3 RV32IMC verification at pushed commit `4d7ebaa`; RAM 34.8%, flash 82.9%. Existing Android dev33 gate remains passed. Both canonical wrappers now reject source that is not clean and confirmed at the live upstream ref before compiling. |

## 2026-08-13 USB and external-service update

- USB success and stale-host recovery now pass on dev42/dev32: the retained 68,277-byte `test_tables.epub` queue resumed from a fresh Begin after firmware replacement, completed, cleared its durable queue, and produced exact X3 path `/Books/test_tables.epub`.
- USB-specific cancel/abort and sequential two-file queueing remain open. Earlier BLE cancellation and BLE process-death evidence do not substitute for these USB cases.
- Google reading-history production acceptance remains blocked by the production OAuth client. Wallet-format import exists, but consumer Wallet enumeration does not; live flight status still requires the documented provider/proxy boundary.
- Trial, entitlement, ads, purchase, and the SDK-free community build remain unimplemented pending the external identity, Play Billing, product, AdMob, and UMP contracts recorded in `monetization-entitlement-design.md`.

## Next executable sequence

1. Accept immediate Power cancellation during final sync plus established-deep-sleep instant wake and boot-feedback timing on the deployed dev27/dev34 pair.
2. Run USB book success, cancellation, stale-host retry, and multi-file queueing; rerun BLE/process-death transfer and Focus regressions only where the final-sync path could affect them. The pre-flash retained dev26 report was recovered over PC serial: it has an empty panic string (consistent with the recorded CPU-lockup reset class), ends around final-sync radio restore with `PWR Lock already held`, and contains SD read addresses without a usable exception backtrace, so no SD causality is claimed.
3. Finish Static/Live pass removal and persistence, Settings ACK truth, firmware-source flashing, accessibility, and automated regression coverage.
4. Configure valid Android OAuth before Drive create/restore/delete/revoke; resolve the external product/backend decisions before trial expiry, ads, upgrade, recovery, and community ad-free build acceptance can be claimed.
