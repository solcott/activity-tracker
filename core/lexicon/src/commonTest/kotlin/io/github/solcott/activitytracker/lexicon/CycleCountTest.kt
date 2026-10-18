package io.github.solcott.activitytracker.lexicon

import io.github.solcott.activitytracker.lexicon.app.CycleCount
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CycleCountTest {
    @Test
    fun eachSportNamesWhatItCounts() {
        assertEquals(CycleCount.STEPS, CycleCount.unitOf(Sport.RUN))
        assertEquals(CycleCount.STEPS, CycleCount.unitOf(Sport.HIKE))
        assertEquals(CycleCount.STROKES, CycleCount.unitOf(Sport.SWIM))
        assertEquals(CycleCount.STROKES, CycleCount.unitOf(Sport.ROW))
        assertEquals(CycleCount.STROKES, CycleCount.unitOf(Sport.KAYAK))
        assertEquals(CycleCount.PUSHES, CycleCount.unitOf(Sport.WHEELCHAIR))
        assertNull(CycleCount.unitOf(Sport.RIDE), "a ride's crank revolutions aren't shown")
        assertNull(CycleCount.unitOf(Sport.STRENGTH))
    }
}
