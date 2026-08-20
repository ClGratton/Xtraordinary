# Firmware release notices

This directory is the tracked notice payload for Xtraordinary firmware artifacts. `MANIFEST.sha256` binds every included full licence or NOTICE file. Run `scripts/check-firmware-release-notices.ps1` to verify the pack, or pass `-Update` only when deliberately changing the reviewed contents.

Pinned build inputs represented here:

- Xtraordinary and CrossPoint-derived firmware: MIT;
- CrossInk: MIT, exact upstream `main` licence retrieved 20 August 2026;
- Open X4 SDK: MIT;
- ArduinoJson 7.4.2 and QRCode 0.0.1: MIT;
- PNGdec 1.1.6 and JPEGDEC `86282979224c8a32fd51e091ed5a35b0c699a52b`: Apache-2.0;
- ArduinoWebSockets 2.7.3: LGPL-2.1, with its bundled libb64 notice;
- SdFat 2.3.1: MIT;
- NimBLE-Arduino 2.5.0, Apache NimBLE, and TinyCrypt: bundled licence and NOTICE texts;
- Expat 2.7.3: MIT, exact upstream COPYING text retrieved 20 August 2026; its vendored `siphash.h` is accompanied by the complete CC0-1.0 legal text;
- uzlib 2.9.8: zlib-style licence;
- Noto Sans, Noto Sans Hebrew, Noto Serif, and OpenDyslexic: OFL-1.1;
- Ubuntu fonts: Ubuntu Font Licence 1.0.

This pack satisfies the repository-side requirement to ship complete texts. It does not by itself resolve LGPL static-link compliance, XTEINK trademark/product-art permission, or permission to automate retrieval and installation of the XTEINK OEM image. Those remain separate release blockers in `docs/legal-release-audit.md` and `docs/firmware-brand-release-rights.md`.
