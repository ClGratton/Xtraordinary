#include "MaintenanceLeasePolicy.h"

#include <gtest/gtest.h>

TEST(MaintenanceLeasePolicyTest, BoundsAndExpiryAreEphemeral) {
  using companion::MaintenanceLeasePolicy;
  EXPECT_EQ(MaintenanceLeasePolicy::deadline(1000, 30), 31000u);
  EXPECT_EQ(MaintenanceLeasePolicy::deadline(1000, 900), 601000u);
  EXPECT_EQ(MaintenanceLeasePolicy::deadline(1000, 0), 0u);
  EXPECT_TRUE(MaintenanceLeasePolicy::active(1000, 31000));
  EXPECT_FALSE(MaintenanceLeasePolicy::active(31000, 31000));
}
