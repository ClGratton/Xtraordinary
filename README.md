<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/assets/xtraordinary-logo-dark.svg">
    <img src="firmware/src/images/logo.svg" width="90" alt="Xtraordinary logo">
  </picture>
</p>

<h1 align="center">Xtraordinary</h1>

Android companion prototype and an XTEINK X3-focused CrossPoint firmware research fork.

The product model is simple: the phone owns integrations and intelligence; XTEINK is the persistent, low-power display and physical control surface on the back of the phone. The app contains Focus, Read, and Tools surfaces, persistent folder-based EPUB sync, a reconciled phone/device library, cached metadata enrichment, and the shared magnetic interaction system. The X3 companion build now adds encrypted BLE commands, a device-owned focus countdown, authoritative SD-card inventory/delete operations, and streaming inactive-slot firmware updates.

- [Architecture and implementation plan](docs/architecture-plan.md)
- [Exact CrossPoint X3 change plan](docs/crosspoint-change-plan.md)
- [Phone and X3 interface hierarchy](docs/interface-hierarchy.md)
- [Confirmed Material 3 Expressive design system](DESIGN.md)
- [Project-local build environment](docs/build-environment.md)
- `firmware/` is a CrossPoint Reader `1.4.1` research fork plus an isolated `x3_companion` build. Normal CrossPoint build targets remain unchanged.
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

The repository intentionally contains no production Gemini key, Firebase project, or real boarding-pass barcode. The companion transport is currently enabled only in the X3-specific build; X4/X4 Pro firmware assets are not published until they have their own hardware validation.

## Build the X3 companion firmware

```powershell
. .\scripts\use-toolchains.ps1
Set-Location firmware
pio run -e x3_companion
```

Tagged `xtraordinary-v*` builds publish `xtraordinary-x3.bin` and a model-explicit, SHA-256 manifest. The Android app polls the latest GitHub Release, never guesses an asset by filename, and requires the X3 center button to be held when a firmware transfer begins.
