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

