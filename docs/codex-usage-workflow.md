# Codex usage workflow

Status: mandatory for long-running implementation and specialist-review work in this repository.

Usage discipline is repository state, not chat memory. Any change to this procedure, its thresholds, or its delegation rules must update this document and append the dated ledger in `docs/codex-usage-ledger.md` in the same commit.

## Authoritative meter

Read the signed-in Codex plan meter through the local Codex app-server method `account/rateLimits/read`. Record `usedPercent`, `windowDurationMins`, and `resetsAt`. Public OpenAI API billing, token prices, and API rate-limit pages are different systems and must not be presented as the signed-in Codex weekly meter.

For a forensic task audit, read only top-level `token_count` events from the current task rollout. Report deltas for input, cached input, uncached input, output, reasoning output, total tokens, and model-call count. Do not sum forked-agent rollout totals: forked rollouts inherit counters/history and can duplicate usage.

## Required checkpoints

Take and record a meter snapshot:

1. at the start of long-running UI/review work;
2. immediately before and after every reviewer or subagent wave;
3. after every compiler/build attempt;
4. every 30 minutes while a task remains active, if no other checkpoint occurred.

Report a change of five percentage points or more immediately. A stage that consumes ten percentage points pauses before another reviewer wave or build so the implementation owner can reconcile what remains.

When twenty percent or less remains, stop optional review loops, research, and parallel work. Continue only the minimum path required to leave source safe and documented unless the user explicitly asks to spend more.

## Delegation budget

- Use `fork_turns: "none"` and give each specialist a compact, source-bound packet.
- Never fork the full history of this long-running task.
- Use one active specialist by default. Add parallel specialists only when the user explicitly requests a full wave and the pre-wave meter permits it.
- Reuse one narrow reviewer for one re-review. Do not spawn a replacement swarm because a reviewer missed a defect.
- The implementation owner writes code. Reviewers remain read-only and return a verdict plus exact evidence.
- Stop after the required verdict; do not ask reviewers to restate other roles.

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

For teaching material, default to one source-research pass, one audience/learning-objective outline, primary-agent drafting, and one final pedagogy/factual review. Do not run multiple vague rewrite agents. Ask the reviewer to check named outcomes such as prerequisite fit, misconception risk, worked-example correctness, cognitive load, and assessment alignment.
