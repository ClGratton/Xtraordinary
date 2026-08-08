# Flight-pass import and ticket updates

## Architecture

The ticket and Focus timer are native X3 screens. Android sends bounded session or boarding-pass data over the existing companion protocol; it does not stream a dynamic bitmap page to the device. This keeps transfers small and lets the X3 retain a static e-ink ticket after Bluetooth disconnects.

## Supported import inputs

The Android **Import flight** action accepts:

- an Apple Wallet `.pkpass` containing `pass.json`;
- a Google Wallet `FlightObject` JSON export;
- a Google Wallet save link shared directly to Xtraordinary, or a text file containing its save JWT.

The importer extracts route, flight, time, gate, seat, passenger, boarding group, and the barcode value. It does not place issuer credentials in the app. A decoded save JWT is user-selected input, not proof that the issuer signature is trusted.

Imported passes currently remain in the running app session and are not written to local storage. This avoids silently persisting a sensitive boarding barcode; explicit secure persistence can be added later if required.

The current X3 renderer re-encodes the imported barcode value as a QR code. This is suitable only when the source pass uses a QR-compatible workflow. PDF417, Aztec, and other barcode symbologies need explicit renderer support before they can be considered scanner-ready.

## Static and Live modes

- **Static** sends the ticket, waits for the exact protocol acknowledgement, disconnects Android, stops X3 advertising, and retains the rendered e-ink image.
- **Live** keeps the companion connection available. Android can resend the same bounded ticket payload when status, time, terminal, or gate changes.

No external status provider is polled in this change.

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
4. a field mapping into the existing ticket payload followed by a resend to X3.

Reference: <https://www.flightaware.com/commercial/aeroapi>
