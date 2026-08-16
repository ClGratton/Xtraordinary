# UI decision workflow

Status: mandatory for Android companion UI work. This process complements `PRODUCT.md`, `DESIGN.md`, Android platform guidance, and the repository engineering policy gate.

## Purpose

UI work must be based on product intent, rendered evidence, and explicit trade-offs. A local improvement is not accepted if it breaks the surrounding hierarchy, hides a required action, invents a new interaction convention, or only works in one screenshot.

## Mandatory sequence

### 1. Establish product truth

- Name the user task, the user’s current state, and the outcome the surface must enable.
- Separate required work from optional enhancements.
- Identify what the app can prove now; never style an unavailable or unacknowledged capability as complete.

### 2. Locate the existing owner

- Read `PRODUCT.md`, `DESIGN.md`, and the implementation and state owner for the surface.
- Prefer an existing reusable component, lifecycle, state model, spacing token, or navigation destination.
- Do not duplicate a task in onboarding and Settings. Onboarding introduces; Settings manages.

### 3. Inspect rendered evidence

- Review the current default screenshot and the real-device surface when available.
- For changed full-screen or sheet surfaces, inspect default phone, compact-height or narrow-width, and 1.3x text renders.
- Check the first visible viewport and the complete scroll range. Ordinary scrolling must be evident from content flow; never add “More” merely to reveal required controls.

### 4. Research the platform pattern

- Use current primary sources: official Android, Material, Google Identity/Drive, and W3C guidance as applicable.
- Record links in the audit or decision note when external guidance materially affects the result.
- Platform convention yields only when the product has a documented reason and the alternative remains accessible.
- Study and apply `docs/ui-visual-language-foundations.md` before selecting or drawing any functional icon. A visible control is not accepted until its metaphor, geometry, placement, and optical quality agree.

### 5. Write the hierarchy before layout values

- State the primary information, primary action, secondary actions, status, explanation, and destructive action in that order.
- In Settings, order groups by expected use and product context. Do not promote an infrequent account, backup, reset, or help action merely because it must be available directly.
- Use size, position, and grouping before color.
- Reserve saturated color for selection, urgency, and the dominant action; do not make every card equally prominent.

### 6. Evaluate alternatives and dependencies

- Consider at least the incumbent pattern and one plausible alternative.
- Record why the chosen pattern reduces steps, ambiguity, or state duplication.
- Check adjacent surfaces so the change does not move the same task into two owners or remove needed context.

### 7. Cover all states

Every interactive surface must deliberately handle the applicable states:

- disconnected, connected, and stale/reconnecting;
- idle, loading/progress, success, recoverable failure, and unavailable;
- empty, one item, many items, and long content;
- default text, 1.3x text, compact height, and narrow width;
- TalkBack name/role/state, keyboard or switch navigation where applicable, and reduced motion;
- destructive confirmation, cancellation, and recovery.

### 8. Implement with bounded tokens

- Reuse the established type, color, shape, spacing, and motion system.
- Add a shared token or component when the same rule appears more than once.
- Do not change unrelated spacing or illustration geometry to solve a local overflow.
- Generated artwork is allowed for editorial onboarding imagery, not for functional icons or controls. Store final assets in `drawable-nodpi` and provide semantic descriptions when the image communicates meaning.

### 9. Verify and record evidence

- Run the separate stable Terra reviewer prompts required by `docs/ui-specialist-review-contract.md`; do not let the implementation owner self-certify UX, hierarchy, layout, typography, color, shape, motion, or accessibility.
- Bind each passing receipt to the exact reviewed source hashes. Any later source change expires the receipt and requires the affected reviews again.
- Run the engineering policy gate and canonical Android build.
- Generate the screenshot references named in `docs/ui-surface-evidence.json` and inspect the changed images, not just the task result.
- The canonical build must run screenshot validation against the reviewed references. Updating a reference is a separate, deliberate review action and must never happen inside the normal validation build.
- For changed interactive layouts, add machine-readable geometry and semantics assertions for the critical relationships identified in step 5. A screenshot comparison is supporting evidence, not a substitute for bounds, non-overlap, touch-target, traversal, and state assertions.
- Run the Impeccable detector once on the final changed UI targets.
- On a physical phone, exercise the actual primary action and capture the resulting state. Hardware claims require protocol or device evidence, not presence alone.
- Add the decision and remaining limitations to the dated audit, acceptance note, or handoff.

## Definition of done

A UI change is complete only when:

1. the correct surface owns the task;
2. required actions are visible or reachable by ordinary scrolling without an ambiguous overflow label;
3. loading, failure, empty, and destructive states are honest;
4. compact/default/large-text evidence exists for the changed surface;
5. touch targets, contrast, semantics, and motion behavior are checked;
6. the policy gate and canonical build pass;
7. the real interaction is verified where the environment permits it;
8. unresolved external configuration or hardware limits are stated explicitly.

## Prohibited shortcuts

- Do not reopen setup to manage an account, backup, or ordinary preference.
- Do not add “More” to hide required content that can be laid out or scrolled normally.
- Do not shrink all illustrations or type globally to fix one dense state.
- Do not remove explanatory copy solely to make a screenshot fit.
- Do not present a planned feature with the same enabled affordance as a working feature.
- Do not infer design quality from compilation or a single default-size screenshot.

## Authorities

- `PRODUCT.md`: product purpose, user, principles, accessibility intent.
- `DESIGN.md`: current navigation, theme, hierarchy, motion, and component decisions.
- `docs/ui-audit-2026-08-11.md`: dated evidence and prioritized findings.
- `docs/passes-design-ux-contract.md`: mandatory reconciled UX and visual contract for Android Passes and the X3 ticket renderer.
- `docs/ui-surface-evidence.json`: machine-checked render coverage contract.
- `docs/ui-specialist-review-contract.md`: permanent reviewer roles, receipt workflow, and bounded-delegation rules.
- `docs/ui-review-policy.json`: machine-checked role, source, and evidence contract.
- `docs/engineering-policy.json`: build-blocking repository rules.
