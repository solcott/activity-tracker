package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.FailureReason
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.app.detail.AvgMaxMetric
import io.github.solcott.activitytracker.app.detail.DetailSection
import io.github.solcott.activitytracker.app.detail.Figure
import io.github.solcott.activitytracker.app.detail.FigureKey
import io.github.solcott.activitytracker.app.detail.FigureTexts
import io.github.solcott.activitytracker.app.detail.FigureUnit
import io.github.solcott.activitytracker.app.detail.FigureValue
import io.github.solcott.activitytracker.app.detail.GasMix
import io.github.solcott.activitytracker.app.detail.LapRow
import io.github.solcott.activitytracker.app.detail.LapTableTexts
import io.github.solcott.activitytracker.app.detail.PaceKind
import io.github.solcott.activitytracker.app.detail.StatsGroupKind
import io.github.solcott.activitytracker.app.detail.StreamKind
import io.github.solcott.activitytracker.app.detail.StreamUnits
import io.github.solcott.activitytracker.app.detail.ZoneRows
import io.github.solcott.activitytracker.app.detail.ZoneTime
import io.github.solcott.activitytracker.app.edit.ActivityDetailsFields
import io.github.solcott.activitytracker.app.edit.DetailsFormTexts
import io.github.solcott.activitytracker.app.gear.GearTexts
import io.github.solcott.activitytracker.app.icon.SportIconNames
import io.github.solcott.activitytracker.app.moderation.ModerationTexts
import io.github.solcott.activitytracker.app.record.GearCollectionOption
import io.github.solcott.activitytracker.app.record.GearField
import io.github.solcott.activitytracker.app.record.GearFieldTexts
import io.github.solcott.activitytracker.app.record.GearOption
import io.github.solcott.activitytracker.app.segment.EffortTexts
import io.github.solcott.activitytracker.app.segment.SegmentTexts
import io.github.solcott.activitytracker.app.sport.SportTypeGroups
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.CardStatsText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.text.DayText
import io.github.solcott.activitytracker.app.text.DiveText
import io.github.solcott.activitytracker.app.text.DurationText
import io.github.solcott.activitytracker.app.text.ErrorTexts
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StartText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.app.text.ZoneNameText
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allDrawableResources
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.moderation.BlurReason
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.moderation.WarnReason
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.training.ZoneName
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.DecoModel
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Gender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.NotRanked
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.app.WaterType
import io.github.solcott.activitytracker.textfield.textFieldStateOf
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Every string and image name shared code can choose exists (dev/shared-display-text). The choices are names, not typed resources, so the
 * compiler no longer says when one is missing; this does, for every kind of error and reason, every unit formatter in both unit systems,
 * every closed label and every sport's image. Labels from open sets are left out: a missing one is their fallback's case.
 */
class SharedTextNamesTest {
    @Test
    fun everyKindOfErrorHasItsString() {
        val missing = UiError.Kind.entries.map(ErrorTexts::kindName).filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun everyReasonHasItsString() {
        val reasons = FailureReason.entries.map(ErrorTexts::reasonName) + "error_with_reason"
        val missing = reasons.filterNot { it in Res.allStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    @Test
    fun everyFormatterNamesAString() {
        val texts =
            UnitSystem.entries.flatMap { units ->
                listOf(
                    StatText.missing,
                    StatText.distance(1_000.0, units),
                    StatText.pace(1_000.0, 300, units),
                    StatText.speed(5_000, units),
                    StatText.swimPace(1_000.0, 1_200, units),
                    StatText.elevation(100.0, units),
                    StatText.depth(18.0, units),
                    StatText.temperature(200, units),
                    StatText.weight(70_000, units),
                ) +
                    listOf(Sport.RUN, Sport.RIDE, Sport.SWIM).flatMap { sport ->
                        StatText.rate(sport, 1_000.0, 300, units).let { listOfNotNull(it?.label, it?.value) }
                    }
            }
        assertAllExist(texts)
    }

    @Test
    fun everyClosedLabelNamesAString() {
        val texts =
            DetailGroup.entries.flatMap { listOf(LabelText.detailGroup(it), LabelText.visibilityScope(it)) } +
                listOf(Gender.FEMALE, Gender.MALE, Gender.NON_BINARY).map(LabelText::gender) +
                Visibility.all.map(LabelText::visibility) +
                LabelText.handle("alice.test") +
                listOf(false, true).flatMap { listOf(LabelText.expanded(it), LabelText.save(it)) } +
                VisibilityTexts.activityNote(VisibilityFields(canChoose = true)) +
                DetailCategory.all.map(LabelText::detailCategory) +
                DetailCategory.all.mapNotNull(VisibilityTexts::categoryHelp) +
                VisibilityTexts.categoryName(VisibilityFields(runningForm = false), DetailCategory.CADENCE) +
                listOfNotNull(VisibilityTexts.followersNote(Visibility.FOLLOWERS, approvesFollowers = false)) +
                listOf(false, true).map { VisibilityTexts.lockedNote(VisibilityFields(canChoose = false, spacesUnsupported = it)) } +
                // The string an unknown sensor falls back to.
                (LabelText.sensorType("unknown") as AppText.Named).fallback!!
        assertAllExist(texts)
    }

    /** A value of every kind whose text names a string. */
    private val figureValues =
        listOf(
            FigureValue.Pace(3_000, swim = false),
            FigureValue.Pace(1_000, swim = true),
            FigureValue.Quantity(200, FigureUnit.Watts),
            FigureValue.Quantity(140, FigureUnit.Bpm),
            FigureValue.Quantity(90, FigureUnit.Rpm),
            FigureValue.Quantity(600, FigureUnit.Kilocalories),
            FigureValue.Quantity(900, FigureUnit.Kilojoules),
            FigureValue.Quantity(50, FigureUnit.Milliseconds),
            FigureValue.LeftRight(StreamKind.TorqueEffectiveness, 70.0, null),
            FigureValue.BatteryUsed(90.0, 40.0),
            FigureValue.Gear(3),
            FigureValue.Shifts(2, 30),
            FigureValue.PoolLength(25, yards = true),
            FigureValue.PoolLength(50, yards = false),
            FigureValue.GearItem("Bike", GearType.all.first(), null),
            FigureValue.Token(WaterType.SALT),
            FigureValue.Gases(persistentListOf(GasMix(21, 0), GasMix(32, 0), GasMix(18, 45), GasMix(100, 0)), closedCircuit = true),
            FigureValue.PercentRange(5, 12),
            FigureValue.Stamina(100, 70, 48),
            FigureValue.Stamina(100, null, null),
            FigureValue.Fluid(700),
            FigureValue.PressureRange(200.0, 50.0),
            FigureValue.Bar(1.4),
            FigureValue.GasRate(18.0),
            FigureValue.PressureRate(1.2),
            FigureValue.VerticalRate(150),
            FigureValue.Minutes(90),
            FigureValue.Minutes(3_000),
            FigureValue.DecoModel(DecoModel.ZHL16C, 30, 85),
            FigureValue.DecoModel(null, 30, 85),
            FigureValue.PpO2Limits(1400, 1600, 1600),
            FigureValue.Setpoints(700, 1300),
        )

    @Test
    fun everyFigureStreamAndCardNamesAString() {
        val now = Instant.parse("2026-06-01T18:00:00Z")
        val texts =
            UnitSystem.entries.flatMap { units ->
                figureValues.map { FigureTexts.value(it, units, now, TimeZone.UTC) } +
                    StreamKind.entries.map { StreamUnits.valueText(it, 1.0, units) } +
                    PaceKind.entries.map { StreamUnits.paceText(it, 300.0, units) }
            } +
                FigureKey.entries
                    .filterNot { it in setOf(FigureKey.StreamAverage, FigureKey.LeftRight) }
                    .map { FigureTexts.label(Figure(it, FigureValue.Missing)) } +
                StreamKind.entries.map(StreamUnits::title) +
                StatsGroupKind.entries.map(FigureTexts::group) +
                AvgMaxMetric.entries.map(FigureTexts::avgMax) +
                DetailSection.entries.map(FigureTexts::section) +
                listOf(FigureTexts.avgMaxHeading, FigureTexts.avgColumn, FigureTexts.minColumn, FigureTexts.maxColumn) +
                listOf(FigureTexts.allStats, FigureTexts.trainingLoad, AppText.named("chart_pace")) +
                listOf(DecoModel.VPM_B, DecoModel.VPM_B_GFS, DecoModel.DCIEM).map(DiveText::decoModel) +
                listOf(WaterType.FRESH, WaterType.EN13319, WaterType.CUSTOM).mapNotNull(DiveText::water) +
                listOf(DetailSection.HeartRate, DetailSection.Power).flatMap { section ->
                    ZoneRows.of(
                            listOf(
                                ZoneTime("1", ZoneName.entries.first(), 100, 120, 60),
                                ZoneTime("2", ZoneName.entries.first(), 120, null, 60),
                            ),
                            section,
                        )
                        .flatMap { listOf(it.label, it.range, it.title, it.percent, it.spoken) } + ZoneRows.heading(section)
                } +
                listOf(ZoneRows.missing, ZoneRows.setThresholds, ZoneRows.percent(0.001)) +
                UnitSystem.entries.flatMap { units ->
                    listOf(false, true).flatMap { ski ->
                        val laps =
                            listOf(
                                LapRow(1, 60, null, 1.0, 10.0, 140, 200, 80, 10.0, intensity = "warmup", descent = 5.0),
                                LapRow(2, 60, null, 1.0, 10.0, 140, null, 80, 10.0, intensity = "rest"),
                            )
                        LapTableTexts.of(laps, ski, units).let { table -> table.headers + table.rows.flatMap { it.cells + it.spoken } }
                    }
                }
        assertAllExist(texts)
    }

    @Test
    fun everyGearTypeAndTotalNamesAString() {
        val totals =
            GearTotals(activities = 1, movingTime = 3_600, distance = 1_000, elevationGain = 0, elevationLoss = 100, days = 1, dives = 1)
        val texts =
            GearType.all.map(GearTexts::type) +
                GearMainTotal.entries.flatMap { main -> UnitSystem.entries.map { GearTexts.mainTotal(main, totals, it) } } +
                GearTexts.mainTotal(GearMainTotal.DISTANCE, totals.copy(activities = 0, movingTime = 0, distance = 0), UnitSystem.Metric)
        assertAllExist(texts)
    }

    @Test
    fun everyGearFieldTextNamesAString() {
        val option =
            GearOption(AtUri("at://did:plc:a/app.activitytracker.gear.item/a"), "Canyon", GearType.BIKE, visibility = Visibility.SELF)
        val field = GearField(persistentListOf(option), notAdded = 2)
        val texts =
            listOfNotNull(
                GearFieldTexts.summary(field),
                GearFieldTexts.line(option),
                GearFieldTexts.line(option.copy(kind = "road", retired = true)),
                GearFieldTexts.collection(GearCollectionOption("race", "Race day", 3)),
                GearFieldTexts.notAdded(field),
                DetailsFormTexts.photosCount(ActivityDetailsFields(textFieldStateOf(), null, persistentListOf(), 4, {})),
            )
        assertAllExist(texts)
    }

    @Test
    fun everyTypePickerHeadingAndAJoinedListNameAString() {
        val texts =
            (SportTypeGroups.allTypes + SportTypeGroups.gearSports).map { it.label } +
                listOfNotNull(AppText.joined(listOf(AppText.written("Ride"), AppText.written("Run"))))
        assertAllExist(texts)
    }

    @Test
    fun everyLengthOfTimeAndDayNamesAString() {
        val today = LocalDate(2026, 6, 1)
        val texts =
            listOf(10.minutes, 3.hours, 2.days).map(DurationText::approximate) +
                (1..12).map { month -> DayText.day(LocalDate(2025, month, 1), today) } +
                DayText.day(today, today) +
                DayText.day(LocalDate(2026, 5, 31), today) +
                DayText.dayAndTime(Instant.parse("2026-06-01T07:00:00Z"), Instant.parse("2026-06-01T12:00:00Z"), TimeZone.UTC)
        assertAllExist(texts)
    }

    @Test
    fun everyDateSegmentTypeAndBoardWordNamesAString() {
        val texts =
            (1..12).map { DateText.date(2026, it, 1) } +
                listOf(SegmentKind.RUN, SegmentKind.LIFT).map { SegmentTexts.type(SegmentSportGroup.ALPINE, it) } +
                listOf(
                        SegmentSportGroup.RIDE,
                        SegmentSportGroup.RUN,
                        SegmentSportGroup.HIKE,
                        SegmentSportGroup.SKI,
                        SegmentSportGroup.PADDLE,
                    )
                    .map(SegmentTexts::sportGroup) +
                RankBoardTexts.scope(LeaderboardScope.EVERYONE).options.map { it.label } +
                RankBoardTexts.reached(DateText.isoDate("2026-10-26"))
        assertAllExist(texts)
    }

    @Test
    fun everyZoneNameNamesAString() {
        assertAllExist(ZoneName.entries.map(ZoneNameText::of))
    }

    @Test
    fun everyLabelBadgeLockAndCardFigureNamesAString() {
        val labels =
            BlurReason.entries.map(ModerationTexts::blur) +
                WarnReason.entries.map(ModerationTexts::warn) +
                BlurReason.entries.flatMap { ModerationTexts.notices(ModerationDecision.Blur(it), isOwn = true) }.map { it.text } +
                ModerationTexts.notices(ModerationDecision.Takedown, isOwn = true).map { it.text }
        val tiers = Visibility.all.flatMap { listOfNotNull(VisibilityTexts.badge(it), VisibilityTexts.badgeDescription(it)) }
        val locks =
            DetailCategory.all.flatMap { category ->
                listOfNotNull(VisibilityTexts.lock(category, Visibility.SELF), VisibilityTexts.lockDescription(category, Visibility.SELF))
            }
        val sports = listOf(Sport.RIDE, Sport.RUN, Sport.SWIM, Sport.STRENGTH)
        val figures =
            UnitSystem.entries
                .flatMap { units ->
                    sports.flatMap { sport -> listOfNotNull(CardStatsText.distance(sport, 1_000.0, units)) } +
                        listOfNotNull(CardStatsText.maxDepth(null, 18.2, units))
                }
                .flatMap { listOf(it.label, it.value) }
        val start =
            StartText.of(
                FigureValue.StartTime(1_780_000_000, -360, dateOnly = false),
                Instant.fromEpochSeconds(1_780_000_000),
                TimeZone.UTC,
            )
        assertAllExist(labels + tiers + locks + figures + start)
    }

    @Test
    fun everyEffortRowWordNamesAString() {
        val effort = { rank: Int?, notRanked: String? ->
            EffortView(
                segment =
                    SegmentViewBasic(
                        uri = AtUri("at://did:plc:a/app.activitytracker.feed.segment/1"),
                        name = "Hill",
                        sport = "ride",
                        distanceMeters = 1,
                    ),
                activity = AtUri("at://did:plc:a/app.activitytracker.feed.activity/1"),
                startOffset = 0,
                elapsed = 60,
                startedOn = "2026-05-01",
                eBike = true,
                best = true,
                rank = rank,
                notRanked = notRanked,
            )
        }
        val efforts =
            (1..10).map { effort(it, null) } + listOf(NotRanked.HIDDEN, NotRanked.EXCLUDED, NotRanked.IMPLAUSIBLE).map { effort(null, it) }
        assertAllExist(
            efforts.flatMap {
                listOfNotNull(
                    EffortTexts.date(it),
                    EffortTexts.rank(it),
                    EffortTexts.pr(it),
                    EffortTexts.notRanked(it),
                    EffortTexts.eBike(it),
                )
            },
        )
    }

    @Test
    fun everySportImageExists() {
        val kinds =
            listOfNotNull(
                SegmentTexts.kindIcon(SegmentSportGroup.ALPINE, SegmentKind.LIFT),
                SegmentTexts.kindIcon(SegmentSportGroup.ALPINE, null),
            )
        val missing = (SportIconNames.all + kinds).filterNot { it in Res.allDrawableResources }
        assertTrue(missing.isEmpty(), "No drawable for $missing")
    }

    private fun assertAllExist(texts: List<AppText>) {
        val missing =
            texts.flatMap(::names).distinct().filterNot {
                it in Res.allStringResources || it in Res.allPluralStringResources || isStringArrayItem(it)
            }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    /** The names [text] and its arguments choose. */
    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Written,
            is AppText.Time -> emptyList()
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
        }
}
