# Interface hierarchy

Status: corrected hierarchy implemented in the Android prototype, 2026-07-22.

The phone app uses the expressive control-deck interface from concept B. Concept C is retained only as the global Quiet theme. See [`DESIGN.md`](../DESIGN.md) for the visual rules.

## Android app

```text
App shell
├─ X3 status + Settings
├─ Focus tab
│  ├─ X3 image field
│  ├─ duration control
│  ├─ Start / Pause / Resume
│  └─ Send current scene to X3
├─ Read tab
│  ├─ import local EPUB
│  ├─ extract embedded metadata locally
│  ├─ imported books
│  ├─ cross-library search (planned)
│  └─ supported library/catalog connectors (planned)
└─ Tools tab
   ├─ Tools hub
   │  ├─ Passes & codes (available prototype)
   │  ├─ Now playing (planned)
   │  ├─ Navigation (planned)
   │  ├─ Lists & notes (planned)
   │  ├─ Calendar (planned)
   │  ├─ Shared text (planned)
   │  ├─ Phone status (planned)
   │  ├─ Camera remote (planned)
   │  └─ AI output (planned)
   └─ Passes & codes
      ├─ swipeable pass carousel
      ├─ Static / Live pane
      ├─ pass details
      └─ Send to X3

Settings sheet
├─ Expressive / Quiet theme
├─ X3 device
├─ Notification rules
├─ Gemini & privacy
└─ Flight updates

Android share target (planned)
└─ Import preview
   └─ Confirm fields -> Static or Live pane -> Send to X3
```

There are exactly three permanent tabs: Focus, Read, and Tools. Reading is core to the X3, while Passes remains nested inside Tools because many users will never use it. Quiet is a theme and never creates another navigation branch.

## Focus

### Setup

The screen is the concept-B control deck:

1. X3 status and Settings.
2. A real monochrome image field representing the content to send to the rear display.
3. One large duration control.
4. Start focus and a compact send action.

The image field is not a duplicate timer. Starting Focus is always explicit; pairing or placing the phone face-down never starts it.

### Active session

The phone sends an absolute deadline and session definition. The X3 maintains the countdown locally so a BLE interruption does not end the session. Android changes Start to Pause/Resume and exposes End without rearranging the screen.

Notification triage exists only during an explicitly active Focus session:

```text
incoming notification
├─ deterministic urgent rule -> immediate X3 overlay
├─ deterministic quiet rule -> suppress/count
└─ ambiguous -> optional Gemini API classification
   ├─ urgent -> immediate overlay
   ├─ important -> defer until session end
   └─ irrelevant -> suppress/count
```

Rules override AI. Timeout, quota, network, malformed output, safety block, or low confidence falls back to `after focus`, never an interruption.

### Completion

The completion scene contains only:

- `Needs attention`: deferred important cards;
- `Quietly held`: aggregate counts by app/category.

It is not a chronological notification feed.

## Tools hub

The hub exposes optional companion functions without forcing them into the app's primary navigation. Only working tools are actionable. Planned cards are visibly labelled so the prototype never claims an integration is complete.

## Read

The working path is library-first and uses Android's system multi-document picker:

```text
Read library -> small Import action -> OpenMultipleDocuments
-> copy persistent read access when available
-> inspect META-INF/container.xml
-> locate the OPF package
-> extract cover / title / author / language / publisher / date / ISBN
-> fill only missing fields from Open Library when reachable
-> cache normalized metadata and cover locally
-> restore the library automatically on future launches
```

The app does not upload the EPUB, scrape another app's files, or bypass DRM. The picker can select several documents at once. Duplicate document URIs are skipped and counted; malformed EPUBs add nothing. Since DocumentsUI is owned by Android rather than this app, imported rows cannot be greyed inside the system picker. The in-app library remains the authoritative imported state.

Search and All / With covers / Needs details filters operate on cached local metadata. EPUB fields always win. A missing field may be enriched through a low-volume Open Library Search API request; normalized results and downloaded cover art are cached, and failed lookups are throttled for seven days.

The screen also reserves honest planned surfaces for unified title/author search and opt-in provider connections. Google Play Books, Kindle, Kobo, and Project Gutenberg are discovery targets, not implied working integrations. Purchased collections are shown only when a provider offers supported authorization and access.

## Passes & codes

### Collection interaction

The complete pass card is the carousel page. There is no route chip or ticket selector above it. The viewport reveals an edge of the next card, and after the first page it also reveals the previous card edge. A horizontal swipe selects another pass; details below follow the settled page.

Each card contains:

- regenerated code preview;
- full departure → arrival route on both perforated edge rails, plus flight and status;
- departure time and countdown;
- gate, terminal, and seat;
- Start live pane;
- Send to X3 without starting live updates.

### Google Wallet entry

The reliable first flow is:

```text
Open boarding pass in Google Wallet
-> take screenshot
-> share screenshot to X3 Companion
-> local barcode decode + local OCR
-> confirm the small set of ticket fields
-> choose Static or Live pane
-> send to X3
```

The app copies the shared image immediately, decodes Aztec/QR/PDF417 locally, regenerates the symbol at exact X3 pixels, and treats OCR text as editable suggestions. It never sends a resized screenshot as the final code.

A Google Wallet share link is not a general data API. `flightobject.get` requires issuer authorization, so an ordinary companion app cannot retrieve another issuer's barcode just from an object reference. When only a Wallet link is received, the app should explain that a screenshot or original airline/PDF source is required.

### Static versus Live pane

| Choice | X3 power | Updates | Use |
|---|---|---|---|
| Static | Render, persist, then complete power-off | None until physical wake | Reliable scanning and maximum battery life. |
| Live pane | Low-duty BLE companion state | Android pushes meaningful changes | Gate, delay, boarding, and departure updates. |

Static is the safe default. Live pane is explicitly started and can later be pinned as a static scene.

### X3 live layout

The code region has fixed physical pixels and a protected quiet zone. A subordinate pane may show flight, status, gate, scheduled versus estimated time, source, and freshness. It never overlaps or resizes the barcode region.

Gemini is not a flight-status source. It may extract fields from an airline notification, but live values must originate in that notification or an authoritative provider behind an Android `FlightStatusProvider` interface.

## Theme behavior

Expressive and Quiet render the same hierarchy and states. Theme switching preserves:

- active Focus deadline and pause state;
- selected Tools destination;
- current pass carousel page;
- imported EPUB metadata and current reading collection;
- Static/Live pane choice;
- settings/back-stack position.

## X3 / CrossPoint hierarchy

```text
Existing CrossPoint reader and menus
├─ companion pairing/recovery settings
└─ active companion scene
   ├─ base bitmap
   ├─ optional dynamic fields
   ├─ temporary overlays
   ├─ deferred cards
   ├─ generic session/deadline
   └─ contextual physical-button actions
```

CrossPoint does not gain a launcher full of phone features or a hard-coded Pomodoro app. The phone activates one generic scene; Back/Exit restores the previous reader/menu surface.

## Accessibility and trust

- 48 dp minimum Android touch targets.
- TalkBack labels describe actions, not icon shapes.
- Large text scrolls instead of clipping the primary action.
- Connection, freshness, and urgency never rely on color alone.
- The UI says `Not paired` until a real device connection exists.
- Sample codes are marked and intentionally non-scannable.
- The phone never claims an X3 update until the firmware acknowledges it.

## Primary references

- [Google Wallet `flightobject.get` authorization](https://developers.google.com/wallet/reference/rest/v1/flightobject/get)
- [Firebase AI Logic](https://firebase.google.com/docs/ai-logic)
- [Firebase AI Logic structured output](https://firebase.google.com/docs/ai-logic/generate-structured-output)
- [Gemini API key security](https://ai.google.dev/gemini-api/docs/api-key)
