package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Lap
import io.github.solcott.activitytracker.lexicon.app.LapIntensity
import io.github.solcott.activitytracker.lexicon.app.LapTrigger
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class ExportWritersTest {
    private val start = Instant.parse("2026-09-20T07:00:00Z")
    private val reader = ActivityFileReader { error("not FIT") }

    private fun activity(sport: String, tcxSport: TcxSport, points: List<ExportPoint>, isDive: Boolean = false) =
        ExportActivity(
            name = "Morning <Ride> & \"more\"",
            sport = sport,
            tcxSport = tcxSport,
            startedAt = start,
            elapsed = 60.seconds,
            moving = 50.seconds,
            distanceMeters = 300.0,
            avgHeartRate = 130,
            maxHeartRate = 150,
            isDive = isDive,
            points = points,
        )

    private val ride =
        activity(
            Sport.RIDE,
            TcxSport.Biking,
            (0..3).map {
                ExportPoint(
                    time = start + it.seconds,
                    latitude = LATS[it],
                    longitude = -122.3,
                    altitudeMeters = 100.0 + it,
                    distanceMeters = it * 11.1,
                    speedMps = 11.1,
                    heartRate = 120 + it,
                    cadence = 85 + it,
                    powerWatts = 200 + it * 10,
                    temperatureCelsius = 18.5,
                )
            } + ExportPoint(time = start + 4.seconds, heartRate = 125), // no position: left out of GPX
        )

    @Test
    fun gpxPinsElementNamesAndNamespaces() {
        val gpx = GpxWriter.write(ride)
        assertTrue("""xmlns="http://www.topografix.com/GPX/1/1"""" in gpx)
        assertTrue("""xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v2"""" in gpx)
        assertTrue("<name>Morning &lt;Ride&gt; &amp; &quot;more&quot;</name>" in gpx)
        assertTrue("<type>ride</type>" in gpx)
        assertTrue(
            """<trkpt lat="47.6" lon="-122.3"><ele>100</ele><time>2026-09-20T07:00:00Z</time><extensions><power>200</power>""" +
                "<gpxtpx:TrackPointExtension><gpxtpx:atemp>18.5</gpxtpx:atemp><gpxtpx:hr>120</gpxtpx:hr><gpxtpx:cad>85</gpxtpx:cad>" +
                "<gpxtpx:speed>11.1</gpxtpx:speed></gpxtpx:TrackPointExtension></extensions></trkpt>" in gpx
        )
        assertEquals(4, Regex("<trkpt ").findAll(gpx).count())
    }

    @Test
    fun gpxReadsBackWithTheSameSamples() = runTest {
        val read = reader.read(GpxWriter.write(ride).encodeToByteArray()).single()
        assertEquals(Sport.RIDE, read.type.sport)
        assertEquals("Morning <Ride> & \"more\"", read.title)
        val withPosition = ride.points.filter { it.hasPosition }
        assertEquals(withPosition.map { it.latitude to it.longitude }, read.samples.map { it.latitude to it.longitude })
        assertEquals(withPosition.map { it.time }, read.samples.map { it.time })
        val streams = read.streams!!
        assertEquals(withPosition.map { it.heartRate }, streams.heartRate)
        assertEquals(withPosition.map { it.cadence }, streams.cadence)
        assertEquals(withPosition.map { it.powerWatts }, streams.power)
    }

    @Test
    fun aDivesTemperatureIsTheWatersAndItsDepthIsKept() {
        val dive =
            activity(
                Sport.DIVE,
                TcxSport.Other,
                listOf(ExportPoint(start, 20.5, -86.9, depthMeters = 12.34, temperatureCelsius = 27.0)),
                isDive = true,
            )
        val gpx = GpxWriter.write(dive)
        assertTrue("<gpxtpx:wtemp>27</gpxtpx:wtemp><gpxtpx:depth>12.34</gpxtpx:depth>" in gpx)
        assertFalse("atemp" in gpx)
    }

    @Test
    fun smallCoordinatesAreNeverInExponentForm() {
        val gpx = GpxWriter.write(activity(Sport.RUN, TcxSport.Running, listOf(ExportPoint(start, 0.00001, -0.00002))))
        assertTrue("""lat="0.00001" lon="-0.00002"""" in gpx)
    }

    @Test
    fun tcxRunHasOneLapOfTheRunsTimeAndDistance() = runTest {
        val run =
            activity(
                Sport.RUN,
                TcxSport.Running,
                (0..2).map { ExportPoint(start + it.seconds, LATS[it], -122.3, 50.0, it * 11.1, 3.2, 150 + it, cadence = 88) },
            )
        val tcx = TcxWriter.write(run)
        assertTrue("""xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"""" in tcx)
        assertTrue("""xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2"""" in tcx)
        assertTrue("""<Activity Sport="Running">""" in tcx)
        assertTrue("<Id>2026-09-20T07:00:00Z</Id>" in tcx)
        assertTrue("<TotalTimeSeconds>50</TotalTimeSeconds>" in tcx)
        assertTrue("<DistanceMeters>300</DistanceMeters>" in tcx)
        assertTrue("<AverageHeartRateBpm><Value>130</Value></AverageHeartRateBpm>" in tcx)
        assertTrue("<ns3:TPX><ns3:Speed>3.2</ns3:Speed><ns3:RunCadence>88</ns3:RunCadence></ns3:TPX>" in tcx)

        val read = reader.read(tcx.encodeToByteArray()).single()
        assertEquals(Sport.RUN, read.type.sport)
        assertEquals(300.0, read.distanceMeters)
        assertEquals(130, read.avgHeartRate)
        assertEquals(50L, read.movingSeconds)
        assertEquals(run.points.map { it.latitude }, read.samples.map { it.latitude })
        val streams = assertNotNull(read.streams)
        assertEquals(run.points.map { it.heartRate }, streams.heartRate)
        assertEquals(run.points.map { it.cadence }, streams.cadence)
    }

    @Test
    fun tcxIndoorRideHasTrackpointsWithoutPositions() = runTest {
        val indoor =
            activity(
                Sport.RIDE,
                TcxSport.Biking,
                (0..4).map { ExportPoint(start + it.seconds, heartRate = 110 + it, cadence = 90, powerWatts = 180 + it) },
            )
        val tcx = TcxWriter.write(indoor)
        assertFalse("<Position>" in tcx)
        assertTrue("<Cadence>90</Cadence><Extensions><ns3:TPX><ns3:Watts>180</ns3:Watts></ns3:TPX></Extensions>" in tcx)

        val read = reader.read(tcx.encodeToByteArray()).single()
        assertTrue(read.samples.isEmpty())
        val streams = read.streams!!
        assertEquals(listOf(0, 1, 2, 3, 4), streams.time)
        assertEquals(indoor.points.map { it.heartRate }, streams.heartRate)
        assertEquals(indoor.points.map { it.powerWatts }, streams.power)
        assertEquals(indoor.points.map { it.cadence }, streams.cadence)
    }

    @Test
    fun tcxSwimIsOther() = runTest {
        val swim = activity(Sport.SWIM, TcxSport.Other, (0..1).map { ExportPoint(start + it.seconds, heartRate = 100 + it) })
        val tcx = TcxWriter.write(swim)
        assertTrue("""<Activity Sport="Other">""" in tcx)
        assertTrue("<TotalTimeSeconds>60</TotalTimeSeconds>" in TcxWriter.write(swim.copy(moving = null)))
        val read = reader.read(tcx.encodeToByteArray()).single()
        assertEquals(listOf(100, 101), read.streams!!.heartRate)
    }

    /** Eight 60 s laps alternating 400 m of work and 100 m of rest, a point every 10 s. */
    private val intervalRun =
        activity(
                Sport.RUN,
                TcxSport.Running,
                (0..480 step 10).map { ExportPoint(start + it.seconds, distanceMeters = it * 2.5, heartRate = 140 + it % 30) },
            )
            .copy(
                elapsed = 480.seconds,
                moving = 480.seconds,
                distanceMeters = 2000.0,
                laps =
                    (0 until 8).map { i ->
                        val rest = i % 2 == 1
                        Lap(
                            start = i * 60,
                            elapsed = 60,
                            moving = 60,
                            distance = if (rest) 100 else 400,
                            maxSpeed = if (rest) 2_000 else 7_250,
                            avgHeartRate = if (rest) 140 else 170,
                            calories = if (rest) 5 else 25,
                            intensity = if (rest) LapIntensity.REST else LapIntensity.ACTIVE,
                            trigger = if (rest) LapTrigger.MANUAL else LapTrigger.DISTANCE,
                        )
                    },
            )

    @Test
    fun tcxHasALapPerStoredLap() {
        val tcx = TcxWriter.write(intervalRun)
        assertEquals(8, Regex("<Lap StartTime=").findAll(tcx).count())
        assertEquals(4, Regex("<Intensity>Resting</Intensity>").findAll(tcx).count())
        assertTrue("""<Lap StartTime="2026-09-20T07:01:00Z">""" in tcx)
        assertTrue("<Calories>25</Calories>" in tcx && "<TriggerMethod>Distance</TriggerMethod>" in tcx)
        assertTrue("<MaximumSpeed>7.25</MaximumSpeed>" in tcx)
        assertEquals(intervalRun.points.size, Regex("<Trackpoint>").findAll(tcx).count(), "each point in one lap")
    }

    @Test
    fun tcxLapsComeBack() = runTest {
        val read = reader.read(TcxWriter.write(intervalRun).encodeToByteArray()).single()
        assertEquals(intervalRun.laps, read.streams?.laps)
        assertEquals(2000.0, read.distanceMeters)
    }
}
