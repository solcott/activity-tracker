package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FitActivityMapperTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private fun summary(
        sport: String? = "running",
        subSport: String? = null,
        elapsed: Double? = 1800.0,
        timer: Double? = 1700.0,
        distance: Double? = 5000.0,
        ascent: Double? = 42.0,
        avgHr: Int? = 150,
        maxHr: Int? = 175,
        maxSpeed: Double? = 4.2,
        temperature: Double? = 12.0,
    ) =
        FitSummary(
            sport,
            subSport,
            start.toEpochMilliseconds(),
            elapsed,
            timer,
            distance,
            ascent,
            null,
            maxSpeed,
            avgHr,
            maxHr,
            null,
            null,
            temperature,
        )

    private fun record(
        sec: Long,
        lat: Double? = 47.6 + sec * 1e-4,
        lon: Double? = -122.3,
        alt: Double? = null,
        hr: Int? = null,
        distance: Double? = null,
    ) = FitRecord((start + sec.seconds).toEpochMilliseconds(), lat, lon, alt, hr, null, null, null, distance, null)

    private fun fileId(type: String? = "activity") = FitFileId(type, "garmin", "fenix7", start.toEpochMilliseconds())

    @Test
    fun sessionTotalsWin() {
        val activity = FitActivityMapper.map(FitActivity(fileId(), listOf(summary()), emptyList(), listOf(record(0), record(10))))

        assertEquals(Sport.RUN, activity.sport)
        assertEquals(start, activity.startedAt)
        assertEquals(30.minutes, activity.elapsed)
        assertEquals(1700L, activity.movingSeconds)
        assertEquals(5000.0, activity.distanceMeters)
        assertEquals(42.0, activity.elevationGainMeters)
        assertEquals(150, activity.avgHeartRate)
        assertEquals(175, activity.maxHeartRate)
        assertEquals(4.2, activity.maxSpeedMps)
        assertEquals(12.0, activity.avgTemperatureCelsius)
        assertEquals("Garmin fenix7", activity.device)
        assertEquals(2, activity.samples.size)
    }

    @Test
    fun withoutASessionTotalsComeFromRecords() {
        val records =
            listOf(
                record(0, alt = 100.0, hr = 120, distance = 0.0),
                record(60, alt = 101.0, hr = 140, distance = 200.0), // inside the hysteresis: no climb yet
                record(120, alt = 110.0, hr = 160, distance = 400.0),
            )
        val activity = FitActivityMapper.map(FitActivity(fileId(), emptyList(), emptyList(), records.reversed()))

        assertEquals(Sport.OTHER, activity.sport)
        assertEquals(start, activity.startedAt)
        assertEquals(2.minutes, activity.elapsed)
        assertNull(activity.movingSeconds)
        assertEquals(400.0, activity.distanceMeters)
        assertEquals(10.0, activity.elevationGainMeters)
        assertEquals(140, activity.avgHeartRate)
        assertEquals(160, activity.maxHeartRate)
        assertEquals(start, activity.samples.first().time, "samples are in time order")
    }

    @Test
    fun indoorActivityHasNoSamples() {
        val activity =
            FitActivityMapper.map(
                FitActivity(
                    fileId(),
                    listOf(summary(sport = "training", subSport = "indoor_cycling")),
                    emptyList(),
                    listOf(record(0, lat = null, lon = null)),
                )
            )
        assertEquals(Sport.RIDE, activity.sport)
        assertTrue(activity.samples.isEmpty())
    }

    @Test
    fun multipleSessionsAddUp() {
        val activity =
            FitActivityMapper.map(
                FitActivity(
                    fileId(),
                    listOf(summary(avgHr = 140, timer = 1000.0), summary(sport = "cycling", avgHr = 160, timer = 3000.0)),
                    emptyList(),
                    emptyList(),
                )
            )
        assertEquals(Sport.RUN, activity.sport)
        assertEquals(10_000.0, activity.distanceMeters)
        assertEquals(4000L, activity.movingSeconds)
        assertEquals(155, activity.avgHeartRate)
    }

    @Test
    fun mapsSportNamesInEverySdksSpelling() {
        assertEquals(Sport.RIDE, FitActivityMapper.sport("E_BIKING"))
        assertEquals(Sport.RIDE, FitActivityMapper.sport("eBiking"))
        assertEquals(Sport.SKI, FitActivityMapper.sport("CROSS_COUNTRY_SKIING"))
        assertEquals(Sport.SKI, FitActivityMapper.sport("crossCountrySkiing"))
        assertEquals(Sport.HIKE, FitActivityMapper.sport("hiking"))
        assertEquals(Sport.SWIM, FitActivityMapper.sport("SWIMMING"))
        assertEquals(Sport.RUN, FitActivityMapper.sport("training", "treadmill"))
        assertEquals(Sport.OTHER, FitActivityMapper.sport("rowing"))
        assertEquals(Sport.OTHER, FitActivityMapper.sport(null))
    }

    @Test
    fun rejectsOtherFileTypes() {
        assertFailsWith<ActivityFileException> {
            FitActivityMapper.map(FitActivity(fileId(type = "COURSE"), listOf(summary()), emptyList(), emptyList()))
        }
    }

    @Test
    fun rejectsFilesWithNoActivityData() {
        assertFailsWith<ActivityFileException> { FitActivityMapper.map(FitActivity(fileId(), emptyList(), emptyList(), emptyList())) }
    }

    @Test
    fun semicircles() {
        assertEquals(180.0, FitUnits.semicirclesToDegrees(Int.MAX_VALUE), 1e-6)
        assertEquals(-90.0, FitUnits.semicirclesToDegrees(Int.MIN_VALUE / 2), 1e-9)
    }
}
