# Codex usage workflow

Status: mandatory for long-running implementation and specialist-review work in this repository.

Usage discipline is repository state, not chat memory. Any change to this procedure, its thresholds, or its delegation rules must update this document and append the dated ledger in `docs/codex-usage-ledger.md` in the same commit.

## Canonical release-content portability

The fixed Android and firmware licence/NOTICE inventories are text-only, but Git may materialize their repository LF content as CRLF on Windows. `scripts/generate-android-release-notices.ps1` and `scripts/check-firmware-release-notices.ps1` therefore hash canonical LF-normalized bytes for these inventories only. They still reject missing/extra files and every non-line-ending content change. Do not reuse this primitive for binaries, APKs, firmware images, or arbitrary release assets. When changing either verifier, run its matching `scripts/test-*-release-notices.ps1`; each proves CRLF equivalence and that a real content mutation fails. Regenerate `release-notices/android/MANIFEST.sha256` only through the reviewed Android generator and `release-notices/firmware/MANIFEST.sha256` only through the firmware verifier's reviewed `-Update` path.

## Authoritative meter

Run `scripts/read-codex-usage.ps1` outside the filesystem sandbox in the normal signed-in user context. The script performs the initialized local Codex app-server handshake and reads `account/rateLimits/read`; do not recreate that handshake ad hoc. Record `usedPercent`, `windowDurationMins`, and `resetsAt`. Public OpenAI API billing, token prices, and API rate-limit pages are different systems and must not be presented as the signed-in Codex weekly meter. A sandbox-context result is invalid because it resolves a different Codex home and may have no signed-in account.

`windowDurationMins = 10080` is a seven-day allowance. Treat its percentage as weekly budget consumption, never as a short rolling window that can be casually spent within one task. State the window length whenever reporting the percentage.

## Windows deployment-target discovery

Use `scripts/resolve-xtraordinary-deployment-targets.ps1` as the reusable preflight for an Android install or guarded X3 flash. It first retains an already authenticated ADB device. Only when no device is connected and OpenScreen reports no current `_adb-tls-connect._tcp` service does it restart the normal-user repository daemon with `ADB_MDNS_OPENSCREEN=0`, query Windows Bonjour, and connect only the freshly discovered TLS endpoint. This changes neither pairing nor app data; never print or record a pairing code.

The same helper identifies X3 from present `VID_303A:1001` composite/interface PnP records before selecting its associated `COM` interface. A ports-only scan is insufficient: a present composite without a serial interface is a reconnect/Device Manager condition, not evidence that X3 is absent. Discovery is read-only and must not open the serial port, reset the device, alter NVS, Bluetooth bonds, app data, or SD/books.

For a forensic task audit, read only top-level `token_count` events from the current task rollout. Report deltas for input, cached input, uncached input, output, reasoning output, total tokens, and model-call count. Do not sum forked-agent rollout totals: forked rollouts inherit counters/history and can duplicate usage.

Run `scripts/audit-codex-task-usage.ps1` instead of broad text searches over rollout JSONL. It streams the file, counts only typed records, and reports cache effectiveness, per-call input distribution, compactions, tool-call concentration, task-local weekly growth, and lifetime totals without printing message bodies back into the model context.

## Replay, trend, and weekly ceilings

Model-call count is reported for forensics and checkpoint cadence only. It is never a hard stop. The stage gate makes decisions from these measured signals:

- post-compaction median input above 75,000 tokens after three samples;
- the latest post-compaction input above 120,000 tokens;
- a recent three-sample median above 75,000 tokens after growing more than 35% from the first three-sample median;
- more than five percentage points of weekly allowance consumed by one task;
- the signed-in weekly meter reaching the 80% warning threshold or the 95% protected-stage cutoff.

Replay and trend signals produce `compaction-recommended`; five-point task growth produces `task-budget-checkpoint`. They are optimization evidence, not stop conditions. Compact the same task when the owning client can do so, reduce the next packet, and continue a bounded source fix without creating another visible task. The signed-in weekly reserve is the only protected-stage stop: 80% is the warning threshold and 95% is the fail-closed compiler/device threshold unless the user has authorized the documented reserve override. This distinction prevents a measurement from stranding a nearly finished fix and paying for the same context again.

Manual compaction has a precise ownership boundary. Codex app-server schema exposes `thread/compact/start` with only `threadId`, and the owning interactive client can run `/compact` between turns. An agent turn has no direct compaction tool. On Windows there is no supported app-server daemon lifecycle, so a new stdio app-server is a separate in-memory owner: sending `thread/compact/start` for the active desktop thread returns `thread not found`. Resuming that live rollout into the separate process and compacting it concurrently is unsafe and prohibited. When the owning client cannot compact between turns, stay in the same coordinator and finish the smallest coherent source stage. A single internal `fork_turns: "none"` worker is optional only when its compact packet is cheaper than continuing; it must not trigger further handoffs.

Run `scripts/audit-codex-task-usage.ps1` once at task entry as a non-blocking observation. Run `scripts/audit-codex-task-usage.ps1 -EnforceStageGate` only before a compiler or device-deployment phase; canonical compiler wrappers provide that single protected-stage check. Do not rerun the audit before ordinary source inspection, editing, documentation, focused script tests, or a correction discovered by a failed pre-compiler gate. The audit binds to `CODEX_THREAD_ID`, falling back to `CODEX_SESSION_ID`, validates the rollout's `session_meta.payload.id`, and must never select the most recently modified rollout belonging to another root task or specialist. An explicit `-RolloutPath` is reserved for fixtures and forensics; pair it with `-ThreadId` when identity enforcement is required. Fewer than two samples return `warming-up`, not failure.

An exceptional weekly-reserve continuation requires direct user authorization recorded in the dated ledger before the protected stage. Only then may a canonical wrapper pass the visible `-AllowDocumentedWeeklyReserveOverride` flag. The audit accepts that flag only for the 95% protected-stage reserve violation and prints the active exception. Task-growth and replay signals remain visible advisories. This is not a default, environment, or hidden bypass.

## Required checkpoints

Take and record a meter snapshot:

1. at the start of long-running UI/review work;
2. once before and once after a required reviewer wave, not per reviewer;
3. after a compiler/build attempt;
4. every 30 minutes while a task remains active, if no other checkpoint occurred.

Do not audit by model-call count. Prefer one composed read-only command that answers all related questions; a sequence of tiny shell or reviewer turns is a usage defect even when every individual call is cache-hit. The 30-minute checkpoint is enough unless a protected stage or a five-point meter change occurs first.

Report a change of five percentage points or more immediately. A stage that consumes ten percentage points pauses before another reviewer wave or build so the implementation owner can reconcile what remains.

When twenty percent or less remains, stop optional review loops, research, and parallel work. Continue only the minimum path required to leave source safe and documented unless the user explicitly asks to spend more.

## Model routing and delegation budget

Choose by cost per accepted result, not by the largest reasoning label:

- Use `gpt-5.6-luna` medium for bounded repository discovery, log parsing, documentation, mechanical edits, focused test repair, and build/deployment orchestration.
- Use Luna high for a bounded multi-file implementation or diagnosis. Escalate that same bounded task to Luna xhigh only after medium/high exposes a concrete reasoning gap or when failure is costly and external acceptance checks exist. Xhigh is not a default.
- Use `gpt-5.6-terra` medium/high for cross-layer synchronization or state-machine changes, firmware/device safety, unfamiliar weakly tested code, and the mandatory UI specialist contracts. Do not use Terra for command running, status collection, or repeating an inspection already completed by the owner.
- Sol coordinates scope, resolves contradictions, and handles the rare architecture decision that cannot be reduced to a bounded packet. It does not perform bulk shell, build, or reviewer work.
- Re-evaluate this routing with repository acceptance data rather than brand assumptions. API prices and public benchmarks inform the prior but do not prove Codex subscription-meter cost.

### Evidence basis, checked 2026-08-24

The current OpenAI model catalog prices Luna at $0.20 input / $0.02 cached input / $1.20 output per million API tokens and Terra at $2.00 / $0.20 / $12.00: Luna is one tenth of Terra's API token price. This is strong routing evidence but is not a claim that the signed-in Codex weekly meter applies the same multiplier.

OpenAI's published coding evaluations show the quality gap is usually much smaller than the API price gap: Terra/Luna score 77.4/74.6 on the Artificial Analysis Coding Agent Index, 63.4%/62.7% on SWE-Bench Pro, 69.6%/67.2% on DeepSWE, and 87.4%/84.7% on Terminal-Bench 2.1. Terra retains material advantages in security, self-improvement, and some long-context evaluations, which is why it remains the escalation route for firmware safety, cross-layer state, and weakly tested unfamiliar code.

Independent Codex evidence does not justify Luna xhigh as the default. NixBench's replicated 29-task corpus reports overlapping Luna and Terra family results; its fixed xhigh baseline was 19/29 for both, while Luna high averaged 22.6 tasks in 44.4 seconds and Luna xhigh 22.4 in 53.9 seconds. A separate 294-run Codex study found medium, high, xhigh, and max all accepted 42/42 valid matched tasks while median elapsed time and input tokens increased with effort. Therefore start Luna at medium, measure acceptance externally, and escalate only on an observed gap.

Sources: [OpenAI Luna model](https://developers.openai.com/api/docs/models/gpt-5.6-luna), [OpenAI Terra model](https://developers.openai.com/api/docs/models/gpt-5.6-terra), [OpenAI GPT-5.6 evaluations](https://openai.com/index/gpt-5-6/), [OpenAI model guidance](https://developers.openai.com/api/docs/guides/latest-model), [Artificial Analysis comparison](https://artificialanalysis.ai/models/comparisons/gpt-5-6-luna-xhigh-vs-gpt-5-6-terra), [NixBench](https://nixbench.com/results.html), and [294-run Codex study](https://instavar.com/research/agents/gpt-5-6-codex-models-reasoning-levels-benchmark-2026).

- Use `fork_turns: "none"` and give each specialist a compact, source-bound packet.
- Never fork the full history of this long-running task.
- Use one active worker by default. Add parallel specialists only for tasks with no shared files or sequential dependency, or when a mandatory UI contract requires distinct roles and the pre-wave meter permits it.
- Consolidate all known changes to the same UI surfaces before their mandatory role wave. Do not pay eight independent reviews for a narrow intermediate fix when another known layout, appearance, or copy change on those surfaces is still pending.
- Reuse one narrow reviewer for one re-review. Do not spawn a replacement swarm because a reviewer missed a defect.
- The implementation owner writes code. Reviewers remain read-only and return a verdict plus exact evidence.
- Stop after the required verdict; do not ask reviewers to restate other roles.
- Never keep completed agents merely as an excuse to start iterative review traffic. Reconcile their result once, close the wave, and start no re-review until all source corrections for that surface are batched.

## Durable bounded-milestone ownership

Every milestone task must read `TODO.md` first and treat it as the authoritative actionable backlog. It then reads only the directly relevant current `HANDOFF.md` section and the policy or evidence files linked from that section. Older unchecked tracker entries are incident history unless represented in `TODO.md`; remembered chat scope is not an entrypoint. Update `TODO.md` status at the milestone checkpoint.

The visible coordinator remains the owner across related milestones. It may assign one compact `fork_turns: "none"` worker using the model-routing table above, but a worker is not mandatory. The same owner takes a change through implementation, debugging, focused tests, and acceptance evidence from its bounded start through its checkpointed end.

Do not replace the execution owner mid-milestone and do not create a reviewer swarm for the milestone. The coordinator does not duplicate a worker's source inspection: it reconciles one concise result, publishes product/device/next-outcome status, and checks the remaining weekly budget before considering another milestone.

At the milestone end, the owner checkpoints code, evidence, handoff, tracker, and usage ledger. Any internal worker then terminates; the visible coordinator remains available for the next related milestone. This is bounded milestone ownership, not persistent whole-project ownership or unbounded work assignment.

Before milestone implementation begins, the execution owner runs a bounded regression-impact audit against the existing power, synchronization, pairing, state-truth, and legal policies plus measured hardware evidence relevant to the change. Existing measured behavior is a constraint. If a proposed fix conflicts with it, evaluate compatibility explicitly and document the evidence-backed decision before editing; never silently regress a policy or replace measured behavior with an assumption.

At the milestone checkpoint, update `TODO.md` status and normalize the completed result and remaining authoritative backlog in the directly relevant current `HANDOFF.md` section. Continue a related next milestone in the same visible coordinator. Create a new user-visible Codex task only when the user explicitly asks or the objective is genuinely unrelated; if a transition occurs, immediately navigate to and open it in the app. Never create a chain of tasks as a reaction to replay size.

## User-visible task continuity and status

A user-requested continuation or move may create or select another user-visible Codex task. When it does, the visible coordinator must immediately navigate to and open that task in the Codex app with the available task-navigation tool. Never leave the user on the Subagents page, and never treat merely providing a task name or ID as a handoff. The user must land in the task where visible coordination continues.

At each milestone, the visible coordinator publishes a plain-language product status containing all four facts: completed product work, the current phase, whether the phone or X3 changed, and the next verifiable outcome. Keep subagent and worker implementation details out of user-facing status unless the user asks for them.

Fresh bounded workers created with `fork_turns: "none"` remain internal execution contexts. They do not create user-visible tasks and do not change which task the user sees.

## Worker supervision and durable self-correction

Every worker wait is bounded to at most ten minutes. On timeout, the coordinator performs exactly one compact progress health check. That check must inspect real progress evidence: a new commit or working-tree change, an active build/deploy/device process, or a concrete worker checkpoint. Agent status `running` by itself is not evidence of progress. If the check proves a stall, interrupt the worker and recover the bounded task; otherwise start one new bounded wait. Do not repeatedly poll, send status nudges, or spend coordinator calls without a new event.

An acknowledged workflow mistake is a defect in the durable process. Correct it in the same checkpoint by updating this workflow or the applicable repository guidance, adding a machine-checkable policy rule or focused fixture when practical, and recording the cause and correction in `docs/codex-usage-ledger.md`. A chat apology or promise is not a correction. This rule is recursive: failing to document an acknowledged workflow mistake is itself a workflow mistake and triggers the same durable correction.

Execution management belongs to the agents. Model selection and escalation, context compaction, known path/toolchain recovery, evidence-based retry, build sequencing, and safe in-scope recovery must not be delegated to the user. User input is reserved for unauthorized destructive action, unresolved irreversible target ambiguity, external credentials or coordination, meaningful scope expansion, or a genuinely product-changing choice.

Pixel-backed work uses `scripts/manage-pixel-screen-timeout.ps1`. `-Action Begin` must run before the phase starts: it reads and durably snapshots the current `screen_off_timeout`, then disables automatic screen-off without replacing an existing snapshot. `-Action Restore` must run at every normal completion, stop, or error checkpoint and restore the exact saved timeout. If the snapshot is unavailable or exact restoration fails, the script applies a 30-minute (`1800000` ms) fallback. The coordinator must verify the resulting setting; an unbounded timeout may never be left behind after work ends.

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
2. Keep one coordinator for related objectives. Start a fresh visible task only when the user requests it or the objective is genuinely unrelated.
3. Primary agent inspects and implements. Delegate only when the packet is cheaper than continuing, one bounded concern at a time with no inherited history.
4. Give a specialist the minimum packet: exact question, 2-5 inputs, read-only/edit ownership, output format, and stop condition.
5. Reconcile once. Batch corrections. Build or render once from the settled candidate.
6. Store successful commands, decisions, rubrics, and recurring checks in the project; future tasks read those artifacts.
7. Take meter snapshots at the checkpoints above and stop optional iteration when the budget threshold is reached.
8. If the audit recommends compaction, compact through the owning client when available; otherwise finish the smallest coherent stage in the same coordinator. Never create an agent chain to escape context. At the protected weekly cutoff, stop optional work and preserve enough reserve to checkpoint or complete the explicitly authorized final stage.

For teaching material, default to one source-research pass, one audience/learning-objective outline, primary-agent drafting, and one final pedagogy/factual review. Do not run multiple vague rewrite agents. Ask the reviewer to check named outcomes such as prerequisite fit, misconception risk, worked-example correctness, cognitive load, and assessment alignment.
