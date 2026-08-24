# Codex usage ledger

## 2026-08-24 - Selected-slot Android screenshot checkpoint

- Task: `01a03418-5baa-70e0-bf5f-14f9670160e5`. Entry passed at 27% of the 10,080-minute signed-in window with 0-point task growth. Compiler entry passed at 28% and 1-point growth. The post-build audit passed at 29% and 2-point growth, resetting at Unix `1788169810`, with no replay/budget violation.
- Post-attempt token evidence: 21 model calls; 1,198,588 input, 1,136,384 cached input, 62,204 uncached input, 6,599 output, 2,842 reasoning-output, and 1,205,187 total tokens; replay median 61,052, maximum 74,871, and recent median 73,679 after 119% growth.
- Build boundary: clean pushed `beca301` passed usage, source provenance, notices, artwork provenance, and the 165-rule gate with only the documented 46-item UI debt deferred. Gradle completed protocol tests, Community/Play compilation, unit tests, and APK assembly, then both 32-case screenshot validations failed on the same 10 image-backed/adaptive cases. The wrapper never reached post-Gradle APK-notice verification, so the assembled APKs are not accepted for deployment.
- User-directed stop: no further screenshot investigation, build, firmware, target discovery, install, flash, live-slot read, or BLE/device action is authorized in this task. The phone and X3 were untouched. No automatic continuation task was created.

## 2026-08-24 - Selected-slot protected-build checkpoint

- Task: `01a0340f-8c19-7153-a870-15917f706dff`. Entry returned `warming-up`. The compiler-bound audit passed at 25% of the 10,080-minute signed-in window, 2-point task-local growth, 49,628 replay median, and no replay/budget violations; the environment-corrected retry audit also passed at 26% and 3-point growth.
- Build boundary: the first Android wrapper call stopped before gates because the fresh worktree lacked its ignored `.tools` junction. After restoring the established junction, the sole canonical retry from clean pushed `de1fae5` passed usage and source provenance, then stopped before compiler entry because PowerShell 7 emitted the reviewed Community/Play license inventories without their UTF-8 BOM. The dependency classpaths and license rows were unchanged. Pushed `e32529b` makes the generated license reports and manifest encoding deterministic; the focused Android notice fixture and 165-rule Release gate pass with only the explicit pre-existing 46-item UI-review debt.
- Stop decision: the post-attempt gate returned `compaction-required` at 26% weekly use and 3-point task growth because recent input median grew 146% to 80,000 tokens. No Android compiler/APK, firmware compiler/artifact, ADB, COM, device read, install, flash, reset, pair/re-pair, bond/app-data/timeout change, NVS/SD/book write, setting edit, live-slot read, or BLE acceptance ran. Resume from clean pushed `e32529b` only in a fresh passing task, then keep Android and firmware builds sequential and preserve the selected-slot/fresh-capabilities acceptance boundary.

## 2026-08-24 - Selected-slot guard source checkpoint

- Task: `01a033fe-a8b8-7200-a375-13182ed695b7`. Entry `-EnforceStageGate` returned `within-budget` at 16% weekly use, 0-point task-local growth, one 27,017-token input (90.02% cached), and no replay/budget signal. The checkpoint audit returned `compaction-required` at 21% weekly use, 5-point task-local growth, 140,931 replay median, 214,417 latest input, and 562% recent growth; no later protected stage is authorized in this task.
- Source result from pushed base `a67371a`: firmware now exposes running and next-boot OTA partitions in the live runtime trace; Android requires matching known-slot evidence before reset; Windows reads and CRC-validates `otadata`, resolves app0/app1 through the checked-in partition table, and uses the selected offset instead of fixed app0. The raw workflow never rewrites `otadata`, and all records preserve the distinction between selected boot target and running-firmware acceptance.
- Verification/evidence boundary: focused PowerShell fixtures passed, and the 165-rule `FirmwareRelease` static gate plus 25-file notice pack passed. No compiler, ADB, COM, device read, install, flash, reset, pair/re-pair, bond/app-data/timeout change, NVS/SD/book write, setting edit, or BLE acceptance ran. Neither dev33 nor dev37 is accepted as running; resume from a fresh passing audit and require selected-slot evidence plus the complete fresh BLE protocol-ready chain.

## 2026-08-24 - Protected discovery workflow checkpoint

- Task: `01a033f6-ba01-7e91-8ab7-f20f325c48ab`. Entry `-EnforceStageGate` returned `within-budget`: 14% used in the 10,080-minute signed-in window, 0-point task-local weekly growth, one 31,211-token call (77.92% cached), no compactions, and no replay/budget signal.
- Source checkpoint: the new shared preflight retains an authenticated ADB target, uses Bonjour only after an empty OpenScreen current-service result, and maps present X3 `VID_303A:1001` composite/interface records before COM selection. Its focused fixture passed. The full 158-rule policy gate passed with the existing explicit 46-item UI-review debt only; release mode still fails closed. The Android notice generator refreshed only UTF-8 BOM/manifest hashes after the checker detected stale inventories.
- Protected physical evidence: the retained authenticated Pixel at its current endpoint returned `screen_off_timeout=1800000`; the normal foreground probe received no fresh BLE protocol packets. The debug app's read-only USB diagnostic did complete, proving Pixel USB-host communication. It is a live serial request, but the `CrossPoint version: xtraordinary-v0.2.6-dev33-usb-reply-local` string belongs to the retained crash report, while active boot 5 runtime trace has no version. Thus it rules out an app cache but proves neither running dev33 nor dev37; the dev37 hash proves image write only. Source audit found a dual-OTA table (`app0=0x10000`, `app1=0x650000`, `otadata=0xe000`) while the flasher unconditionally writes app0, leaving unselected app1 as an unproven candidate root cause. The bond remains present; no install, flash, reset, pairing, app-data, NVS, SD/book, setting, or serial-port action occurred. Resume with non-destructive boot-slot reading and a fresh BLE capabilities/status/revisioned-library/policy-ACK chain, not a flash retry.

## 2026-08-24 - Bounded device-deployment checkpoint

- Task: `01a033d7-8315-7c83-a1fd-646fa27f147c`. The entry/pre-deployment `-EnforceStageGate` audit returned `within-budget`: 9% used in the 10,080-minute signed-in window, 0-point task-local weekly growth, one model call with 26,813 input tokens (86.88% cached), no compactions, and no replay or weekly-reserve violation.
- Evidence-only deployment result: X3 USB/JTAG discovery found no present `VID_303A&PID_1001` device or associated serial port; the normal-user repository ADB daemon restart found no mDNS services and `adb devices -l` was empty. There was no current TLS endpoint to connect.
- Durable decision: do not invoke a raw/esptool or `-SkipAndroidRelease` flash, remembered ADB port, pairing/reset, timeout change, or compiler. No phone/X3 state changed; retain the exact dev49/dev37 artifact hashes in `HANDOFF.md` and resume only from a fresh physical X3 target plus current authenticated ADB endpoint.

## 2026-08-24 - Canonical build/deploy retry checkpoint

- Task: `01a033c5-0656-7f81-9b5d-48c1c63862fe`. Entry and protected-stage `-EnforceStageGate` audits returned `within-budget`; the signed-in weekly meter was 6% with 0-point task-local weekly growth. No weekly-reserve override was accepted.
- One permitted Android wrapper invocation from clean pushed `23acfd8` used only `-AllowDeferredUiReviewDebt`, passed provenance/notices/artwork/156-rule policy gates, and completed both debug artifact pipelines. One non-overlapping canonical firmware wrapper invocation then passed provenance, 25 firmware notice files, and the same policy gate, producing the source-bound dev37 artifact and release record.
- Device result: repository ADB restart and fresh mDNS found only the current TLS endpoint `192.168.1.61:42795`; two current-endpoint connection attempts failed and `adb devices -l` remained empty. No install, timeout change, bond change, flash, NVS/SD/book mutation, or handshake occurred. Stop this checkpoint at the authenticated-device boundary; do not retry a compiler or use remembered ADB ports.

## 2026-08-24 - Canonical Android build compile blocker

- Task: `01a033be-a4a5-71e3-a5d7-1d813a0828d0`. The pre-compiler re-audit remained `within-budget` at 5% of the 10,080-minute signed-in window, 1-point task-local growth, 46,582 replay median, and 51,530 latest input. No weekly override was used.
- One and only one `scripts/build-xtraordinary-app.ps1 -AllowDeferredUiReviewDebt` invocation ran. Provenance, licence/notice, artwork, and engineering policy gates passed; the explicit deferral covered the documented 46 UI-review findings only.
- Gradle failed before artifact creation for both CommunityDebug and PlayDebug because `CompanionViewModel.kt:1210` treats `finalSnapshot` as a revisioned library snapshot although its assignment at lines 1166-1175 is the `Unit` digest pass. No ADB/mDNS, install, firmware work, handshake, or phone/X3 mutation began. Do not retry this build in this milestone; require a source correction, commit/push, and a fresh authorized build task.

## 2026-08-24 - Fresh deployment task audit

- Historical correction: the `compaction-required` result (3% weekly use, 1-point task growth, 145,461 latest input) belongs to task `01a02a88-cd91-7361-a6e3-1941183387f4`; it remains valid history and is not attributed to this fresh owner.
- Task: `01a033be-a4a5-71e3-a5d7-1d813a0828d0`. Its bound `-EnforceStageGate` audit passed: 4% weekly use in the 10,080-minute window, 0-point task-local weekly growth, replay median 30,915, latest input 41,093, and recent input growth 10%. No weekly-reserve override was needed or accepted.
- Regression-impact decision: this deployment changes no power, synchronization, pairing, state-truth, USB, or legal behavior. It must preserve the interactive lease and matching-ACK contract, Pixel app data and timeout, Bluetooth bonds, X3 NVS/SD/books, and application-only firmware scope; legal status remains not release-cleared.
- Pre-build result: no compiler, APK, ADB/mDNS, install, firmware comparison/build/flash, artifact publication, or hardware handshake has started. After this three-document checkpoint is committed/pushed, one canonical Android build may proceed with the explicit deferred-UI-receipt flag only.

## 2026-08-22 - Final release-artifact gate checkpoint

- Task: `01a02a88-cd91-7361-a6e3-1941183387f4`.
- Source/evidence: pushed Android notice portability correction `586cbac59fde4ac22b69217f5eb6b8858dd3cdc6`; its focused verifier passed CRLF/LF equivalence and true-content mismatch rejection, with unchanged 147-module Community and 180-module Play graphs.
- Protected-stage result: one canonical Android wrapper attempt used the documented UI-debt and weekly-reserve flags, but its independent task audit rejected continuation before provenance, policy, Gradle, ADB, or hardware work: weekly meter 99%, task growth 12 points, median input 106,239, latest input 125,009, and recent input growth 182% to 124,470. The weekly override did not and must not waive those limits.
- Preservation result: no APK/artifact exists from this checkpoint; no artifact destination was contacted; no Android install, firmware build/flash, Pixel app-data or timeout change, Bluetooth-bond change, X3 NVS/SD write, or handshake occurred. Stop the path and require a fresh passing task audit before another protected stage.

## 2026-08-16 - Long-history reviewer incident

- Task: `019faeb2-432c-7801-bfcd-2383a59d6ecb`
- Signed-in plan: Plus, seven-day window (`10080` minutes), reset 2026-08-23 20:44 Europe/Rome, no reset credits.
- First audited interval: weekly meter moved from 0% to 53% between 18:44:34Z and 20:20:33Z.
- Top-level task evidence for that interval: 370 model-call token events; 51,908,900 input tokens; 51,003,520 cached input; 905,380 uncached input; 103,124 output; 29,803 reasoning output; 52,012,024 total.
- Primary cause: hundreds of rounds repeatedly carried roughly 138k-147k input tokens from this July-to-August task. Most input was cached, but it still accompanied each model round. Fifteen distinct design/review agents and 71 agent messages amplified the number of rounds. Full-history forks made the process especially wasteful.
- Accounting limit: per-agent totals were not summed because forked rollouts inherit history/counters and would double-count.
- Corrective workflow: repository-owned specialist prompts, `fork_turns: "none"`, compact evidence packets, one active reviewer by default, pre/post meter snapshots, and a stop threshold at 20% remaining.

## 2026-08-16 - Visual-language correction checkpoint

- Pre-review snapshot: 53% used.
- Post-review snapshot: 60% used; a seven-point increase, reported immediately.
- Work performed: one narrow Terra re-review, permanent visual-language course/policy work, and implementation preparation. No broad specialist wave.
- Decision: stop all optional review passes. Permit only one candidate build/render and one final read-only inspection of the changed Default/Quiet evidence.
- Additional enforcement: exact meter procedure, 30-minute checkpoints, five/ten-point escalation thresholds, delegation limits, build rerun limit, and mandatory ledger updates are now stored in `docs/codex-usage-workflow.md` and protected by the engineering policy gate.

## 2026-08-16 - Build and adaptive-evidence checkpoint

- Pre-build snapshot: 60% used.
- Post-build snapshot: 66% used; a six-point increase, reported and recorded immediately.
- Work performed: one build stopped on incorrect JUnit imports; one corrected Community/Play compile, unit-test, and screenshot build passed; compact evidence then exposed label/icon crowding; a measured-width policy correction and final Community/Play build passed.
- Cause: full dual-variant compiler, unit-test, and deterministic screenshot pipelines were run three times. The narrow reviewer was not repeated after the adaptive-only correction.
- Durable correction: compact width now removes the optional QR object glyph before squeezing the action label, while retaining the independent edge cue. The portable any-project and teaching-material delegation template was added to `docs/codex-usage-workflow.md` in the same commit.

## 2026-08-16 - Orphan-chevron correction checkpoint

- Pre-stage snapshot: 66% used.
- Post-stage snapshot: 74% used; an eight-point increase, reported immediately. Seven-day window remains `10080` minutes and resets at Unix `1787510643` (2026-08-23 20:44 Europe/Rome); Plus plan, no credits.
- Work performed: one source correction, one canonical Community/Play UI-evidence build, inspection of the two relevant renders, and one narrow read-only Terra re-review. The build passed in 1 minute 8 seconds.
- Decision: no further compiler run or reviewer wave in this task. Preserve the accepted Passes evidence and leave firmware, hardware-current, external-service, accessibility, and full-release acceptance explicitly open in `HANDOFF.md` and `docs/x3-takeover-tracker.md`.

## 2026-08-16 - Final-sync firmware build checkpoint

- Pre-build snapshot: 74% used.
- Post-build snapshot: 81% used; a seven-point increase, reported and recorded immediately. Seven-day window remains `10080` minutes and resets at Unix `1787510643` (2026-08-23 20:44 Europe/Rome); Plus plan, no credits.
- Work performed: one canonical X3 release build for `xtraordinary-v0.2.6-dev35-final-sync-local`; the 98-rule firmware-release policy gate passed, ESP32-C3 RV32IMC verification passed, and PlatformIO completed in 1 minute 10 seconds at 35.2% RAM and 83.0% flash.
- Artifact: `firmware/.pio/build/x3_companion_release/firmware.bin`, 5,455,216 bytes, SHA-256 `A8B157833668BD14A7022DECAC8B8D48C438DAAC2F04311B35A9A0B19AE0E9CC`.
- Decision: no duplicate compiler run. Continue only with the already-built artifact, guarded hardware flash, and bounded physical validation; do not dispatch optional reviewer waves.

## 2026-08-16 - Physical-acceptance stop checkpoint

- Previous checkpoint: 81% used after the final-sync firmware build.
- Current snapshot: 92% used; an eleven-point increase, reported immediately. Seven-day window remains `10080` minutes and resets at Unix `1787510643` (2026-08-23 20:44 Europe/Rome); Plus plan, no credits.
- Work performed: guarded application-only X3 flash, encrypted post-flash bootstrap, rapid Settings stress and ACK reconciliation, retained crash/runtime diagnostics, repeated reconnect attempts, and source-level isolation of the standby advertising path. No additional compiler or reviewer wave ran.
- Decision: stop all builds, optional research, and specialist work. Use the remaining task only to preserve exact physical evidence, diagnose with existing binaries and logs, and leave the repository and handoff safe. A new firmware or Android candidate must wait for a later usage window.

## 2026-08-20 - Dev36 deployment and pre-review checkpoint

- Start snapshot: 2% used after the weekly reset. Pre-review snapshot: 8% used; a six-point increase, reported and recorded immediately. Seven-day window remains `10080` minutes and resets at Unix `1787838919` (2026-08-27 15:55 Europe/Rome); Plus plan, no credits.
- Work performed: one canonical dev36 firmware build, one guarded application-only flash, post-flash encrypted bootstrap, reset-free USB-to-fast-radio acceptance, two clean reconnect cycles, one invalid one-shot-probe diagnosis, reconnect-harness/policy repair, and the correctly configured ten-minute deep-sleep observation. No Android compiler or UI reviewer ran in this interval.
- Cause: device deployment and physical acceptance required many short orchestration rounds plus one ten-minute hardware boundary; the single firmware compiler did not move the meter from its 2% post-build checkpoint.
- Durable correction: the reconnect verifier now refuses to confuse an idle foreground one-shot probe with durable pending work and requires either `-PersistentWorkAlreadyQueued` or `-WakeViaUsbPort`; rule 99 protects the contract. The next UI wave begins from this 8% snapshot and uses sequential, history-free Terra packets.

## 2026-08-20 - First Passes review probe checkpoint

- Start snapshot: 8% used. End snapshot: 15% used; a seven-point increase, reported and recorded before the evidence compiler. Seven-day window remains `10080` minutes and resets at Unix `1787838918` (2026-08-27 15:55 Europe/Rome); Plus plan, no credits.
- Work performed: three compact history-free read-only probes covered UX flow, hierarchy, and the deterministic layout detector. The wave stopped at the first actionable UX blocker instead of dispatching the remaining specialists. The primary agent then implemented the contextual Import flight action and hardened the shared course, UX reviewer prompt, Passes contract, and machine policy.
- Cause: the three review calls and implementation/reconciliation traffic accounted for the metered interval; no compiler ran in this interval.
- Durable correction: task-starting contextual header actions must now retain a recognizable control silhouette and coherent cue, rule 100 protects the Passes implementation, and UX receipts may cite only evidence types approved by `docs/ui-review-policy.json`. Remaining specialists run sequentially from a compact source-and-evidence packet.

## 2026-08-20 - Passes mandatory review-wave checkpoint

- Start snapshot: 16% used after the evidence build and canonical usage-reader validation. End snapshot: 21% used; a five-point increase, reported immediately. Seven-day window remains `10080` minutes and resets at Unix `1787838918` (2026-08-27 15:55 Europe/Rome); Plus plan, no credits.
- Work performed: sequential source-bound UX, hierarchy, layout/spacing, typography, color/contrast, and shape/affordance reviews against one settled source commit and one Community/Play evidence set. No additional compiler ran.
- Cause: the required independent specialist calls account for the metered interval. Each reviewer owned one role, inherited no long task history, and stopped after its verdict.
- Durable correction: only the two still-mandatory roles, motion/interaction and accessibility/adaptive, continue. Optional review loops remain stopped; any blocker returns to one affected reviewer rather than restarting the wave.

## 2026-08-20 - Weekly-budget and consolidation stop checkpoint

- Previous checkpoint: 21% used. Current snapshot: 29% used; an eight-point increase, reported immediately. This is a seven-day `10080`-minute allowance, resetting at Unix `1787838918` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed: one Passes candidate build correction path and source-bound accessibility, motion, shape, and UX re-reviews after a semantics-only lint fix. The remaining review cascade was stopped when the owner identified unresolved setup and appearance work on the same Android UI.
- Workflow incident: the canonical meter reader failed under Windows PowerShell 5.1 because `ProcessStartInfo.ArgumentList` was exposed as null. The reader now falls back to a correctly quoted `Arguments` string and returned the signed-in snapshot successfully.
- Durable correction: the workflow now explicitly treats `10080` minutes as weekly budget, requires that fact in usage reports, and requires known changes on the same UI surfaces to be consolidated before one mandatory specialist wave. No more Passes/setup/appearance reviewers or compiler runs occur until that implementation batch is settled.

## 2026-08-20 - Consolidated Android candidate pre-build checkpoint

- Previous checkpoint: 29% used. Pre-build snapshot: 36% used; a seven-point increase, reported immediately. Seven-day window remains `10080` minutes and resets at Unix `1787838919` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed: one primary-agent implementation batch covering measured setup action anchoring, independent scene-drag Light/Dark and Expressive/Minimal state, real Minimal shape/spacing/motion effects, direct legal/notices access, product-level license/trademark audit, and durable engineering guards. No reviewer wave or compiler ran in this interval.
- Cause: repeated long-context primary-agent implementation and documentation rounds are themselves material weekly usage even without delegation or compilation.
- Decision: permit one pushed `UiEvidenceCandidate` run for this settled batch. If it needs more than one evidence-based correction/rerun, stop and preserve the exact blocker instead of starting another compiler/reviewer loop.

## 2026-08-20 - Consolidated Android evidence checkpoint

- Previous checkpoint: 36% used. Post-build snapshot: 40% used; a four-point increase. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838919` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed: one pushed Community/Play unit-test and deterministic screenshot evidence build. The first wrapper invocation stopped before compilation because two new unit-test sources were not mapped to the Android UI surface; the mapping-only correction was pushed, and the bounded candidate then completed and generated both reference sets.
- Evidence decision: setup Library and Device actions now share the measured lower action zone. The same render exposed a separate Focus defect: a fixed 304 dp maximum left a large unassigned band on taller viewports. No Terra review wave starts on rejected evidence.
- Durable correction in progress: replace the fixed maximum with a reusable viewport-fill policy, protect it with pure geometry tests, and make unassigned first-viewport slack plus sibling action drift explicit blocking checks in the permanent layout course and policy.

## 2026-08-20 - Final adaptive evidence checkpoint

- Previous checkpoint: 40% used. Post-rerender snapshot: 43% used; a three-point increase. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838919` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed: one pushed, provenance-gated Community/Play evidence rerender for the single adaptive-layout correction. The 111-rule gate passed; both variants compiled, both unit-test suites passed, and both deterministic screenshot suites passed in 1 minute 11 seconds.
- Render decision: Setup Library and Device actions share the same lower action edge; Expressive and Minimal Focus now assign tall-screen slack to the timer surface and keep the action directly above navigation; the large-text Focus fixture remains unclipped.
- Budget decision: the consolidated Android implementation and evidence phase moved the weekly meter from 36% to 43%. Freeze these source and rendered references. Do not run another Android UI compiler before the final source-bound review records and canonical release build.

## 2026-08-20 - Final-review early-stop checkpoint

- Previous checkpoint: 43% used. Current snapshot: 48% used; a five-point increase, reported immediately. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838919` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed: exactly three independent history-free Terra roles reviewed the same frozen Android/Passes source and evidence: UX flow, motion/interaction, and accessibility/adaptive. UX and motion passed; accessibility blocked the generic Android surface because exclusive Settings choices lacked group/radio semantics, policy chips were 44 dp rather than 48 dp, and the Light/Dark pager lacked a non-gesture selection route.
- Decision: stop the remaining five mandatory roles. Correct the central Settings selection primitive and carousel semantics first; do not spend five more reviews on source that is already known to be unacceptable. After one corrected evidence set, rerun the eight-role wave once.

## 2026-08-20 - Consolidated accessibility and device-verifier checkpoint

- Previous checkpoint: 48% used. Current snapshot: 52% used; a four-point increase. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838918` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed: the primary agent corrected mutually exclusive Settings controls to expose one radio group, 48 dp targets, and non-gesture scene selection; strengthened the permanent viewport/action-zone and accessibility contracts; and isolated a reconnect-verifier false negative caused by a single-line USB trace acknowledgement regex. No reviewer or compiler ran.
- Budget decision: preserve the remaining 48% by running one pushed Community/Play evidence build for the settled Android source, then one eight-role final Terra wave only if that evidence is acceptable. Do not restart exploratory or preliminary reviewer loops.

## 2026-08-20 - Consolidated Android final-evidence checkpoint

- Pre-build snapshot: 52% used. Post-build snapshot: 53% used; a one-point increase. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838918` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed: one pushed-source `UiEvidenceCandidate` run. The 112-rule policy gate passed, Community and Play compiled, both unit-test suites passed, and both deterministic screenshot suites regenerated in 1 minute 8 seconds.
- Render decision: Settings retains the edge-peeking sea/astronaut selector and distinct Expressive/Minimal controls; policy selectors have 48 dp targets. Setup Library/Device actions share the lower action zone, and Focus assigns tall-screen slack to the timer surface. Freeze this evidence; do not run another Android compiler before final reviews.

## 2026-08-20 - UI-review overrun and product-scope stop

- Previous recorded checkpoint: 53% used. Current snapshot: 64% used; an eleven-point increase, reported immediately. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838918` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed: three final history-free Terra roles reviewed the frozen Android/Passes candidate; two blockers prompted one source-only viewport/evidence-policy correction through pushed commit `4a9997e`. No compiler, APK install, firmware build, flash, or physical-device acceptance ran in this interval.
- Scope failure: too much weekly budget was spent iterating visual evidence while release licensing, Play Billing, entitlement-backend, UMP, and AdMob work remained incomplete. The missing product integrations were already explicit in `HANDOFF.md`, `docs/monetization-entitlement-design.md`, and `docs/legal-release-audit.md` and should have been prioritized earlier.
- Durable correction: stop the Android UI review/rerender loop in this task. Do not dispatch more specialists or run another UI compiler. Separate repository-closeable license artifacts and integration boundaries from external Play/AdMob/backend/trademark/legal inputs, and never describe the existing entitlement reducer as an in-app purchase implementation.

## 2026-08-20 - Play integration evidence overrun

- Previous checkpoint: 64% used. Current snapshot: 76% used; a twelve-point increase, reported immediately. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838918` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Product work completed: the primary agent added flavor-scoped Billing 9.1.0, Mobile Ads 25.4.0 and UMP 4.0.0 development integrations, fail-closed non-debug purchase verification, explicit pending-purchase state, an SDK-free/ad-free Community runtime, Settings purchase/restore/privacy/source controls, legal disclosures, dependency notices, CrossPoint/CrossInk/XTEINK rights boundaries, tests, policy rules, tracker, and handoff updates.
- Verification performed: four pushed-source candidate runs compiled Community and Play, ran both unit suites, and generated deterministic evidence. The first established API correctness. The second exposed a missing dedicated purchase-card fixture. The third exposed that the fixture/component emitted sibling nodes into an unsuitable parent and that compact secondary actions produced a poor large-text result. The fourth passed after the central component and compact action policy were corrected.
- Waste diagnosis: the final two rerenders should not have been necessary. The component should have owned one root layout from the first implementation, and the initial evidence fixture should have modeled the real Settings parent before the first compiler. The primary agent failed to perform that source-level containment check.
- Budget decision: 24% remains. Preserve it. Run no optional UI loops and no additional candidate compiler. If mandatory source-bound reviewer receipts cannot be completed before the meter reaches 80% used, stop with the exact release-gate blocker instead of spending the weekly reserve. A final assembly/install build is permitted only after the gate has valid current receipts.

## 2026-08-20 - First mandatory receipt checkpoint

- Previous checkpoint: 76% used. Current snapshot: 79% used; a three-point increase. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838918` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Review result: the frozen, already-rendered source passed the hierarchy, layout/spacing/margins, and typography roles for both `android-ui` monetization Settings and the Passes rebind. No compiler, screenshot regeneration, device operation, or source redesign ran.
- Budget decision: use the three already-open Terra tasks for one compact mandatory role each, then re-read the meter. Do not create a broad review wave or run another candidate compiler. At 80% used, stop further reviewer dispatch and report the missing release receipts; preserve the remaining weekly reserve for canonical build and physical acceptance.

## 2026-08-20 - Mandatory review cutoff

- Previous checkpoint: 79% used. Current snapshot: 83% used; a four-point increase. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838918` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Review result: UX-flow found the missing explicit `Continue with free` exit; color/contrast found non-transaction text actions incorrectly using a 3.17:1 primary role; shape/affordance required a Quiet monetization fixture. The primary agent corrected all three in one source batch. The first evidence run stopped at a fixture-only enum compile error; the corrected run passed both variant compiles, both unit suites, and both screenshot update suites in 1 minute 6 seconds. Visual inspection then found the Quiet fixture host did not propagate `onBackground`, so the host—not the app card—required one final source-only correction.
- Budget decision: stop all further reviewer dispatch because the meter exceeded the 80% cutoff. Do not create replacement or optional reviewers in this weekly window. Finish the deterministic fixture correction locally, then preserve the release gate as blocked on fresh source-bound `motion-interaction` and `accessibility-adaptive` receipts plus re-binding the six earlier roles to the final evidence commit.

## 2026-08-20 - Final weekly-reserve stop

- Previous checkpoint: 83% used. Final snapshot: 87% used; a four-point increase. This is the seven-day `10080`-minute allowance, resetting at Unix `1787838918` (2026-08-27 15:55 Europe/Rome), Plus plan, no credits.
- Work performed without further agents: corrected and visually inspected the Quiet monetization host; froze and pushed the passing Community/Play unit and deterministic screenshot evidence; created a tracked 25-file firmware licence/NOTICE pack with a SHA-256 manifest; added a verifier that the engineering gate runs before compilers; and narrowed the legal audit to the genuinely external or still-unimplemented blockers.
- Device check: restarted repository ADB in the normal user context, queried `_adb-tls-connect._tcp` through ADB mDNS and DNS, reran `adb devices -l`, and inspected present Windows serial/USB devices. No wireless-debug endpoint, Pixel USB device, or X3 USB/JTAG port was present, so no install, flash, bond change, app-data change, NVS write, or SD mutation was attempted.
- Stop decision: preserve the remaining 13% weekly allowance. The next allowed reviewer wave is the complete final-source rebind after reset or explicit user override; only after all eight receipts pass may the canonical APK build and non-clearing phone install run.

## 2026-08-20 - Explicit legal-release continuation

- Previous checkpoint: 87% used. Current signed-in snapshot: 90% used; a three-point increase. This is the seven-day `10080`-minute allowance, resetting 2026-08-27 15:55 Europe/Rome, Plus plan, no credits.
- User override: continue the minimum repository-closeable legal-release work despite the normal twenty-percent reserve stop.
- Work performed without agents or compilers: resolved the exact Community/Play runtime graphs twice to close undeclared-POM coverage; generated the Android legal manifest; added Android/APK notice integrity gates, artwork integrity/provenance inventory, fail-closed release handling for the XTEINK OEM image, and source-bound firmware release records. The first legal-generation attempt stopped safely when it exposed six Community-only undeclared coordinates; the explicit override registry was completed before the successful rerun.
- Budget decision: no UI review waves, screenshot runs, APK compiler, firmware compiler, install, or flash in this slice. Continue only legal/source integrity checks and one commit/push checkpoint.

## 2026-08-20 - Legal-release overrun stop

- Previous checkpoint: 90% used. Final signed-in snapshot: 96% used; a six-point increase, reported immediately. This is the seven-day `10080`-minute allowance, resetting 2026-08-27 15:55 Europe/Rome, Plus plan, no credits.
- Work performed: completed, validated, committed, and pushed the repository-closeable legal slice at `4b3e1d25fd59e4ecd722b3adbc6ae40045a1da37`; then confirmed the live remote branch with the pushed-source verifier. No subagent, Android compiler, firmware compiler, screenshot renderer, device install, or flash ran.
- Cost diagnosis: the overrun came from continuing inside an exceptionally long accumulated task context, several source/document inspections, and repeated exact Community/Play dependency-resolution checks. The work was substantive, but this task was no longer token-efficient.
- Durable stop: make no further implementation, review, build, or device attempt in this weekly window. Start the next objective in a fresh task from `HANDOFF.md` and the tracker, using only the exact files for that objective. Preserve the remaining four percent for emergency handoff only.

## 2026-08-22 - Book-transfer incident source checkpoint

- Start snapshot: 9% used. Current snapshot: 23% used; a fourteen-point increase, reported immediately. This is the seven-day `10080`-minute allowance, resetting at Unix `1787999373`, Plus plan, no credits.
- Work performed: no subagent, compiler, screenshot renderer, APK install, firmware build, or flash. The primary agent used the live Pixel/X3 state and narrow source tracing to isolate terminal upload replay, cancellation races, premature library reconciliation, background ownership, undersized USB chunks, non-atomic same-name replacement, EPUB filtering/validation, and missing-cover lookup.
- Cost diagnosis: the active task still carries an exceptionally large multi-week history, and several broad source/document reads consumed the checkpoint despite no compiler or delegation. This is a ten-point stage overrun under `docs/codex-usage-workflow.md`.
- Durable correction: stop exploratory reads and freeze the scope to the settled incident diff. Run only one pushed-source Android evidence build, one firmware build, the single mandatory source-bound reviewer wave, one canonical Android assembly, and the physical acceptance matrix. No optional reviewer/rewrite loops; a failed step gets one evidence-based correction and one rerun only.

## 2026-08-22 - Book-transfer evidence and first review checkpoint

- Previous checkpoint: 30% used. Current snapshot: 35% used; a five-point increase, reported immediately. This is the seven-day `10080`-minute allowance, resetting at Unix `1787999373`, Plus plan, no credits.
- Work performed: the canonical dev37 firmware build passed at 35.3% RAM and 83.0% flash; the Android candidate unit/evidence run exposed a transient Gradle cache rename failure, then passed on the evidence-based retry; hierarchy, layout/spacing/margins, and typography passed for both `android-ui` and `passes` after the typography reviewer caught missing Read large-text reflow and fixtures.
- Durable correction: the Read header now stacks above 1.15x, book rows grow and preserve two lines for dynamic operational facts, transfer badges grow at 2x, and Community/Play 1.3x and 2x evidence is policy-mapped. Keep this frozen source, dispatch only the five remaining mandatory roles once, and do not run another candidate renderer.

## 2026-08-22 - Full rollout forensic audit and preventive hard gate

- Authoritative weekly slice in this task: 8% to 42% used in the seven-day `10080`-minute window resetting at Unix `1787999373`; increase 34 percentage points. The user's app UI reported approximately 50%, while the latest repository reader/top-level rollout meter available to this audit reported 42%; do not conceal either observation.
- Top-level current-week delta: 288 model calls; 48,303,532 input tokens, of which 47,429,120 were cached and 874,412 uncached; 115,278 output tokens; 30,026 reasoning-output tokens; 48,418,810 total tokens. Cache hit rate was 98.19%, but median input per call was 176,468, p90 was 223,304, and maximum was 243,989. Four context compactions occurred in this weekly slice.
- Rollout-lifetime evidence: 5,332 model calls, 779,569,624 input tokens, 763,087,232 cached input, 16,482,392 uncached input, 1,729,522 output, 510,792 reasoning output, and 781,299,146 total tokens across 48 compactions. Tool traffic was dominated by 3,775 `exec` and 1,033 `wait` calls. These are top-level rollout figures only; forked-agent rollouts were not summed.
- Root cause: output verbosity was negligible relative to input. The task repeatedly paid a 150k-240k-token accumulated prompt for tiny tool decisions. A 98% cache-hit rate reduced recomputation but did not prevent weekly allowance consumption. Four compactions did not make the task efficient; they permitted the same oversized task to continue.
- Preventive correction: `scripts/audit-codex-task-usage.ps1` now streams typed JSONL events without echoing message bodies and enforces task-entry/stage ceilings: 20 calls, 75k median input after three calls, 120k for any latest call, zero compactions, five weekly percentage points per task, and an 80% global reserve cutoff. `AGENTS.md` requires it at task entry, every ten root calls, and before broad investigation, review, compilation, or device deployment. Both canonical build wrappers fail closed on `handoff-required`. Crossing a ceiling triggers a durable continuity handoff and fresh task, not abandonment of the objective.
- Post-fix signed-in snapshot: 43% used, `10080` minutes, same reset `1787999373`, Plus plan, no credits. The Pixel was rediscovered through the normal-user ADB daemon and its temporary two-hour screen timeout was restored from `7200000` to `1800000` ms.

## 2026-08-22 - Quiet transfer evidence hard-stop checkpoint

- Start snapshot: 44% used. Stop snapshot: 47% used; a three-point increase in the seven-day `10080`-minute allowance resetting at Unix `1787999373`, Plus plan, no credits.
- Root rollout evidence: 23 top-level model calls; 1,895,226 input tokens, of which 1,791,616 were cached and 103,610 uncached; 12,254 output tokens; 4,771 reasoning-output tokens; 1,907,480 total tokens; 90,371 median input; 114,997 maximum input; zero compactions. The root violated the 20-call and 75,000-median ceilings.
- Work performed: `color-contrast` passed the Read transfer correction; `shape-affordance` found the corrected Stop geometry clean but required a current Quiet render. Commit `5ef87c0` added one shared Quiet transfer screenshot fixture, preserved only deliberate Read references, and was pushed. The mechanical Impeccable detector returned no findings.
- Gate incident: the explicit root audit reported `handoff-required`, but returned without terminating the composed shell. The canonical wrapper then selected the newest specialist rollout instead of the invoking root rollout and began the UI-evidence build. The build was cancelled after initial preprocessing and `:protocol:compileKotlin`, before screenshot generation. The worktree remained clean; no APK install, firmware flash, bond change, app-data change, NVS write, SD mutation, or temporary phone setting occurred.
- Durable correction: continue in a fresh task. First make `-EnforceStageGate` fail the process on `handoff-required` and bind wrappers to the invoking root rollout. Then run one pushed-source UI-evidence candidate, generate the missing Quiet Read PNG, complete only the required narrow reviews/receipts, and proceed to canonical build and physical acceptance.

## 2026-08-22 - Thread-bound, compaction-first audit correction

- Invoking bounded-agent ID: `01a02a3a-71aa-71e2-9368-d1bd69dbed0c`. The audit resolved only its exact rollout and reported a 51% signed-in seven-day meter with zero task-local percentage-point growth at the first correction checkpoint. The known old root ID `019faeb2-432c-7801-bfcd-2383a59d6ecb` resolved its own distinct rollout and retained the expected high replay/weekly-growth evidence; an unknown ID returned `not-applicable` rather than borrowing the newest specialist rollout.
- Policy correction: model-call count and compaction count are forensic/checkpoint fields, not hard stops. Protected-stage decisions now use post-compaction median/latest input, recent replay growth, task-local weekly growth, and the 80% weekly reserve. Replay is recalculated after the latest compaction so compaction can actually clear accumulated-context pressure.
- Compaction boundary: generated app-server schema confirms `thread/compact/start` accepts `threadId`. A separate Windows stdio app-server returned `thread not found` for the active desktop-owned agent, and Windows does not support the app-server daemon lifecycle. Resuming and mutating the live rollout concurrently from that isolated process is prohibited. Use the owning client’s `/compact` between turns; when that is unavailable during an active turn, assign the next bounded stage to a fresh `fork_turns: "none"` agent instead of requiring a user-created task.

## 2026-08-22 - Visible task continuity and milestone policy

- User-requested correction: whenever an automatic continuation or move creates or selects another user-visible Codex task, the visible coordinator must immediately open it in the Codex app, never leave the user on Subagents or return only a task identifier, and report product-centered milestones covering completed work, current phase, phone/X3 impact, and the next verifiable outcome. Internal `fork_turns: "none"` workers remain non-visible, and worker mechanics stay out of user status unless requested.
- Machine enforcement: added required-pattern rules `visible-codex-task-transitions-open-immediately`, `visible-coordinator-milestones-are-product-centered`, and `history-free-bounded-workers-remain-internal`; JSON parsing and focused matching passed for all three.
- Validation boundary: the full engineering gate retained 27 pre-existing missing or stale `passes`/`android-ui` UI-receipt failures. No new governance-policy violation appeared; those receipt blockers require separate UI-review work and were not changed in this governance slice.

## 2026-08-22 - Bounded book-transfer milestone ownership and reconciliation

- Start snapshot: 79% used in the signed-in seven-day `10080`-minute allowance, resetting at Unix `1787999373`; Plus plan, no credits. The fresh task-local audit was `warming-up` because it had fewer than two samples.
- Work performed: corrected the USB transfer reconciliation boundary so a commit ACK no longer marks a phone book as present on X3. Only a final revisioned BLE `LibraryPage` snapshot updates X3 library state; a USB transfer uses an immediately available BLE snapshot when present and otherwise remains conservatively unreconciled until a later snapshot. The transfer queue still clears only after the successful transaction commit.
- Governance correction: the usage workflow and machine-checkable policy now require one fresh `fork_turns: "none"` `gpt-5.6-terra` owner for each substantial milestone, including implementation, debugging, focused verification, acceptance evidence, a bounded end checkpoint, no mid-milestone replacement, no reviewer swarm, and coordinator weekly-budget review before another milestone.
- Validation boundary: the release policy's 27 existing stale or missing `passes`/`android-ui` receipts remain deferred by this milestone's user override. No receipt was cleared, regenerated, or represented as current.

## 2026-08-22 - Protected-stage reserve stop

- Start snapshot: 79% used. Stop snapshot: 80% used in the signed-in seven-day `10080`-minute allowance, resetting at Unix `1787999373`; Plus plan, no credits.
- Task-local audit: 12 model calls, 894,216 total tokens (798,720 cached input), 75,349 median input tokens, 102,152 maximum input tokens, zero compactions, and two percentage points of task-local weekly growth. It returned `weekly-budget-exhausted` because the signed-in weekly reserve cutoff is 80%, with replay median/trend signals also requiring a fresh bounded stage.
- Stop decision: no canonical compiler, deployment, ADB/mDNS device discovery, install, flash, screenshot regeneration, or reviewer work began. The pushed source checkpoint remains `e743bc8`; all phone/X3 state is preserved. Resume only in a fresh bounded milestone after the reserve resets or under an explicit user budget exception.

## 2026-08-22 - Explicit release-debt waiver for build and physical acceptance

- User authorization: continue this one fresh bounded build/deploy/physical-acceptance milestone despite the 80% weekly-reserve advisory and the pre-existing Terra review-receipt debt.
- Exact scope: only the canonical Android debug build may be invoked with the visible `-AllowDeferredUiReviewDebt` parameter, and only when every Release-gate violation is the `ui-changes-require-stable-terra-reviews` rule. The waiver does not clear, regenerate, or claim current UI receipts; it must print the complete deferred failure list and count. Firmware Release, default Android Release, source-push, notice/provenance, usage, and every other engineering policy remain fail-closed.
- Protected operations authorized after the waiver source and this ledger record are committed and pushed: canonical debug Android build, retained-data `adb install -r`, required guarded application-partition-only firmware flash if the pushed source requires it, and focused real Pixel/X3 acceptance using only safe EPUB fixtures. Bluetooth bonds, Pixel app data, X3 NVS, SD contents, and real books must be preserved.

## 2026-08-22 - Build/deploy replay stop and visible next-task contract

- The explicit build/deploy continuation reached 84% signed-in use with two points of task-local growth, but recent replay median grew 93% to 84,602 tokens. The documented weekly-reserve override correctly refused to waive that independent replay signal; no compiler, ADB discovery, install, flash, bond/data/NVS/SD write, or book mutation began.
- Pushed controls `c3c4cf8` and `c09c6a7` remain visible and non-default: the first defers only the documented 27-item UI-receipt rule while checking 148 other rules, and the second waives only the documented weekly reserve while replay/task-growth stay fail-closed.
- Durable continuation correction: before each milestone, audit regression impact against relevant power/sync/pairing/state/legal policy and measured hardware evidence. At checkpoint, normalize result/backlog, create the next substantial milestone as a new user-visible Codex task, immediately navigate/open it, and end the old coordinator without user intervention.

## 2026-08-22 - Firmware notice verifier portability correction

- Context: the fresh canonical Android build/deploy task reached a clean pushed candidate but the pre-compiler firmware-notice gate compared Windows CRLF working-tree bytes with a mixed LF/CRLF manifest, blocking the required build without a real licence-content change.
- Durable correction: `scripts/check-firmware-release-notices.ps1` now hashes canonical LF bytes only for its fixed text notice inventory while retaining inventory and content-mismatch failures. The reviewed manifest was regenerated from those canonical bytes; no tracked licence/NOTICE payload was edited.
- Focused evidence: `scripts/test-firmware-release-notices.ps1` passed both CRLF equivalence and real-content mismatch rejection, and the repository pack passed the corrected verifier. The engineering policy and AGENTS/workflow guidance now require this scoped portability invariant; no compiler, phone, or X3 mutation occurred in this correction stage.

## 2026-08-22 - Canonical build/deploy stage blocked after portability checkpoint

- Start snapshot: 89% used. Current snapshot: 94% used in the signed-in seven-day `10080`-minute allowance, resetting at Unix `1787999373`; a five-point task-local increase.
- Task-local evidence: 30 calls; 1,830,548 total tokens (1,738,240 cached input); median input 62,539; maximum 84,975; zero compactions. The recent three-sample input median grew 154% from 33,358 to 84,742 tokens.
- Stop decision: after pushing `fde20a9d1680081ee5616fba89f381e08a4f0f7a`, `audit-codex-task-usage.ps1 -EnforceStageGate -AllowDocumentedWeeklyReserveOverride` rejected the protected stage. The weekly exception cannot waive the independent replay/task-growth condition. No compiler, ADB/device discovery, installation, flash, bond/data/NVS/SD mutation, or physical acceptance began; resume only with a fresh history-free bounded task.

## 2026-08-22 - Android notice verifier portability correction

- Context: the authorized canonical Android build reached its pre-compiler legal-notice check, where the resolved Community/Play graphs remained semantically unchanged (147/180 rows) but the generator compared CRLF checkout bytes with an LF-oriented tracked manifest.
- Durable correction: the fixed text-only Android notice inventory now hashes canonical LF bytes for both resolved reports and its manifest. The generator retains graph and override validation; no APK, firmware image, or other binary payload is normalized. The reviewed manifest was regenerated without a dependency-coordinate or licence-metadata change.
- Focused evidence: `scripts/test-android-release-notices.ps1` passed CRLF equivalence and true-content mismatch rejection. The existing 27 deferred UI-review receipt findings remain release debt, and legal status remains **not release-cleared**. No Android compiler, installation, flash, bond/app-data change, NVS write, or SD mutation occurred in this correction stage.

## 2026-08-24 - Stage-aware audit and cost-routed agents

- Cause: the previous policy made replay and five-point task growth fail-closed before ordinary work and required a new Terra-owned visible task per milestone. That amplified the condition it measured by replaying policy/history, repeating entry gates, and paying coordinator handoffs before reaching a build.
- Durable correction: one visible coordinator continues related milestones; delegation is optional and limited to one history-free worker for sequential work. The audit is observational at entry and fail-closed only once before compiler/device work when the signed-in weekly meter reaches 95%. The 80% level, replay size/growth, and task-local five-point change remain reported advisories that suppress optional work but do not block source correction or create task chains.
- Model routing: Luna medium is the default bounded execution lane; Luna high handles bounded cross-file work; Luna xhigh requires a measured reasoning gap or costly bounded failure. Terra medium/high is reserved for cross-layer state, firmware/device safety, weakly tested unfamiliar code, and mandatory UI roles. Sol coordinates rather than running bulk execution.
- Evidence boundary: current official API model pages price Luna at one tenth of Terra per input, cached-input, and output token. Official and independent coding benchmarks show a much smaller capability gap and no consistent ordinary-task benefit from xhigh. API pricing is not claimed to equal signed-in Codex weekly-meter accounting; repository acceptance evidence remains the routing authority.
- Device boundary: this governance change ran no compiler, deployment discovery, install, flash, reset, pairing, app-data, timeout, NVS, SD/book, or BLE operation. Pixel and X3 state are unchanged.
## 2026-08-24 - Selected-slot Android/firmware protected-stage checkpoint

- Android outputs present from the canonical wrapper were recorded and both post-build notice verifiers passed: Community 85,609,521 bytes, SHA-256 `09234D1766650323894660CF1135CB5765B8DE753B5125D4D3171722A81820FF`; Play 90,183,253 bytes, SHA-256 `53685BC241A1084CCEA3AF68D40AE0B71926F937080FA41FE8A6EDA77C4BC416`.
- The ten cross-flavor screenshot drifts remain explicitly deferred per user instruction; no UI acceptance claim was made.
- Sequential firmware wrapper `xtraordinary-v0.2.6-dev38-selected-slot-local`, Jobs 2, stopped before PlatformIO at the FirmwareRelease policy gate: `weekly-reserve-override-is-ledger-bound-and-narrow`. No firmware artifact, deployment discovery, install, flash, live-slot read, BLE chain, or phone/X3 mutation occurred. Preserve app data, timeout `1800000`, bond, NVS, SD, and books; resolve the protected-stage policy blocker before retrying.

## 2026-08-24 - Worker supervision and recursive workflow correction

- Cause: the coordinator repeatedly checked a healthy worker and then treated `running` as sufficient proof of progress. This spent coordinator calls without product evidence and later missed a real stall: the worktree remained clean at `e4aaa58` and no build, deploy, flash, or device process was active.
- Durable correction: worker waits are capped at ten minutes and followed by exactly one compact check of real progress evidence. A liveness label alone is insufficient; a proven stall is interrupted and recovered, while genuine progress receives one new bounded wait without polling or nudges.
- Recursive correction: every acknowledged workflow mistake must update durable guidance plus a machine-checkable rule or focused fixture when practical in the same checkpoint. A chat promise is not remediation, and failing to document the mistake is itself governed by the same rule.
- Authority boundary: agents retain model routing, compaction, known toolchain/path recovery, evidence-based retry, build sequencing, and safe in-scope recovery. These execution decisions are not delegated to the user. No phone or X3 operation occurred while making this policy correction.

## 2026-08-24 - Pixel screen-timeout lifecycle

- User requirement: every Pixel-backed work phase must prevent the screen from timing out, but must not leave a changed personal setting behind.
- Live start state: repository ADB read `screen_off_timeout=1800000` ms and `stay_on_while_plugged_in=0` from Pixel `192.168.1.61:36221`. The work timeout was set and verified as `2147483647` ms; the prior 30-minute value is the required restore target for this phase.
- Durable correction: `scripts/manage-pixel-screen-timeout.ps1` snapshots the original timeout before disabling screen-off, preserves the first snapshot across repeated begins, restores the exact value at every completion/error checkpoint, and applies 30 minutes when state is unavailable or exact restoration fails. The engineering gate requires both the lifecycle policy and helper invariants.
## 2026-08-24 - Selected-slot firmware/install blocker

- Policy correction `e4aaa58` enabled the canonical firmware build: `xtraordinary-v0.2.6-dev38-selected-slot-local`, 5,456,368 bytes, SHA-256 `E0E8FA4E3347BB4533CAF34E8CCF7CC25A4306E5988A7CBA64204190A0F389FE`, source-bound release record present.
- Current mDNS Pixel endpoint `192.168.1.61:36221` resolved and guarded retained-data Community installation completed (`0.2.0-dev49`, APK SHA-256 `09234D1766650323894660CF1135CB5765B8DE753B5125D4D3171722A81820FF`). Fresh BLE bootstrap then crashed with `Invalid UTF-8 field length` in `PayloadCodec.decodeCapabilities` (`Payloads.kt:287`, `BluetoothCompanionClient.kt:895`). No X3 flash/live-slot read/BLE acceptance/reset or bond, app-data, timeout, NVS, SD, or book mutation occurred.

## 2026-08-24 - BLE decode containment and fresh-bootstrap checkpoint

- Pushed `77d58b0` after confirming the crash cause: payload decoder exceptions escaped `runCatching(...).onSuccess(...)`. The client now catches dispatch/decode failures, emits bounded type/id/size/hex-prefix diagnostics, and fails the link without process death. No speculative wire-format parser was added; source firmware history remains two-byte length-prefixed.
- Focused canonical Android tasks passed (protocol tests, both flavor unit tests/lint/assemble; screenshots intentionally omitted). Community APK `0.2.0-dev49`, 85,815,129 bytes, SHA-256 `3F9BD0A6233D91B888B5692A8B696858CEA7C931D73EA1D68CD57ADFB83DE139`; wrapper notices passed. Guarded retained-data install completed on `192.168.1.61:36221`.
- Fresh post-fix launch remained alive but yielded no fresh Capabilities notification, so no raw payload, firmware version, StatusChanged, revisioned LibraryPage, or policy ACK proof exists. PC X3 resolver found no `VID_303A:1001`, as expected while X3 is Pixel-hosted. No flash, slot read, reset, bond/app-data/timeout/NVS/SD/book mutation occurred. A bare PATH `adb` diagnostic was corrected by using repository-resolved toolchains/canonical resolver; do not repeat PATH guessing.

## 2026-08-24 - Observed Capabilities version-length correction and transport stop

- One bounded standby-discovery wait produced a live 66-byte Capabilities envelope. Rejection evidence prefix `020058333300787472616f7264696e61` proves current two-byte length-prefixed fields: model length 2 (`X3`), firmware version length 51 (`0x0033`). Pushed `7b0d855` raises only the Capabilities version limit to 64 bytes and adds the exact boundary test; protocol tests pass.
- Focused canonical Android build passed and retained-data install completed; Community SHA-256 `EEC00C70851B9AEE8A2A40F623ED6D4CFEED354BC9DF093119B4C830B09567B5`. Subsequent relaunch/device-card reconnect attempts saw only `connect requested` with no GATT/notification. No fresh runtime acceptance chain exists; no X3 flash, slot read, reset, bond/app-data/timeout/NVS/SD/book mutation occurred.

## 2026-08-24 - Pixel-host USB enumeration blocker

- Canonical target resolution retained Pixel `192.168.1.61:36221`. Repository-resolved `adb shell dumpsys usb` showed `host_connected=true`, `source_power=true`, `connected=false`, `configured=false`; no current host device or permission entry exists. Historical Espressif manufacturer/product records (`12346`/`4097`) are Aug 22 only, while the Aug 24 event log ends in USB removal.
- Guarded USB discovery (`UsbEspFlasher.refresh/findDevice`, VID_303A:1001) therefore had no present device to wake/query. No USB command, reset, flash, slot read, or state mutation occurred. Fresh BLE acceptance remains blocked; preserve bond/app data/timeout/NVS/SD/books.

## 2026-08-24 - Fresh BLE chain and OTA-proof blocker

- Fresh BLE matched X3 `7C:E8:B1:71:13:3E` at RSSI -41, opened GATT, discovered 3 services, negotiated MTU 256, subscribed notifications, and received Capabilities, two StatusChanged, four LibraryPage messages, and ACK traffic without a crash.
- Guarded diagnostics returned active `RUNTIME_TRACE_ACTIVE boot=13 reset=8 ...` but no `RUNTIME_TRACE_OTA`; `dev33` is retained crash-report content only. Selected-slot dev38 deployment remained blocked before mutation; no reset, slot write, flash, bond/app-data/timeout/NVS/SD/book mutation occurred.

## 2026-08-24 - Fresh version and deterministic OTA-proof blocker

- Pushed `9fb5c4a` adds bounded logging of accepted fresh Capabilities. Retained-data Community reinstall used APK `0.2.0-dev49`, 85,782,220 bytes, SHA-256 `3E6A6F98A4D9E9F33F7FBCDACFFFB13D22BCA4DC475298C8AC231347D744B90F`; fresh BLE reported `xtraordinary-v0.2.6-dev37-book-reconciliation-local`, followed by StatusChanged, revisioned LibraryPage, and policy ACK evidence. No crash, bond, or data loss.
- Source/history inspection proves OTA output was added in `de1fae5`; the live ACTIVE/PREVIOUS trace is short and the 4 KiB reader/filter would preserve OTA, so absence is an installed-runtime capability gap, not truncation/parser filtering. A safe retry then timed out reading the diagnostic command while Pixel USB briefly enumerated VID:PID `303A:1001`; current USB state is disconnected/unconfigured. Deployment remains fail-closed before reset/write; no flash or slot assumption.

## 2026-08-24 - Managed OTA safety decision

- Source inspection confirms the existing BLE `BEGIN_FIRMWARE`/chunk/commit/apply path is inactive-slot-safe: firmware stores and hashes the SD image, validates it, selects `esp_ota_get_next_update_partition(nullptr)`, and calls `ota_boot::switchTo`; it does not require an Android address or rewrite raw otadata. Dev37 Capabilities advertises firmware update support.
- The same source requires `MappedInputManager::Button::Confirm` physically pressed when `BEGIN_FIRMWARE` arrives. No unauthorized transfer was attempted. Minimum physical action is pressing/holding Confirm while launching the existing in-app install; then accept only fresh post-reboot Capabilities, StatusChanged, revisioned LibraryPage, and matching ACKs. Pixel timeout restoration was run at the boundary.

## 2026-08-24 - Managed dev38 attempt stopped before authorization

- Pixel Downloads received the canonical firmware; the app verified version `xtraordinary-v0.2.6-dev38-selected-slot-local` and SHA-256 `E0E8FA4E3347BB4533CAF34E8CCF7CC25A4306E5988A7CBA64204190A0F389FE`.
- The existing managed BLE install was prepared, but no `BEGIN_FIRMWARE` was sent: BLE had no advertisement and Pixel USB was `connected=false/configured=false`. Confirm was not consumed; no transfer/commit/apply/reboot or device-state mutation occurred.

## 2026-08-24 - Managed OTA retry transport boundary

- One bounded retry after navigation-button wake waited roughly 75 seconds. The app logged only `connect requested model=X3 phase=Disconnected`; no BLE scan/GATT/notifications or USB enumeration reached the install path. `BEGIN_FIRMWARE` was never sent, so Confirm timing was not exercised and no transfer/reboot/device mutation occurred.

## 2026-08-24 - Android BLE lifecycle diagnosis

- Repository ADB proved Bluetooth enabled, companion process alive, and `BLUETOOTH_SCAN`/`CONNECT` granted. Force-stop/relaunch retained data and started one fresh connect request; no scan callback/failure, GATT, notification, or USB event arrived through the bounded standby interval.
- `BluetoothCompanionClient.connect()` already performs idempotent disconnect/stopScan before each low-latency scan. No stale scan job/backoff/cancellation defect is evidenced; no speculative source fix, Bluetooth cycle, pairing reset, or OTA mutation was performed.

## 2026-08-24 - Durable diagnostic-order correction

- Coordinator correction: simultaneous BLE and USB absence must first be classified as X3 powered-off/sleep or physical transport absence; only after wake and reusable fast-lease evidence may Android scan/reconnect lifecycle be blamed. Added this required pattern to AGENTS, USB maintenance workflow, and `docs/engineering-policy.json`.
