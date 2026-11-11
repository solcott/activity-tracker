package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.ALPINE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.SKI
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Downhill pistes as `alpine` runs and named aerialways as lifts (segments/trail-candidates). */
class AlpineCandidatesTest {
    private val lat = 47.0
    private val lng = 8.0

    private fun piste(name: String?, vararg more: Pair<String, String>) = buildMap {
        put("piste:type", "downhill")
        name?.let { put("name", it) }
        putAll(more)
    }

    private fun lift(kind: String, name: String?) = buildMap {
        put("aerialway", kind)
        name?.let { put("name", it) }
    }

    /** Nodes 1 to 3 running 1.2 km north, a node every 600 m. */
    private fun OsmBuilder.column(meters: Double = 600.0) {
        node(1, lat, lng)
        node(2, north(lat, meters), lng)
        node(3, north(lat, 2 * meters), lng)
    }

    @Test
    fun aDownhillPisteIsAnAlpineTrailNamedByItsPisteName() {
        assertEquals(setOf(ALPINE), TrailTags.wayGroups(piste("Lynx")))
        val renamed = TrailTags.normalized(piste(null, "piste:name" to "Upper Lynx"))
        assertEquals("Upper Lynx", renamed["name"])
        assertEquals(setOf(ALPINE), TrailTags.wayGroups(renamed))
        assertEquals("Lynx", TrailTags.normalized(piste("Lynx", "piste:name" to "Other"))["name"], "a name wins")
        assertEquals(setOf(SKI), TrailTags.wayGroups(mapOf("piste:type" to "nordic", "name" to "Loop")), "nordic is unchanged")
    }

    @Test
    fun aPisteMappedAsAnAreaIsNoTrail() {
        assertTrue(TrailTags.wayGroups(piste("Bowl", "area" to "yes")).isEmpty())
    }

    @Test
    fun aDownhillPisteRouteIsAnAlpineTrail() {
        assertEquals(
            setOf(ALPINE),
            TrailTags.relationGroups(mapOf("type" to "route", "route" to "piste", "piste:type" to "downhill", "name" to "Lynx")),
        )
        assertTrue(
            TrailTags.relationGroups(mapOf("type" to "route", "route" to "piste", "piste:type" to "nordic", "name" to "Loop")).isEmpty(),
        )
    }

    @Test
    fun alpineTrailsGoOnlyTheWayTheyreDrawn() {
        assertEquals(Directions.FORWARD, TrailTags.directions(piste("Lynx"), ALPINE))
        assertEquals(Directions.FORWARD, TrailTags.directions(piste("Lynx", "oneway" to "-1"), ALPINE))
        val data = osm {
            column()
            way(10, 1, 2, 3, tags = piste("Lynx"))
        }
        val candidates = candidatesOf(data, ClosureFilter(emptyList()))
        assertEquals(listOf("Lynx"), candidates.map { it.name }, "one way, no direction in its name")
        assertEquals(SegmentKind.RUN, candidates.single().segmentKind)
        assertEquals(lat, candidates.single().points.first().lat, 1e-9)
    }

    @Test
    fun aNamedChairliftIsOneWholeLiftCandidate() {
        val data = osm {
            column(700.0)
            node(4, north(lat, 700.0), lng + 0.01)
            way(10, 1, 2, 3, tags = lift("chair_lift", "Summit Quad"))
            // A piste meeting the lift at its middle node doesn't cut it.
            way(11, 2, 4, tags = piste("Traverse"))
        }
        val quad = candidatesOf(data, ClosureFilter(emptyList())).single { it.segmentKind == SegmentKind.LIFT }
        assertEquals("Summit Quad" to ALPINE, quad.name to quad.group)
        assertEquals(listOf(10L), quad.osmWays)
        assertEquals(1400.0, SegmentGeometry.lengthMeters(quad.points), 5.0)
        assertEquals(lat, quad.points.first().lat, 1e-9, "bottom to top as drawn")
    }

    @Test
    fun aGondolaKeepsItsGenericWord() {
        val data = osm {
            column()
            way(10, 1, 2, 3, tags = lift("gondola", "Peak Gondola"))
        }
        assertEquals(listOf("Peak Gondola"), candidatesOf(data, ClosureFilter(emptyList())).map { it.name })
    }

    @Test
    fun zipLinesShortTowsAndUnnamedLiftsGiveNothing() {
        val data = osm {
            column()
            node(5, north(lat, 2000.0), lng)
            node(6, north(lat, 2120.0), lng)
            way(10, 1, 2, 3, tags = lift("zip_line", "Flyer"))
            way(11, 5, 6, tags = lift("rope_tow", "Bunny Tow"))
            way(12, 1, 2, 3, tags = lift("t-bar", null))
        }
        assertTrue(candidatesOf(data, ClosureFilter(emptyList())).isEmpty())
    }

    @Test
    fun aLiftAndARunOnOneLineAreBothKept() {
        val data = osm {
            column()
            node(4, north(lat, 1200.0), lng + 0.000_01)
            way(10, 1, 2, 3, tags = lift("chair_lift", "Summit Quad"))
            way(11, 1, 2, 4, tags = piste("Under the Quad"))
        }
        val kinds = candidatesOf(data, ClosureFilter(emptyList())).map { it.segmentKind }.sorted()
        assertEquals(listOf(SegmentKind.LIFT, SegmentKind.RUN), kinds)
    }

    @Test
    fun theCandidatesFileCarriesALiftsKind() {
        val data = osm {
            column()
            way(10, 1, 2, 3, tags = lift("gondola", "Peak Gondola"))
            way(11, 1, 2, 3, tags = piste("Lynx"))
        }
        val out = StringWriter()
        write(out, "test-region", null, candidatesOf(data, ClosureFilter(emptyList())))
        val file = readCandidates(out.toString().lineSequence())
        assertEquals(
            mapOf("Peak Gondola" to SegmentKind.LIFT, "Lynx" to null),
            file.candidates.associate { it.name to it.kind },
        )
    }
}
