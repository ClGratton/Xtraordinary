# X3 Companion design system

Status: direction confirmed on 2026-07-22 and implemented in the Android prototype.

The selected structure uses concept B as the interface and concept C only as a global **Quiet** theme. Quiet never changes navigation, feature availability, content order, or the current Focus/Read/Tools state.

## North star

Use the interaction grammar of the Pixel media player without pretending the companion is a music app: one oversized stateful object, bold numerical information, shaped controls, strong tonal contrast, and calm transitions. Material 3 Expressive supplies the visual language; the X3 e-ink image field supplies the product identity.

Expressiveness comes from proportion, shape, type, and motion, not from adding destinations or decorative cards.

## Permanent hierarchy

The app has exactly three top-level destinations:

```text
Focus
Read
  Local EPUB import and metadata
  Imported books
  Cross-library search (planned)
  Store/catalog connections (planned)
Tools
  Passes & codes
  Now playing (planned)
  Navigation (planned)
  Lists & notes (planned)
  Calendar (planned)
  Shared text (planned)
  Phone status (planned)
  Camera remote (planned)
  AI output (planned)
```

Read is top-level because reading is core to the X3. Passes is an optional tool, not a tab. Focus contains only focus controls. Settings is opened from the compact X3 status row and does not become another tab.

## Themes, not modes

### Expressive

- Warm coral palette, light surfaces, strong primary action.
- The B control-deck layout is the default.
- Large image field, organic duration surface, and generous shaped actions.

### Quiet

- Black and charcoal surfaces with a restrained lime accent.
- Exactly the same Focus/Read/Tools hierarchy, geometry, labels, and actions.
- Switching theme preserves the timer, selected pass, carousel page, live/static choice, and back stack.

The theme choice lives in Settings. There is no Normal/Minimal tab, density mode, or separate Quiet screen.

## Focus screen

Order is fixed:

1. compact X3 connection state and Settings;
2. the actual monochrome image field that will be sent to the X3;
3. one organic `Display duration` surface with large time, increment/decrement controls, and slider;
4. one dominant Start/Pause/Resume action and a compact send-to-X3 action;
5. Focus/Read/Tools navigation.

The image field is visual content, not a second timer or editable task field. The duration appears once. Starting Focus is explicit and never happens merely because the device pairs or the phone is placed face-down.

After Start, the primary action performs a short mitosis transition into two equal sibling controls: Pause/Resume and Stop. Stop is never hidden in an overflow menu, and the transition runs once per state change rather than continuously.

## Tools screen

Tools is a quiet capability hub. The available Passes & codes tool is visually dominant; planned functions remain visibly marked as planned and do not pretend to work.

Opening a tool is nested navigation. Android Back and `Back to Tools` return to the hub while the bottom navigation remains on Tools.

## Read screen

- Read opens directly as the user's library; there is no import hero competing with the books.
- A small book-plus action at bottom right opens Android's multi-document picker for one or more EPUBs.
- The library, filters, embedded covers, and normalized metadata restore automatically at launch.
- EPUB metadata and cover art are authoritative. Missing cover, author, publisher, language, or year may be filled with a low-volume Open Library lookup, then cached locally with a seven-day retry throttle.
- Duplicate document URIs are not added again and the result notice reports already-imported selections. Android's system picker owns its file-row appearance; the app cannot recolor those rows.
- Local title/author/metadata search and All / With covers / Needs details filters work now.
- Research and connectors for Google Play Books, Kindle, Kobo, and Project Gutenberg remain visibly marked planned and separate from owned books.
- Future connectors use supported sign-in/provider access only. The app never scrapes credentials or bypasses DRM.

## Passes & codes

- Pass cards form a horizontal edge-peeking carousel.
- There is no duplicate selector row. Swipe the card itself to change pass.
- The next card edge is visible on the first item; previous and next edges are visible in the middle of a longer collection.
- Each perforated side rail shows the complete departure → arrival route, so either exposed carousel edge identifies its pass.
- The selected card contains code, route, flight/status, departure/countdown, gate, terminal, seat, Start live pane, and Send to X3.
- Details below the carousel update with the settled card.
- Static and Live pane are presentation/power choices, not navigation destinations.
- Sample codes are visibly marked and intentionally non-scannable.

## Color, shape, and type

- Use fixed product palettes so Expressive and Quiet remain intentional across devices.
- Never use color alone for connection, urgency, selection, or freshness.
- The duration surface uses an asymmetric, softly undulating outline rather than a generic rounded rectangle.
- X3 previews remain rectangular enough to communicate real e-ink geometry.
- Countdown and flight time use tabular display numerals; labels stay short and sentence case.
- Minimum touch target is 48 dp. Large-text layouts must scroll instead of clipping controls.

## Icons

Icons are drawn as consistent vector geometry. `Send to X3` uses an upward transfer arrow leaving a tray, never a Unicode approximation. The same icon is shared by Focus and Passes.

## Motion

- Use short fades for destination changes and restrained spring motion for direct manipulation.
- The pass carousel follows the finger and snaps to a card.
- Do not animate continuously while a timer runs.
- Never imply the X3 updated before a firmware acknowledgement.
- Respect Android reduced-motion settings.

## X3 display rules

- Phone UI may be expressive; the X3 scene stays pure black and white.
- Barcode quiet zones and module size take priority over decoration.
- Live flight data never overlaps or resizes the code region.
- Static passes can be committed as the final e-ink image and then power the X3 down.
- Live pane keeps a low-duty connection only while updates are useful.

## Explicit anti-patterns

- No Passes, Ticket, Minimal, or AI top-level tab.
- No ticket actions inside Focus.
- No selector chips above a swipeable pass carousel.
- No feature grid presented as if every planned tool already works.
- No fake device connection, fake scannable pass, glassmorphism, or continuous decorative animation.

## Primary references

- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Google Wallet `flightobject.get` authorization requirement](https://developers.google.com/wallet/reference/rest/v1/flightobject/get)
- [Firebase AI Logic for Android/mobile clients](https://firebase.google.com/docs/ai-logic)
- [Firebase App Check protection for AI Logic](https://firebase.google.com/docs/ai-logic/app-check)
