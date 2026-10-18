package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GpxParserTest {
    /** Garmin Connect's export: TrackPointExtension under its own prefix. */
    private val garmin =
        """<?xml version="1.0" encoding="UTF-8"?>
        <gpx creator="Garmin Connect" version="1.1" xmlns="http://www.topografix.com/GPX/1/1"
             xmlns:ns3="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">
          <metadata><time>2026-09-20T06:59:00.000Z</time></metadata>
          <trk><name>Morning Run</name><type>running</type><trkseg>
            <trkpt lat="47.6000" lon="-122.3000"><ele>10.0</ele><time>2026-09-20T07:00:00.000Z</time>
              <extensions><ns3:TrackPointExtension><ns3:atemp>12.0</ns3:atemp><ns3:hr>120</ns3:hr><ns3:cad>80</ns3:cad></ns3:TrackPointExtension></extensions>
            </trkpt>
            <trkpt lat="47.6045" lon="-122.3000"><ele>20.0</ele><time>2026-09-20T07:02:30.000Z</time>
              <extensions><ns3:TrackPointExtension><ns3:atemp>12.0</ns3:atemp><ns3:hr>160</ns3:hr><ns3:cad>84</ns3:cad></ns3:TrackPointExtension></extensions>
            </trkpt>
          </trkseg></trk>
        </gpx>"""

    @Test
    fun readsAGarminGpxWithExtensions() {
        val decoded = GpxParser.parse(garmin)

        assertEquals("running", decoded.sessions.single().sport)
        assertNull(decoded.fileId, "GPX creator is software, not a device")
        val first = decoded.records.first()
        assertEquals(Instant.parse("2026-09-20T07:00:00Z").toEpochMilliseconds(), first.timestampMillis)
        assertEquals(47.6, first.latitude)
        assertEquals(10.0, first.altitudeMeters)
        assertEquals(120, first.heartRate)
        assertEquals(80, first.cadence)
        assertEquals(12.0, first.temperatureCelsius)

        val activity = DecodedActivityMapper.map(decoded)
        assertEquals(Sport.RUN, activity.type.sport)
        assertEquals(Instant.parse("2026-09-20T07:00:00Z"), activity.startedAt, "starts at the first point, not metadata time")
        assertEquals(500.4, activity.distanceMeters!!, 1.0)
        assertEquals(150L, activity.movingSeconds)
        assertEquals(10.0, activity.elevationGainMeters)
        assertEquals(140, activity.avgHeartRate)
        assertEquals(160, activity.maxHeartRate)
        assertEquals("Morning Run", activity.title)
    }

    @Test
    fun theTitleFallsBackToTheMetadataName() {
        fun gpx(metadata: String, track: String) =
            """<gpx version="1.1"><metadata>$metadata</metadata><trk>$track<trkseg>
              <trkpt lat="47.6" lon="-122.3"><name>Waypoint</name><time>2026-09-20T07:00:00Z</time></trkpt>
            </trkseg></trk></gpx>"""

        assertEquals("Lake loop", GpxParser.parse(gpx("<name>Lake loop</name>", "")).title)
        assertEquals("Track", GpxParser.parse(gpx("<name>Lake loop</name>", "<name>Track</name>")).title)
        assertNull(DecodedActivityMapper.map(GpxParser.parse(gpx("", "<name>  </name>"))).title, "a blank name is no title")
    }

    @Test
    fun theTrackDescriptionStartsTheDescription() {
        val strava = garmin.replace("<name>Morning Run</name>", "<name>Morning Run</name><desc>Hill repeats</desc>")

        assertEquals("Hill repeats", GpxParser.parse(strava).notes)
        assertEquals("Hill repeats", DecodedActivityMapper.map(GpxParser.parse(strava)).description)
        assertNull(DecodedActivityMapper.map(GpxParser.parse(garmin)).description, "no desc, no description")
    }

    @Test
    fun readsPowerAndUnprefixedExtensions() {
        val strava =
            """<gpx creator="StravaGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><type>cycling</type><trkseg>
              <trkpt lat="47.6" lon="-122.3"><time>2026-09-20T07:00:00Z</time><extensions><power>210</power>
                <gpxtpx:TrackPointExtension xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v2"><gpxtpx:hr>130</gpxtpx:hr></gpxtpx:TrackPointExtension>
              </extensions></trkpt>
            </trkseg></trk></gpx>"""
        val sample = GpxParser.parse(strava).records.single()
        assertEquals(210, sample.powerWatts)
        assertEquals(130, sample.heartRate)
    }

    @Test
    fun aPauseBetweenSegmentsIsNotMovingTime() {
        val paused =
            """<gpx version="1.1"><trk>
              <trkseg>
                <trkpt lat="47.6000" lon="-122.3"><time>2026-09-20T07:00:00Z</time></trkpt>
                <trkpt lat="47.6045" lon="-122.3"><time>2026-09-20T07:02:30Z</time></trkpt>
              </trkseg>
              <trkseg>
                <trkpt lat="47.6045" lon="-122.3"><time>2026-09-20T07:20:00Z</time></trkpt>
                <trkpt lat="47.6090" lon="-122.3"><time>2026-09-20T07:22:30Z</time></trkpt>
              </trkseg>
            </trk></gpx>"""
        val activity = DecodedActivityMapper.map(GpxParser.parse(paused))

        assertEquals(22.minutes + 30.seconds, activity.elapsed)
        assertEquals(300L, activity.movingSeconds, "17.5 minutes stopped between segments drop out")
        assertEquals(Sport.OTHER, activity.type.sport, "no <type>")
    }

    @Test
    fun aRouteWithoutTimesIsRejected() {
        val route =
            """<gpx version="1.1"><trk><trkseg><trkpt lat="47.6" lon="-122.3"/><trkpt lat="47.7" lon="-122.3"/></trkseg></trk></gpx>"""
        val e = assertFailsWith<ActivityFileException> { GpxParser.parse(route) }
        assertTrue("route" in e.message.orEmpty())
    }

    @Test
    fun aTimeWithoutAZoneIsUtc() {
        assertEquals(Instant.parse("2026-09-20T07:00:00Z").toEpochMilliseconds(), parseXmlTime("2026-09-20T07:00:00"))
        assertEquals(Instant.parse("2026-09-20T05:00:00Z").toEpochMilliseconds(), parseXmlTime("2026-09-20T07:00:00+02:00"))
        assertNull(parseXmlTime("yesterday"))
    }
}
