package io.github.solcott.activitytracker.activityfile

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Reads a dive exported from Shearwater Cloud as XML: a `dive` root whose `diveLog` holds the header (dates, max depth, GF, CNS) and a
 * `diveLogRecord` every 10 seconds (depth, water temperature, gas). With `imperialUnits` set, depths are feet and temperatures °F.
 *
 * Times are the dive computer's wall clock with no zone, so they're read in [timeZone], the importing device's by default. A dive imported
 * after flying home would land at the wrong hour, so the clock's start goes along as [DecodedActivity.localStart] for the athlete to place
 * with [ImportedActivity.inZone].
 */
internal object ShearwaterXmlParser {
    fun parse(xml: String, timeZone: TimeZone = TimeZones.device()): DecodedActivity {
        val state = State()
        walkXml(xml, onStart = { path, _ -> state.start(path) }, onEnd = state::end)
        if (!state.sawDiveLog) activityFileError("No dive in this Shearwater file")

        val header = Header(state.header)
        val startText = header["startDate"].orEmpty()
        val localStart = parseLocalDateTime(startText)
        val start =
            localStart?.toInstant(timeZone)?.toEpochMilliseconds()
                ?: parseXmlTime(startText)
                ?: activityFileError("No start time in this Shearwater file")
        val end = header["endDate"]?.let { parseLocalTime(it, timeZone) }
        val elapsedSeconds = header.double("maxTime")?.takeIf { it > 0 } ?: end?.let { (it - start) / MILLIS_PER_SECOND }?.takeIf { it > 0 }
        val dive = dive(header, state.records)
        return DecodedActivity(
            fileId = DiveLogs.source(MODELS.getOrNull(header.int("product") ?: -1), MANUFACTURER),
            sessions = listOf(DiveLogs.summary(dive, start, elapsedSeconds)),
            laps = emptyList(),
            records =
                state.records.map { r ->
                    SampleBuilder()
                        .apply {
                            time = r.timeMillis?.let { start + it }
                            depth = header.meters(r.depth)
                            temperature = header.celsius(r.temperature)
                        }
                        .build()
                },
            dive = dive,
            localStart = localStart,
        )
    }

    /** The `diveLog` values, which know the file's units. */
    private class Header(private val values: Map<String, String>) {
        private val imperial = values["imperialUnits"].toBoolean()

        operator fun get(name: String) = values[name]

        fun int(name: String) = values[name]?.toIntOrNull()

        fun double(name: String) = values[name]?.toDoubleOrNull()

        fun meters(value: Double?) = value?.let { if (imperial) it * METERS_PER_FOOT else it }

        fun celsius(value: Double?) = value?.let { if (imperial) (it - FAHRENHEIT_OFFSET) / FAHRENHEIT_PER_CELSIUS else it }
    }

    private fun dive(header: Header, records: List<RecordBuilder>) =
        DecodedDive(
            maxDepthMeters = header.meters(header.double("maxDepth")),
            diveNumber = header.int("number"),
            surfaceIntervalMinutes = header.int("surfaceMins"),
            gradientFactorLow = header.int("gfMin")?.takeIf { it > 0 },
            gradientFactorHigh = header.int("gfMax")?.takeIf { it > 0 },
            startCns = header.int("startCns"),
            endCns = header.int("endCns"),
            gases = records.mapNotNull { it.gas }.distinct(),
            closedCircuit = records.any { it.closedCircuit },
        )

    private class State {
        var sawDiveLog = false
        val header = mutableMapOf<String, String>()
        var record: RecordBuilder? = null
        val records = mutableListOf<RecordBuilder>()

        fun start(path: List<String>) {
            when (path.last()) {
                "diveLog" -> sawDiveLog = true
                "diveLogRecord" -> record = RecordBuilder()
            }
        }

        fun end(path: List<String>, text: String) {
            val name = path.last()
            val current = record
            when {
                name == "diveLogRecord" -> {
                    current?.let { records += it }
                    record = null
                }
                current != null && path.parent == "diveLogRecord" -> current.read(name, text)
                path.parent == "diveLog" && text.isNotEmpty() -> header[name] = text
            }
        }
    }

    /** One `diveLogRecord`, in the file's units. */
    private class RecordBuilder {
        var timeMillis: Long? = null
        var depth: Double? = null
        var temperature: Double? = null
        var oxygen: Double? = null
        var helium: Double? = null
        var closedCircuit = false

        val gas: DiveGas?
            get() = DiveLogs.gasFromFractions(oxygen, helium)

        fun read(name: String, text: String) {
            when (name) {
                "currentTime" -> timeMillis = text.toDoubleOrNull()?.toLong()
                "currentDepth" -> depth = text.toDoubleOrNull()
                "waterTemp" -> temperature = text.toDoubleOrNull()
                "fractionO2" -> oxygen = text.toDoubleOrNull()
                "fractionHe" -> helium = text.toDoubleOrNull()
                // "OC/BO" is open circuit (or bailout); a rebreather logs "CC" settings.
                "currentCircuitSetting" -> closedCircuit = text.uppercase().startsWith("CC")
            }
        }
    }

    /**
     * Shearwater Cloud writes dates in US form ("10/16/2024 5:30:57 PM"). Also takes 24-hour and day-first dates (a first number over 12
     * must be the day) and ISO 8601, with or without a zone. Epoch millis, or null if unparseable.
     */
    fun parseLocalTime(text: String, timeZone: TimeZone): Long? =
        parseLocalDateTime(text)?.toInstant(timeZone)?.toEpochMilliseconds() ?: parseXmlTime(text.trim())

    /** Shearwater's model names, indexed by the `product` code libdivecomputer numbers them with. */
    private val MODELS =
        listOf(
            null,
            null,
            "Predator",
            "Petrel",
            "NERD",
            "Perdix",
            "Perdix AI",
            "NERD 2",
            "Teric",
            "Peregrine",
            "Petrel 3",
            "Perdix 2",
            "Tern",
            "Peregrine TX",
        )

    private const val MANUFACTURER = "Shearwater"
    private const val METERS_PER_FOOT = 0.3048
    private const val FAHRENHEIT_OFFSET = 32.0
    private const val FAHRENHEIT_PER_CELSIUS = 1.8
    private const val MILLIS_PER_SECOND = 1000.0
}
