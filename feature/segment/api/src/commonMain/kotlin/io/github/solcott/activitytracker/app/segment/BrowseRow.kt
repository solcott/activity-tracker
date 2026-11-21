package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.PathView
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * One row of Trails & roads (segments/segment-browse): a trail or road with segments on it, or a segment group that lies on none. The map
 * draws, selects and hovers it by [key].
 */
@Immutable
sealed interface BrowseRow {
    /** What the row is selected, hovered and opened by: the path's URI, or the group's shown segment's. */
    val key: AtUri

    /** The level the row's line is drawn in and its swatch shows (segments/trail-ratings); null unrated. */
    val rating: TrailRating?

    /**
     * A trail or road (segments/segment-paths), with the segment groups on it that this list knows of ([groups]: the area's, after the
     * sport filter; none for a name result), and the sport group the list is narrowed to ([sport]), if any.
     */
    @Immutable
    data class Path(val path: PathView, val groups: ImmutableList<SegmentGroupRow> = persistentListOf(), val sport: String? = null) :
        BrowseRow {
        override val key: AtUri
            get() = path.uri

        /** The path's level for the sport group the list is narrowed to, or its first known scale's with none. */
        override val rating: TrailRating?
            get() = TrailRating.of(path.trail, sport)

        /** Whether it's a road rather than a trail. */
        val road: Boolean
            get() = path.kind == PathKind.ROAD
    }

    /** A segment group on no path (segments/segment-groups), drawn and opened as its shown segment. */
    @Immutable
    data class Group(val group: SegmentGroupRow) : BrowseRow {
        override val key: AtUri
            get() = group.shown.uri

        override val rating: TrailRating?
            get() = group.rating
    }

    companion object {
        /**
         * [segments] as rows: one per path in [paths] that one of them lies on, holding the groups of its segments, then one per segment
         * group that lies on no known path, each segment group shown as its first member of [sport]. In the order each first appears.
         */
        fun of(segments: List<SegmentView>, paths: List<PathView>, sport: String?): List<BrowseRow> {
            val known = paths.associateBy { it.uri }
            val onPath = LinkedHashMap<AtUri, MutableList<SegmentView>>()
            val loose = mutableListOf<SegmentView>()
            for (segment in segments) {
                val on = segment.paths.filter { it in known }
                if (on.isEmpty()) loose += segment else on.forEach { onPath.getOrPut(it) { mutableListOf() } += segment }
            }
            return onPath.map { (uri, on) -> Path(known.getValue(uri), SegmentGroupRow.of(on, sport).toImmutableList(), sport) } +
                SegmentGroupRow.of(loose, sport).map(::Group)
        }
    }
}
