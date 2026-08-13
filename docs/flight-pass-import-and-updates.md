# Flight-pass import and ticket updates

## Architecture

The ticket and Focus timer are native X3 screens. Android sends bounded ticket fields plus a small one-bit barcode bitmap over the companion protocol. It does not stream the complete screen. This keeps transfers bounded and lets the X3 retain a static e-ink ticket after Bluetooth disconnects.

## Supported import inputs

The Android **Import flight** action accepts:

- an Apple Wallet `.pkpass` containing `pass.json`;
- a Google Wallet `FlightObject` JSON export;
- a Google Wallet save link shared directly to Xtraordinary, or a text file containing its save JWT.

The importer extracts route, flight, departure/arrival time, operating date when exposed, gate, seat, passenger, boarding group, and the barcode value. It does not place issuer credentials in the app. A decoded save JWT is user-selected input, not proof that the issuer signature is trusted.

Imported passes are stored in the app's private local preferences so a process restart cannot bring the demo cards back over a real import. Android backup is disabled for this app data, and the optional Google reading-history backup explicitly excludes boarding passes.

Android decodes the source value and symbology, regenerates the same real symbology with ZXing, and uses the resulting one-bit BMP for both the app preview and X3. `BeginTicketBarcode`, sequential `TicketBarcodeChunk` packets, and `CommitTicketBarcode` validate a staged image; `ShowTicket` then promotes it alongside the matching fields. A disconnect before `ShowTicket` discards only the staged image. The X3 validates 1-bit BMP dimensions before promotion and never substitutes QR for a successfully imported PDF417 or Aztec pass.

The image is bounded to 340 x 340 pixels and 64 KiB, includes the encoder's quiet zone, and persists beside the ticket metadata on the SD card. Each chunk is protected by the normal envelope CRC and exact sequential offset checks. A disconnected transfer leaves the previous committed barcode untouched.

## One hierarchy, two scanner formats

QR/Aztec-style matrix codes and PDF417/linear barcodes are variants of the same pass, not separate ticket designs. Android therefore keeps the same order for both: route, status and flight, departure/gate/terminal/seat, then the scanner code. Only the code chamber changes aspect ratio. X3 uses the same order, anchors the code in the lower half of the display, and exposes the rotated fullscreen **Scan** action only when the transmitted bitmap is wider than it is tall.

## Static and Live modes

- **Static** sends the ticket, waits for the exact protocol acknowledgement, disconnects Android, stops X3 advertising, and retains the rendered e-ink image.
- **Live** keeps X3 awake and pulse-discoverable, but does not retain GATT merely because the mode is active. Android reconnects only when a fresh update creates durable pending work, then resends the same bounded ticket payload and releases the link after acknowledgement.

`FlightStatusProvider` is the reusable update boundary. `ProxyFlightStatusProvider` calls only a project-owned HTTPS proxy with flight number, operating date, and origin; it rejects mismatched identities and snapshots without an observation timestamp. The live loop polls at most every five minutes and backs failures off to thirty minutes. Static tickets never start it. A materially newer snapshot is persisted and replayed to X3 through the same acknowledged ticket transfer and generic interactive-transport lease used for a manual send.

The endpoint is injected only into the Play build through the `xtraordinaryFlightStatusProxy` Gradle property. It is empty by default and always empty in the community build, so the repository does not pretend that a provider is active before the proxy exists.

Ticket payload v2 adds arrival time and signed delay minutes. X3 advertises support in capabilities; Android sends the legacy payload to older firmware. New firmware explicitly migrates a persisted v1 ticket rather than deleting it. Both app and X3 place arrival and delay with the operational facts above the scanner code. Delay is never inferred from ticket text.

## Google Wallet limitation

Google Wallet's documented `flightobject.get` operation is an issuer API. It requires an issuer OAuth scope, an issuer account, and the object's issuer-qualified ID. It is not a consumer API for listing or exporting arbitrary passes already stored in a person's Google Wallet.

Do not ship a Google Wallet issuer service-account private key in the Android app. If Xtraordinary later becomes a pass issuer, keep those credentials in a small backend and expose only the minimum app endpoint.

Official references:

- <https://developers.google.com/wallet/reference/rest/v1/flightobject/get>
- <https://developers.google.com/wallet/tickets/boarding-passes/getting-started/auth/rest>
- <https://developers.google.com/wallet/reference/rest/v1/flightobject>
- <https://developers.google.com/wallet/tickets/boarding-passes/use-cases/updates>

## Quick external update path

FlightAware AeroAPI is the quickest documented REST candidate for live flight status. A production implementation needs:

1. an API key kept outside the Android package, ideally behind a small proxy;
2. flight identity plus operating date and origin to avoid ambiguous flight numbers;
3. polling only while a Live ticket is active, with a conservative interval and backoff;
4. the proxy response contract documented by `ProxyFlightStatusProvider`, followed by the existing acknowledged resend to X3.

Reference: <https://www.flightaware.com/commercial/aeroapi>
