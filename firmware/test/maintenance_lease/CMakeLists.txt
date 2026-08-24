add_executable(MaintenanceLeaseTest MaintenanceLeaseTest.cpp)
target_link_libraries(MaintenanceLeaseTest PRIVATE crosspoint_test_common GTest::gtest_main)
target_include_directories(MaintenanceLeaseTest PRIVATE ../../src/companion)
gtest_discover_tests(MaintenanceLeaseTest)
