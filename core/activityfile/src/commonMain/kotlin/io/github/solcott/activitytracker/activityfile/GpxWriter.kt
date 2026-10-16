package io.github.solcott.activitytracker.activityfile

/**
 * Writes an [ExportActivity] as GPX 1.1: one track of the points that have a position. Channels go in Garmin's `TrackPointExtension` v2
 * (`atemp` or, for a dive, `wtemp`, then `depth`, `hr`, `cad`, `speed`, in its schema's order) and power in a bare `<power>`, as Strava
 * writes and reads it. Unlike `Gpx.write` in `core/location`, which writes the stored track blob, this is for files that leave the app.
 */
object GpxWriter {
    const val TRACK_POINT_EXTENSION_NS = "http://www.garmin.com/xmlschemas/TrackPointExtension/v2"

    fun write(activity: ExportActivity): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<gpx version="1.1" creator="activity-tracker" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:gpxtpx="$TRACK_POINT_EXTENSION_NS">"""
        )
        appendLine("  <metadata><time>${activity.startedAt}</time></metadata>")
        appendLine("  <trk>")
        appendLine("    <name>${activity.name.escapeXml()}</name>")
        appendLine("    <type>${activity.sport.escapeXml()}</type>")
        appendLine("    <trkseg>")
        for (p in activity.points) {
            val lat = p.latitude
            val lon = p.longitude
            if (lat == null || lon == null) continue
            append("""      <trkpt lat="${lat.toXmlDecimal(COORDINATE_DECIMALS)}" lon="${lon.toXmlDecimal(COORDINATE_DECIMALS)}">""")
            p.altitudeMeters?.let { append("<ele>${it.toXmlDecimal(METER_DECIMALS)}</ele>") }
            append("<time>${p.time}</time>")
            appendExtensions(p, activity.isDive)
            appendLine("</trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    private fun StringBuilder.appendExtensions(p: ExportPoint, isDive: Boolean) {
        val garmin = buildString {
            p.temperatureCelsius?.let {
                val tag = if (isDive) "wtemp" else "atemp"
                append("<gpxtpx:$tag>${it.toXmlDecimal(TEMPERATURE_DECIMALS)}</gpxtpx:$tag>")
            }
            p.depthMeters?.let { append("<gpxtpx:depth>${it.toXmlDecimal(METER_DECIMALS)}</gpxtpx:depth>") }
            p.heartRate?.let { append("<gpxtpx:hr>$it</gpxtpx:hr>") }
            p.cadence?.let { append("<gpxtpx:cad>$it</gpxtpx:cad>") }
            p.speedMps?.let { append("<gpxtpx:speed>${it.toXmlDecimal(SPEED_DECIMALS)}</gpxtpx:speed>") }
        }
        if (garmin.isEmpty() && p.powerWatts == null) return
        append("<extensions>")
        p.powerWatts?.let { append("<power>$it</power>") }
        if (garmin.isNotEmpty()) append("<gpxtpx:TrackPointExtension>$garmin</gpxtpx:TrackPointExtension>")
        append("</extensions>")
    }

    private const val COORDINATE_DECIMALS = 7
    private const val METER_DECIMALS = 2
    private const val SPEED_DECIMALS = 3
    private const val TEMPERATURE_DECIMALS = 1
}
