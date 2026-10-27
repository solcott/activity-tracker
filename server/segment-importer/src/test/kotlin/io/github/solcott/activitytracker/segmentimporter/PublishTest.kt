package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateCheck
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `publish` (segments/segment-import): ridden candidates that duplicate nothing become segments, never doubled or rewritten. */
class PublishTest {
    private val fake = FakeServices()
    private val clients = Clients(fake.http, fake.settings)
    private val clock =
        object : Clock {
            override fun now() = Instant.parse("2026-10-21T12:00:00Z")
        }

    /** Records in the fake repo, by rkey. */
    private val repo = mutableMapOf<String, String>()

    /** An athlete's active segment, which [appView] reports as the duplicate of a candidate. */
    private val athleteSegment =
        SegmentView(
            uri = AtUri("at://did:plc:alice/${Nsid.FEED_SEGMENT}/3kx"),
            cid = Cid("bafyseg"),
            creator = ProfileViewBasic(Did("did:plc:alice"), Handle("alice.test")),
            record = SegmentRecord("Climb", "ride", "line", "athlete", "CC0-1.0", Instant.parse("2026-10-01T00:00:00Z")),
            distanceMeters = 1000,
            status = SegmentStatus.ACTIVE,
        )

    private fun candidate(id: String) =
        if (id.startsWith("usfs-")) {
            """{"id":"$id","sport":"hike","name":"Trail $id","line":"line-$id","source":"agency","license":"CC0-1.0",""" +
                """"attribution":"USDA Forest Service"}"""
        } else {
            """{"id":"$id","sport":"ride","name":"Trail $id","line":"line-$id","source":"osm","license":"ODbL-1.0"}"""
        }

    /** Candidates [appView]'s check reports as duplicates of [athleteSegment]; a test may change them between runs. */
    private val liveDuplicates = mutableSetOf<String>()

    /**
     * The AppView lists [ids] as ridden, [pageSize] at a time, and its check reports [duplicates] (added to [liveDuplicates]) as duplicates
     * and leaves out [unstaged], as candidates a `stage` finished away.
     */
    private fun appView(
        ids: List<String>,
        duplicates: Set<String> = emptySet(),
        pageSize: Int = 500,
        unstaged: Set<String> = emptySet(),
    ) {
        liveDuplicates += duplicates
        fake.on(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES) { call ->
            val after = call.params["cursor"]
            val page = ids.filter { after == null || it > after }.take(pageSize)
            val cursor = if (page.size == pageSize) ""","cursor":"${page.last()}"""" else ""
            json("""{"candidates":[${page.joinToString(",") { candidate(it) }}]$cursor}""")
        }
        fake.on(Nsid.IMPORTER_CHECK_SEGMENT_CANDIDATES) { call ->
            val input = AtJson.decodeFromJsonElement(CheckSegmentCandidates.Input.serializer(), call.body!!)
            val checked =
                input.ids
                    .filter { it !in unstaged }
                    .map { id -> SegmentCandidateCheck(id, if (id in liveDuplicates) listOf(athleteSegment.uri) else emptyList()) }
            json(AtJson.encodeToString(CheckSegmentCandidates.Output.serializer(), CheckSegmentCandidates.Output(checked)))
        }
        fake.on("com.atproto.repo.getRecord") { call ->
            val rkey = call.params.getValue("rkey")
            repo[rkey]?.let { json("""{"uri":"at://${FakeServices.DID}/${Nsid.FEED_SEGMENT}/$rkey","value":$it}""") }
                ?: error(HttpStatusCode.BadRequest, "RecordNotFound")
        }
        fake.on("com.atproto.repo.createRecord") { call ->
            val rkey = call.body!!.getValue("rkey").jsonPrimitive.content
            if (rkey in repo) return@on error(HttpStatusCode.BadRequest, "InvalidRequest", "Record already exists")
            repo[rkey] = call.body.getValue("record").toString()
            json("""{"uri":"at://${FakeServices.DID}/${Nsid.FEED_SEGMENT}/$rkey","cid":"bafy$rkey"}""")
        }
    }

    @Test
    fun aRiddenCandidateBecomesASegmentKeyedByItsId() = runTest {
        appView(listOf("c1"))
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1"), result.published)
        assertNull(result.stoppedBy)
        val create = fake.calls("com.atproto.repo.createRecord").single().body!!
        assertEquals(
            FakeServices.DID to Nsid.FEED_SEGMENT,
            create.getValue("repo").jsonPrimitive.content to create.getValue("collection").jsonPrimitive.content,
        )
        assertEquals("c1" to "false", create.getValue("rkey").jsonPrimitive.content to create.getValue("validate").jsonPrimitive.content)
        val record = AtJson.decodeFromJsonElement(SegmentRecord.serializer(), create.getValue("record").jsonObject)
        assertEquals(
            SegmentRecord("Trail c1", "ride", "line-c1", "osm", "ODbL-1.0", Instant.parse("2026-10-21T12:00:00Z")),
            record,
        )
        assertEquals(
            "Bearer svc:${FakeServices.APPVIEW_DID}:${Nsid.IMPORTER_CHECK_SEGMENT_CANDIDATES}",
            fake.calls(Nsid.IMPORTER_CHECK_SEGMENT_CANDIDATES).single().auth,
        )
    }

    @Test
    fun anAgencyCandidateIsPublishedWithItsSourceLicenceAndAttribution() = runTest {
        appView(listOf("usfs-1"))
        assertEquals(listOf("usfs-1"), publish(clients, "us-vermont", clock).published)
        val create = fake.calls("com.atproto.repo.createRecord").single().body!!
        assertEquals(
            SegmentRecord(
                "Trail usfs-1",
                "hike",
                "line-usfs-1",
                "agency",
                "CC0-1.0",
                Instant.parse("2026-10-21T12:00:00Z"),
                attribution = "USDA Forest Service",
            ),
            AtJson.decodeFromJsonElement(SegmentRecord.serializer(), create.getValue("record").jsonObject),
        )
    }

    @Test
    fun aDuplicateIsSkipped() = runTest {
        appView(listOf("c1", "c2"), duplicates = setOf("c1"))
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1") to listOf("c2"), result.duplicates to result.published)
        assertEquals(setOf("c2"), repo.keys)
    }

    @Test
    fun knownDuplicatesAreCheckedInOneCallPerPage() = runTest {
        val ids = (100 until 400).map { "c$it" }
        appView(ids, duplicates = ids.toSet())
        val result = publish(clients, "us-vermont", clock)
        assertEquals(ids, result.duplicates)
        assertEquals(1, fake.calls(Nsid.IMPORTER_CHECK_SEGMENT_CANDIDATES).size)
        assertEquals(
            ids,
            fake.calls(Nsid.IMPORTER_CHECK_SEGMENT_CANDIDATES).single().body!!.getValue("ids").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(emptyList(), fake.calls("com.atproto.repo.getRecord") + fake.calls("com.atproto.repo.createRecord"))
    }

    @Test
    fun aDuplicateWhoseSegmentIsGoneIsPublishedNextRun() = runTest {
        appView(listOf("c1"), duplicates = setOf("c1"))
        assertEquals(listOf("c1"), publish(clients, "us-vermont", clock).duplicates)
        liveDuplicates.clear()
        assertEquals(listOf("c1"), publish(clients, "us-vermont", clock).published)
        assertEquals(setOf("c1"), repo.keys)
    }

    @Test
    fun aCandidateNoLongerStagedIsntPublished() = runTest {
        appView(listOf("c1", "c2"), unstaged = setOf("c1"))
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1") to listOf("c2"), result.gone to result.published)
        assertEquals(setOf("c2"), repo.keys)
    }

    @Test
    fun anExistingRecordIsNeverRewritten() = runTest {
        appView(listOf("c1"))
        repo["c1"] = """{"name":"Old line"}"""
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1"), result.present)
        assertEquals(emptyList(), fake.calls("com.atproto.repo.createRecord"))
        assertEquals("""{"name":"Old line"}""", repo["c1"])
    }

    @Test
    fun aKeyCreatedMeanwhileCountsAsPresent() = runTest {
        appView(listOf("c1"))
        // Another run creates c1 between this run's check and its write.
        var checks = 0
        fake.on("com.atproto.repo.getRecord") { call ->
            val rkey = call.params.getValue("rkey")
            if (checks++ == 0) {
                repo[rkey] = """{"name":"Theirs"}"""
                return@on error(HttpStatusCode.BadRequest, "RecordNotFound")
            }
            json("""{"uri":"at://${FakeServices.DID}/${Nsid.FEED_SEGMENT}/$rkey","value":${repo.getValue(rkey)}}""")
        }
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1") to emptyList<String>(), result.present to result.published)
        assertEquals("""{"name":"Theirs"}""", repo["c1"])
    }

    @Test
    fun pagesAreFollowedPastTheFirst() = runTest {
        val ids = (1000 until 1501).map { "c$it" }
        appView(ids)
        val result = publish(clients, "us-vermont", clock)
        assertEquals(501, result.published.size)
        assertEquals(listOf(null, "c1499"), fake.calls(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES).map { it.params["cursor"] })
        assertEquals(listOf("500"), fake.calls(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES).map { it.params["limit"] }.distinct())
    }

    @Test
    fun aRateLimitStopsTheRunKeepingWhatItDid() = runTest {
        appView(listOf("c1", "c2", "c3"))
        var creates = 0
        fake.on("com.atproto.repo.createRecord") { call ->
            if (++creates == 2) return@on error(HttpStatusCode.TooManyRequests, "RateLimitExceeded")
            val rkey = call.body!!.getValue("rkey").jsonPrimitive.content
            repo[rkey] = call.body.getValue("record").toString()
            json("""{"uri":"at://${FakeServices.DID}/${Nsid.FEED_SEGMENT}/$rkey","cid":"bafy$rkey"}""")
        }
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1"), result.published)
        assertEquals(429, assertIs<XrpcException>(result.stoppedBy).status)
        assertEquals(setOf("c1"), repo.keys)
    }
}
