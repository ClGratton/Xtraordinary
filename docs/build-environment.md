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

That sets `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `ANDROID_USER_HOME`, the project-local Gradle and PlatformIO caches, UTF-8 Python output, and project-scoped Git trust, then adds the local Java, Android, Gradle, ADB, and PlatformIO commands to the current PowerShell `PATH`. `ANDROID_USER_HOME` deliberately points at the owner's existing `.android` directory so debug APK upgrades use the same certificate and preserve installed app data.

Run this bootstrap in the same PowerShell process before invoking `esptool.py` directly. Esptool's Unicode progress output can raise `UnicodeEncodeError` under the default Windows code page and interrupt a flash after the device has already entered the bootloader. Never retry with a different binary: enable the repository UTF-8 environment, rerun the exact artifact, and require both `Hash of data verified.` and exit code `0` before treating the flash as successful.

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
gradle --no-daemon :protocol:test :app:lintCommunityDebug :app:assembleCommunityDebug :app:lintPlayDebug :app:assemblePlayDebug
```

The canonical wrapper also runs `:app:validateDebugScreenshotTest`; a visual regression now blocks every normal Android build instead of remaining an optional follow-up. The resulting APKs are `app/build/outputs/apk/community/debug/app-community-debug.apk` and `app/build/outputs/apk/play/debug/app-play-debug.apk`. The installer defaults to the ad-free community artifact. Compose reference images are updated only after visual review through an explicit update task; updating a baseline is never treated as proof that the new layout is correct.

Install replacement APKs through the repository workflow:

```powershell
& .\scripts\install-xtraordinary-app.ps1
```

Do not use a raw `adb install -r` while the current app may own an X3 GATT session. Package replacement kills the old process; without an explicit disconnect/close/settle handshake, Android can retain a callback-less `bta_dm_disc_gatt` direct connection and falsely make the next process look unable to reach a strongly advertising X3. The install script requires `PERIPHERAL_RESET_READY`, force-stops only after that confirmation, retains app data, verifies Package Manager's installed version, and relaunches the app.

Firmware release builds are intentionally single-job. This avoids Windows path/compiler races and is the canonical command:

```powershell
$env:XTRAORDINARY_VERSION = "xtraordinary-v0.2.6-dev9-local"
pio run --project-dir .\firmware --environment x3_companion_release -j 1
```

`XTRAORDINARY_VERSION` is generated into the single `BuildVersion` library
object. It must not return to a global `-DCROSSPOINT_VERSION` build flag: that
flag invalidated every translation unit and made a version-only candidate take
7 minutes 19 seconds on this workstation. After the one-time migration,
changing only the candidate version completed the same canonical policy,
link, architecture, size, and image workflow in 54.34 seconds on 2026-08-12.
If a version-only build starts compiling Arduino, NimBLE, SdFat, or EPUB again,
treat it as a cache-invalidation regression rather than a normal small edit.

The Espressif 14.2 assembler resolves its own executable path at startup. In a restricted Codex filesystem sandbox it can panic with `Failed to get path name. Error code: 5` before compiling the first object. Confirm with the project assembler's `--version`; if it succeeds only outside the sandbox, run the same canonical PlatformIO command with filesystem permission. Do not clean caches, relink libraries, patch firmware, or start parallel builds for this access-denied signature.

## Safe X3 application flash

Use the repository workflow rather than opening COM7 manually:

```powershell
& .\scripts\flash-x3-companion.ps1 -Port COM7
```

The script refuses to start unless the firmware artifact, one ADB phone, and the requested serial port are present. It sends the debug app an explicit reset-preparation intent, requires confirmation that GATT was disconnected and closed, then stops the process. Only after that handshake does it write the application partition at `0x10000` (preserving NVS, pairing, and books), require esptool success, wait for boot, and relaunch the app. A deployment is not complete until the subsequent capabilities/status handshake succeeds.

Both canonical build wrappers run `scripts/assert-pushed-source.ps1` before the engineering-policy gate or compiler. A build requires a named branch, a clean working tree, a configured remote upstream, and exact equality between local HEAD, the remote-tracking HEAD, and a live `git ls-remote` result. Commit and push source before compiling; never use a generated binary as the only recovery point. `.codex-build`, `artifacts`, PlatformIO output, Gradle output, and the generated BuildVersion include are disposable and ignored.

After flashing, verify recurrence rather than accepting one connection:

```powershell
& .\scripts\verify-x3-reconnect.ps1 -Mode Background -Iterations 5 -TimeoutSeconds 20
& .\scripts\verify-x3-reconnect.ps1 -Mode ProcessDeath -Iterations 3 -TimeoutSeconds 20
```

Background mode exercises the real `onStop` path and its clean disconnect. Process-death mode force-stops the app but waits beyond the X3's ten-second BLE supervision timeout before reconnecting; a three-second force-stop loop is invalid because it can race the still-live peripheral connection. Neither mode deletes the bond or toggles Bluetooth. A pass requires the Android 17/API 37 GATT path, status-0 connection, notification subscription, and a decoded X3 capabilities packet. One pass is not release acceptance. Also run at least three controlled X3 reset/reconnect cycles through the safe flash/reset workflow before promoting firmware.

Do not open COM7 as a passive log probe. On this X3, even a Windows `SerialPort` open with DTR and RTS requested false caused `USB_UART_CHIP_RESET`. Treat every COM7 open as a possible peripheral reset. Use an intentional serial monitor only when reset is acceptable, and never run an automatic crash-log probe immediately after flashing.

When the X3 is attached to the Pixel, retrieve both the persistent crash file and retained runtime checkpoints through the debug app without opening desktop serial:

```powershell
& .\scripts\read-x3-diagnostics.ps1
```

The script uses the repository ADB toolchain, requires exactly one phone, and invokes the app's bounded, read-only USB diagnostics path. It does not reset the X3, clear app data, or alter the Bluetooth bond. Preserve the complete output: a CPU-lockup report without a panic string is incomplete unless its `RUNTIME_TRACE_PREVIOUS` checkpoint is also captured.

## Long-running command discipline

Builds, tests, installs, flashing, and device-log captures must never be left running blindly.

- Start every long-running command with a bounded wait appropriate to the expected operation.
- If it has not completed, inspect its new output, process state, CPU activity, and output-artifact timestamp before waiting again.
- A timeout or an existing artifact is not success. Record success only from the command's zero exit code and expected final output, then inspect the produced artifact.
- Never start a second firmware build while a timed-out PlatformIO process is still alive. Inspect and terminate the exact orphaned process group first, then run one final `-j 1` consistency build.
- If the process is silent, stalled, or no longer consuming work, stop diagnosing it as “still building”; capture the evidence and fix or restart the exact failed step.
- Keep each follow-up wait bounded and report progress during hardware or build work rather than allowing an unobserved multi-minute command.

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
