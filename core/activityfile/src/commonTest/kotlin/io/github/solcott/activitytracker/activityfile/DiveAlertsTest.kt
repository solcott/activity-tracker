package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiveAlertsTest {
    @Test
    fun uddfAlarmsMapOntoFitTokensOrTheirOwn() {
        assertEquals(DiveAlertKind.ASCENT_CRITICAL, DiveAlerts.uddfToken("ascent"))
        assertEquals(DiveAlertKind.DECO_CEILING_BROKEN, DiveAlerts.uddfToken("deco"))
        assertEquals(DiveAlertKind.NEAR_SURFACE, DiveAlerts.uddfToken(" Surface "))
        assertEquals(DiveAlertKind.RBT, DiveAlerts.uddfToken("rbt"))
        assertEquals(DiveAlertKind.SKIN_COOLING, DiveAlerts.uddfToken("skincooling"))
        assertEquals("wetnotes", DiveAlerts.uddfToken("wetnotes"), "an unknown alarm keeps its text")
        assertNull(DiveAlerts.uddfToken("  "))
        assertEquals("ascent", DiveAlerts.byToken(DiveAlertKind.ASCENT_CRITICAL)?.uddfAlarm)
        assertNull(DiveAlerts.byToken(DiveAlertKind.BATTERY_LOW)?.uddfAlarm)
    }

    @Test
    fun fitValuesResolveAndDismissalsAreDropped() {
        assertEquals(DiveAlertKind.NDL_REACHED, DiveAlerts.fitToken(0))
        assertEquals(DiveAlertKind.TANK_BATTERY_LOW, DiveAlerts.fitToken(32))
        assertNull(DiveAlerts.fitToken(18))
        assertNull(DiveAlerts.fitToken(19))
        assertEquals(17, DiveAlerts.byToken(DiveAlertKind.ASCENT_CRITICAL)?.fitValue)
        assertNull(DiveAlerts.byToken(DiveAlertKind.MICROBUBBLES)?.fitValue)
    }

    @Test
    fun unknownFitValuesKeepTheSdksNameOrTheirNumber() {
        assertEquals("someNewAlert", DiveAlerts.fitToken(120, "SOME_NEW_ALERT"))
        assertEquals("someNewAlert", DiveAlerts.fitToken(120, "someNewAlert"))
        assertEquals("120", DiveAlerts.fitToken(120, "120"))
        assertEquals("120", DiveAlerts.fitToken(120))
    }

    @Test
    fun warningsAreMarkedAndInformationalAlertsAndUnknownKindsAreNot() {
        assertTrue(DiveAlerts.isWarning(DiveAlertKind.NDL_REACHED))
        assertTrue(DiveAlerts.isWarning(DiveAlertKind.MICROBUBBLES))
        assertFalse(DiveAlerts.isWarning(DiveAlertKind.SAFETY_STOP_COMPLETE))
        assertFalse(DiveAlerts.isWarning(DiveAlertKind.SETPOINT_SWITCH_AUTO_HIGH))
        assertFalse(DiveAlerts.isWarning("someNewAlert"))
    }

    @Test
    fun tokensAreUniqueAndWithinTheLexiconsLength() {
        assertEquals(DiveAlerts.ALL.size, DiveAlerts.ALL.map { it.token }.toSet().size)
        assertEquals(DiveAlerts.ALL.mapNotNull { it.fitValue }.size, DiveAlerts.ALL.mapNotNull { it.fitValue }.toSet().size)
        assertTrue(DiveAlerts.ALL.all { it.token.length <= 64 })
    }
}
