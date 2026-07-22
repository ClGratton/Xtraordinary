#include "CompanionFocusActivity.h"

#ifdef ENABLE_X3_COMPANION

#include <HalDisplay.h>

#include <cstdio>

#include "fontIds.h"

void CompanionFocusActivity::onEnter() {
  Activity::onEnter();
  renderer.setOrientation(GfxRenderer::LandscapeCounterClockwise);
  requestUpdate();
}

void CompanionFocusActivity::onExit() {
  renderer.setOrientation(GfxRenderer::Portrait);
  Activity::onExit();
}

void CompanionFocusActivity::loop() {
  session_.update();
  const uint32_t minute = (session_.remainingSeconds() + 59) / 60;
  if (minute != shownMinute_) requestUpdate();
  if (mappedInput.wasReleased(MappedInputManager::Button::Back)) {
    session_.stop();
    onGoHome();
  }
}

void CompanionFocusActivity::render(RenderLock&&) {
  shownMinute_ = (session_.remainingSeconds() + 59) / 60;
  char time[16];
  const uint32_t seconds = session_.remainingSeconds();
  std::snprintf(time, sizeof(time), "%02lu:%02lu", static_cast<unsigned long>(seconds / 60),
                static_cast<unsigned long>(seconds % 60));
  const int width = renderer.getScreenWidth();
  const int height = renderer.getScreenHeight();
  renderer.clearScreen();
  renderer.drawRoundedRect(30, 30, width - 60, height - 60, 4, 26, true);
  renderer.drawCenteredText(UI_12_FONT_ID, height / 2 - 105, session_.title(), true, EpdFontFamily::BOLD);
  renderer.drawCenteredText(UI_12_FONT_ID, height / 2 - 20, time, true, EpdFontFamily::BOLD);
  const char* state = session_.phase() == companion::SessionPhase::PAUSED
                          ? "Paused"
                          : (session_.phase() == companion::SessionPhase::COMPLETE ? "Complete" : "Focus");
  renderer.drawCenteredText(UI_10_FONT_ID, height / 2 + 74, state);
  renderer.drawCenteredText(SMALL_FONT_ID, height - 66, "Back stops this session");
  renderer.displayBuffer(HalDisplay::FAST_REFRESH);
}
#endif
