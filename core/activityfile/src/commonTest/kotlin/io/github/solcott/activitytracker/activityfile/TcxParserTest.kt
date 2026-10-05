package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TcxParserTest {
    /** Garmin's TCX shape: two laps, TPX speed and power on track points, LX averages on laps. */
    private val ride =
        """${"﻿"}<?xml version="1.0" encoding="UTF-8"?>
        <TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
            xmlns:ns3="http://www.garmin.com/xmlschemas/ActivityExtension/v2">
          <Activities>
            <Activity Sport="Biking">
              <Id>2026-09-20T07:00:00.000Z</Id>
              <Lap StartTime="2026-09-20T07:00:00.000Z">
                <TotalTimeSeconds>600.0</TotalTimeSeconds><DistanceMeters>5000.0</DistanceMeters><MaximumSpeed>12.5</MaximumSpeed>
                <AverageHeartRateBpm><Value>140</Value></AverageHeartRateBpm><MaximumHeartRateBpm><Value>160</Value></MaximumHeartRateBpm>
                <Cadence>85</Cadence>
                <Track>
                  <Trackpoint><Time>2026-09-20T07:00:00.000Z</Time><Position><LatitudeDegrees>47.6</LatitudeDegrees><LongitudeDegrees>-122.3</LongitudeDegrees></Position>
                    <AltitudeMeters>10.0</AltitudeMeters><DistanceMeters>0.0</DistanceMeters><HeartRateBpm><Value>130</Value></HeartRateBpm><Cadence>80</Cadence>
                    <Extensions><ns3:TPX><ns3:Speed>8.0</ns3:Speed><ns3:Watts>200</ns3:Watts></ns3:TPX></Extensions></Trackpoint>
                  <Trackpoint><Time>2026-09-20T07:10:00.000Z</Time><Position><LatitudeDegrees>47.645</LatitudeDegrees><LongitudeDegrees>-122.3</LongitudeDegrees></Position>
                    <AltitudeMeters>30.0</AltitudeMeters><DistanceMeters>5000.0</DistanceMeters><HeartRateBpm><Value>150</Value></HeartRateBpm></Trackpoint>
                </Track>
                <Extensions><ns3:LX><ns3:AvgSpeed>8.3</ns3:AvgSpeed><ns3:AvgWatts>205</ns3:AvgWatts></ns3:LX></Extensions>
              </Lap>
              <Lap StartTime="2026-09-20T07:15:00.000Z">
                <TotalTimeSeconds>300.0</TotalTimeSeconds><DistanceMeters>2500.0</DistanceMeters><MaximumSpeed>10.0</MaximumSpeed>
                <AverageHeartRateBpm><Value>170</Value></AverageHeartRateBpm><MaximumHeartRateBpm><Value>180</Value></MaximumHeartRateBpm>
                <Track>
                  <Trackpoint><Time>2026-09-20T07:20:00.000Z</Time><Position><LatitudeDegrees>47.6675</LatitudeDegrees><LongitudeDegrees>-122.3</LongitudeDegrees></Position>
                    <AltitudeMeters>25.0</AltitudeMeters><DistanceMeters>7500.0</DistanceMeters></Trackpoint>
                </Track>
              </Lap>
              <Creator xsi:type="Device_t" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><Name>Edge 530</Name><UnitId>1</UnitId></Creator>
            </Activity>
          </Activities>
          <Author xsi:type="Application_t" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"><Name>Garmin Connect API</Name></Author>
        </TrainingCenterDatabase>"""

    @Test
    fun lapsBecomeTheTotals() {
        val decoded = TcxParser.parse(ride.removePrefix("﻿"))
        assertEquals(2, decoded.laps.size)
        assertEquals(205, decoded.laps.first().avgPowerWatts)
        val point = decoded.records.first()
        assertEquals(200, point.powerWatts)
        assertEquals(8.0, point.speedMps)
        assertEquals(130, point.heartRate)
        assertEquals(0.0, point.distanceMeters, "a track point's distance, not the lap's")

        val activity = DecodedActivityMapper.map(decoded)
        assertEquals(Sport.RIDE, activity.sport)
        assertEquals(Instant.parse("2026-09-20T07:00:00Z"), activity.startedAt)
        assertEquals(20.minutes, activity.elapsed, "from the samples: TCX laps hold timer time only")
        assertEquals(900L, activity.movingSeconds)
        assertEquals(7500.0, activity.distanceMeters)
        assertEquals(12.5, activity.maxSpeedMps)
        assertEquals(150, activity.avgHeartRate, "lap averages weighted by lap time")
        assertEquals(180, activity.maxHeartRate)
        assertEquals(20.0, activity.elevationGainMeters)
        assertEquals("Edge 530", activity.device, "Creator, not the exporting Author")
    }

    @Test
    fun aCourseIsRejected() {
        val course =
            """<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"><Courses><Course><Name>Loop</Name>
              <Track><Trackpoint><Time>2026-09-20T07:00:00Z</Time></Trackpoint></Track></Course></Courses></TrainingCenterDatabase>"""
        val e = assertFailsWith<ActivityFileException> { TcxParser.parse(course) }
        assertTrue("course" in e.message.orEmpty())
    }
}
