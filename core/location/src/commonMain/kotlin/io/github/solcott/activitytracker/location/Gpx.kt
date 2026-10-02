package io.github.solcott.activitytracker.location

/** Minimal GPX 1.1 writer for the full-resolution track blob. */
object Gpx {
    const val MIME_TYPE = "application/gpx+xml"

    fun write(name: String, type: String, samples: List<LocationSample>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<gpx version="1.1" creator="activity-tracker" xmlns="http://www.topografix.com/GPX/1/1">""")
        appendLine("  <trk>")
        appendLine("    <name>${name.escapeXml()}</name>")
        appendLine("    <type>${type.escapeXml()}</type>")
        appendLine("    <trkseg>")
        for (s in samples) {
            append("""      <trkpt lat="${s.latitude}" lon="${s.longitude}">""")
            s.altitude?.let { append("<ele>$it</ele>") }
            append("<time>${s.time}</time>")
            appendLine("</trkpt>")
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    private fun String.escapeXml() = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
