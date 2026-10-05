package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Instant

class GarminJavaFitDecoderTest {
    private val decoder = GarminJavaFitDecoder()
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    @Test
    fun decodesARunWithGpsAndHeartRate() {
        val fit = decoder.decode(FitFixtures.run(start))

        assertEquals("ACTIVITY", fit.fileId?.type)
        assertEquals(1, fit.sessions.size)
        assertEquals(1, fit.laps.size)
        assertEquals(FitFixtures.RUN_POINTS, fit.records.size)
        val first = fit.records.first()
        assertEquals(start.toEpochMilliseconds(), first.timestampMillis)
        assertEquals(47.6, first.latitude!!, 1e-6)
        assertEquals(-122.3, first.longitude!!, 1e-6)
        assertEquals(120, first.heartRate)

        val activity = DecodedActivityMapper.map(fit)
        assertEquals(Sport.RUN, activity.sport)
        assertEquals(start, activity.startedAt)
        assertEquals(1000.0, activity.distanceMeters!!, 0.01)
        assertEquals(155, activity.avgHeartRate)
        assertEquals("Garmin fenix7", activity.device)
        assertEquals(FitFixtures.RUN_POINTS, activity.samples.size)
    }

    @Test
    fun decodesAnIndoorRideWithPowerAndNoGps() {
        val fit = decoder.decode(FitFixtures.indoorRide(start))

        assertTrue(fit.records.all { it.latitude == null })
        assertEquals(200, fit.records.first().powerWatts)

        val activity = DecodedActivityMapper.map(fit)
        assertEquals(Sport.RIDE, activity.sport)
        assertTrue(activity.samples.isEmpty())
    }

    @Test
    fun rejectsATruncatedFile() {
        val bytes = FitFixtures.run(start)
        assertFailsWith<ActivityFileException> { decoder.decode(bytes.copyOf(bytes.size - 20)) }
    }

    @Test
    fun rejectsAFileThatIsNotFit() {
        assertFailsWith<ActivityFileException> { decoder.decode("<gpx></gpx>".encodeToByteArray()) }
    }
}
