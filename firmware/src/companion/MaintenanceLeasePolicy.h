#pragma once

#include <cstdint>

namespace companion {

/** Ephemeral test-only keep-awake deadline; it never changes persisted policy. */
struct MaintenanceLeasePolicy {
  static constexpr uint16_t MAX_SECONDS = 600;

  static uint32_t deadline(uint32_t nowMs, uint16_t seconds) {
    const uint16_t bounded = seconds > MAX_SECONDS ? MAX_SECONDS : seconds;
    return bounded == 0 ? 0u : nowMs + static_cast<uint32_t>(bounded) * 1000u;
  }

  static bool active(uint32_t nowMs, uint32_t untilMs) {
    return untilMs != 0 && static_cast<int32_t>(untilMs - nowMs) > 0;
  }
};

}  // namespace companion
