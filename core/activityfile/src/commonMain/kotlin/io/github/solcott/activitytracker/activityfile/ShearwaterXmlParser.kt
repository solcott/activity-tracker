package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.DecoModel
import kotlin.math.roundToLong
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Reads a dive exported from Shearwater Cloud as XML: a `dive` root whose `diveLog` holds the header (dates, max depth, GF, CNS, deco
 * model) and a `diveLogRecord` every 10 seconds (depth, water temperature, gas, ppO₂, NDL, first stop depth, TTS, and up to four
 * transmitters' tank pressures, in PSI whatever the units). With `imperialUnits` set, depths are feet and temperatures °F.
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
        val tankUpdates = tankUpdates(state.records, start)
        val dive = dive(header, state.records, tankUpdates)
        return DecodedActivity(
            // Shearwater Cloud writes the computer's serial in hex ("9DA7AB33") and its firmware as a build number.
            fileId =
                DiveLogs.source(
                    MODELS.getOrNull(header.int("product") ?: -1),
                    MANUFACTURER,
                    serial = header["computerSerial"],
                    firmware = header["computerFirmware"],
                ),
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
                        .copy(
                            ppO2Bar = r.ppO2,
                            ndlSeconds = r.ndlMinutes?.let { (it * SECONDS_PER_MINUTE).roundToLong() },
                            // 0 is "not in deco"; the file has no per-stop time.
                            nextStopDepthMeters = header.meters(r.firstStopDepth?.takeIf { it > 0 }),
                            ttsSeconds = r.ttsMinutes?.let { (it * SECONDS_PER_MINUTE).roundToLong() },
                        )
                },
            dive = dive,
            localStart = localStart,
            gasSwitches = gasSwitches(state.records, start),
            tankUpdates = tankUpdates,
        )
    }

    /** Each numeric tank reading (text such as `AI is off` or `N/A` means none), named by its transmitter slot, "0" to "3". */
    private fun tankUpdates(records: List<RecordBuilder>, start: Long): List<DecodedTankUpdate> = records.flatMap { r ->
        val time = r.timeMillis ?: return@flatMap emptyList()
        r.tankPsi.entries
            .sortedBy { it.key }
            .mapNotNull { (slot, psi) -> psi.takeIf { it > 0 }?.let { DecodedTankUpdate(start + time, slot.toString(), it * BAR_PER_PSI) } }
    }

    /** A tank per transmitter slot with readings, its start and end its first and last. */
    private fun tanks(updates: List<DecodedTankUpdate>): List<DiveTank> =
        updates
            .groupBy { it.sensor }
            .entries
            .sortedBy { it.key }
            .map { (sensor, readings) -> DiveTank(readings.first().pressureBar, readings.last().pressureBar, null, sensor = sensor) }

    /** A switch wherever a record's gas differs from the one before it, the first record's gas included. */
    private fun gasSwitches(records: List<RecordBuilder>, start: Long): List<DecodedGasSwitch> {
        var breathing: DiveGas? = null
        return records.mapNotNull { r ->
            val gas = r.gas
            val time = r.timeMillis
            if (gas == null || time == null || gas == breathing) return@mapNotNull null
            breathing = gas
            DecodedGasSwitch(start + time, gas)
        }
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

    private fun dive(header: Header, records: List<RecordBuilder>, tankUpdates: List<DecodedTankUpdate>) =
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
            tanks = tanks(tankUpdates),
            decoModel = header.int("decoModel")?.let(DECO_MODELS::getOrNull),
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
        /** Bar. */
        var ppO2: Double? = null
        var ndlMinutes: Double? = null
        /** In the file's units; 0 outside deco. */
        var firstStopDepth: Double? = null
        var ttsMinutes: Double? = null
        /** Each transmitter slot's numeric reading. */
        val tankPsi = mutableMapOf<Int, Double>()

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
                "averagePPO2" -> ppO2 = text.toDoubleOrNull()
                "currentNdl" -> ndlMinutes = text.toDoubleOrNull()
                "firstStopDepth" -> firstStopDepth = text.toDoubleOrNull()
                "ttsMins" -> ttsMinutes = text.toDoubleOrNull()
                else ->
                    TANK_PRESSURE.matchEntire(name)?.let { match ->
                        text.trim().toDoubleOrNull()?.let { tankPsi[match.groupValues[1].toInt()] = it }
                    }
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

    /** `decoModel`, as libdivecomputer numbers it: 0 is Bühlmann ZHL-16C with gradient factors. */
    private val DECO_MODELS = listOf(DecoModel.ZHL16C, DecoModel.VPM_B, DecoModel.VPM_B_GFS, DecoModel.DCIEM)

    private val TANK_PRESSURE = Regex("tank([0-3])pressurePSI")
    private const val BAR_PER_PSI = 0.0689475729
    private const val SECONDS_PER_MINUTE = 60
    private const val MANUFACTURER = "Shearwater"
    private const val METERS_PER_FOOT = 0.3048
    private const val FAHRENHEIT_OFFSET = 32.0
    private const val FAHRENHEIT_PER_CELSIUS = 1.8
    private const val MILLIS_PER_SECOND = 1000.0
}
