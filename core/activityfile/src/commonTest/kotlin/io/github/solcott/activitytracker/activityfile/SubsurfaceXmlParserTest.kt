package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.activityfile.SubsurfaceFixtures.ccrDive
import io.github.solcott.activitytracker.activityfile.SubsurfaceFixtures.decoDive
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
        assertEquals(ImportedDevice(recorder = true, model = "Suunto D5", serialNumber = "1a2b"), activity.devices?.devices?.single())
        assertEquals("Palancar Gardens", activity.title)

        val dive = assertNotNull(activity.dive)
        assertEquals(24.3, dive.maxDepthMeters)
        assertEquals(14.1, dive.avgDepthMeters)
        assertEquals(27.0, dive.minWaterTempCelsius!!, 0.01, "samples without temp don't count as 0")
        assertEquals(listOf(DiveGas(32, 0)), dive.gases)
        assertEquals(listOf(DiveTank(206.8, 68.9, null, sensor = "0")), dive.tanks)
        assertEquals(DivePosition(20.354, -87.026), dive.entry)
        assertEquals(listOf(10L, 600L, 1800L, 3140L), dive.profile.map { it.seconds })
    }

    @Test
    fun keepsEachGasChangeAtItsEventsTime() {
        val deco =
            """<dive number='43' date='2024-03-05' time='08:00:00' duration='50:00 min'>
              <cylinder o2='21.0%' start='200.0 bar' end='80.0 bar' />
              <cylinder o2='50.0%' />
              <divecomputer model='Suunto D5'>
              <event time='32:00 min' type='25' flags='2' name='gaschange' cylinder='1' o2='50.0%' />
              <event time='40:00 min' name='gaschange' cylinder='0' />
              <event name='gaschange' cylinder='1' />
              <sample time='0:10 min' depth='2.0 m' />
              </divecomputer>
            </dive>"""
        val decoded = SubsurfaceXmlParser.parse(logbook(deco), zone).single()
        val start = decoded.sessions.single().startTimeMillis!!
        assertEquals(
            listOf(1920L to DiveGas(50, 0), 2400L to DiveGas(21, 0)),
            decoded.gasSwitches.map { (it.timestampMillis - start) / 1000 to it.gas },
            "a change with no time is dropped; a cylinder index finds its gas",
        )
        assertEquals(listOf(DiveGas(21, 0), DiveGas(50, 0)), decoded.dive?.gases)
        val ccr = SubsurfaceXmlParser.parse(logbook(ccrDive), zone).single()
        assertEquals(listOf(DecodedGasSwitch(ccr.sessions.single().startTimeMillis!!, DiveGas(21, 35))), ccr.gasSwitches)
    }

    @Test
    fun readsARebreatherDiveFromItsFirstComputer() {
        val activity = read(logbook()).last()
        assertEquals(SubSport.CCR, activity.type.subSport)
        assertEquals(65.minutes, activity.elapsed, "hours in the duration")
        assertEquals("Shearwater Petrel", activity.device)
        assertEquals(
            ImportedDevice(recorder = true, model = "Shearwater Petrel"),
            activity.devices?.devices?.single(),
            "each dive keeps the computer that logged it, here one with no device ID",
        )
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
    fun carriesEachDecoValueForwardUntilTheNextSampleThatCarriesIt() {
        val records = SubsurfaceXmlParser.parse(logbook(decoDive), zone).single().records
        assertEquals(listOf(null, 720L, 720L, 0L, 0L, 0L, 0L, 0L), records.map { it.ndlSeconds })
        val stop = records[4]
        assertEquals(3.0, stop.nextStopDepthMeters)
        assertEquals(120L, stop.nextStopSeconds)
        assertEquals(300L, stop.ttsSeconds)
        assertEquals(14, stop.cnsPercent)
        assertEquals(1.4, stop.ppO2Bar)
        assertEquals(3.0, records[5].nextStopDepthMeters, "carried forward")
        assertEquals(listOf(null, null), records.drop(6).map { it.nextStopDepthMeters }, "in_deco='0' clears the stop")
        assertEquals(listOf(null, null), records.drop(6).map { it.nextStopSeconds })
        assertEquals(1.4, records.last().ppO2Bar, "only the stop clears")

        val streams = read(logbook(decoDive)).single().streams!!
        assertEquals(listOf(null, null, null, null, 300, 300, null, null), streams.decoStopDepth)
        assertEquals(1400, streams.ppO2!![4])
    }

    @Test
    fun readsEachCylindersPressureInCylinderOrder() {
        val decoded = SubsurfaceXmlParser.parse(logbook(decoDive), zone).single()
        assertEquals(
            listOf(DiveTank(210.0, 90.0, null, sensor = "0"), DiveTank(200.0, 150.0, null, sensor = "1")),
            decoded.dive!!.tanks,
            "cylinders without start and end take them from their readings",
        )
        val start = decoded.sessions.single().startTimeMillis!!
        assertEquals(
            listOf(10L to "0", 10L to "1", 600L to "0", 1200L to "1", 1800L to "0"),
            decoded.tankUpdates.map { (it.timestampMillis - start) / 1000 to it.sensor },
        )
        assertEquals(2, read(logbook(decoDive)).single().streams!!.tankPressures!!.size)
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
