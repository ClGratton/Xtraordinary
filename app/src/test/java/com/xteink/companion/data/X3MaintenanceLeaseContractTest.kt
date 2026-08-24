package com.xteink.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class X3MaintenanceLeaseContractTest {
    @Test fun leaseIsBoundedAndNeverRepresentsAUserSetting() {
        assertEquals(1, X3MaintenanceLeaseContract.boundedSeconds(-5))
        assertEquals(600, X3MaintenanceLeaseContract.boundedSeconds(900))
        assertEquals(120, X3MaintenanceLeaseContract.boundedSeconds(120))
    }
}
