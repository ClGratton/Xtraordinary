#include "CompanionFocusActivity.h"

#ifdef ENABLE_X3_COMPANION

#include <HalDisplay.h>
#include <I18n.h>

#include <cstdio>
#include <cstring>

#include "components/UITheme.h"
#include "fontIds.h"

namespace {
constexpr uint8_t DIGIT_SEGMENTS[10] = {
    0x3f,  // 0: a b c d e f
    0x06,  // 1: b c
    0x5b,  // 2: a b d e g
    0x4f,  // 3: a b c d g
    0x66,  // 4: b c f g
    0x6d,  // 5: a c d f g
    0x7d,  // 6: a c d e f g
    0x07,  // 7: a b c
    0x7f,  // 8
    0x6f,  // 9: a b c d f g
};

void drawDigit(const GfxRenderer& renderer, int x, int y, int width, int height, int thickness, uint8_t digit) {
  if (digit > 9) return;
  const uint8_t segments = DIGIT_SEGMENTS[digit];
  const int half = height / 2;
  if (segments & 0x01) renderer.fillRect(x + thickness, y, width - thickness * 2, thickness, true);
  if (segments & 0x02) renderer.fillRect(x + width - thickness, y + thickness, thickness, half - thickness, true);
  if (segments & 0x04) renderer.fillRect(x + width - thickness, y + half, thickness, half - thickness, true);
  if (segments & 0x08)
    renderer.fillRect(x + thickness, y + height - thickness, width - thickness * 2, thickness, true);
  if (segments & 0x10) renderer.fillRect(x, y + half, thickness, half - thickness, true);
  if (segments & 0x20) renderer.fillRect(x, y + thickness, thickness, half - thickness, true);
  if (segments & 0x40)
    renderer.fillRect(x + thickness, y + half - thickness / 2, width - thickness * 2, thickness, true);
}
}  // namespace

void CompanionFocusActivity::onEnter() {
  Activity::onEnter();
  renderer.setOrientation(GfxRenderer::Portrait);
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
  char minutes[12];
  std::snprintf(minutes, sizeof(minutes), "%lu", static_cast<unsigned long>(shownMinute_));
  const int width = renderer.getScreenWidth();
  const int height = renderer.getScreenHeight();
  renderer.clearScreen();
  renderer.drawRoundedRect(30, 30, width - 60, height - 60, 4, 26, true);
  renderer.drawCenteredText(UI_12_FONT_ID, 126, session_.title(), true, EpdFontFamily::BOLD);

  const size_t digitCount = std::strlen(minutes);
  const int digitWidth = digitCount <= 2 ? 86 : (digitCount == 3 ? 68 : 52);
  const int digitHeight = digitCount <= 2 ? 190 : 168;
  const int digitGap = digitCount <= 2 ? 16 : 12;
  const int thickness = digitCount <= 2 ? 15 : 12;
  const int labelWidth = renderer.getTextWidth(UI_12_FONT_ID, tr(STR_MIN_SHORT), EpdFontFamily::BOLD);
  const int numberWidth = static_cast<int>(digitCount) * digitWidth +
                          static_cast<int>(digitCount > 0 ? digitCount - 1 : 0) * digitGap;
  const int contentWidth = numberWidth + 16 + labelWidth;
  int digitX = (width - contentWidth) / 2;
  const int digitY = 236;
  for (size_t index = 0; index < digitCount; ++index) {
    drawDigit(renderer, digitX, digitY, digitWidth, digitHeight, thickness,
              static_cast<uint8_t>(minutes[index] - '0'));
    digitX += digitWidth + digitGap;
  }
  renderer.drawText(UI_12_FONT_ID, digitX + 4, digitY + digitHeight - 44, tr(STR_MIN_SHORT), true,
                    EpdFontFamily::BOLD);

  const char* state = session_.phase() == companion::SessionPhase::PAUSED
                          ? tr(STR_PAUSED)
                          : (session_.phase() == companion::SessionPhase::COMPLETE ? tr(STR_COMPLETE) : tr(STR_FOCUS));
  renderer.drawCenteredText(UI_10_FONT_ID, 514, state);
  const auto labels = mappedInput.mapLabels(tr(STR_BACK), "", "", "");
  GUI.drawButtonHints(renderer, labels.btn1, labels.btn2, labels.btn3, labels.btn4);
  renderer.displayBuffer(HalDisplay::FAST_REFRESH);
}
#endif
