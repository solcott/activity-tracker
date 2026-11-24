package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.edit.ActivityTextLimits
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** When the activity forms count a title or description, and when they hold Save back. */
class ActivityTextLimitsTest {
    @Test
    fun theCounterShowsOnlyNearTheLimit() {
        assertNull(ActivityTextLimits.descriptionCounter("x".repeat(899)))
        val near = ActivityTextLimits.descriptionCounter("x".repeat(900)) as AppText.Named
        assertEquals("details_length", near.name)
        assertEquals(listOf("900", formatWhole(1_000L)), near.args.map { (it as AppText.Written).text })
        assertEquals("90", ((ActivityTextLimits.titleCounter("x".repeat(90)) as AppText.Named).args[0] as AppText.Written).text)
    }

    @Test
    fun textFitsUpToTheLimitCountedAsItSaves() {
        assertTrue(ActivityTextLimits.titleFits("x".repeat(100)))
        assertTrue(ActivityTextLimits.titleFits("  " + "x".repeat(100) + "\n"), "spaces round it are trimmed when saved")
        assertFalse(ActivityTextLimits.titleFits("x".repeat(101)))
        assertFalse(ActivityTextLimits.fits("Ride", "x".repeat(1_001)))
        assertTrue(ActivityTextLimits.fits("Ride", "😀".repeat(1_000)), "an emoji counts once")
    }
}
