package io.github.solcott.activitytracker.activityfile

import kotlin.math.roundToInt

/**
 * The Connect IQ developer fields we recognize, matched by their `field_description` name and units (trimmed, ignoring case) rather than by
 * app: Stryd ships several apps whose IDs and field numbers change between versions, but keeps these names. Anything else is ignored.
 */
object DeveloperFields {
    private val POWER = "power" to "watts"
    private val FORM_POWER = "form power" to "watts"
    private val LEG_SPRING_STIFFNESS = "leg spring stiffness" to "kn/m"

    /**
     * [sample] with its recognized developer fields applied: Stryd's power fills [DecodedSample.powerWatts] only when the record has no
     * native power, and form power and leg spring stiffness fill their own fields.
     */
    fun apply(sample: DecodedSample): DecodedSample {
        if (sample.developerFields.isEmpty()) return sample
        val fields = sample.developerFields.associate { (it.name?.trim()?.lowercase() to it.units?.trim()?.lowercase()) to it.value }
        return sample.copy(
            powerWatts = sample.powerWatts ?: fields[POWER]?.takeIf { it >= 0 }?.roundToInt(),
            formPowerWatts = fields[FORM_POWER]?.takeIf { it >= 0 },
            legSpringStiffnessKnPerM = fields[LEG_SPRING_STIFFNESS]?.takeIf { it >= 0 },
        )
    }
}
