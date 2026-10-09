package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.SubsurfaceFixtures.ccrDive
import io.github.solcott.activitytracker.activityfile.SubsurfaceFixtures.logbook
import io.github.solcott.activitytracker.activityfile.SubsurfaceFixtures.trip
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset

class SubsurfaceXmlParserTest {
    /** Cozumel. */
    private val zone = FixedOffsetTimeZone(UtcOffset(hours = -5))

    private fun read(xml: String) = SubsurfaceXmlParser.parse(xml, zone).map(DecodedActivityMapper::map)

    @Test
    fun readsDivesInTripsAndOutside() {
        val dives = read(logbook())
        assertEquals(listOf(41, 42), dives.map { it.dive?.diveNumber })
        assertEquals(Instant.parse("2024-03-02T14:15:00Z"), dives[0].startedAt)
        assertEquals(LocalDateTime(2024, 3, 2, 9, 15, 0), dives[0].localStart)
    }

    @Test
    fun readsANitroxDive() {
        val activity = read(logbook()).first()
        assertEquals(Sport.DIVE, activity.type.sport)
        assertEquals(SubSport.SINGLE_GAS, activity.type.subSport)
        assertEquals(52.minutes + 30.seconds, activity.elapsed)
        assertEquals("Suunto D5", activity.device)
        assertEquals("Palancar Gardens", activity.title)

        val dive = assertNotNull(activity.dive)
        assertEquals(24.3, dive.maxDepthMeters)
        assertEquals(14.1, dive.avgDepthMeters)
        assertEquals(27.0, dive.minWaterTempCelsius!!, 0.01, "samples without temp don't count as 0")
        assertEquals(listOf(DiveGas(32, 0)), dive.gases)
        assertEquals(listOf(DiveTank(206.8, 68.9, null)), dive.tanks)
        assertEquals(DivePosition(20.354, -87.026), dive.entry)
        assertEquals(listOf(10L, 600L, 1800L, 3140L), dive.profile.map { it.seconds })
    }

    @Test
    fun readsARebreatherDiveFromItsFirstComputer() {
        val activity = read(logbook()).last()
        assertEquals(SubSport.CCR, activity.type.subSport)
        assertEquals(65.minutes, activity.elapsed, "hours in the duration")
        assertEquals("Shearwater Petrel", activity.device)
        assertNull(activity.title)
        assertEquals(22.0, activity.avgTemperatureCelsius!!, 0.01, "the header's water temperature, with none in the samples")

        val dive = assertNotNull(activity.dive)
        assertEquals(45.0, dive.maxDepthMeters, "not the second computer's")
        assertEquals(true, dive.closedCircuit)
        assertEquals(listOf(DiveGas(100, 0), DiveGas(21, 35)), dive.gases)
        assertEquals(emptyList(), dive.tanks, "no pressures logged")
        assertEquals(3, dive.profile.size)
    }

    @Test
    fun readsUnitSuffixes() {
        assertEquals(3930.0, SubsurfaceXmlParser.seconds("1:05:30 min"))
        assertEquals(2730.0, SubsurfaceXmlParser.seconds("45:30 min"))
        assertEquals(30.48, SubsurfaceXmlParser.meters("100 ft")!!, 0.001)
        assertEquals(25.0, SubsurfaceXmlParser.celsius("77 F")!!, 0.001)
        assertEquals(200.0, SubsurfaceXmlParser.bar("2900.6 psi")!!, 0.1)
    }

    @Test
    fun skipsAnUndatedDive() {
        assertEquals(listOf(42), read(logbook(trip(date = "") + ccrDive)).map { it.dive?.diveNumber })
    }

    @Test
    fun rejectsALogbookWithNoDives() {
        assertFailsWith<ActivityFileException> { read(logbook(dives = "")) }
        assertFailsWith<ActivityFileException> { read(logbook(trip(date = ""))) }
    }
}
