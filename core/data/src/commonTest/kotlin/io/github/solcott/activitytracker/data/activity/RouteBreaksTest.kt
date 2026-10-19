package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RouteBreaksTest {
    private val start = Instant.parse("2026-10-19T07:00:00Z")

    /** [count] positions east along the equator from [fromMeters], [meters] apart and [every] apart in time from [at]. */
    private fun leg(at: Instant, fromMeters: Double, count: Int, meters: Double = 5.0, every: Duration = 1.seconds) =
        (0 until count).map { i -> LocationSample(0.0, (fromMeters + i * meters) / METERS_PER_DEGREE, null, null, null, at + every * i) }

    /** Ten minutes at one position a second and 5 m/s, then [gap] with no position, resuming [away] meters on. */
    private fun withGap(gap: Duration, away: Double): List<LocationSample> {
        val first = leg(start, 0.0, 600)
        val last = first.last()
        return first + leg(last.time + gap, 599 * 5.0 + away, 600)
    }

    @Test
    fun aDeadGpsBreaksTheLine() {
        assertEquals(listOf(600, 600), RouteBreaks.segments(withGap(6.minutes, away = 3_000.0)).map { it.size })
    }

    @Test
    fun aStopInOnePlaceStaysOneLine() {
        assertEquals(1, RouteBreaks.segments(withGap(30.minutes, away = 20.0)).size)
    }

    @Test
    fun aShortDropoutStaysOneLine() {
        assertEquals(1, RouteBreaks.segments(withGap(60.seconds, away = 500.0)).size)
    }

    @Test
    fun sparseSteadyRecordingStaysOneLine() {
        // A position every 60 s, 250 m apart: the gap limit is four intervals, 4 minutes.
        assertEquals(1, RouteBreaks.segments(leg(start, 0.0, 60, meters = 250.0, every = 60.seconds)).size)
    }

    @Test
    fun atMostTenBreaksAreKeptTheFurthestApart() {
        // 15 legs, the gap before leg i resuming (i * 100 + 300) m on: the four nearest (gaps 1–4) join.
        var route = leg(start, 0.0, 60)
        for (i in 1..14) route = route + leg(route.last().time + 5.minutes, route.last().longitude * METERS_PER_DEGREE + i * 100 + 300, 60)
        val segments = RouteBreaks.segments(route)
        assertEquals(RouteBreaks.MAX_BREAKS + 1, segments.size)
        assertEquals(listOf(300) + List(10) { 60 }, segments.map { it.size })
        assertEquals(route, segments.flatten())
    }

    private companion object {
        /** Along the equator, near enough for these distances. */
        const val METERS_PER_DEGREE = 111_195.0
    }
}
