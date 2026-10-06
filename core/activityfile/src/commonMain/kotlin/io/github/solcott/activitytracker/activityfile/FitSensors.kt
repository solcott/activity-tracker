package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.SensorType

/** What a paired sensor measures or does, and its lexicon [token] (a [SensorType] value). */
enum class SensorKind(val token: String) {
    HeartRate(SensorType.HEART_RATE),
    Power(SensorType.POWER),
    SpeedCadence(SensorType.SPEED_CADENCE),
    Speed(SensorType.SPEED),
    Cadence(SensorType.CADENCE),
    Shifting(SensorType.SHIFTING),
    Radar(SensorType.RADAR),
    Light(SensorType.LIGHT),
    Trainer(SensorType.TRAINER),
    Footpod(SensorType.FOOTPOD),
    Other(SensorType.OTHER),
}

/** How full a sensor's battery was when the activity ended, as the device reported it. */
enum class SensorBattery {
    New,
    Good,
    Ok,
    Low,
    Critical,
    Charging,
}

/** Turns a FIT file's `device_info` messages into the sensors paired with the recording device. */
object FitSensors {
    /**
     * One per paired sensor, in the order the file first lists them. Devices write `device_info` at the start and the end, so a sensor's
     * last message wins: it has the battery as the activity ended. The recording device and its built-in sensors (GPS, barometer) are
     * local, so they're left out: the recording device is the activity's `device`.
     */
    fun interpret(sensors: List<DecodedSensor>): List<ImportedSensor> {
        val latest = LinkedHashMap<Any, DecodedSensor>()
        for (sensor in sensors) {
            if (sensor.sourceType?.fitKey() == LOCAL) continue
            val key = sensor.deviceIndex ?: listOf(sensor.sourceType, sensor.deviceType, sensor.manufacturer, sensor.product)
            latest[key] = sensor
        }
        return latest.values.map { sensor ->
            ImportedSensor(
                kind = KINDS[sensor.deviceType?.fitKey()] ?: SensorKind.Other,
                manufacturer = DeviceNames.manufacturer(sensor.manufacturer),
                model = DeviceNames.product(sensor.product),
                battery = BATTERIES[sensor.batteryStatus?.fitKey()],
                batteryPercent = sensor.batteryLevelPercent?.takeIf { it in 0..PERCENT },
            )
        }
    }

    private const val LOCAL = "local"
    private const val PERCENT = 100

    /** ANT+ and Bluetooth device types, by [fitKey]. */
    private val KINDS: Map<String, SensorKind> =
        mapOf(
            "heartrate" to SensorKind.HeartRate,
            "bikepower" to SensorKind.Power,
            "bikespeedcadence" to SensorKind.SpeedCadence,
            "bikespeed" to SensorKind.Speed,
            "bikecadence" to SensorKind.Cadence,
            "shifting" to SensorKind.Shifting,
            "bikeradar" to SensorKind.Radar,
            "bikelightmain" to SensorKind.Light,
            "bikelightshared" to SensorKind.Light,
            "fitnessequipment" to SensorKind.Trainer,
            "biketrainer" to SensorKind.Trainer,
            "stridespeeddistance" to SensorKind.Footpod,
            "footpod" to SensorKind.Footpod,
        )

    private val BATTERIES: Map<String, SensorBattery> =
        mapOf(
            "new" to SensorBattery.New,
            "good" to SensorBattery.Good,
            "ok" to SensorBattery.Ok,
            "low" to SensorBattery.Low,
            "critical" to SensorBattery.Critical,
            "charging" to SensorBattery.Charging,
        )
}

/** Display names for devices from the names each FIT SDK spells its enums with ("EDGE_840", "edge840", "WAHOO_FITNESS", "wahooFitness"). */
internal object DeviceNames {
    /** "garmin" → "Garmin", "wahooFitness" → "Wahoo Fitness", "SRAM" → "SRAM". Null when the file doesn't say. */
    fun manufacturer(name: String?): String? = name?.takeUnless { it.fitKey() in UNKNOWN }?.let(::words)

    /**
     * "EDGE_840" or "edge840" → "Edge 840", "HRM_200" → "HRM 200". A display name with spaces (TCX's "Forerunner 965") is kept as written,
     * and a bare product number, which only its maker can name, is dropped. Products whose SDK name is narrower than the device take Garmin
     * Connect's name ([RENAMES]).
     */
    fun product(name: String?): String? {
        val trimmed = name?.trim()?.takeUnless { it.isEmpty() || it.fitKey() in UNKNOWN } ?: return null
        return when {
            ' ' in trimmed -> trimmed
            trimmed.all { it.isDigit() } -> null
            else -> RENAMES[trimmed.fitKey()] ?: words(trimmed)
        }
    }

    /** "Garmin Edge 840"; whichever half is known, or null for neither. */
    fun device(manufacturer: String?, product: String?): String? =
        listOfNotNull(manufacturer(manufacturer), product(product)).joinToString(" ").ifBlank { null }

    private fun words(name: String): String? =
        name
            .replace('_', ' ')
            .replace(WORD_BREAK, " ")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                val lower = word.lowercase()
                if (lower in ACRONYMS) lower.uppercase() else lower.replaceFirstChar { it.uppercase() }
            }
            .ifBlank { null }

    /** camelCase humps, and where letters meet a model number: "hrmPro", "edge840", "FR965". */
    private val WORD_BREAK = Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Za-z])(?=[0-9])")

    /** The SDKs' RALLY_200 covers the single-sided Rally 100 pedals too, which report the same product. */
    private val RENAMES = mapOf("rally200" to "Rally 100/200")

    private val UNKNOWN = setOf("unknown", "invalid", "development")

    /** Words the makers write in capitals. */
    private val ACRONYMS = setOf("hrm", "gps", "sram", "srm", "fsa", "fr", "xc", "se", "hr")
}
