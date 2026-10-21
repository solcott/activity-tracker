package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.HIKE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.PADDLE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RIDE
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.RUN
import io.github.solcott.activitytracker.lexicon.app.SegmentSportGroup.SKI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Which ways and relations are trails for which sport group (segments/trail-candidates). */
class TrailTagsTest {
    private fun groups(vararg tags: Pair<String, String>) = TrailTags.wayGroups(tags.toMap())

    @Test
    fun aMountainBikePathIsATrailForRidesRunsAndHikes() {
        assertEquals(setOf(RIDE, RUN, HIKE), groups("highway" to "path", "name" to "Oak", "mtb:scale" to "2"))
    }

    @Test
    fun aFootwayIsClosedToBikesUnlessTaggedOpen() {
        assertEquals(setOf(RUN, HIKE), groups("highway" to "footway", "name" to "Lake Walk"))
        assertEquals(setOf(RIDE, RUN, HIKE), groups("highway" to "footway", "name" to "Lake Walk", "bicycle" to "designated"))
    }

    @Test
    fun aCyclewayIsARideTrail() {
        assertEquals(setOf(RIDE), groups("highway" to "cycleway", "name" to "Rail Trail"))
    }

    @Test
    fun accessTagsCloseAGroup() {
        assertEquals(setOf(RUN, HIKE), groups("highway" to "path", "name" to "Oak", "bicycle" to "no"))
        assertEquals(setOf(RIDE), groups("highway" to "path", "name" to "Oak", "foot" to "no"))
    }

    @Test
    fun scaleTagsMakeATrailWhateverTheHighway() {
        assertEquals(setOf(RUN, HIKE), groups("name" to "Summit Route", "sac_scale" to "mountain_hiking"))
    }

    @Test
    fun aNordicPisteIsASkiTrail() {
        assertEquals(setOf(SKI), groups("name" to "Upper Loop", "piste:type" to "nordic"))
    }

    @Test
    fun anUnnamedPathIsNoTrailOnItsOwn() {
        assertTrue(groups("highway" to "path").isEmpty())
        assertTrue(groups("highway" to "path", "name" to "  ").isEmpty())
    }

    @Test
    fun closedAndUnsuitableWaysAreLeftOut() {
        assertTrue(groups("highway" to "track", "name" to "Ranch Track", "access" to "private").isEmpty(), "private track")
        assertTrue(groups("highway" to "footway", "name" to "Main St", "footway" to "sidewalk").isEmpty(), "sidewalk")
        assertTrue(groups("highway" to "footway", "name" to "Main St", "footway" to "crossing").isEmpty(), "crossing")
        assertTrue(groups("highway" to "residential", "name" to "Elm St", "mtb:scale" to "0").isEmpty(), "road")
        assertTrue(groups("highway" to "steps", "name" to "Stairs").isEmpty(), "steps")
        assertTrue(groups("highway" to "path", "name" to "Arcade", "tunnel" to "building_passage").isEmpty(), "building passage")
        assertTrue(groups("highway" to "path", "name" to "Plaza", "area" to "yes").isEmpty(), "area")
        assertTrue(TrailTags.excluded(mapOf("highway" to "path", "access" to "no")), "excluded inside relations too")
    }

    @Test
    fun routeRelationsAreTrailsForTheirGroups() {
        fun route(kind: String, name: String? = "Route") =
            TrailTags.relationGroups(
                buildMap {
                    put("type", "route")
                    put("route", kind)
                    name?.let { put("name", it) }
                }
            )
        assertEquals(setOf(RIDE), route("mtb"))
        assertEquals(setOf(RIDE), route("bicycle"))
        assertEquals(setOf(RUN, HIKE), route("hiking"))
        assertEquals(setOf(RUN, HIKE), route("foot"))
        assertEquals(setOf(RUN, HIKE), route("running"))
        assertEquals(setOf(SKI), route("ski"))
        assertEquals(setOf(PADDLE), route("canoe"), "a canoe route is a paddle trail and no other group's")
        assertTrue(route("bus").isEmpty())
        assertTrue(route("hiking", name = null).isEmpty(), "unnamed")
    }

    @Test
    fun onewayTagsLimitDirections() {
        assertEquals(true to false, TrailTags.directions(mapOf("oneway" to "yes"), RIDE))
        assertEquals(false to true, TrailTags.directions(mapOf("oneway" to "-1"), RUN))
        assertEquals(true to true, TrailTags.directions(mapOf("oneway" to "yes", "oneway:bicycle" to "no"), RIDE))
        assertEquals(true to false, TrailTags.directions(mapOf("oneway" to "yes", "oneway:bicycle" to "no"), HIKE))
        assertEquals(true to true, TrailTags.directions(emptyMap(), HIKE))
    }
}
