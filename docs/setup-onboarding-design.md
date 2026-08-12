# Xtraordinary setup onboarding design

**Status:** Approved implementation brief  
**Date:** 2026-08-11  
**Target:** Android first-run setup on compact portrait phones, verified first on Pixel 10 at 412 x 915 dp

## Purpose

Setup should get a new user to the first useful state quickly: a phone library linked to an XTEINK device. It is a three-page working flow, not a tutorial before the flow.

The Welcome page introduces that value and offers optional Google backup. The Library and Device pages own their respective instructions and actions. Welcome must not repeat those later steps.

## User constraints

These requirements are binding:

- The complete Welcome page, including **Back up with Google**, is visible without vertical scrolling at the default Pixel display and font settings.
- Do not add **More**, a down arrow, a fade, or another cue implying hidden required content.
- Do not add **Start setup**. The user is already in setup and moves between its pages directly.
- Do not repeat folder-linking or device-adding instructions on Welcome; those belong to pages 2 and 3.
- Keep the established illustrations at the same 176 x 150 dp visual scale across all three pages.
- Use a generic Android phone silhouette with a centered hole-punch camera; do not use an iPhone notch or branded hardware cues.
- Keep the illustration language flat, minimal, and close to the original placeholder geometry. Do not introduce engraving, photorealism, 3D materials, or ornate editorial scenes.
- Do not compress the whole flow merely to solve one overflow. Preserve readable type and deliberate whitespace.
- Keep **Skip** available for experienced users.

## Research basis

The design pass used Impeccable's onboarding, layout, typography, color, Android, and native-adaptation guidance. The relevant conclusions are:

- Onboarding should lead to first value, teach one concept at a time, remain skippable, and avoid duplicating instructions that are better presented at the point of action.
- Layout hierarchy must be established through reading order, proximity, and rhythm. A clean mechanical scan does not prove that the visual hierarchy is good.
- Android UI should use Material components, semantic type roles, semantic color roles, safe drawing insets, and a minimum 48 dp touch target.
- Default compact-phone composition should fit cleanly, while larger text and unusually short windows must reflow or scroll without losing content or functionality.

Primary references:

- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3): semantic color, typography, shape, and emphasis roles.
- [Compose button guidance](https://developer.android.com/develop/ui/compose/components/button): filled, tonal, and text buttons map to different action emphasis.
- [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults): interactive targets should be at least 48 dp.
- [Compose accessibility](https://developer.android.com/develop/ui/compose/accessibility): scalable content, semantics, traversal, and testing.
- [Build adaptive apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps): layouts must respond to the available window rather than a specific device model.
- [Compose window insets](https://developer.android.com/develop/ui/compose/system/insets-ui): protect important content and controls from system UI with safe insets.
- [WCAG reflow guidance](https://www.w3.org/WAI/WCAG22/Understanding/reflow): resizing must not make information or functionality disappear.

## Information hierarchy

The reading order is consistent on all three pages:

1. **App context:** `Xtraordinary` and the low-emphasis **Skip** action.
2. **Flow context:** `Setup` as the screen title.
3. **Progress:** `n of 3 · Page` inside the selected page.
4. **Concept:** one full-size line illustration.
5. **Page purpose:** one `headlineMedium` title.
6. **Page-specific content and actions:** only information needed for this step.
7. **Position:** three passive page dots below the pager.

The Welcome page contains:

- `Your phone library and XTEINK in one place.`
- the optional Google backup group;
- no folder/device instruction;
- no page-level continuation button.

The right-hand page edge plus the page count and dots communicate horizontal progression. They must not be confused with vertical overflow messaging.

## Spacing system

Use a 4 dp base grid and name setup-specific roles instead of scattering one-off values. The desired rhythm alternates close spacing inside a semantic group with larger spacing between groups.

| Role | Value | Reason |
| --- | ---: | --- |
| Screen top | 8 dp | Breathing room after safe drawing inset |
| Screen bottom | 16 dp | Separates dots from navigation inset |
| Header horizontal | 20 dp | Aligns the app header and screen title |
| Screen-title top/bottom | 8 / 12 dp | Keeps title with the pager while separating it from the header |
| Pager side inset | 20 dp | Leaves an 8 dp next-page edge with 12 dp page spacing at 412 dp width |
| Page spacing | 12 dp | Keeps adjacent cards distinct |
| Page inner horizontal | 24 dp | Stable readable measure |
| Page inner vertical | 20 dp | Generous without causing default overflow |
| Major page-group gap | 16 dp | Step, illustration, title, and action groups |
| Step-chip padding | 16 x 8 dp | Compact label with clear shape |
| Inner-card padding | 16 dp | Matches established card density |
| Closely related text gap | 4 dp | Title and explanation read as one group |
| Group separation | 12 dp | Separates explanation, consent, and action |
| Sibling action gap | 8 dp | Standard minimum between controls |
| Full-width button height | 56 dp | Comfortable and consistent across pages |
| Text-button minimum height | 48 dp | Android accessibility floor |

Do not reduce the illustrations. Welcome remains 176 x 154 dp; Library and Device remain 176 x 150 dp, preserving their established shared width and optical scale.

## Typography

Use only the existing Material theme roles:

| Content | Role |
| --- | --- |
| App name | `titleLarge` |
| Screen title | `headlineLarge` |
| Page title | `headlineMedium` |
| Page instruction | `bodyLarge` |
| Optional-backup title | `titleMedium` |
| Optional-backup explanation | `bodyMedium` |
| Consent copy | `bodySmall` |
| Progress and state labels | `labelLarge` |

Do not introduce custom font sizes to solve fit. Text must continue to follow Android font scaling.

## Illustration and motion system

Each page combines one transparent PNG base with a small semantic Compose overlay:

- **Welcome:** generic Android phone and e-ink reader joined by a line; three coral dots pulse in sequence to communicate transfer.
- **Library:** an empty folder base; three code-drawn book cards descend behind its lip.
- **Device:** a plain e-ink reader base; paired arcs pulse outward on both sides.

The PNGs are non-functional editorial assets with one coordinated fixed palette: dark navy outlines, pale lavender fills, and a restrained coral detail. Transparency lets the selected Material surface remain the background, avoiding a foreign rectangular image field. All animated color comes from Material semantic roles. Motion runs only for the settled page, uses a short linear 1.6-second cycle, and follows Android's animator-duration scale. Preview and screenshot rendering uses a fixed representative frame so visual tests remain deterministic. The whole illustration has one semantic description; decorative layers are hidden from accessibility services.

## Color and surface hierarchy

Preserve the existing Expressive theme and Android dynamic color. No raw colors belong in functional setup controls or code-drawn motion. The fixed illustration palette above is the documented exception for generated editorial PNGs.

- Canvas: `background` / `onBackground`.
- Selected page: `surfaceContainerHigh` / `onSurface`.
- Adjacent page: `surfaceContainerLow` / `onSurfaceVariant`.
- Optional backup group: `surfaceContainerHighest`; it is nested and visually distinct without becoming a second primary page.
- Supporting copy: `onSurfaceVariant`.
- Links and checkbox: Material `primary` behavior supplied by their components.
- **Back up with Google:** `FilledTonalButton`, because it is a significant optional action rather than the required completion action.
- **Finish setup:** filled `Button`, the highest-emphasis completion action.
- **Skip**, legal links, and **Do this later:** `TextButton`, the low-emphasis action role.

Selected state, labels, position, and component shape must carry meaning alongside color. Do not use accent color as decoration.

## Welcome composition

The Google backup surface is internally grouped rather than using one uniform gap for every child:

1. Backup title.
2. 4 dp gap.
3. Short optionality/data explanation.
4. 12 dp gap.
5. Unchecked consent row.
6. Legal links, each retaining a 48 dp target.
7. 8 dp gap.
8. Full-width 56 dp tonal action.

The disabled backup button remains visible before consent so the relationship between consent and action is obvious. The unchecked control and the word **Optional** make the choice non-coercive.

## Adaptation and scrolling

- At the default 412 x 915 dp compact portrait viewport, all Welcome content and its button must fit without vertical scrolling.
- Retain vertical scrolling only as a fallback for increased font/display scale, localization expansion, multi-window, or a shorter device.
- Never hide or remove functionality to force a fit.
- No vertical scroll affordance is shown when the page does not overflow.
- Horizontal paging remains the only default-direction gesture, reinforced by edge peek, page count, and dots.
- Safe drawing insets remain applied around the screen.

## Implementation boundary

The implementation should be limited to:

- `SetupScreen.kt` for setup-only layout roles and composition;
- `strings.xml` for setup copy;
- setup screenshot previews and references;
- this design brief and execution-state documentation.

Do not change the global theme or unrelated screens to make this page fit.

## Acceptance criteria

The work is complete only when evidence shows:

- On the real Pixel at default display/font scale, the full Welcome Google card, legal links, and backup button are visible before any vertical scroll.
- The original `Xtraordinary` / `Setup` hierarchy and full-size illustration remain.
- Welcome contains no duplicated folder/device instruction, **More**, arrow, or **Start setup**.
- Library and Device use the same outer alignment, illustration scale, type roles, and 56 dp full-width action sizing.
- Every interactive target is at least 48 dp.
- Static 412 x 915 dp screenshots pass for Welcome, Library, and Device.
- A compact-height preview and a 1.3 font-scale preview show no overlap, clipping, or lost functionality; vertical scrolling is allowed in these stress cases.
- Expressive static color and the real Pixel's dynamic color both retain clear text, surface, action, and disabled-state hierarchy.
- TalkBack traversal follows the visible reading order.
- Impeccable layout and typography scans have no unexplained findings after implementation.
- On a physical phone, only the active page animates; the dots, books, and signal arcs remain decorative and do not carry information unavailable in text.
