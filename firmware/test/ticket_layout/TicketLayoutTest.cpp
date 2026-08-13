#include <gtest/gtest.h>

#include "activities/companion/TicketLayout.h"

TEST(TicketLayout, PortraitUsesSymmetricFactCentersAndLeavesHintStripFree) {
  const auto layout = TicketLayout::create(528, 792, false);
  EXPECT_EQ(layout.centerX, 264);
  EXPECT_EQ(layout.factCenters[0], 108);
  EXPECT_EQ(layout.factCenters[1], 264);
  EXPECT_EQ(layout.factCenters[2], 420);
  EXPECT_EQ(layout.factCenters[0] + layout.factCenters[2], 528);
  EXPECT_LE(layout.matrixChamber.y + layout.matrixChamber.height, 752);
  EXPECT_LE(layout.linearChamber.y + layout.linearChamber.height, 752);
  EXPECT_LT(layout.matrixFooterY, layout.matrixChamber.y);
  EXPECT_FALSE(TicketLayout::intersects({0, layout.matrixFooterY, 528, 16}, layout.matrixChamber));
  EXPECT_FALSE(TicketLayout::intersects({0, layout.linearFooterY, 528, 16}, layout.linearChamber));
  EXPECT_LE(layout.matrixChamber.y + layout.matrixChamber.height, 792 - TicketLayout::kHintStrip);
}

TEST(TicketLayout, RotatedScannerAvoidsPhysicalHintAxis) {
  const auto layout = TicketLayout::create(792, 528, true);
  EXPECT_EQ(layout.matrixChamber.x, TicketLayout::kHintStrip + 20);
  EXPECT_LE(layout.matrixChamber.x + layout.matrixChamber.width, 792);
  EXPECT_LE(layout.matrixChamber.y + layout.matrixChamber.height, 528);
  const auto linear = TicketLayout::integerScaledPlacement(layout.linearChamber, 380, 140);
  const auto matrix = TicketLayout::integerScaledPlacement(layout.matrixChamber, 300, 300);
  EXPECT_TRUE(TicketLayout::contains(layout.linearChamber, linear.rect));
  EXPECT_TRUE(TicketLayout::contains(layout.matrixChamber, matrix.rect));
  EXPECT_EQ(linear.scale, 1);
}
