package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.location.toXmlDecimal

/**
 * Writes an [ExportActivity] as GPX 1.1: one track of the points that have a position. Channels go in Garmin's `TrackPointExtension` v2
 * (`atemp` or, for a dive, `wtemp`, then `depth`, `hr`, `cad`, `speed`, in its schema's order) and power in a bare `<power>`, as Strava
 * writes and reads it. Unlike `Gpx.write` in `core/location`, which writes the stored track blob, this is for files that leave the app. One
 * point to a line.
 */
object GpxWriter {
    const val TRACK_POINT_EXTENSION_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v2"

    fun write(activity: ExportActivity): String =
        XmlOutput.document(GPX_NS, "gpx", mapOf("gpxtpx" to TRACK_POINT_EXTENSION_NS)) {
            attr("version", "1.1")
            attr("creator", "activity-tracker")
            line()
            element("metadata") { leaf("time", activity.startedAt) }
            line()
            element("trk") {
                line()
                leaf("name", activity.name)
                line()
                leaf("type", activity.sport)
                line()
                element("trkseg") {
                    for (p in activity.points) {
                        val lat = p.latitude
                        val lon = p.longitude
                        if (lat == null || lon == null) continue
                        line()
                        element("trkpt") {
                            attr("lat", lat.toXmlDecimal(COORDINATE_DECIMALS))
                            attr("lon", lon.toXmlDecimal(COORDINATE_DECIMALS))
                            leaf("ele", p.altitudeMeters?.toXmlDecimal(METER_DECIMALS))
                            leaf("time", p.time)
                            extensions(p, activity.isDive)
                        }
                    }
                    endLine()
                }
                endLine()
            }
        }

    private fun XmlOutput.extensions(p: ExportPoint, isDive: Boolean) {
        val garmin = listOf(p.temperatureCelsius, p.depthMeters, p.heartRate, p.cadence, p.speedMps).any { it != null }
        if (!garmin && p.powerWatts == null) return
        element("extensions") {
            leaf("power", p.powerWatts)
            if (garmin) {
                element("TrackPointExtension", TRACK_POINT_EXTENSION_NS) {
                    leaf(
                        if (isDive) "wtemp" else "atemp",
                        p.temperatureCelsius?.toXmlDecimal(TEMPERATURE_DECIMALS),
                        TRACK_POINT_EXTENSION_NS,
                    )
                    leaf("depth", p.depthMeters?.toXmlDecimal(METER_DECIMALS), TRACK_POINT_EXTENSION_NS)
                    leaf("hr", p.heartRate, TRACK_POINT_EXTENSION_NS)
                    leaf("cad", p.cadence, TRACK_POINT_EXTENSION_NS)
                    leaf("speed", p.speedMps?.toXmlDecimal(SPEED_DECIMALS), TRACK_POINT_EXTENSION_NS)
                }
            }
        }
    }

    private const val GPX_NS = "http://www.topografix.com/GPX/1/1"
    private const val COORDINATE_DECIMALS = 7
    private const val METER_DECIMALS = 2
    private const val SPEED_DECIMALS = 3
    private const val TEMPERATURE_DECIMALS = 1
}
