# Firmware distribution and brand-rights matrix

Status: **release-blocking evidence register**. Last checked 20 August 2026. This is an engineering inventory, not legal advice.

This matrix is required because an open-source firmware license, permission to use a trademark, and permission to fetch or install an OEM binary are separate grants. Passing one row never closes another.

| Surface | What Xtraordinary currently does | License/right evidence | Release requirement |
| --- | --- | --- | --- |
| Xtraordinary firmware | Builds and distributes a modified CrossPoint-derived X3 binary | Repository root and `firmware/LICENSE` retain MIT terms; latest firmware-changing source commit is recoverable from Git history | Every binary release must name its exact source commit/tag, include the MIT notice, publish the corresponding source and modifications, and bind binary/source with size and SHA-256 |
| CrossPoint picker | Resolves the latest `crosspoint-reader/crosspoint-reader` GitHub release, downloads its `firmware.bin`, verifies GitHub's SHA-256 digest, and flashes it | Upstream repository declares MIT and keeps `LICENSE`: https://github.com/crosspoint-reader/crosspoint-reader | The app notices and release pack must retain the upstream copyright/license. Record the selected tag, asset digest, and upstream source URL for every accepted binary. Do not imply endorsement |
| CrossInk picker | Resolves the latest `uxjulia/CrossInk` GitHub release, downloads a matching X3 binary, verifies GitHub's SHA-256 digest, and flashes it | CrossInk is a CrossPoint fork; its current `LICENSE` is MIT and retains Dave Allie's 2025 copyright: https://github.com/uxjulia/CrossInk/blob/main/LICENSE | Retain the CrossInk and inherited CrossPoint notices, record tag/digest/source URL, and do not imply endorsement. Recheck the selected release's bundled font/assets notices before public distribution |
| Open X4 SDK | Vendors the hardware SDK used by the fork | `firmware/open-x4-sdk/LICENSE` is MIT | Keep its notice in the firmware source and generated release-notices pack |
| XTEINK word mark and device representation | Ordinary setup, device status, discovery, library, and Focus-preview UI now says only `X3`/`X3 reader`; the decorative `XTEINK` frame text was removed. The mark remains only where needed to identify compatibility/legal attribution, the explicitly selected OEM firmware source, and the existing Bluetooth advertising name/UUID identifiers that cannot be renamed without a pairing/protocol migration | `LegalDocuments.kt` identifies XTEINK as its owner's trademark and states that Xtraordinary is independent and unendorsed. No repository-owned source file establishes a logo or product-image licence | Review the narrower store compatibility wording, legal attribution, OEM source label, and Bluetooth name. Do not reintroduce decorative branding or upstream logos. If permission is not obtained, retain only counsel-approved nominative compatibility text and the technically necessary protocol identity |
| XTEINK OEM firmware | Pins `XT V5.1.6 EN` to an XTEINK-hosted CDN object, downloads it into app cache, verifies fixed size/SHA-256 and flashes it in private debug maintenance builds. Release builds now reject the source unless `xtraordinaryXteinkOemPermissionAcknowledged=true` is explicitly supplied | A public download URL, checksum, debug recovery access, and build property are not evidence of permission to redistribute, deep-link, automate download, or install | Obtain and archive written permission before enabling the property for a public artifact. Without it, keep the release route disabled and provide only an official recovery link/instructions. Keep the fixed checksum for maintenance verification, not as a rights claim |
| Xtraordinary/CrossPoint/CrossInk names and artwork | Presents source choices in one firmware picker | Open-source licences cover copyrighted source, not trademark endorsement | Use plain source names only to identify compatibility; preserve independent/unendorsed wording and avoid upstream logos unless separately licensed |

## Evidence required for a firmware release

For every distributed or installable firmware artifact, record all of the following together:

1. provider and user-facing source label;
2. exact release tag and source commit;
3. immutable source URL and binary URL;
4. binary size and SHA-256;
5. complete licence/NOTICE bundle and its manifest hash;
6. corresponding-source URL for Xtraordinary binaries;
7. modification summary relative to CrossPoint;
8. written-permission evidence where the provider is XTEINK/OEM rather than an open-source project;
9. reviewer/date and whether the artifact was only downloaded, flashed for private maintenance, or approved for public distribution.

The Android firmware picker is not itself permission evidence. `FirmwareReleaseRepository.kt` proves the current technical download and checksum path; it does not close trademark, OEM-binary, or store-listing rights.

Canonical Xtraordinary builds write `xtraordinary-release-record.json` beside `firmware.bin`. That generated record binds the artifact to the exact repository commit, corresponding-source URL, CrossPoint baseline `2754a5ff01644d36cf0a17db98f28408666ba518`, notice-manifest hash, and modification documents. It deliberately retains unresolved LGPL and final-review fields; generation is not legal approval.
