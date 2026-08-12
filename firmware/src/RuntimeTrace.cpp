#include "RuntimeTrace.h"

#include <esp_attr.h>
#include <esp_system.h>

#include <cstring>

namespace runtime_trace {
namespace {

constexpr uint32_t TRACE_MAGIC = 0x58545243;  // XTRC
constexpr uint16_t TRACE_VERSION = 1;

struct Snapshot {
  uint32_t bootSequence = 0;
  uint32_t loopCount = 0;
  uint32_t checkpointMs = 0;
  uint32_t lastLoopStartedMs = 0;
  uint32_t lastLoopCompletedMs = 0;
  uint32_t powerHeldMs = 0;
  uint16_t checkpoint = 0;
  uint8_t rawState = 0;
  uint8_t debouncedState = 0;
  uint8_t pressedEvents = 0;
  uint8_t releasedEvents = 0;
  uint8_t resetReason = 0;
  uint8_t reserved[3] = {};
};

struct RetainedTrace {
  uint32_t magic = 0;
  uint16_t version = 0;
  uint16_t reserved = 0;
  Snapshot previous{};
  Snapshot active{};
};

RTC_NOINIT_ATTR RetainedTrace trace;

const char* checkpointName(const uint16_t value) {
  switch (static_cast<Checkpoint>(value)) {
    case Checkpoint::NONE: return "NONE";
    case Checkpoint::SETUP_STARTED: return "SETUP_STARTED";
    case Checkpoint::SETUP_COMPLETE: return "SETUP_COMPLETE";
    case Checkpoint::LOOP_STARTED: return "LOOP_STARTED";
    case Checkpoint::GPIO_UPDATED: return "GPIO_UPDATED";
    case Checkpoint::COMPANION_LOOP_ENTER: return "COMPANION_LOOP_ENTER";
    case Checkpoint::COMPANION_RADIO_RESUME_ENTER: return "COMPANION_RADIO_RESUME_ENTER";
    case Checkpoint::COMPANION_RADIO_RESUME_EXIT: return "COMPANION_RADIO_RESUME_EXIT";
    case Checkpoint::COMPANION_ADVERTISING_POLICY_ENTER: return "COMPANION_ADVERTISING_POLICY_ENTER";
    case Checkpoint::COMPANION_ADVERTISING_STOP_ENTER: return "COMPANION_ADVERTISING_STOP_ENTER";
    case Checkpoint::COMPANION_ADVERTISING_STOP_EXIT: return "COMPANION_ADVERTISING_STOP_EXIT";
    case Checkpoint::COMPANION_ADVERTISING_START_ENTER: return "COMPANION_ADVERTISING_START_ENTER";
    case Checkpoint::COMPANION_ADVERTISING_START_EXIT: return "COMPANION_ADVERTISING_START_EXIT";
    case Checkpoint::COMPANION_ADVERTISING_POLICY_EXIT: return "COMPANION_ADVERTISING_POLICY_EXIT";
    case Checkpoint::COMPANION_LOOP_EXIT: return "COMPANION_LOOP_EXIT";
    case Checkpoint::ACTIVITY_LOOP_ENTER: return "ACTIVITY_LOOP_ENTER";
    case Checkpoint::ACTIVITY_LOOP_EXIT: return "ACTIVITY_LOOP_EXIT";
    case Checkpoint::POWER_SAVING_ENTER: return "POWER_SAVING_ENTER";
    case Checkpoint::LOOP_COMPLETE: return "LOOP_COMPLETE";
    case Checkpoint::SLEEP_ENTRY: return "SLEEP_ENTRY";
  }
  return "UNKNOWN";
}

void printSnapshot(Print& output, const char* label, const Snapshot& snapshot) {
  output.printf(
      "RUNTIME_TRACE_%s boot=%lu reset=%u loops=%lu checkpoint=%s(%u) checkpoint_ms=%lu loop_start_ms=%lu "
      "loop_complete_ms=%lu raw=0x%02X debounced=0x%02X pressed=0x%02X released=0x%02X power_held_ms=%lu\n",
      label, static_cast<unsigned long>(snapshot.bootSequence), static_cast<unsigned>(snapshot.resetReason),
      static_cast<unsigned long>(snapshot.loopCount), checkpointName(snapshot.checkpoint),
      static_cast<unsigned>(snapshot.checkpoint),
      static_cast<unsigned long>(snapshot.checkpointMs), static_cast<unsigned long>(snapshot.lastLoopStartedMs),
      static_cast<unsigned long>(snapshot.lastLoopCompletedMs), static_cast<unsigned>(snapshot.rawState),
      static_cast<unsigned>(snapshot.debouncedState), static_cast<unsigned>(snapshot.pressedEvents),
      static_cast<unsigned>(snapshot.releasedEvents), static_cast<unsigned long>(snapshot.powerHeldMs));
}

}  // namespace

void begin() {
  if (trace.magic != TRACE_MAGIC || trace.version != TRACE_VERSION) {
    std::memset(&trace, 0, sizeof(trace));
    trace.magic = TRACE_MAGIC;
    trace.version = TRACE_VERSION;
  } else {
    trace.previous = trace.active;
  }

  const uint32_t nextBoot = trace.previous.bootSequence + 1;
  std::memset(&trace.active, 0, sizeof(trace.active));
  trace.active.bootSequence = nextBoot;
  trace.active.resetReason = static_cast<uint8_t>(esp_reset_reason());
  mark(Checkpoint::SETUP_STARTED);
}

void mark(const Checkpoint checkpoint) {
  trace.active.checkpoint = static_cast<uint16_t>(checkpoint);
  trace.active.checkpointMs = millis();
  if (checkpoint == Checkpoint::LOOP_COMPLETE) trace.active.lastLoopCompletedMs = trace.active.checkpointMs;
}

void beginLoop() {
  ++trace.active.loopCount;
  trace.active.lastLoopStartedMs = millis();
  mark(Checkpoint::LOOP_STARTED);
}

void recordInput(const uint8_t rawState, const uint8_t debouncedState, const uint8_t pressedEvents,
                 const uint8_t releasedEvents, const uint32_t powerHeldMs) {
  trace.active.rawState = rawState;
  trace.active.debouncedState = debouncedState;
  trace.active.pressedEvents = pressedEvents;
  trace.active.releasedEvents = releasedEvents;
  trace.active.powerHeldMs = powerHeldMs;
}

void dump(Print& output) {
  if (trace.magic != TRACE_MAGIC || trace.version != TRACE_VERSION) {
    output.println("RUNTIME_TRACE_UNAVAILABLE");
    return;
  }
  printSnapshot(output, "PREVIOUS", trace.previous);
  printSnapshot(output, "ACTIVE", trace.active);
}

}  // namespace runtime_trace
