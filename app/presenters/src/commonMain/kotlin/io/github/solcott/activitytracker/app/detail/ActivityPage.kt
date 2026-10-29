package io.github.solcott.activitytracker.app.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.solcott.activitytracker.app.visibility.CategoryVisibility
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.sport.RateKind
import io.github.solcott.activitytracker.data.sport.SportCatalog
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityDive
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.CycleCount
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearView
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.math.roundToLong
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Works out an activity's [ActivityPageContent] from its record as served (narrower categories the viewer wasn't admitted to already left
 * out) and its charts. Pure, so the activity page and the import preview build the same content, and tests call it directly.
 */
internal object ActivityPage {
    /**
     * [marks] holds, on the owner's own page, each detail category stored narrower than the activity with its visibility; empty for anyone
     * else's, so nothing is marked.
     */
    fun build(
        record: ActivityRecord,
        charts: ActivityCharts?,
        marks: Map<String, String> = emptyMap(),
        gear: List<GearView> = emptyList(),
    ): ActivityPageContent {
        val figures = Figures(record, charts, gear)
        return ActivityPageContent(
            start = figures.startTime(marks[DetailCategory.START_TIME]),
            hero = figures.hero(),
            trainingLoad = trainingLoad(charts?.trainingLoad),
            cards = cards(record, charts, figures, marks),
            stats = figures.stats(marks),
            avgMax = figures.avgMax(),
            charts = charts,
            mapMark = marks[DetailCategory.MAP]?.takeIf { record.polyline != null },
        )
    }

    /**
     * What [draft]'s page will show its owner once published, for the import preview: every category, each narrower than the activity
     * marked, and charts built from its streams, zones and training load off the main thread. [units] are the athlete's, for the lap table.
     */
    suspend fun fromDraft(draft: ActivityDraft, units: UnitSystem): ActivityPageContent {
        val record = draft.toRecord(createdAt = draft.startedAt, polyline = ActivityDraft.polylineOf(draft.route))
        val charts =
            draft.streams?.let { data ->
                val streams =
                    GetActivityStreams.Output(
                        uri = PREVIEW_URI,
                        visibility = draft.visibility,
                        channels = emptyList(),
                        data = data,
                        heartRateZones = draft.heartRateZones,
                        powerZones = draft.powerZones,
                        trainingLoad = draft.trainingLoad,
                    )
                ChartsBuilder.forSport(ChartsBuilder.buildOffMain(streams, units), record.sport)
            }
        return build(record, charts, narrowerThan(draft.visibility, draft.tiers))
    }

    /** Each of [tiers] narrower than the [activity]'s visibility. */
    fun narrowerThan(activity: String, tiers: Map<String, String>): Map<String, String> = tiers.filterValues {
        Visibility.rank(it) > Visibility.rank(activity)
    }

    private val PREVIEW_URI = AtUri("at://preview")

    private fun trainingLoad(load: TrainingLoadStats?): ImmutableList<Figure> =
        listOfNotNull(
                load?.ftp?.let { Figure(FigureKey.Ftp, FigureValue.Quantity(it.toLong(), FigureUnit.Watts)) },
                load?.intensityFactor?.let { Figure(FigureKey.IntensityFactor, FigureValue.Decimal(it, 2)) },
                load?.trainingStressScore?.let { Figure(FigureKey.TrainingStressScore, FigureValue.Decimal(it, 0)) },
                load?.aerobicTrainingEffect?.let { Figure(FigureKey.AerobicEffect, FigureValue.Decimal(it, 1)) },
                load?.anaerobicTrainingEffect?.let { Figure(FigureKey.AnaerobicEffect, FigureValue.Decimal(it, 1)) },
                load?.exerciseLoad?.let { Figure(FigureKey.ExerciseLoad, FigureValue.Count(it.toLong())) },
                load
                    ?.takeIf { it.staminaStart != null || it.staminaEnd != null || it.staminaMin != null }
                    ?.let { Figure(FigureKey.Stamina, FigureValue.Stamina(it.staminaStart, it.staminaEnd, it.staminaMin)) },
                load?.sweatLossMl?.let { Figure(FigureKey.SweatLoss, FigureValue.Fluid(it)) },
                load?.hrvRmssd?.let { Figure(FigureKey.Hrv, FigureValue.Quantity(it.toLong(), FigureUnit.Milliseconds)) },
            )
            .toImmutableList()

    private fun cards(record: ActivityRecord, charts: ActivityCharts?, figures: Figures, marks: Map<String, String>) =
        detailSections(record, charts)
            .filter { it != DetailSection.Device }
            .mapNotNull { section -> charts?.let { MetricCards.card(section, it, figures, marks[categoryOf(section)]) } }
            .toImmutableList()

    /** The detail category whose visibility a section's card is marked with; null for core data, never marked. */
    fun categoryOf(section: DetailSection): String? =
        when (section) {
            DetailSection.HeartRate -> DetailCategory.HEART_RATE
            DetailSection.Power,
            DetailSection.CyclingDynamics -> DetailCategory.POWER
            DetailSection.Speed -> DetailCategory.SPEED
            DetailSection.Cadence,
            DetailSection.RunningDynamics -> DetailCategory.CADENCE
            DetailSection.Physiology,
            DetailSection.Respiration -> DetailCategory.PHYSIOLOGY
            DetailSection.Depth,
            DetailSection.Oxygen,
            DetailSection.TankPressure -> DetailCategory.DIVE
            else -> null
        }
}

/** What the page shows of [shown] and its [charts]: each of the owner's [narrower] categories marked on its card. */
@Composable
internal fun rememberPage(shown: ActivityView?, charts: ActivityCharts?, narrower: ImmutableList<CategoryVisibility>): ActivityPageContent =
    remember(shown, charts, narrower) {
        shown?.let { ActivityPage.build(it.record, charts, narrower.associate { c -> c.category to c.visibility }, it.gear.orEmpty()) }
            ?: ActivityPageContent()
    }

/** How a sport's hero reads: each family has its own figures, in its own order. */
internal enum class SportFamily {
    Ride,
    OnFoot,
    Swim,
    Dive,
    Snow,
    /** Strength, yoga, HIIT and the rest without a pace or speed. */
    NoRate,
    Other;

    companion object {
        fun of(sport: String): SportFamily =
            when (sport) {
                Sport.RIDE,
                Sport.HANDCYCLE -> Ride
                Sport.RUN,
                Sport.WALK,
                Sport.HIKE -> OnFoot
                Sport.SWIM -> Swim
                Sport.DIVE -> Dive
                Sport.SKI,
                Sport.SNOWBOARD -> Snow
                else -> if (SportCatalog.rate(sport) == RateKind.None) NoRate else Other
            }
    }
}

/** Each figure of [record] and [charts] the page can show, by what it is. A figure the viewer wasn't served is null. */
@Suppress("TooManyFunctions") // One per figure, so the hero, the cards and the stats list share them.
internal class Figures(
    private val record: ActivityRecord,
    private val charts: ActivityCharts?,
    /** The gear the viewer may see on it (`activityView.gear`). */
    private val gear: List<GearView> = emptyList(),
) {
    private val sport = record.sport
    private val rate = SportCatalog.rate(sport)
    private val dive: ActivityDive? = record.dive
    private val time = record.movingTime ?: record.elapsedTime

    fun line(kind: StreamKind): LineSeries? = charts?.lines?.firstOrNull { it.kind == kind }

    /** What the sport's pace is per; null for a sport timed by speed or without a rate. */
    fun paceKind(): PaceKind? =
        when (rate) {
            RateKind.Pace -> PaceKind.Distance
            RateKind.SwimPace -> PaceKind.Swim
            else -> null
        }

    fun hero(): ImmutableList<Figure> =
        when (SportFamily.of(sport)) {
                SportFamily.Ride -> listOf(distance(), movingTime(), elevationGain(), rateAvg(), avgPower(), calories())
                SportFamily.OnFoot -> listOf(distance(), movingTime(), rateAvg(), elevationGain(), avgHeartRate(), calories())
                SportFamily.Swim -> listOf(distance(), movingTime(), rateAvg(), avgHeartRate(), calories())
                SportFamily.Dive -> listOf(maxDepth(), diveTime(), waterTemp(), avgDepth())
                SportFamily.Snow -> listOf(runs(), descent(), movingTime(), maxSpeed())
                SportFamily.NoRate -> listOf(Figure(FigureKey.Time, FigureValue.Duration(time)), avgHeartRate(), calories(), sets())
                SportFamily.Other -> listOf(distance(), movingTime(), elevationGain(), rateAvg(), calories())
            }
            .filterNotNull()
            .take(MAX_HERO)
            .toImmutableList()

    fun stats(marks: Map<String, String>): ImmutableList<StatsGroup> =
        listOf(
                StatsGroupKind.Timing to
                    listOf(
                        record.movingTime?.let { Figure(FigureKey.MovingTime, FigureValue.Duration(it)) },
                        Figure(FigureKey.ElapsedTime, FigureValue.Duration(record.elapsedTime)),
                    ),
                StatsGroupKind.DistanceSpeed to
                    listOf(
                        distance(),
                        rateAvg(),
                        rateMax(),
                        poolLength(record.poolLength)?.let { Figure(FigureKey.PoolLength, FigureValue.PoolLength(it.value, it.yards)) },
                        record.avgStrokeDistance
                            ?.takeIf { it > 0 }
                            ?.let { Figure(FigureKey.AvgStrokeDistance, FigureValue.Length(it / CM_PER_M)) },
                    ),
                StatsGroupKind.HeartRate to
                    listOf(
                        avgHeartRate(),
                        record.maxHeartRate?.let { Figure(FigureKey.MaxHeartRate, FigureValue.Quantity(it.toLong(), FigureUnit.Bpm)) },
                    ),
                StatsGroupKind.Power to
                    listOf(
                        avgPower(),
                        record.maxPower?.let { Figure(FigureKey.MaxPower, watts(it)) },
                        record.normalizedPower?.let { Figure(FigureKey.NormalizedPower, watts(it)) },
                    ),
                StatsGroupKind.Cadence to
                    listOf(
                        record.avgCadence?.let { Figure(FigureKey.AvgCadence, FigureValue.Quantity(it.toLong(), FigureUnit.Rpm)) },
                        record.maxCadence?.let { Figure(FigureKey.MaxCadence, FigureValue.Quantity(it.toLong(), FigureUnit.Rpm)) },
                    ),
                StatsGroupKind.Elevation to elevationRows(),
                StatsGroupKind.WorkEnergy to
                    listOf(
                        work(),
                        calories()?.copy(mark = marks[DetailCategory.CALORIES]),
                    ),
                StatsGroupKind.Temperature to temperatureRows(),
                StatsGroupKind.Counts to counts(),
                StatsGroupKind.Dive to diveFigures(),
                StatsGroupKind.Device to
                    listOf(record.device?.let { Figure(FigureKey.Device, FigureValue.Text(it)) }) +
                        record.sensors.orEmpty().map { Figure(FigureKey.Sensor, FigureValue.Sensor(it.kind, it.manufacturer, it.model)) } +
                        gear.map { Figure(FigureKey.GearItem, FigureValue.GearItem(it.name, it.type, it.kind, it.uri.value)) },
            )
            .mapNotNull { (kind, rows) -> rows.filterNotNull().takeIf { it.isNotEmpty() }?.let { StatsGroup(kind, it.toImmutableList()) } }
            .toImmutableList()

    /** Gain and descent, then the highest and lowest points where the viewer was served the elevation stream. */
    private fun elevationRows(): List<Figure?> {
        val elevation = line(StreamKind.Elevation)
        return listOf(
            elevationGain(),
            descent(),
            elevation?.max?.let { Figure(FigureKey.Highest, FigureValue.Stream(StreamKind.Elevation, it)) },
            elevation?.min?.let { Figure(FigureKey.Lowest, FigureValue.Stream(StreamKind.Elevation, it)) },
        )
    }

    /**
     * The average, then the minimum and maximum where the viewer was served the temperature stream; a dive gives its water temperature
     * instead.
     */
    private fun temperatureRows(): List<Figure?> {
        val samples = line(StreamKind.Temperature)?.takeIf { dive == null }
        return listOf(
            record.avgTemperature?.let { Figure(FigureKey.AvgTemperature, FigureValue.Temperature(it)) },
            samples?.min?.let { Figure(FigureKey.MinTemperature, FigureValue.Temperature(it.roundToLong())) },
            samples?.max?.let { Figure(FigureKey.MaxTemperature, FigureValue.Temperature(it.roundToLong())) },
            dive?.minTemperature?.let { Figure(FigureKey.WaterTemp, FigureValue.Temperature(it)) },
        )
    }

    fun avgMax(): ImmutableList<AvgMaxRow> {
        val speed = average(StreamKind.Speed) to max(StreamKind.Speed)
        val paced = rate == RateKind.Pace || rate == RateKind.SwimPace
        fun speedValue(mm: Double?) = mm?.let {
            if (paced) FigureValue.Pace(it.toLong(), rate == RateKind.SwimPace) else FigureValue.Speed(it.toLong())
        }
        return listOf(
                AvgMaxRow(
                    AvgMaxMetric.HeartRate,
                    stream(StreamKind.HeartRate, average(StreamKind.HeartRate)),
                    stream(StreamKind.HeartRate, max(StreamKind.HeartRate)),
                ),
                AvgMaxRow(
                    AvgMaxMetric.Power,
                    stream(StreamKind.Power, average(StreamKind.Power)),
                    stream(StreamKind.Power, max(StreamKind.Power)),
                ),
                AvgMaxRow(if (paced) AvgMaxMetric.Pace else AvgMaxMetric.Speed, speedValue(speed.first), speedValue(speed.second)),
                AvgMaxRow(
                    AvgMaxMetric.Cadence,
                    stream(StreamKind.Cadence, average(StreamKind.Cadence)),
                    stream(StreamKind.Cadence, max(StreamKind.Cadence)),
                ),
                AvgMaxRow(
                    AvgMaxMetric.Temperature,
                    stream(StreamKind.Temperature, average(StreamKind.Temperature)),
                    stream(StreamKind.Temperature, max(StreamKind.Temperature)),
                    min = stream(StreamKind.Temperature, line(StreamKind.Temperature)?.min),
                ),
            )
            .filter { it.avg != null || it.max != null }
            .toImmutableList()
    }

    /**
     * [kind]'s average as the record has it, else over its stream. Records and streams keep heart rate, power, speed, cadence and
     * temperature in the same units.
     */
    fun average(kind: StreamKind): Double? =
        when (kind) {
            StreamKind.HeartRate -> record.avgHeartRate?.toDouble()
            StreamKind.Power -> record.avgPower?.toDouble()
            StreamKind.Speed -> record.avgSpeed?.toDouble()
            StreamKind.Cadence -> record.avgCadence?.toDouble()
            StreamKind.Temperature -> record.avgTemperature?.toDouble()
            else -> null
        } ?: line(kind)?.average

    fun max(kind: StreamKind): Double? =
        when (kind) {
            StreamKind.HeartRate -> record.maxHeartRate?.toDouble()
            StreamKind.Power -> record.maxPower?.toDouble()
            StreamKind.Speed -> record.maxSpeed?.toDouble()
            StreamKind.Cadence -> record.maxCadence?.toDouble()
            else -> null
        } ?: line(kind)?.max

    private fun stream(kind: StreamKind, value: Double?) = value?.let { FigureValue.Stream(kind, it) }

    /** The distance, a dash for a sport with a rate that has none, or nothing for one without a rate and no distance (`activity-stats`). */
    fun distance(): Figure? =
        record.distance.let { meters ->
            when {
                meters != null && meters > 0 -> Figure(FigureKey.Distance, FigureValue.Distance(meters.toDouble()))
                rate != RateKind.None ->
                    Figure(FigureKey.Distance, meters?.let { FigureValue.Distance(it.toDouble()) } ?: FigureValue.Missing)
                else -> null
            }
        }

    /**
     * The elevation gain when there is some, or a recorded 0 for a sport with a rate that can climb. Unlike distance it's never a dash: a
     * missing one, or a swim's 0, says nothing.
     */
    fun elevationGain(): Figure? {
        val meters = record.elevationGain ?: return null
        val shown = meters > 0 || (rate != RateKind.None && SportFamily.of(sport) != SportFamily.Swim)
        return Figure(FigureKey.ElevationGain, FigureValue.Elevation(meters.toDouble())).takeIf { shown }
    }

    private fun movingTime() = Figure(FigureKey.MovingTime, FigureValue.Duration(time))

    fun descent() = record.elevationLoss?.let { Figure(FigureKey.Descent, FigureValue.Elevation(it.toDouble())) }

    /** The sport's average speed or pace over its moving time, a dash without a distance; nothing for a sport without a rate. */
    fun rateAvg(): Figure? {
        val mm = record.avgSpeed ?: record.distance?.takeIf { it >= MIN_PACE_DISTANCE_M && time > 0 }?.let { it * MM_PER_M / time }
        return when (rate) {
            RateKind.Speed -> Figure(FigureKey.AvgSpeed, mm?.let { FigureValue.Speed(it) } ?: FigureValue.Missing)
            RateKind.Pace,
            RateKind.SwimPace ->
                Figure(FigureKey.AvgPace, mm?.let { FigureValue.Pace(it, rate == RateKind.SwimPace) } ?: FigureValue.Missing)
            RateKind.None -> null
        }
    }

    private fun rateMax(): Figure? {
        val mm = record.maxSpeed ?: return null
        return when (rate) {
            RateKind.Speed -> Figure(FigureKey.MaxSpeed, FigureValue.Speed(mm))
            RateKind.Pace,
            RateKind.SwimPace -> Figure(FigureKey.MaxPace, FigureValue.Pace(mm, rate == RateKind.SwimPace))
            RateKind.None -> null
        }
    }

    private fun maxSpeed(): Figure? =
        (record.maxSpeed ?: line(StreamKind.Speed)?.max?.toLong())?.let { Figure(FigureKey.MaxSpeed, FigureValue.Speed(it)) }

    fun work() = record.work?.let { Figure(FigureKey.Work, FigureValue.Quantity(it, FigureUnit.Kilojoules)) }

    /** Front and rear shifts as the electronic shifting counted them. */
    fun shiftRows(): List<Figure> =
        listOfNotNull(
            record.frontShifts?.let { Figure(FigureKey.FrontShifts, FigureValue.Count(it.toLong())) },
            record.rearShifts?.let { Figure(FigureKey.RearShifts, FigureValue.Count(it.toLong())) },
        )

    private fun avgHeartRate() =
        record.avgHeartRate?.let { Figure(FigureKey.AvgHeartRate, FigureValue.Quantity(it.toLong(), FigureUnit.Bpm)) }

    private fun avgPower() = record.avgPower?.let { Figure(FigureKey.AvgPower, watts(it)) }

    private fun calories() = record.calories?.let { Figure(FigureKey.Calories, FigureValue.Quantity(it.toLong(), FigureUnit.Kilocalories)) }

    private fun runs() = charts?.skiRuns?.let { Figure(FigureKey.Runs, FigureValue.Count(it.toLong())) }

    private fun sets() = charts?.sets?.count { !it.rest }?.takeIf { it > 0 }?.let { Figure(FigureKey.Sets, FigureValue.Count(it.toLong())) }

    private fun reps() =
        charts
            ?.sets
            ?.mapNotNull { it.reps.takeUnless { _ -> it.rest } }
            ?.takeIf { it.isNotEmpty() }
            ?.let { Figure(FigureKey.Reps, FigureValue.Count(it.sum().toLong())) }

    /** The deepest point, from the dive computer's summary, else the depth chart for a dive logged before records carried one. */
    fun maxDepth(): Figure? =
        (dive?.maxDepth?.let { it / CM_PER_M } ?: line(StreamKind.Depth)?.max)?.let { Figure(FigureKey.MaxDepth, FigureValue.Length(it)) }

    fun avgDepth(): Figure? =
        (dive?.avgDepth?.let { it / CM_PER_M } ?: line(StreamKind.Depth)?.average)?.let {
            Figure(FigureKey.AvgDepth, FigureValue.Length(it))
        }

    /** The whole dive, surface to surface: moving time means nothing underwater. */
    private fun diveTime() = Figure(FigureKey.DiveTime, FigureValue.Duration(record.elapsedTime))

    /** The coldest water, which divers plan for, else the average. */
    private fun waterTemp() =
        (dive?.minTemperature ?: record.avgTemperature)?.let { Figure(FigureKey.WaterTemp, FigureValue.Temperature(it)) }

    /** When it started, or only its day when the viewer wasn't served the time: the record then holds the local midnight. */
    fun startTime(mark: String?): Figure {
        val dateOnly = record.startedAt == DetailCategory.localMidnight(record.startedAt, record.utcOffset)
        return Figure(
            FigureKey.StartTime,
            FigureValue.StartTime(record.startedAt.epochSeconds, record.utcOffset, dateOnly),
            mark = mark,
        )
    }

    private fun counts(): List<Figure?> {
        val cycles =
            when (CycleCount.unitOf(sport)) {
                CycleCount.STEPS -> FigureKey.Steps
                CycleCount.STROKES -> FigureKey.Strokes
                CycleCount.PUSHES -> FigureKey.Pushes
                CycleCount.PEDAL_STROKES -> FigureKey.PedalStrokes
                else -> null
            }
        return listOf(
            cycles?.let { key -> record.cycles?.let { Figure(key, FigureValue.Count(it.toLong())) } },
            record.rearShifts?.let { Figure(FigureKey.Shifts, FigureValue.Shifts(record.frontShifts ?: 0, it)) },
            record.jumps?.let { Figure(FigureKey.Jumps, FigureValue.Count(it.toLong())) },
            record.grit?.let { Figure(FigureKey.Grit, FigureValue.Decimal(it / TENTHS, 1)) },
            record.flow?.let { Figure(FigureKey.Flow, FigureValue.Decimal(it / TENTHS, 1)) },
            runs(),
            sets(),
            reps(),
        )
    }

    /** The Dive group: the depths, then the dive computer's summary; just the depths for a dive logged before records carried one. */
    private fun diveFigures(): List<Figure?> {
        val depths = listOf(maxDepth(), avgDepth())
        val dive = dive ?: return depths.takeIf { sport == Sport.DIVE }.orEmpty()
        return depths + DiveFigures.of(dive)
    }

    private companion object {
        const val MAX_HERO = 6
        const val CM_PER_M = 100.0
        const val MM_PER_M = 1000
        const val TENTHS = 10.0
        /** Below this, pace is noise from GPS jitter. */
        const val MIN_PACE_DISTANCE_M = 10
    }
}

internal fun watts(value: Int) = FigureValue.Quantity(value.toLong(), FigureUnit.Watts)
