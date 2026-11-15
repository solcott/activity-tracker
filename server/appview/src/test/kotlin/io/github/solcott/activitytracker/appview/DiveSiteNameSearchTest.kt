package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSiteNames
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.ktor.server.testing.testApplication
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/** `feed.searchDiveSiteNames` (dive-sites/site-records): sites found by their current name, ranked, then nearest first. */
class DiveSiteNameSearchTest : DiveSiteTestBase() {
    private fun activate(vararg rkeys: String) = rkeys.forEach { rkey -> setSiteStatus(siteUri(rkey), SegmentStatus.ACTIVE) }

    private fun rename(rkey: String, name: String) = runBlocking {
        db.tx { DiveSites.update({ DiveSites.uri eq siteUri(rkey) }) { it[DiveSites.currentName] = name } }
    }

    private fun e7(value: Double) = "${(value * 1e7).roundToInt()}"

    private fun near(east: Double): Map<String, String> = at(east, 0.0).let { mapOf("nearLat" to e7(it.lat), "nearLng" to e7(it.lng)) }

    private fun box(fromEast: Double, toEast: Double): Map<String, String> {
        val sw = at(fromEast, -100.0)
        val ne = at(toEast, 100.0)
        return mapOf("minLat" to e7(sw.lat), "minLng" to e7(sw.lng), "maxLat" to e7(ne.lat), "maxLng" to e7(ne.lng))
    }

    private fun uris(vararg rkeys: String) = rkeys.map { siteUri(it) }

    @Test
    fun namesRankByMatchThenPage() = testApplication {
        application { appView(config, db, verifier) }
        // 300 m apart, so none duplicates another.
        site("3ka", east = 0.0, name = "Hole in the Wall")
        site("3kb", east = 300.0, name = "Blue Hole")
        site("3kc", east = 600.0, name = "Wholesome Reef")
        site("3kd", east = 900.0, name = "Hole")
        site("3ke", east = 1200.0, name = "Ridge")
        activate("3ka", "3kb", "3kc", "3kd", "3ke")
        suspend fun found(query: Map<String, String>) = client.xrpcOk(SearchDiveSiteNames.method, query)

        val all = found(mapOf("q" to "HOLE"))
        assertEquals(uris("3kd", "3ka", "3kb", "3kc"), all.sites.map { it.uri.value }, "whole, prefix, word, anywhere")
        assertNull(all.cursor)

        val first = found(mapOf("q" to "hole", "limit" to "2"))
        assertEquals(uris("3kd", "3ka"), first.sites.map { it.uri.value })
        val second = found(mapOf("q" to "hole", "limit" to "2", "cursor" to first.cursor!!))
        assertEquals(uris("3kb", "3kc"), second.sites.map { it.uri.value })

        assertEquals(emptyList(), found(mapOf("q" to "   ")).sites)
    }

    @Test
    fun withinAMatchNearestFirstAndOnlyInTheBox() = testApplication {
        application { appView(config, db, verifier) }
        site("3ka", east = 0.0, name = "Reef North")
        site("3kb", east = 3000.0, name = "Reef South")
        site("3kc", east = 6000.0, name = "Reef East")
        activate("3ka", "3kb", "3kc")
        suspend fun found(query: Map<String, String>) = client.xrpcOk(SearchDiveSiteNames.method, query).sites.map { it.uri.value }

        assertEquals(uris("3kc", "3kb", "3ka"), found(mapOf("q" to "reef") + near(6000.0)))
        assertEquals(uris("3kc", "3kb"), found(mapOf("q" to "reef") + near(6000.0) + box(2000.0, 7000.0)), "3ka is outside the box")
        assertEquals(uris("3kc", "3ka", "3kb"), found(mapOf("q" to "reef")), "by name without a centre")
    }

    @Test
    fun currentNameNotOriginalAndProposedOnlyToCreator() = testApplication {
        application { appView(config, db, verifier) }
        site("3ka", east = 0.0, name = "Reef 7")
        site("3kb", east = 300.0, name = "Shark Point", did = bob)
        site("3kc", east = 30.0, name = "Shark Alley", did = bob)
        activate("3ka")
        rename("3ka", "Shark Alley")
        suspend fun found(q: String, viewer: String?) =
            client.xrpcOk(SearchDiveSiteNames.method, mapOf("q" to q), viewer).sites.map { it.uri.value }

        assertEquals(uris("3ka"), found("shark alley", null), "the adopted name; bob's duplicate 30 m away is left out")
        assertEquals(emptyList(), found("reef 7", null), "not the original")
        assertEquals(uris("3ka"), found("shark", alice), "bob's proposed site isn't listed to alice")
        assertEquals(setOf(siteUri("3ka"), siteUri("3kb", bob)), found("shark", bob).toSet(), "his own proposed site")
    }

    @Test
    fun partialBoxOrCentreIsInvalid() = testApplication {
        application { appView(config, db, verifier) }
        assertEquals("InvalidRequest", client.xrpcError(SearchDiveSiteNames.method, mapOf("q" to "reef", "minLat" to "0")))
        assertEquals("InvalidRequest", client.xrpcError(SearchDiveSiteNames.method, mapOf("q" to "reef", "nearLat" to "0")))
    }
}
