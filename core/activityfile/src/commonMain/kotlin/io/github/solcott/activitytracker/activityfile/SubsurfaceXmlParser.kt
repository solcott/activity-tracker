package io.github.solcott.activitytracker.activityfile

import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Reads Subsurface's own logbook (`.ssrf` or `.xml`): a `divelog` root with `divesites` and `dives`, the dives either directly under
 * `dives` or grouped into `trip`s. Nearly everything is in attributes, each value with its unit ("30.1 m", "24.0 C", "45:30 min", "200.0
 * bar", "32.0%"). A dive merged from several computers lists each one's `divecomputer`; the first is the one Subsurface shows. Samples
 * write their deco state (`ndl`, `tts`, `stopdepth`, `stoptime`, `cns`, `po2`, `in_deco`) only when it changes, so each value holds until
 * the next sample that carries it.
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

    /** One `sample`'s attributes; the deco state is null where the sample doesn't carry it. */
    private class Sample(val seconds: Double?, val depth: Double?, val temperature: Double?) {
        var ndlSeconds: Double? = null
        var ttsSeconds: Double? = null
        var stopDepth: Double? = null
        var stopSeconds: Double? = null
        var cnsPercent: Double? = null
        var ppO2Bar: Double? = null
        var inDeco: Boolean? = null
        /** Bar, by cylinder index. */
        val pressures = mutableMapOf<Int, Double>()
    }

    /** A sample's deco state with each value carried forward from the sample that last set it. */
    private class DecoState(
        val ndlSeconds: Double? = null,
        val ttsSeconds: Double? = null,
        val stopDepth: Double? = null,
        val stopSeconds: Double? = null,
        val cnsPercent: Double? = null,
        val ppO2Bar: Double? = null,
    ) {
        fun after(s: Sample): DecoState {
            // Leaving deco clears the stop until a sample names another.
            val cleared = s.inDeco == false
            return DecoState(
                ndlSeconds = s.ndlSeconds ?: ndlSeconds,
                ttsSeconds = s.ttsSeconds ?: ttsSeconds,
                stopDepth = s.stopDepth ?: stopDepth.takeUnless { cleared },
                stopSeconds = s.stopSeconds ?: stopSeconds.takeUnless { cleared },
                cnsPercent = s.cnsPercent ?: cnsPercent,
                ppO2Bar = s.ppO2Bar ?: ppO2Bar,
            )
        }
    }

    private class Dive(val attribute: Map<String, String>) {
        val cylinders = mutableListOf<Cylinder>()
        val samples = mutableListOf<Sample>()
        /** Gases switched to, by cylinder index or by mix, at the event's time. */
        val switches = mutableListOf<GasChange>()
        var computers = 0
        var model: String? = null
        var closedCircuit = false
        var maxDepth: Double? = null
        var meanDepth: Double? = null
        var waterTemperature: Double? = null
        /** The cylinder each sample's `pressureN` reads, as the last `sensorN` set it. */
        val sensors = mutableMapOf<Int, Int>()

        fun decode(sites: Map<String, Site>, timeZone: TimeZone): DecodedActivity? {
            val text = listOfNotNull(attribute["date"], attribute["time"]).joinToString(" ")
            val localStart = parseLocalDateTime(text) ?: parseLocalDateTime("$text 00:00")
            val start = localStart?.toInstant(timeZone)?.toEpochMilliseconds() ?: return null
            val site = attribute["divesiteid"]?.let { sites[it] }
            // The first cylinder is breathed from the start; a gas change adds the next.
            fun GasChange.gas() = mix ?: cylinder?.let { cylinders.getOrNull(it)?.gas }
            val gases = (listOf(cylinders.firstOrNull()?.gas) + switches.map { it.gas() }).filterNotNull().distinct()
            val gasSwitches = switches.mapNotNull { switch ->
                val gas = switch.gas()
                val seconds = switch.seconds
                if (gas == null || seconds == null) null else DecodedGasSwitch(start + (seconds * DiveLogs.MILLIS_PER_SECOND).toLong(), gas)
            }
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
                    tanks = tanks(),
                    entry = site?.position,
                    siteName = site?.name,
                )
            val elapsed =
                attribute["duration"]?.let(::seconds)?.takeIf { it > 0 } ?: samples.mapNotNull { it.seconds }.maxOrNull()?.takeIf { it > 0 }
            return DecodedActivity(
                fileId = DiveLogs.source(model),
                // Samples carry a temperature only when it changes; with none, the computer's header value stands in.
                sessions =
                    listOf(DiveLogs.summary(dive, start, elapsed, waterTemperature.takeIf { samples.none { s -> s.temperature != null } })),
                laps = emptyList(),
                records = records(start),
                dive = dive,
                localStart = localStart,
                title = site?.name,
                gasSwitches = gasSwitches,
                tankUpdates = tankUpdates(start),
            )
        }

        private fun timeOf(start: Long, s: Sample) = s.seconds?.let { start + (it * DiveLogs.MILLIS_PER_SECOND).toLong() }

        /** Each sample with its deco state carried forward. */
        private fun records(start: Long): List<DecodedSample> {
            var deco = DecoState()
            return samples.map { s ->
                deco = deco.after(s)
                SampleBuilder()
                    .apply {
                        time = timeOf(start, s)
                        depth = s.depth
                        temperature = s.temperature
                    }
                    .build()
                    .copy(
                        ppO2Bar = deco.ppO2Bar,
                        cnsPercent = deco.cnsPercent?.roundToInt(),
                        ndlSeconds = deco.ndlSeconds?.roundToLong(),
                        nextStopDepthMeters = deco.stopDepth?.takeIf { it > 0 },
                        nextStopSeconds = deco.stopSeconds?.roundToLong(),
                        ttsSeconds = deco.ttsSeconds?.roundToLong(),
                    )
            }
        }

        /** Each cylinder reading, named by the cylinder's index. */
        private fun tankUpdates(start: Long): List<DecodedTankUpdate> = samples.flatMap { s ->
            val time = timeOf(start, s) ?: return@flatMap emptyList()
            s.pressures.entries
                .sortedBy { it.key }
                .mapNotNull { (cylinder, bar) -> bar.takeIf { it > 0 }?.let { DecodedTankUpdate(time, cylinder.toString(), it) } }
        }

        /**
         * A tank per cylinder with a start or end pressure or readings over time, named by its index for [DecodedActivity.tankUpdates]. A
         * cylinder without start and end pressures takes them from its readings.
         */
        private fun tanks(): List<DiveTank> = cylinders.mapIndexedNotNull { i, cylinder ->
            val readings = samples.mapNotNull { it.pressures[i]?.takeIf { bar -> bar > 0 } }
            if (cylinder.startBar == null && cylinder.endBar == null && readings.isEmpty()) return@mapIndexedNotNull null
            DiveTank(cylinder.startBar ?: readings.firstOrNull(), cylinder.endBar ?: readings.lastOrNull(), null, sensor = i.toString())
        }
    }

    private class GasChange(val cylinder: Int?, val mix: DiveGas?, val seconds: Double?)

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
                        dive.switches +=
                            GasChange(
                                attribute("cylinder")?.toIntOrNull(),
                                gas(attribute("o2"), attribute("he")),
                                attribute("time")?.let(::seconds),
                            )
                    }
                "sample" -> dive.samples += sample(dive, attribute)
            }
        }

        private fun sample(dive: Dive, attribute: (String) -> String?): Sample {
            val sample =
                Sample(attribute("time")?.let(::seconds), attribute("depth")?.let(::meters), attribute("temp")?.let(::celsius)).apply {
                    ndlSeconds = attribute("ndl")?.let(::seconds)
                    ttsSeconds = attribute("tts")?.let(::seconds)
                    stopDepth = attribute("stopdepth")?.let(::meters)
                    stopSeconds = attribute("stoptime")?.let(::seconds)
                    cnsPercent = attribute("cns")?.let(::percent)
                    ppO2Bar = attribute("po2")?.let(::bar)
                    inDeco = attribute("in_deco")?.trim()?.let { it == "1" }
                }
            // `pressure` is an older file's first cylinder; `pressureN` is the cylinder `sensorN` names, which holds until changed.
            attribute("pressure")?.let(::bar)?.let { sample.pressures[0] = it }
            for (n in 0 until MAX_SENSORS) {
                attribute("sensor$n")?.toIntOrNull()?.let { dive.sensors[n] = it }
                attribute("pressure$n")?.let(::bar)?.let { sample.pressures[dive.sensors[n] ?: n] = it }
            }
            return sample
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
    private const val MAX_SENSORS = 8
    private const val SIXTY = 60
    private const val METERS_PER_FOOT = 0.3048
    private const val FAHRENHEIT_OFFSET = 32.0
    private const val FAHRENHEIT_PER_CELSIUS = 1.8
    private const val PSI_PER_BAR = 14.5038
}
