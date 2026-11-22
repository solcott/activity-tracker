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
    fun thePiecesRunOnWithABreakBetween() {
        val chart = profile.chart(UnitSystem.Metric)
        assertEquals(42, chart.points.size)
        assertEquals(2.0, chart.xMax, 1e-9)
        val second = chart.points[21]
        assertTrue(second.gapBefore, "the second piece starts after a break")
        assertEquals(1.0, second.x, 1e-9)
        assertEquals(listOf(21), chart.points.indices.filter { chart.points[it].gapBefore })
        assertEquals(2_000.0, profile.lengthMeters, 1e-9)
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
