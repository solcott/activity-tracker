package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.DiveSites
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.SegmentNameRules
import io.github.solcott.activitytracker.appview.segments.SegmentNaming
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSite
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSites
import io.github.solcott.activitytracker.lexicon.app.DiveSitePosition
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetDiveSite
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteDives
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteRegulars
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSites
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/** The dive-site queries (dive-sites/site-records, site-visits, site-conditions, site-regulars, site-names), end to end over HTTP. */
class DiveSiteQueriesTest : DiveSiteTestBase() {
    private val withImporter = config.copy(segmentImporter = Did(importer))

    private fun ApplicationTestBuilder.serve() = application { appView(withImporter, db, verifier) }

    private fun activate(uri: String) = runBlocking {
        db.tx { DiveSites.update({ DiveSites.uri eq uri }) { it[DiveSites.status] = SegmentStatus.ACTIVE } }
    }

    private fun e7(east: Double, north: Double) = at(east, north).let { (it.lat * 1e7).roundToInt() to (it.lng * 1e7).roundToInt() }

    private suspend fun <I, O> HttpClient.procedure(
        nsid: String,
        input: KSerializer<I>,
        body: I,
        output: KSerializer<O>,
        viewer: String?,
    ): Pair<HttpResponse, O?> {
        val response =
            post("/xrpc/$nsid") {
                viewer?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
                contentType(ContentType.Application.Json)
                setBody(AtJson.encodeToString(input, body))
            }
        val text = response.bodyAsText()
        return response to if (response.status == HttpStatusCode.OK) AtJson.decodeFromString(output, text) else null
    }

    // Records and search

    @Test
    fun aSiteIsServedWhateverItsStatusAndAnUnknownOneIsNotFound() = testApplication {
        serve()
        site("3ks")
        site("3kt", east = 30.0, did = bob)
        val site = client.xrpcOk(GetDiveSite.method, mapOf("uri" to siteUri("3ks"))).site
        assertEquals("Blue Hole", site.currentName)
        assertEquals(SegmentStatus.PROPOSED, site.status)
        val duplicate = client.xrpcOk(GetDiveSite.method, mapOf("uri" to siteUri("3kt", bob))).site
        assertEquals(siteUri("3ks"), duplicate.duplicateOf?.value)
        assertEquals("NotFound", client.xrpcError(GetDiveSite.method, mapOf("uri" to siteUri("3kz"))))
    }

    @Test
    fun searchListsActiveSitesAndProposedOnesOnlyToTheirCreatorAndDivers() = testApplication {
        serve()
        site("3ks")
        site("3kt", east = 300.0)
        activate(siteUri("3ks"))
        val (minLat, minLng) = e7(-1000.0, -1000.0)
        val (maxLat, maxLng) = e7(1000.0, 1000.0)
        val box = mapOf("minLat" to "$minLat", "minLng" to "$minLng", "maxLat" to "$maxLat", "maxLng" to "$maxLng")
        fun listed(viewer: String?) = runBlocking { client.xrpcOk(SearchDiveSites.method, box, viewer).sites.map { it.uri.value } }
        assertEquals(listOf(siteUri("3ks")), listed(bob))
        assertEquals(listOf(siteUri("3ks")), listed(null))
        assertEquals(listOf(siteUri("3ks"), siteUri("3kt")), listed(alice), "her own proposed site")
        diveAt("3kb", east = 300.0, did = bob)
        match()
        assertEquals(listOf(siteUri("3ks"), siteUri("3kt")), listed(bob), "a site he dived")
        val (farLat, _) = e7(0.0, 300_000.0)
        assertEquals("InvalidRequest", client.xrpcError(SearchDiveSites.method, box + ("maxLat" to "$farLat")))
    }

    @Test
    fun checkDiveSiteGivesDuplicatesAndTheAllowance() = testApplication {
        serve()
        site("3ks")
        val (lat, lng) = e7(20.0, 0.0)
        val input = CheckDiveSite.Input(lat, lng)
        val (_, bobs) =
            client.procedure(Nsid.FEED_CHECK_DIVE_SITE, CheckDiveSite.Input.serializer(), input, CheckDiveSite.Output.serializer(), bob)
        assertEquals(listOf(siteUri("3ks")), assertNotNull(bobs).duplicates.map { it.uri.value })
        assertEquals(10, bobs.remaining)
        val (_, alices) =
            client.procedure(Nsid.FEED_CHECK_DIVE_SITE, CheckDiveSite.Input.serializer(), input, CheckDiveSite.Output.serializer(), alice)
        assertEquals(9, assertNotNull(alices).remaining)
        val (signedOut, _) =
            client.procedure(Nsid.FEED_CHECK_DIVE_SITE, CheckDiveSite.Input.serializer(), input, CheckDiveSite.Output.serializer(), null)
        assertEquals(HttpStatusCode.Unauthorized, signedOut.status)
    }

    @Test
    fun onlyTheImporterChecksExtractedSites() = testApplication {
        serve()
        site("3ks")
        activate(siteUri("3ks"))
        val (nearLat, nearLng) = e7(30.0, 0.0)
        val (farLat, farLng) = e7(5000.0, 0.0)
        val input =
            CheckDiveSites.Input(listOf(DiveSitePosition("osm-node-1", nearLat, nearLng), DiveSitePosition("osm-node-2", farLat, farLng)))
        val (_, checked) =
            client.procedure(
                Nsid.IMPORTER_CHECK_DIVE_SITES,
                CheckDiveSites.Input.serializer(),
                input,
                CheckDiveSites.Output.serializer(),
                importer,
            )
        assertEquals(listOf(listOf(siteUri("3ks")), emptyList()), assertNotNull(checked).sites.map { s -> s.duplicates.map { it.value } })
        val (refused, _) =
            client.procedure(
                Nsid.IMPORTER_CHECK_DIVE_SITES,
                CheckDiveSites.Input.serializer(),
                input,
                CheckDiveSites.Output.serializer(),
                alice,
            )
        assertEquals(HttpStatusCode.Forbidden, refused.status)
    }

    // Visits

    @Test
    fun aSignedOutViewerIsListedOnlyPublicDives() = testApplication {
        serve()
        site("3ks")
        diveAt("3ka")
        diveAt("3kb", did = bob)
        diveAt("3kc", did = bob, tier = Visibility.SELF)
        diveAt("3kd", did = carol, tier = Visibility.SELF)
        match()
        fun listed(viewer: String?) = runBlocking {
            client.xrpcOk(GetDiveSiteDives.method, mapOf("site" to siteUri("3ks")), viewer).dives.map { it.uri.value }.toSet()
        }
        assertEquals(setOf(activityUri("3ka"), activityUri("3kb", bob)), listed(null))
        assertEquals(setOf(activityUri("3ka"), activityUri("3kb", bob), activityUri("3kc", bob)), listed(bob))
    }

    @Test
    fun aDivesPageLinksItsSiteOnlyWhenTheViewerMaySeeTheVisit() = testApplication {
        serve()
        site("3ks")
        diveAt("3ka")
        put(
            alice,
            Nsid.FEED_ACTIVITY_DETAILS,
            "3ka",
            """{"activity":"${activityUri("3ka")}","polyline":"_p~iF~ps|U","createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.SELF,
        )
        diveAt("3kb", did = bob)
        follow(carol, alice)
        match()
        val shown = client.xrpcOk(GetActivity.method, mapOf("uri" to activityUri("3kb", bob)), carol).activity.diveSite
        assertEquals("Blue Hole", assertNotNull(shown).name)
        assertNull(client.xrpcOk(GetActivity.method, mapOf("uri" to activityUri("3ka")), carol).activity.diveSite, "Map is Only me")
        assertNotNull(client.xrpcOk(GetActivity.method, mapOf("uri" to activityUri("3ka")), alice).activity.diveSite, "her own")
    }

    // Conditions

    @Test
    fun conditionsSummarizeSummerAndWinter() = testApplication {
        serve()
        site("3ks")
        repeat(4) {
            diveAt("3ka$it", dive = """"maxDepth":${1800 + it * 100},"minTemperature":260""", startedAt = "2026-08-0${it + 1}T09:00:00Z")
        }
        repeat(4) { diveAt("3kb$it", dive = """"maxDepth":2400,"minTemperature":180""", startedAt = "2026-02-0${it + 1}T09:00:00Z") }
        match()
        val conditions = assertNotNull(client.xrpcOk(GetDiveSiteConditions.method, mapOf("site" to siteUri("3ks"))).conditions)
        assertEquals(8, conditions.dives)
        assertEquals(180, conditions.minTemperature)
        assertEquals(260, conditions.maxTemperature)
        assertEquals(2400, conditions.maxDepth)
        assertNull(conditions.medianVisibility, "no dive recorded visibility")
        assertEquals(listOf(2 to 180L, 8 to 260L), conditions.months.map { it.month to it.medianTemperature })
    }

    @Test
    fun aHiddenDiveCategoryGivesAFollowerNoConditions() = testApplication {
        serve()
        site("3ks")
        // The Dive category is its dive streams' channels (DetailCategory): an Only me part holding depth hides it.
        diveAt("3ka", dive = """"maxDepth":1800,"minTemperature":260""")
        put(
            alice,
            Nsid.FEED_ACTIVITY_STREAMS,
            "3ka",
            """{"activity":"${activityUri("3ka")}","channels":["depth"],"data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkdepth"},""" +
                """"mimeType":"application/json","size":100},"createdAt":"2026-10-05T08:00:00Z"}""",
            Visibility.SELF,
        )
        follow(bob, alice)
        match()
        assertEquals(1, client.xrpcOk(GetDiveSiteDives.method, mapOf("site" to siteUri("3ks")), bob).dives.size, "the dive shows")
        assertNull(client.xrpcOk(GetDiveSiteConditions.method, mapOf("site" to siteUri("3ks")), bob).conditions)
        assertEquals(260, client.xrpcOk(GetDiveSiteConditions.method, mapOf("site" to siteUri("3ks")), alice).conditions?.minTemperature)
    }

    // Regulars

    @Test
    fun aProposedSiteHasNoRegulars() = testApplication {
        serve()
        site("3ks")
        diveAt("3ka")
        match()
        assertEquals(emptyList(), client.xrpcOk(GetDiveSiteRegulars.method, mapOf("site" to siteUri("3ks"))).entries)
    }

    @Test
    fun regularsCountOnlyVisitsTheViewerMaySeeAndLeaveOutHiddenAthletes() = testApplication {
        serve()
        site("3ks")
        activate(siteUri("3ks"))
        repeat(6) {
            diveAt(
                "3kb$it",
                did = bob,
                tier = if (it < 4) Visibility.SELF else Visibility.PUBLIC,
                startedAt = "2026-08-0${it + 1}T09:00:00Z",
            )
        }
        repeat(8) { diveAt("3kc$it", did = carol, startedAt = "2026-08-0${it + 1}T09:00:00Z") }
        spaceWrite(
            self(carol),
            Nsid.ACTOR_SEGMENT_PREFS,
            "self",
            """{"matching":true,"hideFromLeaderboards":true,"updatedAt":"2026-10-15T12:00:00Z"}""",
        )
        diveAt("3ka", startedAt = "2026-08-01T09:00:00Z")
        match()
        val board = client.xrpcOk(GetDiveSiteRegulars.method, mapOf("site" to siteUri("3ks")), alice)
        assertEquals(listOf(bob to 2, alice to 1), board.entries.map { it.athlete.did.value to it.visits })
        assertEquals(listOf(1, 2), board.entries.map { it.rank })
        assertEquals(1, board.viewer?.visits)
        val own = client.xrpcOk(GetDiveSiteRegulars.method, mapOf("site" to siteUri("3ks")), bob).entries.first()
        assertEquals(bob to 6, own.athlete.did.value to own.visits, "he sees all of his own")
        assertEquals(
            "InvalidRequest",
            client.xrpcError(GetDiveSiteRegulars.method, mapOf("site" to siteUri("3ks"), "scope" to "following")),
        )
        val following = client.xrpcOk(GetDiveSiteRegulars.method, mapOf("site" to siteUri("3ks"), "scope" to "following"), alice)
        assertEquals(listOf(alice), following.entries.map { it.athlete.did.value })
    }

    // Names

    @Test
    fun aRegularMayProposeAndOneVisitMayOnlyVote() = testApplication {
        serve()
        site("3ks")
        activate(siteUri("3ks"))
        repeat(6) { diveAt("3ka$it", startedAt = "2026-08-0${it + 1}T09:00:00Z") }
        diveAt("3kb", did = bob)
        match()
        val bobs = assertNotNull(client.xrpcOk(GetDiveSite.method, mapOf("uri" to siteUri("3ks")), bob).site.viewer)
        assertTrue(bobs.canVoteOnNames)
        assertFalse(bobs.canProposeName)
        assertTrue(client.xrpcOk(GetDiveSite.method, mapOf("uri" to siteUri("3ks")), alice).site.viewer?.canProposeName == true)

        commit(
            alice,
            Nsid.FEED_SEGMENT_NAME_PROPOSAL,
            "3kp",
            """{"segment":"${siteUri("3ks")}","name":"Shark Alley","createdAt":"2026-10-28T00:00:00Z"}""",
        )
        val proposal = "at://$alice/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp"
        val proposals = client.xrpcOk(GetDiveSite.method, mapOf("uri" to siteUri("3ks"))).site.nameProposals
        assertEquals(listOf("Shark Alley"), proposals.map { it.name })
        val cid = proposals.single().cid.value
        fun vote(voter: String) =
            spaceWrite(
                space(AthleteSpace.INTERACTIONS, voter),
                Nsid.FEED_SEGMENT_NAME_VOTE,
                "3kv",
                """{"subject":{"uri":"$proposal","cid":"$cid"},"direction":"up","createdAt":"2026-10-28T00:00:00Z"}""",
            )
        vote(bob)
        vote(carol)
        val counted = client.xrpcOk(GetDiveSite.method, mapOf("uri" to siteUri("3ks"))).site.nameProposals.single()
        assertEquals(1, counted.up, "carol never dived it, so her vote doesn't count")

        runBlocking { SegmentNaming(db, rules = SegmentNameRules(minUp = 1, minOpenDays = 0)).update() }
        val renamed = client.xrpcOk(GetDiveSite.method, mapOf("uri" to siteUri("3ks"))).site
        assertEquals("Shark Alley", renamed.currentName)
        assertEquals("Blue Hole", renamed.record.name, "the record keeps the original")
        assertEquals("Shark Alley", client.xrpcOk(GetActivity.method, mapOf("uri" to activityUri("3kb", bob))).activity.diveSite?.name)
    }

    @Test
    fun aProposalFromAnAthleteWithTooFewDatesIsDropped() = testApplication {
        serve()
        site("3ks")
        activate(siteUri("3ks"))
        diveAt("3kb", did = bob)
        match()
        commit(
            bob,
            Nsid.FEED_SEGMENT_NAME_PROPOSAL,
            "3kp",
            """{"segment":"${siteUri("3ks")}","name":"Bob's","createdAt":"2026-10-28T00:00:00Z"}""",
        )
        assertEquals(emptyList(), client.xrpcOk(GetDiveSite.method, mapOf("uri" to siteUri("3ks"))).site.nameProposals)
    }
}
