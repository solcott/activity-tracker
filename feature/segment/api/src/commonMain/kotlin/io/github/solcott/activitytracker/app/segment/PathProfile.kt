package io.github.solcott.activitytracker.app.segment

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.chart.ChartSpan
import io.github.solcott.activitytracker.app.chart.LineChart
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.StatText
import io.github.solcott.activitytracker.data.units.UnitConversions
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** One covered line piece of a trail: its [index] in the path's lines, its terrain [profile], and its [line] for the map's marker. */
@Immutable data class PathProfilePiece(val index: Int, val profile: SegmentProfile, val line: ImmutableList<GeoPoint>)

/** Where the segment [uri] lies along the piece at [piece], from [startMeters] to [endMeters] along it, nearer end first. */
@Immutable data class PathSpan(val uri: String, val piece: Int, val startMeters: Long, val endMeters: Long)

/**
 * A trail's terrain profile as the Trail page draws it (segments/path-profile): its covered [pieces] one after another, the meters they
 * climb ([gainMeters]) and descend ([lossMeters]) together, and where each segment the AppView returned lies along one ([spans]). Both UIs
 * ask it, so the chart, its stretches and the map's marker follow one rule.
 */
@Immutable
class PathProfile(
    val pieces: ImmutableList<PathProfilePiece>,
    val gainMeters: Int,
    val lossMeters: Int,
    val spans: ImmutableList<PathSpan>,
) {
    /** Where each piece starts along the chart, in meters: the pieces before it laid end to end. */
    private val startsMeters: ImmutableList<Double> =
        pieces.runningFold(0.0) { at, piece -> at + piece.profile.distanceMeters }.toImmutableList()

    /** The pieces' whole length, in meters. */
    val lengthMeters: Double
        get() = startsMeters.last()

    /** The profile as a chart in [units], the line breaking between pieces, with no stretches marked yet ([marked]). */
    fun chart(units: UnitSystem): LineChart = profileChart(pieces.map { it.profile }, units)

    /**
     * [chart] (this profile's, in [units]) with each of [rows]' stretches marked in its rating level's colour, from its shown segment's
     * span, and [shaded]'s (a row's shown segment's URI: the selected or hovered one) shaded, in order along the trail. A row whose segment
     * has no span has no mark.
     */
    fun marked(chart: LineChart, rows: List<SegmentGroupRow>, shaded: String?, units: UnitSystem): LineChart =
        chart.withSpans(
            rows
                .mapNotNull { row ->
                    val uri = row.shown.uri.value
                    val span = spans.firstOrNull { it.uri == uri } ?: return@mapNotNull null
                    val start = pieceStart(span.piece) ?: return@mapNotNull null
                    ChartSpan(
                        from = toUnit(start + span.startMeters, units),
                        to = toUnit(start + span.endMeters, units),
                        rating = row.rating,
                        selected = uri == shaded,
                    )
                }
                // In order along the trail, whatever order the list sorts its rows in.
                .sortedWith(compareBy({ it.from }, { it.to })),
        )

    /**
     * The place on the trail at [x] along the chart in [units], for the map's marker as a readout moves; null outside every piece, and when
     * a piece's line is missing.
     */
    fun pointAt(x: Double, units: UnitSystem): GeoPoint? {
        val meters = UnitConversions.fromDistanceUnit(x, units)
        val index = pieces.indices.lastOrNull { startsMeters[it] <= meters + EDGE_M } ?: return null
        val piece = pieces[index]
        val along = (meters - startsMeters[index]).coerceIn(0.0, piece.profile.distanceMeters.toDouble())
        // The served length and the decoded line's agree but for rounding, so the marker is placed by share of each.
        val share = if (piece.profile.distanceMeters > 0) along / piece.profile.distanceMeters else 0.0
        return SegmentGeometry.pointAlong(piece.line, share * SegmentGeometry.lengthMeters(piece.line))
    }

    /** Where the piece at [index] in the path's lines starts along the chart, in meters; null when it has no profile. */
    private fun pieceStart(index: Int): Double? = pieces.indexOfFirst { it.index == index }.takeIf { it >= 0 }?.let(startsMeters::get)

    private fun toUnit(meters: Double, units: UnitSystem): Double = UnitConversions.toDistanceUnit(meters, units)

    companion object {
        /**
         * [profile] as the AppView served it for a path of [lines] (its view's line pieces, encoded): each covered piece whose heights draw
         * and whose line decodes. Null when none does.
         */
        fun of(profile: GetPath.PathProfile?, lines: List<String>): PathProfile? {
            val pieces =
                profile?.pieces.orEmpty().mapNotNull { piece ->
                    val heights = terrainProfileOf(piece.heights, piece.lengthMeters, piece.maxGrade) ?: return@mapNotNull null
                    val line = lines.getOrNull(piece.piece)?.let(SegmentGeometry::decodePolyline)?.takeIf { it.size >= 2 }
                    line?.let { PathProfilePiece(piece.piece, heights, it.toImmutableList()) }
                }
            if (profile == null || pieces.isEmpty()) return null
            return PathProfile(
                pieces.toImmutableList(),
                profile.gain,
                profile.loss,
                profile.spans.map { PathSpan(it.segment.value, it.piece, it.startMeters, it.endMeters) }.toImmutableList(),
            )
        }

        /** How far before a piece's start a place still counts as on it, so rounding at a join doesn't fall between pieces. */
        private const val EDGE_M = 0.5
    }
}

/** What the Trail page's summary adds for its [profile] in [units] (segments/path-profile): "520 m up" and "310 m down". */
object PathProfileTexts {
    fun climb(profile: PathProfile, units: UnitSystem): List<AppText> =
        listOf(
            AppText.named("path_climb_up", StatText.elevation(profile.gainMeters.toDouble(), units)),
            AppText.named("path_climb_down", StatText.elevation(profile.lossMeters.toDouble(), units)),
        )
}
