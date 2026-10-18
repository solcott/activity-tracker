package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.UddfFixtures.decoDive
import io.github.solcott.activitytracker.activityfile.UddfFixtures.logbook
import io.github.solcott.activitytracker.activityfile.UddfFixtures.nitroxDive
import io.github.solcott.activitytracker.activityfile.UddfFixtures.richDive
import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
        assertEquals(
            listOf(DiveTank(200.0, 50.0, null, volumeLiters = 12.0, gas = DiveGas(32, 0), sensor = "tank0")),
            dive.tanks,
            "Pascal to bar, with the tank's volume and mix",
        )
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

    private fun decode(dive: String) = UddfParser.parse(logbook(listOf(dive)), zone).single()

    @Test
    fun keepsEachSwitchAtItsWaypointsTime() {
        val start = Instant.parse("2024-05-12T20:30:00Z").toEpochMilliseconds()
        assertEquals(
            listOf(DecodedGasSwitch(start, DiveGas(21, 35)), DecodedGasSwitch(start + 2_400_000, DiveGas(50, 0))),
            decode(decoDive).gasSwitches,
        )
        val rich = decode(richDive)
        assertEquals(listOf(0L, 1500L), rich.gasSwitches.map { (it.timestampMillis - rich.sessions.single().startTimeMillis!!) / 1000 })
        assertEquals(DiveGas(50, 0), rich.gasSwitches.last().gas, "a switchmix before its waypoint's divetime still takes that time")
    }

    @Test
    fun readsTheWaypointsDiveStateAndHeartRate() {
        val rich = decode(richDive)
        assertEquals(listOf(80, 95, 90, null), rich.records.map { it.heartRate })
        assertEquals(0.32, rich.records[0].ppO2Bar!!, 1e-6, "Pascal to bar")
        val bottom = rich.records[1]
        assertEquals(1.30, bottom.ppO2Bar!!, 1e-6)
        assertEquals(listOf(1.18, 1.20, 1.22), bottom.measuredPpO2Bars.map { kotlin.math.round(it * 100) / 100 })
        assertEquals(12, bottom.cnsPercent)
        assertEquals(840L, bottom.ndlSeconds)
        val stop = rich.records[2]
        assertEquals(3.0, stop.nextStopDepthMeters)
        assertEquals(180L, stop.nextStopSeconds)
        assertNull(rich.records[0].nextStopDepthMeters)
    }

    @Test
    fun readsEachWaypointsAlarmsAsAlertsAtItsTime() {
        val rich = decode(richDive)
        val start = rich.sessions.single().startTimeMillis!!
        assertEquals(
            listOf(600L to DiveAlertKind.RBT, 2400L to DiveAlertKind.ASCENT_CRITICAL, 2400L to "wetnotes"),
            rich.diveAlerts.map { (it.timestampMillis - start) / 1000 to it.kind },
            "an alarm UDDF doesn't name keeps its text",
        )
        assertTrue(decode(nitroxDive()).diveAlerts.isEmpty())
    }

    @Test
    fun readsTankPressureByTheTankItRefersTo() {
        val rich = decode(richDive)
        val tank = rich.dive!!.tanks.single()
        assertEquals("tank_main", tank.sensor)
        val start = rich.sessions.single().startTimeMillis!!
        assertEquals(
            listOf(0L to 200.0, 600L to 150.0, 2400L to 60.0),
            rich.tankUpdates.map { (it.timestampMillis - start) / 1000 to it.pressureBar },
        )
        assertTrue(rich.tankUpdates.all { it.sensor == "tank_main" })
    }

    @Test
    fun readsATanksVolumeAndMix() {
        val tank = decode(richDive).dive!!.tanks.single()
        assertEquals(12.0, tank.volumeLiters!!, 1e-9, "cubic meters to liters")
        assertEquals(DiveGas(32, 0), tank.gas)
        assertEquals(200.0 to 60.0, tank.startPressureBar to tank.endPressureBar)
    }

    @Test
    fun readsWhatsLoggedBeforeAndAfterTheDive() {
        val rich = decode(richDive)
        val dive = rich.dive!!
        assertEquals(30.0, dive.airTemperatureCelsius!!, 0.01)
        assertEquals(450.0, dive.altitudeMeters)
        assertEquals(15.0, dive.visibilityMeters)
        assertEquals(64800.0, dive.noFlyTimeSeconds)
        assertEquals(72000.0, dive.desaturationTimeSeconds)
        assertEquals(1.4, dive.highestPpO2Bar!!, 1e-6)
        assertEquals("Turtle at the wall", rich.notes)
    }

    @Test
    fun aBuddyLinkIsIgnored() {
        val rich = decode(richDive)
        val text = rich.toString()
        assertFalse("Rivera" in text || "buddy_sam" in text, text)
        assertEquals("Blue Hole", rich.title, "the site is still found among the links")
    }

    @Test
    fun aDiveWithoutTheseHasNone() {
        val nitrox = decode(nitroxDive())
        assertNull(nitrox.notes)
        assertTrue(nitrox.records.all { it.ppO2Bar == null && it.cnsPercent == null && it.heartRate == null })
        assertTrue(nitrox.tankUpdates.isEmpty())
        assertNull(nitrox.dive!!.airTemperatureCelsius)
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
