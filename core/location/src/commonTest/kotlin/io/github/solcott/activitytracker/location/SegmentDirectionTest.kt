package io.github.solcott.activitytracker.location

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SegmentDirectionTest {
    @Test
    fun aSteadyClimbIsUphillAndDownhillReversed() {
        val climb = SegmentClimb.of(gainM = 199, lossM = 0, lengthM = 2287)
        assertEquals(SegmentClimb.Uphill, climb)
        assertEquals(SegmentClimb.Downhill, climb?.reversed())
        assertEquals(SegmentClimb.Downhill, SegmentClimb.of(gainM = 0, lossM = 199, lengthM = 2287))
    }

    @Test
    fun aRollingTrailNeitherClimbsNorDescends() {
        assertNull(SegmentClimb.of(gainM = 100, lossM = 90, lengthM = 2000))
    }

    @Test
    fun aShortSteepPitchIsUnderTheMinimum() {
        assertNull(SegmentClimb.of(gainM = 12, lossM = 0, lengthM = 300))
        assertEquals(SegmentClimb.Uphill, SegmentClimb.of(gainM = 15, lossM = 0, lengthM = 300))
    }

    @Test
    fun aLongGentleClimbIsUnderTheGrade() {
        assertNull(SegmentClimb.of(gainM = 50, lossM = 0, lengthM = 2000), "2.5%")
        assertEquals(SegmentClimb.Uphill, SegmentClimb.of(gainM = 60, lossM = 0, lengthM = 2000), "3%")
    }

    @Test
    fun unknownElevationIsNeither() {
        assertNull(SegmentClimb.of(gainM = null, lossM = null, lengthM = 2000))
        assertNull(SegmentClimb.of(gainM = 100, lossM = null, lengthM = 2000))
    }

    @Test
    fun aClimbingLineIsNamedByItsClimbElseByCompass() {
        val east = listOf(GeoPoint(40.0, -111.80), GeoPoint(40.0, -111.78))
        assertEquals(SegmentDirection.Uphill, SegmentDirection.of(east, gainM = 199, lossM = 0, lengthM = 2287))
        assertEquals(SegmentDirection.Downhill, SegmentDirection.of(east, gainM = 0, lossM = 199, lengthM = 2287))
        assertEquals(SegmentDirection.Eastbound, SegmentDirection.of(east, gainM = 10, lossM = 0, lengthM = 2287))
        assertEquals(SegmentDirection.Eastbound, SegmentDirection.of(east, gainM = null, lossM = null, lengthM = 2287))
    }

    @Test
    fun aLoopIsNamedByItsTurnWhateverItsElevation() {
        val loop = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01), GeoPoint(0.01, 0.01), GeoPoint(0.01, 0.0), GeoPoint(0.0, 0.0))
        assertEquals(SegmentDirection.Counterclockwise, SegmentDirection.of(loop, gainM = 300, lossM = 0, lengthM = 4400))
    }

    @Test
    fun wordsNameDirections() {
        assertEquals(SegmentDirection.Uphill, SegmentDirection.ofWord("uphill"))
        assertEquals(SegmentDirection.Westbound, SegmentDirection.ofWord("westbound"))
        assertNull(SegmentDirection.ofWord("old"))
    }
}
