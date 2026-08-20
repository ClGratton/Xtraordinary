# Legal and attribution release audit

Status: **not release-cleared**. Last audited 20 August 2026. This is an engineering inventory, not legal advice.

## Completed in product/source

- Root Xtraordinary MIT license is present.
- CrossPoint fork and Open X4 SDK retain their MIT license files and upstream identity.
- Settings exposes Privacy, Terms, and Open-source notices without forcing setup replay.
- Privacy now describes local pass/photo processing, optional Drive app-data backup, optional live-flight proxy traffic, Open Library, GitHub/firmware-host requests, Bluetooth storage, and the absence of current ads, billing, analytics, and remote diagnostics.
- Terms state independent/unendorsed status, authorized-use limits, pass/flight limitations, firmware risk, and Google-backup authorization.
- `THIRD_PARTY_NOTICES.md` inventories direct Android, firmware, font, artwork, and trademark surfaces.
- The complete Ubuntu Font Licence 1.0 is stored beside the redistributed Ubuntu fonts.
- Vendored uzlib is identified as version 2.9.8 and has a normalized complete zlib-style license file beside its source.
- `docs/firmware-brand-release-rights.md` separately inventories Xtraordinary, CrossPoint, CrossInk, Open X4, XTEINK trademark/device representation, and the pinned OEM-binary path; open-source licensing is not treated as trademark or OEM permission.

## Blocking before public distribution

| Area | Current evidence | Required closeout |
| --- | --- | --- |
| Exact Android dependency notices | Direct Gradle dependencies inventoried | Generate and inspect the exact release variant's full resolved/transitive graph; bundle every required license/NOTICE text; verify current `META-INF` exclusions do not discard mandatory notices |
| Firmware release pack | Direct PlatformIO and vendored libraries inventoried from the current environment | Copy version-pinned full license/NOTICE texts into a tracked release-notices directory and generate a manifest/hash list from the canonical firmware environment |
| ArduinoWebSockets LGPL-2.1 | License confirmed in `.pio/libdeps/.../WebSockets/LICENSE`; firmware links embedded code statically | Obtain a compliance decision and ship source/relinkable material and written offer as required, or replace it with a compatible permissive implementation |
| Generated art | Production and concept PNGs are in source | Record generator/account, prompt/source ownership, output date, and the commercial-output terms that applied; keep a human approval record |
| XTEINK name/logo/product imagery | App and docs use XTEINK text; source includes X3 outlines and compatibility claims | Get written permission where logo/product imagery is used, or remove those assets; have counsel approve nominative compatibility wording and store listing |
| OEM firmware distribution | App can download a pinned OEM image from XTEINK's host | Confirm permission to deep-link, download, verify, and install the OEM image; otherwise change the product to open the official recovery route instead of redistributing/accessing it in-app |
| CrossPoint fork obligations | Source fork is public and MIT notice retained | Publish exact corresponding source for every distributed firmware binary and clearly identify modifications/upstream commit in the release notes |
| Legal identity/jurisdiction | Drafts still name only the maintainer and GitHub support | Add final legal publisher identity, direct privacy contact, governing/consumer terms, age/territory requirements, and verified public policy URLs |
| Google/flight/store production terms | Development behavior is documented | Complete OAuth verification/branding, flight-provider/proxy contract, Play billing/ads/UMP terms if those SDKs are ever added, and update consent/policies before enablement |
| Scanner and travel claims | UI warns that passes are conveniences | Avoid promising issuer/airport acceptance until real scanner acceptance is recorded; keep delay/status source and freshness visible when live data is used |

## Release gate

A release manager must not mark this audit complete merely because the repository has license files. Close each blocking row with a dated evidence link, exact artifact/version, reviewer identity, and any required written permission. Terms and Privacy must be rechecked against the exact enabled build flags and network endpoints for every public release.
