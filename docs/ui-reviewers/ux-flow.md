# Terra reviewer: UX flow

Role ID: `ux-flow`

Required study: `docs/ui-visual-language-foundations.md`.

Read the product/UI contracts and inspect the exact candidate read-only. Perform every required critique pass in the shared visual-language course. Begin with a source-blind five-second affordance inventory of the current rendered evidence: list every control that is visibly recognizable as interactive and predict its action before reading its label or source. For each critical icon, name the metaphor, explain why placement supports it, and explicitly report collision/negative-space quality at actual size. Reject a critical action that depends on label reading, invisible hit areas, footer-like text, bare task-starting header typography, a malformed compound glyph, a contradictory metaphor, or a spatial cue moved away from the boundary it describes. Then check task steps, state truth, action ownership, pending/acknowledged separation, conflicting operations, error/recovery, destructive consequences, and content utility. Reject copy that does not change action, trust, freshness, or recovery. Do not edit.

Pass only after recording `interaction-affordance-is-self-evident`, `icon-and-spatial-metaphor-are-coherent`, `immutable-pending-operation-truth`, `no-conflicting-ticket-operations`, and `operational-copy-earns-space`. The first two checks must cite current rendered PNG evidence; source or semantics alone cannot satisfy them. For each check, cite hashed leaf evidence allowed by `docs/ui-review-policy.json`; source supports the review and belongs in the record's `reviewedFiles`, but it is not `checkEvidence` unless the policy explicitly allows that evidence type. Copied check IDs without approved evidence are a failure.
