# Xtraordinary release handoff — 2026-08-13

## 2026-08-24 fresh Capabilities and selected-slot proof boundary

Pushed `9fb5c4a` adds a bounded accepted-Capabilities log. After the retained-data Community reinstall (dev49/code 50, APK 85,782,220 bytes, SHA-256 `3E6A6F98A4D9E9F33F7FBCDACFFFB13D22BCA4DC475298C8AC231347D744B90F`), fresh BLE evidence logged firmware `xtraordinary-v0.2.6-dev37-book-reconciliation-local`, then fresh StatusChanged, revisioned LibraryPage pages, and matching policy ACKs. No crash occurred; bond and app data were retained.

The source trace path is now classified: `READ_X3_DIAGNOSTICS` calls the guarded USB `CRASH_REPORT` and `RUNTIME_TRACE` commands; the runtime reader retains `RUNTIME_TRACE_` lines, bounds the response at 4 KiB, and completes on `RUNTIME_TRACE_ACTIVE`. Firmware commit `de1fae5` introduced `RUNTIME_TRACE_OTA` before ACTIVE. The live trace contains ACTIVE/PREVIOUS but no OTA line, and the response is far below 4 KiB, so this is not page truncation or Android filtering; the installed dev37 runtime predates the OTA trace feature. The guarded flasher has no Android otadata fallback and still fails closed before reset/write without the running/next-boot equality proof.

A read-only retry while Pixel logs showed X3 VID:PID `303A:1001` briefly enumerated ended with `Timed out reading X3 diagnostic command`; current `dumpsys usb` is again `host_connected=true`, `connected=false`, `configured=false`. No reset, slot read, flash, bond/app-data/timeout/NVS/SD/book mutation occurred. Minimum next transition is restoring stable Pixel-host USB transport (or an authorized physical target transition to a host exposing the guarded serial path); do not assume `0x10000` or rewrite otadata. Screenshot/UI verification remains explicitly skipped.

## 2026-08-24 managed OTA safety decision

The existing BLE `BEGIN_FIRMWARE`/chunk/commit/apply path is present in the installed app and firmware. Firmware writes the received image to SD, validates SHA-256 and the image, then `flashFromSdPath` chooses `esp_ota_get_next_update_partition(nullptr)` and `ota_boot::switchTo(dest)`, so it is the reusable inactive-slot path and preserves NVS/SD/books. Fresh Capabilities includes `supportsFirmwareUpdate`; nevertheless `beginFirmware()` fail-closes unless `MappedInputManager::Button::Confirm` is physically pressed at the start. No transfer was attempted without that authorization. The only remaining physical action is to press/hold X3 Confirm while starting the existing in-app install; after its Begin ACK, the guarded workflow can transfer the canonical dev38 artifact and require fresh post-reboot Capabilities, StatusChanged, revisioned LibraryPage, and policy ACKs. Raw USB flashing remains blocked and unchanged.

## 2026-08-24 managed dev38 attempt transport stop

The canonical dev38 artifact was copied to Pixel Downloads and the app verified version `xtraordinary-v0.2.6-dev38-selected-slot-local` with SHA-256 `E0E8FA4E3347BB4533CAF34E8CCF7CC25A4306E5988A7CBA64204190A0F389FE`. Relaunch produced only `connect requested model=X3 phase=Disconnected`; no BLE advertisement arrived and Pixel USB was `connected=false/configured=false`. `BEGIN_FIRMWARE` was never sent, so Confirm was not consumed and no transfer, commit/apply, reboot, or device-state mutation occurred.

## 2026-08-24 managed OTA retry boundary

One bounded retry after a navigation-button wake waited about 75 seconds (the configured standby interval plus margin). The installed app again logged only `connect requested model=X3 phase=Disconnected`; no X3 scan match, GATT, service discovery, notifications, or USB enumeration occurred. The verified local dev38 artifact remained selected but `BEGIN_FIRMWARE` was never sent, so no Confirm timing retry applies and no transfer, reboot, or device-state mutation occurred.

## 2026-08-24 BLE bootstrap crash containment checkpoint

The live Pixel crash was reproduced twice on the `a57565b` candidate: `IllegalArgumentException: Invalid UTF-8 field length` at `PayloadCodec.decodeCapabilities` (`Payloads.kt:472/297`) from `BluetoothCompanionClient.handleEnvelope:895`. The cause was confirmed in source: `runCatching { EnvelopeCodec.decode(bytes) }.onSuccess { ... payload decode ... }` did not contain exceptions thrown inside `onSuccess`, so malformed Capabilities killed the main process. Pushed `77d58b0` wraps decode and dispatch independently, logs only message type/id, payload size, and a 16-byte hex prefix on rejection, and transitions the link to error without process death. No unverified wire-format fallback was added; all repository firmware versions emit two-byte length-prefixed capability fields.

The focused canonical Android wrapper (protocol tests, both flavor unit tests/lint/assemble; screenshot validation intentionally omitted) passed. Community APK `0.2.0-dev49`, 85,815,129 bytes, SHA-256 `3F9BD0A6233D91B888B5692A8B696858CEA7C931D73EA1D68CD57ADFB83DE139`; wrapper notice checks passed. Guarded retained-data install completed on current Pixel mDNS endpoint `192.168.1.61:36221`. The post-fix fresh launch stayed alive but produced no fresh Capabilities notification during the bounded observation; the visible Paired/100% state is retained state and not acceptance evidence. Therefore no fresh firmware version, StatusChanged, revisioned LibraryPage, or policy ACK chain exists. PC resolver correctly found no X3 `VID_303A:1001` (X3 remains Pixel-hosted), and no flash, slot read, reset, bond/data/timeout/NVS/SD/book mutation occurred.

Diagnostic correction: one attempted bare `adb` command failed PATH lookup; subsequent device work used the repository-resolved Android SDK executable through `use-toolchains.ps1` and the canonical target resolver. Never infer device state from bare PATH tooling.

## 2026-08-24 observed Capabilities wire correction and transport blocker

After a bounded standby-discovery wait, the app connected and received a 66-byte Capabilities envelope. The bounded rejection log recorded prefix `020058333300787472616f7264696e61`: length 2, model `X3`, then version length `0x0033` (51 bytes), followed by the version bytes. This exactly explains the prior `Invalid UTF-8 field length`: Android enforced 48 bytes while the current firmware emits 51. Pushed `7b0d855` raises only this length-prefixed Capabilities bound to 64 bytes and adds a 51-byte round-trip test; no speculative legacy parser was introduced.

The resulting focused canonical Android build passed and retained-data Community reinstall completed (`EEC00C70851B9AEE8A2A40F623ED6D4CFEED354BC9DF093119B4C830B09567B5`). Subsequent bounded relaunch and device-card reconnect attempts logged only `connect requested model=X3 phase=Disconnected`; no GATT connection or notification arrived across the configured standby interval. The parser fix is source/test-proven but not yet fresh-runtime accepted. Do not flash or infer from retained Paired/100%; require fresh Capabilities, StatusChanged, revisioned LibraryPage, policy ACKs, and RUNTIME_TRACE_OTA running==next boot.

## 2026-08-24 Pixel-host USB discovery boundary

The canonical resolver retained Pixel `192.168.1.61:36221`. Repository-resolved `adb shell dumpsys usb` showed `host_connected=true`, `source_power=true`, `connected=false`, `configured=false`, and no current host device or permission entry. Historical Espressif `manufacturer=12346` / `product=4097` records are Aug 22 only; the Aug 24 event log ends in USB removal. The guarded `UsbEspFlasher.refresh/findDevice` consequently found no present VID_303A:1001 target to wake or query. No USB command, reset, flash, slot read, or state mutation occurred; this is an external physical USB-enumeration blocker.

## 2026-08-24 fresh BLE and guarded OTA-trace checkpoint

After the reported X3 return, fresh BLE matched `7C:E8:B1:71:13:3E` at RSSI -41, opened GATT, discovered 3 services, negotiated MTU 256, subscribed notifications, and received Capabilities, two StatusChanged, four LibraryPage messages, and ACKs without a crash. The guarded debug `READ_X3_DIAGNOSTICS` intent completed over Android USB and returned active `RUNTIME_TRACE_ACTIVE boot=13 reset=8 ...`, but no `RUNTIME_TRACE_OTA`. `dev33-usb-reply-local` appears only in retained crash-report content, not running-version proof. Since running and next-boot application partitions cannot be proven equal, dev38 flashing was not invoked.

## 2026-08-24 selected-slot firmware/install blocker

After policy correction `e4aaa58`, canonical firmware `xtraordinary-v0.2.6-dev38-selected-slot-local` built successfully: 5,456,368 bytes, SHA-256 `E0E8FA4E3347BB4533CAF34E8CCF7CC25A4306E5988A7CBA64204190A0F389FE`, with source-bound release record. Current Pixel mDNS endpoint `192.168.1.61:36221` resolved and guarded retained-data Community installation completed (`0.2.0-dev49`, APK SHA-256 `09234D1766650323894660CF1135CB5765B8DE753B5125D4D3171722A81820FF`). Before X3 mutation, fresh BLE bootstrap crashed repeatedly at `PayloadCodec.decodeCapabilities` with `IllegalArgumentException: Invalid UTF-8 field length` (`Payloads.kt:287`, `BluetoothCompanionClient.kt:895`). No flash, live-slot read, BLE acceptance, reset, bond/data/NVS/SD/book, or timeout mutation occurred. Preserve all device state; fresh protocol proof remains outstanding.

## 2026-08-24 Codex overhead and model-routing correction

The repository workflow no longer reacts to high replay or five-point task growth by repeatedly creating fresh visible tasks, rerunning entry gates, and rereading the same policy set. One visible coordinator now owns related milestones. A single internal history-free worker is optional only when its compact packet is cheaper than continuing; sequential worker chains and duplicate coordinator inspection are prohibited. Usage is observed once at task entry and enforced once before a compiler or device stage. Replay, growth, and task consumption remain reported optimization evidence, while only the signed-in 95% weekly protected-stage floor is fail-closed without the documented user override. Ordinary inspection, editing, focused tests, documentation, and correction of pre-build blockers remain locally actionable.

Model routing is now Luna-medium-first for bounded discovery, logs, documentation, mechanical edits, focused tests, and build orchestration; Luna high handles bounded cross-file implementation, and Luna xhigh is used only after a concrete reasoning failure or for a costly bounded failure with external checks. Terra medium/high remains reserved for cross-layer state, firmware/device safety, unfamiliar weakly tested code, and mandatory UI specialist roles. Sol coordinates and resolves architecture/contradictions rather than performing bulk execution. The routing basis and limitations are recorded in `docs/codex-usage-workflow.md`: official API pricing makes Luna one tenth of Terra per token, published coding benchmarks are much closer, and independent Codex results do not show a consistent benefit from xhigh on ordinary bounded tasks. API pricing is not represented as the Codex weekly-meter multiplier.

This governance-only milestone did not compile, install, flash, discover, connect, reset, pair, or change the Pixel or X3. Continue from the selected-slot Android screenshot checkpoint below; the next product outcome remains classification/correction of the deterministic cross-flavor screenshot evidence drift before any accepted install.

## 2026-08-24 selected-slot Android/firmware protected-stage checkpoint

The canonical Android outputs already present in this worktree were recorded and their post-build notice checks passed. Community debug APK: 85,609,521 bytes, SHA-256 `09234D1766650323894660CF1135CB5765B8DE753B5125D4D3171722A81820FF`; Play debug APK: 90,183,253 bytes, SHA-256 `53685BC241A1084CCEA3AF68D40AE0B71926F937080FA41FE8A6EDA77C4BC416`. The ten screenshot cases intentionally deferred by user direction remain unaccepted UI evidence; this checkpoint does not claim UI acceptance.

The required sequential `scripts/build-x3-firmware.ps1 -Version xtraordinary-v0.2.6-dev38-selected-slot-local -Jobs 2` invocation passed usage, pushed-source, and notice checks but stopped before PlatformIO because `scripts/check-engineering-policies.ps1 -Mode FirmwareRelease` reported `weekly-reserve-override-is-ledger-bound-and-narrow`. No firmware binary/release record, target resolution, ADB install, selected-slot read/flash, BLE handshake, or Pixel/X3 mutation occurred. Phone app data, timeout `1800000`, bond, and X3 NVS/SD/books remain unchanged prior truth. A fresh protected-stage audit and policy resolution are required before any deployment mutation.

## 2026-08-24 selected-slot Android screenshot checkpoint

This fresh task attached exact source checkpoint `beca301a018f02169364abbd6002441621292e75` (which contains the deterministic-BOM fix `e32529b9a18ffd2aec6d60d6ca820db62881f05e`) to clean pushed branch `codex/x3-selected-slot-acceptance`. The ignored `.tools` junction now points to the established `C:\Users\cla20\Documents\Xteink\.tools`; Gradle 9.5.0 and PlatformIO 6.1.19 were present. The bounded regression audit found no conflict with the selected-slot, application-only, retained-bond/data/NVS/SD, fresh-capabilities, revisioned-library, or matching-policy-ACK contracts. Legal status remains not release-cleared.

The one canonical `scripts/build-xtraordinary-app.ps1 -AllowDeferredUiReviewDebt` invocation passed its task-bound usage, pushed-source, 25-file firmware notice, Community/Play Android notice-graph, nine-artwork provenance, and 165-rule engineering gates, with only the explicit pre-existing 46-item UI-review debt deferred. Gradle passed `:protocol:test`, both Community/Play Kotlin and Java compilation, both debug unit-test tasks, and both APK assembly tasks. The wrapper then failed before its post-Gradle APK-notice verification because both screenshot-validation tasks reported the same 10 failures out of 32: `devicesEmptyScreenshot_Devices empty`, `firstRunSetupLargeTextScreenshot_First-run setup large text`, `expressiveFocusLargeTextScreenshot_Expressive focus large text`, `expressiveFocusPhoneScreenshot_Expressive focus phone`, `deviceModelPickerScreenshot_Device model picker`, `firstRunSetupScreenshot_First-run setup`, `firstRunSetupCompactScreenshot_First-run setup compact height`, `quietFocusPhoneScreenshot_Quiet focus phone`, `expressiveFocusCompactScreenshot_Expressive focus compact`, and `setupDeviceScreenshot_Setup device`. The root pattern already established is deterministic cross-flavor rendered-evidence drift: the identical names and percentages fail in Community and Play, with large Focus artwork differences (15.61%-19.28%) and smaller setup/device rendering differences (0.12%-1.58%); no Android UI source changed after the prior `23acfd8` candidate. Do not accept or install these assembled APKs as a canonical passing build, and do not regenerate evidence without the required UI-review path.

The task entry audit passed at 27% weekly use and 0-point task growth. The compiler-bound audit passed at 28% and 1-point growth. The mandatory post-attempt audit also passed at 29% of the 10,080-minute window and 2-point task growth: 21 model calls; 1,198,588 input, 1,136,384 cached input, 62,204 uncached input, 6,599 output, 2,842 reasoning-output, and 1,205,187 total tokens; replay median 61,052, maximum 74,871, and recent median 73,679 after 119% growth; no replay/budget violation. The user then directed an immediate stop. No firmware compiler/artifact, target discovery, ADB, PnP/COM read, install, flash, reset, pair/re-pair, app-data/timeout change, NVS/SD/book/settings write, live-slot read, or BLE action ran. The phone and X3 were untouched; Community dev49/code 50, timeout `1800000`, retained bond, and the Pixel-hosted X3 remain prior truth only. Do not create another task from this checkpoint.

## 2026-08-24 selected-slot protected-build checkpoint

This milestone started from exact pushed `codex/x3-selected-slot-guard` commit `de1fae5dd960fc19f4fabf9cca157419f97b1767`. The fresh worktree was detached at that commit, so it was attached to the existing local/upstream branch after both refs were proven to match. The entry gate returned `warming-up`; the compiler-bound audit then passed at 25% weekly use, 2-point task growth, 49,628 replay median, and no replay/budget violations. The first Android wrapper call stopped before its own gates because the ignored `.tools` bundle was absent. Restoring the same junction used by the established worktree exposed the existing Gradle and PlatformIO bundles without changing tracked source.

The one evidence-based canonical Android retry from clean pushed `de1fae5` passed its task-bound usage and source-provenance gates, then stopped before Kotlin/Java compiler entry: the Android legal generator reported only `communityReleaseRuntimeLicenses.tsv` stale. A byte comparison proved both Community and Play dependency classpaths and license rows were unchanged; PowerShell 7 had omitted the three-byte UTF-8 BOM that the reviewed tracked reports contain. Pushed correction `e32529b9a18ffd2aec6d60d6ca820db62881f05e` centralizes deterministic UTF-8-with-BOM output for both license reports and the manifest. `scripts/test-android-release-notices.ps1` passed deterministic-BOM generation, canonical CRLF equivalence, and genuine-content rejection; the 165-rule Release gate passed with only the explicit pre-existing 46-item UI-review debt.

The mandatory post-attempt audit returned `compaction-required` at 26% weekly use and 3-point task growth because the recent input median reached 80,000 tokens after 146% growth. No further protected stage is authorized in this task. No Android compiler or APK, firmware compiler or artifact, ADB/mDNS/PnP/COM discovery, install, flash, reset, pairing/bond, app-data/timeout, NVS, SD/book, setting, live-slot read, or BLE action occurred. Community dev49/code 50, timeout `1800000`, retained bond, and the Pixel-hosted X3 remain prior truth only. Resume from clean pushed `e32529b` in a fresh task whose own audit passes; compile Android and `xtraordinary-v0.2.6-dev38-selected-slot-local` sequentially, then resolve the current targets and use only the guarded Windows selected-slot path. Accept the running firmware only from fresh BLE `Capabilities` plus `StatusChanged`, the final revisioned `LibraryPage`, and both matching current policy ACKs.

## 2026-08-24 selected-slot guard source checkpoint

This milestone started from pushed `a67371a6378865ef41b59a13c9d29ce358b53807` after the physical-evidence correction: the live USB diagnostic's `dev33` text belongs to the retained crash report, while the active boot-5 runtime trace contains no version. Neither dev33 nor dev37 is accepted as running. The dual-OTA table is authoritative: `otadata` at `0xE000`, app0/ota_0 at `0x10000`, and app1/ota_1 at `0x650000`. The prior dev37 hash remains app0 write evidence only; an unselected app1 is a concrete but still unproven root-cause hypothesis.

The reusable selected-slot policy is now implemented in source. New firmware emits `RUNTIME_TRACE_OTA` with both `esp_ota_get_running_partition()` and `esp_ota_get_boot_partition()` before the active trace. Android's USB flasher requests that live record before changing reset lines, requires the running and next-boot label/offset to match one of the two known slots, and otherwise fails before entering the bootloader. The guarded Windows flasher now loads `scripts/x3-ota-slot-policy.ps1`, reads only the `otadata` region with esptool before any application write, validates ESP-IDF sequence CRC/state, maps the selected sequence through `firmware/partitions.csv`, checks the artifact fits, and passes the resolved app0/app1 offset to `write-flash`. It does not rewrite `otadata`. `otadata` remains selected-boot evidence, not proof of what ran after bootloader fallback.

Focused `scripts/test-x3-ota-slot-policy.ps1` fixtures passed app0/app1 selection, independent CRC vectors, corrupt-newer-entry fallback, and invalid/aborted fail-closed behavior. `scripts/check-engineering-policies.ps1 -Mode FirmwareRelease` passed all 165 rules and the 25-file firmware notice pack. No Android or firmware compiler ran, so the Kotlin/firmware changes are source/static evidence only. The task-entry usage gate passed at 16% weekly use and 0-point task growth; the checkpoint re-audit correctly returned `compaction-required` at 21%, 5-point task growth, 140,931 replay median, 214,417 latest input, and 562% recent growth. Do not enter another compiler or device stage in this task.

The Pixel and X3 were not contacted. No ADB, COM, serial, build, install, flash, reset, pairing/bond, app-data, timeout, NVS, SD/book, setting, or BLE acceptance action occurred. Community dev49/code 50, Pixel timeout `1800000`, and the retained LE bond remain prior truth, not freshly rechecked here. The next fresh passing task must compile the source, then use only the guarded selected-slot path and require the complete fresh BLE `Capabilities` firmware version, `StatusChanged`, revisioned `LibraryPage`, and matching policy ACK chain before accepting the running firmware.

## 2026-08-24 protected discovery workflow checkpoint

The guarded deployment completed before this checkpoint: Community `com.xteink.companion` is installed as `0.2.0-dev49` / code `50` with retained app data; the source-matched dev37 application image was written only to `0x10000`, hash-verified, and the app relaunched. The prior evidence owner confirmed the `1800000` ms timeout plus a running app process after relaunch, the retained `XTEINK Companion` LE Secure Connections bond, and present Windows `USB\\VID_303A&PID_1001` composite/JTAG/serial interfaces with `COM7`. No reset, pairing/bond, app-data, NVS, SD, book, or settings mutation belongs to that evidence checkpoint.

`scripts/resolve-xtraordinary-deployment-targets.ps1` is now the shared, read-only deployment preflight. It keeps an authenticated ADB device; only when none exists and OpenScreen has no current TLS service does it restart the normal-user repository daemon with `ADB_MDNS_OPENSCREEN=0`, query Bonjour, and connect only a current `_adb-tls-connect._tcp` endpoint. It also checks present `VID_303A:1001` composite/interface PnP records before selecting an associated serial port. Focused fixtures pass, and the 158-rule engineering gate passes only with the existing explicit deferred UI-review debt (46 stale/missing UI-review findings); release mode remains fail-closed.

The X3 has now moved from the PC's COM7 connection to the Pixel USB host, so PC PnP absence is expected and must not trigger any reset, re-pair, reflash, timeout change, or COM-port probe. The retained authenticated Pixel (`192.168.1.61:36221`) returned `1800000` for the timeout and the debug app's read-only USB diagnostic completed, proving Pixel USB-host communication. It opens the live USB device and sends `CRASH_REPORT` plus `RUNTIME_TRACE`, so this is not an app cache. However, its `xtraordinary-v0.2.6-dev33-usb-reply-local` line comes from the retained crash report; the separate active trace is boot 5 and contains no version. Therefore neither dev33 nor dev37 is accepted as the running firmware. The flash script always writes `app0` at `0x10000`, while the partition table also has `app1` at `0x650000` and `otadata`; booting the unmodified app1 slot is a concrete, unproven explanation for the mismatch. The next bounded milestone must read the active boot/OTA selection non-destructively and add a guarded selected-slot policy before any flash. The retained LE Secure Connections bond remains present. No fresh BLE foreground `Capabilities`, `StatusChanged`, revisioned `LibraryPage`, or matching policy acknowledgement arrived. No install, flash, reset, pairing/bond, app-data, NVS, SD, book, settings, or COM-port mutation occurred in this checkpoint.

## 2026-08-24 canonical build/deploy retry checkpoint

Pushed source `23acfd87919f178c2d1cf055ce1b2f311439f187` on `codex/x3-build-deploy-acceptance` was clean and upstream-matched. The task-bound usage audit passed before Android, device, and firmware protected stages (weekly meter 6%, task-local weekly growth 0 points; the gate returned `within-budget`). The bounded regression-impact decision made no behavioral change: retain fast-lease-before-transaction, matching-ACK and final revisioned `LibraryPage` truth, Bluetooth bonds, Pixel app data/timeout, and X3 NVS/SD/books. Legal status remains **not release-cleared**.

One permitted `scripts/build-xtraordinary-app.ps1 -AllowDeferredUiReviewDebt` invocation passed pushed-source, notice, artwork-inventory, and 156-rule policy gates, then completed the canonical Community/Play unit-test, screenshot-validation, lint, and assemble stages. Both output APKs are version `0.2.0-dev49` / code `50`: Community `app/build/outputs/apk/community/debug/app-community-debug.apk`, 85,609,465 bytes, SHA-256 `4EB930B773D05C30094E1ADC31E8B748E59AE539B507311909289E61CBF5B72C`; Play `app/build/outputs/apk/play/debug/app-play-debug.apk`, 90,183,197 bytes, SHA-256 `ED59A0A6D9C51211B50E2458A6F2E5A9B9280DA4C9AAB95F2DE593500F34C067`.

The repository ADB daemon was restarted in the normal user context. Fresh mDNS twice advertised `adb-59081FDCR0011P-1eQHBH._adb-tls-connect._tcp` at `192.168.1.61:42795`; both attempts to connect to that current endpoint failed and `adb devices -l` stayed empty. Therefore no `adb install -r`, Pixel app-data/timeout change, Bluetooth-bond change, X3 flash, NVS/SD/book change, or handshake smoke occurred. Do not use remembered ADB ports; re-run the daemon/mDNS workflow and connect only a current TLS endpoint.

Installed X3 dev36's source baseline is `9378f4254b61c334eb0bf02649443ad595bf0fed`. The pushed candidate changes `firmware/src/companion/CompanionService.cpp` and `.h`, so a firmware artifact is required before acceptance. One non-concurrent canonical `scripts/build-x3-firmware.ps1 -Version xtraordinary-v0.2.6-dev37-book-reconciliation-local -Jobs 2` invocation passed source provenance, 25 firmware notice files, and the 156-rule policy gate. It produced `firmware/.pio/build/x3_companion_release/firmware.bin`, 5,455,808 bytes, SHA-256 `32D6ABC4545EA3B86D8A93BE3398B99C19DE5ACC5E350F78EF93BC52426D07DA`, plus its source-bound `xtraordinary-release-record.json`. Application-only flash through the guarded phone workflow and the version/handshake smoke remain blocked by the unavailable authenticated ADB connection. The record retains unresolved public-release obligations, including ArduinoWebSockets LGPL-2.1 static-link compliance and final reviewer/date approval.

### 2026-08-24 device-deployment checkpoint

Task `01a033d7-8315-7c83-a1fd-646fa27f147c` passed its bound `scripts/audit-codex-task-usage.ps1 -EnforceStageGate` before deployment (9% of the 10,080-minute signed-in window, 0-point task-local weekly growth, one 26,813-token model call, and no replay/budget signal). The clean, upstream-matched checkpoint is `20ee5f610bd40f40291b47223cd23dc8963b715b`; its only delta from the artifact source `23acfd87919f178c2d1cf055ce1b2f311439f187` is prior checkpoint documentation, so the app and firmware source remain artifact-matching.

Exact candidate artifacts were rehashed without rebuilding: Community APK `C:\Users\cla20\.codex\worktrees\8255\Xteink\app\build\outputs\apk\community\debug\app-community-debug.apk`, `0.2.0-dev49` / code 50, 85,609,465 bytes, SHA-256 `4EB930B773D05C30094E1ADC31E8B748E59AE539B507311909289E61CBF5B72C`; Play APK `C:\Users\cla20\.codex\worktrees\8255\Xteink\app\build\outputs\apk\play\debug\app-play-debug.apk`, `0.2.0-dev49` / code 50, 90,183,197 bytes, SHA-256 `ED59A0A6D9C51211B50E2458A6F2E5A9B9280DA4C9AAB95F2DE593500F34C067`; X3 application image `C:\Users\cla20\.codex\worktrees\8255\Xteink\firmware\.pio\build\x3_companion_release\firmware.bin`, `xtraordinary-v0.2.6-dev37-book-reconciliation-local`, 5,455,808 bytes, SHA-256 `32D6ABC4545EA3B86D8A93BE3398B99C19DE5ACC5E350F78EF93BC52426D07DA`.

The documented PC USB/serial check found serial ports `COM1`, `COM5`, `COM6`, `COM8`, and `COM9`, but no present Espressif/X3 `VID_303A&PID_1001` PnP target. Therefore `scripts/flash-x3-companion.ps1` was not invoked: its guarded release path additionally requires exactly one authenticated ADB phone, and a raw/esptool or `-SkipAndroidRelease` flash would violate the GATT-release contract. Separately, the repository ADB daemon was killed/restarted in the normal user context; `adb mdns services` returned `List of discovered mdns services` with no endpoint and `adb devices -l` returned no devices. No current TLS endpoint/port exists to connect, so no `adb install -r`, installed-package/flavor query, timeout query/change, firmware flash, reset, pairing/bond operation, app-data operation, or version/handshake smoke occurred. Pixel app data and its recorded `1800000` timeout, Android/X3 bond, and X3 NVS/SD/books remain unmodified by this checkpoint.

## 2026-08-24 canonical Android build compile blocker

The one authorized `scripts/build-xtraordinary-app.ps1 -AllowDeferredUiReviewDebt` invocation reached Gradle after the bound usage, pushed-source, firmware/Android notice, artwork inventory, and 156-rule engineering-policy gates passed (46 explicitly deferred UI-receipt findings only). Both CommunityDebug and PlayDebug then failed before APK creation at `app/src/main/java/com/xteink/companion/ui/CompanionViewModel.kt:1210`: `finalSnapshot` is the `Unit` result of the earlier input-digest `use` block (lines 1166-1175), but the code dereferences it as a revisioned library snapshot. The later upload result is discarded at lines 1177-1203. The failing line is from `e743bc88`.

No APK exists and no ADB/mDNS discovery, install, firmware comparison/build/flash, handshake, Pixel setting/data/bond change, or X3 NVS/SD/book/bond mutation started. Stop this deployment milestone. A narrowly owned source correction must retain the final revisioned BLE `LibraryPage` reconciliation contract (and USB's later BLE reconciliation) before it is committed/pushed and a new authorized canonical build is attempted.

## 2026-08-24 deployment-resume audit checkpoint

The earlier `01a02a88-cd91-7361-a6e3-1941183387f4` task did fail closed on its own 145,461-token replay input; that is retained as historical evidence below, not a result of this deployment owner. This fresh deployment task (`01a033be-a4a5-71e3-a5d7-1d813a0828d0`) passed `scripts/audit-codex-task-usage.ps1 -EnforceStageGate`: signed-in weekly use 4%, task-local weekly growth 0 points, replay median 30,915, latest input 41,093, and recent growth 10%. No weekly-reserve override is required or accepted.

The bounded regression-impact audit found no proposed behavioral change: deployment preserves the existing fast-lease-before-transaction and acknowledgement truth contract, keeps Bluetooth bonds, Pixel app data, X3 NVS/SD/books, and uses application-only firmware flashing if and only if a source-matching delta requires it. The legal audit remains **not release-cleared** and does not permit public-release claims. With this checkpoint committed and pushed, run exactly one canonical Android build with `-AllowDeferredUiReviewDebt`; no protected device stage has started yet.

## 2026-08-22 final release-artifact checkpoint

Pushed source is clean through `586cbac59fde4ac22b69217f5eb6b8858dd3cdc6` on `codex/x3-build-deploy-acceptance`. The fixed Android notice inventory now hashes only its declared text payload canonically: `scripts/test-android-release-notices.ps1` passed both CRLF/LF equivalence and genuine-content-mismatch rejection, and the resolved Community/Play graphs remained semantically unchanged at 147/180 modules. Legal status remains **not release-cleared**.

The one authorized canonical Android wrapper invocation with `-AllowDeferredUiReviewDebt -AllowDocumentedWeeklyReserveOverride` stopped before provenance, policy, Gradle, artifact creation, ADB, or device mutation. Its task-bound gate rejected the non-waivable conditions: 99% signed-in weekly use, 12-point task growth, post-compaction median input 106,239 tokens, latest input 125,009 tokens, and recent growth 182% to 124,470 tokens. There is no new APK to install, no artifact-upload destination to evaluate, no firmware build/flash, and no Pixel/X3 state change; installed X3 dev36, NVS, SD, bond, app data, and the Pixel timeout remain untouched.

Stop this release-artifact path here. A future fresh history-free task may repeat the canonical build only after its own protected-stage audit passes, then use `adb install -r`, conditionally application-flash a source-matching already-built firmware artifact if one exists, and record local artifact paths/SHA-256 unless an established repository destination is found. Do not infer a build, upload, install, or handshake from this checkpoint.

## 2026-08-22 build/deploy milestone checkpoint and next-task handoff

The Windows canonical-build portability correction is pushed at `fde20a9d1680081ee5616fba89f381e08a4f0f7a`. `scripts/check-firmware-release-notices.ps1` now hashes canonical LF bytes only for its fixed text notice inventory, retains true content/inventory mismatch failures, and has an executable CRLF-equivalence plus changed-content rejection fixture. No licence payload changed; only its previously mixed-line-ending manifest was regenerated. AGENTS, the usage workflow/ledger, and the engineering policy now make this scoped portability invariant durable.

The required post-push task audit then failed closed before any protected stage: 94% signed-in weekly use, 5 percentage points task-local growth, 30 calls, 62,539 median input tokens, 84,975 maximum input tokens, and recent input-median growth of 154% to 84,742 tokens. `-AllowDocumentedWeeklyReserveOverride` correctly refused to waive the independent replay/task-growth conditions. No Gradle/PlatformIO compiler, ADB/mDNS discovery, APK install, firmware flash, Bluetooth-bond change, Pixel app-data change, X3 NVS/SD write, or book mutation occurred. The Pixel timeout was not changed and remains required to be `1800000` ms after later device work.

Resume only in a fresh history-free bounded build/deploy task: audit first, then from clean pushed `fde20a9` run the canonical Android wrapper with the two documented overrides only if its audit passes. Continue with retained-data ADB install, dev36 source delta, guarded application-only firmware flash if required, and the focused safe-fixture transfer acceptance matrix. Legal status remains **not release-cleared**.

The explicit, non-default release-debt controls are pushed at `c3c4cf8` and `c09c6a7`. Android Release still fails by default on the same 27 correlated `ui-changes-require-stable-terra-reviews` findings. `-AllowDeferredUiReviewDebt` accepts only that exact rule after a documented ledger authorization, prints every deferred finding, and leaves the other 148 engineering rules fail-closed. `-AllowDocumentedWeeklyReserveOverride` accepts only the documented weekly-meter reserve exception and cannot waive replay or task-growth safety.

No compiler or deployment started. The fresh build task reached 84% signed-in weekly use with only 2% task-local growth, but its recent replay median grew 93% to 84,602 tokens. The independent replay guard correctly rejected the weekly override before source provenance/compiler entry. No ADB/mDNS discovery, APK install, firmware build/flash, Bluetooth-bond change, Pixel app-data change, X3 NVS/SD write, or book-library mutation occurred.

The next substantial milestone is a compaction-safe canonical build/deploy/physical-acceptance task. It must first run the bounded regression-impact audit against the existing power/sync/pairing/state/legal policies and measured hardware evidence, then run a fresh task-local usage audit. If replay passes, invoke `scripts/build-xtraordinary-app.ps1 -AllowDeferredUiReviewDebt -AllowDocumentedWeeklyReserveOverride`, install with `adb install -r`, determine whether firmware differs from installed dev36 before any application-only flash, and execute the preserved-data transfer/background/reconnect acceptance matrix. The remaining product backlog stays authoritative in `docs/x3-takeover-tracker.md`; duplicate historical entries are evidence history, not permission to rerun proven work.

## 2026-08-22 bounded book-transfer milestone checkpoint

Pushed `e743bc886f55f911d9491ad98b4e732481364e10` completes the source-side reconciliation correction and the bounded-milestone governance rule. A successful BLE upload now returns its final revisioned `LibraryPage` snapshot to the transfer owner; that snapshot, rather than a `CommitBookUpload` ACK, is the only source that marks a phone book present on X3. USB uses the same snapshot when BLE is already ready and otherwise keeps the phone library conservatively unreconciled until a later BLE snapshot. The durable queue still clears only after a successful transfer transaction; terminal rejection, unreadable/empty source, validation failure, explicit Stop, and task swipe remain non-replayable through the shared lifecycle policy.

The exact pre-compiler source/static evidence passed: policy JSON parses, the new bounded-milestone required-pattern matches, `git diff --check` passed, and focused source assertions passed for final BLE snapshot return, snapshot-only ViewModel reconciliation, immediate BLE snapshot after USB when available, and removal of the optimistic `/Books/<name>` assignment. The full engineering gate reached only the user-deferred 27 missing/stale `passes`/`android-ui` UI-review receipt failures; it reported no new governance or transfer-policy violation.

No canonical Android or firmware compiler, APK installation, firmware flash, ADB/mDNS device stage, or physical acceptance ran. The task-local pre-stage audit ended `weekly-budget-exhausted` at the signed-in 80% / 10,080-minute reserve boundary (task-local growth 2 points, 12 calls, 75,349 median input tokens), so the wrapper/device stages are correctly prohibited before their own gate or any hardware mutation. The working tree is clean and source provenance is pushed. Phone app data, Bluetooth pairing/bond, X3 NVS, firmware, SD/library files, and all installed artifacts are unchanged.

Next verifiable outcome after the weekly reserve resets or an explicit authorized budget exception: run a fresh bounded task, repeat its usage audit, run the canonical Android wrapper (which will be blocked until exactly the deferred receipt class is resolved), then use only a reviewed built APK and application-only firmware artifact for the preserved-data physical matrix: foreground/background truth, background upload continuity, swipe/Stop/rejection cleanup and no replay, picker/OPF behavior, row-local/multi-book/direct-send flows, final library/delete snapshot reconciliation, same-name rollback, metadata/cover/X3-only filtering, and wake/reconnect state truth.

## 2026-08-22 Quiet Read generated-evidence checkpoint

Fresh task `01a02a44-1f16-7fa1-892c-69f96672a966` passed its task-local usage gate and ran exactly one canonical `scripts/build-xtraordinary-app.ps1 -UiEvidenceCandidate` from clean pushed `d98a9ff1f5d9d03a094cad6eef4f0128dff8b9a3`. Source provenance, all 139 engineering rules, Community/Play unit tests, and both deterministic screenshot-update tasks passed; Gradle completed 64 tasks in 1m16s. No APK install, firmware build/flash, bond, app data, NVS, or SD state changed.

The build generated two required Quiet active-upload leaf references. Both are 1082x1995 and byte-identical at SHA-256 `396604331CE6845759C0A7E2485581E9C386BCE516123CDCD8E9318C66051D90`:

- `app/src/screenshotTestCommunityDebug/reference/com/xteink/companion/ui/CompanionPreviewScreenshotTestKt/quietReadUploadActiveScreenshot_Quiet Read upload active_38ba0625_0.png`
- `app/src/screenshotTestPlayDebug/reference/com/xteink/companion/ui/CompanionPreviewScreenshotTestKt/quietReadUploadActiveScreenshot_Quiet Read upload active_38ba0625_0.png`

Default-size and nearest-neighbor 2x inspection passed the evidence-generation check: the upload row stays legible in Quiet grayscale, its progress hierarchy is clear, and the Stop glyph remains one centered square with clean negative space and no competing ring. The generated update also touched 20 unrelated Community/Play references; those build-only changes were restored from the clean baseline, leaving only the two deliberate Quiet Read PNGs. `docs/ui-review-policy.json` now requires both exact leaf paths; receipt hashes remain the responsibility of the source-bound reviewers.

Continue with exactly the narrow `shape-affordance`, `motion-interaction`, and `accessibility-adaptive` Terra reviews. Rebind earlier passing roles only if the final source/evidence identity requires it, then commit/push receipts and run the canonical Android build before any deployment.

## 2026-08-22 fresh-task audit exit-status checkpoint

The thread-bound audit now passes against the invoking fresh task, but the first and only `-UiEvidenceCandidate` invocation in that task stopped before source provenance, policy checks, or Gradle. Its internal audit printed `status: within-budget` for thread `01a02a40-78b5-7781-b90b-edea8f3f9f50`, then the Android wrapper misread a stale nonzero PowerShell `$LASTEXITCODE` and threw at line 66. No screenshot reference, APK, install, flash, bond, app data, NVS, or SD state changed.

The reusable process-status correction is pushed at `9694b50`: `scripts/audit-codex-task-usage.ps1` now returns explicit exit code zero after a passing report, and `docs/engineering-policy.json` prevents that contract from regressing. A contaminated-exit test seeded `$LASTEXITCODE=17`, received `within-budget`, and observed exit zero. The evidence-mode engineering gate passed all 139 rules.

Fresh-task continuation order:

1. From clean pushed `9694b50`, run the task-local usage audit and exactly one canonical `scripts/build-xtraordinary-app.ps1 -UiEvidenceCandidate`.
2. Inspect the generated Community/Play diff and retain only the deliberate Quiet Read active-upload references. Add the exact generated Quiet PNG paths and hashes to `docs/ui-review-policy.json`; do not accept unrelated reference churn.
3. Run only the narrow `shape-affordance`, `motion-interaction`, and `accessibility-adaptive` Terra reviews described below, then rebind earlier passing roles only as required by final source/evidence identity.
4. Commit/push reviewed evidence and receipts, then run the canonical Android build before any deployment.

## 2026-08-22 Quiet transfer evidence checkpoint and mandatory task reset

This fresh continuation reached the repository's hard task ceiling before a protected build stage. The explicit root audit reported `handoff-required`: 23 top-level calls, 1,895,226 input tokens (1,791,616 cached), 90,371 median input tokens, 114,997 maximum input tokens, three percentage points of task-local weekly growth (44% to 47%), and no compaction. Do not continue review, compilation, install, flash, or device acceptance in this task context.

The pushed source is safe and the worktree is clean at `5ef87c0`. That commit preserves only the eight deliberate Community/Play Read references, removes the unrelated generated screenshot noise, and adds one centralized `Quiet Read upload active` fixture through the shared transfer screenshot content. The Impeccable mechanical detector returned an empty finding set for the changed screenshot source.

Read-only Terra review status bound to `ReadContent.kt` SHA-256 `645632C38B934C025E5BA0424C98FFD66AB3C932CF03B250579421981A3A18EE`:

- `color-contrast` passed with no findings. Measured Expressive ratios were 5.85:1 for the upload label against the row, 5.32:1 for indicator against track, 7.43:1 for passive location text, and 12.43:1 for Stop. Quiet grayscale was source-derived because the missing Quiet active-upload render had not yet been generated.
- `shape-affordance` passed containment, target, and resting-action checks but correctly blocked `glyph-geometry-is-optically-separated` only because the contract requires a hashed current Quiet active-upload PNG. Default actual-size and 2x inspection found the Stop glyph optically clean after `fa61f83` removed the competing ring.

The attempted `-UiEvidenceCandidate` run exposed a usage-gate defect and was cancelled immediately after initial Gradle preprocessing and `:protocol:compileKotlin`; it generated no new screenshot reference, APK, install, or device mutation. The explicit root audit had already returned `handoff-required`, but the composed shell continued because that status did not fail the process. The wrapper's own audit then selected the most-recent specialist rollout (`...17-57-32-01a02a31-0c12-7c82-a3e4-d8a791ac954b.jsonl`) rather than the root rollout and reported it within budget. Before another protected stage, make `-EnforceStageGate` exit nonzero on `handoff-required` and bind canonical wrappers to the invoking root rollout rather than the newest rollout file.

Fresh-task continuation order:

1. Fix and test the audit/wrapper rollout-binding failure without starting a compiler.
2. From clean pushed `5ef87c0`, run exactly one canonical `scripts/build-xtraordinary-app.ps1 -UiEvidenceCandidate` to generate the new Quiet Read transfer PNG for Community and Play.
3. Inspect and stage only deliberate Read references, add the exact generated Quiet paths to `docs/ui-review-policy.json`, and run only the narrow `shape-affordance` re-review plus the still-outstanding `motion-interaction` and `accessibility-adaptive` reviews. Rebind the other passing roles only as required by the final source/evidence hashes; do not launch an optional critique wave.
4. Record final source-bound receipts, commit/push, then pass the canonical Android build before any `adb install -r` or application-only firmware flash.

No temporary phone setting remains. No Bluetooth bond, app data, X3 NVS, SD data, firmware, or installed APK changed in this continuation.

## 2026-08-22 usage-bound continuation checkpoint

The current Codex task is mechanically over budget and must not perform another broad investigation, reviewer wave, compiler, install, flash, or device acceptance. `scripts/audit-codex-task-usage.ps1` reported 288 calls, 48.4M tokens, 176k median input per call, four compactions, and a weekly-meter increase from 8% to 42%. Continue the same objective in a fresh task from this file; this is a context reset, not a product-work stop.

Preventive enforcement is now source state: run `scripts/audit-codex-task-usage.ps1 -EnforceStageGate` at fresh-task entry and before each costly stage. Protected ceilings are 20 calls, 75k median input after three calls, 120k latest-call input, zero compactions, five weekly percentage points per task, and 80% total weekly use.

Current product boundary to resume:

- pushed implementation through `fa61f83` contains the book-transfer lifecycle fixes and the latest Read color/Stop-glyph correction;
- X3 dev37 firmware built successfully but is not flashed;
- Android dev49 is not installed and no final canonical Android build exists;
- latest Community/Play Read evidence was regenerated after `fa61f83`, but the worktree also contains unrelated changed screenshot references; inspect and stage only deliberate references;
- hierarchy, layout, typography, and UX review results exist; color and shape blocked the prior candidate and need one narrow re-review of `fa61f83`; motion and accessibility remain outstanding; no final source-bound receipts exist;
- physical acceptance still must cover foreground/background link truth, background upload continuity, Stop/abort cleanup, rejection without a zero-chapter library entry, exact EPUB picker filtering, row-local progress, direct-send offer, multi-book upload, library/delete reconciliation, cover parity, and X3 library filtering;
- preserve Bluetooth bond/NVS/SD/app data. Re-discover wireless ADB through the repository mDNS workflow. Pixel timeout was restored from `7200000` to `1800000` ms through `Android_LPVTV8X5.local.:34473` at this checkpoint.

## 2026-08-22 book-transfer/background incident candidate

The installed Pixel build is still `0.2.0-dev43`; Android `0.2.0-dev49` / firmware `dev37` are source candidates not yet built, installed, flashed, or physically accepted. The current device evidence isolated the reported failures rather than treating them as UI noise: terminal X3 NACKs were left in the durable queue and replayed on reconnect/foreground; cancellation did not join the old worker before clearing UI; delete released GATT after the command ACK rather than the final `LibraryPage`; USB used 240-byte chunks despite the safe 488-byte envelope; retrying an existing destination was rejected; and X3-only Open Library lookup used polluted filenames with a seven-day retry delay.

The dirty source candidate now centralizes terminal-versus-transient transfer failure, keeps active transfers in a foreground service, retains the fast/persistent Bluetooth path through backgrounding, treats task removal as real cancellation, places progress on the active book row, clears selection when transfer starts, prompts to send newly imported books, filters the picker to EPUB, rejects OPFs with no readable spine item, normalizes X3-only metadata lookup, waits for the final library snapshot after upload/delete, uses 488-byte USB chunks, and publishes verified replacements through a hidden rollback file. The canonical behavior is in `docs/x3-power-sync-flow.md`; matching policy rules and tests must pass before compilation.

Remaining acceptance is deliberately explicit: commit/push the complete source; run one bounded Android evidence build and the mandatory source-bound UI reviews; run one canonical Android build; build/flash firmware application-only; install with `adb install -r`; preserve app data, SD, NVS, bond, and pairing; then physically test brief background/foreground, long background transfer, task-swipe cancellation/no replay, rejected source/no replay, USB and BLE success, same-name atomic replacement, delete/snapshot reconciliation, Project Hail Mary cover recovery, and `test_tables.epub` real pagination. The Pixel screen timeout was temporarily raised from 30 minutes to two hours for this session and must be restored to `1800000` ms before handoff ends.

## 2026-08-20 legal-release repository closeout

The repository-closeable notice and provenance controls are now implemented without an Android compiler, APK install, firmware build, flash, or device mutation. `release-notices/android` records the exact Community/Play release runtime graphs (147/180 modules), POM licence metadata and hashes, 38 exact-version overrides for undeclared POMs, the complete current Apache-2.0 and Checker Framework MIT texts, official Google component terms pointers, and manifest SHA-256 `3BD61CB44B42F5FC16DB37B93486D876E19410E1DF8622215B94D45ECC21BC37`. The pre-compiler gate re-resolves both graphs and rejects stale reports, uncovered POMs, or a changed manifest. Android no longer discards every `META-INF/AL2.0`/`LGPL2.1` entry; both legal packs are embedded as assets and assembled APKs are byte-verified against them.

`docs/artwork-provenance.tsv` now hash-binds all nine production/concept PNGs and records dimensions, introducing commits, dates, and Git authors. It deliberately leaves generator account, prompt/source chain, commercial-output terms, and human approval as `MISSING`; Git history is not misrepresented as rights clearance. Release Android variants now fail closed on XTEINK's pinned OEM image unless `xtraordinaryXteinkOemPermissionAcknowledged=true` is deliberately supplied, while debug builds retain the private maintenance path. Every canonical X3 build now emits a JSON release record binding the binary to the exact Xtraordinary commit/source URL, CrossPoint `1.4.1` baseline, binary/notice hashes, and modification documents.

Decorative XTEINK word-mark text has also been removed from ordinary setup, status, discovery, library, and Focus-preview surfaces; they now use `X3` or `X3 reader`. The name remains only in legal compatibility attribution, the explicit OEM firmware source, and the existing Bluetooth advertising/UUID identity. Do not rename that internal pairing identity casually: it requires a coordinated firmware/app migration and could break existing bonds.

The remaining legal blockers are external or require a deliberate engineering/legal choice: ArduinoWebSockets 2.7.3 is confirmed statically linked by the current firmware map (`WebSockets.cpp.o` and `WebSocketsServer.cpp.o`), so LGPL compliance/replacement is still required; artwork rights fields remain missing; XTEINK brand/product-image permission remains missing; enabling OEM access publicly requires written permission; final publisher identity/jurisdiction/public URLs and Play/AdMob/UMP/OAuth/flight contracts remain incomplete. The exact signed APK/AAB and generated firmware record still need final release review. Do not describe the product as release-cleared.

## 2026-08-20 dev36 USB/radio synchronization deployment

The pushed `9378f4254b61c334eb0bf02649443ad595bf0fed` firmware correction was canonically built as `xtraordinary-v0.2.6-dev36-usb-radio-sync-local`. The firmware-release path passed pushed-source provenance, 98 engineering rules, ESP32-C3 RV32IMC verification, and the release build at 35.2% RAM / 83.0% flash. Artifact: 5,455,232 bytes, SHA-256 `15E79602C11E6D8736CD275A7D1EDDCD576E35DC7F69A7DBD98B3F3A8F3A6A5A`. The guarded COM7 workflow received Android's `PERIPHERAL_RESET_READY`, wrote and verified only the application partition, relaunched the existing dev43 app, and preserved NVS, the Secure Connections bond, phone app data, and SD.

The first post-flash phone bootstrap passed at RSSI -34: API 37 status-0 GATT, three services, MTU 256, notifications, Capabilities, StatusChanged, four LibraryPage replies, and both policy acknowledgements. The USB/radio lifecycle defect is physically accepted. After the initial USB/JTAG open reset the X3 to boot 4, two subsequent recognized `RUNTIME_TRACE` commands left the boot counter unchanged. The immediately following phone launch matched at RSSI -37 and completed a fresh encrypted capabilities exchange. This proves valid reset-free USB activity now extends the shared inactivity deadline and rearms fast advertising through the reusable `wakeFastAdvertising()` policy.

The first reconnect harness run passed two cycles, then its third plain foreground launch correctly missed the 1.5-second/30-second standby pulse. That was a verifier defect, not a radio failure: foreground visibility owns only one 15-second status probe, while only durable pending work retries across standby pulses. `scripts/verify-x3-reconnect.ps1` now refuses to run without an explicit discovery precondition: `-PersistentWorkAlreadyQueued` or a bounded `-WakeViaUsbPort`. The firmware-release gate enforces this contract as rule 99.

Current applied phone policy is synchronized (`sync_pending=false`): five-minute fast window, 30-second standby pulse, two-second connected-background interval, ten-minute sleep, 15-page full refresh, and instant Power. With Android force-stopped and no subsequent traffic, COM7 disappeared at 630 seconds, directly observing the configured deep-sleep transition rather than a live CPU lockup. The retained trace for this exact boundary is still unread because deep sleep physically disconnects USB and a targeted Windows restart correctly reports that the device is not connected. On the next physical Power press or cable replug, retrieve the retained trace before declaring the final-sync incident fully closed; expected evidence is previous boot 4 with reset `8` and no new panic marker.

Signed-in Codex usage was checked through `account/rateLimits/read` before and after the one build: 2% used both times in the 10,080-minute window, below the five-point ledger threshold. No Android compiler, APK install, bond reset, NVS erase, app-data clear, or SD mutation occurred.

## 2026-08-16 reconciliation: implemented is not the same as physically accepted

The 2026-08-16 work is a Passes UI and engineering-process slice, not completion of the entire takeover. It hardened the shared pass hierarchy, responsive type/layout, selected Static/Live action ownership, immutable pending-operation truth, reduced-motion policy, review provenance, usage accounting, and the face-change affordance. The pass face now uses one centred labelled control, an optional small QR cue only when width permits, and no detached chevron. The reusable visual-language course and policy gate explicitly reject compound paper/arrow glyphs and orphaned directional punctuation.

The two independent appearance axes are implemented in source. A draggable sea/astronaut picture carousel is the visual **light/dark mode** selector: dragging or selecting a scene previews and chooses the corresponding mode. The separate Settings choice is **Expressive/Minimal** and remains the concise, accessible control for visual complexity. Minimal uses its own shapes plus reduced density, motion, and decorative treatment rather than merely recoloring the same layout. Both choices persist independently. Pixel drag/tap, restart, reduced-motion, and TalkBack acceptance still remain.

Release-readiness also requires a first-class legal and attribution surface. Audit and ship the notices for the Android app, the forked reader firmware, every redistributed library/font/icon/art asset, and upstream firmware components; preserve required source/license offers and attribution text. Separately resolve permission and wording for XTEINK names, logos, product imagery, and compatibility claims, and publish user-facing Terms of Service and Privacy Policy that match the actual backup, flight, entitlement, advertising, diagnostics, and account behavior. Repository license files alone are not proof that this product-level work is complete.

The orphan-chevron correction is pushed at `f31169d`. Its canonical UI-evidence candidate passed source provenance, 97 engineering rules, Community and Play compilation, both unit-test suites, and both deterministic screenshot-update suites in 1 minute 8 seconds. Fresh Default and Quiet renders then passed the narrow read-only Terra affordance review. This is compiled/rendered evidence, not a phone install or X3 flash. The Release policy mode deliberately remains blocked until the current Passes source has the complete eight-role source-bound review record; the narrow affordance review is not misrepresented as that full gate.

The radio, battery, synchronization, and reading-statistics work was not discarded or replaced by this design pass. Its canonical behavior is documented in [`docs/x3-power-sync-flow.md`](docs/x3-power-sync-flow.md) and [`docs/interactive-transport-lifecycle.md`](docs/interactive-transport-lifecycle.md): five minutes of fast discovery by default, a 1.5-second advertising pulse every 30/60/120 seconds in standby, a separate 1/2/4-second connected-background interval, a 10-minute default inactivity sleep, and a generic scoped interactive lease. Desired phone settings remain distinct from matching X3 acknowledgements. Focus and an open Live ticket keep X3 awake and pulse-discoverable without retaining GATT solely because the surface is visible. Reading sessions are journalled on X3, transferred in chunks, persisted on Android before ACK, and displayed as cumulative and per-session statistics; the real two-session/eight-sample import was previously accepted.

Evidence status must remain explicit:

- **Implemented and protocol/real-device evidenced:** fresh Secure Connections bonding; encrypted GATT bootstrap; capability/status/policy and reading-statistics ACK chains; Bluetooth-off blocker truth; idle GATT release; bounded settings delivery; generic interactive ownership; Home standby text derived from the applied interval; end-to-end reading-statistics import.
- **Implemented but still awaiting current physical acceptance:** Home current between standby pulses; Focus/Live wakefulness and update latency; Reading drain/current measurement; Static/Live removal and persistence; current pass gestures, e-ink matrix/linear rendering, rotated scanner, ghosting, barcode scanning, and ACK/power behavior. Rapid Settings-edit stress is now physically accepted and recorded in the tracker.
- **Firmware acceptance in progress:** `xtraordinary-v0.2.6-dev35-final-sync-local` canonically built and was application-only flashed with SHA-256 `A8B157833668BD14A7022DECAC8B8D48C438DAAC2F04311B35A9A0B19AE0E9CC`; write verification and the full encrypted post-flash bootstrap passed with NVS, bond, app data, and SD preserved. Repeated final-sync sleep-boundary reconnect/wake cycles and retained-crash readback are still required before the CPU-lockup blocker closes.
- **Latest physical radio diagnosis:** two independent retained traces now show ordinary deep-sleep reset `8` and no new panic reason, while the old crash file remains stale. The first reconnect harness also raced a new direct GATT against the preceding ACL teardown because Android took about five seconds to close the link; the harness now observes the real X3 ACL and requires a fresh capabilities handshake. A later awake/healthy-loop run isolated a separate source defect: recognized USB commands extended `lastActivityAtMs` but did not rearm `advertisingWindowStartedAtMs_`, so Bluetooth expired at its older five-minute deadline while USB kept the CPU awake. The source now routes valid USB activity through generic `wakeFastAdvertising()` and the policy gate requires the two deadlines to remain synchronized. This correction is source-only and deliberately unbuilt/unflashed at the 92%-used Codex stop checkpoint.
- **External work still open:** production Google Drive OAuth acceptance; a deployed live-flight proxy/provider and credentials outside the APK; production entitlement backend and Play/AdMob/UMP account configuration; accessibility and the complete canonical regression/physical acceptance gate.

## 2026-08-20 Play monetization and release-rights candidate

Android `0.2.0-dev48` now has a real development integration rather than only a monetization state model. The `play` distribution owns Google Play Billing 9.1.0, Google Mobile Ads 25.4.0, and UMP 4.0.0 through flavor-scoped dependencies. It queries and launches the one-time ad-free product, restores purchases, represents pending purchases explicitly, requests consent information and required privacy options, and shows one anchored adaptive banner only when the central policy allows the current browsing surface and no hardware operation is active. Settings exposes the localized Play price, buy, restore, privacy, and public community-source actions. Debug uses Google's published sample ad identifiers and labels itself as test configuration.

The `community` distribution remains SDK-free and always ad-free. A non-debug Play purchase deliberately remains unacknowledged and in `Verifying` until an authenticated backend verifies its token; the app does not turn a local timestamp or Play acknowledgement into production entitlement. Production still requires the final Play product/test accounts, AdMob identifiers, published UMP messages, authenticated trial/purchase/refund/revocation service, OAuth configuration, store Data safety declarations, and real Play-installed acceptance. A sideloaded debug build cannot prove Play purchase availability.

User-facing Privacy, Terms, and Notices now disclose the Community/Play boundary and purchase verification. `THIRD_PARTY_NOTICES.md` inventories Billing, Mobile Ads, UMP, CrossPoint, CrossInk, Open X4, fonts, and vendored libraries. `docs/firmware-brand-release-rights.md` keeps open-source copyright, XTEINK trademark/product imagery, and OEM-firmware permission as separate release questions. The repository now contains the full Ubuntu Font Licence and uzlib licence, but the legal audit remains intentionally **not release-cleared** until its evidence rows close.

The Play integration and legal boundary are pushed through `a3ef139`. After specialist review found three concrete omissions, Settings now exposes an explicit **Continue with free** exit, reserves saturated primary color for the purchase transaction, and has deterministic Expressive/large-text/Quiet evidence. The final candidate run from pushed `a3ef139` passed the 122-rule policy gate, Community and Play compilation, both unit-test suites, and both screenshot-update suites in 1 minute 6 seconds. The preceding run stopped before rendering on a fixture-only enum typo; it produced no accepted evidence. The current PNGs were visually inspected after a final preview-host correction made Quiet propagate `onBackground` like the real Settings surface.

Do not run the canonical APK build or install dev48 yet. At the mandatory usage cutoff, the seven-day meter was 83% used. Fresh source-bound `motion-interaction` and `accessibility-adaptive` receipts remain missing, and the six earlier roles must be rebound to the final source/evidence commit before `Release` mode can pass. No phone app, X3 firmware, bond, NVS, app data, or SD state was changed in this monetization slice.

The authoritative consolidated open list is the final 2026-08-16 section of [`docs/x3-takeover-tracker.md`](docs/x3-takeover-tracker.md). Older unchecked tracker entries may describe superseded candidates; they are history unless repeated in that consolidated list.

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
