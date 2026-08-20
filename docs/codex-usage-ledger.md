# Codex usage ledger

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
