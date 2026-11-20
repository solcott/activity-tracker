package io.github.solcott.activitytracker.segmentimporter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** Ways taking access, operator and website tags from their named route relations (segments/trail-attributes). */
class RouteInheritanceTest {
    private fun route(name: String, vararg tags: Pair<String, String>) = mapOf("type" to "route", "route" to "mtb", "name" to name, *tags)

    private fun OsmBuilder.twoWays() {
        node(1, 44.4, -72.0)
        node(2, north(44.4, 500.0), -72.0)
        node(3, north(44.4, 1_000.0), -72.0)
        way(10, 1, 2, tags = path("Loop"))
        way(11, 2, 3, tags = path("Loop", "website" to "https://own.example"))
    }

    @Test
    fun aWayTakesItsRoutesWebsite() {
        val data = osm {
            twoWays()
            relation(1, 10, 11, tags = route("Kingdom Trails Loop", "website" to "https://kingdomtrails.org", "dog" to "leashed"))
        }
        val ways = RouteInheritance.applied(data).ways.associateBy { it.id }
        assertEquals("https://kingdomtrails.org", TrailTags.facts(ways.getValue(10).tags, "ride").website)
        assertEquals("https://own.example", ways.getValue(11).tags["website"], "a way's own tag stands")
        assertEquals("leashed", TrailTags.facts(ways.getValue(11).tags, "ride").dog)
    }

    @Test
    fun routesThatDisagreeGiveNothing() {
        val data = osm {
            twoWays()
            relation(1, 10, tags = route("North Loop", "operator" to "Kingdom Trail Association"))
            relation(2, 10, tags = route("South Loop", "operator" to "Town of Burke"))
            relation(3, 10, tags = route("East Loop"))
        }
        assertEquals(null, RouteInheritance.applied(data).ways.single { it.id == 10L }.tags["operator"])
    }

    @Test
    fun anUnnamedRouteGivesNothing() {
        val data = osm {
            twoWays()
            relation(1, 10, tags = mapOf("type" to "route", "route" to "mtb", "dog" to "no"))
        }
        assertSame(data, RouteInheritance.applied(data))
    }
}
