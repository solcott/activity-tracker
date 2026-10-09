package io.github.solcott.activitytracker.activityfile

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Reads a UDDF (Universal Dive Data Format) logbook, the export MacDive, Subsurface, Suunto DM5, LogTRAK and divelogs.de share. One file
 * holds many dives under `profiledata/repetitiongroup/dive`; each refers by id to the gas mixes, sites and dive computers defined once
 * elsewhere in the file. Everything is SI: meters, seconds, Kelvin and Pascal, gas as fractions.
 *
 * Times are usually the dive computer's wall clock with no zone, read in [timeZone] and kept as [DecodedActivity.localStart], as with
 * Shearwater files.
 */
internal object UddfParser {
    fun parse(xml: String, timeZone: TimeZone = TimeZones.device()): List<DecodedActivity> {
        val state = State()
        walkXml(xml, onStart = state::start, onEnd = state::end)
        // A logbook from one computer often doesn't link it from each dive.
        val onlyComputer = state.computers.values.singleOrNull()
        // One undated dive shouldn't keep the rest of a logbook out.
        return state.dives
            .mapNotNull { it.decode(state, onlyComputer, timeZone) }
            .ifEmpty { activityFileError("No dated dive in this UDDF file") }
    }

    /** An element being read, which takes its children's text. */
    private interface Element {
        fun read(name: String, parent: String?, text: String)
    }

    private class Site : Element {
        var name: String? = null
        var latitude: Double? = null
        var longitude: Double? = null

        override fun read(name: String, parent: String?, text: String) {
            when {
                name == "name" && parent == "site" -> this.name = text
                name == "latitude" -> latitude = text.toDoubleOrNull()
                name == "longitude" -> longitude = text.toDoubleOrNull()
            }
        }
    }

    private class Computer : Element {
        var name: String? = null
        var model: String? = null
        var manufacturer: String? = null

        override fun read(name: String, parent: String?, text: String) {
            when {
                // UDDF nests the maker's own name: manufacturer/name.
                name == "name" && parent == "manufacturer" -> manufacturer = text
                name == "name" && parent == "divecomputer" -> this.name = text
                name == "model" -> model = text
            }
        }
    }

    private class Mix : Element {
        var oxygen: Double? = null
        var helium: Double? = null

        override fun read(name: String, parent: String?, text: String) {
            when (name) {
                "o2" -> oxygen = text.toDoubleOrNull()
                "he" -> helium = text.toDoubleOrNull()
            }
        }
    }

    private class Tank : Element {
        var mixRef: String? = null
        var beginPascals: Double? = null
        var endPascals: Double? = null

        override fun read(name: String, parent: String?, text: String) {
            when (name) {
                "tankpressurebegin" -> beginPascals = text.toDoubleOrNull()
                "tankpressureend" -> endPascals = text.toDoubleOrNull()
            }
        }
    }

    private class Waypoint : Element {
        var seconds: Double? = null
        var depth: Double? = null
        var temperature: Double? = null

        override fun read(name: String, parent: String?, text: String) {
            when (name) {
                "divetime" -> seconds = text.toDoubleOrNull()
                "depth" -> depth = text.toDoubleOrNull()
                "temperature" -> temperature = text.toDoubleOrNull()
            }
        }
    }

    private class Dive : Element {
        val links = mutableListOf<String>()
        var dateTime: String? = null
        var number: Int? = null
        var surfaceIntervalSeconds: Double? = null
        var greatestDepth: Double? = null
        var averageDepth: Double? = null
        var duration: Double? = null
        val tanks = mutableListOf<Tank>()
        val waypoints = mutableListOf<Waypoint>()
        val switchedMixes = mutableListOf<String>()
        var closedCircuit = false

        override fun read(name: String, parent: String?, text: String) {
            val number = text.toDoubleOrNull()
            when (name) {
                "datetime" -> if (parent == "informationbeforedive") dateTime = text
                "divenumber" -> this.number = number?.toInt()
                "passedtime" -> if (parent == "surfaceintervalbeforedive") surfaceIntervalSeconds = number
                "greatestdepth" -> greatestDepth = number
                "averagedepth" -> averageDepth = number
                "diveduration" -> duration = number
            }
        }

        fun decode(file: State, onlyComputer: Computer?, timeZone: TimeZone): DecodedActivity? {
            val text = dateTime.orEmpty()
            val localStart = parseLocalDateTime(text)
            val start = localStart?.toInstant(timeZone)?.toEpochMilliseconds() ?: parseXmlTime(text) ?: return null
            val site = links.firstNotNullOfOrNull { file.sites[it] }
            val computer = links.firstNotNullOfOrNull { file.computers[it] } ?: onlyComputer
            val mixes = (switchedMixes + tanks.mapNotNull { it.mixRef } + links).mapNotNull { file.mixes[it] }
            val records = waypoints.map { w ->
                SampleBuilder()
                    .apply {
                        time = w.seconds?.let { start + (it * DiveLogs.MILLIS_PER_SECOND).toLong() }
                        depth = w.depth
                        temperature = w.temperature?.let(::celsius)
                    }
                    .build()
            }
            val dive =
                DecodedDive(
                    maxDepthMeters = greatestDepth,
                    diveNumber = number,
                    surfaceIntervalMinutes = surfaceIntervalSeconds?.let { (it / DiveLogs.SECONDS_PER_MINUTE).toInt() },
                    gradientFactorLow = null,
                    gradientFactorHigh = null,
                    startCns = null,
                    endCns = null,
                    gases = mixes.mapNotNull { DiveLogs.gasFromFractions(it.oxygen, it.helium) }.distinct(),
                    closedCircuit = closedCircuit,
                    avgDepthMeters = averageDepth,
                    tanks =
                        tanks
                            .filter { it.beginPascals != null || it.endPascals != null }
                            .map {
                                DiveTank(it.beginPascals?.div(DiveLogs.PASCALS_PER_BAR), it.endPascals?.div(DiveLogs.PASCALS_PER_BAR), null)
                            },
                    entry = site?.position(),
                    siteName = site?.name,
                )
            val elapsed = duration?.takeIf { it > 0 } ?: waypoints.mapNotNull { it.seconds }.maxOrNull()?.takeIf { it > 0 }
            return DecodedActivity(
                fileId = computer?.let { DiveLogs.source(it.model ?: it.name, it.manufacturer) },
                sessions = listOf(DiveLogs.summary(dive, start, elapsed)),
                laps = emptyList(),
                records = records,
                dive = dive,
                localStart = localStart,
                title = site?.name,
            )
        }

        /** A 0, 0 position is an exporter's placeholder, not a site in the Gulf of Guinea. */
        private fun Site.position(): DivePosition? =
            latitude
                ?.let { lat -> longitude?.let { lon -> DivePosition(lat, lon) } }
                ?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
    }

    /** UDDF says Kelvin, but some exporters write Celsius anyway; no water a diver survives is below 200 K, and none is above 200 °C. */
    private fun celsius(value: Double) = if (value > KELVIN_OR_CELSIUS) value - DiveLogs.KELVIN_OFFSET else value

    private class State {
        val sites = mutableMapOf<String, Site>()
        val computers = mutableMapOf<String, Computer>()
        val mixes = mutableMapOf<String, Mix>()
        val dives = mutableListOf<Dive>()
        private var site: Site? = null
        private var computer: Computer? = null
        private var mix: Mix? = null
        private var dive: Dive? = null
        private var tank: Tank? = null
        private var waypoint: Waypoint? = null

        fun start(path: List<String>, attribute: (String) -> String?) {
            val current = dive
            if (current == null) startDefinition(path, attribute("id")) else startInDive(current, path.last(), attribute)
        }

        /** The sites, computers and mixes dives refer to, and the dives themselves. */
        private fun startDefinition(path: List<String>, id: String?) {
            when (path.last()) {
                "site" -> if (path.parent == "divesite") site = Site().also { if (id != null) sites[id] = it }
                "divecomputer" -> computer = Computer().also { if (id != null) computers[id] = it }
                "mix" -> if (path.parent == "gasdefinitions") mix = Mix().also { if (id != null) mixes[id] = it }
                "dive" -> if (path.parent == "repetitiongroup") dive = Dive()
            }
        }

        private fun startInDive(current: Dive, name: String, attribute: (String) -> String?) {
            when (name) {
                "tankdata" -> tank = Tank()
                "waypoint" -> waypoint = Waypoint()
                "link" -> attribute("ref")?.let { ref -> tank?.let { it.mixRef = ref } ?: current.links.add(ref) }
                "switchmix" -> attribute("ref")?.let { current.switchedMixes.add(it) }
                "divemode" -> if (attribute("type") == "closedcircuit") current.closedCircuit = true
                "setpo2" -> current.closedCircuit = true
            }
        }

        fun end(path: List<String>, text: String) {
            when (path.last()) {
                "site" -> site = null
                "divecomputer" -> computer = null
                "mix" -> mix = null
                "dive" -> {
                    dive?.let { dives += it }
                    dive = null
                }
                "tankdata" -> {
                    tank?.let { dive?.tanks?.add(it) }
                    tank = null
                }
                "waypoint" -> {
                    waypoint?.let { dive?.waypoints?.add(it) }
                    waypoint = null
                }
                else -> if (text.isNotEmpty()) read(path, text)
            }
        }

        /** Text goes to the innermost element being read. */
        private fun read(path: List<String>, text: String) {
            val element: Element? = site ?: computer ?: mix ?: waypoint ?: tank ?: dive
            element?.read(path.last(), path.parent, text)
        }
    }

    private const val KELVIN_OR_CELSIUS = 200.0
}
