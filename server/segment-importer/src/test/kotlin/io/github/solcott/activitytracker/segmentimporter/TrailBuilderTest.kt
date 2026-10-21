package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RUN
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Joining named ways and route relations into trails (segments/trail-candidates). */
class TrailBuilderTest {
    private fun OsmBuilder.column(ids: LongRange, lng: Double, spacing: Double = 250.0) = ids.forEachIndexed { i, id ->
        node(id, north(44.4, i * spacing), lng)
    }

    @Test
    fun aTrailMappedAsSeveralWaysIsOneTrail() {
        val data = osm {
            column(1L..9L, -72.7)
            // Four ways, the third drawn against the others.
            way(10, 1, 2, 3, tags = path("Oak Trail"))
            way(11, 3, 4, 5, tags = path("Oak Trail"))
            way(12, 7, 6, 5, tags = path("Oak Trail"))
            way(13, 7, 8, 9, tags = path("Oak Trail"))
        }
        val oak = TrailBuilder.build(data).single { it.group == RIDE }
        assertEquals("Oak Trail", oak.name)
        assertEquals((1L..9L).toList(), oak.nodes.toList().let { if (it.first() == 1L) it else it.reversed() })
        assertEquals(setOf(10L, 11L, 12L, 13L), oak.ways)
    }

    @Test
    fun aTrailEndsWhereThreeWaysOfItMeet() {
        val data = osm {
            column(1L..3L, -72.7)
            node(4, north(44.4, 750.0), -72.69)
            node(5, north(44.4, 750.0), -72.71)
            way(10, 1, 2, 3, tags = path("Oak Trail"))
            way(11, 3, 4, tags = path("Oak Trail"))
            way(12, 3, 5, tags = path("Oak Trail"))
        }
        assertEquals(3, TrailBuilder.build(data).count { it.group == RIDE }, "a fork never picks a branch")
    }

    @Test
    fun aNamedRouteOverUnnamedPathsIsOneTrailNamedByTheRoute() {
        val data = osm {
            column(40L..43L, -72.68, spacing = 300.0)
            way(150, 40, 41, tags = path(null))
            way(151, 42, 41, tags = path(null))
            way(152, 42, 43, tags = path(null))
            relation(200, 150, 151, 152, tags = mapOf("type" to "route", "route" to "hiking", "name" to "Ridge Loop"))
        }
        val trails = TrailBuilder.build(data)
        assertEquals(setOf(RUN, HIKE), trails.map { it.group }.toSet())
        trails.forEach { trail ->
            assertEquals("Ridge Loop", trail.name)
            assertTrue(trail.fromRelation)
            assertEquals(listOf(40L, 41L, 42L, 43L), trail.nodes.toList())
            assertEquals(listOf(150L, 151L, 152L), trail.edgeWays.toList())
        }
    }

    @Test
    fun aRelationWhoseFirstWayRunsBackwardsIsTurnedAround() {
        val data = osm {
            column(1L..3L, -72.7)
            way(10, 2, 1, tags = path(null))
            way(11, 2, 3, tags = path(null))
            relation(200, 10, 11, tags = mapOf("type" to "route", "route" to "mtb", "name" to "Loop"))
        }
        assertEquals(listOf(1L, 2L, 3L), TrailBuilder.build(data).single().nodes.toList())
    }

    @Test
    fun aRelationWithAGapGivesATrailPerJoinedStretch() {
        val data = osm {
            column(1L..6L, -72.7)
            way(10, 1, 2, tags = path(null))
            way(11, 2, 3, tags = path(null))
            way(12, 4, 5, tags = path(null))
            way(13, 5, 6, tags = path(null))
            relation(200, 10, 11, 12, 13, tags = mapOf("type" to "route", "route" to "mtb", "name" to "Gappy Route"))
        }
        val trails = TrailBuilder.build(data)
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(4L, 5L, 6L)), trails.map { it.nodes.toList() })
        assertTrue(trails.all { it.name == "Gappy Route" })
    }

    @Test
    fun excludedMembersAreLeftOutOfARelation() {
        val data = osm {
            column(1L..4L, -72.7)
            way(10, 1, 2, tags = path(null))
            way(11, 2, 3, tags = path(null, "access" to "private"))
            way(12, 3, 4, tags = path(null))
            relation(200, 10, 11, 12, tags = mapOf("type" to "route", "route" to "mtb", "name" to "Route"))
        }
        assertEquals(listOf(listOf(1L, 2L), listOf(3L, 4L)), TrailBuilder.build(data).map { it.nodes.toList() })
    }

    @Test
    fun edgesCarryTheirWaysDirections() {
        val data = osm {
            column(1L..3L, -72.7)
            way(10, 1, 2, tags = path("Plunge", "oneway" to "yes"))
            way(11, 3, 2, tags = path("Plunge"))
        }
        val plunge = TrailBuilder.build(data).first { it.group == RIDE }
        assertEquals(listOf(1L, 2L, 3L), plunge.nodes.toList())
        assertEquals(listOf(true, true), plunge.forward.toList())
        assertEquals(listOf(false, true), plunge.backward.toList())
    }
}
