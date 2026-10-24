package io.github.solcott.activitytracker.lexicon.app

import kotlinx.serialization.Serializable

/** How surely a [SensorKey] tells one device from another. */
enum class SensorKeyStrength {
    /** Maker and serial number: this unit. */
    SERIAL,

    /** Maker, ANT device number and transmission type: this unit, as far as ANT can tell. */
    ANT,

    /** Maker and model: every unit of that model, so it may match someone else's on a shared computer. */
    MODEL,
}

/**
 * `app.activitytracker.gear.autoAssign#sensorKey`: what tells one device from another across imports, built from a [ActivityDevicesRecord]
 * device's strongest identifiers ([of]). It holds serial numbers and ANT IDs, so it's stored only in the athlete's `self` space.
 */
@Serializable
data class SensorKey(
    val manufacturer: String? = null,
    val serialNumber: String? = null,
    /** 1 to 65535. */
    val antDeviceNumber: Int? = null,
    /** 0 to 255. */
    val antTransmissionType: Int? = null,
    val model: String? = null,
) {
    /** How surely it tells units apart, from the identifiers it holds. */
    val strength: SensorKeyStrength
        get() =
            when {
                serialNumber != null -> SensorKeyStrength.SERIAL
                antDeviceNumber != null -> SensorKeyStrength.ANT
                else -> SensorKeyStrength.MODEL
            }

    /** A stable name for the device it identifies, equal for keys that [match][matches]: for screens to tell devices apart. */
    val id: String
        get() =
            normalized().run { listOf(strength, manufacturer, serialNumber, antDeviceNumber, antTransmissionType, model) }.joinToString("|")

    /** Whether it may match other units of its model. */
    val weak: Boolean
        get() = strength == SensorKeyStrength.MODEL

    /**
     * Whether [other] names the same device: the same [strength] and equal fields, makers and models compared trimmed and case-folded,
     * serials as written.
     */
    fun matches(other: SensorKey): Boolean = normalized() == other.normalized()

    /** The key as [matches] compares it: only its [strength]'s fields, makers and models trimmed and case-folded. */
    fun normalized(): SensorKey =
        when (strength) {
            SensorKeyStrength.SERIAL -> SensorKey(manufacturer = fold(manufacturer), serialNumber = serialNumber)
            SensorKeyStrength.ANT ->
                SensorKey(
                    manufacturer = fold(manufacturer),
                    antDeviceNumber = antDeviceNumber,
                    antTransmissionType = antTransmissionType,
                )
            SensorKeyStrength.MODEL -> SensorKey(manufacturer = fold(manufacturer), model = fold(model))
        }

    companion object {
        /**
         * The key of [device] from its strongest identifiers: its serial; else its ANT device number and transmission type; else its model,
         * which needs a maker too. Null when the file names neither (a device known only by its kind), so it's neither offered nor matched.
         */
        fun of(device: ActivityDevice): SensorKey? {
            val maker = device.manufacturer?.trim()?.ifEmpty { null }
            val serial = device.serialNumber?.ifBlank { null }
            val model = device.model?.trim()?.ifEmpty { null }
            return when {
                serial != null -> SensorKey(manufacturer = maker, serialNumber = serial, model = model)
                device.antDeviceNumber != null && device.antTransmissionType != null ->
                    SensorKey(
                        manufacturer = maker,
                        antDeviceNumber = device.antDeviceNumber,
                        antTransmissionType = device.antTransmissionType,
                        model = model,
                    )
                maker != null && model != null -> SensorKey(manufacturer = maker, model = model)
                else -> null
            }
        }

        private fun fold(name: String?): String? = name?.trim()?.lowercase()?.ifEmpty { null }
    }
}

/** The gear [type] (a [GearType] value) and [kind] a new item made from a device starts with. */
data class GearSuggestion(val type: String, val kind: String? = null)

/** What gear a device is likely to be, from what it measures. */
object SensorGear {
    /**
     * The suggestion for a device of [role] (a [DeviceRole] value) and, for a sensor, [kind] (a [SensorType] value): a power meter or
     * shifter is a component, a strap, pod or radar a sensor, a light or the recording device a device, a trainer a trainer.
     */
    fun suggestion(role: String, kind: String?): GearSuggestion =
        if (role == DeviceRole.RECORDER) GearSuggestion(GearType.DEVICE)
        else
            when (kind) {
                SensorType.POWER -> GearSuggestion(GearType.COMPONENT, "powerMeter")
                SensorType.SHIFTING -> GearSuggestion(GearType.COMPONENT, "derailleur")
                SensorType.HEART_RATE -> GearSuggestion(GearType.SENSOR, "heartRate")
                SensorType.SPEED -> GearSuggestion(GearType.SENSOR, "speed")
                SensorType.CADENCE -> GearSuggestion(GearType.SENSOR, "cadence")
                SensorType.SPEED_CADENCE -> GearSuggestion(GearType.SENSOR, "speed")
                SensorType.FOOTPOD -> GearSuggestion(GearType.SENSOR, "footPod")
                SensorType.RADAR -> GearSuggestion(GearType.SENSOR, "radar")
                SensorType.LIGHT -> GearSuggestion(GearType.DEVICE)
                SensorType.TRAINER -> GearSuggestion(GearType.TRAINER, "smart")
                else -> GearSuggestion(GearType.SENSOR)
            }
}
