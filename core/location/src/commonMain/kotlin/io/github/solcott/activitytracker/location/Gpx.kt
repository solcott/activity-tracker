package io.github.solcott.activitytracker.location

import kotlin.math.abs
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.newGenericWriter
import nl.adaptivity.xmlutil.xmlStreaming

/** Minimal GPX 1.1 writer for the full-resolution track blob, on xmlutil's writer. */
object Gpx {
    const val MIME_TYPE = "application/gpx+xml"

    fun write(name: String, type: String, samples: List<LocationSample>): String = writeSegments(name, type, listOf(samples))

    /** One `<trkseg>` per segment of [segments]: a combined activity's pieces, recorded one after another. */
    fun writeSegments(name: String, type: String, segments: List<List<LocationSample>>): String {
        val out = StringBuilder()
        val xml = xmlStreaming.newGenericWriter(out, xmlDeclMode = XmlDeclMode.None)
        xml.addTrailingSpaceBeforeEnd = false
        xml.startDocument("1.0", "UTF-8", null)
        xml.line()
        xml.element("gpx") {
            namespaceAttr("", GPX_NS)
            attribute(null, "version", null, "1.1")
            attribute(null, "creator", null, "activity-tracker")
            line()
            element("trk") {
                line()
                textElement("name", name.toXmlText())
                line()
                textElement("type", type.toXmlText())
                for (segment in segments) {
                    line()
                    element("trkseg") {
                        for (s in segment) {
                            line()
                            element("trkpt") {
                                attribute(null, "lat", null, s.latitude.toGpxDecimal())
                                attribute(null, "lon", null, s.longitude.toGpxDecimal())
                                s.altitude?.let { textElement("ele", it.toGpxDecimal()) }
                                textElement("time", s.time.toString())
                            }
                        }
                        endLine()
                    }
                }
                endLine()
            }
            endLine()
        }
        xml.line()
        xml.endDocument()
        return out.toString()
    }

    private inline fun XmlWriter.element(name: String, body: XmlWriter.() -> Unit) {
        startTag(GPX_NS, name, "")
        body()
        endTag(GPX_NS, name, "")
    }

    private fun XmlWriter.textElement(name: String, text: String) = element(name) { text(text) }

    /** A line break indented for the next child of the open element. */
    private fun XmlWriter.line() = ignorableWhitespace("\n" + INDENT.repeat(depth))

    /** A line break indented for the open element's end tag. */
    private fun XmlWriter.endLine() = ignorableWhitespace("\n" + INDENT.repeat(depth - 1))

    /**
     * [Double.toString], which keeps every digit of the full-resolution blob, unless that's in exponent form (`-5.0E-4` on the JVM near the
     * equator or the prime meridian), which isn't an `xsd:decimal`: then [toXmlDecimal] at [SMALL_DECIMALS] places.
     */
    private fun Double.toGpxDecimal(): String {
        val plain = toString()
        if ('E' !in plain && 'e' !in plain) return plain
        return toXmlDecimal(if (abs(this) < 1) SMALL_DECIMALS else 0)
    }

    private const val INDENT = "  "
    private const val GPX_NS = "http://www.topografix.com/GPX/1/1"

    /** About 0.1 µm of latitude: more than any receiver resolves. */
    private const val SMALL_DECIMALS = 12
}
