#pragma once

#include "CompanionProtocol.h"

namespace companion {

// Final sync runs on the bounded BLE lifecycle worker while the main task is
// committed to visible sleep. Keep state/control updates available, but defer
// snapshot scans and bulk transports whose SD/file work belongs to the normal
// main-loop lifecycle. Android retains desired state and replays NACKed work on
// the next protocol-ready link.
constexpr bool acceptsDuringFinalSync(MessageType type) {
  switch (type) {
    case MessageType::HELLO:
    case MessageType::GET_STATUS:
    case MessageType::ACK_STATUS:
    case MessageType::SET_CLOCK:
    case MessageType::START_SESSION:
    case MessageType::PAUSE_SESSION:
    case MessageType::RESUME_SESSION:
    case MessageType::STOP_SESSION:
    case MessageType::SHOW_TICKET:
    case MessageType::CLEAR_TICKET:
    case MessageType::SET_RADIO_POLICY:
    case MessageType::SET_READER_POLICY:
    case MessageType::ACQUIRE_INTERACTIVE_LEASE:
    case MessageType::GET_READING_STATS:
    case MessageType::ACK_READING_STATS:
      return true;
    default:
      return false;
  }
}

static_assert(acceptsDuringFinalSync(MessageType::SET_RADIO_POLICY));
static_assert(acceptsDuringFinalSync(MessageType::START_SESSION));
static_assert(acceptsDuringFinalSync(MessageType::GET_READING_STATS));
static_assert(!acceptsDuringFinalSync(MessageType::GET_LIBRARY));
static_assert(!acceptsDuringFinalSync(MessageType::BEGIN_BOOK_UPLOAD));
static_assert(!acceptsDuringFinalSync(MessageType::BEGIN_FIRMWARE));

}  // namespace companion
