package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** `getActivitySegments` and `getSegmentEfforts` (segments/segment-efforts): efforts served only at their narrowest tier. */
class SegmentEffortsTest : SegmentTestBase() {
    /** Alice's run [rkey] at [tier] along the segment, matched. Bob follows Alice; Carol is a stranger. */
    private fun run(rkey: String, tier: String = Visibility.PUBLIC, speed: Double = 5.0) {
        activity(rkey, tier = tier)
        track(rkey, speed = speed)
    }

    private fun setUp() {
        matching(true)
        follow(bob, alice)
        segment("3ks", did = carol)
    }

    private suspend fun HttpClient.segmentsOf(rkey: String, viewer: String?) =
        xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri(rkey)), viewer).efforts

    private suspend fun HttpClient.hidden(rkey: String, viewer: String?) =
        assertEquals("NotFound", xrpcError(GetActivitySegments.method, mapOf("activity" to activityUri(rkey)), viewer))

    @Test
    fun effortsFollowTheActivitysTier() = testApplication {
        application { appView(config, db, verifier) }
        setUp()
        run("3ka")
        run("3kb", Visibility.FOLLOWERS)
        run("3kc", Visibility.SELF)
        match()

        for (viewer in listOf(null, carol, bob, alice)) assertEquals(1, client.segmentsOf("3ka", viewer).size, "public, to $viewer")
        assertEquals(1, client.segmentsOf("3kb", bob).size)
        assertEquals(1, client.segmentsOf("3kb", alice).size)
        client.hidden("3kb", carol)
        client.hidden("3kb", null)
        assertEquals(1, client.segmentsOf("3kc", alice).size)
        client.hidden("3kc", bob)

        val effort = client.segmentsOf("3ka", carol).single()
        assertEquals(segmentUri("3ks", carol), effort.segment.uri.value)
        assertEquals("Hill", effort.segment.name)
        assertEquals(20, effort.startOffset)
        assertEquals(200, effort.elapsed)
        assertEquals("2026-10-06", effort.startedOn)
        assertEquals(true, effort.best)
    }

    @Test
    fun aMapCategoryNarrowerThanTheActivityHidesItsEfforts() = testApplication {
        application { appView(config, db, verifier) }
        setUp()
        run("3ka")
        put(
            alice,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3ka",
            """{"activity":"${activityUri("3ka")}","polyline":"_p~iF~ps|U","createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.FOLLOWERS,
        )
        match()

        assertEquals(emptyList(), client.segmentsOf("3ka", carol), "the activity is visible, its efforts aren't")
        assertEquals(1, client.segmentsOf("3ka", bob).size)
        assertEquals(
            emptyList(),
            client.xrpcOk(GetSegmentEfforts.method, mapOf("segment" to segmentUri("3ks", carol), "actor" to alice), carol).efforts,
        )
    }

    @Test
    fun aBlockedViewerSeesNothing() = testApplication {
        application { appView(config, db, verifier) }
        setUp()
        run("3ka")
        match()
        block(alice, bob)

        client.hidden("3ka", bob)
        assertEquals(
            emptyList(),
            client.xrpcOk(GetSegmentEfforts.method, mapOf("segment" to segmentUri("3ks", carol), "actor" to alice), bob).efforts,
        )
    }

    @Test
    fun anAthletesEffortsAreListedFastestFirstAndPaged() = testApplication {
        application { appView(config, db, verifier) }
        setUp()
        run("3ka", speed = 4.0)
        run("3kb", speed = 5.0)
        run("3kc", speed = 2.5)
        run("3kd", Visibility.FOLLOWERS, speed = 10.0)
        match()

        val query = mapOf("segment" to segmentUri("3ks", carol), "actor" to alice)
        val own = client.xrpcOk(GetSegmentEfforts.method, query, alice).efforts
        assertEquals(listOf("3kd", "3kb", "3ka", "3kc").map { activityUri(it) }, own.map { it.activity.value })
        assertEquals(listOf(true, null, null, null), own.map { it.best })

        val first = client.xrpcOk(GetSegmentEfforts.method, query + ("limit" to "2"), alice)
        assertEquals(listOf("3kd", "3kb").map { activityUri(it) }, first.efforts.map { it.activity.value })
        val second = client.xrpcOk(GetSegmentEfforts.method, query + mapOf("limit" to "2", "cursor" to first.cursor!!), alice)
        assertEquals(listOf("3ka", "3kc").map { activityUri(it) }, second.efforts.map { it.activity.value })

        // A stranger sees the three public ones, the fastest of those marked best.
        val stranger = client.xrpcOk(GetSegmentEfforts.method, query, carol).efforts
        assertEquals(listOf("3kb", "3ka", "3kc").map { activityUri(it) }, stranger.map { it.activity.value })
        assertEquals(true, stranger.first().best)

        assertEquals("NotFound", client.xrpcError(GetSegmentEfforts.method, mapOf("segment" to segmentUri("3kx"), "actor" to alice)))
        assertNull(client.xrpcOk(GetSegmentEfforts.method, query + ("actor" to "nobody.test")).cursor)
    }

    @Test
    fun anActivitysEffortsAreInTheOrderTheyBegan() = testApplication {
        application { appView(config, db, verifier) }
        setUp()
        // A second segment further along the same line: 1.2 to 1.6 km.
        segment("3kt", path = listOf(0.0 to 1200.0, 0.0 to 1600.0), did = carol)
        activity("3ka")
        track("3ka", pieces = listOf(listOf(0.0 to -100.0, 0.0 to 1700.0)))
        match()

        val efforts = client.segmentsOf("3ka", alice)
        assertEquals(listOf(segmentUri("3ks", carol), segmentUri("3kt", carol)), efforts.map { it.segment.uri.value })
    }
}
