package io.github.solcott.activitytracker.data.training

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
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
    fun paceUnit(sport: String?, units: UnitSystem): PaceUnit =
        when {
            sport == Sport.SWIM && units == UnitSystem.Metric -> PaceUnit.Per100Meters
            sport == Sport.SWIM -> PaceUnit.Per100Yards
            units == UnitSystem.Metric -> PaceUnit.PerKilometer
            else -> PaceUnit.PerMile
        }
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

/** The distance a pace is given per. */
enum class PaceUnit(val meters: Double) {
    PerKilometer(UnitConversions.METERS_PER_KM),
    PerMile(UnitConversions.METERS_PER_MILE),
    Per100Meters(SWIM_PACE_LENGTHS.toDouble()),
    Per100Yards(SWIM_PACE_LENGTHS * UnitConversions.METERS_PER_YARD),
}

/** Paces as `m:ss`, converted to and from the mm/s records store. */
object PaceText {
    private const val MM_PER_M = 1000.0
    private const val SECONDS_PER_MINUTE = 60L

    /** Whole seconds per [unit] at [mmPerSecond]. */
    fun seconds(mmPerSecond: Long, unit: PaceUnit): Long = (unit.meters * MM_PER_M / mmPerSecond).roundToLong()

    /** [mmPerSecond] as `m:ss` per [unit]. */
    fun format(mmPerSecond: Long, unit: PaceUnit): String {
        val total = seconds(mmPerSecond, unit)
        return "${total / SECONDS_PER_MINUTE}:${(total % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }

    /** Typed `m:ss` (or whole seconds) per [unit] in mm/s; null when it doesn't parse or isn't positive. */
    fun parse(text: String, unit: PaceUnit): Long? {
        val parts = text.trim().split(':')
        val seconds =
            when (parts.size) {
                1 -> parts[0].toLongOrNull()
                2 -> {
                    val minutes = parts[0].ifEmpty { "0" }.toLongOrNull()
                    val secs = parts[1].takeIf { it.length == 2 }?.toLongOrNull()?.takeIf { it < SECONDS_PER_MINUTE }
                    if (minutes != null && secs != null) minutes * SECONDS_PER_MINUTE + secs else null
                }
                else -> null
            }
        return seconds?.takeIf { it > 0 }?.let { (unit.meters * MM_PER_M / it).roundToLong() }
    }
}
