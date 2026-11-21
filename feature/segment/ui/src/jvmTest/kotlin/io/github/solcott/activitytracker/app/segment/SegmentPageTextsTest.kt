package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.fixtures.fakeLeaderboardEntry
import io.github.solcott.activitytracker.app.fixtures.fakeSegment
import io.github.solcott.activitytracker.app.fixtures.fakeSegmentRecord
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allPluralStringResources
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * What the Segment page's heading, figure row and Leaderboard card say (segments/segment-page, segments/segment-elevation,
 * segments/segment-leaderboards): a figure the segment lacks is left out, and every name has its string.
 */
class SegmentPageTextsTest {
    private val climb = fakeSegment(distanceMeters = 1_600).copy(elevationGain = 84, elevationLoss = 12)
    private val profile = SegmentProfile(persistentListOf(1_420.0, 1_504.0), 1_600, persistentListOf(4.0), maxGradePercent = 12.0)

    @Test
    fun allFourFigures() {
        val figures = SegmentPageTexts.figures(climb, profile, UnitSystem.Metric)
        assertEquals(
            listOf("segment_figure_distance", "segment_figure_gain", "segment_figure_avg_grade", "segment_figure_max_grade"),
            figures.map { (it.label as AppText.Named).name },
        )
        assertEquals(StatText.distance(1_600.0, UnitSystem.Metric), figures[0].value)
        assertEquals(StatText.elevation(84.0, UnitSystem.Metric), figures[1].value)
        assertEquals(AppText.named("segment_figure_loss", StatText.elevation(12.0, UnitSystem.Metric)), figures[1].detail)
        assertEquals(AppText.named("unit_percent", AppText.written("4.5")), figures[2].value)
        assertEquals(AppText.named("unit_percent", AppText.written("12")), figures[3].value)
    }

    @Test
    fun imperialFigures() {
        val figures = SegmentPageTexts.figures(climb, profile = null, UnitSystem.Imperial)
        assertEquals(StatText.elevation(84.0, UnitSystem.Imperial), figures[1].value)
        assertEquals(AppText.named("segment_figure_loss", StatText.elevation(12.0, UnitSystem.Imperial)), figures[1].detail)
        assertEquals(3, figures.size, "no maximum grade without a profile")
    }

    @Test
    fun noElevationLeavesTheLengthAlone() {
        val figures = SegmentPageTexts.figures(fakeSegment(), profile = null, UnitSystem.Metric)
        assertEquals(listOf("segment_figure_distance"), figures.map { (it.label as AppText.Named).name })
        assertNull(figures.single().detail)
    }

    @Test
    fun theHeadingSaysWhatItIsAndItsStatus() {
        assertEquals(
            AppText.named("segment_length_type", SegmentTexts.type(SegmentSportGroup.RIDE, null), AppText.named("segment_status_active")),
            SegmentPageTexts.heading(fakeSegment()),
        )
        assertEquals(
            AppText.named("segment_length_type", SegmentTexts.type(SegmentSportGroup.RIDE, null), AppText.named("segment_status_proposed")),
            SegmentPageTexts.heading(fakeSegment(status = SegmentStatus.PROPOSED)),
        )
        val lift = fakeSegment(record = fakeSegmentRecord(sport = SegmentSportGroup.ALPINE, kind = SegmentKind.LIFT))
        assertEquals(AppText.named("segment_kind_ski_lift"), (SegmentPageTexts.heading(lift) as AppText.Named).args.first())
    }

    @Test
    fun theLeaderboardCardsWords() {
        val entry = fakeLeaderboardEntry(rank = 18, elapsed = 303)
        assertEquals(AppText.written(StatText.duration(303)), SegmentPageTexts.leaderboardValue(entry))
        assertEquals(
            AppText.named("segment_leaderboard_yours", AppText.written(StatText.duration(303)), AppText.written("18")),
            SegmentPageTexts.leaderboardYours(entry),
        )
    }

    @Test
    fun everyNameHasItsString() {
        val texts =
            SegmentPageTexts.figures(climb, profile, UnitSystem.Metric).flatMap { listOfNotNull(it.value, it.label, it.detail) } +
                SegmentPageTexts.heading(fakeSegment()) +
                SegmentPageTexts.onTrails +
                SegmentPageTexts.leaderboardNone +
                SegmentPageTexts.leaderboardYours(fakeLeaderboardEntry()) +
                SegmentPageTexts.extremes(profile, UnitSystem.Metric)
        val missing = texts.flatMap(::names).distinct().filterNot { it in Res.allStringResources || it in Res.allPluralStringResources }
        assertTrue(missing.isEmpty(), "No string for $missing")
    }

    private fun names(text: AppText): List<String> =
        when (text) {
            is AppText.Named -> listOf(text.name) + text.args.flatMap(::names)
            is AppText.Plural -> listOf(text.name) + text.args.flatMap(::names)
            else -> emptyList()
        }
}
