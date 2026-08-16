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
