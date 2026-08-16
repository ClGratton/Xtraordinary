# Stable UI specialist review contract

Status: mandatory for every Android UI change and every X3 display-layout change.

The reviewer roles are repository-owned. They are not remembered chat personas and they are not optional advice. A UI candidate must be reviewed by separate read-only Terra agents using the prompt files under `docs/ui-reviewers/`. Their receipts are bound to the latest commit that changed the registered surface and to SHA-256 hashes of every source file classified to that surface. Changing reviewed source makes the receipt stale and blocks the engineering policy gate. A registered Android UI/resource or X3 display path with no unique surface owner is a gate failure, not an invitation to choose a convenient reviewer scope.

## Required roles

1. `ux-flow`: task path, state truth, action ownership, useful copy, recovery, and destructive behavior.
2. `hierarchy`: first-glance order, salience, information utility, and removal of non-operational provenance.
3. `layout-spacing-margins`: geometry, alignment, rhythm, responsive fit, and stable bounds.
4. `typography`: semantic roles, wrapping, reflow, density, long values, and large text.
5. `color-contrast`: role pairs, contrast, status semantics, grayscale, and primary-action exclusivity.
6. `shape-affordance`: containment, silhouette, touch targets, recognisable controls, and action ownership.
7. `motion-interaction`: continuity, gesture ownership, haptics, state transitions, and reduced motion.
8. `accessibility-adaptive`: semantics, traversal, targets, narrow/compact layouts, and 1.3x/2.0x text.

X3 display changes additionally require `eink-scanner`: binary hierarchy, safe regions, mapped buttons, ghosting refresh, quiet zones, and physical scanning evidence.

One agent may not sign two required roles for the same candidate. The implementation owner may not sign a reviewer role. Reviews remain narrow: each specialist reads shared product truth but reports only its assigned concern.

## Required sequence

1. Commit and push the complete source candidate without accepting new screenshot baselines.
2. Generate candidate references only through `scripts/build-xtraordinary-app.ps1 -UiEvidenceCandidate`. It runs the exact Community/Play unit-test and deterministic screenshot-update task set. This mode still requires pushed source and the static policy contract; it cannot run lint, assembly, or release validation and it cannot satisfy review receipts.
3. Run every applicable stable reviewer prompt against that exact candidate and deterministic evidence.
4. Resolve every blocking finding in source. Any source correction expires prior receipts and requires the affected reviews again.
5. Record pass receipts in the surface review record with concrete check IDs and existing evidence paths.
6. Commit and push the receipts and deliberately reviewed screenshot references.
7. Run the canonical wrapper. `scripts/check-engineering-policies.ps1` rejects missing roles, reused reviewers, stale source commits, changed hashes, unresolved verdicts, and missing evidence.

A successful build or screenshot comparison does not sign a visual judgement. The receipt records which specialist made the judgement and which machine evidence supports it.

## Receipt schema and identity boundary

Each surface record contains `sourceCommit`, `implementationOwnerId`, `reviewedFiles` as a complete path-to-SHA-256 map, an explicit empty `findings` array, and one receipt for every required role. A receipt contains a distinct `reviewerId`, a distinct `reviewerTaskId`, `agentType: terra_medium`, `verdict: pass`, the role's exact `checks`, and non-empty `checkEvidence` for every check. Each evidence item is a repository-relative **leaf file** plus its SHA-256; a directory, a generic screenshot claim, or copied check IDs are not evidence.

The local gate can prove coverage, freshness, file identity, and that declared identities are distinct. It cannot prove that a string in JSON was produced by an independent Terra agent. Protected CI with authenticated reviewer/task identities or protected CODEOWNERS approval is required to make identity independence tamper-resistant. Do not describe a local receipt alone as cryptographic or human identity proof.

## Content utility rule

Operational surfaces show information only when it changes an immediate action, trust, freshness, or recovery decision. Import provenance such as `Imported from Google Wallet` belongs in import history/details, not on the boarding-pass face. Live provider and freshness may remain when they establish whether the displayed operational data is current, stale, or unavailable.

## Bounded delegation

Use one narrow task per specialist, reuse shared evidence paths, and stop after a verdict plus actionable checks. Do not ask every reviewer to rediscover the full repository or restate other roles. Reconcile once after the independent reports.
