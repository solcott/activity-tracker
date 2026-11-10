package io.github.solcott.activitytracker.app.gear

import io.github.solcott.activitytracker.app.text.AppText

/**
 * What a linked or ignored device reads as, chosen here for the shared and the native UI (dev/shared-display-text): its name, the line
 * under it, and the spoken labels of its Un-ignore and Unlink.
 */
object LinkedSensorTexts {
    /** Its maker and model ([deviceName]): "Garmin Edge 840", or "Shearwater Perdix AI" for a model that names its maker. */
    fun name(sensor: LinkedSensor): AppText = AppText.written(deviceName(sensor.manufacturer, sensor.model))

    /** "Serial …2071", or that it may match other units when it is known only by its model; null when neither. */
    fun detail(sensor: LinkedSensor): AppText? =
        sensor.serialTail?.let { AppText.named("new_device_serial", AppText.written(it)) }
            ?: AppText.named("new_device_weak").takeIf { sensor.weak }

    /** Un-ignore read aloud: "Un-ignore Garmin Edge 840". */
    fun unignore(sensor: LinkedSensor): AppText = AppText.named("gear_unignore_named", name(sensor))

    /** Unlink read aloud: "Unlink SRAM 1052". */
    fun unlink(sensor: LinkedSensor): AppText = AppText.named("gear_unlink_named", name(sensor))
}
