package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The average temperature [DecodedActivityMapper] publishes: the samples' over time, where Garmin's session gives whole degrees. */
class DecodedTemperatureTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")

    private val file = DecodedSource("activity", "garmin", "edge840", start.toEpochMilliseconds())

    private fun session(temperature: Double? = null) =
        DecodedSummary(
            "cycling",
            "mountain",
            start.toEpochMilliseconds(),
            1800.0,
            1700.0,
            5000.0,
            42.0,
            null,
            4.2,
            150,
            175,
            null,
            null,
            temperature,
        )

    private fun record(sec: Long, temperature: Double?) =
        DecodedSample(
            (start + sec.seconds).toEpochMilliseconds(),
            47.6 + sec * 1e-4,
            -122.3,
            null,
            null,
            null,
            null,
            null,
            null,
            temperature,
        )

    @Test
    fun averageTemperatureComesFromTheSamples() {
        // Garmin's whole-degree session average (19 °C) beside samples averaging 19.25 °C over time.
        val records = (0L until 400L).map { record(it, temperature = if (it < 300) 19.0 else 20.0) }
        val activity = DecodedActivityMapper.map(DecodedActivity(file, listOf(session(temperature = 19.0)), emptyList(), records))

        assertEquals(19.25, activity.avgTemperatureCelsius)
    }

    @Test
    fun averageTemperatureWeighsEachSampleByItsTime() {
        // Smart recording: 18 °C held 8 s, then 22 °C held 2 s at a time.
        val records = (0L until 100L step 10).flatMap { listOf(record(it, temperature = 18.0), record(it + 8, temperature = 22.0)) }
        val activity = DecodedActivityMapper.map(DecodedActivity(file, listOf(session()), emptyList(), records))

        assertEquals(18.8, activity.avgTemperatureCelsius!!, 0.05)
    }

    @Test
    fun aPauseCountsItsTemperatureOnce() {
        val records = (0L until 10L).map { record(it, temperature = 20.0) } + record(610, temperature = 10.0)
        val activity = DecodedActivityMapper.map(DecodedActivity(file, listOf(session()), emptyList(), records))

        assertEquals(20.0, activity.avgTemperatureCelsius!!, 1.0)
    }

    @Test
    fun withoutTemperatureSamplesTheSessionAverageStands() {
        val activity =
            DecodedActivityMapper.map(
                DecodedActivity(file, listOf(session(temperature = 19.0)), emptyList(), listOf(record(0, temperature = null)))
            )

        assertEquals(19.0, activity.avgTemperatureCelsius)
    }
}
