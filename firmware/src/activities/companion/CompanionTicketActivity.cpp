#include "CompanionTicketActivity.h"

#ifdef ENABLE_X3_COMPANION

#include <HalDisplay.h>
#include <I18n.h>

#include <cstdio>
#include <string>

#include "companion/CompanionService.h"
#include "fontIds.h"
#include "util/QrUtils.h"

namespace {
void drawRouteArrow(const GfxRenderer& renderer, int centerX, int centerY) {
  constexpr int HALF_WIDTH = 54;
  constexpr int HEAD = 12;
  renderer.drawLine(centerX - HALF_WIDTH, centerY, centerX + HALF_WIDTH, centerY, 3, true);
  renderer.drawLine(centerX + HALF_WIDTH, centerY, centerX + HALF_WIDTH - HEAD, centerY - HEAD, 3, true);
  renderer.drawLine(centerX + HALF_WIDTH, centerY, centerX + HALF_WIDTH - HEAD, centerY + HEAD, 3, true);
}
}  // namespace

void CompanionTicketActivity::onEnter() {
  Activity::onEnter();
  renderer.setOrientation(GfxRenderer::Portrait);
  requestUpdate();
}

void CompanionTicketActivity::onExit() {
  renderer.setOrientation(GfxRenderer::Portrait);
  Activity::onExit();
}

void CompanionTicketActivity::loop() {
  if (mappedInput.wasReleased(MappedInputManager::Button::Back)) {
    companion::companionService.leaveTicket();
    onGoHome(HomeMenuItem::TICKET);
  }
}

void CompanionTicketActivity::render(RenderLock&&) {
  const int width = renderer.getScreenWidth();
  const int height = renderer.getScreenHeight();
  renderer.clearScreen();

  renderer.drawText(NOTOSANS_18_FONT_ID, 30, 26, ticket_.origin, true, EpdFontFamily::BOLD);
  const int destinationWidth = renderer.getTextWidth(NOTOSANS_18_FONT_ID, ticket_.destination, EpdFontFamily::BOLD);
  renderer.drawText(NOTOSANS_18_FONT_ID, width - 30 - destinationWidth, 26, ticket_.destination, true,
                    EpdFontFamily::BOLD);
  drawRouteArrow(renderer, width / 2, 43);

  renderer.drawText(NOTOSANS_14_FONT_ID, 30, 82, ticket_.flight, true, EpdFontFamily::BOLD);
  const int statusWidth = renderer.getTextWidth(UI_10_FONT_ID, ticket_.status);
  renderer.drawText(UI_10_FONT_ID, width - 30 - statusWidth, 88, ticket_.status);
  renderer.drawLine(30, 122, width - 30, 122, 2, true);

  // The 420 px white field leaves a scanner-safe quiet zone around a QR
  // bounded to 340 px. Only black and white are used on the X3.
  renderer.drawRoundedRect(30, 142, width - 60, 420, 2, 18, true);
  QrUtils::drawQrCode(renderer, Rect{70, 182, width - 140, 340}, std::string(ticket_.barcodePayload));

  char departure[40];
  std::snprintf(departure, sizeof(departure), "%s  %s", tr(STR_DEPARTURE), ticket_.departureTime);
  renderer.drawCenteredText(UI_12_FONT_ID, 584, departure, true, EpdFontFamily::BOLD);

  char gate[40];
  char seat[24];
  if (ticket_.terminal[0] != '\0') {
    std::snprintf(gate, sizeof(gate), "%s %s  %s %s", tr(STR_TERMINAL_SHORT), ticket_.terminal,
                  tr(STR_GATE_SHORT), ticket_.gate);
  } else {
    std::snprintf(gate, sizeof(gate), "%s %s", tr(STR_GATE_SHORT), ticket_.gate);
  }
  std::snprintf(seat, sizeof(seat), "%s %s", tr(STR_SEAT_SHORT), ticket_.seat);
  renderer.drawText(UI_10_FONT_ID, 42, 640, gate, true, EpdFontFamily::BOLD);
  const int seatWidth = renderer.getTextWidth(UI_10_FONT_ID, seat, EpdFontFamily::BOLD);
  renderer.drawText(UI_10_FONT_ID, width - 42 - seatWidth, 640, seat, true, EpdFontFamily::BOLD);

  renderer.drawCenteredText(UI_10_FONT_ID, 696, ticket_.passenger);
  renderer.drawCenteredText(UI_10_FONT_ID, 730, ticket_.boardingGroup);
  renderer.drawCenteredText(SMALL_FONT_ID, height - 46,
                            ticket_.mode == companion::TicketMode::LIVE ? tr(STR_LIVE_TICKET) : tr(STR_STATIC_TICKET));
  renderer.displayBuffer(HalDisplay::FULL_REFRESH);
}

#endif
