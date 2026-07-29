# ADR-0001: Open-source repository and paid Google Play distribution

**Status:** Accepted
**Date:** 2026-07-26
**Deciders:** ClGratton

## Context

Xtraordinary contains an Android companion and an X3-specific CrossPoint
firmware fork. The source should remain inspectable and buildable, while the
Google Play build needs a small, sustainable revenue path. The Android app
also polls GitHub Releases for signed firmware, so GitHub Releases cannot be
disabled globally.

The project must not create an artificial security boundary in client code.
Anyone able to rebuild the Android app can also remove a client-side feature
gate. Shipping a weakened or debug APK would add risk without preventing that.

Current platform constraints:

- Google Play Billing is required for an in-app purchase that unlocks Android
  app functionality.
- A non-consumable Play purchase can be restored after reinstall through the
  purchasing Play account. The backend verifies and acknowledges the purchase.
- Rewarded ads must be explicitly chosen and must state a concrete in-app
  reward. Reward prompts must not say that watching an ad supports the
  developer.
- A seven-day, non-renewing welcome period is not a Play Billing product.
  Android Auto Backup preserves it across ordinary reinstalls when backup is
  available. Preventing deliberate reinstall abuse without account sign-in
  requires Play Integrity device recall, which is currently beta-only.
- Firmware installation is setup and recovery infrastructure. It must remain
  available without payment or advertising.

## Decision

1. Keep the repository source-buildable under its applicable licenses.
2. Publish no Android APK in GitHub Releases. Source tags remain available.
3. Continue publishing signed, model-specific firmware assets in GitHub
   Releases because the app's update flow depends on them.
4. Produce two Android distribution flavors:
   - `community`: source-build distribution, separate application ID, all
     local and connected features available, no Play Billing or advertising.
   - `play`: official Play-signed distribution with automatic updates,
     seven welcome days, rewarded access, and a one-time Pro product.
5. Use one non-consumable Play product, `xtraordinary_pro`. Display the
   localized price returned by Play rather than hardcoding currency text.
6. After the welcome period, one explicitly chosen rewarded ad grants 24 hours
   of connected companion actions. The prompt states that exact reward.
   Starting a phone-only Focus timer remains available without an ad.
7. Never gate pairing, first-time firmware installation, firmware recovery,
   local library browsing, local EPUB import, or deletion of user data.
8. Verify Pro purchase tokens on the backend with the Google Play Developer
   API, grant only `PURCHASED` products matching the expected package and
   product ID, and acknowledge unacknowledged purchases within three days.
9. Restore local welcome/reward state through Android Auto Backup. Treat this
   as reinstall resilience, not fraud-proof DRM. Revisit Play Integrity device
   recall only after beta access is granted and measured abuse justifies it.

## Options considered

### GitHub debug APK

| Dimension | Assessment |
|---|---|
| Complexity | Low |
| Security | Poor |
| User trust | Poor |
| Revenue protection | Negligible |

Debuggable binaries expose development behavior and still do not stop a
motivated user from rebuilding. Rejected.

### No GitHub Android APK, paid convenience build on Play

| Dimension | Assessment |
|---|---|
| Complexity | Medium |
| Security | Good |
| User trust | Good |
| Revenue protection | Appropriate for a niche open-source app |

This follows the practical model used by projects such as DAVx5: source
remains available while the store build charges for convenient, signed,
automatically updated distribution. Accepted.

### Closed or crippled community source flavor

| Dimension | Assessment |
|---|---|
| Complexity | High |
| Security | Poor |
| User trust | Poor |
| Revenue protection | Negligible |

Feature gates in published client source are easy to patch and would conflict
with the project's open-source purpose. Rejected.

### Subscription

| Dimension | Assessment |
|---|---|
| Complexity | Medium |
| Sustainable revenue | High |
| Fit for current scope | Low |

Subscriptions can sustain long-lived apps, but the current local-first,
single-device feature set has no ongoing hosted service that justifies a
recurring charge. Revisit if cloud sync or maintained data services ship.

## Licensing and marks

- Repository-level original Android/protocol work remains under the root MIT
  license.
- `firmware/` retains the original CrossPoint MIT notice and Dave Allie's
  copyright.
- `firmware/open-x4-sdk/` retains the Open X4 E-Paper Contributors MIT notice.
- Binary distributions must reproduce all three notices.
- XTEINK names are used only to identify compatible hardware. Xtraordinary is
  not affiliated with or endorsed by XTEINK or CrossPoint.
- Do not reuse XTEINK's corporate logo or imply that XTEINK published the app.
- The simulated reader frame uses the Xtraordinary name, not the XTEINK
  wordmark. Compatibility references remain plain text and the app includes a
  visible non-affiliation notice.
  The in-app wordmark may identify the physical device shown in a preview.

## Consequences

- The Play build provides the path intended for ordinary users and funds
  maintenance without pretending source compilation can be prevented.
- GitHub firmware downloads remain available, but there is no competing
  one-click Android install.
- Community and Play builds use different application IDs and signatures, so
  they can coexist and cannot silently replace each other.
- The one-week welcome period survives normal backup/restore but is not a hard
  anti-abuse boundary until Play Integrity device recall becomes available.
- Ad and consent SDKs are isolated to the Play flavor.

## Action items

1. [ ] Add `community` and `play` Android product flavors.
2. [ ] Add the Play Billing non-consumable entitlement flow and backend verifier.
3. [ ] Add the 24-hour rewarded-access flow and EU consent handling.
4. [ ] Add Play Console product, AdMob app/ad unit, privacy message, Data safety,
       and internal-test configuration.
5. [ ] Add automated checks preventing sample ad IDs or an empty verifier URL
       in Play release builds.
6. [ ] Reassess price and conversion after the first real usage cohort.
