package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.ActivityDevice
import io.github.solcott.activitytracker.lexicon.app.DeviceRole
import io.github.solcott.activitytracker.lexicon.app.GearSuggestion
import io.github.solcott.activitytracker.lexicon.app.SensorGear
import io.github.solcott.activitytracker.lexicon.app.SensorKey

/**
 * The device as `feed.activityDevices` stores it. Every field comes from the file, so it's held to the lexicon's bounds here: a value the
 * PDS would refuse would stall the outbox, and a key built from it ([sensorKey]) matches the same device read back from a stored record.
 */
fun ImportedDevice.toActivityDevice(): ActivityDevice =
    ActivityDevice(
        role = if (recorder) DeviceRole.RECORDER else DeviceRole.SENSOR,
        kind = kind?.token,
        manufacturer = manufacturer?.take(ActivityDevice.MAX_NAME),
        model = model?.take(ActivityDevice.MAX_NAME),
        serialNumber = serialNumber?.take(ActivityDevice.MAX_IDENTIFIER),
        antDeviceNumber = antDeviceNumber?.takeIf { it in ActivityDevice.ANT_DEVICE_NUMBERS },
        antTransmissionType = antTransmissionType?.takeIf { it in ActivityDevice.ANT_TRANSMISSION_TYPES },
        connection = connection,
        softwareVersion = softwareVersion?.take(ActivityDevice.MAX_IDENTIFIER),
        battery = battery?.token,
        batteryPercent = batteryPercent?.takeIf { it in 0..ActivityDevice.MAX_PERCENT },
        batteryVoltage = batteryMillivolts?.takeIf { it >= 0 },
        operatingTime = operatingSeconds?.takeIf { it >= 0 },
    )

/** The device's [SensorKey], as it's stored; null when the file doesn't identify it. */
val ImportedDevice.sensorKey: SensorKey?
    get() = SensorKey.of(toActivityDevice())

/** The gear a sensor of this kind is likely to be. */
val SensorKind.suggestedGear: GearSuggestion
    get() = SensorGear.suggestion(DeviceRole.SENSOR, token)
