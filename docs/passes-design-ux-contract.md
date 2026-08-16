# Passes design and UX contract

Status: mandatory for Android Passes work and the native X3 ticket renderer.

This contract reconciles independent reviews of UX, hierarchy, spacing, margins and alignment, typography, color, and shape/affordance. It complements `PRODUCT.md`, `DESIGN.md`, `docs/ui-decision-workflow.md`, the transport lifecycle, and the acknowledgement rules in `AGENTS.md`.

## User tasks and truth model

The traveler must be able to:

1. identify the selected pass;
2. identify which pass and mode X3 has actually acknowledged;
3. choose Static or Live for the next send;
4. send or remove a named pass and understand queued, connecting, transferring, acknowledged, stale, and failed states;
5. inspect a large scanner-safe code without losing carousel position;
6. understand that removing the X3 copy does not delete the phone copy.

Keep selected phone pass, desired next-send mode, persisted pending intent, transport progress, acknowledged X3 pass/mode, fresh authoritative reconciliation, and cached capability/presence distinct. Connection, lease ownership, desired state, cache, or a tap never means X3 applied a command. Applied UI requires the matching acknowledgement or later fresh authoritative reconciliation.

Opening Passes, turning a card, or paging must not acquire, retry, or renew the interactive Bluetooth lease. Send, remove, and explicit refresh use the reusable scoped lease. A displayed Live pass may remain pulse-discoverable without retaining GATT solely because Passes is visible.

## Phone information architecture

The screen order is fixed:

1. Back and Import flight share the first action row.
2. A separate context row contains the large left-aligned `Passes & codes` title and right-aligned count.
3. The selected pass is the dominant object in an edge-peeking carousel.
4. Compact deployment status states acknowledged or pending X3 truth with pass identity.
5. Static and Live form one exclusive `Mode for next send` group with one stable primary action.
6. Bottom navigation remains separate.

Never center the title between Back and Import. Never let connection chrome, count, mode tiles, or flight number compete with the selected pass.

### Compact pass front

- Primary: origin to destination.
- Secondary: status and signed delay; departure and arrival; gate, terminal, and seat.
- Tertiary: flight number, passenger/group, source/freshness, and explicit `Show code` action.
- The front contains no barcode semantics.
- A sample always says `Sample - not valid for boarding` visually and semantically.

### Full-code back

- Primary: one large binary barcode and its quiet zone.
- Secondary: one thin route/flight identity line.
- Tertiary: explicit `Show details` action.
- The back contains no duplicate operational-fact grid.
- Front and back have identical bounds, clipping, shape, elevation, and carousel position.
- Swap faces at the midpoint of a restrained vertical-axis turn; text is never mirrored. Reduced motion performs an immediate same-bounds swap.
- The whole card is not an invisible tap target. The labelled turn control is at least 48 by 48 dp.

Horizontal drag belongs exclusively to pass paging. It retains magnetic resistance, threshold/fling/settle haptics, and visible neighboring route rails. Turning never changes pager index; paging never changes face.

## Deployment and mode interaction

Deployment status names the affected pass and acknowledged mode. Required state language includes:

- `Not on X3`
- `On X3: AZ 610 - Static`
- `A different pass is on X3: DL 2048 - Live`
- `AZ 610 - Static is queued for X3. It is not on X3 yet.`
- `Sending AZ 610 to X3...`
- `Removing AZ 610 from X3. It stays visible until X3 confirms.`
- `AZ 610 removed from X3. Your phone copy remains.`

If selected ID differs from acknowledged deployed ID, the selected card is never labelled `On X3` and an unqualified remove action is never shown.

Static and Live are one selectable group. Its semantic label is `Mode for next send`; changing it cannot relabel acknowledged X3 mode. Both options retain the same base material and zero elevation. Only the selected option owns the send/remove action.

The chooser's quality comes from physical continuity and unequal emphasis: the selected option expands horizontally, its color and outline transform, and its action grows from inside the same surface; the resting option contracts but remains legible and selectable. This single authored motion explains selection, power consequence, and action ownership without another instruction block. It must not be flattened into detached equal-width chips plus a separate action row. The enclosing chooser keeps stable bounds while selection changes, haptics confirm the threshold, and reduced-motion settings remain authoritative.

- Static: `Keeps this confirmed pass visible after X3 sleeps. No flight refresh.`
- Live: `Checks for flight changes periodically. X3 may sleep between check-ins.`

Do not use `Live pane`. Source and freshness are visible when available. Live with stale or unavailable provider data says so and preserves last confirmed X3 truth.

Removal names the deployed pass and confirms that the phone copy remains. Pending removal continues to render old acknowledged truth until the matching clear acknowledgement.

Use compact inline status near its operation. Do not add redundant transient bottom banners for ordinary send success when acknowledged status and transformed action already communicate it. Status changes use a polite live region and never depend on color or haptics alone.

## Android visual system

### Hierarchy and typography

Use the existing system sans family and semantic Material roles through purpose-named pass tokens. Do not add another Android font.

- Screen title: `headlineLarge`.
- Route and primary departure/arrival time: `titleLarge`; time uses tabular numerals.
- Gate, terminal, and seat: `titleMedium` and must not be outranked by passenger or flight metadata.
- Passenger/group: body role.
- Status, flight identity, count, and chrome: label roles.
- Static/Live explanation: body role, not a semibold label block.
- Actions: label role.

Dynamic bounded text declares line limit and overflow. Never shrink text to fit. Operational values reflow before truncation. The screen scrolls at large font scale rather than clipping controls.

### Color

Use complete reviewed Material role pairs. A static theme must not inherit an unreviewed chromatic default role.

- Quiet defines every used role as true grayscale.
- Ordinary text contrast is at least 4.5:1; required icons, outlines, and state boundaries are at least 3:1.
- Saturated primary is reserved for the enabled main transaction. Neutral status does not use primary or error.
- Selected mode uses `primaryContainer/onPrimaryContainer`; unselected uses a surface pair and outline.
- Late/problem uses an error container plus signed or explanatory text. Early/informational uses a tertiary container plus signed text.
- Scanner content is fixed pure black and white.
- State remains understandable when desaturated.

### Shape and containment

Use the checked-in shape family by role:

- physical pass: `shapes.large`, low elevation, no outline;
- Static/Live choices: `shapes.large`, zero elevation; selection changes proportion and color rather than replacing the silhouette;
- scanner chamber: `shapes.small`, one hard border;
- actions and badges: `CircleShape`.

Avoid double framing. The pass uses elevation, unselected choices use an outline, and the scanner chamber uses a hard border. Raw corner literals do not belong in Passes.

### Spacing and alignment

Use a four-dp quantum and purpose-named tokens. The default vocabulary is 4, 8, 12, 16, and 24 dp; raw one-off gaps require an explicit component constraint.

- Non-bleed groups use a 16 dp screen gutter.
- Header actions share one vertical center. Context title starts and count ends on the common gutter.
- The pager alone bleeds to the viewport.
- Define pager content padding `P`, page gap `S`, and visible neighboring route rail `R`; preserve `P - S = R`.
- Left/right rails and perforations mirror around the card center.
- Matrix/linear cards and both faces share outer bounds and center axes.
- Body inset adapts so code plus quiet zone remains contained at compact widths.
- Static/Live selection and pending/action transforms do not move the enclosing row.

Normal-scale header, card, deployment status, and mode/action fit the first content viewport at 412 by 915 and 360 by 800. At larger text scales scrolling is allowed; clipping and overlap are not.

At normal text scale the pass height is viewport-responsive within reviewed minimum and maximum bounds: compact screens keep the minimum, while taller phones give the pass the otherwise unused vertical space. Font-scale expansion remains a separate policy so accessibility growth is never inferred from screen height.

## X3 ticket contract

Matrix and linear tickets are one design. Header and operational regions use identical coordinates; only scanner-chamber aspect ratio changes.

Order:

1. route header;
2. status and signed delay, departure and arrival, gate, terminal, and seat;
3. lower scanner chamber;
4. optional non-overlapping metadata/footer;
5. mapped physical-button hints.

The code remains dominant and is anchored low. Flight, passenger/group, and Static/Live are tertiary. Hierarchy uses size, weight, position, and black/white structure, never multiple gray shades.

Use one computed `TicketLayout` owner for portrait 528 by 792 and rotated 792 by 528:

- portrait center axis is x=264;
- bottom 40 px mapped-hint strip is excluded from content;
- ordinary content ends above the hint safe area;
- three operational columns derive symmetrically from screen width and common margins;
- footer/mode text never intersects the scanner chamber;
- fallback QR uses the same computed matrix rectangle;
- barcode quiet zones remain binary and undecorated.

Wide linear/PDF417 tickets expose mapped `Back / Scan`; fullscreen scan exposes mapped `Back / Ticket`. Matrix tickets remain portrait and expose Back only. Fullscreen scan uses integer scaling inside the real safe rectangle, never overlaps mapped hints, and returning to portrait performs a full refresh. Do not draw one-off button labels; use the reusable mapped-button path.

X3 dynamic strings use purpose-named font roles and width-bounded helpers. Layout uses measured font bounds or geometric tests. UTF-8 truncation never splits a code point, and unsupported glyphs are not silently rendered as replacement characters.

## Mandatory machine evidence

### State and UX

- matching ticket acknowledgement gates deployed success;
- matching clear acknowledgement or fresh authoritative reconciliation gates removal success;
- selected and deployed pass identities cannot be conflated;
- desired next-send mode cannot relabel acknowledged mode;
- opening, turning, or paging Passes does not own an interactive lease;
- every pending/failure state names operation, pass, and current device truth;
- sample, source, freshness, and stale Live semantics are exposed;
- pager has position plus Previous/Next accessibility actions;
- Static/Live is a selectable group with exactly one selected option;
- operation controls are at least 48 dp and status is a polite live region.

### Android geometry and rendering

- 412 by 915 and 360 by 800, normal font;
- 412 by 915 at 1.3 and 2.0 font scale;
- matrix and linear, front and full-code back;
- Expressive fallback, Quiet, and an injected dynamic palette;
- neutral, delayed, pending, acknowledged, stale, error, sample, and long-content fixtures;
- header/context/non-bleed gutter equality and non-overlap;
- `visibleNeighborWidth = routeRailWidth`;
- mirrored rails/perforations;
- identical front/back bounds;
- barcode containment and quiet zone;
- selector and navigation non-overlap;
- tap-turn keeps page; swipe-page keeps face; reduced motion reaches the same final state;
- pager haptic regression tests;
- every used static color role explicitly defined, Quiet roles grayscale, role-pair contrast passing, and interpolated pager colors legible.

Screenshot comparison supports these checks but does not replace bounds, semantics, contrast, state, or interaction assertions. Baselines change only after deliberate visual review.

### X3 geometry and rendering

- pure layout tests for portrait 528 by 792 and rotated 792 by 528;
- all regions inside the display safe area and outside mapped hints;
- symmetric operational columns and common screen axis;
- shared header/fact pixels for equal matrix and linear metadata;
- scanner/footer non-intersection;
- matrix, PDF417, Code128, fallback QR, and rotated scan containment;
- integer scaling and quiet-zone preservation;
- binary 0/255 pixels and no gray-coded meaning;
- maximum protocol-length and non-ASCII fixtures with safe glyph/truncation behavior;
- mapped Back/Scan and Back/Ticket labels plus full refresh on scanner exit.

## Mandatory human evidence

Automation cannot approve:

- first-glance salience and visual hierarchy;
- optical balance, calmness, shape character, and amount of neighboring reveal;
- whether Static/Live wording predicts real power behavior;
- whether turn affordance is discoverable and motion reads as one object;
- real TalkBack, Switch Access, Voice Access, D-pad, magnification, and large-text comprehension;
- physical e-ink legibility, ghosting, mapped-button alignment, and barcode scanning;
- real ACK, deep-sleep retention, Live pulse timing, stale-provider behavior, and recovery.

Reviewers must state whether evidence is source-derived, rendered, automated, phone-tested, protocol-observed, X3-observed, or scanner-observed. Passing a build or screenshot gate alone is never visual, UX, power, or hardware acceptance.
