package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What an activity's page shows of its data, worked out by the presenter from what the viewer was served: its headline figures, a training
 * load card, a card per metric, every figure grouped, and the avg/max table. The import preview builds the same from the file it would
 * publish. Figures carry label keys and values in record units; the UI words and formats them.
 */
@Immutable
data class ActivityPageContent(
    /** At most six, by sport family. */
    val hero: ImmutableList<Figure> = persistentListOf(),
    /** FTP, IF, TSS, training effect and HRV, each where recorded; empty when the viewer wasn't served training load, so no card shows. */
    val trainingLoad: ImmutableList<Figure> = persistentListOf(),
    /** One per [DetailSection] with data the viewer was served, in section order; never [DetailSection.Device]. */
    val cards: ImmutableList<MetricCard> = persistentListOf(),
    /** In [StatsGroupKind] order; a group with no figures is left out. */
    val stats: ImmutableList<StatsGroup> = persistentListOf(),
    /** In [AvgMaxMetric] order, only metrics with an average or a maximum. */
    val avgMax: ImmutableList<AvgMaxRow> = persistentListOf(),
    /** The charts the cards draw from: the shared x range, the elevation backdrop and a dive's markers. Null without streams. */
    val charts: ActivityCharts? = null,
    /** On the owner's page, the Map category's visibility when it's narrower than the activity, shown by the map. */
    val mapMark: String? = null,
)

/** One labelled figure. [index] completes labels like "Tank 2" or "Best 20 min power". [mark] is a category's narrower visibility. */
@Immutable data class Figure(val key: FigureKey, val value: FigureValue, val index: Int? = null, val mark: String? = null)

/** What a figure is, which the UI names in full ("Normalized power", not "NP"). */
enum class FigureKey {
    Distance,
    MovingTime,
    ElapsedTime,
    /** A sport without a rate's time: moving time, else elapsed. */
    Time,
    StartTime,
    ElevationGain,
    Descent,
    AvgSpeed,
    MaxSpeed,
    AvgPace,
    MaxPace,
    AvgHeartRate,
    MaxHeartRate,
    AvgPower,
    MaxPower,
    NormalizedPower,
    Work,
    Calories,
    AvgCadence,
    MaxCadence,
    AvgTemperature,
    PoolLength,
    AvgStrokeDistance,
    Steps,
    Strokes,
    Pushes,
    Shifts,
    Jumps,
    Grit,
    Flow,
    Runs,
    Laps,
    Legs,
    Lengths,
    Sets,
    Reps,
    MaxDepth,
    AvgDepth,
    DiveTime,
    /** The coldest water, else the average temperature. */
    WaterTemp,
    BottomTime,
    Gas,
    SurfaceInterval,
    DiveNumber,
    Cns,
    /** Whole-body oxygen exposure, in oxygen toxicity units. */
    Otu,
    Rmv,
    Sac,
    AirUsed,
    EndN2,
    HighestPpO2,
    MaxAscentRate,
    DescentTime,
    WaterType,
    Visibility,
    AirTemperature,
    Altitude,
    NoFlyTime,
    DesaturationTime,
    DecoModel,
    PpO2Limits,
    Setpoints,
    SafetyStop,
    Ftp,
    IntensityFactor,
    TrainingStressScore,
    AerobicEffect,
    AnaerobicEffect,
    Hrv,
    /** A card headline's average or maximum, under its metric's title. */
    Avg,
    Max,
    /** The best average power over [Figure.index] minutes. */
    BestPower,
    /** The highest point on the elevation stream. */
    Highest,
    MaxGrade,
    MostUsedGear,
    FrontShifts,
    RearShifts,
    /** Tank [Figure.index]'s highest pressure. */
    TankPressure,
    /** Named by its [FigureValue.Stream.kind]'s chart title: an average of a line with no headline of its own. */
    StreamAverage,
    /** The left and right legs' averages of [FigureValue.LeftRight.kind], named by its chart title. */
    LeftRight,
    BatteryUsed,
    Device,
    Sensor,
}

/** A figure's value in the units records and streams keep; the UI converts to the viewer's units. */
@Immutable
sealed interface FigureValue {
    /** Not recorded: a dash, where `feed/activity-stats` keeps the figure (a run's distance). */
    data object Missing : FigureValue

    data class Distance(val meters: Double) : FigureValue

    data class Duration(val seconds: Long) : FigureValue

    /** Meters or feet, whole. */
    data class Elevation(val meters: Double) : FigureValue

    /** A depth, stroke or visibility: meters or feet to one decimal. */
    data class Length(val meters: Double) : FigureValue

    data class Speed(val mmPerSecond: Long) : FigureValue

    /** Time per kilometer or mile, or per 100 m or yd when [swim]. */
    data class Pace(val mmPerSecond: Long, val swim: Boolean) : FigureValue

    data class Temperature(val tenthsCelsius: Long) : FigureValue

    data class Count(val value: Long) : FigureValue

    data class Decimal(val value: Double, val decimals: Int) : FigureValue

    data class Quantity(val value: Long, val unit: FigureUnit) : FigureValue

    /** In [kind]'s stream unit, as [LineSeries] keeps it. */
    data class Stream(val kind: StreamKind, val value: Double) : FigureValue

    data class LeftRight(val kind: StreamKind, val left: Double?, val right: Double?) : FigureValue

    data class BatteryUsed(val fromPercent: Double, val toPercent: Double) : FigureValue

    data class Gear(val rear: Int) : FigureValue

    data class Shifts(val front: Int, val rear: Int) : FigureValue

    data class PoolLength(val value: Int, val yards: Boolean) : FigureValue

    /** The start on the activity's own clock ([utcOffsetMinutes] east of UTC); just its date when the viewer wasn't served the time. */
    data class StartTime(val epochSeconds: Long, val utcOffsetMinutes: Int?, val dateOnly: Boolean) : FigureValue

    /** Free text as recorded, like a device's name. */
    data class Text(val text: String) : FigureValue

    /** A sensor, named by its kind (a `SensorKind` token) and its make and model. */
    data class Sensor(val kind: String, val manufacturer: String?, val model: String?) : FigureValue

    /** A token the UI names, like a `WaterType`; one it doesn't know reads as itself. */
    data class Token(val token: String) : FigureValue

    /** Each gas in order of first use, as oxygen and helium percents in pairs. */
    data class Gases(val mixes: ImmutableList<GasMix>, val closedCircuit: Boolean) : FigureValue

    /** A percent at the start and the end, like CNS. */
    data class PercentRange(val start: Int, val end: Int) : FigureValue

    data class PressureRange(val startBar: Double, val endBar: Double) : FigureValue

    data class Bar(val value: Double) : FigureValue

    /** Liters per minute. */
    data class GasRate(val litersPerMinute: Double) : FigureValue

    data class PressureRate(val barPerMinute: Double) : FigureValue

    /** A vertical speed, shown per minute in meters or feet. */
    data class VerticalRate(val mmPerSecond: Long) : FigureValue

    /** A long wait in hours and minutes, like a surface interval. */
    data class Minutes(val minutes: Int) : FigureValue

    /** A deco model token (null when the dive names none) and ZHL-16C's gradient factors. */
    data class DecoModel(val model: String?, val gradientFactorLow: Int?, val gradientFactorHigh: Int?) : FigureValue

    /** ppO₂ limits in millibar, each where set. */
    data class PpO2Limits(val warn: Int?, val critical: Int?, val deco: Int?) : FigureValue

    data class Setpoints(val low: Int?, val high: Int?) : FigureValue
}

@Immutable data class GasMix(val oxygen: Int, val helium: Int)

enum class FigureUnit {
    Watts,
    Bpm,
    Rpm,
    Kilocalories,
    Kilojoules,
    Milliseconds,
}

/** A metric's card: its title is [section]'s, marked with [mark] on the owner's page when its category is narrower than the activity. */
@Immutable
data class MetricCard(
    val section: DetailSection,
    /** Average and maximum, or what replaces them, like normalized power beside power. */
    val headline: ImmutableList<Figure>,
    /** The metric's other figures. */
    val rows: ImmutableList<Figure> = persistentListOf(),
    /** The lines it charts, in drawing order. */
    val series: ImmutableList<LineSeries> = persistentListOf(),
    /** Time in heart rate or power zones; null without zones. */
    val zones: ImmutableList<ZoneTime>? = null,
    /** The power card's best efforts; empty elsewhere. */
    val powerCurve: ImmutableList<CurvePoint> = persistentListOf(),
    /** The gears card's shifts; empty elsewhere. */
    val gears: ImmutableList<GearPoint> = persistentListOf(),
    val mark: String? = null,
)

@Immutable data class StatsGroup(val kind: StatsGroupKind, val rows: ImmutableList<Figure>)

/** The stats list's groups, in the order they show. */
enum class StatsGroupKind {
    Timing,
    DistanceSpeed,
    HeartRate,
    Power,
    Cadence,
    Elevation,
    WorkEnergy,
    Temperature,
    Counts,
    Dive,
    Device,
}

@Immutable data class AvgMaxRow(val metric: AvgMaxMetric, val avg: FigureValue?, val max: FigureValue?)

/** The avg/max table's rows, in order. A sport with a pace shows [Pace] in place of [Speed]. */
enum class AvgMaxMetric {
    HeartRate,
    Power,
    Speed,
    Pace,
    Cadence,
    Temperature,
}
