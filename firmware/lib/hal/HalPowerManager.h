#pragma once

#include <Arduino.h>
#include <BatteryMonitor.h>
#include <InputManager.h>
#include <Logging.h>
#include <Wire.h>
#include <freertos/semphr.h>

#include <cassert>

#include "HalGPIO.h"

class HalPowerManager;
extern HalPowerManager powerManager;  // Singleton

class HalPowerManager {
  int normalFreq = 0;  // MHz
  bool isLowPower = false;
  int rejectedFrequencyMhz = 0;  // A clock configuration rejection is permanent for this boot/SoC.

  // I2C fuel gauge configuration for X3 battery monitoring
  bool _batteryUseI2C = false;                   // True if using I2C fuel gauge (X3), false for ADC (X4)
  mutable int _batteryCachedPercent = 0;         // Last read battery percentage (0-100)
  mutable unsigned long _batteryLastPollMs = 0;  // Timestamp of last battery read in milliseconds

  enum LockMode { None, NormalSpeed };
  LockMode currentLockMode = None;
  SemaphoreHandle_t modeMutex = nullptr;  // Protect access to currentLockMode

 public:
  // ESP32-C3 supports the 40 MHz XTAL and XTAL/2 (20 MHz), but not the
  // XTAL/4 10 MHz mode exposed only by the original ESP32. Requesting 10 MHz
  // repeatedly makes the Arduino core allocate an error string on every
  // rejection, eventually starving the main loop.
  static constexpr int LOW_POWER_FREQ = 20;                    // MHz; ESP32-C3 XTAL/2
  static constexpr int BLE_SAFE_FREQ = 80;                     // MHz; keeps the ESP32-C3 APB at 80 MHz
  static constexpr unsigned long IDLE_POWER_SAVING_MS = 3000;  // ms
  static constexpr unsigned long BATTERY_POLL_MS = 1500;       // ms

  void begin();

  // Control CPU frequency for power saving
  void setPowerSaving(bool enabled, int minimumFrequencyMhz = LOW_POWER_FREQ);

  // Setup wake up GPIO and enter deep sleep
  // Should be called inside main loop() to handle the currentLockMode
  // A transition-wide probe may request a safe reboot until the final
  // deep-sleep commit. This closes the interval after the main loop stops
  // processing buttons but before hardware wake is armed.
  void startDeepSleep(HalGPIO& gpio, bool (*wakeRequested)() = nullptr) const;

  // Get battery percentage (range 0-100)
  uint16_t getBatteryPercentage() const;

  // RAII helper class to manage power saving locks
  // Usage: create an instance of Lock in a scope to disable power saving, for example when running a task that needs
  // full performance. When the Lock instance is destroyed (goes out of scope), power saving will be re-enabled.
  class Lock {
    friend class HalPowerManager;
    bool valid = false;

   public:
    explicit Lock();
    ~Lock();

    // Non-copyable and non-movable
    Lock(const Lock&) = delete;
    Lock& operator=(const Lock&) = delete;
    Lock(Lock&&) = delete;
    Lock& operator=(Lock&&) = delete;
  };
};
