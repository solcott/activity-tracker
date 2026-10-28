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
        assertEquals("Oak", oak.label, "without its generic word")
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
            assertEquals(TrailKind.RELATION, trail.kind)
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

    private fun down(name: String) = path(name, "oneway" to "yes")

    private fun parents(data: OsmData) = TrailBuilder.build(data).filter { it.group == RIDE && it.kind == TrailKind.PARENT }

    @Test
    fun sectionsMeetingEndToEndAlsoFormTheirParentTrail() {
        val data = osm {
            column(1L..4L, -72.7)
            way(10, 1, 2, tags = down("Rush Trail - Tom Sawyer Section"))
            way(11, 2, 3, tags = down("Rush Trail"))
            way(12, 3, 4, tags = down("Rush Trail - 2112 Section"))
        }
        val rush = parents(data).single()
        assertEquals("Rush Trail", rush.name)
        assertEquals(listOf(1L, 2L, 3L, 4L), rush.nodes.toList())
        assertEquals(listOf(true, true, true), rush.forward.toList())
        assertEquals(listOf(false, false, false), rush.backward.toList())
        assertEquals(3, TrailBuilder.build(data).count { it.group == RIDE && it.kind == TrailKind.NAMED }, "each section is still a trail")
    }

    @Test
    fun aFamilyOfOneNameHasNoParentTrail() {
        val data = osm {
            column(1L..3L, -72.7)
            way(10, 1, 2, tags = path("Oak Trail - Upper Section"))
            way(11, 2, 3, tags = path("Oak Trail - Upper Section"))
        }
        assertEquals(emptyList(), parents(data))
    }

    @Test
    fun aTwoWayFamilyIsOneParentTrailBothWays() {
        val data = osm {
            column(1L..3L, -72.7)
            way(10, 1, 2, tags = path("Ridge Trail - North Section"))
            way(11, 3, 2, tags = path("Ridge Trail - South Section"))
        }
        val ridge = parents(data).single()
        assertEquals("Ridge Trail", ridge.name)
        assertTrue(ridge.forward.all { it } && ridge.backward.all { it })
    }

    @Test
    fun aForkThatRejoinsGivesAParentTrailPerBranch() {
        val data = osm {
            column(1L..2L, -72.7)
            node(3, north(44.4, 500.0), -72.695)
            node(4, north(44.4, 500.0), -72.705)
            node(5, north(44.4, 750.0), -72.7)
            node(6, north(44.4, 1000.0), -72.7)
            way(10, 1, 2, tags = down("Rush Trail - Tom Sawyer Section"))
            way(11, 2, 3, 5, tags = down("Rush Trail - Exit Stage Left Section"))
            way(12, 2, 4, 5, tags = down("Rush Trail - Through the Trees Trail Section"))
            way(13, 5, 6, tags = down("Rush Trail"))
        }
        assertEquals(
            setOf("Rush Trail via Exit Stage Left Section", "Rush Trail via Through the Trees Trail Section"),
            parents(data).map { it.name }.toSet(),
        )
        assertEquals(setOf("Rush › Exit Stage Left", "Rush › Through the Trees Trail"), parents(data).map { it.label }.toSet())
        assertEquals(setOf(setOf(11L), setOf(12L)), parents(data).map { it.ownWays }.toSet(), "only each branch is its own")
        assertTrue(parents(data).all { it.family == "Rush Trail" })
    }

    @Test
    fun anEntryJoiningPartwayDownIsNamedFromItsFirstSection() {
        val data = osm {
            column(1L..2L, -72.7)
            node(3, north(44.4, 500.0), -72.695)
            node(4, north(44.4, 500.0), -72.705)
            node(5, north(44.4, 750.0), -72.7)
            node(6, north(44.4, 1000.0), -72.7)
            node(7, north(44.4, 750.0), -72.69)
            way(10, 1, 2, tags = down("Rush Trail - Tom Sawyer Section"))
            way(11, 2, 3, 5, tags = down("Rush Trail - Exit Stage Left Section"))
            way(12, 2, 4, 5, tags = down("Rush Trail - Through the Trees Trail Section"))
            way(13, 5, 6, tags = down("Rush Trail - Mystic Rhythms Section"))
            // A bare "Rush Trail" way joining at the top of Mystic Rhythms from elsewhere.
            way(14, 7, 5, tags = down("Rush Trail"))
        }
        assertEquals(
            setOf(
                "Rush Trail via Exit Stage Left Section",
                "Rush Trail via Through the Trees Trail Section",
                "Rush Trail from Mystic Rhythms Section",
            ),
            parents(data).map { it.name }.toSet(),
        )
        assertEquals(
            setOf("Rush › Exit Stage Left", "Rush › Through the Trees Trail", "Rush from Mystic Rhythms"),
            parents(data).map { it.label }.toSet(),
        )
    }

    @Test
    fun aFamilyWithTooManyPathsKeepsOnlyItsLongest() {
        val data = osm {
            column(1L..2L, -72.7)
            // Three ways from 2 to 6, one by a detour to the east, then two ways from 6 to 9: six paths.
            node(3, north(44.4, 500.0), -72.69)
            node(4, north(44.4, 500.0), -72.7)
            node(5, north(44.4, 500.0), -72.71)
            node(6, north(44.4, 750.0), -72.7)
            node(7, north(44.4, 1000.0), -72.699)
            node(8, north(44.4, 1000.0), -72.701)
            node(9, north(44.4, 1250.0), -72.7)
            way(10, 1, 2, tags = down("Rush Trail - Tom Sawyer Section"))
            way(11, 2, 3, 6, tags = down("Rush Trail - East Section"))
            way(12, 2, 4, 6, tags = down("Rush Trail - Middle Section"))
            way(13, 2, 5, 6, tags = down("Rush Trail - West Section"))
            way(14, 6, 7, 9, tags = down("Rush Trail - Left Section"))
            way(15, 6, 8, 9, tags = down("Rush Trail - Right Section"))
        }
        val longest = parents(data).single()
        assertEquals("Rush Trail", longest.name)
        assertTrue(11L in longest.ways, "the detour east is the longest")
    }

    @Test
    fun aWayMeetingAFamilyMidWayIsOnNoParentTrail() {
        val data = osm {
            column(1L..4L, -72.7)
            node(5, north(44.4, 500.0), -72.69)
            way(10, 1, 2, tags = down("Rush Trail - Dreamline Section"))
            way(11, 2, 3, 4, tags = down("Rush Trail - Limelight Section"))
            // A second Limelight way, a feeder that ends in the middle of the main one.
            way(12, 5, 3, tags = down("Rush Trail - Limelight Section"))
        }
        val rush = parents(data).single()
        assertEquals(listOf(1L, 2L, 3L, 4L), rush.nodes.toList())
        assertEquals(setOf(10L, 11L), rush.ways)
    }
}
