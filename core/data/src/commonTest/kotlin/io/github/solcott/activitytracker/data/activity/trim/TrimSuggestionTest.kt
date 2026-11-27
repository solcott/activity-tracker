package io.github.solcott.activitytracker.data.activity.trim

import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.END
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.RIDE_END
import io.github.solcott.activitytracker.data.activity.trim.TrimFixtures.STANDING_START
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class TrimSuggestionTest {
    @Test
    fun carParkStartAndDriveHome() {
        val suggestion = TrimSuggestion.of(TrimFixtures.input)!!
        assertEquals(STANDING_START, suggestion.start?.seconds)
        val end = assertIs<TrimSuggestion.EndCut.Travel>(suggestion.end)
        assertEquals(3264.0, end.meters, 1.0)
        assertEquals(KeptSpan(STANDING_START, RIDE_END), suggestion.span)
    }

    @Test
    fun standingAtTheEnd() {
        // Cut the drive off: what's left ends with 9 min at the car.
        val streams = ActivityTrimmer.streams(TrimFixtures.streams, KeptSpan(0, TrimFixtures.DRIVE_START))
        val suggestion = TrimSuggestion.of(TrimInput(TrimFixtures.start, TrimFixtures.DRIVE_START, streams, emptyList()))!!
        assertEquals(TrimSuggestion.EndCut.Standing(540), suggestion.end)
    }

    @Test
    fun cleanRunHasNone() {
        val times = (0..3600 step 10).toList()
        val run = ActivityStreamData(time = times, distance = times.map { it * 300 }, speed = times.map { if (it < 20) 0 else 3000 })
        assertNull(TrimSuggestion.of(TrimInput(TrimFixtures.start, 3600, run, emptyList())))
    }

    @Test
    fun noneWithoutSpeedOrDistance() {
        val times = (0..END step 10).toList()
        val heartRateOnly = ActivityStreamData(time = times, heartRate = times.map { 120 })
        assertNull(TrimSuggestion.of(TrimInput(TrimFixtures.start, END, heartRateOnly, emptyList())))
    }
}
