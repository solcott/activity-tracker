package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.api.PdsLookup
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.GearTotals
import io.github.solcott.activitytracker.lexicon.app.GetActorGear
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.activitytracker.lexicon.app.GetGearActivities
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** How an item's usage is totalled and what each viewer's totals count (`gear/gear-usage`). */
class GearUsageTest : GearTestBase() {
    private val tarmac = gearUri("3tarmac")

    private fun ApplicationTestBuilder.start() {
        application { appView(config.copy(signingKey = TEST_SIGNING_KEY), db, verifier, PdsLookup { "http://pds.test" }) }
        // Carol follows Alice; Dave is a stranger.
        follow(carol, alice)
    }

    private suspend fun HttpClient.call(method: XrpcQuery<*, *>, query: String, viewer: String?): HttpResponse =
        get("/xrpc/${method.nsid}?$query") {
            viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, method.nsid)}") }
        }

    private suspend fun <O> HttpClient.ok(method: XrpcQuery<*, O>, query: String, viewer: String?): O {
        val response = call(method, query, viewer)
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return AtJson.decodeFromString(method.output, response.bodyAsText())
    }

    private suspend fun HttpClient.gear(uri: String = tarmac, viewer: String? = alice) =
        ok(GetGear.method, "uri=${uri.encodeURLParameter()}", viewer)

    private suspend fun HttpClient.totals(uri: String = tarmac, viewer: String? = alice) = gear(uri, viewer).usage.totals

    private suspend fun HttpClient.activities(uri: String = tarmac, viewer: String? = alice, limit: Int = 30, cursor: String? = null) =
        ok(
            GetGearActivities.method,
            "uri=${uri.encodeURLParameter()}&limit=$limit" + cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty(),
            viewer,
        )

    @Test
    fun shoesOverThreeRunsOnTwoDates() = testApplication {
        start()
        item("3shoes", Visibility.PUBLIC, "Pegasus", type = "shoes")
        val shoes = listOf(gearUri("3shoes"))
        activity("3r1", sport = "run", start = "2026-10-05T07:00:00Z", stats = """"distance":10000,"movingTime":3000,""", gear = shoes)
        activity("3r2", sport = "run", start = "2026-10-05T18:00:00Z", stats = """"distance":12000,"movingTime":3600,""", gear = shoes)
        activity("3r3", sport = "run", start = "2026-10-07T07:00:00Z", stats = """"distance":8000,"movingTime":2400,""", gear = shoes)

        val totals = client.totals(gearUri("3shoes"))
        assertEquals(3, totals.activities)
        assertEquals(30_000, totals.distance)
        assertEquals(9_000, totals.movingTime)
        assertEquals(2, totals.days)
        assertNull(totals.dives)
    }

    @Test
    fun daysUsedFollowTheLocalDate() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        // 23:30 UTC on the 4th and 07:00 UTC on the 5th are both the 5th at UTC+2.
        activity("3a", start = "2026-10-04T23:30:00Z", stats = """"utcOffset":120,""", gear = listOf(tarmac))
        activity("3b", start = "2026-10-05T07:00:00Z", stats = """"utcOffset":120,""", gear = listOf(tarmac))

        assertEquals(1, client.totals().days)
    }

    @Test
    fun aManualSkiDayWithoutMovingTimeAddsItsElapsedTimeAndSkisTotalTheirVertical() = testApplication {
        start()
        item("3skis", Visibility.PUBLIC, "Enforcer", type = "ski")
        val skis = listOf(gearUri("3skis"))
        activity("3s1", sport = "ski", start = "2026-01-10T09:00:00Z", elapsed = 18_000, stats = """"elevationLoss":3000,""", gear = skis)
        activity(
            "3s2",
            sport = "ski",
            start = "2026-01-11T09:00:00Z",
            elapsed = 20_000,
            stats = """"movingTime":10000,"elevationLoss":4500,""",
            gear = skis,
        )

        val totals = client.totals(gearUri("3skis"))
        assertEquals(28_000, totals.movingTime)
        assertEquals(7_500, totals.elevationLoss)
        assertEquals(2, totals.days)
    }

    @Test
    fun diveGearTotalsDivesAndBottomTime() = testApplication {
        start()
        item("3reg", Visibility.PUBLIC, "Regulator", type = "diveGear")
        val reg = listOf(gearUri("3reg"))
        activity("3d1", sport = "dive", stats = """"dive":{"bottomTime":2400},""", gear = reg)
        activity("3d2", sport = "dive", start = "2026-10-06T07:00:00Z", stats = """"dive":{"bottomTime":3000},""", gear = reg)
        activity("3sn", sport = "snorkel", start = "2026-10-07T07:00:00Z", gear = reg)

        val totals = client.totals(gearUri("3reg"))
        assertEquals(3, totals.activities)
        assertEquals(2, totals.dives)
        assertEquals(5_400, totals.bottomTime)
    }

    @Test
    fun aCombinedActivityCountsOnceAndItsRecordingsCountAgainAfterASplit() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        val ride = """"distance":40000,"""
        activity("3edge", stats = ride, gear = listOf(tarmac))
        activity("3watch", stats = ride, gear = listOf(tarmac))
        val members = """"combined":{"members":["${activityUri("3edge")}","${activityUri("3watch")}"],"sources":[]},"""
        activity("3both", stats = ride + members, gear = listOf(tarmac))

        assertEquals(40_000, client.totals().distance)
        assertEquals(listOf(activityUri("3both")), client.activities().activities.map { it.uri.value })

        remove(Nsid.FEED_ACTIVITY, Visibility.PUBLIC, "3both")

        assertEquals(80_000, client.totals().distance)
        assertEquals(2, client.activities().activities.size)
    }

    @Test
    fun breakdownsListEachTypeAndYearWithoutStartingUsage() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac", extra = """"startingDistance":2000000,"startingTime":360000,""")
        activity("3road", start = "2025-06-01T07:00:00Z", stats = """"subSport":"road","distance":100000,""", gear = listOf(tarmac))
        activity("3c1", start = "2026-03-01T07:00:00Z", stats = """"subSport":"commute","distance":25000,""", gear = listOf(tarmac))
        activity("3c2", start = "2026-03-02T07:00:00Z", stats = """"subSport":"commute","distance":25000,""", gear = listOf(tarmac))

        val usage = client.gear().usage
        assertEquals(2_150_000, usage.totals.distance)
        assertEquals(360_000 + 3 * 3600L, usage.totals.movingTime)
        assertEquals(2_000_000, usage.starting?.distance)
        assertEquals(listOf("commute" to 50_000L, "road" to 100_000L), usage.bySport.map { it.subSport to it.totals.distance })
        assertEquals(listOf(2026 to 50_000L, 2025 to 100_000L), usage.byYear.map { it.year to it.totals.distance })
    }

    @Test
    fun totalsFollowRemovedGearAndStayWhenTheItemRetires() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        activity("3a", stats = """"distance":50000,""", gear = listOf(tarmac))
        activity("3b", start = "2026-10-06T07:00:00Z", stats = """"distance":30000,""", gear = listOf(tarmac))

        item("3tarmac", Visibility.PUBLIC, "Tarmac", extra = """"retiredAt":"2026-10-20T00:00:00Z",""")
        assertEquals(80_000, client.totals().distance)
        assertTrue(client.gear().retired)

        activity("3a", stats = """"distance":50000,""")
        assertEquals(30_000, client.totals().distance)
    }

    @Test
    fun theAthleteCountsEverythingAndAFollowerOnlyWhatTheyMaySee() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        activity("3r1", stats = """"distance":40000,""", gear = listOf(tarmac))
        activity("3r2", start = "2026-10-06T07:00:00Z", stats = """"distance":40000,""", gear = listOf(tarmac))
        activity("3commute", tier = Visibility.SELF, start = "2026-10-07T07:00:00Z", stats = """"distance":10000,""", gear = listOf(tarmac))

        assertEquals(90_000, client.totals(viewer = alice).distance)
        assertEquals(80_000, client.totals(viewer = carol).distance)
        assertEquals(80_000, client.totals(viewer = null).distance)
        assertEquals(2, client.activities(viewer = carol).activities.size)
        assertEquals(Visibility.PUBLIC, client.gear(viewer = alice).visibility)
        assertNull(client.gear(viewer = carol).visibility)
    }

    @Test
    fun aGearCategoryNarrowerThanTheRideCountsOnlyForThoseItAdmits() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        activity("3ride", stats = """"distance":40000,""")
        details("3ride", Visibility.FOLLOWERS, listOf(tarmac))

        assertEquals(1, client.totals(viewer = carol).activities)
        assertEquals(GearTotals.ZERO, client.totals(viewer = dave))
        assertEquals(emptyList(), client.activities(viewer = dave).activities)
    }

    @Test
    fun aStrangerAskingForAnOnlyMeItemGetsNotFound() = testApplication {
        start()
        item("3meter", Visibility.SELF, "Power meter", type = "sensor")
        activity("3ride", gear = listOf(gearUri("3meter")))

        for (viewer in listOf(dave, carol, null)) {
            for (response in
                listOf(
                    client.call(GetGear.method, "uri=${gearUri("3meter").encodeURLParameter()}", viewer),
                    client.call(GetGearActivities.method, "uri=${gearUri("3meter").encodeURLParameter()}", viewer),
                )) {
                assertEquals(HttpStatusCode.BadRequest, response.status, viewer)
                assertTrue("NotFound" in response.bodyAsText(), viewer)
            }
        }
        assertEquals(1, client.totals(gearUri("3meter"), alice).activities)
    }

    @Test
    fun startingUsageIsAddedForAnyViewerAdmittedToTheItem() = testApplication {
        start()
        item("3tarmac", Visibility.FOLLOWERS, "Tarmac", extra = """"startingDistance":2000000,""")
        activity("3ride", tier = Visibility.SELF, stats = """"distance":40000,""", gear = listOf(tarmac))

        assertEquals(2_000_000, client.totals(viewer = carol).distance)
        assertEquals(2_040_000, client.totals(viewer = alice).distance)
    }

    @Test
    fun anUnknownUriIsNotFound() = testApplication {
        start()
        val response = client.call(GetGear.method, "uri=${gearUri("3nothing").encodeURLParameter()}", alice)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue("NotFound" in response.bodyAsText())
    }

    @Test
    fun gearActivitiesPageNewestFirst() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        activity("3a", start = "2026-10-01T07:00:00Z", gear = listOf(tarmac))
        activity("3b", start = "2026-10-02T07:00:00Z", gear = listOf(tarmac))
        activity("3c", start = "2026-10-03T07:00:00Z", gear = listOf(tarmac))

        val first = client.activities(limit = 2)
        assertEquals(listOf("3c", "3b"), first.activities.map { it.uri.rkey })
        val second = client.activities(limit = 2, cursor = first.cursor)
        assertEquals(listOf("3a"), second.activities.map { it.uri.rkey })
        assertNull(second.cursor)
    }

    @Test
    fun actorGearListsItemsInUseFirstThenByNameWithTotals() = testApplication {
        start()
        item("3old", Visibility.PUBLIC, "Allez", extra = """"retiredAt":"2026-01-01T00:00:00Z",""")
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        item("3aethos", Visibility.PUBLIC, "Aethos")
        item("3meter", Visibility.SELF, "Power meter", type = "sensor")
        activity("3a", stats = """"distance":40000,""", gear = listOf(tarmac, gearUri("3old")))

        val all = client.ok(GetActorGear.method, "actor=$alice", alice).items
        assertEquals(listOf("Aethos", "Power meter", "Tarmac", "Allez"), all.map { it.gear.name })
        assertEquals(listOf(0L, 0L, 40_000L, 40_000L), all.map { it.totals.distance })
        assertEquals(listOf(false, false, false, true), all.map { it.retired })

        val names = mutableListOf<String>()
        var cursor: String? = null
        do {
            val page =
                client.ok(
                    GetActorGear.method,
                    "actor=$alice&limit=1" + cursor?.let { "&cursor=${it.encodeURLParameter()}" }.orEmpty(),
                    dave,
                )
            names += page.items.map { it.gear.name }
            cursor = page.cursor
        } while (cursor != null)
        assertEquals(listOf("Aethos", "Tarmac", "Allez"), names)
    }

    private val chain3 = gearUri("3chain3")

    @Test
    fun aChainCountsTheRidesOfItsBikeWhileMounted() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        item("3chain3", Visibility.PUBLIC, "Chain #3", type = "component", extra = mounts(on("3tarmac", "2026-03-01T00:00:00Z")))
        activity("3feb", start = "2026-02-10T07:00:00Z", stats = """"distance":100000,""", gear = listOf(tarmac))
        activity("3mar", start = "2026-03-10T07:00:00Z", stats = """"distance":300000,""", gear = listOf(tarmac))

        assertEquals(300_000, client.totals(chain3).distance)
        assertEquals(400_000, client.totals().distance)
        assertEquals(listOf(activityUri("3mar")), client.activities(chain3).activities.map { it.uri.value })
        assertEquals(2026, client.gear(chain3).usage.byYear.single().year)
    }

    @Test
    fun tiresCountThroughAWheelsetOnTheBike() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        item("3wheels", Visibility.PUBLIC, "Wheels", type = "component", extra = mounts(on("3tarmac", "2026-03-01T00:00:00Z")))
        item("3tires", Visibility.PUBLIC, "Tires", type = "component", extra = mounts(on("3wheels", "2026-03-01T00:00:00Z")))
        activity("3mar", start = "2026-03-10T07:00:00Z", stats = """"distance":50000,""", gear = listOf(tarmac))

        for (uri in listOf(tarmac, gearUri("3wheels"), gearUri("3tires"))) assertEquals(1, client.totals(uri).activities, uri)
    }

    @Test
    fun aRideListingTheBikeAndItsPowerMeterCountsOnceForTheMeter() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        item("3meter", Visibility.PUBLIC, "Power meter", type = "component", extra = mounts(on("3tarmac", "2026-03-01T00:00:00Z")))
        activity("3mar", start = "2026-03-10T07:00:00Z", stats = """"distance":50000,""", gear = listOf(tarmac, gearUri("3meter")))

        val totals = client.totals(gearUri("3meter"))
        assertEquals(1, totals.activities)
        assertEquals(50_000, totals.distance)
        val listed = client.ok(GetActorGear.method, "actor=$alice", alice).items.associate { it.gear.uri.value to it.totals.activities }
        assertEquals(1, listed[gearUri("3meter")])
    }

    @Test
    fun aSwapLeavesEarlierRidesWithTheOldChain() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        item(
            "3chain3",
            Visibility.PUBLIC,
            "Chain #3",
            type = "component",
            extra = mounts(on("3tarmac", "2026-03-01T00:00:00Z", "2026-07-01T00:00:00Z")),
        )
        item("3chain4", Visibility.PUBLIC, "Chain #4", type = "component", extra = mounts(on("3tarmac", "2026-07-01T00:00:00Z")))
        activity("3jun", start = "2026-06-30T23:00:00Z", stats = """"distance":10000,""", gear = listOf(tarmac))
        activity("3jul", start = "2026-07-01T00:00:00Z", stats = """"distance":20000,""", gear = listOf(tarmac))

        assertEquals(10_000, client.totals(chain3).distance)
        assertEquals(20_000, client.totals(gearUri("3chain4")).distance)
    }

    @Test
    fun aFollowerCountsNothingThroughAnOnlyMeBike() = testApplication {
        start()
        item("3tarmac", Visibility.SELF, "Tarmac")
        item("3chain3", Visibility.PUBLIC, "Chain #3", type = "component", extra = mounts(on("3tarmac", "2026-03-01T00:00:00Z")))
        activity("3mar", start = "2026-03-10T07:00:00Z", stats = """"distance":50000,""", gear = listOf(tarmac))

        assertEquals(1, client.totals(chain3, viewer = alice).activities)
        assertEquals(GearTotals.ZERO, client.totals(chain3, viewer = carol))
        assertEquals(emptyList(), client.activities(chain3, viewer = carol).activities)
    }

    @Test
    fun aDeletedBikeCountsNothingForItsChain() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        item("3chain3", Visibility.PUBLIC, "Chain #3", type = "component", extra = mounts(on("3tarmac", "2026-03-01T00:00:00Z")))
        activity("3mar", start = "2026-03-10T07:00:00Z", stats = """"distance":50000,""", gear = listOf(tarmac))
        activity("3own", start = "2026-03-11T07:00:00Z", stats = """"distance":7000,""", gear = listOf(chain3))

        commit(alice, Nsid.GEAR_ITEM, "3tarmac", null)

        assertEquals(7_000, client.totals(chain3).distance)
    }

    @Test
    fun aBikeListsCurrentThenPreviousComponentsAndAChainItsMountHistory() = testApplication {
        start()
        item("3tarmac", Visibility.PUBLIC, "Tarmac")
        item("3stumpy", Visibility.SELF, "Stumpjumper")
        item(
            "3chain3",
            Visibility.PUBLIC,
            "Chain #3",
            type = "component",
            extra = mounts(on("3tarmac", "2026-03-01T00:00:00Z", "2026-07-01T00:00:00Z"), on("3stumpy", "2026-07-01T00:00:00Z")),
        )
        item("3chain4", Visibility.PUBLIC, "Chain #4", type = "component", extra = mounts(on("3tarmac", "2026-07-01T00:00:00Z")))
        item("3meter", Visibility.SELF, "Power meter", type = "component", extra = mounts(on("3tarmac", "2026-03-01T00:00:00Z")))
        activity("3jul", start = "2026-07-10T07:00:00Z", stats = """"distance":20000,""", gear = listOf(tarmac))

        val components = client.gear(viewer = alice).components.orEmpty()
        assertEquals(listOf("Power meter", "Chain #4", "Chain #3"), components.map { it.gear.name })
        assertEquals(listOf(null, null, Instant.parse("2026-07-01T00:00:00Z")), components.map { it.to })
        assertEquals(20_000, components[1].totals.distance)

        // Carol sees neither the Only me power meter nor the Only me Stumpjumper in Chain #3's history.
        assertEquals(listOf("Chain #4", "Chain #3"), client.gear(viewer = carol).components.orEmpty().map { it.gear.name })
        assertEquals(listOf("Tarmac", "Stumpjumper"), client.gear(chain3, viewer = alice).mounts.orEmpty().map { it.parent.name })
        assertEquals(listOf("Tarmac"), client.gear(chain3, viewer = carol).mounts.orEmpty().map { it.parent.name })
        assertNull(client.gear(gearUri("3chain4"), viewer = alice).components)
    }
}
