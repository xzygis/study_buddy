package com.xzygis.studybuddy.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OemLockScreenSettingsTest {
    @Test
    fun requiresExtraLockScreenSetupOnHuaweiAndXiaomiFamilies() {
        listOf("huawei", "xiaomi", "redmi", "poco").forEach {
            assertTrue("$it should require OEM lock-screen setup", OemLockScreenSettings.isRelevant(it))
        }
        assertFalse(OemLockScreenSettings.isRelevant("google"))
    }

    @Test
    fun mapsHuaweiToPermissionManager() {
        assertEquals(2, OemLockScreenSettings.popupComponentsFor("huawei").size)
    }

    @Test
    fun mapsXiaomiFamiliesToPermissionEditor() {
        listOf("xiaomi", "redmi", "poco").forEach {
            assertEquals(2, OemLockScreenSettings.popupComponentsFor(it).size)
        }
    }
}
