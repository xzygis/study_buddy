package com.xzygis.studybuddy.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemAutostartSettingsTest {
    @Test
    fun mapsMajorOemsToAutostartActivities() {
        listOf(
            "xiaomi",
            "huawei",
            "honor",
            "oppo",
            "realme",
            "oneplus",
            "vivo",
            "iqoo",
        ).forEach { manufacturer ->
            assertTrue(
                "$manufacturer should have an autostart activity",
                OemAutostartSettings.componentsFor(manufacturer).isNotEmpty(),
            )
        }
    }

    @Test
    fun unknownOemFallsBackWithoutPrivateComponent() {
        assertEquals(emptyList<Any>(), OemAutostartSettings.componentsFor("unknown"))
    }
}
