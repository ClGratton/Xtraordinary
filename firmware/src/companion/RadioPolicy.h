#pragma once

namespace companion {

// Persisted content is deliberately absent from this policy. A saved Live
// ticket is data, not a runtime radio state: only an active Focus session or
// the currently displayed Live ticket may bypass the boot/Home fast window.
constexpr bool requiresPersistentSlowAdvertising(bool focusActive,
                                                 bool liveTicketDisplayed) {
  return focusActive || liveTicketDisplayed;
}

static_assert(!requiresPersistentSlowAdvertising(false, false));
static_assert(requiresPersistentSlowAdvertising(true, false));
static_assert(requiresPersistentSlowAdvertising(false, true));

}  // namespace companion
