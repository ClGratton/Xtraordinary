#pragma once

#ifdef ENABLE_X3_COMPANION

#include "activities/Activity.h"
#include "companion/SessionEngine.h"

class CompanionFocusActivity final : public Activity {
  companion::SessionEngine& session_;
  uint32_t shownMinute_ = UINT32_MAX;

 public:
  CompanionFocusActivity(GfxRenderer& renderer, MappedInputManager& mappedInput, companion::SessionEngine& session)
      : Activity("CompanionFocus", renderer, mappedInput), session_(session) {}
  void onEnter() override;
  void onExit() override;
  void loop() override;
  void render(RenderLock&&) override;
  bool preventAutoSleep() override { return session_.active(); }
};
#endif
