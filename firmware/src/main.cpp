#include <Arduino.h>
#include <BuildVersion.h>
#include <Epub.h>
#include <FontCacheManager.h>
#include <FontDecompressor.h>
#include <GfxRenderer.h>
#include <HalClock.h>
#include <HalDisplay.h>
#include <HalGPIO.h>
#include <HalPowerManager.h>
#include <HalStorage.h>
#include <HalSystem.h>
#include <HalTiltSensor.h>
#include <I18n.h>
#include <Logging.h>
#include <SPI.h>
#include <WiFi.h>
#include <builtinFonts/all.h>

#include <cstring>

#include "CrossPointSettings.h"
#include "CrossPointState.h"
#include "KOReaderCredentialStore.h"
#include "MappedInputManager.h"
#include "OpdsServerStore.h"
#include "RecentBooksStore.h"
#include "RuntimeTrace.h"
#include "SdCardFontSystem.h"
#include "activities/Activity.h"
#include "activities/ActivityManager.h"
#include "activities/settings/SdFirmwareUpdateActivity.h"
#include "components/UITheme.h"
#include "fontIds.h"
#include "images/LoadingIcon.h"
#include "util/ButtonNavigator.h"
#include "util/ScreenshotUtil.h"
#ifdef ENABLE_X3_COMPANION
#include "companion/CompanionService.h"

// USB commands hex-encode one complete companion envelope. Reserve enough CDC
// RX capacity for the largest protocol packet plus its prefix and terminator.
constexpr size_t USB_COMMAND_RX_BUFFER_BYTES = companion::MAX_PACKET_BYTES * 2 + 64;
#endif

GfxRenderer renderer(display);
MappedInputManager mappedInputManager(gpio, renderer);
ActivityManager activityManager(renderer, mappedInputManager);
FontDecompressor fontDecompressor;
SdCardFontSystem sdFontSystem;
FontCacheManager fontCacheManager(renderer.getFontMap(), renderer.getSdCardFonts());
static unsigned long allowSleepAt = 0;
// Physical input and accepted external commands share this inactivity clock.
// USB operations must wake low-power processing and keep their bounded work
// alive without requiring a separate physical button press.
static unsigned long lastActivityAtMs = 0;

// Fonts
EpdFont notoserif14RegularFont(&notoserif_14_regular);
EpdFont notoserif14BoldFont(&notoserif_14_bold);
EpdFont notoserif14ItalicFont(&notoserif_14_italic);
EpdFont notoserif14BoldItalicFont(&notoserif_14_bolditalic);
EpdFontFamily notoserif14FontFamily(&notoserif14RegularFont, &notoserif14BoldFont, &notoserif14ItalicFont,
                                    &notoserif14BoldItalicFont);
#ifndef OMIT_FONTS
EpdFont notoserif12RegularFont(&notoserif_12_regular);
EpdFont notoserif12BoldFont(&notoserif_12_bold);
EpdFont notoserif12ItalicFont(&notoserif_12_italic);
EpdFont notoserif12BoldItalicFont(&notoserif_12_bolditalic);
EpdFontFamily notoserif12FontFamily(&notoserif12RegularFont, &notoserif12BoldFont, &notoserif12ItalicFont,
                                    &notoserif12BoldItalicFont);
EpdFont notoserif16RegularFont(&notoserif_16_regular);
EpdFont notoserif16BoldFont(&notoserif_16_bold);
EpdFont notoserif16ItalicFont(&notoserif_16_italic);
EpdFont notoserif16BoldItalicFont(&notoserif_16_bolditalic);
EpdFontFamily notoserif16FontFamily(&notoserif16RegularFont, &notoserif16BoldFont, &notoserif16ItalicFont,
                                    &notoserif16BoldItalicFont);
EpdFont notoserif18RegularFont(&notoserif_18_regular);
EpdFont notoserif18BoldFont(&notoserif_18_bold);
EpdFont notoserif18ItalicFont(&notoserif_18_italic);
EpdFont notoserif18BoldItalicFont(&notoserif_18_bolditalic);
EpdFontFamily notoserif18FontFamily(&notoserif18RegularFont, &notoserif18BoldFont, &notoserif18ItalicFont,
                                    &notoserif18BoldItalicFont);

EpdFont notosans12RegularFont(&notosans_12_regular);
EpdFont notosans12BoldFont(&notosans_12_bold);
EpdFont notosans12ItalicFont(&notosans_12_italic);
EpdFont notosans12BoldItalicFont(&notosans_12_bolditalic);
EpdFontFamily notosans12FontFamily(&notosans12RegularFont, &notosans12BoldFont, &notosans12ItalicFont,
                                   &notosans12BoldItalicFont);
EpdFont notosans14RegularFont(&notosans_14_regular);
EpdFont notosans14BoldFont(&notosans_14_bold);
EpdFont notosans14ItalicFont(&notosans_14_italic);
EpdFont notosans14BoldItalicFont(&notosans_14_bolditalic);
EpdFontFamily notosans14FontFamily(&notosans14RegularFont, &notosans14BoldFont, &notosans14ItalicFont,
                                   &notosans14BoldItalicFont);
EpdFont notosans16RegularFont(&notosans_16_regular);
EpdFont notosans16BoldFont(&notosans_16_bold);
EpdFont notosans16ItalicFont(&notosans_16_italic);
EpdFont notosans16BoldItalicFont(&notosans_16_bolditalic);
EpdFontFamily notosans16FontFamily(&notosans16RegularFont, &notosans16BoldFont, &notosans16ItalicFont,
                                   &notosans16BoldItalicFont);
EpdFont notosans18RegularFont(&notosans_18_regular);
EpdFont notosans18BoldFont(&notosans_18_bold);
EpdFont notosans18ItalicFont(&notosans_18_italic);
EpdFont notosans18BoldItalicFont(&notosans_18_bolditalic);
EpdFontFamily notosans18FontFamily(&notosans18RegularFont, &notosans18BoldFont, &notosans18ItalicFont,
                                   &notosans18BoldItalicFont);

#endif  // OMIT_FONTS

EpdFont smallFont(&notosans_8_regular);
EpdFontFamily smallFontFamily(&smallFont);

EpdFont ui10RegularFont(&ubuntu_10_regular);
EpdFont ui10BoldFont(&ubuntu_10_bold);
EpdFontFamily ui10FontFamily(&ui10RegularFont, &ui10BoldFont);

EpdFont ui12RegularFont(&ubuntu_12_regular);
EpdFont ui12BoldFont(&ubuntu_12_bold);
EpdFontFamily ui12FontFamily(&ui12RegularFont, &ui12BoldFont);

// Definitions for SilentRestart.h. RTC_NOINIT survives ESP.restart() but not power loss.
RTC_NOINIT_ATTR uint32_t silentRebootMagic;
RTC_NOINIT_ATTR uint32_t silentRebootTarget;
constexpr uint32_t SILENT_REBOOT_MAGIC = 0xC1EAB007;
constexpr uint32_t SILENT_REBOOT_TARGET_HOME = 0;
constexpr uint32_t SILENT_REBOOT_TARGET_READER = 1;

// How the device is coming back to life, resolved once at boot. Both resume
// flows suppress the splash and leave the panel holding its pre-boot frame; a
// plain boot shows the splash. See setup() for the resolution.
enum class BootResume : uint8_t {
  Splash,       // cold boot, flash, panic, or plain reboot
  Silent,       // heap-defrag ESP.restart() (RTC flag; lost on power loss)
  QuickResume,  // wake from a quick-resume deep sleep (SD flag; survives power loss)
};

// Latched true once enterDeepSleep() commits to sleeping, before it tears down
// the current activity. WiFi activities call silentRestart() in onExit() to
// clear heap fragmentation on the way out, but deep sleep is a full chip reset
// on wake and already clears the heap, so rebooting here would just power the
// device back up against the user's sleep gesture. Never cleared:
// startDeepSleep() does not return, so a set latch only ends at the wakeup reset.
static bool deepSleepInProgress = false;

void silentRestart() {
  if (deepSleepInProgress) return;  // sleeping supersedes the heap-defrag reboot
  silentRebootTarget = SILENT_REBOOT_TARGET_HOME;
  silentRebootMagic = SILENT_REBOOT_MAGIC;
  LOG_DBG("MAIN", "Silent restart (target=home)");
  // E-ink retains the previous frame until Home's first paint lands (~2-3s).
  // Without an overlay, users don't see the reboot and fire input through to
  // Home. Select on the default selectorIndex=0 then opens the most-recent
  // book, looking like a trampoline back to the reader they just exited.
  GUI.drawPopup(renderer, tr(STR_LOADING_POPUP));
  delay(50);
  ESP.restart();
}

void silentRestartToReader() {
  if (deepSleepInProgress) return;  // sleeping supersedes the heap-defrag reboot
  silentRebootTarget = SILENT_REBOOT_TARGET_READER;
  silentRebootMagic = SILENT_REBOOT_MAGIC;
  LOG_DBG("MAIN", "Silent restart (target=reader)");
  GUI.drawPopup(renderer, tr(STR_LOADING_POPUP));
  delay(50);
  ESP.restart();
}

void waitForPowerRelease() {
  gpio.update();
  while (gpio.isPressed(HalGPIO::BTN_POWER)) {
    delay(50);
    gpio.update();
  }
}

constexpr char SLEEP_FRAME_FILE[] = "/.crosspoint/sleep_frame.bin";

static bool rawPowerButtonPressed() { return digitalRead(InputManager::POWER_BUTTON_PIN) == LOW; }

static volatile bool sleepTransitionWakeLatched = false;

static void ARDUINO_ISR_ATTR latchSleepTransitionWake() { sleepTransitionWakeLatched = true; }

static void armSleepTransitionWake() {
  sleepTransitionWakeLatched = false;
  attachInterrupt(digitalPinToInterrupt(InputManager::POWER_BUTTON_PIN), latchSleepTransitionWake, FALLING);
}

static void disarmSleepTransitionWake() {
  detachInterrupt(digitalPinToInterrupt(InputManager::POWER_BUTTON_PIN));
}

static bool sleepTransitionWakeRequested() {
  return sleepTransitionWakeLatched || rawPowerButtonPressed();
}

static void restoreAfterCancelledSleep(bool resumeReader, bool previousShowBootScreen) {
  deepSleepInProgress = false;
  APP_STATE.showBootScreen = previousShowBootScreen;
  APP_STATE.saveToFile();
  Storage.remove(SLEEP_FRAME_FILE);
  // Consume the complete wake gesture before normal input resumes. Otherwise
  // the same physical tap can be debounced as a new Power command and put the
  // freshly restored activity straight back to sleep.
  waitForPowerRelease();
  gpio.update();
  delay(10);
  gpio.update();
  display.requestCleanRefresh();
  if (resumeReader && !APP_STATE.openEpubPath.empty()) {
    activityManager.goToReader(APP_STATE.openEpubPath);
  } else {
    activityManager.goHome();
  }
  activityManager.loop();
  activityManager.requestUpdateAndWait();
  lastActivityAtMs = millis();
  allowSleepAt = millis() + 500;
#ifdef ENABLE_X3_COMPANION
  companion::companionService.wakeFastAdvertising();
#endif
}

static void saveSleepFrameBuffer() {
  HalFile file;
  if (!Storage.openFileForWrite("SLP", SLEEP_FRAME_FILE, file)) return;
  file.write(renderer.getFrameBuffer(), renderer.getBufferSize());
  file.close();
}

static bool loadSleepFrameBuffer() {
  HalFile file;
  if (!Storage.openFileForRead("SLP", SLEEP_FRAME_FILE, file)) return false;
  const size_t bufferSize = display.getBufferSize();
  const size_t bytesRead = file.read(display.getFrameBuffer(), bufferSize);
  file.close();
  if (bytesRead != bufferSize) {
    Storage.remove(SLEEP_FRAME_FILE);
    return false;
  }
  Storage.remove(SLEEP_FRAME_FILE);
  return true;
}

// Enter deep sleep mode
void enterDeepSleep(bool fromTimeout = false) {
  runtime_trace::mark(runtime_trace::Checkpoint::SLEEP_ENTRY);
  HalPowerManager::Lock powerLock;  // Ensure we are at normal CPU frequency for sleep preparation
  APP_STATE.lastSleepFromReader = activityManager.isReaderActivity();

  const bool isQuickResumeSleep =
      SETTINGS.sleepScreen == CrossPointSettings::SLEEP_SCREEN_MODE::QUICK_RESUME ||
      (fromTimeout &&
       SETTINGS.quickResumeSleepScreen == CrossPointSettings::QUICK_RESUME_SLEEP_SCREEN::QUICK_RESUME_AFTER_TIMEOUT);
  const bool previousShowBootScreen = APP_STATE.showBootScreen;
  APP_STATE.showBootScreen = !isQuickResumeSleep;

  APP_STATE.saveToFile();

  // Commit to sleeping before goToSleep() runs the outgoing activity's onExit():
  // a WiFi activity would otherwise silentRestart() here and reboot instead.
  deepSleepInProgress = true;
  // A manual sleep may begin while Power is still held. Arm transition wake
  // only after that initiating press is released, so a later falling edge is
  // unambiguously a wake request. Keep the latch active while the truthful
  // Sleeping frame is rendered to physical completion.
  if (!fromTimeout) waitForPowerRelease();
  armSleepTransitionWake();
  activityManager.goToSleep(fromTimeout);

  if (sleepTransitionWakeRequested()) {
    LOG_INF("MAIN", "Power pressed while the Sleeping frame rendered; cancelling sleep");
    disarmSleepTransitionWake();
    restoreAfterCancelledSleep(APP_STATE.lastSleepFromReader, previousShowBootScreen);
    return;
  }

  if (isQuickResumeSleep) {
    saveSleepFrameBuffer();
  }

#ifdef ENABLE_X3_COMPANION
  // The sleep state is already visible. Give durable phone work one final
  // discovery/sync opportunity, then stop the controller. The whole vendor
  // lifecycle runs in a disposable worker behind one hard deadline, so neither
  // advertising restart nor teardown can strand the old awake frame onscreen.
  const auto finalizeResult =
      companion::companionService.finalizeForDeepSleep(1200, 2100, sleepTransitionWakeRequested);
  if (finalizeResult == companion::DeepSleepFinalizeResult::WakeCancelled) {
    LOG_INF("MAIN", "Power pressed during final sync; cancelling sleep");
    disarmSleepTransitionWake();
    restoreAfterCancelledSleep(APP_STATE.lastSleepFromReader, previousShowBootScreen);
    return;
  }
  if (finalizeResult == companion::DeepSleepFinalizeResult::WakeRequiresRestart) {
    LOG_INF("MAIN", "Power pressed after BLE shutdown began; restarting awake safely");
    disarmSleepTransitionWake();
    deepSleepInProgress = false;
    APP_STATE.showBootScreen = previousShowBootScreen;
    APP_STATE.saveToFile();
    if (APP_STATE.lastSleepFromReader && !APP_STATE.openEpubPath.empty()) {
      silentRestartToReader();
    } else {
      silentRestart();
    }
    return;
  }
  // Final sync may finish between two callback samples. The ISR latch remains
  // authoritative across the remaining WiFi/display shutdown interval.
  if (sleepTransitionWakeRequested()) {
    LOG_INF("MAIN", "Power pressed after final sync; restarting awake safely");
    disarmSleepTransitionWake();
    deepSleepInProgress = false;
    APP_STATE.showBootScreen = previousShowBootScreen;
    APP_STATE.saveToFile();
    if (APP_STATE.lastSleepFromReader && !APP_STATE.openEpubPath.empty()) {
      silentRestartToReader();
    } else {
      silentRestart();
    }
    return;
  }
#endif

  // Tear down WiFi so the modem power domain isn't held alive across deep sleep.
  // Wake from deep sleep is effectively a chip reset, so no state needs to survive.
  if (WiFi.getMode() != WIFI_MODE_NULL) {
    WiFi.disconnect(true);
    WiFi.mode(WIFI_OFF);
  }

  halTiltSensor.deepSleep();
  if (sleepTransitionWakeRequested()) {
    disarmSleepTransitionWake();
    ESP.restart();
  }
  display.deepSleep();
  LOG_DBG("MAIN", "Entering deep sleep");

  // The latch stays armed through the final hardware commit. PowerManager
  // reboots if it observes the request after the panel has already slept.
  powerManager.startDeepSleep(gpio, sleepTransitionWakeRequested);
}

void setupDisplayAndFonts(bool seamless = false) {
  display.begin(seamless);
  renderer.begin();
  activityManager.begin();
  LOG_DBG("MAIN", "Display initialized");

  // Initialize font decompressor for compressed reader fonts
  if (!fontDecompressor.init()) {
    LOG_ERR("MAIN", "Font decompressor init failed");
  }
  fontCacheManager.setFontDecompressor(&fontDecompressor);
  renderer.setFontCacheManager(&fontCacheManager);
  renderer.insertFont(NOTOSERIF_14_FONT_ID, notoserif14FontFamily);
#ifndef OMIT_FONTS
  renderer.insertFont(NOTOSERIF_12_FONT_ID, notoserif12FontFamily);
  renderer.insertFont(NOTOSERIF_16_FONT_ID, notoserif16FontFamily);
  renderer.insertFont(NOTOSERIF_18_FONT_ID, notoserif18FontFamily);

  renderer.insertFont(NOTOSANS_12_FONT_ID, notosans12FontFamily);
  renderer.insertFont(NOTOSANS_14_FONT_ID, notosans14FontFamily);
  renderer.insertFont(NOTOSANS_16_FONT_ID, notosans16FontFamily);
  renderer.insertFont(NOTOSANS_18_FONT_ID, notosans18FontFamily);
#endif  // OMIT_FONTS
  renderer.insertFont(UI_10_FONT_ID, ui10FontFamily);
  renderer.insertFont(UI_12_FONT_ID, ui12FontFamily);
  renderer.insertFont(SMALL_FONT_ID, smallFontFamily);

  // Discover and load SD card fonts
  sdFontSystem.begin(renderer);

  LOG_DBG("MAIN", "Fonts setup");
}

void setup() {
  lastActivityAtMs = millis();
#ifdef ENABLE_SERIAL_LOG
  // Earliest possible Serial setup. The 250 ms stall before begin() lets the
  // USB Serial/JTAG peripheral finish power-on and lets the host complete USB
  // enumeration before we touch the CDC state — otherwise cold boot races
  // and the host has to be physically replugged for logs to flow. Warm reboot
  // worked without the delay because USB was already enumerated.
  delay(250);
#ifdef ENABLE_X3_COMPANION
  const size_t usbCommandRxBufferBytes = logSerial.setRxBufferSize(USB_COMMAND_RX_BUFFER_BYTES);
#endif
  Serial.begin(115200);
  logSerial.setTxTimeoutMs(1);  // This is a load-bearing 1. Do not modify.
#ifdef ENABLE_X3_COMPANION
  if (usbCommandRxBufferBytes != USB_COMMAND_RX_BUFFER_BYTES) {
    LOG_ERR("USB", "Could not allocate command RX buffer requested=%u actual=%u",
            static_cast<unsigned>(USB_COMMAND_RX_BUFFER_BYTES), static_cast<unsigned>(usbCommandRxBufferBytes));
  }
#endif
#endif

  runtime_trace::begin();

  HalSystem::begin();

  // Read-and-clear so a panic later in setup() doesn't loop into silent reboot.
  // Bound the target range too — RTC_NOINIT memory is uninitialized on cold boot.
  const bool isSilentReboot = (silentRebootMagic == SILENT_REBOOT_MAGIC);
  const uint32_t snapshotTarget =
      (isSilentReboot && silentRebootTarget <= SILENT_REBOOT_TARGET_READER) ? silentRebootTarget : 0;
  silentRebootMagic = 0;
  silentRebootTarget = 0;

  gpio.begin();
  powerManager.begin();
  halTiltSensor.begin();
  halClock.begin();

  LOG_INF("MAIN", "Hardware detect: %s", gpio.deviceIsX3() ? "X3" : "X4");

  // SD Card Initialization
  // We need 6 open files concurrently when parsing a new chapter
  if (!Storage.begin()) {
    LOG_ERR("MAIN", "SD card initialization failed");
    setupDisplayAndFonts(isSilentReboot);
    activityManager.goToFullScreenMessage("SD card error", EpdFontFamily::BOLD);
#ifdef ENABLE_X3_COMPANION
    // Keep recovery/control available even when the card is missing or slow.
    // Ticket persistence degrades to RAM, but pairing and remote diagnostics
    // must not disappear behind the SD error screen.
    companion::companionService.begin();
#endif
    return;
  }

  HalSystem::checkPanic();

  SETTINGS.loadFromFile();
  APP_STATE.loadFromFile();
  RECENT_BOOKS.loadFromFile();
  I18N.setLanguage(static_cast<Language>(SETTINGS.language));
  KOREADER_STORE.loadFromFile();
  OPDS_STORE.loadFromFile();
  UITheme::getInstance().reload();
  ButtonNavigator::setMappedInputManager(mappedInputManager);

  const auto wakeupReason = gpio.getWakeupReason();
  switch (wakeupReason) {
    case HalGPIO::WakeupReason::PowerButton:
      LOG_DBG("MAIN", "Verifying power button press duration");
      gpio.verifyPowerButtonWakeup(SETTINGS.getPowerButtonDuration(),
                                   SETTINGS.shortPwrBtn == CrossPointSettings::SHORT_PWRBTN::SLEEP);
      break;
    case HalGPIO::WakeupReason::AfterUSBPower:
      // If USB power caused a cold boot, go back to sleep
      LOG_DBG("MAIN", "Wakeup reason: After USB Power");
      powerManager.startDeepSleep(gpio);
      break;
    case HalGPIO::WakeupReason::AfterFlash:
      // After flashing, just proceed to boot
    case HalGPIO::WakeupReason::Other:
    default:
      break;
  }

  // Recovery firmware mode: hold left side button (BTN_UP) together with the power button at
  // boot to skip directly to the SD-card firmware update screen. Useful on devices where USB
  // flashing has been locked down (e.g. recent X3 firmware).
  bool recoveryFirmwareMode = false;
  if (wakeupReason == HalGPIO::WakeupReason::PowerButton && rawPowerButtonPressed()) {
    // Refresh the cached button state a few times — isPressed() needs ~half a second to settle
    // after boot per the HalGPIO contract. Use a millis-based deadline so we always wait the full
    // settle window even if the loop body takes longer than expected on slow boots.
    const unsigned long settleStart = millis();
    while (rawPowerButtonPressed() && millis() - settleStart < 500) {
      gpio.update();
      delay(10);
    }
    if (rawPowerButtonPressed() && gpio.isPressed(HalGPIO::BTN_UP)) {
      recoveryFirmwareMode = true;
      LOG_INF("MAIN", "Recovery firmware mode (UP + POWER held at boot)");
    }
  }

  // First serial output only here to avoid timing inconsistencies for power button press duration verification
  LOG_DBG("MAIN", "Starting CrossPoint version %s", CROSSPOINT_VERSION);

  // Resolve the single boot-presentation decision. Skipping the splash also
  // skips the panel-clearing pass and the X3 initial-full-sync arming (see
  // HalDisplay::begin), so the first paint is FAST_REFRESH (~500ms) over the
  // retained frame and input dispatches against a visible UI.
  const BootResume resume = isSilentReboot              ? BootResume::Silent
                            : !APP_STATE.showBootScreen ? BootResume::QuickResume
                                                        : BootResume::Splash;

  setupDisplayAndFonts(resume != BootResume::Splash);

  switch (resume) {
    case BootResume::Silent:
      // Splash skipped: the routing block below picks the target activity; the
      // panel keeps showing the pre-reboot popup until that first paint lands.
      break;
    case BootResume::QuickResume:
      // One-shot flag: re-arm the splash for the next non-quick-resume boot. Save
      // before any painting so a hang in the blocking paint path can't strand
      // us in a quick-resume-with-no-frame loop on the next boot.
      APP_STATE.showBootScreen = true;
      APP_STATE.saveToFile();
      if (loadSleepFrameBuffer()) {
        // Frame restored: swap the sleep moon for the loading icon.
        const auto pageHeight = renderer.getScreenHeight();
        renderer.drawImage(LoadingIcon, 0, pageHeight - LOADINGICON_HEIGHT, LOADINGICON_WIDTH, LOADINGICON_HEIGHT);
        renderer.displayBuffer(HalDisplay::HALF_REFRESH);
      } else {
        activityManager.goToBoot();  // frame file missing, fall back to the splash
      }
      break;
    case BootResume::Splash:
      activityManager.goToBoot();
      break;
  }

  if (recoveryFirmwareMode) {
    // Skip normal home/reader routing: jump straight into the SD firmware picker.
    activityManager.replaceActivity(
        std::make_unique<SdFirmwareUpdateActivity>(renderer, mappedInputManager, /*recoveryMode=*/true));
  } else if (HalSystem::isRebootFromPanic()) {
    // If we rebooted from a panic, go to crash report screen to show the panic info
    activityManager.goToCrashReport();
  } else if (resume == BootResume::Silent && snapshotTarget == SILENT_REBOOT_TARGET_READER &&
             !APP_STATE.openEpubPath.empty()) {
    activityManager.goToReader(APP_STATE.openEpubPath);
  } else if (resume == BootResume::Silent) {
    // target == home (or reader with no open book): land on home — don't fall
    // through to the sleep-wake "resume reader" logic, which fires on stale
    // openEpubPath + lastSleepFromReader from a prior session.
    activityManager.goHome();
  } else if (APP_STATE.openEpubPath.empty() || !APP_STATE.lastSleepFromReader ||
             mappedInputManager.isPressed(MappedInputManager::Button::Back) || APP_STATE.readerActivityLoadCount > 0) {
    // Boot to home screen if no book is open, last sleep was not from reader, back button is held, or reader activity
    // crashed (indicated by readerActivityLoadCount > 0)
    activityManager.goHome();
  } else {
    // Clear app state to avoid getting into a boot loop if the epub doesn't load
    const auto path = APP_STATE.openEpubPath;
    APP_STATE.openEpubPath = "";
    APP_STATE.readerActivityLoadCount++;
    APP_STATE.saveToFile();
    activityManager.goToReader(path);
  }

  if (resume == BootResume::Silent) {
    // Block until the first paint physically completes. refreshDisplay()
    // waits on the panel BUSY pin so when this returns the user can see the
    // new activity. Without the wait, an edge captured by gpio.update()
    // during boot dispatches against an invisible Home and the default
    // selectorIndex=0 opens the most-recent book.
    activityManager.requestUpdateAndWait();
    // Absorb any button held at this point into currentState as a non-edge:
    // two gpio.update() calls separated by > InputManager's 5ms debounce
    // transition the held bit through lastDebounceTime into currentState
    // without setting pressedEvents, so the first loop()'s own gpio.update()
    // sees state == currentState and emits nothing.
    gpio.update();
    delay(10);
    gpio.update();
  }

  // Ensure we're not still holding the power button before leaving setup
  waitForPowerRelease();
  allowSleepAt = millis() + 2000;
#ifdef ENABLE_X3_COMPANION
  companion::companionService.begin();
#endif
  runtime_trace::mark(runtime_trace::Checkpoint::SETUP_COMPLETE);
}

void loop() {
  runtime_trace::beginLoop();
  static unsigned long maxLoopDuration = 0;
  const unsigned long loopStartTime = millis();
  static unsigned long lastMemPrint = 0;

  gpio.update();
  runtime_trace::recordInput(gpio.getRawButtonState(), gpio.getDebouncedButtonState(), gpio.getPressedButtonEvents(),
                             gpio.getReleasedButtonEvents(), gpio.getPowerButtonHeldTime());
  runtime_trace::mark(runtime_trace::Checkpoint::GPIO_UPDATED);
#ifdef ENABLE_X3_COMPANION
  companion::companionService.setReading(activityManager.isReaderActivity());
  // BLE callbacks only stamp activity and enqueue work. Restore full speed on
  // the main loop before decoding a command or rendering its resulting UI.
  if (companion::companionService.requiresFullClock()) powerManager.setPowerSaving(false);
  runtime_trace::mark(runtime_trace::Checkpoint::COMPANION_LOOP_ENTER);
  companion::companionService.loop();
  runtime_trace::mark(runtime_trace::Checkpoint::COMPANION_LOOP_EXIT);
#endif
  halTiltSensor.update(SETTINGS.tiltPageTurn, SETTINGS.orientation, activityManager.isReaderActivity());

  renderer.setFadingFix(SETTINGS.fadingFix);

  if (Serial && millis() - lastMemPrint >= 10000) {
    LOG_INF("MEM", "Free: %d bytes, Total: %d bytes, Min Free: %d bytes, MaxAlloc: %d bytes", ESP.getFreeHeap(),
            ESP.getHeapSize(), ESP.getMinFreeHeap(), ESP.getMaxAllocHeap());
    lastMemPrint = millis();
  }

  // Handle incoming serial commands,
  // nb: we use logSerial from logging to avoid deprecation warnings
  if (logSerial.available() > 0) {
    String line = logSerial.readStringUntil('\n');
    if (line.startsWith("CMD:")) {
      String cmd = line.substring(4);
      cmd.trim();
      const bool recognizedCommand = cmd == "SCREENSHOT" || cmd == "CRASH_REPORT"
#ifdef ENABLE_X3_COMPANION
                                     || cmd == "RUNTIME_TRACE" || cmd.startsWith("USB_BOOK:")
#endif
          ;
      if (!recognizedCommand) return;
      lastActivityAtMs = millis();
      powerManager.setPowerSaving(false);
      if (cmd == "SCREENSHOT") {
        const uint32_t bufferSize = display.getBufferSize();
        logSerial.printf("SCREENSHOT_START:%d\n", bufferSize);
        uint8_t* buf = display.getFrameBuffer();
        logSerial.write(buf, bufferSize);
        logSerial.printf("SCREENSHOT_END\n");
      } else if (cmd == "CRASH_REPORT") {
        logSerial.printf("CRASH_REPORT_START\n");
        if (!Storage.readFileToStream("/crash_report.txt", logSerial, 256)) {
          logSerial.printf("No crash report is available.\n");
        }
        logSerial.printf("\nCRASH_REPORT_END\n");
#ifdef ENABLE_X3_COMPANION
      } else if (cmd == "RUNTIME_TRACE") {
        runtime_trace::dump(logSerial);
      } else if (cmd.startsWith("USB_BOOK:")) {
        const String encoded = cmd.substring(9);
        uint8_t packet[companion::MAX_PACKET_BYTES];
        size_t packetLength = 0;
        bool valid = encoded.length() > 0 && (encoded.length() % 2) == 0 &&
                     encoded.length() <= companion::MAX_PACKET_BYTES * 2;
        auto nibble = [](const char value) -> int {
          if (value >= '0' && value <= '9') return value - '0';
          if (value >= 'a' && value <= 'f') return value - 'a' + 10;
          if (value >= 'A' && value <= 'F') return value - 'A' + 10;
          return -1;
        };
        for (size_t i = 0; valid && i < encoded.length(); i += 2) {
          const int high = nibble(encoded[i]);
          const int low = nibble(encoded[i + 1]);
          valid = high >= 0 && low >= 0;
          if (valid) packet[packetLength++] = static_cast<uint8_t>((high << 4) | low);
        }
        uint32_t messageId = 0;
        const bool accepted = valid &&
                              companion::companionService.handleUsbBookPacket(packet, packetLength, messageId);
        logSerial.printf("USB_BOOK_%s:%lu\n", accepted ? "ACK" : "NACK",
                         static_cast<unsigned long>(messageId));
#endif
      }
    }
  }

  // Check for any user activity (button press or release) or active background work
  const bool buttonActivity = gpio.wasAnyPressed() || gpio.wasAnyReleased();
  const bool tiltActivity = halTiltSensor.hadActivity();
  if (buttonActivity || tiltActivity) {
    lastActivityAtMs = millis();         // Reset inactivity timer
    powerManager.setPowerSaving(false);  // Restore normal CPU frequency on user activity
#ifdef ENABLE_X3_COMPANION
    if (buttonActivity) companion::companionService.wakeFastAdvertising();
#endif
  } else if (activityManager.preventAutoSleep()) {
    // Focus and Live ticket must stay awake, but that is not continuous user
    // activity. Keep the inactivity deadline parked without pinning the CPU at
    // full speed on every loop iteration.
    lastActivityAtMs = millis();
  }

  static bool screenshotButtonsReleased = true;
  static bool screenshotComboActive = false;
  if (gpio.isPressed(HalGPIO::BTN_POWER) && gpio.isPressed(HalGPIO::BTN_DOWN)) {
    screenshotComboActive = true;
    if (screenshotButtonsReleased) {
      screenshotButtonsReleased = false;
      {
        RenderLock lock;
        ScreenshotUtil::takeScreenshot(renderer);
      }
    }
    return;
  }
  if (screenshotComboActive) {
    if (gpio.isPressed(HalGPIO::BTN_POWER)) return;
    if (gpio.wasReleased(HalGPIO::BTN_POWER)) {
      screenshotButtonsReleased = true;
      screenshotComboActive = false;
      return;
    }
    screenshotButtonsReleased = true;
    screenshotComboActive = false;
  }

  unsigned long sleepTimeoutMs = SETTINGS.getSleepTimeoutMs();
#ifdef ENABLE_X3_COMPANION
  // The phone-managed companion policy is authoritative for idle Home and
  // static-ticket sleep. Activities such as Reading, Focus and Live ticket
  // already reset lastActivityAtMs while they intentionally remain active.
  sleepTimeoutMs = companion::companionService.companionSleepAfterMs();
#endif
  bool preserveStaticTicket = false;
#ifdef ENABLE_X3_COMPANION
  // A static e-ink ticket is already the low-power screen. Keep the rendered
  // ticket intact with BLE quiet and the CPU power-saved instead of replacing
  // it with the generic sleep artwork. Back exits the ticket and restores the
  // fast discovery window.
  preserveStaticTicket = companion::companionService.staticTicketDisplayed();
#endif
  if (!preserveStaticTicket && sleepTimeoutMs > 0 && millis() - lastActivityAtMs >= sleepTimeoutMs) {
    LOG_DBG("SLP", "Auto-sleep triggered after %lu ms of inactivity", sleepTimeoutMs);
    enterDeepSleep(true);
    // This should never be hit as `enterDeepSleep` calls esp_deep_sleep_start
    return;
  }

  if (millis() >= allowSleepAt && gpio.isPressed(HalGPIO::BTN_POWER) &&
      gpio.getPowerButtonHeldTime() > SETTINGS.getPowerButtonDuration()) {
    // If the screenshot combination is potentially being pressed, don't sleep
    if (gpio.isPressed(HalGPIO::BTN_DOWN)) {
      return;
    }
    enterDeepSleep();
    // This should never be hit as `enterDeepSleep` calls esp_deep_sleep_start
    return;
  }

  // Refresh screen when power button is short-pressed with FORCE_REFRESH setting.
  if (SETTINGS.shortPwrBtn == CrossPointSettings::SHORT_PWRBTN::FORCE_REFRESH &&
      mappedInputManager.wasReleased(MappedInputManager::Button::Power)) {
    LOG_DBG("MAIN", "Manual screen refresh triggered");
    RenderLock lock;
    renderer.displayBuffer(HalDisplay::HALF_REFRESH);
  }

  // Refresh the battery icon when USB is plugged or unplugged.
  // Placed after sleep guards so we never queue a render that won't be processed.
  if (gpio.wasUsbStateChanged()) {
#ifdef ENABLE_X3_COMPANION
    companion::companionService.notifyPowerChanged();
#endif
    activityManager.requestUpdate();
  }

  const unsigned long activityStartTime = millis();
  runtime_trace::mark(runtime_trace::Checkpoint::ACTIVITY_LOOP_ENTER);
  activityManager.loop();
  runtime_trace::mark(runtime_trace::Checkpoint::ACTIVITY_LOOP_EXIT);
  const unsigned long activityDuration = millis() - activityStartTime;

  const unsigned long loopDuration = millis() - loopStartTime;
  if (loopDuration > maxLoopDuration) {
    maxLoopDuration = loopDuration;
    if (maxLoopDuration > 50) {
      LOG_DBG("LOOP", "New max loop duration: %lu ms (activity: %lu ms)", maxLoopDuration, activityDuration);
    }
  }

  // Add delay at the end of the loop to prevent tight spinning
  // When an activity requests skip loop delay (e.g., webserver running), use yield() for faster response
  // Otherwise, use longer delay to save power
  if (activityManager.skipLoopDelay()) {
    powerManager.setPowerSaving(false);  // Make sure we're at full performance when skipLoopDelay is requested
    yield();                             // Give FreeRTOS a chance to run tasks, but return immediately
  } else {
    bool companionNeedsFullClock = false;
#ifdef ENABLE_X3_COMPANION
    companionNeedsFullClock = companion::companionService.requiresFullClock();
#endif
    if (millis() - lastActivityAtMs >= HalPowerManager::IDLE_POWER_SAVING_MS && !companionNeedsFullClock) {
      runtime_trace::mark(runtime_trace::Checkpoint::POWER_SAVING_ENTER);
      // If we've been inactive for a while, increase the delay to save power
      int minimumFrequency = HalPowerManager::LOW_POWER_FREQ;
#ifdef ENABLE_X3_COMPANION
      if (companion::companionService.requiresBleSafeClock()) minimumFrequency = HalPowerManager::BLE_SAFE_FREQ;
#endif
      powerManager.setPowerSaving(true, minimumFrequency);
      delay(50);
    } else {
      if (companionNeedsFullClock) {
        // Pairing and bursts run at normal speed. Once BLE traffic has been
        // idle for a measured grace period, the companion-safe 80 MHz floor
        // is sufficient and avoids holding 160 MHz for the entire link.
        powerManager.setPowerSaving(false);
      }
      // Short delay to prevent tight loop while still being responsive
      delay(10);
    }
  }
  runtime_trace::mark(runtime_trace::Checkpoint::LOOP_COMPLETE);
}
