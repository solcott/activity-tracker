package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.ShearwaterFixtures.dive
import io.github.solcott.activitytracker.activityfile.ShearwaterFixtures.record
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.offsetAt

class ShearwaterXmlParserTest {
    /** Where the sample dive was logged: its clock read 5:30 PM at UTC-6. */
    private val zone = FixedOffsetTimeZone(UtcOffset(hours = -6))

    private fun read(xml: String) = DecodedActivityMapper.map(ShearwaterXmlParser.parse(xml, zone))

    @Test
    fun readsADiveInImperialUnits() {
        val activity = read(dive())
        assertEquals(Sport.DIVE, activity.type.sport)
        assertEquals(SubSport.SINGLE_GAS, activity.type.subSport)
        assertEquals(Instant.parse("2024-10-16T23:30:57Z"), activity.startedAt)
        assertEquals(2754.seconds, activity.elapsed)
        assertEquals("Shearwater Peregrine TX", activity.device)
        assertEquals(emptyList(), activity.samples, "no positions under water")
        assertEquals(null, activity.distanceMeters)
        assertEquals(null, activity.elevationGainMeters)
        assertEquals(28.06, activity.avgTemperatureCelsius!!, 0.01, "82.5 °F")

        val dive = assertNotNull(activity.dive)
        assertEquals(21.824, dive.maxDepthMeters!!, 0.001, "71.6 ft")
        assertEquals(27.78, dive.minWaterTempCelsius!!, 0.01, "82 °F")
        assertEquals(listOf(DiveGas(32, 0)), dive.gases)
        assertEquals(false, dive.closedCircuit)
        assertEquals(21, dive.diveNumber)
        assertEquals(355, dive.surfaceIntervalMinutes)
        assertEquals(40 to 85, dive.gradientFactorLow to dive.gradientFactorHigh)
        assertEquals(1 to 10, dive.startCns to dive.endCns)
        assertEquals(listOf(0L, 10L, 20L, 30L), dive.profile.map { it.seconds })
        assertEquals(21.702, dive.profile[2].depthMeters, 0.001, "71.2 ft")
    }

    @Test
    fun keepsTheClockForTheAthleteToPlace() {
        val activity = read(dive())
        assertEquals(LocalDateTime(2024, 10, 16, 17, 30, 57), activity.localStart)

        // Logged in Cozumel (UTC-5), imported back home at UTC-6: the same 5:30 PM is an hour earlier.
        val cozumel = activity.inZone(TimeZone.of("America/Cancun"))
        assertEquals(Instant.parse("2024-10-16T22:30:57Z"), cozumel.startedAt)
        assertEquals(activity.elapsed, cozumel.elapsed)
        assertSame(activity, activity.inZone(zone), "already there")
        assertEquals(-300, cozumel.utcOffsetMinutes, "where the dive was placed")
        assertEquals(
            zone.offsetAt(activity.startedAt).totalSeconds / 60,
            activity.utcOffsetMinutes,
            "read in the device's zone until placed",
        )
    }

    @Test
    fun aZonedStartNeedsNoPlacing() {
        val activity = read(dive(startDate = "2024-10-16T23:30:57Z"))
        assertEquals(Instant.parse("2024-10-16T23:30:57Z"), activity.startedAt)
        assertEquals(null, activity.localStart)
        assertSame(activity, activity.inZone(TimeZone.UTC))
    }

    @Test
    fun zonesAreRegionsAndUtc() {
        assertTrue("America/Cancun" in TimeZones.ids)
        assertTrue("UTC" in TimeZones.ids)
        assertTrue(TimeZones.ids.none { it == "US/Central" || it.startsWith("Etc/") })
        assertEquals(TimeZones.ids.sorted(), TimeZones.ids)
    }

    @Test
    fun metricDivesStayAsWritten() {
        val activity = read(dive(records = listOf(record(0, 1.0, 28.0), record(10_000, 18.5, 27.0)), imperial = false, maxDepth = "18.7"))
        val dive = assertNotNull(activity.dive)
        assertEquals(18.7, dive.maxDepthMeters)
        assertEquals(listOf(1.0, 18.5), dive.profile.map { it.depthMeters })
        assertEquals(27.0, dive.minWaterTempCelsius)
    }

    @Test
    fun gasesPickTheSubSport() {
        val switched =
            read(
                dive(
                    records =
                        listOf(
                            record(0, 10.0, 80.0, o2 = 0.18, he = 0.45),
                            record(10_000, 20.0, 80.0, o2 = 0.5),
                            record(20_000, 0.0, 80.0, o2 = 0.5),
                        )
                )
            )
        assertEquals(SubSport.MULTI_GAS, switched.type.subSport)
        assertEquals(listOf(DiveGas(18, 45), DiveGas(50, 0)), switched.dive?.gases)

        val rebreather = read(dive(records = listOf(record(0, 10.0, 80.0, o2 = 0.21, circuit = "CC"))))
        assertEquals(SubSport.CCR, rebreather.type.subSport)
        assertEquals(true, rebreather.dive?.closedCircuit)
    }

    @Test
    fun keepsEachGasChangeAtItsRecordsTime() {
        val decoded =
            ShearwaterXmlParser.parse(
                dive(
                    records =
                        listOf(
                            record(0, 10.0, 80.0, o2 = 0.21),
                            record(1_790_000, 70.0, 80.0, o2 = 0.21),
                            record(1_800_000, 60.0, 80.0, o2 = 0.5),
                            record(1_810_000, 50.0, 80.0, o2 = 0.5),
                            record(2_000_000, 20.0, 80.0, o2 = 0.21),
                        )
                ),
                zone,
            )
        val start = decoded.sessions.single().startTimeMillis!!
        assertEquals(
            listOf(0L to DiveGas(21, 0), 1800L to DiveGas(50, 0), 2000L to DiveGas(21, 0)),
            decoded.gasSwitches.map { (it.timestampMillis - start) / 1000 to it.gas },
            "the first gas, then each change, a switch back included",
        )
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), decoded.dive?.gases, "each gas once")
    }

    @Test
    fun unknownModelsAreJustShearwater() {
        assertEquals("Shearwater", read(dive(product = "99")).device)
        assertEquals("Shearwater Petrel", read(dive(product = "3")).device)
    }

    @Test
    fun elapsedFallsBackToTheEndDate() {
        assertEquals(45.minutes + 49.seconds, read(dive(maxTime = "0")).elapsed)
    }

    @Test
    fun needsADiveLog() {
        assertFailsWith<ActivityFileException> { ShearwaterXmlParser.parse("""<dive version="3"></dive>""", zone) }
    }

    @Test
    fun readsDatesInEveryShape() {
        fun parse(text: String) = ShearwaterXmlParser.parseLocalTime(text, zone)?.let(Instant::fromEpochMilliseconds)
        val evening = Instant.parse("2024-10-16T23:30:57Z")
        assertEquals(evening, parse("10/16/2024 5:30:57 PM"))
        assertEquals(evening, parse("16/10/2024 17:30:57"), "day first")
        assertEquals(evening, parse("2024-10-16T17:30:57"))
        assertEquals(evening, parse("2024-10-16T23:30:57Z"))
        assertEquals(Instant.parse("2024-10-16T06:05:00Z"), parse("10/16/2024 12:05 AM"))
        assertEquals(Instant.parse("2024-10-16T18:05:00Z"), parse("10/16/2024 12:05 PM"))
        assertEquals(null, parse("yesterday"))
        assertEquals(null, parse("13/13/2024 10:00:00"))
    }
}
