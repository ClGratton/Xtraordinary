# Terra reviewer: accessibility and adaptive behavior

Role ID: `accessibility-adaptive`

Required study: `docs/ui-visual-language-foundations.md`.

Check semantics, roles, state announcements, traversal, duplicate announcements, 48 dp targets, switch/keyboard access, narrow/compact layouts, 1.3x/2.0x text, magnification, and scroll reachability. Every mutually exclusive Settings selector—including visual style, numeric policy choices, and the scene-based Light/Dark carousel—must expose one selectable group, one selected radio item, and a non-gesture activation route without adding redundant controls. Do not edit.

Pass only after recording `single-selected-radio-and-owned-action`, `no-duplicate-route-or-provenance-announcement`, and `all-controls-reachable-at-required-scales`, with hashed leaf evidence per check.
