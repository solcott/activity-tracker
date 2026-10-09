package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.UddfFixtures.decoDive
import io.github.solcott.activitytracker.activityfile.UddfFixtures.logbook
import io.github.solcott.activitytracker.activityfile.UddfFixtures.nitroxDive
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.UtcOffset

class UddfParserTest {
    /** Belize, where the sample dives' clocks were set. */
    private val zone = FixedOffsetTimeZone(UtcOffset(hours = -6))

    private fun read(xml: String) = UddfParser.parse(xml, zone).map(DecodedActivityMapper::map)

    @Test
    fun readsEveryDiveInOrder() {
        val dives = read(logbook())
        assertEquals(listOf(12, 13), dives.map { it.dive?.diveNumber })
        assertEquals(listOf(Instant.parse("2024-05-12T16:02:00Z"), Instant.parse("2024-05-12T20:30:00Z")), dives.map { it.startedAt })
    }

    @Test
    fun readsANitroxDiveInSiUnits() {
        val activity = read(logbook()).first()
        assertEquals(Sport.DIVE, activity.type.sport)
        assertEquals(SubSport.SINGLE_GAS, activity.type.subSport)
        assertEquals(2730.seconds, activity.elapsed)
        assertEquals("Shearwater Perdix AI", activity.device, "the file's only computer, with its maker")
        assertEquals("Blue Hole", activity.title)
        assertEquals(25.0, activity.avgTemperatureCelsius!!, 0.01, "Kelvin to °C, skipping a waypoint with none")

        val dive = assertNotNull(activity.dive)
        assertEquals(30.1, dive.maxDepthMeters)
        assertEquals(17.2, dive.avgDepthMeters)
        assertEquals(24.0, dive.minWaterTempCelsius!!, 0.01)
        assertEquals(listOf(DiveGas(32, 0)), dive.gases)
        assertNull(dive.surfaceIntervalMinutes, "the first dive's interval is infinity")
        assertEquals(listOf(DiveTank(200.0, 50.0, null)), dive.tanks, "Pascal to bar")
        assertEquals(DivePosition(17.3157, -87.5346), dive.entry)
        assertEquals(listOf(0L, 600L, 1200L, 2700L), dive.profile.map { it.seconds })
    }

    @Test
    fun followsGasSwitches() {
        val deco = read(logbook()).last()
        assertEquals(SubSport.MULTI_GAS, deco.type.subSport)
        val dive = assertNotNull(deco.dive)
        assertEquals(listOf(DiveGas(21, 35), DiveGas(50, 0)), dive.gases)
        assertEquals(213, dive.surfaceIntervalMinutes)
        assertEquals(3600.seconds, deco.elapsed, "the last waypoint, with no duration given")
        assertNull(deco.title, "no site linked")
    }

    @Test
    fun keepsTheClockForTheAthleteToPlace() {
        val activity = read(logbook(listOf(nitroxDive()))).single()
        assertEquals(LocalDateTime(2024, 5, 12, 10, 2, 0), activity.localStart)
    }

    @Test
    fun takesAZonedTimeAsWritten() {
        val activity = read(logbook(listOf(nitroxDive(datetime = "2024-05-12T10:02:00-05:00")))).single()
        assertEquals(Instant.parse("2024-05-12T15:02:00Z"), activity.startedAt)
        assertNull(activity.localStart)
    }

    @Test
    fun acceptsCelsiusWrittenAsIs() {
        val xml = logbook(listOf(nitroxDive().replace("<temperature>297.15</temperature>", "<temperature>24.0</temperature>")))
        assertEquals(24.0, read(xml).single().dive!!.minWaterTempCelsius!!, 0.01)
    }

    @Test
    fun skipsAnUndatedDive() {
        val undated = decoDive.replace("<datetime>2024-05-12T14:30:00</datetime>", "")
        assertEquals(listOf(12), read(logbook(listOf(nitroxDive(), undated))).map { it.dive?.diveNumber })
    }

    @Test
    fun rejectsALogbookWithNoDives() {
        assertFailsWith<ActivityFileException> { read(logbook(emptyList())) }
    }
}
