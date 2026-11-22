package io.github.solcott.activitytracker.app.segment

import io.github.solcott.activitytracker.app.chart.ChartSpan
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allStringResources
import io.github.solcott.activitytracker.data.units.UnitSystem
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.GetPath
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A trail's profile as the Trail page draws it (segments/path-profile): its pieces, stretches, map marker and climb. */
class PathProfileTest {
    private val north = SegmentGeometry.encodePolyline(listOf(GeoPoint(44.0, -73.0), GeoPoint(44.009, -73.0)))
    private val east = SegmentGeometry.encodePolyline(listOf(GeoPoint(44.009, -73.0), GeoPoint(44.009, -72.9875)))

    /** Two pieces of a path of three lines, the middle one uncovered: 1 km north, then 1 km east. */
    private val profile =
        checkNotNull(
            PathProfile.of(
                GetPath.PathProfile(
                    pieces =
                        listOf(
                            GetPath.PieceProfile(0, List(21) { 10_000 + it * 50 }, lengthMeters = 1_000),
                            GetPath.PieceProfile(2, List(21) { 11_000 - it * 20 }, lengthMeters = 1_000),
                        ),
                    gain = 100,
                    loss = 40,
                    spans =
                        listOf(
                            GetPath.SegmentSpan(PathSamples.profile.groups[0].shown.uri, 0, 0, 1_000),
                            GetPath.SegmentSpan(PathSamples.profile.groups[1].shown.uri, 2, 250, 750),
                            GetPath.SegmentSpan(AtUri("at://did:plc:x/app.activitytracker.feed.segment/elsewhere"), 1, 0, 10),
                        ),
                ),
                listOf(north, "not a line", east),
            ),
        )

    @Test
    fun piecesThatMeetRunOnWithoutABreak() {
        val chart = profile.chart(UnitSystem.Metric)
        assertEquals(42, chart.points.size)
        assertEquals(2.0, chart.xMax, 1e-9)
        val second = chart.points[21]
        assertEquals(1.0, second.x, 1e-9)
        assertTrue(chart.points.none { it.gapBefore }, "north ends where east starts")
        assertEquals(2_000.0, profile.lengthMeters, 1e-9)
    }

    /**
     * A trail stored as its arms, each drawn from the top down, meeting at the bottom (B), with a way in to the top of the longer one (A),
     * a stub off its middle and a part of its own far away: the longer arm keeps its direction, the shorter is turned round to run on up
     * from B, the way in comes first, the stub is left out and the far part comes after a break.
     */
    private val arms = run {
        val a = GeoPoint(44.0, -73.0)
        val b = GeoPoint(44.009, -73.0)
        val top = GeoPoint(44.009, -72.9935)
        val line = { points: List<GeoPoint> -> SegmentGeometry.encodePolyline(points) }
        val lines =
            listOf(
                line(listOf(top, GeoPoint(44.00901, -73.0))), // 0: the shorter arm, ~520 m, top down to B
                line(listOf(a, b)), // 1: the longer arm, 1 km, A to B
                line(listOf(GeoPoint(44.0045, -73.0), GeoPoint(44.0045, -73.0006))), // 2: a ~50 m stub off its middle
                line(listOf(GeoPoint(43.996, -73.0), GeoPoint(43.99999, -73.0))), // 3: the way in, ~445 m, ending at A
                line(listOf(GeoPoint(45.0, -73.0), GeoPoint(45.001, -73.0))), // 4: a part far away, ~110 m
            )
        val uri = { name: String -> AtUri("at://did:plc:x/app.activitytracker.feed.segment/$name") }
        checkNotNull(
            PathProfile.of(
                GetPath.PathProfile(
                    pieces =
                        listOf(
                            GetPath.PieceProfile(0, List(11) { 20_000 - it * 100 }, lengthMeters = 520),
                            GetPath.PieceProfile(1, List(21) { 10_000 + it * 450 }, lengthMeters = 1_000),
                            GetPath.PieceProfile(2, List(2) { 14_000 }, lengthMeters = 50),
                            GetPath.PieceProfile(3, List(9) { 9_000 + it * 125 }, lengthMeters = 445),
                            GetPath.PieceProfile(4, List(3) { 30_000 }, lengthMeters = 110),
                        ),
                    gain = 300,
                    loss = 0,
                    spans =
                        listOf(
                            GetPath.SegmentSpan(uri("arm"), 0, 20, 120),
                            GetPath.SegmentSpan(uri("stub"), 2, 0, 50),
                        ),
                ),
                lines,
            ),
        )
    }

    @Test
    fun thePiecesRunInTheTrailsOrderEachTheSameWay() {
        assertEquals(listOf(3, 1, 0, 4), arms.pieces.map { it.index }, "the way in, the long arm, the short arm, then the far part")
        assertEquals(listOf(false, true, true, false), arms.pieces.map { it.joined })
        val short = arms.pieces[2]
        assertEquals(1_900.0, short.profile.heightsM.first(), 1e-9, "the short arm climbs from B as the trail runs")
        assertEquals(2_000.0, short.profile.heightsM.last(), 1e-9)
        assertTrue(short.profile.gradesPercent.all { it > 0 }, "its grades are uphill as the trail runs")
        val chart = arms.chart(UnitSystem.Metric)
        assertEquals(listOf(9 + 21 + 11), chart.points.indices.filter { chart.points[it].gapBefore }, "a break only before the far part")
        assertEquals(445.0 + 1_000 + 520 + 110, arms.lengthMeters, 1e-9, "the stub is left out")
    }

    @Test
    fun aTurnedPiecesStretchesAndMarkerFollowTheTrail() {
        assertEquals(PathSpan("at://did:plc:x/app.activitytracker.feed.segment/arm", 0, 400, 500), arms.spans.first())
        val end = checkNotNull(arms.pointAt((445.0 + 1_000 + 519) / 1_000, UnitSystem.Metric))
        assertNear(GeoPoint(44.009, -72.9935), end) // a meter short of the top: the far part starts at its end
        val start = checkNotNull(arms.pointAt(0.0, UnitSystem.Metric))
        assertNear(GeoPoint(43.996, -73.0), start)
    }

    @Test
    fun eachListedRowsStretchIsMarkedInItsLevel() {
        val rows = PathSamples.profile.groups
        val base = profile.chart(UnitSystem.Metric)
        val marked = profile.marked(base, rows, shaded = rows[1].shown.uri.value, units = UnitSystem.Metric)
        assertEquals(
            listOf(
                ChartSpan(0.0, 1.0, rows[0].rating, selected = false),
                ChartSpan(1.25, 1.75, rows[1].rating, selected = true),
            ),
            marked.spans,
            "along the second piece, after the first; the stray span's segment isn't listed",
        )
        assertEquals(listOf(rows[0].shown.uri.value), rows.take(1).map { it.shown.uri.value })
        assertEquals(1, profile.marked(base, rows.take(1), null, UnitSystem.Metric).spanCount, "a row filtered out has no mark")
        assertEquals(base.points, marked.points)
    }

    @Test
    fun theMarkerIsOnTheRightPiece() {
        val start = checkNotNull(profile.pointAt(0.0, UnitSystem.Metric))
        assertNear(GeoPoint(44.0, -73.0), start)
        val middleNorth = checkNotNull(profile.pointAt(0.5, UnitSystem.Metric))
        assertNear(GeoPoint(44.0045, -73.0), middleNorth)
        val middleEast = checkNotNull(profile.pointAt(1.5, UnitSystem.Metric))
        assertNear(GeoPoint(44.009, -72.99375), middleEast)
        assertNull(profile.pointAt(-1.0, UnitSystem.Metric), "before every piece")
        val miles = checkNotNull(profile.pointAt(0.5 * 1_000 / 1_609.344, UnitSystem.Imperial))
        assertNear(middleNorth, miles)
    }

    @Test
    fun theSummaryAddsTheClimbAndDescent() {
        val texts = PathProfileTexts.climb(profile, UnitSystem.Metric)
        assertEquals(listOf("path_climb_up", "path_climb_down"), texts.map { (it as AppText.Named).name })
        val names = Res.allStringResources.keys
        assertTrue("path_climb_up" in names && "path_climb_down" in names && "path_profile_title" in names)
        val summary = PathTexts.summary(PathSamples.profile.path!!, UnitSystem.Metric, profile).toString()
        assertTrue("path_climb_up" in summary, summary)
        assertTrue("path_climb_up" !in PathTexts.summary(PathSamples.profile.path!!, UnitSystem.Metric).toString())
    }

    @Test
    fun withoutCoveredPiecesThereIsNoProfile() {
        assertNull(PathProfile.of(null, listOf(north)))
        val uncovered = GetPath.PathProfile(listOf(GetPath.PieceProfile(1, List(3) { 0 }, 100)), gain = 0, loss = 0, spans = emptyList())
        assertNull(PathProfile.of(uncovered, listOf(north)), "its line isn't there")
    }

    private fun assertNear(expected: GeoPoint, actual: GeoPoint) =
        assertTrue(abs(expected.lat - actual.lat) < 1e-4 && abs(expected.lng - actual.lng) < 1e-4, "expected $expected, was $actual")
}
