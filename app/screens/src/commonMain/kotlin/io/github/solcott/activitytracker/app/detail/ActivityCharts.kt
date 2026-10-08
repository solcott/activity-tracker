package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.data.training.ZoneName
import kotlinx.collections.immutable.ImmutableList

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
    /** Best average power over each duration, shortest first; empty without power. */
    val powerCurve: ImmutableList<CurvePoint>,
    /** Each shift, in time order; empty without electronic shifting. */
    val gears: ImmutableList<GearPoint>,
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
}

/** [points] in [kind]'s unit; [average] and [max] are over every sample, not just the thinned points. */
@Immutable data class LineSeries(val kind: StreamKind, val points: ImmutableList<ChartSample>, val average: Double?, val max: Double?)

@Immutable data class ChartSample(val seconds: Int, val value: Double)

/** Time in one zone, labelled as the zone model labels it. [low] and [high] are bpm or watts; [high] is null for the top zone. */
@Immutable data class ZoneTime(val label: String, val name: ZoneName, val low: Int, val high: Int?, val seconds: Int)

@Immutable data class CurvePoint(val seconds: Int, val watts: Int)

/** Gears after a shift at [seconds]; rear gear 1 is the largest cog. */
@Immutable data class GearPoint(val seconds: Int, val front: Int?, val rear: Int?)
