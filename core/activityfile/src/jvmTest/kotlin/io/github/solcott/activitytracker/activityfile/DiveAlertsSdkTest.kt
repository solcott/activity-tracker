package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.DiveAlert
import kotlin.test.Test
import kotlin.test.assertEquals

class DiveAlertsSdkTest {
    @Test
    fun everyAlertTheJavaSdkNamesIsInTheTable() {
        val named =
            DiveAlert.entries.filter { it != DiveAlert.INVALID }.map { it.value.toInt() }.filterNot { it in DiveAlerts.FIT_DISMISSALS }
        val missing = named.filter { DiveAlerts.byFitValue(it) == null }.map { DiveAlert.getByValue(it.toShort()) }
        assertEquals(emptyList(), missing)
        // The table's FIT tokens are the SDK's names in lower camel case.
        named.forEach { value ->
            val sdk = DiveAlert.getByValue(value.toShort()).name
            assertEquals(DiveAlerts.fitToken(value + 1000, sdk), DiveAlerts.fitToken(value), sdk)
        }
    }
}
