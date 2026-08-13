#pragma once

#include <algorithm>

// Pure geometry owner for the native companion ticket. It deliberately has no
// renderer dependency so portrait and rotated safe areas are testable on host.
struct TicketRect {
  int x;
  int y;
  int width;
  int height;
};

struct TicketScaledBarcode {
  TicketRect rect;
  int scale;
};

struct TicketLayout {
  static constexpr int kHintStrip = 40;
  int width;
  int height;
  bool rotated;
  int centerX;
  int factCenters[3];
  TicketRect matrixChamber;
  TicketRect linearChamber;
  int matrixFooterY;
  int linearFooterY;

  static bool contains(const TicketRect& outer, const TicketRect& inner) {
    return inner.x >= outer.x && inner.y >= outer.y && inner.x + inner.width <= outer.x + outer.width &&
           inner.y + inner.height <= outer.y + outer.height;
  }

  static bool intersects(const TicketRect& first, const TicketRect& second) {
    return first.x < second.x + second.width && first.x + first.width > second.x &&
           first.y < second.y + second.height && first.y + first.height > second.y;
  }

  static TicketScaledBarcode integerScaledPlacement(const TicketRect& safe, int barcodeWidth, int barcodeHeight) {
    const int scale = std::max(1, std::min(safe.width / barcodeWidth,
                                           safe.height / barcodeHeight));
    const int width = barcodeWidth * scale;
    const int height = barcodeHeight * scale;
    return {{safe.x + (safe.width - width) / 2, safe.y + (safe.height - height) / 2, width, height}, scale};
  }

  static TicketLayout create(int width, int height, bool rotated) {
    const int contentBottom = rotated ? height : height - kHintStrip;
    const int safeLeft = rotated ? kHintStrip : 0;
    const int safeWidth = width - safeLeft;
    constexpr int kFactMargin = 30;
    TicketLayout layout{width, height, rotated, width / 2,
                        {kFactMargin + (width - 2 * kFactMargin) / 6, width / 2,
                         width - kFactMargin - (width - 2 * kFactMargin) / 6},
                        {40, 390, width - 80, contentBottom - 390},
                        {18, 430, width - 36, contentBottom - 430}, 374, 398};
    if (rotated) {
      // In LandscapeClockwise the physical hint strip is on x=[0,40), not at
      // the bottom. Scanner content is placed wholly to its right.
      layout.matrixChamber = {safeLeft + 20, 20, safeWidth - 40, height - 40};
      layout.linearChamber = layout.matrixChamber;
    }
    return layout;
  }
};
