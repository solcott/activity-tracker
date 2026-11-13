package io.github.solcott.activitytracker.app.settings

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.ZoneNameText
import io.github.solcott.activitytracker.data.training.PaceText
import io.github.solcott.activitytracker.data.training.PaceUnit
import io.github.solcott.activitytracker.data.training.Zone
import io.github.solcott.activitytracker.data.training.ZoneBasis
import io.github.solcott.activitytracker.data.training.ZoneKind
import io.github.solcott.activitytracker.data.training.ZoneModel
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.PrivacyZone

/**
 * What a zone's row says that follows a rule, chosen here for the shared and the native UI (dev/shared-display-text).
 *
 * Their signatures are also what gives [Zone] and [PrivacyZone] a Swift type: each appears in the contracts only as an immutable list's
 * element, whose type Swift export erases, and a native view reads those lists' elements as these classes (dev/ios-swift-interop).
 */
object ZoneTexts {
    /** A computed zone's label: "Zone 2", "Zone 5a". */
    fun label(zone: Zone): AppText = AppText.named("zones_label", AppText.written(zone.label))

    /** What a computed zone is called: "Aerobic". */
    fun name(zone: Zone): AppText = ZoneNameText.of(zone.name)

    /**
     * A computed zone's bounds with their unit: "143–150 bpm" and "180+ bpm" for the last heart rate zone, watts for power, and for speed
     * the paces per [paceUnit], slowest first and open at either end ("Slower than 6:15 /km", "5:10–4:50 /km", "4:15 /km or faster"). A
     * speed zone's `high` is the last mm/s inside it, so the faster pace shown is the next one's.
     */
    fun range(zone: Zone, kind: ZoneKind, paceUnit: PaceUnit): AppText {
        val high = zone.high
        return when (kind) {
            ZoneKind.HeartRate -> AppText.named("zones_summary_value_bpm", bounds(zone))
            ZoneKind.Power -> AppText.named("zones_summary_value_watts", bounds(zone))
            ZoneKind.Speed ->
                when {
                    zone.low == 0 && high != null -> AppText.named("zones_pace_slower", pace(high + 1L, paceUnit), paceUnit(paceUnit))
                    high == null -> AppText.named("zones_pace_faster", pace(zone.low.toLong(), paceUnit), paceUnit(paceUnit))
                    else ->
                        AppText.named("zones_pace_range", pace(zone.low.toLong(), paceUnit), pace(high + 1L, paceUnit), paceUnit(paceUnit))
                }
        }
    }

    /** What a pace is per: "/km", "/mi", "/100 m", "/100 yd". */
    fun paceUnit(unit: PaceUnit): AppText =
        AppText.named(
            when (unit) {
                PaceUnit.PerKilometer -> "zones_pace_unit_km"
                PaceUnit.PerMile -> "zones_pace_unit_mi"
                PaceUnit.Per100Meters -> "zones_pace_unit_100m"
                PaceUnit.Per100Yards -> "zones_pace_unit_100yd"
            },
        )

    /** A kind of zone's heading: "Heart rate zones", "Power zones", "Pace zones". */
    fun heading(kind: ZoneKind): AppText =
        AppText.named(
            when (kind) {
                ZoneKind.HeartRate -> "zones_heart_rate_heading"
                ZoneKind.Power -> "zones_power_heading"
                ZoneKind.Speed -> "zones_speed_heading"
            },
        )

    /** A zone model's name, by its token; one another client saved that the app has no name for shows as its token. */
    fun model(model: ZoneModel): AppText = modelChoice(model.token)

    /** One choice of a model picker, which names its options by token: the model's name, else the token as it is. */
    fun modelChoice(token: String): AppText = AppText.Named("zone_model_$token", fallback = AppText.written(token))

    /** What a model's zones are a share of: "Lactate threshold". */
    fun basis(basis: ZoneBasis): AppText =
        AppText.named(
            when (basis) {
                ZoneBasis.ThresholdHeartRate -> "zones_basis_threshold_heart_rate"
                ZoneBasis.MaxHeartRate -> "zones_basis_max_heart_rate"
                ZoneBasis.HeartRateReserve -> "zones_basis_reserve"
                ZoneBasis.FunctionalThresholdPower -> "zones_basis_ftp"
                ZoneBasis.ThresholdPace -> "zones_basis_threshold_pace"
            },
        )

    /** What a model needs before it has zones: "Enter your threshold power to see these zones." */
    fun missing(basis: ZoneBasis): AppText =
        AppText.named(
            when (basis) {
                ZoneBasis.ThresholdHeartRate -> "zones_needs_threshold_heart_rate"
                ZoneBasis.MaxHeartRate -> "zones_needs_max_heart_rate"
                ZoneBasis.HeartRateReserve -> "zones_needs_reserve"
                ZoneBasis.FunctionalThresholdPower -> "zones_needs_ftp"
                ZoneBasis.ThresholdPace -> "zones_needs_threshold_pace"
            },
        )

    /** A privacy zone's name: its label, or "Zone 2" for the second in the list when it has none. [index] is its place, from 0. */
    fun privacyZoneName(zone: PrivacyZone, index: Int): AppText =
        zone.label?.let(AppText::written) ?: AppText.named("privacy_zones_unnamed", AppText.written(formatWhole(index + 1)))

    /** "143–150", or "180+" for an open-ended zone, the bounds as plain whole numbers. */
    private fun bounds(zone: Zone): AppText {
        val low = AppText.written(zone.low.toString())
        return zone.high?.let { AppText.named("zones_range", low, AppText.written(it.toString())) }
            ?: AppText.named("zones_range_open", low)
    }

    private fun pace(mmPerSecond: Long, unit: PaceUnit): AppText = AppText.written(PaceText.format(mmPerSecond, unit))
}
