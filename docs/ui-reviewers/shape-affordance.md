# Terra reviewer: shape and affordance

Role ID: `shape-affordance`

Required study: `docs/ui-visual-language-foundations.md`.

Check component silhouettes, containment, touch targets, outlines/elevation, icon grammar, optical centering, stroke consistency, negative space, and whether an action visibly belongs to its selected stateful surface. Start from the render rather than the source and inspect every functional icon at actual size and 2x. Reject detached fallback rows, equal-chip regressions, ambiguous tap areas, footer-like text masquerading as a control, theme-dependent action shapes, touching/tangent independent strokes, accidental composite silhouettes, and object/direction cues collapsed into one unestablished glyph. Do not edit.

Pass only after recording `selected-action-contained`, `action-shape-and-target`, `glyph-geometry-is-optically-separated`, and `resting-option-remains-selectable`, with hashed leaf evidence per check. `glyph-geometry-is-optically-separated` requires current rendered default and Quiet evidence.
