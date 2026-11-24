package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.LapIntensity
import io.github.solcott.activitytracker.lexicon.app.LapTrigger
import io.github.solcott.activitytracker.location.toXmlDecimal
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

    fun write(activity: ExportActivity): String =
        XmlOutput.document(TCX_NS, "TrainingCenterDatabase", mapOf("ns3" to ACTIVITY_EXTENSION_NS)) {
            line()
            element("Activities") {
                line()
                element("Activity") {
                    attr("Sport", activity.tcxSport.xmlName)
                    line()
                    leaf("Id", activity.startedAt)
                    laps(activity)
                    endLine()
                }
                endLine()
            }
        }

    private fun XmlOutput.laps(activity: ExportActivity) {
        val running = activity.tcxSport == TcxSport.Running
        if (activity.laps.isEmpty()) {
            val distance = activity.distanceMeters ?: activity.points.asReversed().firstNotNullOfOrNull { it.distanceMeters } ?: 0.0
            lap(
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
                lap(
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
    private fun XmlOutput.lap(lap: TcxLap, points: List<ExportPoint>, running: Boolean) {
        line()
        element("Lap") {
            attr("StartTime", lap.start)
            line()
            leaf("TotalTimeSeconds", lap.timerSeconds)
            line()
            leaf("DistanceMeters", lap.distanceMeters.toXmlDecimal(METER_DECIMALS))
            lap.maxSpeedMps?.let {
                line()
                leaf("MaximumSpeed", it.toXmlDecimal(SPEED_DECIMALS))
            }
            line()
            leaf("Calories", lap.calories ?: 0)
            lap.avgHeartRate?.let {
                line()
                element("AverageHeartRateBpm") { leaf("Value", it) }
            }
            lap.maxHeartRate?.let {
                line()
                element("MaximumHeartRateBpm") { leaf("Value", it) }
            }
            line()
            leaf("Intensity", if (lap.resting) "Resting" else "Active")
            lap.cadence?.let {
                line()
                leaf("Cadence", it)
            }
            line()
            leaf("TriggerMethod", lap.trigger)
            line()
            element("Track") {
                for (p in points) trackpoint(p, running)
                endLine()
            }
            if (lap.avgSpeedMps != null || lap.avgPower != null) {
                line()
                element("Extensions") {
                    element("LX", ACTIVITY_EXTENSION_NS) {
                        leaf("AvgSpeed", lap.avgSpeedMps?.toXmlDecimal(SPEED_DECIMALS), ACTIVITY_EXTENSION_NS)
                        leaf("AvgWatts", lap.avgPower, ACTIVITY_EXTENSION_NS)
                    }
                }
            }
            endLine()
        }
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

    private fun XmlOutput.trackpoint(p: ExportPoint, running: Boolean) {
        line()
        element("Trackpoint") {
            leaf("Time", p.time)
            if (p.latitude != null && p.longitude != null) {
                element("Position") {
                    leaf("LatitudeDegrees", p.latitude.toXmlDecimal(COORDINATE_DECIMALS))
                    leaf("LongitudeDegrees", p.longitude.toXmlDecimal(COORDINATE_DECIMALS))
                }
            }
            leaf("AltitudeMeters", p.altitudeMeters?.toXmlDecimal(METER_DECIMALS))
            leaf("DistanceMeters", p.distanceMeters?.toXmlDecimal(METER_DECIMALS))
            p.heartRate?.let { element("HeartRateBpm") { leaf("Value", it) } }
            if (!running) leaf("Cadence", p.cadence)
            val runCadence = p.cadence.takeIf { running }
            if (p.speedMps != null || runCadence != null || p.powerWatts != null) {
                element("Extensions") {
                    element("TPX", ACTIVITY_EXTENSION_NS) {
                        leaf("Speed", p.speedMps?.toXmlDecimal(SPEED_DECIMALS), ACTIVITY_EXTENSION_NS)
                        leaf("RunCadence", runCadence, ACTIVITY_EXTENSION_NS)
                        leaf("Watts", p.powerWatts, ACTIVITY_EXTENSION_NS)
                    }
                }
            }
        }
    }

    private const val COORDINATE_DECIMALS = 7
    private const val METER_DECIMALS = 2
    private const val SPEED_DECIMALS = 3
    private const val MILLI = 1000.0
}
