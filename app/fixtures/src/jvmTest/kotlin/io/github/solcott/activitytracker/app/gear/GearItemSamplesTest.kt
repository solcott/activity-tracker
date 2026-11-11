package io.github.solcott.activitytracker.app.gear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** What the gear item page's samples decide, which its previews and snapshots on both platforms rely on (gear/gear-usage). */
class GearItemSamplesTest {
    private val samples = GearSamples.all + GearSamples.variants

    @Test
    fun onlyTheOwnersPagesManageComponents() {
        for ((kind, state) in samples) {
            val owner = state.page?.isOwner == true
            assertEquals(owner, state.showsEdit, kind)
            assertEquals(owner, state.managesComponents, kind)
        }
        assertFalse(GearSamples.componentsFollower.managesComponents)
        assertTrue(GearSamples.componentsFollower.showsComponents)
    }

    @Test
    fun onlyTheFailedMountShowsByComponents() {
        for ((kind, state) in samples) assertEquals(kind == "mountFailed", state.componentsError != null, kind)
        assertNull(GearSamples.mountFailed.activitiesError)
        assertFalse(GearSamples.mountSaving.mountEnabled)
    }

    @Test
    fun theUnmountStartsTheDayAfterChainFourWentOn() {
        assertEquals(LocalDate(2026, 7, 2), GearSamples.unmounting.unmountFirstDay(TimeZone.UTC))
        assertNotNull(GearSamples.replacing.picker?.replacing)
        assertTrue(GearSamples.mountingNone.picker?.candidates?.isEmpty() == true)
    }
}
