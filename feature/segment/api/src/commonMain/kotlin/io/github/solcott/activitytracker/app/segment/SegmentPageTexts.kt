package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.LeaderboardEntry
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.persistentListOf

/**
 * What a segment's page says, for both UIs (segments/segment-page, segments/segment-efforts, segments/segment-elevation,
 * segments/segment-mayors, segments/segment-leaderboards, dev/shared-display-text): the star, the heading, the figure row, the profile's
 * heading, the board cards, its status and the efforts' heading.
 */
@Suppress("TooManyFunctions") // One per line of the page, so both UIs ask the same.
object SegmentPageTexts {
    /** The star's label: Star segment, or Unstar segment when [starred]. */
    fun star(starred: Boolean): AppText = AppText.named(if (starred) "segment_unstar" else "segment_star")

    /** The [profile]'s high and low points in [units], over its card (segments/segment-elevation). */
    fun extremes(profile: SegmentProfile, units: UnitSystem): AppText =
        AppText.named("segment_elevation_range", StatText.elevation(profile.highM, units), StatText.elevation(profile.lowM, units))

    /** The heading's first line (segments/segment-page): what it is and its status ("Ride · Active", "Ski lift · Proposed"). */
    fun heading(segment: SegmentView): AppText =
        AppText.named(
            "segment_length_type",
            SegmentTexts.type(segment.record.sport, segment.record.kind),
            PlaceTexts.status(segment.status) ?: AppText.named("segment_status_active"),
        )

    /** The word before the trails a segment lies on ("on Hogback Ridge Trail"). */
    val onTrails: AppText = AppText.named("segment_on_trails")

    /**
     * The figure row (segments/segment-page, segments/segment-elevation): its length, its gain with its loss and its average grade when the
     * AppView derived them, and the [profile]'s maximum grade when it measured one, in [units]. A figure it lacks is left out.
     */
    fun figures(segment: SegmentView, profile: SegmentProfile?, units: UnitSystem): List<SegmentFigure> {
        val distance = SegmentFigure(StatText.distance(segment.distanceMeters.toDouble(), units), named("segment_figure_distance"))
        val gain = segment.elevationGain
        val elevation =
            gain
                ?.let {
                    val loss = segment.elevationLoss ?: 0
                    val grade = if (segment.distanceMeters > 0) (gain - loss) * PERCENT / segment.distanceMeters else 0.0
                    listOf(
                        SegmentFigure(
                            StatText.elevation(gain.toDouble(), units),
                            named("segment_figure_gain"),
                            detail = AppText.named("segment_figure_loss", StatText.elevation(loss.toDouble(), units)),
                        ),
                        SegmentFigure(percent(grade, 1), named("segment_figure_avg_grade")),
                    )
                }
                .orEmpty()
        val maxGrade = profile?.maxGradePercent?.let { listOf(SegmentFigure(percent(it, 0), named("segment_figure_max_grade"))) }.orEmpty()
        return listOf(distance) + elevation + maxGrade
    }

    /** The Leaderboard card's time for [entry] ("4:12"). */
    fun leaderboardValue(entry: LeaderboardEntry): AppText = SegmentBoardTexts.time(entry)

    /** The viewer's own time and place on the Leaderboard card ("You: 5:03 · #18"). */
    fun leaderboardYours(entry: LeaderboardEntry): AppText =
        AppText.named("segment_leaderboard_yours", SegmentBoardTexts.time(entry), AppText.written(formatWhole(entry.rank)))

    /** The Leaderboard card when no one the viewer may see is on the board. */
    val leaderboardNone: AppText = AppText.named("leaderboard_empty")

    /** What a segment that isn't active means, with [PlaceTexts.statusHelp]; null for an active one. */
    fun statusHelp(segment: SegmentView): AppText? =
        PlaceTexts.statusHelp(
            segment.status,
            proposedHelp = "segment_status_proposed_help",
            duplicateHelp = "segment_duplicate_help",
            archivedHelp = "segment_status_archived_help",
        )

    /** Whether Open the original shows: a duplicate whose original is known. */
    fun offersOriginal(segment: SegmentView): Boolean = PlaceTexts.offersOriginal(segment.status, hasOriginal = segment.duplicateOf != null)

    /** The Leaderboard card's title. */
    val leaderboard: AppText = AppText.named("segment_leaderboard")

    /** The Mayor card's title. */
    val mayorTitle: AppText = AppText.named("segment_mayor_title")

    /** The Mayor card when the segment has no Mayor yet. */
    val mayorNone: AppText = AppText.named("segment_mayor_none")

    /** The Mayor's count on the card ("14 efforts"). */
    fun mayorValue(entry: MayorEntry): AppText = SegmentBoardTexts.efforts(entry)

    /** The viewer's own count on the card ("You: 3 efforts"). */
    fun mayorYours(entry: MayorEntry): AppText =
        AppText.Plural("segment_mayor_yours", entry.efforts, persistentListOf(AppText.written(formatWhole(entry.efforts))))

    /** The efforts' heading: Your rides on a [lift], else Your efforts. */
    fun efforts(lift: Boolean): AppText = AppText.named(if (lift) "segment_your_rides" else "segment_your_efforts")

    /** Under the heading when the viewer has none. */
    val noEfforts: AppText = AppText.named("segment_no_efforts")

    private fun named(name: String): AppText = AppText.named(name)

    private fun percent(value: Double, decimals: Int): AppText =
        AppText.named("unit_percent", AppText.written(formatDecimal(value, decimals)))

    private const val PERCENT = 100.0
}

/** One figure of a segment page's figure row (segments/segment-page): its [value] over its [label], and a smaller [detail] under them. */
@Immutable data class SegmentFigure(val value: AppText, val label: AppText, val detail: AppText? = null)
