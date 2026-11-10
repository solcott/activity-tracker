package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.fixtures.SampleKind
import io.github.solcott.activitytracker.app.text.AppText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A gear collection's title and when its Edit and Delete show and can be used, over every sample (dev/shared-display-text, "A gear
 * collection's page reads the same in both UIs"), and the ignored devices read typed.
 */
class GearPagesSamplesTest {
    private val collections = GearCollectionSamples.all + GearCollectionSamples.variants

    @Test
    fun theTitleIsTheNameOnceKnown() {
        for ((kind, state) in collections) {
            val expected =
                when (kind) {
                    SampleKind.LOADING,
                    SampleKind.FAILED -> AppText.named("gear_collections")
                    SampleKind.EMPTY -> AppText.written("Winter training")
                    else -> AppText.written("Dive kit")
                }
            assertEquals(expected, state.title, kind)
            assertEquals(expected is AppText.Written, state.showsActions, kind)
        }
    }

    @Test
    fun theActionsAreOffOnlyWhileDeleting() {
        for ((kind, state) in collections) assertEquals(kind != "deleting", state.actionsEnabled, kind)
        val deleting = GearCollectionSamples.variants.getValue("deleting")
        assertTrue(deleting.showsActions)
        assertFalse(deleting.actionsEnabled)
    }

    @Test
    fun theIgnoredDevicesAreTheLoadedList() {
        for ((kind, state) in IgnoredSensorsSamples.all + IgnoredSensorsSamples.variants) {
            assertEquals(state.content.data, state.sensors, kind)
        }
        assertEquals(listOf("edge", "strap"), IgnoredSensorsSamples.all.getValue(SampleKind.LOADED).sensors.map { it.id })
        assertTrue(IgnoredSensorsSamples.variants.getValue("nothingIgnored").sensors.isEmpty())
    }
}
