package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import io.github.solcott.activitytracker.location.TrackMath
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** How a segment's line is drawn on a map of many: a lift (muted and dashed), a run, or the selected run (highlighted, on top). */
enum class MapSegmentRole {
    Lift,
    Run,
    Selected,
}

/** One segment's line on a map of many, decoded: [uri] names it, [points] are its positions, [role] how it's drawn. */
@Immutable data class MapSegment(val uri: String, val points: ImmutableList<GeoPoint>, val role: MapSegmentRole)

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

    override fun equals(other: Any?): Boolean = other is SegmentsMap && key == other.key

    override fun hashCode(): Int = key.hashCode()

    companion object {
        /** [lines] with [selected] (a run's URI) highlighted; lines that don't decode to two points are left out. */
        fun of(lines: List<MapSegmentLine>, selected: String?): SegmentsMap {
            val drawn =
                lines
                    .mapNotNull { line ->
                        val points = TrackMath.decodePolylineOrNull(line.polyline)?.takeIf { it.size >= 2 } ?: return@mapNotNull null
                        val role =
                            when {
                                line.lift -> MapSegmentRole.Lift
                                line.uri == selected -> MapSegmentRole.Selected
                                else -> MapSegmentRole.Run
                            }
                        MapSegment(line.uri, points.toImmutableList(), role)
                    }
                    .sortedBy { it.role.ordinal }
            return SegmentsMap(drawn.toImmutableList(), lines.joinToString(";") { "${it.uri}|${it.lift}|${it.polyline}" } + "#$selected")
        }
    }
}
