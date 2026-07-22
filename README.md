# Xtraordinary

Android companion prototype and an XTEINK X3-focused CrossPoint firmware research fork.

The product model is simple: the phone owns integrations and intelligence; the X3 is the persistent, low-power display and physical control surface on the back of the phone. The prototype now contains Focus, Read, and Tools surfaces, persistent folder-based EPUB sync plus multi-file import, a merged phone/X3 library model, cached metadata enrichment, a resistance-haptic pass carousel, dynamic Expressive color plus deterministic grayscale Quiet, separate phone/X3 artwork crops, and a tested versioned protocol module. BLE transfer and firmware integration remain planned.

- [Architecture and implementation plan](docs/architecture-plan.md)
- [Exact CrossPoint X3 change plan](docs/crosspoint-change-plan.md)
- [Phone and X3 interface hierarchy](docs/interface-hierarchy.md)
- [Confirmed Material 3 Expressive design system](DESIGN.md)
- [Project-local build environment](docs/build-environment.md)
- `firmware/` is a research clone of CrossPoint Reader `master` at `2754a5f` (version field 1.4.1) and its hardware SDK. No firmware changes have been made yet.
- [Third-party firmware notices](THIRD_PARTY_NOTICES.md)

## Build the Android prototype

```powershell
& .\scripts\setup-toolchains.ps1 -SkipFirmwareBuild
. .\scripts\use-toolchains.ps1
gradle :protocol:test :app:lintDebug :app:validateDebugScreenshotTest :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected Android device with:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

The repository intentionally contains no production Gemini key, Firebase project, BLE implementation, real boarding-pass barcode, or modified CrossPoint firmware image.
