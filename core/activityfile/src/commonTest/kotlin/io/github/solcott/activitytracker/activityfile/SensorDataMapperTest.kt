package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What the mapper makes of a file's sensor data: developer fields, counts, the UTC offset, HRV and heart rate from `hr` messages. */
class SensorDataMapperTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private fun summary(sport: String = "running", avgHr: Int? = 150, maxHr: Int? = 175) =
        DecodedSummary(sport, null, start.toEpochMilliseconds(), 1800.0, 1700.0, 5000.0, 42.0, null, 4.2, avgHr, maxHr, null, null, 12.0)

    private fun record(sec: Long, lat: Double? = 47.6 + sec * 1e-4, lon: Double? = -122.3) =
        DecodedSample((start + sec.seconds).toEpochMilliseconds(), lat, lon, null, null, null, null, null, null, null)

    private fun fileId() = DecodedSource("activity", "garmin", "fenix7", start.toEpochMilliseconds())

    private fun stryd(name: String, units: String, value: Double) = DecodedDeveloperField(name, units, value)

    @Test
    fun strydPowerFillsOnlyRecordsWithoutNativePower() {
        val fields = listOf(stryd("Power", "Watts", 250.0), stryd("Form Power", "Watts", 70.0), stryd("Leg Spring Stiffness", "KN/m", 9.5))
        val records = listOf(record(0).copy(developerFields = fields), record(10).copy(powerWatts = 300, developerFields = fields))
        val streams = DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary()), emptyList(), records)).streams!!

        assertEquals(listOf(250, 300), streams.power, "the native power wins")
        assertEquals(listOf(70, 70), streams.formPower)
        assertEquals(listOf(950, 950), streams.legSpringStiffness)
    }

    @Test
    fun developerFieldsMatchByNameAndUnitsIgnoringCase() {
        val records =
            listOf(
                record(0).copy(developerFields = listOf(stryd(" power ", "WATTS", 250.0), stryd("Wind Speed", "m/s", 3.0))),
                record(10).copy(developerFields = listOf(stryd("Power", "%", 50.0))),
            )
        val streams = DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary()), emptyList(), records)).streams!!

        assertEquals(listOf(250, null), streams.power, "a field in other units is another field")
        assertNull(streams.formPower)
    }

    @Test
    fun stepsAreTwiceTheStridesAndStrokesAsCounted() {
        fun map(sport: String, cycles: Long, strokeDistance: Double? = null) =
            DecodedActivityMapper.map(
                DecodedActivity(
                    fileId(),
                    listOf(summary(sport).copy(totalCycles = cycles, avgStrokeDistanceMeters = strokeDistance)),
                    emptyList(),
                    emptyList(),
                )
            )

        assertEquals(8400, map("running", 4200).cycles)
        val row = map("rowing", 1850, strokeDistance = 10.2)
        assertEquals(1850, row.cycles)
        assertEquals(1020, row.avgStrokeDistanceCentimeters)
        assertEquals(1500, map("cycling", 1500).cycles, "kept as counted, though not shown")
        assertNull(map("running", 0).cycles)
    }

    @Test
    fun theUtcOffsetComesFromTheActivityMessage() {
        val end = start + 30.minutes
        fun offset(localAhead: Long?) =
            DecodedActivityMapper.map(
                    DecodedActivity(
                        fileId(),
                        listOf(summary()),
                        emptyList(),
                        emptyList(),
                        activityTimestampMillis = end.toEpochMilliseconds(),
                        activityLocalTimestampSeconds = localAhead?.let { end.epochSeconds - 631_065_600L + it },
                    )
                )
                .utcOffsetMinutes

        assertEquals(120, offset(7200))
        assertEquals(-300, offset(-18_000))
        assertEquals(330, offset(19_799), "rounded to the quarter hour: India is +5:30")
        assertNull(offset(null))
        assertNull(offset(20 * 3600L), "no zone is 20 hours ahead")
    }

    @Test
    fun hrvIsSummarizedAsRmssd() {
        val rr = List(60) { if (it % 2 == 0) 0.49 else 0.51 }.toMutableList().apply { add(30, 4.0) }
        val activity =
            DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary()), emptyList(), emptyList(), rrIntervalsSeconds = rr))

        assertEquals(20, activity.hrvRmssd, "the 4 s dropout is left out")
        assertNull(DecodedActivityMapper.map(DecodedActivity(fileId(), listOf(summary()), emptyList(), emptyList())).hrvRmssd)
    }

    @Test
    fun heartRateFromHrMessagesFillsRecordsAndTheSummary() {
        val hr =
            listOf(
                DecodedHrMessage(start.toEpochMilliseconds(), eventTimestamps = listOf(500.0), filteredBpm = listOf(120)),
                DecodedHrMessage(null, eventTimestamps = (1..10).map { 500.0 + it }, filteredBpm = (1..10).map { 120 + it }),
            )
        val activity =
            DecodedActivityMapper.map(
                DecodedActivity(
                    fileId(),
                    listOf(summary("swimming", avgHr = null, maxHr = null)),
                    emptyList(),
                    listOf(record(0, lat = null, lon = null), record(5, lat = null, lon = null), record(10, lat = null, lon = null)),
                    hrMessages = hr,
                )
            )

        // Each record averages the readings since the last, a quarter-second fill of each across its 1 s gap included.
        assertEquals(listOf(120, 122, 127), activity.streams!!.heartRate)
        assertEquals(127, activity.maxHeartRate)
        assertEquals(123, activity.avgHeartRate)
    }

    @Test
    fun aRawDeveloperFieldIsScaledAsItsDescriptionSays() {
        val stiffness = FitDeveloperFieldDescription("Leg Spring Stiffness", "KN/m", scale = 100.0, offset = null)
        assertEquals(DecodedDeveloperField("Leg Spring Stiffness", "KN/m", 9.5), stiffness.field(950.0))
        assertEquals(250.0, FitDeveloperFieldDescription("Power", "Watts", null, null).field(250.0).value)
        assertEquals(250.0, FitDeveloperFieldDescription("Power", "Watts", 0.0, null).field(250.0).value, "a zero scale is no scale")
        assertEquals(-5.0, FitDeveloperFieldDescription("Offset", "x", 1.0, 10.0).field(5.0).value)
    }
}
