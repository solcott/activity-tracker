package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.DeviceConnection
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Turns a decoded file's header and FIT `device_info` messages into the devices it names, with the identifiers that tell one from another
 * across activities. Unlike [FitSensors], which keeps make and model for the public record, it keeps serial numbers, ANT IDs, firmware,
 * battery and operating time, so its result is stored only in the athlete's `self` space.
 */
object FitDevices {
    /**
     * The recording device first, from the file's header ([fileId], FIT `file_id`, TCX `Creator`, a dive log's computer), filled in from
     * the `device_info` at index 0 (FIT's creator); then each paired sensor, in the order the file first lists it. Devices write
     * `device_info` at the start and the end, and a sensor may send its serial only after the first, so a device's messages are merged:
     * each field takes the last value any of them gives. Local devices other than the recorder (its GPS, barometer) are left out. Null when
     * the file names no device.
     */
    fun interpret(fileId: DecodedSource?, sensors: List<DecodedSensor>, sessions: List<DecodedSummary>): ImportedDevices? {
        val grouped = LinkedHashMap<Any, List<DecodedSensor>>()
        for (sensor in sensors) {
            val key = sensor.deviceIndex ?: listOf(sensor.sourceType, sensor.deviceType, sensor.manufacturer, sensor.product)
            grouped[key] = grouped[key].orEmpty() + sensor
        }
        val creator = grouped.remove(CREATOR_INDEX)?.let(::merge)
        val paired = grouped.values.map(::merge).filter { it.sourceType?.fitKey() != FitSensors.LOCAL }
        val devices = listOfNotNull(recorder(fileId, creator)) + paired.map(::sensor)
        if (devices.isEmpty()) return null
        return ImportedDevices(devices, activityProfile = sessions.firstNotNullOfOrNull { it.sportProfileName?.trim()?.ifEmpty { null } })
    }

    private fun recorder(fileId: DecodedSource?, creator: DecodedSensor?): ImportedDevice? {
        val manufacturer = fileId?.manufacturer ?: creator?.manufacturer
        val product = fileId?.product ?: creator?.product
        val device =
            ImportedDevice(
                recorder = true,
                manufacturer = DeviceNames.manufacturer(manufacturer),
                model = model(product),
                serialNumber = fileId?.serialNumber ?: creator?.serialNumber?.toString(),
                connection = if (creator != null) DeviceConnection.LOCAL else null,
                softwareVersion = fileId?.softwareVersion ?: creator?.softwareVersion?.let(::version),
                operatingSeconds = creator?.cumulativeOperatingSeconds,
            )
        return device.takeIf { it.manufacturer != null || it.model != null || it.serialNumber != null }
    }

    private fun sensor(sensor: DecodedSensor) =
        ImportedDevice(
            recorder = false,
            kind = FitSensors.KINDS[sensor.deviceType?.fitKey()] ?: SensorKind.Other,
            manufacturer = DeviceNames.manufacturer(sensor.manufacturer),
            model = model(sensor.product),
            serialNumber = sensor.serialNumber?.toString(),
            antDeviceNumber = sensor.antDeviceNumber,
            antTransmissionType = sensor.antTransmissionType,
            connection = sensor.sourceType?.fitKey()?.let(CONNECTIONS::get),
            softwareVersion = sensor.softwareVersion?.let(::version),
            battery = FitSensors.BATTERIES[sensor.batteryStatus?.fitKey()],
            batteryPercent = sensor.batteryLevelPercent?.takeIf { it in 0..PERCENT },
            batteryMillivolts = sensor.batteryVolts?.let { (it * MILLI).roundToInt() }?.takeIf { it >= 0 },
            operatingSeconds = sensor.cumulativeOperatingSeconds,
        )

    /** One device's messages as one: each field the last any of them gives. */
    private fun merge(messages: List<DecodedSensor>): DecodedSensor = messages.reduce { earlier, later ->
        DecodedSensor(
            deviceIndex = later.deviceIndex ?: earlier.deviceIndex,
            sourceType = later.sourceType ?: earlier.sourceType,
            deviceType = later.deviceType ?: earlier.deviceType,
            manufacturer = later.manufacturer ?: earlier.manufacturer,
            product = later.product ?: earlier.product,
            batteryStatus = later.batteryStatus ?: earlier.batteryStatus,
            batteryLevelPercent = later.batteryLevelPercent ?: earlier.batteryLevelPercent,
            serialNumber = later.serialNumber ?: earlier.serialNumber,
            antDeviceNumber = later.antDeviceNumber ?: earlier.antDeviceNumber,
            antTransmissionType = later.antTransmissionType ?: earlier.antTransmissionType,
            softwareVersion = later.softwareVersion ?: earlier.softwareVersion,
            batteryVolts = later.batteryVolts ?: earlier.batteryVolts,
            cumulativeOperatingSeconds = later.cumulativeOperatingSeconds ?: earlier.cumulativeOperatingSeconds,
        )
    }

    /** Its name, else the file's product number, which identifies a device only its maker can name. */
    private fun model(product: String?): String? =
        DeviceNames.product(product) ?: product?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }

    /** FIT's firmware version, scaled by 100, as devices show it: 31.33, 5.4, 2.05. */
    private fun version(value: Double): String {
        val hundredths = (value * HUNDRED).roundToLong()
        val fraction = (hundredths % HUNDRED).toString().padStart(2, '0').trimEnd('0')
        return if (fraction.isEmpty()) "${hundredths / HUNDRED}" else "${hundredths / HUNDRED}.$fraction"
    }

    private const val CREATOR_INDEX = 0
    private const val PERCENT = 100
    private const val MILLI = 1000
    private const val HUNDRED = 100

    /** FIT source types, by [fitKey]. */
    private val CONNECTIONS =
        mapOf(
            "antplus" to DeviceConnection.ANT_PLUS,
            "ant" to DeviceConnection.ANT,
            "bluetoothlowenergy" to DeviceConnection.BLUETOOTH_LOW_ENERGY,
            "bluetooth" to DeviceConnection.BLUETOOTH,
            "wifi" to DeviceConnection.WIFI,
            "local" to DeviceConnection.LOCAL,
        )
}
