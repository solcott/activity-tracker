package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup

/** What a segment is, as text both UIs show (dev/shared-display-text): its sport group, its type in a list of segments and
 * the image beside a ski segment's type. */
object SegmentTexts {
    /** A [SegmentSportGroup]'s name ("Ride", "Paddle"); an unknown group as its token. */
    fun sportGroup(sport: String): AppText = AppText.Named("segment_sport_$sport", fallback = AppText.written(sport))

    /**
     * What a segment of [sport] and [kind] is: a ski lift or a ski run for `alpine` ones (segments/segment-lifts), else its sport group.
     */
    fun type(sport: String, kind: String?): AppText =
        when {
            SegmentKind.of(kind) == SegmentKind.LIFT -> AppText.named("segment_kind_ski_lift")
            sport == SegmentSportGroup.ALPINE -> AppText.named("segment_kind_ski_run")
            else -> sportGroup(sport)
        }

    /** The image that tells a ski lift from a ski run, by its drawable's name; null for every other segment, which needs none. */
    fun kindIcon(sport: String, kind: String?): String? =
        when {
            SegmentKind.of(kind) == SegmentKind.LIFT -> "ski_lift"
            sport == SegmentSportGroup.ALPINE -> "downhill_skiing"
            else -> null
        }
}
