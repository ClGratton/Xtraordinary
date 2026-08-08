#pragma once

#ifdef ENABLE_X3_COMPANION

#include "activities/Activity.h"
#include "companion/TicketState.h"

class CompanionTicketActivity final : public Activity {
  companion::TicketState& ticket_;

 public:
  CompanionTicketActivity(GfxRenderer& renderer, MappedInputManager& mappedInput, companion::TicketState& ticket)
      : Activity("CompanionTicket", renderer, mappedInput), ticket_(ticket) {}
  void onEnter() override;
  void onExit() override;
  void loop() override;
  void render(RenderLock&&) override;
  bool preventAutoSleep() override { return ticket_.mode == companion::TicketMode::LIVE; }
};

#endif
