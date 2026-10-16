package io.github.solcott.activitytracker.activityfile

/**
 * Writes an [ExportActivity] as a Garmin TCX v2 file: one `Activity` with one `Lap` spanning it (moving time, distance and heart-rate
 * summary), and a `Trackpoint` for every point, with or without a position. Speed, run cadence and power go in the `ActivityExtension` v2
 * `TPX`; a ride's cadence is the trackpoint's own `Cadence`. TCX has no temperature or depth.
 */
object TcxWriter {
    const val TCX_NS = "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2"
    const val ACTIVITY_EXTENSION_NS = "http://www.garmin.com/xmlschemas/ActivityExtension/v2"

    fun write(activity: ExportActivity): String = buildString {
        val running = activity.tcxSport == TcxSport.Running
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<TrainingCenterDatabase xmlns="$TCX_NS" xmlns:ns3="$ACTIVITY_EXTENSION_NS">""")
        appendLine("  <Activities>")
        appendLine("""    <Activity Sport="${activity.tcxSport.xmlName}">""")
        appendLine("      <Id>${activity.startedAt}</Id>")
        appendLine("""      <Lap StartTime="${activity.startedAt}">""")
        // Timer time, as readers take it: elapsed time would read back as moving time that includes the stops.
        appendLine("        <TotalTimeSeconds>${(activity.moving ?: activity.elapsed).inWholeSeconds}</TotalTimeSeconds>")
        // Required by the schema, as is Calories, which the app doesn't store.
        val distance = activity.distanceMeters ?: activity.points.asReversed().firstNotNullOfOrNull { it.distanceMeters } ?: 0.0
        appendLine("        <DistanceMeters>${distance.toXmlDecimal(METER_DECIMALS)}</DistanceMeters>")
        appendLine("        <Calories>0</Calories>")
        activity.avgHeartRate?.let { appendLine("        <AverageHeartRateBpm><Value>$it</Value></AverageHeartRateBpm>") }
        activity.maxHeartRate?.let { appendLine("        <MaximumHeartRateBpm><Value>$it</Value></MaximumHeartRateBpm>") }
        appendLine("        <Intensity>Active</Intensity>")
        appendLine("        <TriggerMethod>Manual</TriggerMethod>")
        appendLine("        <Track>")
        for (p in activity.points) appendTrackpoint(p, running)
        appendLine("        </Track>")
        appendLine("      </Lap>")
        appendLine("    </Activity>")
        appendLine("  </Activities>")
        appendLine("</TrainingCenterDatabase>")
    }

    private fun StringBuilder.appendTrackpoint(p: ExportPoint, running: Boolean) {
        append("          <Trackpoint><Time>${p.time}</Time>")
        if (p.latitude != null && p.longitude != null) {
            append("<Position>")
            append("<LatitudeDegrees>${p.latitude.toXmlDecimal(COORDINATE_DECIMALS)}</LatitudeDegrees>")
            append("<LongitudeDegrees>${p.longitude.toXmlDecimal(COORDINATE_DECIMALS)}</LongitudeDegrees>")
            append("</Position>")
        }
        p.altitudeMeters?.let { append("<AltitudeMeters>${it.toXmlDecimal(METER_DECIMALS)}</AltitudeMeters>") }
        p.distanceMeters?.let { append("<DistanceMeters>${it.toXmlDecimal(METER_DECIMALS)}</DistanceMeters>") }
        p.heartRate?.let { append("<HeartRateBpm><Value>$it</Value></HeartRateBpm>") }
        if (!running) p.cadence?.let { append("<Cadence>$it</Cadence>") }
        val tpx = buildString {
            p.speedMps?.let { append("<ns3:Speed>${it.toXmlDecimal(SPEED_DECIMALS)}</ns3:Speed>") }
            if (running) p.cadence?.let { append("<ns3:RunCadence>$it</ns3:RunCadence>") }
            p.powerWatts?.let { append("<ns3:Watts>$it</ns3:Watts>") }
        }
        if (tpx.isNotEmpty()) append("<Extensions><ns3:TPX>$tpx</ns3:TPX></Extensions>")
        appendLine("</Trackpoint>")
    }

    private const val COORDINATE_DECIMALS = 7
    private const val METER_DECIMALS = 2
    private const val SPEED_DECIMALS = 3
}
