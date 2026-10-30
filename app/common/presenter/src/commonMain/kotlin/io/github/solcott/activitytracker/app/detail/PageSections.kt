package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// What the activity page shows of an activity: which sections it has data for, and its pool length. Detail's screen and record's import
// preview both build the page from these.

/** The sections [activity] has data for: one per kind of chart, and the device (with the gear the viewer may see). */
fun detailSections(activity: ActivityView, charts: ActivityCharts?): ImmutableList<DetailSection> =
    detailSections(activity.record, charts, hasGear = !activity.gear.isNullOrEmpty())

/** The sections [record] has data for: one per kind of chart, and the device, or the gear when [hasGear]. */
fun detailSections(record: ActivityRecord, charts: ActivityCharts?, hasGear: Boolean = false): ImmutableList<DetailSection> {
    val kinds = charts?.lines.orEmpty().map { it.kind }.toSet()
    return DetailSection.entries
        .filter { section ->
            when (section) {
                DetailSection.HeartRate -> StreamKind.HeartRate in kinds
                DetailSection.Power -> StreamKind.Power in kinds
                DetailSection.CyclingDynamics -> CYCLING_DYNAMICS.any { it in kinds }
                DetailSection.Speed -> StreamKind.Speed in kinds
                DetailSection.Cadence -> StreamKind.Cadence in kinds
                DetailSection.Elevation -> StreamKind.Elevation in kinds || StreamKind.Grade in kinds
                DetailSection.Temperature -> StreamKind.Temperature in kinds
                DetailSection.Physiology -> PHYSIOLOGY.any { it in kinds }
                DetailSection.Depth -> StreamKind.Depth in kinds
                DetailSection.Oxygen -> StreamKind.PpO2 in kinds || StreamKind.Cns in kinds
                DetailSection.TankPressure -> !charts?.tankPressures.isNullOrEmpty()
                DetailSection.Respiration -> StreamKind.RespirationRate in kinds
                DetailSection.RunningDynamics -> RUNNING_DYNAMICS.any { it in kinds }
                DetailSection.EBike -> E_BIKE.any { it in kinds }
                DetailSection.Gears -> charts != null && charts.gears.size >= 2
                DetailSection.Legs -> !charts?.legs.isNullOrEmpty()
                DetailSection.Lengths -> !charts?.lengths.isNullOrEmpty()
                DetailSection.Sets -> !charts?.sets.isNullOrEmpty()
                DetailSection.Laps -> !charts?.laps.isNullOrEmpty()
                DetailSection.Device -> record.device != null || !record.sensors.isNullOrEmpty() || hasGear
            }
        }
        .toImmutableList()
}

/** A pool length of [centimeters] in whole meters, or in whole yards when it's a yard pool; null for none. */
fun poolLength(centimeters: Int?): PoolLength? {
    centimeters?.takeIf { it > 0 } ?: return null
    val yards = centimeters / CM_PER_YARD
    val wholeYards = yards.roundToInt()
    return if (centimeters % CM_PER_M != 0 && abs(yards - wholeYards) < YARD_TOLERANCE) PoolLength(wholeYards, yards = true)
    else PoolLength((centimeters / CM_PER_M.toDouble()).roundToInt(), yards = false)
}

private const val CM_PER_M = 100
private const val CM_PER_YARD = 91.44
private const val YARD_TOLERANCE = 0.02

/** The lines the running dynamics section charts. */
val RUNNING_DYNAMICS =
    setOf(
        StreamKind.VerticalOscillation,
        StreamKind.StanceTime,
        StreamKind.StanceTimeBalance,
        StreamKind.StepLength,
        StreamKind.VerticalRatio,
        StreamKind.FormPower,
        StreamKind.LegSpringStiffness,
    )

val CYCLING_DYNAMICS = setOf(StreamKind.TorqueEffectiveness, StreamKind.PedalSmoothness, StreamKind.PlatformCentreOffset)

val PHYSIOLOGY = setOf(StreamKind.CoreTemperature, StreamKind.SmO2, StreamKind.TotalHemoglobin)

val E_BIKE = setOf(StreamKind.EbikeBattery, StreamKind.EbikeAssistMode, StreamKind.EbikeAssistLevel)
