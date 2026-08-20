# Trial, ads, and ad-free entitlement design

- Status: Play development integration implemented; production identity, entitlement verification, and store configuration remain external
- Date: 2026-08-11

## Product requirements

- A new user receives a clearly dated ad-free trial.
- After the trial really elapses, the app becomes ad-supported without blocking X3 setup, pairing, flashing, reading, backup, or recovery.
- Clearing app data or reinstalling must not restart the trial for the same eligible identity.
- A paid upgrade removes ads and can be restored.
- The upgrade surface must also state that an ad-free build can be compiled from the public GitHub source.
- Development must be able to exercise trial-active, expired, purchased, offline, and restored states without changing the phone clock or waiting days.

## Current repository truth

The Android app has a pure centralized `EntitlementState`, injected `EntitlementClock`, server-snapshot reducer, offline grace state, surface-aware `MonetizationPolicy`, and compile-time `community`/`play` variants. The community variant has no billing, consent, or ad SDK and is always ad-free. The Play variant now integrates Google Play Billing 9.1.0, Google User Messaging Platform 4.0.0, and Google Mobile Ads 25.4.0 through `playImplementation` only.

The Play runtime queries a one-time product, restores purchases, acknowledges completed purchases, requests consent information on launch, exposes required privacy options, and requests one anchored adaptive banner only when the centralized policy permits it. Debug builds use Google's published sample AdMob application/banner identifiers and expose an explicit test-configuration label. The Settings sheet exposes purchase, restore, privacy, and public community-source actions. Community builds compile against no Google billing/ad/consent class or manifest entry.

Production remains deliberately fail-closed. The repository has no authenticated entitlement backend, final Play product, production AdMob identifiers, published UMP consent messages, or production Google OAuth client. A non-debug Play build does not grant durable ad-free entitlement merely because a purchase was acknowledged; it remains in `Verifying` until server verification exists. Trial recovery prevention and refund/revocation handling likewise remain unimplemented because they require durable server evidence. The reducer does not use a resettable local install timestamp as entitlement truth.

The sample identifiers are development inputs, never release credentials. A release must supply `xtraordinaryPlayAdFreeProductId`, `xtraordinaryAdMobAppId`, `xtraordinaryAdMobBannerId`, and `xtraordinaryEntitlementEndpoint`, and must fail release acceptance until the endpoint is actually consumed for purchase-token and entitlement verification.

## Recommended product model

Use a **seven-day, no-card, ad-free trial** followed by an ad-supported app and an optional **one-time lifetime ad-free purchase**. The core companion remains free and fully functional. This matches the requested transition to ads without turning basic device ownership into a subscription.

Do not use interstitial, app-open, or rewarded ads. Use one anchored adaptive banner in stable browsing surfaces only, above the bottom navigation. Never place an ad in setup, pairing, device discovery, USB/Bluetooth transfer, firmware install/reset, Google authorization, a destructive confirmation, or an error/recovery flow. Google describes anchored adaptive banners as the responsive format intended to stay at the top or bottom of an interactive layout; interstitials are intended for natural transition points, which this hardware companion does not have. During development, use only Google's test ad unit. Sources: [anchored adaptive banners](https://developers.google.com/admob/android/banner), [interstitial guidance](https://developers.google.com/admob/android/interstitial).

Create two compile-time distributions through one centralized capability:

- `play`: billing, consent, and ads are available; entitlement decides whether an ad slot is shown.
- `community`: billing and ad SDKs are absent, and the build is always ad-free.

UI code must ask a single `MonetizationPolicy`/`EntitlementState`; it must not scatter flavor checks, dates, or product IDs across screens.

## Upgrade hierarchy and copy

These are binding product acceptance rules, not optional tone suggestions. The shop voice is direct and self-assured. It sells a concrete outcome; it never asks users to “support us,” invokes development effort, uses guilt, artificial urgency, fake scarcity, countdown pressure, or repeated interruptions. The expiry notice appears once when the state changes and remains available quietly from Settings afterward.

The shop is a compact product sheet, not a donation appeal:

- Title: **Xtraordinary Ad-Free**
- Value line: **A quieter companion, everywhere you use it.**
- Benefits: **No banner ads**, **One-time purchase**, and **Restores on your Play account**
- Price: the localized Play price beside **One-time purchase**, with no struck-through fake price or discount badge
- Primary action: **Buy ad-free**
- Secondary actions: **Restore purchase** and **Continue with free**

Do not add a founder note, heart icon, tip-jar language, “help keep development going,” “buy us a coffee,” or any explanation of project costs. The product should be worth buying on its own merits.

The expired-trial notice is informational, dismissible, and secondary to the current task. It should say:

> Your 7-day ad-free trial has ended. Xtraordinary stays fully functional. The free version now includes a banner on browsing screens.

Primary action: **Go ad-free**. Secondary action: **Keep using free**.

The purchase sheet must show the localized Play price and the words **One-time purchase** before the action. It also includes a quiet open-source alternative:

> Prefer the open-source route? You can compile the ad-free community build from GitHub.

Actions: **Buy ad-free**, **Restore purchase**, **Continue with free**, and a GitHub link. The GitHub option must not be hidden behind euphemistic copy or presented as a warning. Do not use donation language or put a “Maybe later” action in a recurring loop.

## Entitlement state machine

```text
Unknown -> TrialActive -> AdSupported
    |           |             |
    +-----------+-------------+-> Purchased

Any network-dependent state -> UnknownOffline
Purchased + verified revocation/refund -> AdSupported
```

The UI consumes these states:

- `TrialActive(endsAt, remainingDays)`: no ads; optional remaining-time text in Settings.
- `AdSupported`: test/live banner may load after consent policy resolves.
- `Purchased`: no ads; restore status available in Settings.
- `UnknownOffline(lastVerifiedState)`: retain the last verified paid entitlement for a bounded grace period; never show a blocking paywall.

All time comparisons use an injected `EntitlementClock` and server timestamps. Production code must not expose a hidden UI that rewrites entitlement storage. Debug tests may advance a fake clock.

## Why local storage cannot meet the recovery requirement

A timestamp in SharedPreferences, DataStore, a Room database, Drive `appDataFolder`, or an Android backup can be cleared or replaced. It cannot guarantee that reinstalling does not restart a trial. Google likewise recommends moving sensitive entitlement logic and purchase verification to a backend and verifying purchase tokens with the Google Play Developer API. Source: [Play Billing security](https://developer.android.com/google/play/billing/security).

For the recommended one-time-purchase model, production needs a minimal entitlement service that records trial start/end against a privacy-reviewed authenticated identity and verifies Play purchase tokens. Clearing local app data then causes the same identity to recover the server state instead of receiving a new trial. This guarantee is identity-scoped; preventing trials across entirely different accounts would require invasive device fingerprinting and is not proposed.

An alternative is a Play subscription offer, where Play evaluates new-customer trial eligibility and may detect attempts to circumvent it. That creates an auto-renewing subscription, requires explicit duration/price/cancellation disclosure, and is a materially different product. Sources: [subscription offers and eligibility](https://support.google.com/googleplay/android-developer/answer/12154973), [subscription disclosure policy](https://support.google.com/googleplay/android-developer/answer/9900533).

## Privacy and ad consent

Ad loading must wait until the consent policy resolves. In the EEA and UK, Google requires a certified consent platform such as its User Messaging Platform for applicable personalized/non-personalized ad serving. Refusal must not disable the companion; use the permitted limited/technical mode or show no ad until eligible. Source: [AdMob ad-serving modes](https://developers.google.com/admob/android/privacy/ad-serving-modes).

No reading history, book metadata, Bluetooth identifier, firmware state, Drive identity, pass, Focus session, or device telemetry becomes an ad-targeting signal.

## Acceptance matrix

1. Fresh eligible identity: server records one trial start and app renders `TrialActive` with no ad request.
2. Fake clock advances six days: trial remains active with the correct remaining duration.
3. Fake clock crosses seven full days: state becomes `AdSupported`; the official AdMob test banner appears only on allowed app surfaces.
4. Clear local app data and reinstall with the same identity: server still returns `AdSupported`; no new trial is created.
5. Go offline after expiry: core functions remain available; no blocking upgrade flow appears.
6. Complete a Play license-test purchase in a debug/license-test environment: Play acknowledgement succeeds and the debug state becomes `Purchased`; the banner is removed immediately. Production acceptance separately requires backend token verification before `Purchased`.
7. Reinstall and restore: `Purchased` returns after verification.
8. Refund/revoke in a license-test environment: after verified revocation, state returns to `AdSupported`.
9. Community build: no billing/ad/consent SDK classes or manifest entries exist, the app is ad-free, and the same core regression suite passes.
10. Setup, pairing, discovery, flash/reset, transfer, Google backup, and all recovery/error screens never contain or request an ad.

## Required decisions and external configuration

Implementation cannot truthfully complete until these are decided or supplied:

- Confirm the recommended seven-day trial and one-time lifetime purchase, or explicitly choose an auto-renewing subscription.
- Configure the Android OAuth client and the identity/entitlement backend if the one-time model is chosen.
- Create the Play product and license-test accounts.
- Register the AdMob app/banner unit and UMP consent messages. Development must use Google's sample IDs until then.
- Set the public GitHub repository URL used by the upgrade sheet.
