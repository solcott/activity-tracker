package io.github.solcott.activitytracker.activityfile

import com.garmin.fit.ActivityMesg
import com.garmin.fit.DiveGasMesg
import com.garmin.fit.DiveGasMode
import com.garmin.fit.DiveGasStatus
import com.garmin.fit.DiveSettingsMesg
import com.garmin.fit.DiveSummaryMesg
import com.garmin.fit.Event
import com.garmin.fit.EventMesg
import com.garmin.fit.EventType
import com.garmin.fit.File
import com.garmin.fit.FileIdMesg
import com.garmin.fit.LapMesg
import com.garmin.fit.Mesg
import com.garmin.fit.MesgNum
import com.garmin.fit.RecordMesg
import com.garmin.fit.SessionMesg
import com.garmin.fit.Sport as GarminSport
import com.garmin.fit.SubSport as GarminSubSport
import com.garmin.fit.TankSummaryMesg
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.decode
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.dive
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.indoorRide
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.ride
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.start
import io.github.solcott.activitytracker.activityfile.FitExportFixtures.write
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SubSport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class FitWriterTest {
    private inline fun <T> List<Mesg>.of(num: Int, wrap: (Mesg) -> T): List<T> = filter { it.num == num }.map(wrap)

    private val Float.round3: Double
        get() = kotlin.math.round(this * 1000.0) / 1000.0

    @Test
    fun rideIsACompletedActivityFile() {
        val messages = decode(write(ride))
        val fileId = messages.of(MesgNum.FILE_ID, ::FileIdMesg).single()
        assertEquals(File.ACTIVITY, fileId.type)
        val timers = messages.of(MesgNum.EVENT, ::EventMesg).filter { it.event == Event.TIMER }
        assertEquals(listOf(EventType.START, EventType.STOP_ALL), timers.map { it.eventType })
        assertEquals(listOf(start, start + 10.seconds), timers.map { it.timestamp.date.toInstant().toKotlinInstant() })
        val session = messages.of(MesgNum.SESSION, ::SessionMesg).single()
        val lap = messages.of(MesgNum.LAP, ::LapMesg).single()
        assertEquals(start, session.startTime.date.toInstant().toKotlinInstant())
        assertEquals(session.startTime.timestamp, lap.startTime.timestamp)
        assertEquals(1, session.numLaps)
        val activity = messages.of(MesgNum.ACTIVITY, ::ActivityMesg).single()
        assertEquals(1, activity.numSessions)
        assertEquals(session.timestamp.timestamp, activity.timestamp.timestamp)
        // Summary messages come last.
        assertEquals(MesgNum.ACTIVITY, messages.last().num)
    }

    @Test
    fun recordsCarryEveryChannel() {
        val records = decode(write(ride)).of(MesgNum.RECORD, ::RecordMesg)
        assertEquals(10, records.size)
        val third = records[2]
        val point = ride.points[2]
        assertEquals(point.latitude!!, FitUnits.semicirclesToDegrees(third.positionLat), 1e-7)
        assertEquals(point.longitude!!, FitUnits.semicirclesToDegrees(third.positionLong), 1e-7)
        assertEquals(102.0, third.enhancedAltitude.toDouble(), 0.2)
        assertEquals(22.0, third.distance.toDouble(), 0.01)
        assertEquals(11.0, third.enhancedSpeed.round3)
        assertEquals(122, third.heartRate.toInt())
        assertEquals(87, third.cadence.toInt())
        assertEquals(220, third.power)
        assertEquals(18, third.temperature.toInt())
        assertNull(third.depth)
        assertEquals(start + 2.seconds, third.timestamp.date.toInstant().toKotlinInstant())
    }

    @Test
    fun indoorRecordsHaveNoPosition() {
        val records = decode(write(indoorRide)).of(MesgNum.RECORD, ::RecordMesg)
        assertTrue(records.all { it.positionLat == null && it.positionLong == null })
        assertEquals(ride.points.map { it.powerWatts }, records.map { it.power })
    }

    @Test
    fun sessionCarriesTheSummaryAndSport() {
        val session = decode(write(ride)).of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(GarminSport.CYCLING, session.sport)
        assertEquals(GarminSubSport.ROAD, session.subSport)
        assertEquals(10f, session.totalElapsedTime)
        assertEquals(9f, session.totalTimerTime)
        assertEquals(99f, session.totalDistance)
        assertEquals(9, session.totalAscent)
        assertEquals(11.0, session.enhancedAvgSpeed.round3)
        assertEquals(12.5, session.enhancedMaxSpeed.round3)
        assertEquals(125, session.avgHeartRate.toInt())
        assertEquals(129, session.maxHeartRate.toInt())
        assertEquals(18, session.avgTemperature.toInt())
    }

    @Test
    fun trailRunAndMovingTime() {
        val run = ride.copy(sport = Sport.RUN, subSport = SubSport.TRAIL, elapsed = 50.minutes, moving = 45.minutes)
        val session = decode(write(run)).of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(GarminSport.RUNNING, session.sport)
        assertEquals(GarminSubSport.TRAIL, session.subSport)
        assertEquals(3000f, session.totalElapsedTime)
        assertEquals(2700f, session.totalTimerTime)
    }

    @Test
    fun aTypeFitLacksIsGeneric() {
        val session = decode(write(ride.copy(sport = Sport.GAMING, subSport = null))).of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(GarminSport.GENERIC, session.sport)
    }

    @Test
    fun gearChangesAreEvents() {
        val gears = decode(write(ride)).of(MesgNum.EVENT, ::EventMesg).filter { it.eventType == EventType.MARKER }
        assertEquals(listOf(Event.REAR_GEAR_CHANGE, Event.FRONT_GEAR_CHANGE), gears.map { it.event })
        assertEquals(listOf(start + 2.seconds, start + 6.seconds), gears.map { it.timestamp.date.toInstant().toKotlinInstant() })
        assertEquals(listOf(2, 1), gears.map { it.frontGearNum.toInt() })
        assertEquals(listOf(5, 5), gears.map { it.rearGearNum.toInt() })
    }

    @Test
    fun twoPointsInOneSecondWriteTheFirst() {
        val first = ride.points[1]
        val second = first.copy(time = first.time + 400.milliseconds, latitude = 48.0)
        val records = decode(write(ride.copy(points = ride.points.take(2) + second + ride.points.drop(2)))).of(MesgNum.RECORD, ::RecordMesg)
        assertEquals(10, records.size)
        assertEquals(first.latitude!!, FitUnits.semicirclesToDegrees(records[1].positionLat), 1e-7)
    }

    @Test
    fun diveCarriesItsSettingsGasesTanksAndSummary() {
        val messages = decode(write(dive))
        val settings = messages.of(MesgNum.DIVE_SETTINGS, ::DiveSettingsMesg).single()
        assertEquals(30, settings.gfLow.toInt())
        assertEquals(70, settings.gfHigh.toInt())
        val gases = messages.of(MesgNum.DIVE_GAS, ::DiveGasMesg)
        assertEquals(listOf(21 to 0, 50 to 0), gases.map { it.oxygenContent.toInt() to it.heliumContent.toInt() })
        assertEquals(listOf(0, 1), gases.map { it.messageIndex })
        assertTrue(gases.all { it.status == DiveGasStatus.ENABLED && it.mode == DiveGasMode.CLOSED_CIRCUIT_DILUENT })
        val tank = messages.of(MesgNum.TANK_SUMMARY, ::TankSummaryMesg).single()
        assertEquals(200f, tank.startPressure)
        assertEquals(80f, tank.endPressure)
        assertEquals(1200f, tank.volumeUsed)
        val summary = messages.of(MesgNum.DIVE_SUMMARY, ::DiveSummaryMesg).single()
        assertEquals(MesgNum.SESSION, summary.referenceMesg)
        assertEquals(30f, summary.maxDepth)
        assertEquals(25f, summary.avgDepth)
        assertEquals(90f, summary.bottomTime)
        assertEquals(3600L, summary.surfaceInterval)
        assertEquals(42L, summary.diveNumber)
        assertEquals(5, summary.startCns.toInt())
        assertEquals(18, summary.endCns.toInt())
        assertEquals(25, summary.o2Toxicity)
        val session = messages.of(MesgNum.SESSION, ::SessionMesg).single()
        assertEquals(GarminSport.DIVING, session.sport)
        assertEquals(GarminSubSport.CCR_DIVING, session.subSport)
        assertEquals(30.0, messages.of(MesgNum.RECORD, ::RecordMesg).maxOf { it.depth.toDouble() }, 0.001)
    }

    @Test
    fun aDiveWithoutBottomTimeWritesItsElapsedTime() {
        val logbookDive = dive.copy(dive = dive.dive!!.copy(bottomTime = null))
        val summary = decode(write(logbookDive)).of(MesgNum.DIVE_SUMMARY, ::DiveSummaryMesg).single()
        assertEquals(120f, summary.bottomTime)
    }

    @Test
    fun aRunHasNoDiveMessages() {
        val nums = decode(write(ride.copy(sport = Sport.RUN, subSport = null))).map { it.num }.toSet()
        listOf(MesgNum.DIVE_SETTINGS, MesgNum.DIVE_GAS, MesgNum.TANK_SUMMARY, MesgNum.DIVE_SUMMARY).forEach { assertTrue(it !in nums) }
    }

    private fun java.time.Instant.toKotlinInstant() = kotlin.time.Instant.fromEpochMilliseconds(toEpochMilli())
}
