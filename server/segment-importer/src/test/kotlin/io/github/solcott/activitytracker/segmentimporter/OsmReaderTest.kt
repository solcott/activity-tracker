package io.github.solcott.activitytracker.segmentimporter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Reading the committed PBF fixture (`trails.osm`, built with osmium; see the README). */
class OsmReaderTest {
    private val data = OsmReader.read(fixture())

    @Test
    fun keepsTrailWaysMembersAndNamedRoads() {
        val ids = data.ways.map { it.id }.toSet()
        assertTrue(setOf(100L, 101L, 160L).all { it in ids }, "named trail ways")
        assertTrue(setOf(150L, 151L, 152L).all { it in ids }, "the route relation's unnamed members")
        assertTrue(110L in ids, "a named road, for junctions")
        assertTrue(120L !in ids, "an unnamed path outside any relation")
        assertTrue(130L in ids && 140L in ids, "named highways are kept for junctions even when they're no trail")
    }

    @Test
    fun readsWayTagsAndNodesInOrder() {
        val oak = data.ways.single { it.id == 100L }
        assertEquals("Oak Trail", oak.name)
        assertEquals("2", oak.tags["mtb:scale"])
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), oak.nodes.toList())
    }

    @Test
    fun readsRouteRelationsWithTheirMembers() {
        val loop = data.relations.single()
        assertEquals("Ridge Loop", loop.name)
        assertEquals(listOf(150L, 151L, 152L), loop.members.map { it.ref })
        assertTrue(loop.members.all { it.type == OsmMember.Type.WAY })
    }

    @Test
    fun keepsCoordinatesOnlyForTrailNodes() {
        val first = assertNotNull(data.coords[1])
        assertEquals(44.40, first.lat, 1e-6)
        assertEquals(-72.70, first.lng, 1e-6)
        assertNotNull(data.coords[41], "a relation member's node")
        assertNull(data.coords[20], "a road's node")
        assertNull(data.coords[30], "an unnamed path's node")
    }

    @Test
    fun readsTheReplicationTimestamp() {
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), data.timestamp)
    }
}

internal fun fixture(): File = File(requireNotNull(OsmReaderTest::class.java.getResource("/trails.osm.pbf")).toURI())
