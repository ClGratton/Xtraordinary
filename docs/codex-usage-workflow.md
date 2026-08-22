# Codex usage workflow

Status: mandatory for long-running implementation and specialist-review work in this repository.

Usage discipline is repository state, not chat memory. Any change to this procedure, its thresholds, or its delegation rules must update this document and append the dated ledger in `docs/codex-usage-ledger.md` in the same commit.

## Authoritative meter

Run `scripts/read-codex-usage.ps1` outside the filesystem sandbox in the normal signed-in user context. The script performs the initialized local Codex app-server handshake and reads `account/rateLimits/read`; do not recreate that handshake ad hoc. Record `usedPercent`, `windowDurationMins`, and `resetsAt`. Public OpenAI API billing, token prices, and API rate-limit pages are different systems and must not be presented as the signed-in Codex weekly meter. A sandbox-context result is invalid because it resolves a different Codex home and may have no signed-in account.

`windowDurationMins = 10080` is a seven-day allowance. Treat its percentage as weekly budget consumption, never as a short rolling window that can be casually spent within one task. State the window length whenever reporting the percentage.

For a forensic task audit, read only top-level `token_count` events from the current task rollout. Report deltas for input, cached input, uncached input, output, reasoning output, total tokens, and model-call count. Do not sum forked-agent rollout totals: forked rollouts inherit counters/history and can duplicate usage.

Run `scripts/audit-codex-task-usage.ps1` instead of broad text searches over rollout JSONL. It streams the file, counts only typed records, and reports cache effectiveness, per-call input distribution, compactions, tool-call concentration, task-local weekly growth, and lifetime totals without printing message bodies back into the model context.

## Hard context and call ceilings

The following are stop conditions, not advisory warnings:

- more than 20 top-level model calls in the current weekly slice of one task;
- median input above 75,000 tokens after three calls, or any single call above 120,000 input tokens;
- any context compaction in the current weekly slice;
- more than five percentage points of weekly allowance consumed by one task;
- the signed-in weekly meter reaching 80% used.

At any stop condition, do not dispatch another reviewer, compiler, renderer, install, flash, or exploratory search in the bloated task. Immediately update `HANDOFF.md`, the tracker, and the usage ledger, restore temporary device settings, then continue the same implementation from those durable artifacts in a fresh Codex task. The boundary is continuity work, not abandonment. A user may explicitly authorize additional budget, but the override and its exact scope must be recorded in the ledger before protected thresholds are changed.

Run `scripts/audit-codex-task-usage.ps1 -EnforceStageGate` at task entry, before any reviewer wave, before a broad source investigation, and before a compiler or device-deployment phase. Both canonical compiler wrappers execute the same gate before the pushed-source and engineering gates. This prevents an already-expensive task from entering another costly phase.

## Required checkpoints

Take and record a meter snapshot:

1. at the start of long-running UI/review work;
2. immediately before and after every reviewer or subagent wave;
3. after every compiler/build attempt;
4. every 30 minutes while a task remains active, if no other checkpoint occurred.

Also run the audit after every 10 root model calls. Prefer one composed read-only command that answers all related questions; a sequence of tiny shell or reviewer turns is a usage defect even when every individual call is cache-hit.

Report a change of five percentage points or more immediately. A stage that consumes ten percentage points pauses before another reviewer wave or build so the implementation owner can reconcile what remains.

When twenty percent or less remains, stop optional review loops, research, and parallel work. Continue only the minimum path required to leave source safe and documented unless the user explicitly asks to spend more.

## Delegation budget

- Use `fork_turns: "none"` and give each specialist a compact, source-bound packet.
- Never fork the full history of this long-running task.
- Use one active specialist by default. Add parallel specialists only when the user explicitly requests a full wave and the pre-wave meter permits it.
- Consolidate all known changes to the same UI surfaces before their mandatory role wave. Do not pay eight independent reviews for a narrow intermediate fix when another known layout, appearance, or copy change on those surfaces is still pending.
- Reuse one narrow reviewer for one re-review. Do not spawn a replacement swarm because a reviewer missed a defect.
- The implementation owner writes code. Reviewers remain read-only and return a verdict plus exact evidence.
- Stop after the required verdict; do not ask reviewers to restate other roles.
- Never keep completed agents merely as an excuse to start iterative review traffic. Reconcile their result once, close the wave, and start no re-review until all source corrections for that surface are batched.

## Build budget

- Batch source corrections before compiling.
- Run policy and static checks first.
- Use only the repository build wrappers.
- A failed build gets one evidence-based correction and one rerun. Repeated blind reruns are prohibited.
- Screenshot evidence is generated once per settled source candidate; inspect the smallest evidence set that proves the changed concern.

## Ledger rule

Append `docs/codex-usage-ledger.md` whenever:

- a checkpoint changes the weekly meter by at least five percentage points;
- a usage investigation produces token/call evidence;
- a threshold or workflow rule changes;
- a reviewer/build stage is stopped due to budget.

Each entry records date, task ID when available, start/end percentages, reset window, relevant token deltas, cause, and the durable correction. Do not store credentials, auth tokens, or unrelated account data.

## Portable template for any project

Use this sequence outside Xtraordinary as well:

1. Create a small durable brief: objective, audience, constraints, source-of-truth files, and definition of done.
2. Start a fresh task for each materially different objective. Link the brief instead of carrying an old chat.
3. Primary agent inspects and implements. Delegate only one bounded concern at a time with no inherited history.
4. Give a specialist the minimum packet: exact question, 2-5 inputs, read-only/edit ownership, output format, and stop condition.
5. Reconcile once. Batch corrections. Build or render once from the settled candidate.
6. Store successful commands, decisions, rubrics, and recurring checks in the project; future tasks read those artifacts.
7. Take meter snapshots at the checkpoints above and stop optional iteration when the budget threshold is reached.
8. If the audit reports `handoff-required`, treat a fresh task as mandatory rather than trying to compact and continue. Cache hits reduce recomputation cost but do not make a 100k-plus repeated prompt an efficient workflow.

For teaching material, default to one source-research pass, one audience/learning-objective outline, primary-agent drafting, and one final pedagogy/factual review. Do not run multiple vague rewrite agents. Ask the reviewer to check named outcomes such as prerequisite fit, misconception risk, worked-example correctness, cognitive load, and assessment alignment.
