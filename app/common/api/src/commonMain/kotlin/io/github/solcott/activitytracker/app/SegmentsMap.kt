package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.segment.TrailRating
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * How a segment's line is drawn on a map of many: a lift (muted and dashed), a run, or the selected or hovered run (highlighted, on top).
 */
enum class MapSegmentRole {
    Lift,
    Run,
    Selected,
}

/**
 * One segment's line on a map of many, decoded: [uri] names it, [points] are its positions, [role] how it's drawn, [rating] the colour a
 * run takes (segments/trail-ratings; null unrated), and [trail] false for a line that isn't one (a dive site), drawn in the accent colour.
 */
@Immutable
data class MapSegment(
    val uri: String,
    val points: ImmutableList<GeoPoint>,
    val role: MapSegmentRole,
    val rating: TrailRating? = null,
    val trail: Boolean = true,
)

/** One segment's line placed on a stand-in tile, in drawing order, how it's drawn, its run's [rating] and whether it's a [trail]. */
@Immutable
data class SketchedSegment(
    val points: ImmutableList<ShapePoint>,
    val role: MapSegmentRole,
    val rating: TrailRating? = null,
    val trail: Boolean = true,
)

/** One line of a map's rating legend (segments/trail-ratings): a level, or unrated where [rating] is null, and its name. */
@Immutable data class RatingLegendEntry(val rating: TrailRating?, val label: AppText)

/**
 * Many segments' lines as every map of them draws and answers (segments/segment-browse, dev/shared-display-text): [drawn] in drawing order
 * (lifts first, then runs, then the selected run on top), each decoded once; [nearest], the run a tap selects; [key], what a UI that holds
 * the lines redraws on. The shared map's stand-in and the native iOS map ask it.
 */
@Immutable
class SegmentsMap private constructor(val drawn: ImmutableList<MapSegment>, val key: String) {
    /** Every position of every line, for fitting them all on a stand-in. */
    val allPoints: List<GeoPoint>
        get() = drawn.flatMap { it.points }

    /**
     * The levels the runs are drawn in, Easiest to Extreme, then Unrated when an unrated run shows beside a rated one; empty when no run is
     * rated, so no legend shows (segments/trail-ratings). Lifts take no part.
     */
    val legend: ImmutableList<RatingLegendEntry> = legendOf(drawn)

    /**
     * The run whose line passes nearest ([lat], [lng]) within [withinMeters], the reach of a tap there; null when none is in reach. Lifts
     * can't be selected.
     */
    fun nearest(lat: Double, lng: Double, withinMeters: Double): String? {
        val tap = GeoPoint(lat, lng)
        return drawn
            .asSequence()
            .filter { it.role != MapSegmentRole.Lift }
            .map { it.uri to SegmentGeometry.distanceToLineMeters(tap, it.points) }
            .filter { (_, meters) -> meters <= withinMeters }
            .minByOrNull { (_, meters) -> meters }
            ?.first
    }

    /**
     * Every line placed on a stand-in tile [width] by [height] with [pad] kept clear, all scaled together (`TileFit`, as the shared
     * stand-in fits them), in drawing order; empty when there are none.
     */
    fun sketch(width: Double, height: Double, pad: Double): List<SketchedSegment> {
        val fit = TileFit.of(allPoints, width, height, pad) ?: return emptyList()
        return drawn.map { line -> SketchedSegment(line.points.map(fit::place).toImmutableList(), line.role, line.rating, line.trail) }
    }

    override fun equals(other: Any?): Boolean = other is SegmentsMap && key == other.key

    override fun hashCode(): Int = key.hashCode()

    companion object {
        /**
         * [lines] with [selected] and [hovered] (runs' URIs; the run under the pointer, in a row or on the map) highlighted alike; a lift
         * is never highlighted. Lines that don't decode to two points are left out.
         */
        fun of(lines: List<MapSegmentLine>, selected: String?, hovered: String? = null): SegmentsMap =
            of(lines, selected, hoveredLines = setOfNotNull(hovered))

        /** [lines] with [selected] and every one of [hoveredLines] (a section of rows under the pointer) highlighted alike. */
        fun of(lines: List<MapSegmentLine>, selected: String?, hoveredLines: Set<String>): SegmentsMap {
            val drawn =
                lines
                    .mapNotNull { line ->
                        val points = TrackMath.decodePolylineOrNull(line.polyline)?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                        val role =
                            when {
                                line.lift -> MapSegmentRole.Lift
                                line.uri == selected || line.uri in hoveredLines -> MapSegmentRole.Selected
                                else -> MapSegmentRole.Run
                            }
                        MapSegment(
                            line.uri,
                            points.toImmutableList(),
                            role,
                            line.rating.takeUnless { line.lift || !line.trail },
                            line.trail,
                        )
                    }
                    .sortedBy { it.role.ordinal }
            return SegmentsMap(
                drawn.toImmutableList(),
                lines.joinToString(";") { "${it.uri}|${it.lift}|${it.rating}|${it.trail}|${it.polyline}" } +
                    "#$selected#${hoveredLines.sorted().joinToString(",").ifEmpty { "null" }}",
            )
        }
    }
}

private fun legendOf(drawn: List<MapSegment>): ImmutableList<RatingLegendEntry> {
    val runs = drawn.filter { it.role != MapSegmentRole.Lift && it.trail }
    val levels = runs.mapNotNull { it.rating }.toSet()
    if (levels.isEmpty()) return persistentListOf()
    val rated = TrailRating.entries.filter { it in levels }.map { RatingLegendEntry(it, it.label) }
    val unrated = RatingLegendEntry(null, TrailRating.unratedLabel).takeIf { runs.any { run -> run.rating == null } }
    return (rated + listOfNotNull(unrated)).toImmutableList()
}
