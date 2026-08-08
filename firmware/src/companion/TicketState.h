#pragma once

#ifdef ENABLE_X3_COMPANION

#include <cstdint>

namespace companion {

enum class TicketMode : uint8_t {
  STATIC = 0x00,
  LIVE = 0x01,
};

struct TicketState {
  TicketMode mode = TicketMode::STATIC;
  char origin[4] = {};
  char destination[4] = {};
  char flight[17] = {};
  char status[25] = {};
  char departureTime[17] = {};
  char gate[9] = {};
  char terminal[9] = {};
  char seat[9] = {};
  char passenger[41] = {};
  char boardingGroup[25] = {};
  char barcodePayload[257] = {};
};

}  // namespace companion

#endif
