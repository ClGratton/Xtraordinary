#include "CompanionTicketActivity.h"

#ifdef ENABLE_X3_COMPANION

#include <HalDisplay.h>
#include <HalStorage.h>
#include <I18n.h>
#include <Bitmap.h>

#include <algorithm>
#include <cstdio>
#include <string>

#include "companion/CompanionService.h"
#include "fontIds.h"
#include "util/QrUtils.h"

namespace {
constexpr char TICKET_BARCODE_PATH[] = "/.crosspoint/companion/ticket-barcode.bmp";
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
    if (scannerView_) {
      scannerView_ = false;
      renderer.setOrientation(GfxRenderer::Portrait);
      requestUpdate();
      return;
    }
    companion::companionService.leaveTicket();
    onGoHome(HomeMenuItem::TICKET);
    return;
  }
  if (linearBarcode_ && mappedInput.wasReleased(MappedInputManager::Button::Confirm)) {
    scannerView_ = !scannerView_;
    renderer.setOrientation(scannerView_ ? GfxRenderer::LandscapeClockwise : GfxRenderer::Portrait);
    requestUpdate();
  }
}

bool CompanionTicketActivity::renderScannerView() {
  HalFile barcodeFile = Storage.open(TICKET_BARCODE_PATH, O_RDONLY);
  if (!barcodeFile) return false;
  Bitmap barcode(barcodeFile);
  const bool valid = barcode.parseHeaders() == BmpReaderError::Ok && barcode.is1Bit() &&
                     barcode.getWidth() > barcode.getHeight() && barcode.getWidth() <= 380 &&
                     barcode.getHeight() <= 340;
  if (!valid) {
    barcodeFile.close();
    return false;
  }

  const int width = renderer.getScreenWidth();
  const int height = renderer.getScreenHeight();
  const int scale = std::max(1, std::min(2, std::min((width - 40) / barcode.getWidth(),
                                                    (height - 80) / barcode.getHeight())));
  const int barcodeWidth = barcode.getWidth() * scale;
  const int barcodeHeight = barcode.getHeight() * scale;
  renderer.drawBitmap1BitIntegerScaled(barcode, (width - barcodeWidth) / 2, (height - barcodeHeight) / 2, scale);
  barcodeFile.close();
  drawMappedButtonHints(tr(STR_BACK), "Ticket");
  renderer.displayBuffer(HalDisplay::FULL_REFRESH);
  return true;
}

void CompanionTicketActivity::render(RenderLock&&) {
  renderer.setOrientation(scannerView_ ? GfxRenderer::LandscapeClockwise : GfxRenderer::Portrait);
  const int width = renderer.getScreenWidth();
  const int height = renderer.getScreenHeight();
  renderer.clearScreen();

  if (scannerView_) {
    if (renderScannerView()) return;
    scannerView_ = false;
    linearBarcode_ = false;
    renderer.setOrientation(GfxRenderer::Portrait);
    requestUpdate();
    return;
  }

  renderer.drawText(NOTOSANS_18_FONT_ID, 30, 26, ticket_.origin, true, EpdFontFamily::BOLD);
  const int destinationWidth = renderer.getTextWidth(NOTOSANS_18_FONT_ID, ticket_.destination, EpdFontFamily::BOLD);
  renderer.drawText(NOTOSANS_18_FONT_ID, width - 30 - destinationWidth, 26, ticket_.destination, true,
                    EpdFontFamily::BOLD);
  drawRouteArrow(renderer, width / 2, 43);

  renderer.drawText(NOTOSANS_14_FONT_ID, 30, 82, ticket_.flight, true, EpdFontFamily::BOLD);
  const int statusWidth = renderer.getTextWidth(UI_10_FONT_ID, ticket_.status);
  renderer.drawText(UI_10_FONT_ID, width - 30 - statusWidth, 88, ticket_.status);
  renderer.drawLine(30, 122, width - 30, 122, 2, true);

  const auto drawFact = [&](int centerX, int labelY, int valueY, const char* label, const char* value, int valueFont) {
    const int labelWidth = renderer.getTextWidth(UI_10_FONT_ID, label);
    renderer.drawText(UI_10_FONT_ID, centerX - labelWidth / 2, labelY, label);
    const int valueWidth = renderer.getTextWidth(valueFont, value, EpdFontFamily::BOLD);
    renderer.drawText(valueFont, centerX - valueWidth / 2, valueY, value, true, EpdFontFamily::BOLD);
  };
  char delayText[16] = "-";
  if (ticket_.delayMinutes != companion::TICKET_DELAY_UNKNOWN) {
    if (ticket_.delayMinutes == 0)
      std::snprintf(delayText, sizeof(delayText), "On time");
    else
      std::snprintf(delayText, sizeof(delayText), "%+d min", ticket_.delayMinutes);
  }
  drawFact(92, 150, 184, tr(STR_DEPARTURE), ticket_.departureTime, NOTOSANS_18_FONT_ID);
  drawFact(254, 150, 184, "Arrival", ticket_.arrivalTime[0] == '\0' ? "-" : ticket_.arrivalTime,
           NOTOSANS_18_FONT_ID);
  drawFact(416, 150, 188, "Delay", delayText, NOTOSANS_14_FONT_ID);
  drawFact(92, 232, 266, tr(STR_GATE_SHORT), ticket_.gate, NOTOSANS_16_FONT_ID);
  drawFact(254, 232, 266, tr(STR_TERMINAL_SHORT), ticket_.terminal[0] == '\0' ? "-" : ticket_.terminal,
           NOTOSANS_16_FONT_ID);
  drawFact(416, 232, 266, tr(STR_SEAT_SHORT), ticket_.seat, NOTOSANS_16_FONT_ID);
  renderer.drawCenteredText(NOTOSANS_14_FONT_ID, 316, ticket_.passenger, true, EpdFontFamily::BOLD);
  renderer.drawCenteredText(UI_10_FONT_ID, 346, ticket_.boardingGroup);
  renderer.drawLine(42, 374, width - 42, 374, 1, true);

  // QR/Aztec/PDF417 and linear codes share one hierarchy. Only the scanner
  // chamber changes shape, and both chambers are anchored low on the pass.
  bool barcodeDrawn = false;
  bool linearBarcode = false;
  HalFile barcodeFile = Storage.open(TICKET_BARCODE_PATH, O_RDONLY);
  if (barcodeFile) {
    Bitmap barcode(barcodeFile);
    if (barcode.parseHeaders() == BmpReaderError::Ok && barcode.is1Bit() && barcode.getWidth() <= 380 &&
        barcode.getHeight() <= 340) {
      linearBarcode = barcode.getWidth() > barcode.getHeight();
      const int barcodePanelY = linearBarcode ? 430 : 390;
      const int barcodePanelHeight = linearBarcode ? 230 : 360;
      const int barcodePanelInset = linearBarcode ? 18 : 40;
      renderer.drawRoundedRect(barcodePanelInset, barcodePanelY, width - barcodePanelInset * 2, barcodePanelHeight, 2,
                               18, true);
      const int barcodeX = (width - barcode.getWidth()) / 2;
      const int barcodeY = barcodePanelY + (barcodePanelHeight - barcode.getHeight()) / 2;
      renderer.drawBitmap1Bit(barcode, barcodeX, barcodeY, 380, 340);
      barcodeDrawn = true;
    }
    barcodeFile.close();
  }
  // Backward compatibility for tickets stored before bitmap transport existed.
  if (!barcodeDrawn) {
    renderer.drawRoundedRect(40, 390, width - 80, 360, 2, 18, true);
    QrUtils::drawQrCode(renderer, Rect{70, 420, width - 140, 300}, std::string(ticket_.barcodePayload));
  }
  linearBarcode_ = linearBarcode;
  renderer.drawCenteredText(SMALL_FONT_ID, height - 88,
                            ticket_.mode == companion::TicketMode::LIVE ? tr(STR_LIVE_TICKET) : tr(STR_STATIC_TICKET));
  drawMappedButtonHints(tr(STR_BACK), linearBarcode ? "Scan" : "");
  renderer.displayBuffer(HalDisplay::FULL_REFRESH);
}

#endif
