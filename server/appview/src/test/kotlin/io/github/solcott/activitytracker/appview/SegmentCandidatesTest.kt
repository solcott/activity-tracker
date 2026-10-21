package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SegmentCandidateCells
import io.github.solcott.activitytracker.appview.db.SegmentCandidateRows
import io.github.solcott.activitytracker.appview.db.Segments
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.appview.segments.SegmentJobs
import io.github.solcott.activitytracker.appview.segments.SegmentMatcher
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.FinishSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetRiddenSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.PutSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.SegmentCells
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
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

/** Candidate segments staged by the importer (segments/segment-candidates), and their riders counted from segment tracks. */
class SegmentCandidatesTest : SegmentTestBase() {
    private val withImporter = config.copy(segmentImporter = Did(importer))

    private fun ApplicationTestBuilder.start(config: AppViewConfig = withImporter) = application {
        appView(config, db, verifier, segmentJobs = indexer.segmentJobs)
    }

    private fun stagedIds(): List<String> = runBlocking {
        db.tx { SegmentCandidateRows.selectAll().map { it[SegmentCandidateRows.id] }.sorted() }
    }

    /** [did]'s ride [rkey] along [pieces], with matching on. */
    private fun rode(
        did: String,
        rkey: String,
        pieces: List<List<Pair<Double, Double>>> = listOf(alongStraight),
        tier: String = Visibility.PUBLIC,
    ) {
        matching(true, did)
        activity(rkey, tier = tier, sport = "ride", did = did)
        track(rkey, pieces, did = did)
    }

    private suspend fun HttpClient.call(nsid: String, body: String, caller: String?): HttpResponse =
        post("/xrpc/$nsid") {
            caller?.let { header(HttpHeaders.Authorization, "Bearer ${keys.getValue(it).serviceJwt(it, service, nsid)}") }
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpClient.put(input: PutSegmentCandidates.Input, caller: String? = importer) =
        call(Nsid.IMPORTER_PUT_SEGMENT_CANDIDATES, AtJson.encodeToString(PutSegmentCandidates.Input.serializer(), input), caller)

    private suspend fun HttpClient.finish(region: String, revision: String, caller: String? = importer) =
        call(
            Nsid.IMPORTER_FINISH_SEGMENT_CANDIDATES,
            AtJson.encodeToString(FinishSegmentCandidates.Input.serializer(), FinishSegmentCandidates.Input(region, revision)),
            caller,
        )

    private suspend fun HttpResponse.error(status: HttpStatusCode): String {
        val body = bodyAsText()
        assertEquals(status, this.status, body)
        return AtJson.parseToJsonElement(body).jsonObject.getValue("error").jsonPrimitive.content
    }

    @Test
    fun aBatchWithOneBadLineStoresTheRest() {
        val good = (1..499).map { candidate("c-$it") }
        val short = candidate("c-short", path = listOf(0.0 to 0.0, 0.0 to 120.0))
        assertEquals(listOf("c-short"), stage(*(good + short).toTypedArray()))
        assertEquals(499, stagedIds().size)
        assertNull(ridden("c-short"))

        val rejected =
            stage(
                candidate("Bad Id"),
                candidate("c-unnamed", name = "  "),
                candidate("c-sport", sport = "swim"),
                candidate("c-malformed", line = "not a line"),
                candidate("c-long", line = "_".repeat(100_001)),
                candidate("c-source").copy(source = "o".repeat(65)),
            )
        assertEquals(listOf("Bad Id", "c-unnamed", "c-sport", "c-malformed", "c-long", "c-source"), rejected)
    }

    @Test
    fun reStagingUnchangedKeepsRidersAndRidden() {
        rode(alice, "3ka")
        rode(bob, "3kb")
        rode(carol, "3kc")
        stage(candidate("c-1"))
        match()
        assertEquals(setOf(alice, bob, carol), riders("c-1"))
        assertEquals(true, ridden("c-1"))

        assertEquals(emptyList(), stage(candidate("c-1", name = "Renamed"), revision = "r2"))
        assertEquals(emptyList(), jobs(), "nothing to count again")
        assertEquals(setOf(alice, bob, carol), riders("c-1"))
        assertEquals(true, ridden("c-1"))
        val row = runBlocking { db.tx { SegmentCandidateRows.selectAll().single() } }
        assertEquals("Renamed" to "r2", row[SegmentCandidateRows.name] to row[SegmentCandidateRows.revision])
    }

    @Test
    fun reStagingChangedClearsRidersAndCountsAgain() {
        rode(alice, "3ka")
        rode(bob, "3kb")
        rode(carol, "3kc")
        stage(candidate("c-1"))
        match()
        assertEquals(true, ridden("c-1"))
        // A shorter line, still inside everyone's ride.
        stage(candidate("c-1", path = listOf(0.0 to 0.0, 0.0 to 600.0)), revision = "r2")
        assertEquals(false, ridden("c-1"))
        assertEquals(emptySet(), riders("c-1"))
        assertEquals(listOf(SegmentJobs.CANDIDATE to "c-1"), jobs())
        match()
        assertEquals(setOf(alice, bob, carol), riders("c-1"))
        assertEquals(true, ridden("c-1"))

        // Another sport group: nobody's ride counts.
        stage(candidate("c-1", path = listOf(0.0 to 0.0, 0.0 to 600.0), sport = "run"), revision = "r3")
        match()
        assertEquals(emptySet(), riders("c-1"))
        assertEquals(false, ridden("c-1"))

        // Moved 9 km east: its cells go with it, and nobody rode there.
        val far = listOf(9000.0 to 0.0, 9000.0 to 1000.0)
        stage(candidate("c-1", path = far), revision = "r4")
        val cells = runBlocking { db.tx { SegmentCandidateCells.selectAll().map { it[SegmentCandidateCells.cell] }.toSet() } }
        assertEquals(SegmentCells.cellsOf(far.map { (e, n) -> at(e, n) }), cells)
        match()
        assertEquals(emptySet(), riders("c-1"))
    }

    @Test
    fun finishingARunRemovesTheRegionsCandidatesItDidntStage() = testApplication {
        start()
        rode(alice, "3ka")
        stage(candidate("c-kept"), candidate("c-gone", path = listOf(0.0 to 0.0, 0.0 to 900.0)))
        stage(candidate("c-other"), region = "us-maine")
        match()
        assertEquals(setOf(alice), riders("c-gone"))

        // The next run no longer stages the trail that left OSM.
        stage(candidate("c-kept"), revision = "r2")
        val response = client.finish("us-vermont", "r2")
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        assertEquals(1, AtJson.decodeFromString(FinishSegmentCandidates.Output.serializer(), response.bodyAsText()).removed)
        assertEquals(listOf("c-kept", "c-other"), stagedIds())
        assertEquals(emptySet(), riders("c-gone"))
        assertEquals(setOf(alice), riders("c-kept"))
        assertEquals(setOf(alice), riders("c-other"), "another region is untouched")
        val cells = runBlocking { db.tx { SegmentCandidateCells.selectAll().map { it[SegmentCandidateCells.candidateId] }.toSet() } }
        assertEquals(setOf("c-kept", "c-other"), cells)
    }

    @Test
    fun onlyTheImporterMayCall() = testApplication {
        start()
        val input = PutSegmentCandidates.Input("us-vermont", "r1", listOf(candidate("c-1")))
        val accepted = client.put(input)
        assertEquals(HttpStatusCode.OK, accepted.status, accepted.bodyAsText())
        assertEquals(emptyList(), AtJson.decodeFromString(PutSegmentCandidates.Output.serializer(), accepted.bodyAsText()).rejected)

        assertEquals("NotImporter", client.put(input, caller = alice).error(HttpStatusCode.Forbidden))
        assertEquals("NotImporter", client.finish("us-vermont", "r2", caller = alice).error(HttpStatusCode.Forbidden))
        assertEquals(
            "NotImporter",
            client.xrpc(GetRiddenSegmentCandidates.method, mapOf("region" to "us-vermont"), alice).error(HttpStatusCode.Forbidden),
        )
        assertEquals("AuthenticationRequired", client.put(input, caller = null).error(HttpStatusCode.Unauthorized))
        assertEquals(listOf("c-1"), stagedIds())
    }

    @Test
    fun withNoImporterConfiguredEveryoneIsRefused() = testApplication {
        start(config)
        val input = PutSegmentCandidates.Input("us-vermont", "r1", listOf(candidate("c-1")))
        assertEquals("NotImporter", client.put(input).error(HttpStatusCode.Forbidden))
        assertEquals("NotImporter", client.finish("us-vermont", "r1").error(HttpStatusCode.Forbidden))
        assertEquals(
            "NotImporter",
            client.xrpc(GetRiddenSegmentCandidates.method, mapOf("region" to "us-vermont"), importer).error(HttpStatusCode.Forbidden),
        )
        assertEquals(emptyList(), stagedIds())
    }

    @Test
    fun badBatchesAreRefused() = testApplication {
        start()
        val tooMany = PutSegmentCandidates.Input("us-vermont", "r1", (1..501).map { candidate("c-$it") })
        assertEquals("InvalidRequest", client.put(tooMany).error(HttpStatusCode.BadRequest))
        val badRegion = PutSegmentCandidates.Input("US Vermont", "r1", listOf(candidate("c-1")))
        assertEquals("InvalidRequest", client.put(badRegion).error(HttpStatusCode.BadRequest))
        val badRevision = PutSegmentCandidates.Input("us-vermont", "r".repeat(65), listOf(candidate("c-1")))
        assertEquals("InvalidRequest", client.put(badRevision).error(HttpStatusCode.BadRequest))
        assertEquals(emptyList(), stagedIds())
    }

    @Test
    fun theListingHoldsRiddenCandidatesOnlyWithNoCountsAthletesOrDates() = testApplication {
        start()
        rode(alice, "3ka")
        rode(bob, "3kb")
        rode(carol, "3kc")
        stage(candidate("c-b", name = "Second"), candidate("c-a", name = "First"))
        // Nobody rode this one.
        stage(candidate("c-far", path = listOf(9000.0 to 0.0, 9000.0 to 1000.0)))
        stage(candidate("c-maine"), region = "us-maine")
        match()

        val response = client.xrpc(GetRiddenSegmentCandidates.method, mapOf("region" to "us-vermont"), importer)
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status, body)
        val json = AtJson.parseToJsonElement(body).jsonObject
        assertEquals(setOf("candidates"), json.keys)
        val listed = json.getValue("candidates").jsonArray.map { it.jsonObject }
        assertEquals(listOf("c-a", "c-b"), listed.map { it.getValue("id").jsonPrimitive.content })
        for (item in listed) assertEquals(setOf("id", "sport", "name", "line", "source", "license"), item.keys)
        assertEquals(
            candidate("c-a", name = "First"),
            AtJson.decodeFromString(GetRiddenSegmentCandidates.Output.serializer(), body).candidates.first(),
        )

        val first = client.xrpcOk(GetRiddenSegmentCandidates.method, mapOf("region" to "us-vermont", "limit" to "1"), importer)
        assertEquals(listOf("c-a"), first.candidates.map { it.id })
        val second =
            client.xrpcOk(
                GetRiddenSegmentCandidates.method,
                mapOf("region" to "us-vermont", "limit" to "1", "cursor" to first.cursor!!),
                importer,
            )
        assertEquals(listOf("c-b"), second.candidates.map { it.id })
        val last =
            client.xrpcOk(
                GetRiddenSegmentCandidates.method,
                mapOf("region" to "us-vermont", "limit" to "1", "cursor" to second.cursor!!),
                importer,
            )
        assertEquals(emptyList(), last.candidates)
        assertNull(last.cursor)
    }

    @Test
    fun aWrongSportGroupDoesntCount() {
        matching(true)
        activity("3ka", sport = "run")
        track("3ka")
        stage(candidate("c-ride", sport = "ride"))
        match()
        assertEquals(emptySet(), riders("c-ride"))
    }

    @Test
    fun aPartialPassDoesntCount() {
        rode(alice, "3ka", pieces = listOf(listOf(0.0 to -100.0, 0.0 to 500.0)))
        stage(candidate("c-1"))
        match()
        assertEquals(emptySet(), riders("c-1"))
    }

    @Test
    fun tracksOfPrivateActivitiesCount() {
        rode(alice, "3ka", tier = Visibility.SELF)
        rode(bob, "3kb", tier = Visibility.FOLLOWERS)
        stage(candidate("c-1"))
        match()
        assertEquals(setOf(alice, bob), riders("c-1"))
    }

    @Test
    fun theThirdDistinctRiderMakesItRidden() {
        stage(candidate("c-1"))
        rode(alice, "3ka")
        rode(alice, "3kd")
        rode(bob, "3kb")
        match()
        assertEquals(setOf(alice, bob), riders("c-1"))
        assertEquals(false, ridden("c-1"), "alice's second ride is still one rider")

        rode(carol, "3kc")
        match()
        assertEquals(true, ridden("c-1"))
    }

    @Test
    fun theThresholdIsConfigurable() {
        val eager =
            SegmentMatcher(
                db,
                indexer.segmentJobs,
                { _, _, cid -> blobs[cid]?.let { XrpcBytes(it, "application/json") } },
                candidateRiders = 1,
            )
        stage(candidate("c-1"))
        rode(alice, "3ka")
        runBlocking { eager.runDue() }
        assertEquals(true, ridden("c-1"))
    }

    @Test
    fun ridersAreDroppedWithTheTracksAndRiddenStays() {
        rode(alice, "3ka")
        rode(bob, "3kb")
        rode(carol, "3kc")
        stage(candidate("c-1"), candidate("c-2", path = listOf(0.0 to 0.0, 0.0 to 700.0)))
        match()
        assertEquals(setOf(alice, bob, carol), riders("c-2"))

        // Turning matching off drops them as soon as the setting is indexed, before any job runs.
        matching(false, alice)
        assertEquals(setOf(bob, carol), riders("c-1"))
        assertEquals(setOf(bob, carol), riders("c-2"))
        remove(bob, Nsid.FEED_SEGMENT_TRACK, "3kb", tier = Visibility.SELF)
        assertEquals(setOf(carol), riders("c-1"))
        remove(carol, Nsid.FEED_ACTIVITY, "3kc")
        assertEquals(emptySet(), riders("c-1"), "a deleted activity's track counts no more")
        assertEquals(true to true, ridden("c-1") to ridden("c-2"), "ridden is never undone")
    }

    @Test
    fun candidatesStayOutOfSegmentFeatures() = testApplication {
        start()
        rode(alice, "3ka")
        rode(bob, "3kb")
        rode(carol, "3kc")
        stage(candidate("c-run", sport = "run"), candidate("c-ride"))
        match()
        assertEquals(true, ridden("c-ride"))

        val sw = at(-500.0, 400.0)
        val ne = at(500.0, 600.0)
        val box =
            mapOf(
                "minLat" to "${(sw.lat * 1e7).roundToInt()}",
                "minLng" to "${(sw.lng * 1e7).roundToInt()}",
                "maxLat" to "${(ne.lat * 1e7).roundToInt()}",
                "maxLng" to "${(ne.lng * 1e7).roundToInt()}",
            )
        assertEquals(emptyList(), client.xrpcOk(SearchSegments.method, box, alice).segments)
        assertEquals(emptyList(), client.xrpcOk(GetActivitySegments.method, mapOf("activity" to activityUri("3ka")), alice).efforts)
        val check =
            client.call(
                Nsid.FEED_CHECK_SEGMENT,
                AtJson.encodeToString(CheckSegment.Input.serializer(), CheckSegment.Input("ride", lineOf(straight))),
                alice,
            )
        assertEquals(HttpStatusCode.OK, check.status, check.bodyAsText())
        assertEquals(emptyList(), AtJson.decodeFromString(CheckSegment.Output.serializer(), check.bodyAsText()).duplicates)
        assertEquals(emptyList(), efforts())

        // A segment published over the candidate is no duplicate of it, and its own efforts alone decide its status.
        segment("3ks", sport = "ride", did = alice)
        val status = runBlocking { db.tx { Segments.selectAll().where { Segments.uri eq segmentUri("3ks") }.single()[Segments.status] } }
        assertEquals(SegmentStatus.PROPOSED, status)
        remove(bob, Nsid.FEED_SEGMENT_TRACK, "3kb", tier = Visibility.SELF)
        remove(carol, Nsid.FEED_SEGMENT_TRACK, "3kc", tier = Visibility.SELF)
        match()
        assertEquals(listOf(segmentUri("3ks")), efforts().map { it.segment })
        val after = runBlocking { db.tx { Segments.selectAll().where { Segments.uri eq segmentUri("3ks") }.single()[Segments.status] } }
        assertEquals(SegmentStatus.PROPOSED, after, "the candidate's three riders don't make it active")
        assertFalse(riders("c-ride").isEmpty())
    }
}
