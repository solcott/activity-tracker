package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.decoDive
import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.olderDive
import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.start
import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.streams
import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.write
import io.github.solcott.activitytracker.activityfile.UddfExportFixtures.zone
import io.github.solcott.activitytracker.lexicon.app.ActivityDiveGas
import io.github.solcott.activitytracker.lexicon.app.GasSwitch
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

class UddfWriterTest {
    private val reader = ActivityFileReader { error("not FIT") }

    private suspend fun readBack(activity: ExportActivity) = reader.read(write(activity).encodeToByteArray()).single().inZone(zone)

    private fun switchmixes(xml: String) =
        Regex("""<divetime>(\d+)</divetime>(?:<heartrate>\d+</heartrate>)?<switchmix ref="([^"]+)"/>""")
            .findAll(xml)
            .map { it.groupValues[1].toInt() to it.groupValues[2] }
            .toList()

    @Test
    fun aDecoDiveImportsBackAsTheSameDive() = runTest {
        val read = readBack(decoDive)
        assertEquals(Sport.DIVE, read.type.sport)
        assertEquals(start, read.startedAt)
        assertEquals(2880.seconds, read.elapsed)
        assertEquals("Shearwater Perdix 2", read.device)
        val dive = read.dive!!
        assertEquals(31.2, dive.maxDepthMeters)
        assertEquals(22.1, dive.avgDepthMeters)
        assertEquals(212, dive.diveNumber)
        assertEquals(90, dive.surfaceIntervalMinutes)
        assertEquals("Blue Hole", dive.siteName)
        assertEquals(DivePosition(17.3155, -87.5347), dive.entry)
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), dive.gases)
        assertEquals(29.1, dive.airTemperatureCelsius!!, 1e-9)
        assertEquals(25.0, dive.visibilityMeters)
        assertEquals(0.0, dive.altitudeMeters)
        assertEquals(72_000.0, dive.noFlyTimeSeconds)
        assertEquals(50_400.0, dive.desaturationTimeSeconds)
        assertEquals(1.61, dive.highestPpO2Bar)
        assertEquals(
            listOf(
                DiveTank(200.0, 84.8, null, volumeLiters = 11.1, gas = DiveGas(21, 0), sensor = "tank0"),
                DiveTank(210.0, 162.0, null, volumeLiters = 5.7, gas = DiveGas(50, 0), sensor = "tank1"),
            ),
            dive.tanks.map { it.copy(volumeLiters = it.volumeLiters?.let { v -> kotlin.math.round(v * 10) / 10 }) },
        )
        val back = read.streams!!
        assertEquals(streams.time, back.time)
        assertEquals(streams.depth, back.depth)
        assertEquals(streams.temperature, back.temperature)
        assertEquals(streams.heartRate, back.heartRate)
        assertEquals(streams.ppO2, back.ppO2)
        assertEquals(streams.cns, back.cns)
        assertEquals(streams.ndl, back.ndl)
        assertEquals(streams.decoStopDepth, back.decoStopDepth)
        assertEquals(streams.decoStopTime, back.decoStopTime)
        assertEquals(streams.gasSwitches, back.gasSwitches)
        assertEquals(streams.tankPressures, back.tankPressures)
    }

    @Test
    fun theFileHoldsOneDiveInSiUnits() {
        val xml = write(decoDive)
        assertEquals(1, Regex("<repetitiongroup ").findAll(xml).count())
        assertEquals(1, Regex("<dive ").findAll(xml).count())
        assertTrue("<greatestdepth>31.2</greatestdepth>" in xml)
        assertTrue("<diveduration>2880</diveduration>" in xml)
        assertTrue("<divenumber>212</divenumber>" in xml)
        assertTrue("<passedtime>5400</passedtime>" in xml)
        assertTrue("""<mix id="mix-21-0"><name>Air</name><o2>0.21</o2><he>0</he></mix>""" in xml)
        assertTrue("""<mix id="mix-50-0"><name>EAN50</name><o2>0.5</o2><he>0</he></mix>""" in xml)
        assertTrue("<temperature>285.65</temperature>" in xml, "Kelvin")
        assertTrue("<calculatedpo2>86000</calculatedpo2>" in xml, "Pascal")
        assertTrue("""<tankpressure ref="tank0">20000000</tankpressure>""" in xml)
        assertTrue("""<decostop kind="mandatory" decodepth="6" duration="180"/>""" in xml)
        assertEquals(listOf(0 to "mix-21-0", 1920 to "mix-50-0"), switchmixes(xml))
        assertTrue("""<divecomputer id="dc"><name>Shearwater Perdix 2</name><model>Shearwater Perdix 2</model></divecomputer>""" in xml)
    }

    @Test
    fun aFirstDiveOfTheDayHasNoSurfaceInterval() {
        val xml = write(decoDive.copy(dive = decoDive.dive!!.copy(surfaceInterval = null)))
        assertFalse("surfaceintervalbeforedive" in xml)
    }

    @Test
    fun localTimeIsWrittenInTheGivenZoneWithNoOffset() {
        assertTrue("<datetime>2026-09-20T09:10:00</datetime>" in write(decoDive))
    }

    @Test
    fun anOlderDiveSwitchesOnlyToItsFirstGasAtTheStart() = runTest {
        val xml = write(olderDive)
        assertTrue("""<mix id="mix-50-0">""" in xml, "both gases defined")
        assertEquals(listOf(0 to "mix-21-0"), switchmixes(xml))
        val read = readBack(olderDive)
        assertEquals(start, read.startedAt)
        assertEquals(streams.depth, read.streams!!.depth)
        assertEquals(31.2, read.dive!!.maxDepthMeters)
        assertEquals(DiveGas(21, 0), read.dive.gases.first())
        assertEquals(listOf(GasSwitch(0, 21, 0)), read.streams.gasSwitches)
    }

    @Test
    fun eachGasNoStoredTankHoldsGetsAGasOnlyTank() = runTest {
        val noTanks =
            decoDive.copy(
                dive = decoDive.dive!!.copy(tanks = null),
                points = decoDive.points.map { it.copy(tankPressuresBar = emptyList()) },
            )
        val xml = write(noTanks)
        assertTrue("""<tankdata id="tank0"><link ref="mix-21-0"/></tankdata>""" in xml)
        assertTrue("""<tankdata id="tank1"><link ref="mix-50-0"/></tankdata>""" in xml)
        val read = readBack(noTanks)
        assertEquals(emptyList(), read.dive!!.tanks, "a gas-only tank isn't imported as a tank")
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), read.dive.gases)
        assertEquals(streams.gasSwitches, read.streams!!.gasSwitches)

        val oneTank = decoDive.copy(dive = decoDive.dive.copy(tanks = decoDive.dive.tanks!!.take(1)))
        assertEquals(
            listOf("tank0", "tank1"),
            Regex("""<tankdata id="([^"]+)"""").findAll(write(oneTank)).map { it.groupValues[1] }.toList(),
            "the stored tank holds air, so only 50% gets a gas-only tank, numbered after it",
        )
        assertTrue("""<tankdata id="tank1"><link ref="mix-50-0"/></tankdata>""" in write(oneTank))
        assertEquals(2, Regex("<tankdata ").findAll(write(decoDive)).count(), "both gases are in stored tanks")
    }

    @Test
    fun aMergedDiveDefinesAGasOnlyItsSwitchesName() = runTest {
        val merged =
            decoDive.copy(
                dive = decoDive.dive!!.copy(gases = listOf(ActivityDiveGas(21, 0)), tanks = null),
                gasSwitches = listOf(GasSwitch(0, 21, 0), GasSwitch(1920, 32, 0)),
                points = decoDive.points.map { it.copy(tankPressuresBar = emptyList()) },
            )
        val xml = write(merged)
        assertTrue("""<mix id="mix-32-0"><name>EAN32</name><o2>0.32</o2><he>0</he></mix>""" in xml)
        assertEquals(listOf(0 to "mix-21-0", 1920 to "mix-32-0"), switchmixes(xml))
        assertEquals(listOf(GasSwitch(0, 21, 0), GasSwitch(1920, 32, 0)), readBack(merged).streams!!.gasSwitches)
    }

    @Test
    fun aClosedCircuitDiveSaysSo() = runTest {
        val ccr = decoDive.copy(dive = decoDive.dive!!.copy(closedCircuit = true))
        val xml = write(ccr)
        assertEquals(1, Regex("""<divemode type="closedcircuit"/>""").findAll(xml).count())
        assertTrue(readBack(ccr).dive!!.closedCircuit)
        assertFalse("divemode" in write(decoDive))
    }

    @Test
    fun aDiveWithNoSiteAndNoTrackHasNoDiveSite() = runTest {
        val xml = write(olderDive)
        assertFalse("divesite" in xml)
        assertFalse("""<link ref="site"/>""" in xml)
        assertNull(readBack(olderDive).dive!!.siteName)
    }

    @Test
    fun pointsWithoutDepthAndRepeatsInASecondAddNoWaypoints() {
        val first = decoDive.points.first()
        val extra =
            listOf(
                first.copy(time = start + 500.milliseconds - 1.milliseconds, latitude = 17.0, longitude = -87.0, depthMeters = 9.9),
                ExportPoint(time = start + 30.seconds, latitude = 17.0, longitude = -87.0),
            )
        val activity = decoDive.copy(points = (decoDive.points + extra).sortedBy { it.time })
        val xml = write(activity)
        assertEquals(streams.time.size, Regex("<waypoint>").findAll(xml).count())
        assertFalse("<depth>9.9</depth>" in xml, "the first point of the second is kept")
        assertFalse("<divetime>30</divetime>" in xml)
    }

    @Test
    fun specialCharactersInTheSiteAndDeviceAreEscaped() = runTest {
        val activity = decoDive.copy(dive = decoDive.dive!!.copy(site = "Dan's <Reef> & \"Wall\""), device = "A&B <DC>")
        val xml = write(activity)
        assertTrue("<name>Dan&apos;s &lt;Reef&gt; &amp; &quot;Wall&quot;</name>" in xml)
        assertTrue("<model>A&amp;B &lt;DC&gt;</model>" in xml)
        val read = readBack(activity)
        assertEquals("Dan's <Reef> & \"Wall\"", read.dive!!.siteName)
        assertEquals("A&B <DC>", read.device)
    }
}
