package io.github.solcott.activitytracker.activityfile

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Reads Subsurface's own logbook (`.ssrf` or `.xml`): a `divelog` root with `divesites` and `dives`, the dives either directly under
 * `dives` or grouped into `trip`s. Nearly everything is in attributes, each value with its unit ("30.1 m", "24.0 C", "45:30 min", "200.0
 * bar", "32.0%"). A dive merged from several computers lists each one's `divecomputer`; the first is the one Subsurface shows.
 *
 * Times are the dive computer's wall clock with no zone, read in [timeZone] and kept as [DecodedActivity.localStart].
 */
internal object SubsurfaceXmlParser {
    fun parse(xml: String, timeZone: TimeZone = TimeZones.device()): List<DecodedActivity> {
        val state = State()
        walkXml(xml, onStart = state::start, onEnd = { path, _ -> state.end(path) })
        // One undated dive shouldn't keep the rest of a logbook out.
        return state.dives
            .mapNotNull { it.decode(state.sites, timeZone) }
            .ifEmpty { activityFileError("No dated dive in this Subsurface file") }
    }

    private class Site(val name: String?, val position: DivePosition?)

    private class Cylinder(val gas: DiveGas?, val startBar: Double?, val endBar: Double?)

    private class Sample(val seconds: Double?, val depth: Double?, val temperature: Double?)

    private class Dive(val attribute: Map<String, String>) {
        val cylinders = mutableListOf<Cylinder>()
        val samples = mutableListOf<Sample>()
        /** Gases switched to, by cylinder index or by mix. */
        val switches = mutableListOf<Pair<Int?, DiveGas?>>()
        var computers = 0
        var model: String? = null
        var closedCircuit = false
        var maxDepth: Double? = null
        var meanDepth: Double? = null
        var waterTemperature: Double? = null

        fun decode(sites: Map<String, Site>, timeZone: TimeZone): DecodedActivity? {
            val text = listOfNotNull(attribute["date"], attribute["time"]).joinToString(" ")
            val localStart = parseLocalDateTime(text) ?: parseLocalDateTime("$text 00:00")
            val start = localStart?.toInstant(timeZone)?.toEpochMilliseconds() ?: return null
            val site = attribute["divesiteid"]?.let { sites[it] }
            // The first cylinder is breathed from the start; a gas change adds the next.
            val gases =
                (listOf(cylinders.firstOrNull()?.gas) + switches.map { (index, mix) -> mix ?: index?.let { cylinders.getOrNull(it)?.gas } })
                    .filterNotNull()
                    .distinct()
            val dive =
                DecodedDive(
                    maxDepthMeters = maxDepth,
                    diveNumber = attribute["number"]?.toIntOrNull(),
                    surfaceIntervalMinutes = null,
                    gradientFactorLow = null,
                    gradientFactorHigh = null,
                    startCns = null,
                    endCns = null,
                    gases = gases,
                    closedCircuit = closedCircuit,
                    avgDepthMeters = meanDepth,
                    tanks = cylinders.filter { it.startBar != null || it.endBar != null }.map { DiveTank(it.startBar, it.endBar, null) },
                    entry = site?.position,
                    siteName = site?.name,
                )
            val elapsed =
                attribute["duration"]?.let(::seconds)?.takeIf { it > 0 } ?: samples.mapNotNull { it.seconds }.maxOrNull()?.takeIf { it > 0 }
            val records = samples.map { s ->
                SampleBuilder()
                    .apply {
                        time = s.seconds?.let { start + (it * DiveLogs.MILLIS_PER_SECOND).toLong() }
                        depth = s.depth
                        temperature = s.temperature
                    }
                    .build()
            }
            return DecodedActivity(
                fileId = DiveLogs.source(model),
                // Samples carry a temperature only when it changes; with none, the computer's header value stands in.
                sessions =
                    listOf(DiveLogs.summary(dive, start, elapsed, waterTemperature.takeIf { samples.none { s -> s.temperature != null } })),
                laps = emptyList(),
                records = records,
                dive = dive,
                localStart = localStart,
                title = site?.name,
            )
        }
    }

    private class State {
        val sites = mutableMapOf<String, Site>()
        val dives = mutableListOf<Dive>()
        private var dive: Dive? = null

        fun start(path: List<String>, attribute: (String) -> String?) {
            val current = dive
            when (path.last()) {
                "site" ->
                    if (path.parent == "divesites") attribute("uuid")?.let { sites[it] = Site(attribute("name"), gps(attribute("gps"))) }
                "dive" ->
                    if (path.parent == "dives" || path.parent == "trip")
                        dive = Dive(DIVE_ATTRIBUTES.mapNotNull { n -> attribute(n)?.let { n to it } }.toMap())
                "cylinder" ->
                    current
                        ?.cylinders
                        ?.add(Cylinder(gas(attribute("o2"), attribute("he")), attribute("start")?.let(::bar), attribute("end")?.let(::bar)))
                "divecomputer" ->
                    if (current != null) {
                        current.computers++
                        if (current.computers == 1) {
                            current.model = attribute("model")
                            current.closedCircuit = attribute("dctype")?.uppercase() == "CCR"
                        }
                    }
                else -> if (current?.computers == 1 && path.parent == "divecomputer") readComputer(current, path.last(), attribute)
            }
        }

        private fun readComputer(dive: Dive, name: String, attribute: (String) -> String?) {
            when (name) {
                "depth" -> {
                    dive.maxDepth = attribute("max")?.let(::meters)
                    dive.meanDepth = attribute("mean")?.let(::meters)
                }
                "temperature" -> dive.waterTemperature = attribute("water")?.let(::celsius)
                "event" ->
                    if (attribute("name") == "gaschange") {
                        dive.switches += attribute("cylinder")?.toIntOrNull() to gas(attribute("o2"), attribute("he"))
                    }
                "sample" ->
                    dive.samples +=
                        Sample(attribute("time")?.let(::seconds), attribute("depth")?.let(::meters), attribute("temp")?.let(::celsius))
            }
        }

        fun end(path: List<String>) {
            if (path.last() == "dive") {
                dive?.let { dives += it }
                dive = null
            }
        }
    }

    private val DIVE_ATTRIBUTES = listOf("number", "date", "time", "duration", "divesiteid")

    /** "17.316 -87.535". */
    private fun gps(text: String?): DivePosition? {
        val parts = text?.trim()?.split(Regex("""[\s,]+""")).orEmpty().map { it.toDoubleOrNull() }
        val lat = parts.getOrNull(0)
        val lon = parts.getOrNull(1)
        return if (lat != null && lon != null) DivePosition(lat, lon) else null
    }

    /** "32.0%", or no `o2` at all for air. */
    private fun gas(o2: String?, he: String?): DiveGas? {
        val oxygen = o2?.let(::percent) ?: if (he == null) AIR_PERCENT else return null
        return DiveLogs.gasFromFractions(oxygen / DiveLogs.PERCENT, he?.let(::percent)?.div(DiveLogs.PERCENT))
    }

    /** "45:30 min", "1:02:03 min" or a bare "2730" seconds. */
    fun seconds(text: String): Double? {
        val fields = text.trim().removeSuffix("min").trim().split(':')
        val parts = fields.mapNotNull { it.toDoubleOrNull() }
        return if (parts.size == fields.size) parts.fold(0.0) { total, part -> total * SIXTY + part } else null
    }

    fun meters(text: String) = number(text)?.let { (value, unit) -> if (unit == "ft") value * METERS_PER_FOOT else value }

    fun celsius(text: String) =
        number(text)?.let { (value, unit) -> if (unit == "F") (value - FAHRENHEIT_OFFSET) / FAHRENHEIT_PER_CELSIUS else value }

    fun bar(text: String) = number(text)?.let { (value, unit) -> if (unit == "psi") value / PSI_PER_BAR else value }

    private fun percent(text: String) = number(text)?.first

    /** "30.1 m" as 30.1 and "m". */
    private fun number(text: String): Pair<Double, String>? {
        val match = NUMBER.matchEntire(text.trim())
        return match?.groupValues?.get(1)?.toDoubleOrNull()?.let { it to match.groupValues[2].trim() }
    }

    private val NUMBER = Regex("""(-?\d+(?:\.\d+)?)\s*(.*)""")
    private const val AIR_PERCENT = 21.0
    private const val SIXTY = 60
    private const val METERS_PER_FOOT = 0.3048
    private const val FAHRENHEIT_OFFSET = 32.0
    private const val FAHRENHEIT_PER_CELSIUS = 1.8
    private const val PSI_PER_BAR = 14.5038
}
