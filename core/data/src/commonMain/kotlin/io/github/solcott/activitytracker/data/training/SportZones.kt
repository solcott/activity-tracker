package io.github.solcott.activitytracker.data.training

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatClock
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.SportTraining
import io.github.solcott.activitytracker.lexicon.app.TrainingDetails
import kotlin.math.roundToLong

/** The kinds of zones a sport's training settings offer. */
enum class ZoneKind {
    HeartRate,
    Power,
    Speed,
}

/** Which sports may override the default thresholds, and which kinds of zones each offers. */
object SportZones {
    /**
     * The sports athletes can add overrides for, in the order "Add sport" lists them. Keep `actor.defs#sportTraining.sport`'s knownValues
     * in step.
     */
    val overridable: List<String> = listOf(Sport.RUN, Sport.RIDE, Sport.SWIM, Sport.ROW, Sport.XC_SKI, Sport.WALK, Sport.HIKE)

    private val heartRateOnly = setOf(ZoneKind.HeartRate)

    /**
     * The zone kinds [sport] offers; null is the defaults. Pace zones only where a published model exists (running and swimming), and power
     * only where athletes commonly measure it.
     */
    fun kinds(sport: String?): Set<ZoneKind> =
        when (sport) {
            null,
            Sport.RIDE,
            Sport.ROW -> setOf(ZoneKind.HeartRate, ZoneKind.Power)
            Sport.RUN -> setOf(ZoneKind.HeartRate, ZoneKind.Power, ZoneKind.Speed)
            Sport.SWIM -> setOf(ZoneKind.HeartRate, ZoneKind.Speed)
            else -> heartRateOnly
        }

    /** The unit [sport]'s pace reads in: per 100 m or yd for swimming, per km or mile otherwise. */
    fun paceUnit(sport: String?, units: UnitSystem): PaceUnit = if (sport == Sport.SWIM) PaceUnit.swim(units) else PaceUnit.distance(units)
}

/**
 * The thresholds and zone models one sport uses: its override's values where set, the defaults otherwise. Models are tokens, null when
 * neither picks one.
 */
@Immutable
data class EffectiveTraining(
    /** Watts. */
    val functionalThresholdPower: Long? = null,
    /** Beats per minute. */
    val maxHeartRate: Long? = null,
    /** Beats per minute. */
    val thresholdHeartRate: Long? = null,
    /** Beats per minute. Never per sport. */
    val restingHeartRate: Long? = null,
    /** Millimeters per second. Only per sport: the defaults have none. */
    val thresholdSpeed: Long? = null,
    val heartRateZoneModel: String? = null,
    val powerZoneModel: String? = null,
    val speedZoneModel: String? = null,
)

/** [sport]'s override, or null when it has none. */
fun TrainingDetails.override(sport: String?): SportTraining? = sport?.let { s -> sports.find { it.sport == s } }

/** The thresholds and models an activity of [sport] uses; null [sport] is the defaults. */
fun TrainingDetails.forSport(sport: String?): EffectiveTraining {
    val o = override(sport)
    return EffectiveTraining(
        functionalThresholdPower = o?.functionalThresholdPower ?: functionalThresholdPower,
        maxHeartRate = o?.maxHeartRate ?: maxHeartRate,
        thresholdHeartRate = o?.thresholdHeartRate ?: thresholdHeartRate,
        restingHeartRate = restingHeartRate,
        thresholdSpeed = o?.thresholdSpeed,
        heartRateZoneModel = o?.heartRateZoneModel ?: heartRateZoneModel,
        powerZoneModel = o?.powerZoneModel ?: powerZoneModel,
        speedZoneModel = o?.speedZoneModel,
    )
}

/** [sport]'s entry replaced by [entry], or removed when [entry] is null. A new entry goes last. */
fun TrainingDetails.withOverride(sport: String, entry: SportTraining?): TrainingDetails {
    val others = sports.filterNot { it.sport == sport }
    val index = sports.indexOfFirst { it.sport == sport }.takeIf { it >= 0 } ?: others.size
    return copy(sports = if (entry == null) others else others.toMutableList().apply { add(index, entry) })
}

private const val SWIM_PACE_LENGTHS = 100
private const val MM_PER_M = 1000.0

/** The distance a pace is given per: the one place every pace (a stat, a chart, a zone) reads its length from. */
enum class PaceUnit(val meters: Double) {
    PerKilometer(UnitConversions.METERS_PER_KM),
    PerMile(UnitConversions.METERS_PER_MILE),
    Per100Meters(SWIM_PACE_LENGTHS.toDouble()),
    Per100Yards(SWIM_PACE_LENGTHS * UnitConversions.METERS_PER_YARD);

    /** The seconds this unit takes at [mmPerSecond]; a standstill's is infinite ([Double.MAX_VALUE]), so a chart caps it. */
    fun secondsAt(mmPerSecond: Double): Double = if (mmPerSecond > 0) meters * MM_PER_M / mmPerSecond else Double.MAX_VALUE

    /** The seconds this unit takes on average over [distanceMeters] in [seconds]. */
    fun secondsOver(distanceMeters: Double, seconds: Long): Double = seconds / (distanceMeters / meters)

    companion object {
        /** A kilometer, or a mile. */
        fun distance(units: UnitSystem): PaceUnit =
            when (units) {
                UnitSystem.Metric -> PerKilometer
                UnitSystem.Imperial -> PerMile
            }

        /** 100 meters, or 100 yards, swimming. */
        fun swim(units: UnitSystem): PaceUnit =
            when (units) {
                UnitSystem.Metric -> Per100Meters
                UnitSystem.Imperial -> Per100Yards
            }
    }
}

/** Paces as `m:ss` (`h:mm:ss` from an hour up, as [formatClock] writes every pace), converted to and from the mm/s records store. */
object PaceText {
    private const val SECONDS_PER_MINUTE = 60L

    /** Whole seconds per [unit] at [mmPerSecond]. */
    fun seconds(mmPerSecond: Long, unit: PaceUnit): Long = unit.secondsAt(mmPerSecond.toDouble()).roundToLong()

    /** [mmPerSecond] as `m:ss` per [unit]. */
    fun format(mmPerSecond: Long, unit: PaceUnit): String = formatClock(seconds(mmPerSecond, unit))

    /** Typed `m:ss`, `h:mm:ss` or whole seconds per [unit] in mm/s; null when it doesn't parse or isn't positive. */
    fun parse(text: String, unit: PaceUnit): Long? {
        val parts = text.trim().split(':')
        val seconds =
            when (parts.size) {
                1 -> parts[0].toLongOrNull()
                2,
                3 -> {
                    val lead = parts[0].ifEmpty { "0" }.toLongOrNull()
                    val rest =
                        parts.drop(1).map { part -> part.takeIf { it.length == 2 }?.toLongOrNull()?.takeIf { it < SECONDS_PER_MINUTE } }
                    if (lead == null || rest.any { it == null }) null
                    else rest.fold(lead) { total, part -> total * SECONDS_PER_MINUTE + part!! }
                }
                else -> null
            }
        return seconds?.takeIf { it > 0 }?.let { unit.secondsAt(it.toDouble()).roundToLong() }
    }
}
