# Legal and attribution release audit

Status: **not release-cleared**. Last audited 20 August 2026. This is an engineering inventory, not legal advice.

## Completed in product/source

- Root Xtraordinary MIT license is present.
- CrossPoint fork and Open X4 SDK retain their MIT license files and upstream identity.
- Settings exposes Privacy, Terms, and Open-source notices without forcing setup replay.
- Privacy now describes local pass/photo processing, optional Drive app-data backup, optional live-flight proxy traffic, Open Library, GitHub/firmware-host requests, Bluetooth storage, the Play-only Billing/UMP/Mobile Ads boundary, and the absence of app analytics and remote diagnostics.
- Terms state independent/unendorsed status, authorized-use limits, pass/flight limitations, firmware risk, and Google-backup authorization.
- `THIRD_PARTY_NOTICES.md` inventories direct Android, firmware, font, artwork, and trademark surfaces.
- The complete Ubuntu Font Licence 1.0 is stored beside the redistributed Ubuntu fonts.
- Vendored uzlib is identified as version 2.9.8 and has a normalized complete zlib-style license file beside its source.
- `docs/firmware-brand-release-rights.md` separately inventories Xtraordinary, CrossPoint, CrossInk, Open X4, XTEINK trademark/device representation, and the pinned OEM-binary path; open-source licensing is not treated as trademark or OEM permission.
- `release-notices/firmware` now tracks 25 complete licence/NOTICE files for the pinned Xtraordinary/CrossPoint/CrossInk/Open X4 firmware stack, embedded libraries, and fonts. Its SHA-256 manifest is verified by `scripts/check-firmware-release-notices.ps1`, which the engineering gate runs before any compiler.
- `release-notices/android` now binds the exact 147-module Community and 180-module Play release runtime graphs, resolved POM licence metadata, 38 exact-version overrides for undeclared POMs, complete Apache-2.0 and Checker Framework MIT texts, and official Google component terms pointers. Its manifest SHA-256 is `3BD61CB44B42F5FC16DB37B93486D876E19410E1DF8622215B94D45ECC21BC37`; the Android engineering gate re-resolves both graphs and rejects stale inventories, missing overrides, or a changed pack before an Android compiler. Firmware-only builds verify the independent firmware pack without paying for Android resolution.
- Android packaging no longer deletes every `META-INF/AL2.0` and `META-INF/LGPL2.1` entry. One duplicate is preserved and the complete tracked Android/firmware notice trees are packaged as app assets; every assembled APK is checked byte-for-byte against the source packs.
- `docs/artwork-provenance.tsv` now binds all nine production/concept PNGs to dimensions, hashes, introducing commits, dates, and Git authors. The engineering gate rejects unregistered/drifting art. The missing account/prompt/terms/approval fields remain explicit rather than being guessed.
- Release Android variants now fail closed on the pinned XTEINK OEM firmware route unless `xtraordinaryXteinkOemPermissionAcknowledged=true` is deliberately supplied. Debug maintenance builds retain recovery access; the checksum and the permission decision remain separate.
- Ordinary app UI no longer uses decorative XTEINK text: setup, status, discovery, library, and Focus preview use `X3`/`X3 reader`. The name remains only in legal compatibility attribution, the explicitly selected OEM source, and the existing Bluetooth/protocol identity that cannot be renamed without a migration.
- Every canonical X3 firmware build now emits `xtraordinary-release-record.json` beside the binary, binding version, exact Xtraordinary commit/source URL, CrossPoint `1.4.1` baseline commit, binary size/SHA-256, notice-manifest SHA-256, modification documents, and unresolved LGPL/reviewer obligations.

## Blocking before public distribution

| Area | Current evidence | Required closeout |
| --- | --- | --- |
| Exact Android dependency notices | Exact release graphs, POM hashes, 38 overrides, full current open-source texts, Google terms register, manifest, packaging preservation, and APK byte verifier are implemented | Have the release reviewer inspect the generated pack and exact signed APK/AAB for component-specific NOTICE requirements and accept the current proprietary Google agreements; regenerate after any graph drift |
| Firmware artifact/source binding | Canonical builds now generate a source/binary/notices/upstream-bound JSON record automatically | Attach that generated record and the corresponding source to every public binary; add final reviewer/date approval and regenerate for every artifact |
| ArduinoWebSockets LGPL-2.1 | The current `x3_companion_release/firmware.map` includes `WebSockets.cpp.o` and `WebSocketsServer.cpp.o`; this is confirmed static-link evidence, not a hypothetical dependency | Obtain a compliance decision and ship source/relinkable material and written offer as required, or replace it with a compatible permissive implementation |
| Generated art | All nine production/concept PNGs now have a hash-checked Git provenance inventory | Replace every `MISSING` account, prompt/source, commercial-terms, and human-approval field with dated evidence; Git authorship alone does not prove output rights |
| XTEINK name/logo/product imagery | Decorative/ordinary UI branding is removed; legal compatibility text, the OEM source label, and technically necessary Bluetooth/protocol identity remain | Confirm the narrowed nominative compatibility/store wording and OEM label; do not add logos or product imagery without written permission |
| OEM firmware distribution | Debug maintenance can use the checksum-pinned XTEINK-hosted image; release builds now fail closed unless an explicit permission property is supplied | Obtain and archive written permission before enabling the property for a public artifact; otherwise keep it disabled and provide only the official recovery route |
| CrossPoint fork obligations | Source fork is public and MIT notice retained | Publish exact corresponding source for every distributed firmware binary and clearly identify modifications/upstream commit in the release notes |
| Legal identity/jurisdiction | Drafts still name only the maintainer and GitHub support | Add final legal publisher identity, direct privacy contact, governing/consumer terms, age/territory requirements, and verified public policy URLs |
| Google/flight/store production terms | Play-only Billing 9.1.0, Mobile Ads 25.4.0 and UMP 4.0.0 development integration is inventoried; Community remains SDK-free | Complete OAuth verification/branding, flight-provider/proxy contract, Play product and license-test setup, production AdMob identifiers and consent messages, authenticated purchase-token verification, refund/revocation handling, Data safety declarations, and final Google terms review before release |
| Scanner and travel claims | UI warns that passes are conveniences | Avoid promising issuer/airport acceptance until real scanner acceptance is recorded; keep delay/status source and freshness visible when live data is used |

## Release gate

A release manager must not mark this audit complete merely because the repository has license files. Close each blocking row with a dated evidence link, exact artifact/version, reviewer identity, and any required written permission. Terms and Privacy must be rechecked against the exact enabled build flags and network endpoints for every public release.
