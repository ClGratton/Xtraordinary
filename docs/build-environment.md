# Project-local build environment

The Android and firmware toolchains are installed under `.tools/` and intentionally excluded from Git. No global Android Studio, Gradle, Java, Python package, or PlatformIO installation is required.

## Installed toolchain contract

| Area | Tool | Project version/path |
|---|---|---|
| Android | Eclipse Temurin JDK | 17, `.tools/jdk-17` |
| Android | Android command-line tools | latest official Windows package, `.tools/android-sdk/cmdline-tools/latest` |
| Android | Compile platforms | Android API 36 and 37.1; the app compiles against 37.1 and targets 36 |
| Android | Build Tools | 36.0.0 |
| Android | Platform Tools / ADB | current package in `.tools/android-sdk/platform-tools` |
| Android | Gradle | 9.5.0, compatible with planned Android Gradle Plugin 9.3 |
| Firmware | Python virtual environment | `.tools/platformio-venv` |
| Firmware | PlatformIO and CrossPoint Python requirements | installed in the virtual environment |
| Firmware | ESP32-C3 framework, compiler, and Arduino libraries | `.tools/platformio-core`, resolved by the unmodified `firmware/platformio.ini` during the baseline build |

## First setup or repair

From the repository root in PowerShell:

```powershell
& .\scripts\setup-toolchains.ps1
```

The installer is repeatable. It verifies the Android command-line tools checksum, verifies Gradle against its published checksum, preserves completed downloads, and retries an incomplete Android SDK package without deleting the rest of the SDK.

To install tools without running the firmware baseline build:

```powershell
& .\scripts\setup-toolchains.ps1 -SkipFirmwareBuild
```

## Use in a new terminal

```powershell
. .\scripts\use-toolchains.ps1
```

That sets `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, the local PlatformIO cache, UTF-8 Python output, and project-scoped Git trust, then adds the local Java, Android, Gradle, ADB, and PlatformIO commands to the current PowerShell `PATH`.

Useful checks:

```powershell
java -version
gradle --version
adb version
pio --version
pio run --project-dir .\firmware
```

The Android app is built directly with the verified project-local Gradle distribution:

```powershell
gradle :protocol:test :app:lintDebug :app:validateDebugScreenshotTest :app:assembleDebug
```

The resulting APK is `app/build/outputs/apk/debug/app-debug.apk`. Compose reference images are updated only after visual review with `gradle :app:updateDebugScreenshotTest`.

## Verified baseline

Verified on 2026-07-22:

- Temurin JDK `17.0.19+10`
- Gradle `9.5.0`
- Android API `36` and `37.1`, Build Tools `36.0.0`, Platform Tools/ADB `37.0.0`
- PlatformIO Core `6.1.19`
- Android debug app compilation, screenshot rendering, lint, protocol tests, APK assembly, and Pixel 10 install: **success**
- CrossPoint `1.4.1-dev-master-2754a5f` full default build: **success**
- RAM: `101,196 / 327,680 bytes` (`30.9%`)
- Application flash: `5,225,983 / 6,553,600 bytes` (`79.7%`)
- Output: `firmware/.pio/build/default/firmware.bin`, `5,238,960 bytes`
- SHA-256: `4CD94AF91BFB2259183E2258D01B4500C9999F17408F72092EE58E5A195CBA76`

The baseline emits one upstream deprecation warning in `WebSocketsClient.cpp` (`NetworkClient::flush()`); it does not affect the successful link or image generation. The nested firmware repository remains source-clean after the build because generated files and `.pio/` outputs are ignored.
