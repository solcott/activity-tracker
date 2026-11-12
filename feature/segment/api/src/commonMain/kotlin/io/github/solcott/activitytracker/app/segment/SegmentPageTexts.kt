package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.data.units.formatDecimal
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.MayorEntry
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.persistentListOf

/**
 * What a segment's page says, for both UIs (segments/segment-efforts, segments/segment-elevation, segments/segment-mayors,
 * dev/shared-display-text): the star, its length and type, its elevation, its data's credit, the Mayor card and the efforts' heading.
 */
@Suppress("TooManyFunctions") // One per line of the page, so both UIs ask the same.
object SegmentPageTexts {
    /** The star's label: Star segment, or Unstar segment when [starred]. */
    fun star(starred: Boolean): AppText = AppText.named(if (starred) "segment_unstar" else "segment_star")

    /** Its length in [units] and what it is ("1.60 km · Ride", "0.42 mi · Ski lift"). */
    fun summary(segment: SegmentView, units: UnitSystem): AppText =
        AppText.named(
            "segment_length_type",
            StatText.distance(segment.distanceMeters.toDouble(), units),
            SegmentTexts.type(segment.record.sport, segment.record.kind),
        )

    /**
     * Its gain, loss and average grade (net change over its length) in [units], when the AppView has derived them
     * (segments/segment-elevation); null otherwise.
     */
    fun elevation(segment: SegmentView, units: UnitSystem): AppText? {
        val gain = segment.elevationGain ?: return null
        val loss = segment.elevationLoss ?: 0
        val grade = if (segment.distanceMeters > 0) (gain - loss) * PERCENT / segment.distanceMeters else 0.0
        return AppText.named(
            "segment_elevation",
            StatText.elevation(gain.toDouble(), units),
            StatText.elevation(loss.toDouble(), units),
            percent(grade, 1),
        )
    }

    /** The [profile]'s high and low points in [units], and its maximum grade as a whole percent when the AppView measured one. */
    fun extremes(profile: SegmentProfile, units: UnitSystem): AppText {
        val high = StatText.elevation(profile.highM, units)
        val low = StatText.elevation(profile.lowM, units)
        val maxGrade = profile.maxGradePercent
        return if (maxGrade != null) {
            AppText.named("segment_elevation_extremes", high, low, percent(maxGrade, 0))
        } else {
            AppText.named("segment_elevation_range", high, low)
        }
    }

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

    /** The Leaderboard button. */
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

    private fun percent(value: Double, decimals: Int): AppText =
        AppText.named("unit_percent", AppText.written(formatDecimal(value, decimals)))

    private const val PERCENT = 100.0
}
