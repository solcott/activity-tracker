package io.github.solcott.activitytracker.app.ui.chart

import kotlin.test.Test
import kotlin.test.assertEquals

/** A chart writes the labels it's given and no text of its own (dev/shared-display-text). */
class AxisLabelsTest {
    private val labels = listOf(AxisLabel(0.0, "0:00"), AxisLabel(900.0, "15:00"), AxisLabel(1800.0, ""))

    @Test
    fun aTickReadsItsLabel() {
        assertEquals("15:00", labelAt(labels, 900.0))
        assertEquals("15:00", labelAt(labels, 900.0 + 1e-9))
    }

    @Test
    fun anythingElseIsBlankNeverANumber() {
        assertEquals(BLANK_LABEL, labelAt(labels, 600.0))
        // On the web a string reads empty until resources load.
        assertEquals(BLANK_LABEL, labelAt(labels, 1800.0))
        assertEquals(" ", BLANK_LABEL)
    }
}
