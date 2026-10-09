package io.github.solcott.activitytracker.activityfile

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
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

/**
 * A dive computer's zone-less wall clock as written: ISO 8601 ("2024-10-16T17:30:57", or a space for the T), or US slashed ("10/16/2024
 * 5:30:57 PM", which Shearwater Cloud writes) with 24-hour and day-first variants (a first number over 12 must be the day). Null for a time
 * with a zone, an impossible date, or none at all.
 */
internal fun parseLocalDateTime(text: String): LocalDateTime? {
    val value = text.trim()
    return SLASHED.matchEntire(value)?.let(::slashed) ?: ISO_LOCAL.matchEntire(value)?.let(::isoLocal)
}

private fun slashed(match: MatchResult): LocalDateTime? {
    fun group(name: String) = match.groups[name]?.value?.toInt() ?: 0
    val first = group("first")
    val second = group("second")
    val dayFirst = first > MONTHS
    val hour = group("hour")
    val hour24 =
        when (match.groups["meridiem"]?.value?.uppercase()) {
            null -> hour
            "PM" -> hour % HALF_DAY + HALF_DAY
            else -> hour % HALF_DAY
        }
    return localDateTime(
        group("year"),
        if (dayFirst) second else first,
        if (dayFirst) first else second,
        hour24,
        group("minute"),
        group("sec"),
    )
}

private fun isoLocal(match: MatchResult): LocalDateTime? {
    fun group(name: String) = match.groups[name]?.value?.toInt() ?: 0
    return localDateTime(group("year"), group("month"), group("day"), group("hour"), group("minute"), group("sec"))
}

/** Null for an impossible date, like a 13th month. A missing group (seconds) reads as 0. */
@Suppress("LongParameterList")
private fun localDateTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): LocalDateTime? =
    try {
        LocalDateTime(year, month, day, hour, minute, second)
    } catch (_: IllegalArgumentException) {
        null
    }

private const val MONTHS = 12
private const val HALF_DAY = 12

private val SLASHED =
    Regex(
        """(?<first>\d{1,2})/(?<second>\d{1,2})/(?<year>\d{4})\s+""" +
            """(?<hour>\d{1,2}):(?<minute>\d{2})(?::(?<sec>\d{2}))?\s*(?<meridiem>[AaPp][Mm])?"""
    )
private val ISO_LOCAL =
    Regex("""(?<year>\d{4})-(?<month>\d{1,2})-(?<day>\d{1,2})[T ](?<hour>\d{1,2}):(?<minute>\d{2})(?::(?<sec>\d{2}))?(?:\.\d+)?""")

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
    var depth: Double? = null

    fun build() = DecodedSample(time, latitude, longitude, altitude, heartRate, cadence, power, speed, distance, temperature, depth)
}

internal fun String.toIntValue(): Int? = toDoubleOrNull()?.toInt()
