package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.PathKind
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Candidates know the family section they're named after (segments/trail-candidates), and paths list them (segments/path-import). */
class TrailSectionsTest {
    /**
     * Rush: Tom Sawyer, then two branches (Exit Stage Left, Through the Trees), a plain "Rush Trail" trunk, and 2112, with Canyon Hollow at
     * the fork's end.
     */
    private val rush = osm {
        for (i in 0..1) node(i + 1L, north(44.4, i * 500.0), -72.7)
        node(3, north(44.4, 1000.0), -72.69)
        node(4, north(44.4, 1000.0), -72.71)
        for (i in 0..2) node(i + 5L, north(44.4, 1500.0 + i * 500.0), -72.7)
        way(10, 1, 2, tags = path("Rush Trail - Tom Sawyer Section", "oneway" to "yes"))
        way(11, 2, 3, 5, tags = path("Rush Trail - Exit Stage Left Section", "oneway" to "yes"))
        way(12, 2, 4, 5, tags = path("Rush Trail - Through the Trees Trail Section", "oneway" to "yes"))
        way(13, 5, 6, tags = path("Rush Trail", "oneway" to "yes"))
        way(14, 6, 7, tags = path("Rush Trail - 2112 Section", "oneway" to "yes"))
        node(30, 0.0, 0.0)
        way(30, 30, 5, tags = path("Canyon Hollow Trail"))
    }

    private val kept = candidatesOf(rush, ClosureFilter(emptyList())).filter { it.group == RIDE }

    private fun sectionOf(name: String): String? = kept.single { it.name == name }.section

    @Test
    fun aBranchItsOwnSpansAndItsSectionTrailShareTheSection() {
        assertEquals("Through the Trees Trail", sectionOf("Rush › Through the Trees Trail"))
        assertEquals("Through the Trees Trail", sectionOf("Rush › Through the Trees Trail → Canyon Hollow"))
        assertEquals("Exit Stage Left", sectionOf("Rush › Exit Stage Left"))
        assertEquals("Exit Stage Left", sectionOf("Rush › Exit Stage Left → Canyon Hollow"))
        val sectionTrail = kept.filter { it.kind == TrailKind.NAMED && it.name.startsWith("Rush - Through the Trees Trail") }
        assertTrue(sectionTrail.isNotEmpty(), kept.map { it.name }.toString())
        assertTrue(sectionTrail.all { it.section == "Through the Trees Trail" }, sectionTrail.map { it.name to it.section }.toString())
        assertTrue(kept.filter { it.name.startsWith("Rush - 2112") }.all { it.section == "2112" })
    }

    @Test
    fun aSpanNamedAfterTheFamilyAndAPlainTrailHaveNoSection() {
        assertNull(sectionOf("Rush from Canyon Hollow"))
        assertTrue(kept.filter { it.name.startsWith("Canyon Hollow") }.all { it.section == null })
    }

    @Test
    fun theFamilysPathListsItsSectionsWithTheirKeptIds() {
        val all = candidatesOf(rush, ClosureFilter(emptyList()))
        val path = pathsOf(rush, candidates = all).single { it.kind == PathKind.TRAIL && it.name == "Rush Trail" }
        val sections = requireNotNull(path.sections)
        assertEquals(sections.map { it.name }.sorted(), sections.map { it.name }, "by name")
        val expected = all.filter { it.section != null && it.family == "Rush Trail" }.groupBy({ it.section!! }, { it.id })
        assertEquals(expected.mapValues { (_, ids) -> ids.sorted() }, sections.associate { it.name to it.segments })
        assertNull(pathsOf(rush, candidates = all).single { it.name == "Canyon Hollow Trail" }.sections, "a plain trail has none")
    }

    @Test
    fun anUnbranchedFamilysParentTrailHasNoSection() {
        val data = osm {
            for (i in 0..2) node(i + 1L, north(44.4, i * 800.0), -72.7)
            way(10, 1, 2, tags = path("Rush Trail - Tom Sawyer Section", "oneway" to "yes"))
            way(11, 2, 3, tags = path("Rush Trail - 2112 Section", "oneway" to "yes"))
        }
        val candidates = candidatesOf(data, ClosureFilter(emptyList())).filter { it.group == RIDE }
        assertTrue(candidates.filter { it.kind == TrailKind.PARENT }.all { it.section == null })
        assertTrue(candidates.filter { it.kind == TrailKind.NAMED }.all { it.section != null })
    }
}
