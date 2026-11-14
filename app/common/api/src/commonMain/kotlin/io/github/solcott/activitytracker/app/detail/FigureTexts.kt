package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.gear.GearTexts
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DiveText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StartText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import kotlin.math.roundToLong
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * How the activity page's figures read (feed/activity-page-layout, dev/shared-display-text): a figure's label in full, its value with its
 * unit in the viewer's units, a stats group's and an avg/max row's name, and a card's title. The shared UI's `figureLabel` and `figureText`
 * and the native page both resolve what these return.
 */
@Suppress("TooManyFunctions") // One per kind of figure text, so both UIs ask the same.
object FigureTexts {
    /** A figure's label in full, as the hero, cards and stats list name it. */
    fun label(figure: Figure): AppText {
        val value = figure.value
        return when (figure.key) {
            FigureKey.BestPower -> AppText.named("page_best_power", AppText.written("${figure.index ?: 0}"))
            FigureKey.TankPressure -> AppText.named("page_tank_pressure", AppText.written("${figure.index ?: 1}"))
            FigureKey.StreamAverage -> StreamUnits.title((value as FigureValue.Stream).kind)
            FigureKey.LeftRight -> StreamUnits.title((value as FigureValue.LeftRight).kind)
            FigureKey.DecoModel ->
                AppText.named(if ((value as? FigureValue.DecoModel)?.model == null) "stat_gradient_factors" else "stat_deco_model")
            else -> AppText.named(LABELS.getValue(figure.key))
        }
    }

    /** A stats group's name. */
    fun group(kind: StatsGroupKind): AppText =
        AppText.named(
            when (kind) {
                StatsGroupKind.Timing -> "page_group_timing"
                StatsGroupKind.DistanceSpeed -> "page_group_distance_speed"
                StatsGroupKind.HeartRate -> "page_group_heart_rate"
                StatsGroupKind.Power -> "page_group_power"
                StatsGroupKind.Cadence -> "page_group_cadence"
                StatsGroupKind.Elevation -> "page_group_elevation"
                StatsGroupKind.WorkEnergy -> "page_group_work_energy"
                StatsGroupKind.Temperature -> "page_group_temperature"
                StatsGroupKind.Counts -> "page_group_counts"
                StatsGroupKind.Dive -> "page_group_dive"
                StatsGroupKind.Device -> "page_group_device"
            },
        )

    /** An avg/max table row's name. */
    fun avgMax(metric: AvgMaxMetric): AppText =
        AppText.named(
            when (metric) {
                AvgMaxMetric.HeartRate -> "chart_heart_rate"
                AvgMaxMetric.Power -> "chart_power"
                AvgMaxMetric.Speed -> "chart_speed"
                AvgMaxMetric.Pace -> "stat_pace"
                AvgMaxMetric.Cadence -> "chart_cadence"
                AvgMaxMetric.Temperature -> "chart_temperature"
            },
        )

    /** A section's name. */
    fun section(section: DetailSection): AppText =
        AppText.named(
            when (section) {
                DetailSection.HeartRate -> "chart_heart_rate"
                DetailSection.Power -> "chart_power"
                DetailSection.CyclingDynamics -> "detail_section_cycling_dynamics"
                DetailSection.Speed -> "chart_speed"
                DetailSection.Cadence -> "chart_cadence"
                DetailSection.Elevation -> "chart_elevation"
                DetailSection.Temperature -> "chart_temperature"
                DetailSection.Physiology -> "detail_section_physiology"
                DetailSection.Depth -> "chart_depth"
                DetailSection.Oxygen -> "detail_section_oxygen"
                DetailSection.TankPressure -> "detail_section_tank_pressure"
                DetailSection.Respiration -> "detail_section_respiration"
                DetailSection.RunningDynamics -> "detail_section_running_dynamics"
                DetailSection.EBike -> "detail_section_ebike"
                DetailSection.Gears -> "detail_section_gears"
                DetailSection.Legs -> "detail_section_legs"
                DetailSection.Lengths -> "detail_section_lengths"
                DetailSection.Sets -> "detail_section_sets"
                DetailSection.Laps -> "detail_section_laps"
                DetailSection.Device -> "detail_section_device"
            },
        )

    /** A card's title: Pace for a speed card on a sport timed by pace, else its section's name. */
    fun cardTitle(card: MetricCard): AppText = if (card.pace != null) AppText.named("chart_pace") else section(card.section)

    /** The avg/max table's heading and its columns' names. */
    val avgMaxHeading: AppText = AppText.named("page_avg_max_heading")
    val avgColumn: AppText = AppText.named("detail_avg")
    val minColumn: AppText = AppText.named("detail_min")
    val maxColumn: AppText = AppText.named("detail_max")

    /** The stats list's heading, and the training load card's title. */
    val allStats: AppText = AppText.named("page_all_stats")
    val trainingLoad: AppText = AppText.named("page_training_load")

    /** The page of the gear item [figure] names, when it opens one (gear/gear-usage); null for any other figure. */
    fun gearUri(figure: Figure): String? = (figure.value as? FigureValue.GearItem)?.uri

    /** Whether [rows] give a Min column: some row has a minimum (temperature). */
    fun showsMin(rows: List<AvgMaxRow>): Boolean = rows.any { it.min != null }

    /** Whether [content]'s avg/max table gives a Min column. */
    fun showsMin(content: ActivityPageContent): Boolean = showsMin(content.avgMax)

    /**
     * A figure's value in the viewer's [units], with its unit, in full. A start reads on the activity's own clock beside today, [now] in
     * [zone] (`StartText`).
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod") // One branch per kind of value.
    fun value(value: FigureValue, units: UnitSystem, now: Instant, zone: TimeZone): AppText =
        when (value) {
            FigureValue.Missing -> StatText.missing
            is FigureValue.Distance -> StatText.distance(value.meters, units)
            is FigureValue.Duration -> AppText.written(StatText.duration(value.seconds))
            is FigureValue.Elevation -> StatText.elevation(value.meters, units)
            is FigureValue.Length -> StatText.depth(value.meters, units)
            is FigureValue.Speed -> StatText.speed(value.mmPerSecond, units)
            is FigureValue.Pace -> pace(value, units)
            is FigureValue.Temperature -> StatText.temperature(value.tenthsCelsius, units)
            is FigureValue.Count -> AppText.written(formatWhole(value.value))
            is FigureValue.Decimal -> AppText.written(formatDecimal(value.value, value.decimals))
            is FigureValue.Quantity -> AppText.named(unitName(value.unit), AppText.written(formatWhole(value.value)))
            is FigureValue.Stream -> StreamUnits.valueText(value.kind, value.value, units)
            is FigureValue.LeftRight -> {
                val side = { v: Double? -> v?.let { StreamUnits.valueText(value.kind, it, units) } ?: StatText.missing }
                AppText.named("chart_left_right", side(value.left), side(value.right))
            }
            is FigureValue.BatteryUsed ->
                AppText.named(
                    "chart_battery_used",
                    AppText.named("unit_percent", AppText.written(formatDecimal(value.fromPercent, 0))),
                    AppText.named("unit_percent", AppText.written(formatDecimal(value.toPercent, 0))),
                )
            is FigureValue.Gear -> AppText.named("chart_gear_value", AppText.written("${value.rear}"))
            is FigureValue.Shifts ->
                AppText.named("shifting_front_rear", AppText.written("${value.front}"), AppText.written("${value.rear}"))
            is FigureValue.PoolLength ->
                AppText.named(if (value.yards) "unit_pool_yd" else "unit_pool_m", AppText.written(formatWhole(value.value)))
            is FigureValue.StartTime -> StartText.of(value, now, zone)
            is FigureValue.Text -> AppText.written(value.text)
            is FigureValue.Sensor -> LabelText.sensor(value.kind, value.manufacturer, value.model)
            is FigureValue.GearItem ->
                AppText.named(
                    "page_gear_item_value",
                    AppText.written(value.name),
                    value.kind?.let { GearTexts.kind(value.type, it) } ?: GearTexts.type(value.type),
                )
            is FigureValue.Token -> DiveText.water(value.token) ?: AppText.written(value.token)
            is FigureValue.Gases -> {
                val names = joined(value.mixes.map { DiveText.gasName(it.oxygen, it.helium) }, ARROW_PAIR) ?: AppText.written("")
                if (value.closedCircuit) AppText.named("stat_gas_ccr", names) else names
            }
            is FigureValue.PercentRange ->
                AppText.named("stat_cns_value", AppText.written("${value.start}"), AppText.written("${value.end}"))
            is FigureValue.Stamina -> stamina(value)
            is FigureValue.Fluid -> fluid(value.milliliters, units)
            is FigureValue.PressureRange ->
                AppText.named("stat_pressure_range", DiveText.pressure(value.startBar, units), DiveText.pressure(value.endBar, units))
            is FigureValue.Bar -> AppText.named("unit_pressure_bar", AppText.written(formatDecimal(value.value, 2)))
            is FigureValue.GasRate -> DiveText.gasRate(value.litersPerMinute, units)
            is FigureValue.PressureRate -> DiveText.pressureRate(value.barPerMinute, units)
            is FigureValue.VerticalRate -> verticalRate(value.mmPerSecond, units)
            is FigureValue.Minutes -> DiveText.surfaceInterval(value.minutes)
            is FigureValue.DecoModel -> decoModel(value)
            is FigureValue.PpO2Limits ->
                joined(
                    listOfNotNull(
                        value.warn?.let { AppText.named("stat_ppo2_warn", DiveText.bar(it)) },
                        value.critical?.let { AppText.named("stat_ppo2_critical", DiveText.bar(it)) },
                        value.deco?.let { AppText.named("stat_ppo2_deco", DiveText.bar(it)) },
                    ),
                    DOT_PAIR,
                ) ?: AppText.written("")
            is FigureValue.Setpoints ->
                joined(
                    listOfNotNull(
                        value.low?.let { AppText.named("stat_setpoint_low", DiveText.bar(it)) },
                        value.high?.let { AppText.named("stat_setpoint_high", DiveText.bar(it)) },
                    ),
                    DOT_PAIR,
                ) ?: AppText.written("")
        }

    /** [texts] joined one after the other by the pair string [pair] ("A · B · C"); null for none. */
    fun joined(texts: List<AppText>, pair: String = DOT_PAIR): AppText? = texts.reduceOrNull { list, next ->
        AppText.named(pair, list, next)
    }

    /** "%1$s · %2$s". */
    const val DOT_PAIR = "text_dot_pair"

    /** "%1$s → %2$s". */
    const val ARROW_PAIR = "text_arrow_pair"

    private fun unitName(unit: FigureUnit): String =
        when (unit) {
            FigureUnit.Watts -> "unit_watts"
            FigureUnit.Bpm -> "unit_bpm"
            FigureUnit.Rpm -> "unit_rpm"
            FigureUnit.Spm -> "unit_spm"
            FigureUnit.StrokesPerMinute -> "unit_strokes_per_minute"
            FigureUnit.Kilocalories -> "unit_kcal"
            FigureUnit.Kilojoules -> "unit_kj"
            FigureUnit.Milliseconds -> "unit_ms"
        }

    /** A pace from a speed: the time a kilometer or mile (or 100 m or yd swimming) takes at it. */
    private fun pace(value: FigureValue.Pace, units: UnitSystem): AppText {
        if (value.mmPerSecond <= 0) return StatText.missing
        // The distance covered in an hour at that speed, so the pace is rounded once, per the athlete's unit, as a pace chart rounds it.
        val meters = value.mmPerSecond / MM_PER_M * SECONDS_PER_HOUR
        return if (value.swim) StatText.swimPace(meters, SECONDS_PER_HOUR, units) else StatText.pace(meters, SECONDS_PER_HOUR, units)
    }

    private fun verticalRate(mmPerSecond: Long, units: UnitSystem): AppText {
        val perMinute = UnitConversions.toElevationUnit(mmPerSecond / MM_PER_M * SECONDS_PER_MINUTE, units)
        return AppText.named(
            if (units == UnitSystem.Metric) "unit_rate_m_min" else "unit_rate_ft_min",
            AppText.written(formatDecimal(perMinute, 0)),
        )
    }

    /** The deco model, with ZHL-16C's gradient factors; the gradient factors alone for a dive that names no model. */
    private fun decoModel(value: FigureValue.DecoModel): AppText {
        val gradientFactors =
            value.gradientFactorLow?.let { low ->
                value.gradientFactorHigh?.let { high ->
                    AppText.named("dive_gradient_factors", AppText.written("$low"), AppText.written("$high"))
                }
            }
        val model = value.model?.let { DiveText.decoModel(it) }
        return when {
            model != null && gradientFactors != null -> AppText.named("stat_deco_model_gf", model, gradientFactors)
            model != null -> model
            else -> gradientFactors ?: AppText.written("")
        }
    }

    /** "100% → 70% · min 48%", or whichever of them was recorded. */
    private fun stamina(value: FigureValue.Stamina): AppText {
        val start = value.start
        val end = value.end
        val range =
            if (start != null && end != null) AppText.named("page_stamina_range", AppText.written("$start"), AppText.written("$end"))
            else (start ?: end)?.let { AppText.named("unit_percent", AppText.written("$it")) }
        return joined(listOfNotNull(range, value.min?.let { AppText.named("page_stamina_min", AppText.written("$it")) }))
            ?: AppText.written("")
    }

    /** Milliliters, whole, or fluid ounces to one decimal for imperial. */
    private fun fluid(milliliters: Int, units: UnitSystem): AppText {
        val value = UnitConversions.toFluidUnit(milliliters.toDouble(), units)
        return when (units) {
            UnitSystem.Metric -> AppText.named("unit_fluid_ml", AppText.written(formatWhole(value.roundToLong())))
            UnitSystem.Imperial -> AppText.named("unit_fluid_fl_oz", AppText.written(formatDecimal(value, FLUID_OUNCE_DECIMALS)))
        }
    }

    private const val SECONDS_PER_HOUR = 3600L
    private const val MM_PER_M = 1000.0
    private const val SECONDS_PER_MINUTE = 60
    private const val FLUID_OUNCE_DECIMALS = 1
}

/** Each figure's label, by name. */
private val LABELS: Map<FigureKey, String> =
    mapOf(
        FigureKey.Distance to "stat_distance",
        FigureKey.MovingTime to "stat_moving_time",
        FigureKey.ElapsedTime to "page_elapsed_time",
        FigureKey.Time to "stat_time",
        FigureKey.StartTime to "page_start_time",
        FigureKey.ElevationGain to "page_elevation_gain",
        FigureKey.Descent to "stat_descent",
        FigureKey.AvgSpeed to "page_avg_speed",
        FigureKey.MaxSpeed to "page_max_speed",
        FigureKey.AvgPace to "page_avg_pace",
        FigureKey.MaxPace to "page_max_pace",
        FigureKey.AvgHeartRate to "stat_heart_rate",
        FigureKey.MaxHeartRate to "page_max_heart_rate",
        FigureKey.AvgPower to "stat_avg_power",
        FigureKey.MaxPower to "stat_max_power",
        FigureKey.NormalizedPower to "page_normalized_power",
        FigureKey.Work to "stat_work",
        FigureKey.Calories to "stat_calories",
        FigureKey.AvgCadence to "stat_avg_cadence",
        FigureKey.MaxCadence to "stat_max_cadence",
        FigureKey.AvgTemperature to "page_avg_temperature",
        FigureKey.MinTemperature to "page_min_temperature",
        FigureKey.MaxTemperature to "page_max_temperature",
        FigureKey.PoolLength to "page_pool_length",
        FigureKey.AvgStrokeDistance to "page_avg_stroke_distance",
        FigureKey.Steps to "stat_steps",
        FigureKey.Strokes to "stat_strokes",
        FigureKey.PedalStrokes to "stat_pedal_strokes",
        FigureKey.Pushes to "stat_pushes",
        FigureKey.Shifts to "stat_shifts",
        FigureKey.Jumps to "stat_jumps",
        FigureKey.Grit to "stat_grit",
        FigureKey.Flow to "stat_flow",
        FigureKey.Runs to "stat_runs",
        FigureKey.Laps to "detail_section_laps",
        FigureKey.Legs to "detail_section_legs",
        FigureKey.Lengths to "detail_section_lengths",
        FigureKey.Sets to "detail_section_sets",
        FigureKey.Reps to "page_reps",
        FigureKey.MaxDepth to "stat_max_depth",
        FigureKey.AvgDepth to "stat_avg_depth",
        FigureKey.DiveTime to "stat_dive_time",
        FigureKey.WaterTemp to "stat_water_temp",
        FigureKey.BottomTime to "stat_bottom_time",
        FigureKey.Gas to "stat_gas",
        FigureKey.SurfaceInterval to "stat_surface_interval",
        FigureKey.DiveNumber to "page_dive_number",
        FigureKey.Cns to "stat_cns",
        FigureKey.Otu to "page_otu",
        FigureKey.Rmv to "stat_rmv",
        FigureKey.Sac to "stat_sac",
        FigureKey.AirUsed to "stat_air_used",
        FigureKey.EndN2 to "page_end_n2",
        FigureKey.HighestPpO2 to "page_highest_ppo2",
        FigureKey.MaxAscentRate to "page_max_ascent_rate",
        FigureKey.DescentTime to "stat_descent_time",
        FigureKey.WaterType to "page_water_type",
        FigureKey.Visibility to "stat_visibility",
        FigureKey.AirTemperature to "page_air_temperature",
        FigureKey.Altitude to "stat_altitude",
        FigureKey.NoFlyTime to "page_no_fly_time",
        FigureKey.DesaturationTime to "page_desaturation_time",
        FigureKey.PpO2Limits to "stat_ppo2_limits",
        FigureKey.Setpoints to "stat_setpoints",
        FigureKey.SafetyStop to "stat_safety_stop",
        FigureKey.Ftp to "page_ftp",
        FigureKey.IntensityFactor to "page_intensity_factor",
        FigureKey.TrainingStressScore to "page_tss",
        FigureKey.AerobicEffect to "page_aerobic_effect",
        FigureKey.AnaerobicEffect to "page_anaerobic_effect",
        FigureKey.ExerciseLoad to "page_exercise_load",
        FigureKey.Stamina to "page_stamina",
        FigureKey.SweatLoss to "page_sweat_loss",
        FigureKey.Hrv to "page_hrv",
        FigureKey.Avg to "page_average",
        FigureKey.Min to "page_minimum",
        FigureKey.Max to "page_maximum",
        FigureKey.Best to "page_best",
        FigureKey.Highest to "page_highest",
        FigureKey.Lowest to "page_lowest",
        FigureKey.MaxGrade to "detail_max_grade",
        FigureKey.MostUsedGear to "page_most_used_gear",
        FigureKey.FrontShifts to "page_front_shifts",
        FigureKey.RearShifts to "page_rear_shifts",
        FigureKey.BatteryUsed to "chart_ebike_battery",
        FigureKey.Device to "detail_device",
        FigureKey.Sensor to "page_sensor",
        FigureKey.GearItem to "page_gear_item",
    )
