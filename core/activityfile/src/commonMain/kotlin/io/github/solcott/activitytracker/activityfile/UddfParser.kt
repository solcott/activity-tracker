package io.github.solcott.activitytracker.activityfile

import kotlin.math.roundToInt
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

    private class Tank(val id: String?) : Element {
        var mixRef: String? = null
        var beginPascals: Double? = null
        var endPascals: Double? = null
        var volumeCubicMeters: Double? = null

        override fun read(name: String, parent: String?, text: String) {
            when (name) {
                "tankpressurebegin" -> beginPascals = text.toDoubleOrNull()
                "tankpressureend" -> endPascals = text.toDoubleOrNull()
                "tankvolume" -> volumeCubicMeters = text.toDoubleOrNull()
            }
        }
    }

    private class Waypoint : Element {
        var seconds: Double? = null
        var depth: Double? = null
        var temperature: Double? = null
        var heartRate: Int? = null
        var switchMix: String? = null
        var calculatedPo2: Double? = null
        val measuredPo2 = mutableListOf<Double>()
        var cns: Double? = null
        var noDecoSeconds: Double? = null
        var decoStopDepth: Double? = null
        var decoStopSeconds: Double? = null
        /** Each `tankpressure`, in Pascal, by the `tankdata` its `ref` names (null when it names none). */
        val tankPressures = mutableListOf<Pair<String?, Double>>()
        /** The `ref` of the `tankpressure` being read, which its text belongs to. */
        var tankRef: String? = null
        /** Each `alarm`'s text: `ascent`, `deco`, `rbt`, ... */
        val alarms = mutableListOf<String>()

        override fun read(name: String, parent: String?, text: String) {
            val number = text.toDoubleOrNull()
            when (name) {
                "divetime" -> seconds = number
                "depth" -> depth = number
                "temperature" -> temperature = number
                "heartrate" -> heartRate = number?.toInt()
                "calculatedpo2" -> calculatedPo2 = number
                "measuredpo2" -> number?.let { measuredPo2 += it }
                "cns" -> cns = number
                "nodecotime" -> noDecoSeconds = number
                "tankpressure" -> number?.let { tankPressures += tankRef to it }
                "alarm" -> alarms += text
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
        var airTemperature: Double? = null
        var altitude: Double? = null
        var visibility: Double? = null
        var noFlySeconds: Double? = null
        var desaturationSeconds: Double? = null
        var highestPo2: Double? = null
        val notes = mutableListOf<String>()

        override fun read(name: String, parent: String?, text: String) {
            val number = text.toDoubleOrNull()
            when (name) {
                "datetime" -> if (parent == "informationbeforedive") dateTime = text
                "divenumber" -> this.number = number?.toInt()
                "passedtime" -> if (parent == "surfaceintervalbeforedive") surfaceIntervalSeconds = number
                "greatestdepth" -> greatestDepth = number
                "averagedepth" -> averageDepth = number
                "diveduration" -> duration = number
                "airtemperature" -> airTemperature = number
                "altitude" -> if (parent == "informationbeforedive") altitude = number
                "visibility" -> visibility = number
                "noflighttime" -> noFlySeconds = number
                "desaturationtime" -> desaturationSeconds = number
                "highestpo2" -> highestPo2 = number
                "para" -> if (parent == "notes") notes += text
            }
        }

        fun decode(file: State, onlyComputer: Computer?, timeZone: TimeZone): DecodedActivity? {
            val text = dateTime.orEmpty()
            val localStart = parseLocalDateTime(text)
            val start = localStart?.toInstant(timeZone)?.toEpochMilliseconds() ?: parseXmlTime(text) ?: return null
            val site = links.firstNotNullOfOrNull { file.sites[it] }
            val computer = links.firstNotNullOfOrNull { file.computers[it] } ?: onlyComputer
            val mixes = (switchedMixes + tanks.mapNotNull { it.mixRef } + links).mapNotNull { file.mixes[it] }
            fun timeOf(w: Waypoint) = w.seconds?.let { start + (it * DiveLogs.MILLIS_PER_SECOND).toLong() }
            val sensors = tanks.mapIndexed { i, tank -> tank.id ?: "tank$i" }
            val tankUpdates = tankUpdates(sensors, ::timeOf)
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
                    tanks = tanks(file, sensors, tankUpdates.map { it.sensor }.toSet()),
                    entry = site?.position(),
                    siteName = site?.name,
                    airTemperatureCelsius = airTemperature?.let(::celsius),
                    visibilityMeters = visibility,
                    altitudeMeters = altitude,
                    noFlyTimeSeconds = noFlySeconds,
                    desaturationTimeSeconds = desaturationSeconds,
                    highestPpO2Bar = highestPo2?.let(::bar),
                )
            val elapsed = duration?.takeIf { it > 0 } ?: waypoints.mapNotNull { it.seconds }.maxOrNull()?.takeIf { it > 0 }
            return DecodedActivity(
                fileId = computer?.let { DiveLogs.source(it.model ?: it.name, it.manufacturer) },
                sessions = listOf(DiveLogs.summary(dive, start, elapsed)),
                laps = emptyList(),
                records = records(::timeOf),
                dive = dive,
                localStart = localStart,
                title = site?.name,
                gasSwitches =
                    waypoints.mapNotNull { w ->
                        val gas = w.switchMix?.let(file.mixes::get)?.let { DiveLogs.gasFromFractions(it.oxygen, it.helium) }
                        val time = timeOf(w)
                        if (gas == null || time == null) null else DecodedGasSwitch(time, gas)
                    },
                tankUpdates = tankUpdates,
                diveAlerts = alerts(::timeOf),
                notes = notes.joinToString("\n\n").trim().ifEmpty { null },
            )
        }

        private fun records(timeOf: (Waypoint) -> Long?) = waypoints.map { w ->
            SampleBuilder()
                .apply {
                    time = timeOf(w)
                    depth = w.depth
                    temperature = w.temperature?.let(::celsius)
                    heartRate = w.heartRate
                }
                .build()
                .copy(
                    ppO2Bar = w.calculatedPo2?.let(::bar),
                    measuredPpO2Bars = w.measuredPo2.map(::bar),
                    cnsPercent = w.cns?.roundToInt(),
                    ndlSeconds = w.noDecoSeconds?.toLong(),
                    nextStopDepthMeters = w.decoStopDepth,
                    nextStopSeconds = w.decoStopSeconds?.toLong(),
                )
        }

        /** Each waypoint `alarm` at its waypoint's time; one [DiveAlerts] doesn't name keeps its text. */
        private fun alerts(timeOf: (Waypoint) -> Long?) = waypoints.flatMap { w ->
            val time = timeOf(w) ?: return@flatMap emptyList()
            w.alarms.mapNotNull { alarm -> DiveAlerts.uddfToken(alarm)?.let { DecodedDiveAlert(time, it) } }
        }

        private fun tankUpdates(sensors: List<String>, timeOf: (Waypoint) -> Long?) = waypoints.flatMap { w ->
            val time = timeOf(w) ?: return@flatMap emptyList()
            w.tankPressures.mapNotNull { (ref, pascals) ->
                // A reading that names no tank is the only tank's.
                val sensor = ref?.takeIf { it in sensors } ?: sensors.singleOrNull()?.takeIf { ref == null }
                sensor?.let { DecodedTankUpdate(time, it, pascals / DiveLogs.PASCALS_PER_BAR) }
            }
        }

        /** Each tank that says anything: a pressure, a volume or readings over time ([readTanks], by sensor). */
        private fun tanks(file: State, sensors: List<String>, readTanks: Set<String>) =
            tanks.zip(sensors).mapNotNull { (tank, sensor) ->
                val gas = tank.mixRef?.let(file.mixes::get)?.let { DiveLogs.gasFromFractions(it.oxygen, it.helium) }
                val volume = tank.volumeCubicMeters?.takeIf { it > 0 }?.times(LITERS_PER_CUBIC_METER)
                val noPressures = tank.beginPascals == null && tank.endPascals == null
                if (noPressures && volume == null && sensor !in readTanks) {
                    null
                } else {
                    DiveTank(
                        tank.beginPascals?.div(DiveLogs.PASCALS_PER_BAR),
                        tank.endPascals?.div(DiveLogs.PASCALS_PER_BAR),
                        null,
                        volumeLiters = volume,
                        gas = gas,
                        sensor = sensor,
                    )
                }
            }

        /** A 0, 0 position is an exporter's placeholder, not a site in the Gulf of Guinea. */
        private fun Site.position(): DivePosition? =
            latitude
                ?.let { lat -> longitude?.let { lon -> DivePosition(lat, lon) } }
                ?.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
    }

    /** UDDF says Kelvin, but some exporters write Celsius anyway; no water a diver survives is below 200 K, and none is above 200 °C. */
    private fun celsius(value: Double) = if (value > KELVIN_OR_CELSIUS) value - DiveLogs.KELVIN_OFFSET else value

    /** UDDF says Pascal, but a partial pressure above 10 bar can't be breathed, so a smaller value was written in bar. */
    private fun bar(value: Double) = if (value > BAR_OR_PASCAL) value / DiveLogs.PASCALS_PER_BAR else value

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
                "tankdata" -> tank = Tank(attribute("id"))
                "waypoint" -> waypoint = Waypoint()
                "link" -> attribute("ref")?.let { ref -> tank?.let { it.mixRef = ref } ?: current.links.add(ref) }
                "switchmix" ->
                    attribute("ref")?.let { ref ->
                        current.switchedMixes.add(ref)
                        waypoint?.switchMix = ref
                    }
                "tankpressure" -> waypoint?.tankRef = attribute("ref")
                "decostop" ->
                    waypoint?.let { w ->
                        w.decoStopDepth = attribute("decodepth")?.toDoubleOrNull()
                        w.decoStopSeconds = attribute("duration")?.toDoubleOrNull()
                    }
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
    private const val BAR_OR_PASCAL = 10.0
    private const val LITERS_PER_CUBIC_METER = 1000.0
}
