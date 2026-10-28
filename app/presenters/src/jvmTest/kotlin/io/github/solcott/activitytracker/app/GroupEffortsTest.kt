package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.detail.SegmentEffortGroup
import io.github.solcott.activitytracker.app.detail.groupEfforts
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.EffortView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Grouping an activity's efforts by overlapping spans and nesting them (segments/segment-efforts). */
class GroupEffortsTest {
    private val activity = AtUri("at://did:plc:alice/${Nsid.FEED_ACTIVITY}/3kact")

    private fun effort(name: String, start: Long, elapsed: Long) =
        EffortView(
            SegmentViewBasic(AtUri("at://did:plc:importer/${Nsid.FEED_SEGMENT}/${name.hashCode().toUInt()}"), name, "ride", 1000),
            activity,
            startOffset = start,
            elapsed = elapsed,
            startedOn = "2026-10-27",
        )

    /** Each group as its rows, "<indent><label>" with two spaces per depth. */
    private fun shown(groups: List<SegmentEffortGroup>) = groups.map { g -> g.rows.map { "  ".repeat(it.depth) + it.label } }

    private val rattler = effort("Rattler Trail (eastbound)", 100, 908)
    private val rattler1 = effort("Rattler Trail: Bonneville Shoreline Trail to Upper Rattler Connector Trail", 100, 534)
    private val rattler2 = effort("Rattler Trail: Upper Rattler Connector Trail to Ghost Falls Trail", 634, 374)

    @Test
    fun stretchesNestUnderTheirTrail() {
        assertEquals(
            listOf(
                listOf(
                    "Rattler Trail (eastbound)",
                    "  Bonneville Shoreline Trail to Upper Rattler Connector Trail",
                    "  Upper Rattler Connector Trail to Ghost Falls Trail",
                )
            ),
            shown(groupEfforts(listOf(rattler, rattler1, rattler2))),
        )
    }

    @Test
    fun aRoundedEndStillNests() {
        val late = effort("Rattler Trail: Upper Rattler Connector Trail to Ghost Falls Trail", 634, 375)
        assertEquals(listOf(0, 1), groupEfforts(listOf(rattler, late)).single().rows.map { it.depth })
    }

    @Test
    fun aStretchTimedASecondBeforeItsTrailIsListedUnderIt() {
        val limelight = effort("Rush Trail - Limelight Section", 2001, 192)
        val first = effort("Rush Trail - Limelight Section: Bonneville Shoreline Trail - Draper Section to Creek View Foot Path", 2000, 71)
        val second = effort("Rush Trail - Limelight Section: Creek View Foot Path to Lower Corner Canyon Trail", 2072, 120)
        assertEquals(
            listOf(
                listOf(
                    "Rush Trail - Limelight Section",
                    "  Bonneville Shoreline Trail - Draper Section to Creek View Foot Path",
                    "  Creek View Foot Path to Lower Corner Canyon Trail",
                )
            ),
            // In the AppView's order: by start.
            shown(groupEfforts(listOf(first, limelight, second))),
        )
    }

    @Test
    fun stretchesEndToEndAreSeparateGroups() {
        val groups = groupEfforts(listOf(rattler1, rattler2))
        assertEquals(2, groups.size)
        assertNull(groups[0].rows.single().spanStart)
    }

    @Test
    fun segmentsMeetingAtAJunctionAreSeparateGroups() {
        // Each is timed from its own start line to its own end line, so they overlap by a few seconds (the 2026-10-27 ride).
        val ghostFalls = effort("Ghost Falls Trail: Ghost Connector Trail to Corner Canyon Road", 2777, 601)
        val road = effort("Corner Canyon Road: Ghost Falls Trail to Canyon Hollow Trail", 3375, 214)
        val part6 = effort("Corner Canyon Road (part 6)", 3589, 236)
        val peakView = effort("Peak View Trail (Lower) (eastbound)", 3812, 404)
        assertEquals(4, groupEfforts(listOf(ghostFalls, road, part6, peakView)).size)
    }

    @Test
    fun effortsSharingAStretchAreOneGroup() {
        val first = effort("First", 0, 600)
        val second = effort("Second", 300, 600)
        assertEquals(1, groupEfforts(listOf(first, second)).size)
    }

    @Test
    fun aJunctionOverlapDoesNotJoinTwoGroups() {
        // Rattler and its stretches, then a lone effort starting 3 s before Rattler ends: two groups, not one.
        val next = effort("Ghost Falls Trail: Ghost Connector Trail to Corner Canyon Road", 1005, 601)
        assertEquals(listOf(3, 1), groupEfforts(listOf(rattler, rattler1, rattler2, next)).map { it.rows.size })
    }

    @Test
    fun equalSpansAreSiblings() {
        val twin = effort("Another Trail", 100, 908)
        assertEquals(listOf(0, 0), groupEfforts(listOf(rattler, twin)).single().rows.map { it.depth })
    }

    @Test
    fun overlappingSiblingsStayAtOneDepth() {
        val first = effort("First", 0, 600)
        val second = effort("Second", 300, 600)
        assertEquals(listOf(listOf("First", "Second")), shown(groupEfforts(listOf(first, second))))
    }

    @Test
    fun aShorterEffortStartingTogetherNests() {
        val toCanyon = effort("Rush Trail via Exit Stage Left Section to Canyon Hollow Trail", 500, 492)
        val toShoreline = effort("Rush Trail via Exit Stage Left Section to Bonneville Shoreline Trail", 500, 574)
        val section = effort("Rush Trail - Exit Stage Left Section", 520, 89)
        assertEquals(
            listOf(
                listOf(
                    "Rush Trail via Exit Stage Left Section to Bonneville Shoreline Trail",
                    "  Rush Trail via Exit Stage Left Section to Canyon Hollow Trail",
                    "    Exit Stage Left Section",
                )
            ),
            // In the AppView's order: by start.
            shown(groupEfforts(listOf(toCanyon, toShoreline, section))),
        )
    }

    @Test
    fun twoLapsAreTwoGroups() {
        val lap1 = effort("Loop", 0, 300)
        val lap2 = effort("Loop", 300, 310)
        assertEquals(2, groupEfforts(listOf(lap1, lap2)).size)
    }

    @Test
    fun spansAreFractionsOfTheGroup() {
        val rows = groupEfforts(listOf(rattler, rattler2)).single().rows
        assertEquals(listOf(0f to 1f, 534f / 908 to 1f), rows.map { it.spanStart to it.spanEnd })
    }

    @Test
    fun guidesFollowThreeLevels() {
        // a ─┬─ b ─┬─ c
        //    │     └─ d
        //    └─ e
        val a = effort("A", 0, 1000)
        val b = effort("B", 0, 600)
        val c = effort("C", 0, 300)
        val d = effort("D", 300, 300)
        val e = effort("E", 600, 400)
        val rows = groupEfforts(listOf(a, b, c, d, e)).single().rows
        assertEquals(listOf("A", "B", "C", "D", "E"), rows.map { it.label })
        assertEquals(listOf(0, 1, 2, 2, 1), rows.map { it.depth })
        assertEquals(listOf(false, false, false, true, true), rows.map { it.lastChild })
        // C and D sit under B, which has a later sibling E, so A's line runs past them.
        assertEquals(listOf(emptyList(), emptyList(), listOf(true), listOf(true), emptyList()), rows.map { it.guides.toList() })
    }

    @Test
    fun aLastChildsChildrenHaveNoGuideForIt() {
        val a = effort("A", 0, 1000)
        val b = effort("B", 500, 500)
        val c = effort("C", 600, 300)
        assertEquals(
            listOf(emptyList(), emptyList(), listOf(false)),
            groupEfforts(listOf(a, b, c)).single().rows.map { it.guides.toList() },
        )
    }

    @Test
    fun nestedNamesDropTheirParentsTrail() {
        val ghost = effort("Ghost Falls Trail: Ghost Connector Trail to Corner Canyon Road", 200, 300)
        val labels = groupEfforts(listOf(rattler, ghost)).single().rows.map { it.label }
        assertEquals(listOf("Rattler Trail (eastbound)", "Ghost Falls Trail: Ghost Connector Trail to Corner Canyon Road"), labels)
    }

    @Test
    fun aSectionUnderAParentTrailDropsTheTrail() {
        val parent = effort("Rush Trail via Exit Stage Left Section to Canyon Hollow Trail", 500, 492)
        val section = effort("Rush Trail - Exit Stage Left Section", 520, 89)
        assertEquals(
            listOf("Rush Trail via Exit Stage Left Section to Canyon Hollow Trail", "Exit Stage Left Section"),
            groupEfforts(listOf(parent, section)).single().rows.map { it.label },
        )
    }

    @Test
    fun shortenedNamesNestUnderTheirTrail() {
        fun labels(parent: String, child: String) =
            groupEfforts(listOf(effort(parent, 500, 492), effort(child, 520, 89))).single().rows.map { it.label }
        assertEquals(
            listOf("Rattler (eastbound)", "Upper Rattler Connector → Ghost Falls"),
            labels("Rattler (eastbound)", "Rattler: Upper Rattler Connector → Ghost Falls"),
        )
        assertEquals(
            listOf("Rush › Exit Stage Left → Canyon Hollow", "Exit Stage Left"),
            labels("Rush › Exit Stage Left → Canyon Hollow", "Rush - Exit Stage Left"),
        )
        assertEquals(
            listOf("Rush from Mystic Rhythms → Canyon Hollow", "2112"),
            labels("Rush from Mystic Rhythms → Canyon Hollow", "Rush - 2112"),
        )
        assertEquals(listOf("Rush → Bonneville Shoreline", "Dreamline"), labels("Rush → Bonneville Shoreline", "Rush - Dreamline"))
        assertEquals(
            listOf("Rattler (eastbound)", "Ghost Falls: Ghost Connector → Corner Canyon Road"),
            labels("Rattler (eastbound)", "Ghost Falls: Ghost Connector → Corner Canyon Road"),
        )
    }
}
