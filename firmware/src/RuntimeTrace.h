#pragma once

#include <Arduino.h>

#include <cstdint>

namespace runtime_trace {

enum class Checkpoint : uint16_t {
  NONE = 0,
  SETUP_STARTED,
  SETUP_COMPLETE,
  LOOP_STARTED,
  GPIO_UPDATED,
  COMPANION_LOOP_ENTER,
  COMPANION_RADIO_RESUME_ENTER,
  COMPANION_RADIO_RESUME_EXIT,
  COMPANION_ADVERTISING_POLICY_ENTER,
  COMPANION_ADVERTISING_STOP_ENTER,
  COMPANION_ADVERTISING_STOP_EXIT,
  COMPANION_ADVERTISING_START_ENTER,
  COMPANION_ADVERTISING_START_EXIT,
  COMPANION_ADVERTISING_POLICY_EXIT,
  COMPANION_LOOP_EXIT,
  ACTIVITY_LOOP_ENTER,
  ACTIVITY_LOOP_EXIT,
  POWER_SAVING_ENTER,
  LOOP_COMPLETE,
  SLEEP_ENTRY,
};

#ifdef ENABLE_X3_COMPANION
void begin();
void mark(Checkpoint checkpoint);
void beginLoop();
void recordInput(uint8_t rawState, uint8_t debouncedState, uint8_t pressedEvents, uint8_t releasedEvents,
                 uint32_t powerHeldMs);
void dump(Print& output);
#else
inline void begin() {}
inline void mark(Checkpoint) {}
inline void beginLoop() {}
inline void recordInput(uint8_t, uint8_t, uint8_t, uint8_t, uint32_t) {}
inline void dump(Print&) {}
#endif

}  // namespace runtime_trace
