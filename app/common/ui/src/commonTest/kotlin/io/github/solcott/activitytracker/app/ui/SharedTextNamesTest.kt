package io.github.solcott.activitytracker.app.ui

import io.github.solcott.activitytracker.app.FailureReason
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.boards.RankBoardTexts
import io.github.solcott.activitytracker.app.gear.GearTexts
import io.github.solcott.activitytracker.app.icon.SportIconNames
import io.github.solcott.activitytracker.app.segment.SegmentTexts
import io.github.solcott.activitytracker.app.sport.SportTypeGroups
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.DateText
import io.github.solcott.activitytracker.app.text.DayText
import io.github.solcott.activitytracker.app.text.DurationText
import io.github.solcott.activitytracker.app.text.ErrorTexts
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.app.visibility.VisibilityFields
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allDrawableResources
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.profile.DetailGroup
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.GearMainTotal
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GearType
import io.github.solcott.activitytracker.lexicon.app.Gender
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
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
                DetailCategory.all.map(LabelText::detailCategory) +
                DetailCategory.all.mapNotNull(VisibilityTexts::categoryHelp) +
                VisibilityTexts.categoryName(VisibilityFields(runningForm = false), DetailCategory.CADENCE) +
                listOfNotNull(VisibilityTexts.followersNote(Visibility.FOLLOWERS, approvesFollowers = false)) +
                listOf(false, true).map { VisibilityTexts.lockedNote(VisibilityFields(canChoose = false, spacesUnsupported = it)) } +
                // The string an unknown sensor falls back to.
                (LabelText.sensorType("unknown") as AppText.Named).fallback!!
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
    fun everySportImageExists() {
        val missing = SportIconNames.all.filterNot { it in Res.allDrawableResources }
        assertTrue(missing.isEmpty(), "No drawable for $missing")
    }

    private fun assertAllExist(texts: List<AppText>) {
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
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
