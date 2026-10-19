package io.github.solcott.activitytracker.app.detail

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Each metric's card on the activity page: its headline figures, its other figures, and what it charts. */
internal object MetricCards {
    /** [section]'s card from [charts], marked [mark]; null when it would hold nothing. */
    fun card(section: DetailSection, charts: ActivityCharts, figures: Figures, mark: String?): MetricCard? {
        val series =
            if (section == DetailSection.TankPressure) charts.tankPressures
            else charts.lines.filter { sectionOf(it.kind) == section }.toImmutableList()
        val (headline, rows) = figuresOf(section, charts, figures)
        val card =
            MetricCard(
                section = section,
                headline = headline.toImmutableList(),
                rows = rows.toImmutableList(),
                series = series,
                zones =
                    when (section) {
                        DetailSection.HeartRate -> charts.heartRateZones
                        DetailSection.Power -> charts.powerZones
                        else -> null
                    },
                powerCurve = if (section == DetailSection.Power && charts.powerCurve.size >= 2) charts.powerCurve else EMPTY_CURVE,
                gears = if (section == DetailSection.Gears) charts.gears else EMPTY_GEARS,
                mark = mark,
            )
        return card.takeUnless { it.isEmpty(section) }
    }

    private fun MetricCard.isEmpty(section: DetailSection): Boolean =
        headline.isEmpty() && rows.isEmpty() && series.isEmpty() && zones == null && gears.isEmpty() && section !in TABLES

    private fun figuresOf(section: DetailSection, charts: ActivityCharts, figures: Figures): Pair<List<Figure>, List<Figure>> =
        if (section in EFFORT) effortFigures(section, charts, figures) else otherFigures(section, charts, figures)

    /** The effort metrics' figures: heart rate, power and its dynamics, speed, cadence and elevation. */
    private fun effortFigures(section: DetailSection, charts: ActivityCharts, figures: Figures): Pair<List<Figure>, List<Figure>> =
        when (section) {
            DetailSection.HeartRate -> avgMax(StreamKind.HeartRate, figures) to emptyList()

            DetailSection.Power -> {
                val (avg, max) = avgMax(StreamKind.Power, figures).partition { it.key == FigureKey.Avg }
                val normalized = charts.normalizedPower?.let { Figure(FigureKey.NormalizedPower, watts(it)) }
                // The best 20-minute effort, or the longest under it when the ride is shorter.
                val best = charts.powerCurve.lastOrNull { it.seconds in SECONDS_PER_MINUTE..BEST_POWER_SECONDS }
                avg + listOfNotNull(normalized) + max to
                    listOfNotNull(
                        figures.work(),
                        best?.let { Figure(FigureKey.BestPower, watts(it.watts), index = it.seconds / SECONDS_PER_MINUTE) },
                        averageOf(StreamKind.LeftRightBalance, figures),
                    )
            }

            DetailSection.CyclingDynamics ->
                listOf(StreamKind.TorqueEffectiveness, StreamKind.PedalSmoothness).mapNotNull { kind ->
                    figures.line(kind)?.let { Figure(FigureKey.LeftRight, FigureValue.LeftRight(kind, it.average, it.right?.average)) }
                } to listOfNotNull(averageOf(StreamKind.PlatformCentreOffset, figures))

            DetailSection.Speed -> avgMax(StreamKind.Speed, figures) to emptyList()

            DetailSection.Cadence -> avgMax(StreamKind.Cadence, figures) to emptyList()

            DetailSection.Elevation -> {
                val elevation = figures.line(StreamKind.Elevation)
                listOfNotNull(
                    figures.elevationGain()?.takeUnless { it.value == FigureValue.Missing },
                    elevation?.max?.let { Figure(FigureKey.Highest, FigureValue.Stream(StreamKind.Elevation, it)) },
                    figures.line(StreamKind.Grade)?.max?.let { Figure(FigureKey.MaxGrade, FigureValue.Stream(StreamKind.Grade, it)) },
                ) to listOfNotNull(figures.descent())
            }

            else -> emptyList<Figure>() to emptyList()
        }

    /** The other cards' figures: temperature, physiology, a dive's, running dynamics, an e-bike's, gears and the tables. */
    private fun otherFigures(section: DetailSection, charts: ActivityCharts, figures: Figures): Pair<List<Figure>, List<Figure>> =
        when (section) {
            DetailSection.Temperature -> avgMax(StreamKind.Temperature, figures) to emptyList()

            DetailSection.Physiology -> averages(PHYSIOLOGY_STATS, figures) to emptyList()

            DetailSection.Depth -> listOfNotNull(figures.maxDepth(), figures.avgDepth()) to emptyList()

            DetailSection.Oxygen ->
                listOfNotNull(
                    figures.line(StreamKind.PpO2)?.max?.let { Figure(FigureKey.HighestPpO2, FigureValue.Stream(StreamKind.PpO2, it)) },
                    figures.line(StreamKind.Cns)?.max?.let { Figure(FigureKey.Cns, FigureValue.Stream(StreamKind.Cns, it)) },
                ) to emptyList()

            DetailSection.TankPressure ->
                charts.tankPressures.mapIndexedNotNull { i, tank ->
                    tank.max?.let { Figure(FigureKey.TankPressure, FigureValue.Stream(StreamKind.TankPressure, it), index = i + 1) }
                } to emptyList()

            DetailSection.Respiration -> avgMax(StreamKind.RespirationRate, figures) to emptyList()

            DetailSection.RunningDynamics -> averages(RUNNING_DYNAMICS_STATS, figures) to emptyList()

            DetailSection.EBike -> {
                val battery =
                    figures
                        .line(StreamKind.EbikeBattery)
                        ?.points
                        ?.takeIf { it.size >= 2 }
                        ?.let { Figure(FigureKey.BatteryUsed, FigureValue.BatteryUsed(it.first().value, it.last().value)) }
                listOfNotNull(battery) + averages(listOf(StreamKind.EbikeAssistLevel), figures) to emptyList()
            }

            DetailSection.Gears -> gearFigures(charts.gears) to figures.shiftRows()

            DetailSection.Laps ->
                listOf(
                    charts.skiRuns?.let { Figure(FigureKey.Runs, FigureValue.Count(it.toLong())) }
                        ?: Figure(FigureKey.Laps, FigureValue.Count(charts.laps.size.toLong()))
                ) to emptyList()

            DetailSection.Legs -> count(FigureKey.Legs, charts.legs.count { !it.transition }) to emptyList()

            DetailSection.Lengths -> count(FigureKey.Lengths, charts.lengths.count { !it.rest }) to emptyList()

            DetailSection.Sets -> {
                val reps = charts.sets.mapNotNull { if (it.rest) null else it.reps }
                count(FigureKey.Sets, charts.sets.count { !it.rest }) to
                    listOfNotNull(reps.takeIf { it.isNotEmpty() }?.let { Figure(FigureKey.Reps, FigureValue.Count(it.sum().toLong())) })
            }

            else -> emptyList<Figure>() to emptyList()
        }

    /** The headline average and maximum, as the record has them, else over the stream. */
    private fun avgMax(kind: StreamKind, figures: Figures): List<Figure> =
        listOfNotNull(
            figures.average(kind)?.let { Figure(FigureKey.Avg, FigureValue.Stream(kind, it)) },
            figures.max(kind)?.let { Figure(FigureKey.Max, FigureValue.Stream(kind, it)) },
        )

    private fun averageOf(kind: StreamKind, figures: Figures): Figure? =
        figures.line(kind)?.average?.let { Figure(FigureKey.StreamAverage, FigureValue.Stream(kind, it)) }

    private fun averages(kinds: List<StreamKind>, figures: Figures): List<Figure> = kinds.mapNotNull { averageOf(it, figures) }

    private fun count(key: FigureKey, n: Int) = listOf(Figure(key, FigureValue.Count(n.toLong())))

    /** How often the rider shifted, and the rear gear they spent longest in. */
    private fun gearFigures(gears: ImmutableList<GearPoint>): List<Figure> {
        val shifts = gears.zipWithNext().count { (a, b) -> a.front != b.front || a.rear != b.rear }
        // Each gear holds until the next shift; the last one's end isn't known, so it doesn't count.
        val mostUsed =
            gears
                .zipWithNext()
                .filter { it.first.rear != null }
                .groupBy { it.first.rear }
                .maxByOrNull { (_, spans) -> spans.sumOf { (a, b) -> b.seconds - a.seconds } }
                ?.key
        return listOfNotNull(
            Figure(FigureKey.Shifts, FigureValue.Count(shifts.toLong())),
            mostUsed?.let { Figure(FigureKey.MostUsedGear, FigureValue.Gear(it)) },
        )
    }

    /** The figures a runner compares run to run: bounce, contact and stride, and a Stryd's form power and leg spring stiffness. */
    private val RUNNING_DYNAMICS_STATS =
        listOf(
            StreamKind.VerticalOscillation,
            StreamKind.StanceTime,
            StreamKind.StepLength,
            StreamKind.FormPower,
            StreamKind.LegSpringStiffness,
        )

    private val PHYSIOLOGY_STATS = listOf(StreamKind.CoreTemperature, StreamKind.SmO2, StreamKind.TotalHemoglobin)

    private val EFFORT =
        setOf(
            DetailSection.HeartRate,
            DetailSection.Power,
            DetailSection.CyclingDynamics,
            DetailSection.Speed,
            DetailSection.Cadence,
            DetailSection.Elevation,
        )

    /** Sections whose card is a table the charts hold. */
    private val TABLES = setOf(DetailSection.Laps, DetailSection.Legs, DetailSection.Lengths, DetailSection.Sets)

    private val EMPTY_CURVE = persistentListOf<CurvePoint>()
    private val EMPTY_GEARS = persistentListOf<GearPoint>()
    private const val BEST_POWER_SECONDS = 1200
    private const val SECONDS_PER_MINUTE = 60
}

/** The section each line belongs in. */
internal fun sectionOf(kind: StreamKind): DetailSection =
    when (kind) {
        StreamKind.HeartRate -> DetailSection.HeartRate
        StreamKind.Power,
        StreamKind.LeftRightBalance -> DetailSection.Power
        StreamKind.Speed -> DetailSection.Speed
        StreamKind.Cadence -> DetailSection.Cadence
        StreamKind.Elevation,
        StreamKind.Grade -> DetailSection.Elevation
        StreamKind.Temperature -> DetailSection.Temperature
        StreamKind.Depth -> DetailSection.Depth
        StreamKind.PpO2,
        StreamKind.Cns -> DetailSection.Oxygen
        StreamKind.TankPressure -> DetailSection.TankPressure
        StreamKind.RespirationRate -> DetailSection.Respiration
        StreamKind.VerticalOscillation,
        StreamKind.StanceTime,
        StreamKind.StepLength,
        StreamKind.VerticalRatio,
        StreamKind.StanceTimeBalance,
        StreamKind.FormPower,
        StreamKind.LegSpringStiffness -> DetailSection.RunningDynamics
        StreamKind.TorqueEffectiveness,
        StreamKind.PedalSmoothness,
        StreamKind.PlatformCentreOffset -> DetailSection.CyclingDynamics
        StreamKind.CoreTemperature,
        StreamKind.SmO2,
        StreamKind.TotalHemoglobin -> DetailSection.Physiology
        StreamKind.EbikeBattery,
        StreamKind.EbikeAssistMode,
        StreamKind.EbikeAssistLevel -> DetailSection.EBike
    }
