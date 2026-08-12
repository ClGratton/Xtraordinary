#pragma once

#include <cstdint>

namespace companion {

// Persisted content is deliberately absent from this policy. A saved Live
// ticket is data, not a runtime radio state: only an active Focus session or
// the currently displayed Live ticket may bypass the boot/Home fast window.
constexpr bool requiresPersistentSlowAdvertising(bool focusActive,
                                                 bool liveTicketDisplayed) {
  return focusActive || liveTicketDisplayed;
}

constexpr bool isValidRadioPolicy(uint16_t fastWindowMinutes,
                                  uint16_t standbyIntervalSeconds,
                                  uint16_t connectedIntervalMs,
                                  uint16_t sleepAfterMinutes) {
  return fastWindowMinutes >= 1 && fastWindowMinutes <= 30 &&
         standbyIntervalSeconds >= 10 && standbyIntervalSeconds <= 300 &&
         connectedIntervalMs >= 500 && connectedIntervalMs <= 4000 &&
         sleepAfterMinutes >= 2 && sleepAfterMinutes <= 60 &&
         fastWindowMinutes < sleepAfterMinutes;
}

static_assert(!requiresPersistentSlowAdvertising(false, false));
static_assert(requiresPersistentSlowAdvertising(true, false));
static_assert(requiresPersistentSlowAdvertising(false, true));
static_assert(isValidRadioPolicy(5, 30, 2000, 10));
static_assert(!isValidRadioPolicy(10, 30, 2000, 10));
static_assert(!isValidRadioPolicy(5, 4, 2000, 10));

}  // namespace companion
