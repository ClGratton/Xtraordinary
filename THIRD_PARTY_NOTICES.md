# Third-party notices

This inventory accompanies Xtraordinary source and release artifacts. It records direct dependencies and redistributed assets observed on 20 August 2026. A release pack must include the corresponding full license/NOTICE texts; links alone are not a substitute where a license requires reproduction.

## Android application

| Component | Use | License / terms | Source |
| --- | --- | --- | --- |
| AndroidX Core, Activity, Lifecycle, Compose, Material 3 | Android UI/runtime | Apache-2.0 | https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin | Language/runtime tooling | Apache-2.0 | https://github.com/JetBrains/kotlin |
| ZXing Core 3.5.3 | Barcode generation/decoding | Apache-2.0 | https://github.com/zxing/zxing |
| Google ML Kit text recognition 16.0.1 and barcode scanning 17.3.0 | On-device pass-photo extraction | Google APIs Terms plus notices in the shipped SDK | https://developers.google.com/ml-kit/terms |
| Google Play services Auth 21.6.0 | Optional Google authorization | Google APIs Terms plus notices in the shipped SDK | https://developers.google.com/terms |
| Google Play Billing 9.1.0 (Play distribution only) | One-time ad-free purchase and restore | Google Play SDK terms and Play Billing requirements | https://developer.android.com/google/play/billing |
| Google Mobile Ads 25.4.0 (Play distribution only) | Anchored adaptive banner on policy-approved browsing surfaces | Google Mobile Ads SDK terms | https://developers.google.com/admob/android/terms |
| Google User Messaging Platform 4.0.0 (Play distribution only) | Advertising consent and privacy options before eligible ad requests | Google UMP / EU user consent policy | https://developers.google.com/admob/android/privacy |
| JUnit 4.13.2 | Tests only | EPL-1.0 | https://github.com/junit-team/junit4 |

The canonical release pack must also inventory transitive AAR/JAR dependencies from the exact resolved Gradle graph and preserve every packaged `META-INF` notice before changing packaging exclusions.

## Reader firmware and embedded libraries

| Component | Pinned input | License / notice |
| --- | --- | --- |
| CrossPoint Reader fork | baseline `2754a5ff01644d36cf0a17db98f28408666ba518` plus Xtraordinary commits | MIT; `firmware/LICENSE` |
| CrossPoint release picker | separately published upstream binary selected by the user | MIT; retain upstream release tag, digest, source link, copyright and licence |
| CrossInk release picker | separately published CrossPoint-derived binary selected by the user | MIT; retain CrossInk and inherited CrossPoint notices plus release tag, digest and source link |
| Open X4 SDK | vendored under `firmware/open-x4-sdk` | MIT; `firmware/open-x4-sdk/LICENSE` |
| ArduinoJson 7.4.2 | PlatformIO dependency | MIT |
| QRCode 0.0.1, Richard Moore | PlatformIO dependency | MIT |
| PNGdec 1.1.6 | PlatformIO dependency | Apache-2.0 |
| JPEGDEC `86282979224c8a32fd51e091ed5a35b0c699a52b` | Git dependency | Apache-2.0 |
| ArduinoWebSockets 2.7.3 | PlatformIO dependency | LGPL-2.1; includes libb64 notice |
| SdFat | transitive SDK dependency | MIT |
| NimBLE-Arduino 2.5.0 and Apache NimBLE | companion builds | Apache-2.0 plus NOTICE and bundled component notices |
| Expat | vendored under `firmware/lib/expat` | MIT; `siphash.h` is CC0 |
| uzlib 2.9.8 | vendored under `firmware/lib/uzlib` | zlib-style terms; complete normalized text in `firmware/lib/uzlib/LICENSE.txt` |

The embedded static use of LGPL-2.1 ArduinoWebSockets is a release blocker until counsel-approved compliance is implemented (for example, the required source/relinkable-material offer) or the dependency is replaced with a compatible permissive implementation. Do not describe the firmware binary as release-ready while this remains open.

The complete firmware-side texts are tracked in `release-notices/firmware`. `MANIFEST.sha256` binds the 25 files, including the full CC0-1.0 legal text for Expat's vendored `siphash.h`; `scripts/check-firmware-release-notices.ps1` verifies the inventory and hashes before the engineering policy gate permits a compiler to start.

## Fonts and artwork

| Asset | License / provenance state |
| --- | --- |
| Noto Sans, Noto Serif, Noto Sans Hebrew | SIL OFL-1.1; local `OFL.txt` files are present |
| OpenDyslexic | SIL OFL-1.1; local `OFL.txt` is present |
| Ubuntu font files | Ubuntu Font Licence 1.0; complete text is vendored beside the fonts in `firmware/lib/EpdFont/builtinFonts/source/Ubuntu/ubuntu-font-licence-1.0.txt` |
| Lighthouse/sea, astronaut/space, and onboarding PNGs | Project-generated editorial assets; preserve source-generation/provenance records and confirm the generation service's commercial-output terms for the creating account |
| Code-drawn app icons and shapes | Project source under the repository MIT license; functional icon geometry must remain documented in the UI course/contract |

## Names and trademarks

XTEINK and third-party product/provider names belong to their owners. Xtraordinary uses them only to identify compatibility, selected firmware sources, and data providers. The project states that it is independent and not endorsed. Public store assets, product imagery, logo use, and naming still require a written permission/nominative-use decision recorded in `docs/legal-release-audit.md`.

The provider-by-provider distribution and rights evidence is maintained in `docs/firmware-brand-release-rights.md`. MIT-licensed CrossPoint/CrossInk source does not grant XTEINK trademark, product-image, OEM-firmware, or endorsement rights.
