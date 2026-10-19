package io.github.solcott.activitytracker.location

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
                append("""      <trkpt lat="${s.latitude}" lon="${s.longitude}">""")
                s.altitude?.let { append("<ele>$it</ele>") }
                append("<time>${s.time}</time>")
                appendLine("</trkpt>")
            }
            appendLine("    </trkseg>")
        }
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    private fun String.escapeXml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
