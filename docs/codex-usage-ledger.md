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
