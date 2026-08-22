# Codex usage workflow

Status: mandatory for long-running implementation and specialist-review work in this repository.

Usage discipline is repository state, not chat memory. Any change to this procedure, its thresholds, or its delegation rules must update this document and append the dated ledger in `docs/codex-usage-ledger.md` in the same commit.

## Authoritative meter

Run `scripts/read-codex-usage.ps1` outside the filesystem sandbox in the normal signed-in user context. The script performs the initialized local Codex app-server handshake and reads `account/rateLimits/read`; do not recreate that handshake ad hoc. Record `usedPercent`, `windowDurationMins`, and `resetsAt`. Public OpenAI API billing, token prices, and API rate-limit pages are different systems and must not be presented as the signed-in Codex weekly meter. A sandbox-context result is invalid because it resolves a different Codex home and may have no signed-in account.

`windowDurationMins = 10080` is a seven-day allowance. Treat its percentage as weekly budget consumption, never as a short rolling window that can be casually spent within one task. State the window length whenever reporting the percentage.

For a forensic task audit, read only top-level `token_count` events from the current task rollout. Report deltas for input, cached input, uncached input, output, reasoning output, total tokens, and model-call count. Do not sum forked-agent rollout totals: forked rollouts inherit counters/history and can duplicate usage.

Run `scripts/audit-codex-task-usage.ps1` instead of broad text searches over rollout JSONL. It streams the file, counts only typed records, and reports cache effectiveness, per-call input distribution, compactions, tool-call concentration, task-local weekly growth, and lifetime totals without printing message bodies back into the model context.

## Replay, trend, and weekly ceilings

Model-call count is reported for forensics and checkpoint cadence only. It is never a hard stop. The stage gate makes decisions from these measured signals:

- post-compaction median input above 75,000 tokens after three samples;
- the latest post-compaction input above 120,000 tokens;
- a recent three-sample median above 75,000 tokens after growing more than 35% from the first three-sample median;
- more than five percentage points of weekly allowance consumed by one task;
- the signed-in weekly meter reaching 80% used.

Replay and trend signals produce `compaction-required`. Compact the same task first, then calculate replay from token samples after the latest compaction; compaction count itself is not a violation. Weekly-growth or reserve signals produce `weekly-budget-exhausted`. In either case, the invoking task must not enter another protected stage until its signal is cleared or the minimum remaining stage is assigned to a fresh history-free agent whose own audit passes. Do not require the user to create a replacement task.

Manual compaction has a precise ownership boundary. Codex app-server schema exposes `thread/compact/start` with only `threadId`, and the owning interactive client can run `/compact` between turns. An agent turn has no direct compaction tool. On Windows there is no supported app-server daemon lifecycle, so a new stdio app-server is a separate in-memory owner: sending `thread/compact/start` for the active desktop thread returns `thread not found`. Resuming that live rollout into the separate process and compacting it concurrently is unsafe and prohibited. When the owning client cannot compact between turns, use a fresh `fork_turns: "none"` agent with a compact source-bound packet for the next bounded stage.

Run `scripts/audit-codex-task-usage.ps1 -EnforceStageGate` at task entry, before any reviewer wave, before a broad source investigation, and before a compiler or device-deployment phase. The audit binds to `CODEX_THREAD_ID`, falling back to `CODEX_SESSION_ID`, validates the rollout's `session_meta.payload.id`, and must never select the most recently modified rollout belonging to another root task or specialist. An explicit `-RolloutPath` is reserved for fixtures and forensics; pair it with `-ThreadId` when identity enforcement is required. Fewer than two samples return `warming-up`, not failure. Both canonical compiler wrappers execute the same gate before the pushed-source and engineering gates.

An exceptional weekly-reserve continuation requires direct user authorization recorded in the dated ledger before the protected stage. Only then may a canonical wrapper pass the visible `-AllowDocumentedWeeklyReserveOverride` flag. The audit accepts that flag only for the current weekly-meter reserve violation; task-growth and replay signals remain fail-closed, and it prints the active exception. This is not a default, environment, or hidden bypass.

## Required checkpoints

Take and record a meter snapshot:

1. at the start of long-running UI/review work;
2. immediately before and after every reviewer or subagent wave;
3. after every compiler/build attempt;
4. every 30 minutes while a task remains active, if no other checkpoint occurred.

Also run the audit after every 25 root model calls as a checkpoint, not a stop. Prefer one composed read-only command that answers all related questions; a sequence of tiny shell or reviewer turns is a usage defect even when every individual call is cache-hit.

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

## Durable bounded-milestone ownership

Each substantial milestone has one fresh `fork_turns: "none"` `gpt-5.6-terra` execution owner. That owner owns the milestone's implementation, debugging, focused tests, and acceptance evidence from its bounded start through its checkpointed end.

Do not replace that execution owner mid-milestone and do not create a reviewer swarm for the milestone. The coordinator does not duplicate the owner's source inspection: it reconciles one concise result, publishes product/device/next-outcome status, and checks the remaining weekly budget before considering another milestone.

At the milestone end, the owner checkpoints code, evidence, handoff, tracker, and usage ledger, then terminates. This is bounded milestone ownership, not persistent whole-project ownership or unbounded work assignment.

Before milestone implementation begins, the execution owner runs a bounded regression-impact audit against the existing power, synchronization, pairing, state-truth, and legal policies plus measured hardware evidence relevant to the change. Existing measured behavior is a constraint. If a proposed fix conflicts with it, evaluate compatibility explicitly and document the evidence-backed decision before editing; never silently regress a policy or replace measured behavior with an assumption.

At the milestone checkpoint, normalize the completed result and remaining authoritative backlog in `HANDOFF.md` and the tracker. When another substantial milestone remains, automatically create a new user-visible Codex task for that milestone, immediately navigate to and open it in the app, and end the old coordinator. Do not keep one coordinator alive across milestones and do not require user intervention for the handoff.

## User-visible task continuity and status

An automatic continuation or move may create or select another user-visible Codex task. When it does, the visible coordinator must immediately navigate to and open that task in the Codex app with the available task-navigation tool. Never leave the user on the Subagents page, and never treat merely providing a task name or ID as a handoff. The user must land in the task where visible coordination continues.

At each milestone, the visible coordinator publishes a plain-language product status containing all four facts: completed product work, the current phase, whether the phone or X3 changed, and the next verifiable outcome. Keep subagent and worker implementation details out of user-facing status unless the user asks for them.

Fresh bounded workers created with `fork_turns: "none"` remain internal execution contexts. They do not create user-visible tasks and do not change which task the user sees.

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
8. If the audit reports `compaction-required`, compact through the owning client between turns. If that path is unavailable during an active agent turn, give the next bounded stage to a fresh `fork_turns: "none"` agent. If it reports `weekly-budget-exhausted`, stop optional work and preserve the weekly reserve.

For teaching material, default to one source-research pass, one audience/learning-objective outline, primary-agent drafting, and one final pedagogy/factual review. Do not run multiple vague rewrite agents. Ask the reviewer to check named outcomes such as prerequisite fit, misconception risk, worked-example correctness, cognitive load, and assessment alignment.
