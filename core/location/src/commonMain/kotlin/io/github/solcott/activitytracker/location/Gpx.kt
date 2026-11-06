package io.github.solcott.activitytracker.location

import kotlin.math.abs

/** Minimal GPX 1.1 writer for the full-resolution track blob. */
object Gpx {
    const val MIME_TYPE = "application/gpx+xml"

    fun write(name: String, type: String, samples: List<LocationSample>): String = writeSegments(name, type, listOf(samples))

    /** One `<trkseg>` per segment of [segments]: a combined activity's pieces, recorded one after another. */
    fun writeSegments(name: String, type: String, segments: List<List<LocationSample>>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="activity-tracker" xmlns="http://www.topografix.com/GPX/1/1">""")
        appendLine("  <trk>")
        appendLine("    <name>${name.escapeXml()}</name>")
        appendLine("    <type>${type.escapeXml()}</type>")
        for (segment in segments) {
            appendLine("    <trkseg>")
            for (s in segment) {
                append("""      <trkpt lat="${s.latitude.toGpxDecimal()}" lon="${s.longitude.toGpxDecimal()}">""")
                s.altitude?.let { append("<ele>${it.toGpxDecimal()}</ele>") }
                append("<time>${s.time}</time>")
                appendLine("</trkpt>")
            }
            appendLine("    </trkseg>")
        }
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    /**
     * [Double.toString], which keeps every digit of the full-resolution blob, unless that's in exponent form (`-5.0E-4` on the JVM near the
     * equator or the prime meridian), which isn't an `xsd:decimal`: then [toXmlDecimal] at [SMALL_DECIMALS] places.
     */
    private fun Double.toGpxDecimal(): String {
        val plain = toString()
        if ('E' !in plain && 'e' !in plain) return plain
        return toXmlDecimal(if (abs(this) < 1) SMALL_DECIMALS else 0)
    }

    /** About 0.1 µm of latitude: more than any receiver resolves. */
    private const val SMALL_DECIMALS = 12
}
