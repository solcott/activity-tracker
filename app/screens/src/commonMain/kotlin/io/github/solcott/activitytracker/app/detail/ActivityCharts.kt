package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.training.ZoneName
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What an activity's streams chart, worked out by the presenter and thinned for drawing. Units are SI-ish; the UI converts. */
@Immutable
data class ActivityCharts(
    /** Who may see the streams: a `Visibility` value. */
    val visibility: String,
    /** Seconds from the start to the last sample, the x range every time chart shares. */
    val durationSeconds: Int,
    /** In the order they're drawn. */
    val lines: ImmutableList<LineSeries>,
    val heartRateZones: ImmutableList<ZoneTime>?,
    val powerZones: ImmutableList<ZoneTime>?,
    /** Coggan's normalized power in watts; null without 30 s of power. */
    val normalizedPower: Int?,
    /** Best average power over each duration, shortest first; empty without power. */
    val powerCurve: ImmutableList<CurvePoint>,
    /** Each shift, in time order; empty without electronic shifting. */
    val gears: ImmutableList<GearPoint>,
    /** Each switch of breathing gas, in time order, for markers on the depth chart; empty when the dive stored none. */
    val gasSwitches: ImmutableList<GasSwitchPoint> = persistentListOf(),
    /** The deco ceiling (the next stop's depth) in meters, drawn under the depth line; empty outside deco and without deco data. */
    val ceiling: ImmutableList<ChartSample> = persistentListOf(),
    /** One line per tank, in the dive's tank order, in bar. */
    val tankPressures: ImmutableList<LineSeries> = persistentListOf(),
    /** The recording's laps, in the viewer's units; empty when it stored none. */
    val laps: ImmutableList<LapRow> = persistentListOf(),
    /** A pool swim's lengths, rests included; empty when it stored none. */
    val lengths: ImmutableList<LengthRow> = persistentListOf(),
    /** A workout's sets, rests included; empty when it stored none. */
    val sets: ImmutableList<SetRow> = persistentListOf(),
    /** A multisport activity's legs, transitions included; empty when it stored none. */
    val legs: ImmutableList<LegRow> = persistentListOf(),
    /** A ski or snowboard day's runs, its laps that aren't lift rides; null for other activities or a day without laps. */
    val skiRuns: Int? = null,
    /** The activity's training load, only when the viewer may see its zones record. */
    val trainingLoad: TrainingLoadStats? = null,
)

/** A stream drawn as a line. */
enum class StreamKind {
    /** Beats per minute. */
    HeartRate,

    /** Watts. */
    Power,

    /** Millimeters per second. */
    Speed,

    /** Revolutions or steps per minute. */
    Cadence,

    /** Meters above sea level. */
    Elevation,

    /** Percent. */
    Grade,

    /** Tenths of a degree Celsius. */
    Temperature,

    /** Meters below the surface. */
    Depth,

    /** Breaths per minute. */
    RespirationRate,

    /** Millimeters. */
    VerticalOscillation,

    /** Milliseconds. */
    StanceTime,

    /** Meters. */
    StepLength,

    /** Percent. */
    VerticalRatio,

    /** Percent on the left foot. */
    StanceTimeBalance,

    /** Percent from the right leg. */
    LeftRightBalance,

    /** Bar. */
    PpO2,

    /** Percent of the limit. */
    Cns,

    /** Bar; one line per tank. */
    TankPressure,

    /** Percent; the left leg, with the right as [LineSeries.right]. */
    TorqueEffectiveness,

    /** Percent; the left leg, with the right as [LineSeries.right]. */
    PedalSmoothness,

    /** Platform centre offset in millimeters, negative inboard; the left pedal, with the right as [LineSeries.right]. */
    PlatformCentreOffset,

    /** Tenths of a degree Celsius, like [Temperature]. */
    CoreTemperature,

    /** Muscle oxygen saturation, percent. */
    SmO2,

    /** Total hemoglobin concentration, grams per deciliter. */
    TotalHemoglobin,

    /** An e-bike's battery, percent. */
    EbikeBattery,

    /** An e-bike's assist mode, as the bike numbers it; drawn as steps. */
    EbikeAssistMode,

    /** An e-bike's assist level, percent. */
    EbikeAssistLevel,

    /** Stryd's running form power, watts. */
    FormPower,

    /** Stryd's leg spring stiffness, kN/m. */
    LegSpringStiffness,
}

/**
 * [points] in [kind]'s unit; [average] and [max] are over every sample, not just the thinned points. For a left/right pair (torque
 * effectiveness, pedal smoothness, platform centre offset) this is the left leg and [right] the right, drawn on one chart.
 */
@Immutable
data class LineSeries(
    val kind: StreamKind,
    val points: ImmutableList<ChartSample>,
    val average: Double?,
    val max: Double?,
    val right: LineSeries? = null,
)

@Immutable data class ChartSample(val seconds: Int, val value: Double)

/** Time in one zone, labelled as the zone model labels it. [low] and [high] are bpm or watts; [high] is null for the top zone. */
@Immutable data class ZoneTime(val label: String, val name: ZoneName, val low: Int, val high: Int?, val seconds: Int)

@Immutable data class CurvePoint(val seconds: Int, val watts: Int)

/** Gears after a shift at [seconds]; rear gear 1 is the largest cog. */
@Immutable data class GearPoint(val seconds: Int, val front: Int?, val rear: Int?)

/** A switch at [seconds] to the gas of [oxygen] and [helium] percent. */
@Immutable
data class GasSwitchPoint(val seconds: Int, val oxygen: Int, val helium: Int) {
    val kind: GasKind
        get() = GasKind.of(oxygen, helium)
}

/** How a breathing gas is named: Air for 21/0, EAN and its oxygen for nitrox, Oxygen for pure, Tx oxygen/helium for trimix. */
enum class GasKind {
    Air,
    Nitrox,
    Oxygen,
    Trimix;

    companion object {
        private const val AIR = 21
        private const val PURE = 99

        fun of(oxygen: Int, helium: Int): GasKind =
            when {
                helium > 0 -> Trimix
                oxygen == AIR -> Air
                oxygen >= PURE -> Oxygen
                else -> Nitrox
            }
    }
}

/**
 * A lap, numbered from 1, with its distance in kilometers or miles and its speed in km/h or mph as the viewer reads them, its ascent in
 * meters or feet, and its effort as recorded.
 */
@Immutable
data class LapRow(
    val number: Int,
    val elapsedSeconds: Int,
    val movingSeconds: Int?,
    val distance: Double?,
    val speed: Double?,
    val avgHeartRate: Int?,
    val avgPower: Int?,
    val avgCadence: Int?,
    val ascent: Double?,
    /** A `LapIntensity` token; null when the recording didn't say. */
    val intensity: String? = null,
    /** In meters or feet, like [ascent]. */
    val descent: Double? = null,
) {
    val isRest: Boolean
        get() = intensity == "rest"
}

/** A length of a pool swim: an active one numbered from 1, its [stroke] a `SwimStroke` token; a rest has only its time. */
@Immutable
data class LengthRow(val number: Int?, val stroke: String?, val seconds: Int, val strokes: Int?, val rest: Boolean) {
    /** Swim golf: the length's seconds plus its strokes, lower is more efficient. Null for a rest or without a stroke count. */
    val swolf: Int?
        get() = strokes?.takeUnless { rest }?.let { seconds + it }
}

/**
 * A set of a workout: an active one numbered from 1, with its [exercise] and [category] tokens (the FIT profile's, in snake case), reps and
 * weight in grams as recorded; a rest has only its time.
 */
@Immutable
data class SetRow(
    val number: Int?,
    val rest: Boolean,
    val category: String?,
    val exercise: String?,
    val reps: Int?,
    val weightGrams: Int?,
    val seconds: Int,
)

/** A leg of a multisport activity: its [sport] and [subSport] tokens, time and distance in meters; a transition has only its time. */
@Immutable data class LegRow(val sport: String, val subSport: String?, val transition: Boolean, val seconds: Int, val distanceMeters: Int?)

/** A pool's length as its pool is measured: whole meters, or whole yards for a yard pool. */
@Immutable data class PoolLength(val value: Int, val yards: Boolean)

/** Training load in display units: watts, IF as a ratio, TSS, and training effect from 0 to 5. */
@Immutable
data class TrainingLoadStats(
    val ftp: Int?,
    val intensityFactor: Double?,
    val trainingStressScore: Double?,
    val aerobicTrainingEffect: Double?,
    val anaerobicTrainingEffect: Double?,
    /** Heart rate variability: the RMSSD of the activity's beat-to-beat intervals, in milliseconds. */
    val hrvRmssd: Int? = null,
)
