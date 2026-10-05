package io.github.solcott.activitytracker.activityfile

import kotlin.time.Instant
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * Walks an XML document with xmlutil's pull parser and reports elements by local name, so namespace prefixes (`gpxtpx:`, `ns3:`, none)
 * don't matter. [onStart] gets the path to the element (root first, the element last) and its attributes; [onEnd] gets the same path and
 * the element's own text, trimmed.
 */
internal fun walkXml(
    xml: String,
    onStart: (path: List<String>, attribute: (String) -> String?) -> Unit,
    onEnd: (path: List<String>, text: String) -> Unit,
) {
    val reader =
        try {
            xmlStreaming.newGenericReader(xml, expandEntities = true)
        } catch (e: XmlException) {
            activityFileError(DAMAGED_XML, e)
        }
    val path = mutableListOf<String>()
    val texts = ArrayDeque<StringBuilder>()
    try {
        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.START_ELEMENT -> {
                    path += reader.localName
                    texts.addLast(StringBuilder())
                    onStart(path) { name -> reader.getAttributeValue(null, name) }
                }
                EventType.TEXT,
                EventType.CDSECT,
                EventType.ENTITY_REF -> texts.lastOrNull()?.append(reader.text)
                EventType.END_ELEMENT -> {
                    onEnd(path, texts.removeLast().toString().trim())
                    path.removeLast()
                }
                else -> Unit
            }
        }
    } catch (e: XmlException) {
        activityFileError(DAMAGED_XML, e)
    } catch (e: IllegalStateException) {
        // xmlutil reports some malformed input (an early end, say) this way rather than as XmlException.
        activityFileError(DAMAGED_XML, e)
    }
}

/** Shown to the athlete, so no parser detail; that stays on the exception's cause. */
private const val DAMAGED_XML = "The file is damaged or cut off"

/** The document's root element's local name, or null when [xml] isn't XML. Reads no further than the root. */
internal fun xmlRootElement(xml: String): String? =
    try {
        val reader = xmlStreaming.newGenericReader(xml, expandEntities = true)
        var root: String? = null
        while (root == null && reader.hasNext()) {
            if (reader.next() == EventType.START_ELEMENT) root = reader.localName
        }
        root
    } catch (_: XmlException) {
        null
    } catch (_: IllegalStateException) {
        // xmlutil reports some malformed input (binary data, an early end) this way rather than as XmlException.
        null
    }

/** An ISO 8601 time as epoch millis. A time without a zone is read as UTC, which is what GPX requires anyway. Null if unparseable. */
internal fun parseXmlTime(text: String?): Long? {
    val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val zoned = if (ZONE.containsMatchIn(value)) value else value + "Z"
    return try {
        Instant.parse(zoned).toEpochMilliseconds()
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val ZONE = Regex("(Z|[+-]\\d{2}:?\\d{2})$", RegexOption.IGNORE_CASE)

/** The element's parent in a [walkXml] path. */
internal val List<String>.parent: String?
    get() = getOrNull(size - 2)

/** Collects one track point's values as the parsers meet them. */
internal class SampleBuilder(var latitude: Double? = null, var longitude: Double? = null) {
    var time: Long? = null
    var altitude: Double? = null
    var heartRate: Int? = null
    var cadence: Int? = null
    var power: Int? = null
    var speed: Double? = null
    var distance: Double? = null
    var temperature: Double? = null

    fun build() = DecodedSample(time, latitude, longitude, altitude, heartRate, cadence, power, speed, distance, temperature)
}

internal fun String.toIntValue(): Int? = toDoubleOrNull()?.toInt()
