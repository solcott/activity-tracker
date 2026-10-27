package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.location.GeoPoint
import io.github.solcott.activitytracker.location.SegmentGeometry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Cutting trails into candidates, naming them and giving them ids (segments/trail-candidates). */
class CandidateCutterTest {
    private fun candidates(data: OsmData, group: String = RIDE) =
        CandidateCutter(data).let { cutter -> TrailBuilder.build(data).filter { it.group == group }.flatMap(cutter::cut) }

    /** Oak Trail north along lng -72.7, a node every [spacing] m from id 1, as two ways split at the middle node. */
    private fun OsmBuilder.oak(nodes: Int, spacing: Double = 500.0, vararg more: Pair<String, String>) {
        for (i in 0 until nodes) node(i + 1L, north(44.4, i * spacing), -72.7)
        val mid = nodes / 2 + 1L
        way(100, *(1L..mid).toList().toLongArray(), tags = path("Oak Trail", *more))
        way(101, *(mid..nodes.toLong()).toList().toLongArray(), tags = path("Oak Trail", *more))
    }

    /** A named way crossing node [at] east to west, through nodes [id] and [id] + 1. */
    private fun OsmBuilder.crossing(id: Long, at: Long, tags: Map<String, String>) {
        node(id, 0.0, 0.0)
        node(id + 1, 0.0, 0.0)
        way(id, id, at, id + 1, tags = tags)
    }

    private val Candidate.meters
        get() = lengthMeters

    /** Two-way "Ridge Trail" sections north along lng -72.7, a node every [spacing] m from id 1, split at node [split]. */
    private fun OsmBuilder.ridge(nodes: Int, split: Long, spacing: Double = 500.0) {
        for (i in 0 until nodes) node(i + 1L, north(44.4, i * spacing), -72.7)
        way(100, *(1L..split).toList().toLongArray(), tags = path("Ridge Trail - South Section"))
        way(101, *(split..nodes.toLong()).toList().toLongArray(), tags = path("Ridge Trail - North Section"))
    }

    @Test
    fun aParentTrailGivesOnlyItsWholeCandidateEachWay() {
        val data = osm {
            ridge(nodes = 9, split = 5)
            crossing(20, at = 3, tags = highway("unclassified", "Fire Road 3"))
        }
        val parents = candidates(data).filter { it.kind == TrailKind.PARENT }
        assertEquals(setOf("Ridge Trail (northbound)", "Ridge Trail (southbound)"), parents.map { it.name }.toSet())
        assertTrue(parents.all { it.whole && (it.meters + 50) / 100 * 100 == 4000L })
    }

    @Test
    fun aForkThatRejoinsNamesEachParentAfterItsOwnBranch() {
        val data = osm {
            for (i in 0..1) node(i + 1L, north(44.4, i * 500.0), -72.7)
            node(3, north(44.4, 1000.0), -72.69)
            node(4, north(44.4, 1000.0), -72.71)
            node(5, north(44.4, 1500.0), -72.7)
            way(10, 1, 2, tags = path("Rush Trail - Tom Sawyer Section", "oneway" to "yes"))
            way(11, 2, 3, 5, tags = path("Rush Trail - Exit Stage Left Section", "oneway" to "yes"))
            way(12, 2, 4, 5, tags = path("Rush Trail - Through the Trees Trail Section", "oneway" to "yes"))
        }
        assertEquals(
            listOf("Rush Trail via Exit Stage Left Section", "Rush Trail via Through the Trees Trail Section"),
            candidates(data).filter { it.kind == TrailKind.PARENT }.map { it.name }.sorted(),
        )
    }

    private fun parentSpans(data: OsmData) = candidates(data).filter { it.kind == TrailKind.PARENT && !it.whole }.map { it.name }.toSet()

    @Test
    fun aParentTrailGivesSpansToNamedTrailsAtItsSectionBoundaries() {
        val data = osm {
            for (i in 0 until 5) node(i + 1L, north(44.4, i * 500.0), -72.7)
            way(10, 1, 2, tags = path("Rush Trail - Tom Sawyer Section", "oneway" to "yes"))
            way(11, 2, 3, tags = path("Rush Trail - 2112 Section", "oneway" to "yes"))
            way(12, 3, 4, tags = path("Rush Trail - Dreamline Section", "oneway" to "yes"))
            way(13, 4, 5, tags = path("Rush Trail - Limelight Section", "oneway" to "yes"))
            crossing(20, at = 3, tags = path("Coyote Hollow Foot Path"))
            crossing(22, at = 3, tags = path("Canyon Hollow Trail"))
            crossing(24, at = 4, tags = highway("track", "Bonneville Shoreline Trail - Draper Section"))
        }
        assertEquals(
            setOf("Rush Trail to Canyon Hollow Trail", "Rush Trail to Bonneville Shoreline Trail", "Rush Trail from Canyon Hollow Trail"),
            parentSpans(data),
            "nothing from the Bonneville Shoreline Trail, which is Limelight alone",
        )
    }

    @Test
    fun aRoadCrossingMidSectionGivesNoSpan() {
        val data = osm {
            for (i in 0 until 4) node(i + 1L, north(44.4, i * 500.0), -72.7)
            way(10, 1, 2, 3, tags = path("Rush Trail - Tom Sawyer Section", "oneway" to "yes"))
            way(11, 3, 4, tags = path("Rush Trail - 2112 Section", "oneway" to "yes"))
            crossing(20, at = 2, tags = highway("unclassified", "Fire Road 3"))
        }
        assertEquals(emptySet(), parentSpans(data))
    }

    @Test
    fun aTwoWayParentsSpansReadFromInReverse() {
        val data = osm {
            for (i in 0 until 9) node(i + 1L, north(44.4, i * 500.0), -72.7)
            way(100, 1, 2, 3, tags = path("Ridge Trail - South Section"))
            way(101, 3, 4, 5, 6, tags = path("Ridge Trail - Middle Section"))
            way(102, 6, 7, 8, 9, tags = path("Ridge Trail - North Section"))
            crossing(20, at = 6, tags = highway("unclassified", "Fire Road 3"))
        }
        assertEquals(setOf("Ridge Trail to Fire Road 3", "Ridge Trail from Fire Road 3"), parentSpans(data))
    }

    @Test
    fun aParentTrailOverFiftyKilometersGivesNoCandidate() {
        val data = osm { ridge(nodes = 3, split = 2, spacing = 30_000.0) }
        val found = candidates(data)
        assertEquals(emptyList(), found.filter { it.kind == TrailKind.PARENT })
        assertEquals(4, found.size, "each 30 km section, each way")
    }

    @Test
    fun aTrailCrossedByAFireRoadGivesTheWholeTrailAndEachStretchEachWay() {
        val data = osm {
            oak(nodes = 9)
            crossing(20, at = 4, tags = highway("unclassified", "Fire Road 3"))
        }
        val found = candidates(data)
        assertEquals(6, found.size)
        assertEquals(listOf(1500L, 1500L, 2500L, 2500L, 4000L, 4000L), found.map { (it.meters + 50) / 100 * 100 }.sorted())
    }

    @Test
    fun aShortStretchIsJoinedToTheNextOne() {
        val data = osm {
            // 2 km, nodes every 100 m; named trails meet at 800 m and 1100 m.
            oak(nodes = 21, spacing = 100.0)
            crossing(20_000, at = 9, tags = path("Pine Trail"))
            crossing(20_010, at = 12, tags = path("Elm Trail"))
        }
        val stretches = candidates(data).filter { !it.whole }.map { (it.meters + 50) / 100 * 100 }.toSet()
        assertEquals(setOf(800L, 1200L), stretches, "the 300 m stretch joins the next")
    }

    @Test
    fun anUnnamedCrossingDoesntCutTheTrail() {
        val data = osm {
            oak(nodes = 9)
            crossing(30, at = 6, tags = path(null))
        }
        assertTrue(candidates(data).all { it.whole })
        assertEquals(2, candidates(data).size)
    }

    @Test
    fun aTwoWayTrailGivesEachCandidateBothWays() {
        val data = osm {
            oak(nodes = 9)
            crossing(20, at = 4, tags = highway("unclassified", "Fire Road 3"))
        }
        val found = candidates(data)
        val ends = found.map { it.points.first() to it.points.last() }
        assertTrue(ends.all { (a, b) -> (b to a) in ends })
    }

    @Test
    fun aOneWayTrailFollowsItsWaysDirectionOnly() {
        val data = osm { oak(nodes = 5, spacing = 250.0, "oneway" to "yes") }
        val whole = candidates(data).single()
        assertTrue(whole.points.last().lat > whole.points.first().lat, "north, as the ways are drawn")
        assertEquals("Oak Trail", whole.name, "no direction in the name of a one-way trail")
    }

    @Test
    fun aStretchIsNamedAfterTheWaysAtItsEnds() {
        val data = osm {
            // 3.5 km; a fire road crosses at 1 km and Pine Trail meets it at 2.5 km.
            oak(nodes = 8)
            crossing(20, at = 3, tags = highway("track", "Fire Road 3"))
            crossing(30, at = 6, tags = path("Pine Trail"))
        }
        val names = candidates(data).map { it.name }.toSet()
        assertTrue("Oak Trail: Fire Road 3 to Pine Trail" in names, "$names")
        assertTrue("Oak Trail: Pine Trail to Fire Road 3" in names, "the way back")
        assertTrue("Oak Trail (part 1)" in names, "the stretch from the trail's unnamed start")
        assertTrue("Oak Trail (part 3)" in names, "the stretch to its unnamed end")
    }

    @Test
    fun aWholeTwoWayTrailIsNamedWithItsDirection() {
        val data = osm { oak(nodes = 9) }
        assertEquals(setOf("Oak Trail (northbound)", "Oak Trail (southbound)"), candidates(data).map { it.name }.toSet())
    }

    @Test
    fun aNameIsCutAtAWordToAHundredGraphemes() {
        val name = "The Very Long Trail Name ".repeat(6).trim()
        val cut = CandidateCutter.truncate(name)
        assertTrue(cut.length <= 100)
        assertTrue(name.startsWith(cut) && name[cut.length] == ' ', "cut at a word boundary: '$cut'")
        assertEquals("Short", CandidateCutter.truncate("Short"))
        assertEquals("👍".repeat(100), CandidateCutter.truncate("👍".repeat(101)), "graphemes, not chars")
    }

    @Test
    fun theSameDataGivesTheSameIds() {
        val before = candidates(osm { oak(nodes = 9) }).map { it.id }.toSet()
        val after =
            candidates(
                    osm {
                        oak(nodes = 9)
                        // An unrelated trail elsewhere.
                        node(500, 44.5, -72.5)
                        node(501, north(44.5, 800.0), -72.5)
                        way(600, 500, 501, tags = path("Far Trail"))
                    }
                )
                .filter { it.name.startsWith("Oak") }
                .map { it.id }
                .toSet()
        assertEquals(before, after)
        assertEquals(26, before.first().length)
    }

    @Test
    fun aReroutedTrailGetsNewIds() {
        val before = candidates(osm { oak(nodes = 9) }).map { it.id }.toSet()
        val rerouted = osm {
            for (i in 0 until 9) node(i + 1L, north(44.4, i * 500.0), -72.7)
            way(100, 1, 2, 3, 4, 5, tags = path("Oak Trail"))
            // The second way replaced by a new one.
            way(102, 5, 6, 7, 8, 9, tags = path("Oak Trail"))
        }
        assertTrue(candidates(rerouted).none { it.id in before })
    }

    @Test
    fun directionChangesTheId() {
        val ids = CandidateCutter.idOf(RIDE, listOf(1, 2), true, 10, 20) to CandidateCutter.idOf(RIDE, listOf(2, 1), false, 20, 10)
        assertNotEquals(ids.first, ids.second)
        assertNotEquals(CandidateCutter.idOf(RIDE, listOf(1), true, 10, 20), CandidateCutter.idOf(HIKE, listOf(1), true, 10, 20))
    }

    /** Hiking candidates from a Forest Service dataset of one feature, `TRAIL_CN` 1001, through [points] (lat to lng). */
    private fun agencyCandidates(points: List<Pair<Double, Double>>): List<Candidate> {
        val coordinates = points.joinToString(",") { (lat, lng) -> "[$lng,$lat]" }
        val file = kotlin.io.path.createTempFile("usfs", ".geojsonl").toFile()
        file.writeText(
            """{"type":"Feature","properties":{"TRAIL_NAME":"Long","TRAIL_CN":"1001","BMP":0,""" +
                """"HIKER_PEDESTRIAN_MANAGED":"01/01-12/31"},"geometry":{"type":"LineString","coordinates":[$coordinates]}}""" +
                "\n"
        )
        val data = OsmData(emptyList(), emptyList(), NodeCoords(LongArray(0))) + AgencyReader.read(listOf(AgencyRules.USFS to file))
        return candidates(data, HIKE)
    }

    private val agencyLine = (0..4).map { north(44.4, it * 250.0) to -72.7 }

    @Test
    fun anAgencyCandidateKeepsItsIdWhenAnInteriorVertexMoves() {
        val before = agencyCandidates(agencyLine)
        val moved = agencyCandidates(agencyLine.mapIndexed { i, (lat, lng) -> if (i == 2) lat to lng + 0.00005 else lat to lng })
        assertEquals(2, before.size)
        assertEquals(before.map { it.id }, moved.map { it.id })
        assertEquals(listOf("usfs:1001@0"), before.first().agencyFeatures)
        assertEquals(
            Triple("agency", "CC0-1.0", "USDA Forest Service"),
            before.first().let { Triple(it.source, it.license, it.attribution) },
        )
        assertEquals(emptyList(), before.first().osmWays)
    }

    @Test
    fun anAgencyCandidatesIdChangesWithItsEnds() {
        val before = agencyCandidates(agencyLine).map { it.id }.toSet()
        val longer = agencyCandidates(agencyLine + (north(44.4, 1100.0) to -72.7)).map { it.id }.toSet()
        assertTrue(before.intersect(longer).isEmpty())
        val nudged = agencyCandidates(agencyLine.dropLast(1) + (north(44.4, 1002.0) to -72.7)).map { it.id }.toSet()
        assertTrue(before.intersect(nudged).isEmpty(), "two metres is past the 1e-5 degree rounding")
    }

    @Test
    fun anAgencyLoopsTwoDirectionsHaveTheirOwnIds() {
        val dLng = 250.0 / (111_195.0 * cos(44.4 * Math.PI / 180))
        val square =
            listOf(44.4 to -72.7, 44.4 to -72.7 + dLng, north(44.4, 250.0) to -72.7 + dLng, north(44.4, 250.0) to -72.7, 44.4 to -72.7)
        val found = agencyCandidates(square)
        assertEquals(2, found.size)
        assertEquals(2, found.map { it.id }.toSet().size)
        assertNotEquals(
            CandidateCutter.agencyIdOf(HIKE, listOf("usfs:1"), true, GeoPoint(44.4, -72.7), GeoPoint(44.41, -72.7)),
            CandidateCutter.idOf(HIKE, listOf(1), true, 1, 2),
        )
    }

    /** A loop of one closed way: 1 km around a square, drawn counterclockwise from its south-west corner. */
    private fun OsmBuilder.loop() {
        val dLng = 250.0 / (111_195.0 * cos(44.4 * Math.PI / 180))
        node(1, 44.4, -72.7)
        node(2, 44.4, -72.7 + dLng)
        node(3, north(44.4, 250.0), -72.7 + dLng)
        node(4, north(44.4, 250.0), -72.7)
        way(100, 1, 2, 3, 4, 1, tags = path("Pond Loop"))
    }

    @Test
    fun aLoopsTwoDirectionsHaveTheirOwnIds() {
        val found = candidates(osm { loop() })
        assertEquals(2, found.size)
        assertEquals(2, found.map { it.id }.toSet().size)
    }

    @Test
    fun aWayThatCrossesItselfAtAJunctionGivesEachStretchItsOwnId() {
        val data = osm {
            // A lasso: north 600 m to node 4, around a square, back through node 4 and on north 600 m.
            val dLng = 300.0 / (111_195.0 * cos(44.4 * Math.PI / 180))
            node(1, 44.4, -72.7)
            node(4, north(44.4, 600.0), -72.7)
            node(5, north(44.4, 600.0), -72.7 + dLng)
            node(6, north(44.4, 900.0), -72.7 + dLng)
            node(7, north(44.4, 900.0), -72.7 - dLng)
            node(8, north(44.4, 1500.0), -72.7 - dLng)
            way(100, 1, 4, 5, 6, 7, 4, 8, tags = path("Lasso"))
            crossing(20, at = 4, tags = path("Cross Trail"))
        }
        val found = candidates(data)
        assertEquals(found.size, found.map { it.id }.toSet().size)
    }

    @Test
    fun aTwoWayLoopIsNamedClockwiseOrCounterclockwise() {
        val names = candidates(osm { loop() }).associate { it.points[1] to it.name }
        assertEquals(setOf("Pond Loop (clockwise)", "Pond Loop (counterclockwise)"), names.values.toSet())
        val eastFirst = names.entries.single { it.key.lng > -72.7 + 1e-6 && it.key.lat < 44.4 + 1e-6 }
        assertEquals("Pond Loop (counterclockwise)", eastFirst.value, "east, then north: counterclockwise")
    }

    @Test
    fun everyCandidatePassesTheAppViewsSegmentChecks() {
        val data = osm {
            oak(nodes = 9)
            crossing(20, at = 4, tags = highway("unclassified", "Fire Road 3"))
        }
        for (group in listOf(RIDE, HIKE)) {
            for (candidate in candidates(data, group)) {
                val line = requireNotNull(SegmentGeometry.decodePolyline(candidate.line))
                assertTrue(line.size >= 2)
                val length = SegmentGeometry.lengthMeters(line)
                assertTrue(length >= io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.minMeters(group))
                assertTrue(length <= io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.MAX_METERS)
                assertTrue(candidate.line.length <= CandidateCutter.MAX_LINE_CHARS)
                assertTrue(candidate.name.isNotBlank())
            }
        }
    }

    @Test
    fun aSimplifiedLineStaysWithinFiveMetersOfTheTrail() {
        // A trail wiggling 3 m either side every 20 m.
        val trail = (0..200).map { i -> GeoPoint(north(44.4, i * 20.0), -72.7 + (if (i % 2 == 0) 3.0 else -3.0) / metersPerLngDegree) }
        val line = CandidateCutter.simplified(trail)
        assertTrue(line.size < trail.size)
        assertTrue(trail.all { p -> distanceToLine(p, line) <= CandidateCutter.TOLERANCE_M + 0.01 })
    }

    @Test
    fun aLineTooLongForARecordIsSimplifiedFurther() {
        // 48 km zigzagging 30 m either side every 1.6 m: too many points to keep at 5 m.
        val trail = (0..30_000).map { i -> GeoPoint(north(44.4, i * 1.6), -72.7 + (if (i % 2 == 0) 30.0 else -30.0) / metersPerLngDegree) }
        assertTrue(SegmentGeometry.encodePolyline(trail).length > CandidateCutter.MAX_LINE_CHARS, "the raw line is too long for a record")
        val line = SegmentGeometry.encodePolyline(CandidateCutter.simplified(trail))
        assertTrue(line.length <= CandidateCutter.MAX_LINE_CHARS, "${line.length} chars")
    }

    private val metersPerLngDegree = 111_195.0 * cos(44.4 * Math.PI / 180)

    /** Meters from [p] to the nearest point of [line], on a local flat projection. */
    private fun distanceToLine(p: GeoPoint, line: List<GeoPoint>): Double =
        line.zipWithNext().minOf { (a, b) ->
            fun xy(q: GeoPoint) = (q.lng - p.lng) * metersPerLngDegree to (q.lat - p.lat) * 111_195.0
            val (ax, ay) = xy(a)
            val (bx, by) = xy(b)
            val dx = bx - ax
            val dy = by - ay
            val t = if (dx == 0.0 && dy == 0.0) 0.0 else (-(ax * dx + ay * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
            val x = ax + t * dx
            val y = ay + t * dy
            kotlin.math.sqrt(x * x + y * y).let { if (abs(it) < 1e-9) 0.0 else it }
        }
}
