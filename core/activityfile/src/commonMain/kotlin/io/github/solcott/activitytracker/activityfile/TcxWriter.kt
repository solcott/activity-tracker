package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.LapIntensity
import io.github.solcott.activitytracker.lexicon.app.LapTrigger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Writes an [ExportActivity] as a Garmin TCX v2 file: one `Activity` with a `Lap` per stored lap (its moving time, distance, maximum speed,
 * calories, heart-rate summary, intensity and trigger, with its points), or one spanning the activity with the record's summary when none
 * are stored, and a `Trackpoint` for every point, with or without a position. TCX intensity is Active or Resting only, so a warm-up or
 * cool-down is Active. Speed, run cadence and power go in the `ActivityExtension` v2 `TPX`, a lap's average speed and power in its `LX`; a
 * ride's cadence is the trackpoint's own `Cadence`. TCX has no temperature or depth.
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
        if (activity.laps.isEmpty()) {
            val distance = activity.distanceMeters ?: activity.points.asReversed().firstNotNullOfOrNull { it.distanceMeters } ?: 0.0
            appendLap(
                TcxLap(
                    start = activity.startedAt,
                    // Timer time, as readers take it: elapsed time would read back as moving time that includes the stops.
                    timerSeconds = (activity.moving ?: activity.elapsed).inWholeSeconds,
                    distanceMeters = distance,
                    avgHeartRate = activity.avgHeartRate,
                    maxHeartRate = activity.maxHeartRate,
                ),
                activity.points,
                running,
            )
        } else {
            activity.laps.forEachIndexed { i, lap ->
                val lapStart = activity.startedAt + lap.start.seconds
                val next = activity.laps.getOrNull(i + 1)?.let { activity.startedAt + it.start.seconds }
                // Points before the first lap go in it, and points after the last in that.
                val points = activity.points.filter { p -> (i == 0 || p.time >= lapStart) && (next == null || p.time < next) }
                appendLap(
                    TcxLap(
                        start = lapStart,
                        timerSeconds = (lap.moving ?: lap.elapsed).toLong(),
                        distanceMeters = lap.distance?.toDouble() ?: 0.0,
                        maxSpeedMps = lap.maxSpeed?.div(MILLI),
                        calories = lap.calories,
                        avgHeartRate = lap.avgHeartRate,
                        maxHeartRate = lap.maxHeartRate,
                        resting = lap.intensity == LapIntensity.REST,
                        trigger = TRIGGERS[lap.trigger] ?: MANUAL,
                        cadence = lap.avgCadence.takeUnless { running },
                        avgSpeedMps = lap.avgSpeed?.div(MILLI),
                        avgPower = lap.avgPower,
                    ),
                    points,
                    running,
                )
            }
        }
        appendLine("    </Activity>")
        appendLine("  </Activities>")
        appendLine("</TrainingCenterDatabase>")
    }

    /** A lap in TCX's terms. */
    @Suppress("LongParameterList") // Every field a TCX lap holds.
    private class TcxLap(
        val start: Instant,
        val timerSeconds: Long,
        val distanceMeters: Double,
        val maxSpeedMps: Double? = null,
        val calories: Int? = null,
        val avgHeartRate: Int? = null,
        val maxHeartRate: Int? = null,
        val resting: Boolean = false,
        val trigger: String = MANUAL,
        val cadence: Int? = null,
        val avgSpeedMps: Double? = null,
        val avgPower: Int? = null,
    )

    /** Children in the schema's order; distance, calories, intensity and trigger are required. */
    private fun StringBuilder.appendLap(lap: TcxLap, points: List<ExportPoint>, running: Boolean) {
        appendLine("""      <Lap StartTime="${lap.start}">""")
        appendLine("        <TotalTimeSeconds>${lap.timerSeconds}</TotalTimeSeconds>")
        appendLine("        <DistanceMeters>${lap.distanceMeters.toXmlDecimal(METER_DECIMALS)}</DistanceMeters>")
        lap.maxSpeedMps?.let { appendLine("        <MaximumSpeed>${it.toXmlDecimal(SPEED_DECIMALS)}</MaximumSpeed>") }
        appendLine("        <Calories>${lap.calories ?: 0}</Calories>")
        lap.avgHeartRate?.let { appendLine("        <AverageHeartRateBpm><Value>$it</Value></AverageHeartRateBpm>") }
        lap.maxHeartRate?.let { appendLine("        <MaximumHeartRateBpm><Value>$it</Value></MaximumHeartRateBpm>") }
        appendLine("        <Intensity>${if (lap.resting) "Resting" else "Active"}</Intensity>")
        lap.cadence?.let { appendLine("        <Cadence>$it</Cadence>") }
        appendLine("        <TriggerMethod>${lap.trigger}</TriggerMethod>")
        appendLine("        <Track>")
        for (p in points) appendTrackpoint(p, running)
        appendLine("        </Track>")
        val lx = buildString {
            lap.avgSpeedMps?.let { append("<ns3:AvgSpeed>${it.toXmlDecimal(SPEED_DECIMALS)}</ns3:AvgSpeed>") }
            lap.avgPower?.let { append("<ns3:AvgWatts>$it</ns3:AvgWatts>") }
        }
        if (lx.isNotEmpty()) appendLine("        <Extensions><ns3:LX>$lx</ns3:LX></Extensions>")
        appendLine("      </Lap>")
    }

    private const val MANUAL = "Manual"

    /** TCX `TriggerMethod` by lexicon token; anything else is Manual. */
    private val TRIGGERS =
        mapOf(
            LapTrigger.MANUAL to MANUAL,
            LapTrigger.DISTANCE to "Distance",
            LapTrigger.POSITION to "Location",
            LapTrigger.TIME to "Time",
            LapTrigger.HEART_RATE to "HeartRate",
        )

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
    private const val MILLI = 1000.0
}
