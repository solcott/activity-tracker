package io.github.solcott.activitytracker.activityfile

import io.github.solcott.activitytracker.lexicon.app.DiveAlertKind

/**
 * Every dive alert kind the app names: its [DiveAlertKind] token, its FIT `dive_alert` value, its UDDF 3.2 waypoint `alarm` and whether the
 * depth chart marks it as a warning. FIT's readers and writers, UDDF's and the chart all go through this table.
 */
object DiveAlerts {
    data class Kind(
        val token: String,
        /** FIT `dive_alert`; null for a UDDF alarm FIT doesn't name. */
        val fitValue: Int?,
        /** UDDF waypoint `alarm` text; null when UDDF has no alarm for it. */
        val uddfAlarm: String?,
        /** Says something went wrong or nearly did, so the depth chart marks it; informational alerts aren't drawn. */
        val warning: Boolean,
    )

    /** FIT `dive_alert`'s `alert_dismissed_by_key` and `alert_dismissed_by_timeout`: the diver acknowledging an alert, not an alert. */
    val FIT_DISMISSALS: Set<Int> = setOf(18, 19)

    @Suppress("MagicNumber")
    val ALL: List<Kind> =
        listOf(
            Kind(DiveAlertKind.NDL_REACHED, 0, null, warning = true),
            Kind(DiveAlertKind.GAS_SWITCH_PROMPTED, 1, null, warning = false),
            Kind(DiveAlertKind.NEAR_SURFACE, 2, "surface", warning = false),
            Kind(DiveAlertKind.APPROACHING_NDL, 3, null, warning = true),
            Kind(DiveAlertKind.PO2_WARN, 4, null, warning = true),
            Kind(DiveAlertKind.PO2_CRIT_HIGH, 5, null, warning = true),
            Kind(DiveAlertKind.PO2_CRIT_LOW, 6, null, warning = true),
            Kind(DiveAlertKind.TIME_ALERT, 7, null, warning = true),
            Kind(DiveAlertKind.DEPTH_ALERT, 8, null, warning = true),
            Kind(DiveAlertKind.DECO_CEILING_BROKEN, 9, "deco", warning = true),
            Kind(DiveAlertKind.DECO_COMPLETE, 10, null, warning = false),
            Kind(DiveAlertKind.SAFETY_STOP_BROKEN, 11, null, warning = true),
            Kind(DiveAlertKind.SAFETY_STOP_COMPLETE, 12, null, warning = false),
            Kind(DiveAlertKind.CNS_WARNING, 13, null, warning = true),
            Kind(DiveAlertKind.CNS_CRITICAL, 14, null, warning = true),
            Kind(DiveAlertKind.OTU_WARNING, 15, null, warning = true),
            Kind(DiveAlertKind.OTU_CRITICAL, 16, null, warning = true),
            Kind(DiveAlertKind.ASCENT_CRITICAL, 17, "ascent", warning = true),
            Kind(DiveAlertKind.BATTERY_LOW, 20, null, warning = true),
            Kind(DiveAlertKind.BATTERY_CRITICAL, 21, null, warning = true),
            Kind(DiveAlertKind.SAFETY_STOP_STARTED, 22, null, warning = false),
            Kind(DiveAlertKind.APPROACHING_FIRST_DECO_STOP, 23, null, warning = false),
            Kind(DiveAlertKind.SETPOINT_SWITCH_AUTO_LOW, 24, null, warning = false),
            Kind(DiveAlertKind.SETPOINT_SWITCH_AUTO_HIGH, 25, null, warning = false),
            Kind(DiveAlertKind.SETPOINT_SWITCH_MANUAL_LOW, 26, null, warning = false),
            Kind(DiveAlertKind.SETPOINT_SWITCH_MANUAL_HIGH, 27, null, warning = false),
            Kind(DiveAlertKind.AUTO_SETPOINT_SWITCH_IGNORED, 28, null, warning = false),
            Kind(DiveAlertKind.SWITCHED_TO_OPEN_CIRCUIT, 29, null, warning = false),
            Kind(DiveAlertKind.SWITCHED_TO_CLOSED_CIRCUIT, 30, null, warning = false),
            Kind(DiveAlertKind.TANK_BATTERY_LOW, 32, null, warning = true),
            Kind(DiveAlertKind.PO2_CCR_DIL_LOW, 33, null, warning = true),
            Kind(DiveAlertKind.DECO_STOP_CLEARED, 34, null, warning = false),
            Kind(DiveAlertKind.APNEA_NEUTRAL_BUOYANCY, 35, null, warning = false),
            Kind(DiveAlertKind.APNEA_TARGET_DEPTH, 36, null, warning = false),
            Kind(DiveAlertKind.APNEA_SURFACE, 37, null, warning = false),
            Kind(DiveAlertKind.APNEA_HIGH_SPEED, 38, null, warning = false),
            Kind(DiveAlertKind.APNEA_LOW_SPEED, 39, null, warning = false),
            Kind(DiveAlertKind.BREATH, null, "breath", warning = true),
            Kind(DiveAlertKind.ERROR, null, "error", warning = true),
            Kind(DiveAlertKind.LINK, null, "link", warning = true),
            Kind(DiveAlertKind.MICROBUBBLES, null, "microbubbles", warning = true),
            Kind(DiveAlertKind.RBT, null, "rbt", warning = true),
            Kind(DiveAlertKind.SKIN_COOLING, null, "skincooling", warning = true),
        )

    private val byFit = ALL.filter { it.fitValue != null }.associateBy { it.fitValue }
    private val byUddf = ALL.filter { it.uddfAlarm != null }.associateBy { it.uddfAlarm }
    private val byToken = ALL.associateBy { it.token }

    fun byFitValue(value: Int): Kind? = byFit[value]

    /** UDDF alarm text is matched without regard to case or surrounding space. */
    fun byUddfAlarm(alarm: String): Kind? = byUddf[alarm.trim().lowercase()]

    fun byToken(token: String): Kind? = byToken[token]

    /**
     * The token for a FIT `dive_alert` [value], or null for a dismissal. A value the table lacks is stored as [sdkName] in lower camel case
     * (`SOME_NEW_ALERT` → `someNewAlert`), or as its number when the SDK doesn't name it either.
     */
    fun fitToken(value: Int, sdkName: String? = null): String? {
        if (value in FIT_DISMISSALS) return null
        val name = sdkName?.trim()?.takeIf { it.isNotEmpty() && it.toIntOrNull() == null && !it.equals("invalid", ignoreCase = true) }
        return byFitValue(value)?.token ?: name?.let(WorkoutStructure::camelCase) ?: value.toString()
    }

    /** The token for a UDDF [alarm]: the table's, or the alarm's own text when the table lacks it. Null for blank text. */
    fun uddfToken(alarm: String): String? = byUddfAlarm(alarm)?.token ?: alarm.trim().takeIf { it.isNotEmpty() }

    fun isWarning(token: String): Boolean = byToken(token)?.warning == true
}
