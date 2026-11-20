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
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateCheck
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentStatus
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
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

    /** Records in the fake repo, by collection and rkey. */
    private val repo = mutableMapOf<Pair<String, String>, JsonObject>()

    /** The rkeys of the segments in [repo]. */
    private val keys
        get() = repo.keys.map { it.second }.toSet()

    private fun key(rkey: String) = Nsid.FEED_SEGMENT to rkey

    private fun obj(text: String) = AtJson.parseToJsonElement(text).jsonObject

    /** The segment [repo] holds under [rkey]. */
    private fun written(rkey: String) = AtJson.decodeFromJsonElement(SegmentRecord.serializer(), repo.getValue(key(rkey)))

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
        if (id.startsWith("oneway-")) {
            """{"id":"$id","sport":"ride","name":"Clarks","line":"line-$id","source":"osm","license":"ODbL-1.0","oneWay":true,""" +
                """"climb":"downhill"}"""
        } else if (id.startsWith("lift-")) {
            """{"id":"$id","sport":"alpine","kind":"lift","name":"Summit Quad","line":"line-$id","source":"osm","license":"ODbL-1.0"}"""
        } else if (id.startsWith("usfs-")) {
            """{"id":"$id","sport":"hike","name":"Trail $id","line":"line-$id","source":"agency","license":"CC0-1.0",""" +
                """"attribution":"USDA Forest Service"}"""
        } else {
            """{"id":"$id","sport":"ride","name":"Trail $id","line":"line-$id","source":"osm","license":"ODbL-1.0"}"""
        }

    /** Candidates [appView]'s check reports as duplicates of [athleteSegment]; a test may change them between runs. */
    private val liveDuplicates = mutableSetOf<String>()

    /** Candidates whose own published segment [appView]'s check reports, as it does once the AppView has indexed it. */
    private val indexed = mutableSetOf<String>()

    /**
     * The AppView lists [ids] as ridden, [pageSize] at a time, and its check reports [duplicates] (added to [liveDuplicates]) as
     * duplicates, leaves out [unstaged], as candidates a `stage` finished away, and gives each other one as staged ([candidate]).
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
                    .map { id ->
                        val own = listOfNotNull(AtUri.of(Did(FakeServices.DID), Nsid.FEED_SEGMENT, id).takeIf { id in indexed })
                        SegmentCandidateCheck(
                            id,
                            own + if (id in liveDuplicates) listOf(athleteSegment.uri) else emptyList(),
                            AtJson.decodeFromString(SegmentCandidate.serializer(), candidate(id)),
                        )
                    }
            json(AtJson.encodeToString(CheckSegmentCandidates.Output.serializer(), CheckSegmentCandidates.Output(checked)))
        }
        fake.serveRepo(repo)
    }

    @Test
    fun aRiddenCandidateBecomesASegmentKeyedByItsId() = runTest {
        appView(listOf("c1"))
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1"), result.published)
        assertNull(result.stoppedBy)
        val commit = fake.calls(FakeServices.APPLY_WRITES).single().body!!
        assertEquals(
            FakeServices.DID to "false",
            commit.getValue("repo").jsonPrimitive.content to commit.getValue("validate").jsonPrimitive.content,
        )
        assertEquals(listOf(listOf(Triple("create", Nsid.FEED_SEGMENT, "c1"))), fake.commits())
        val record = written("c1")
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
    fun aOneWayCandidateIsPublishedOneWay() = runTest {
        appView(listOf("c1", "oneway-1"))
        assertEquals(listOf("c1", "oneway-1"), publish(clients, "us-vermont", clock).published)
        assertEquals(true, written("oneway-1").oneWay)
        assertNull(written("c1").oneWay, "a two-way candidate's segment doesn't say")
        assertEquals("downhill", written("oneway-1").climb)
        assertNull(written("c1").climb, "a candidate with no climb gives none")
    }

    @Test
    fun aLiftIsPublishedWithItsKind() = runTest {
        appView(listOf("lift-1"))
        assertEquals(listOf("lift-1"), publish(clients, "us-vermont", clock).published)
        val record = written("lift-1")
        assertEquals(Triple("alpine", "lift", "Summit Quad"), Triple(record.sport, record.kind, record.name))
    }

    @Test
    fun anAgencyCandidateIsPublishedWithItsSourceLicenceAndAttribution() = runTest {
        appView(listOf("usfs-1"))
        assertEquals(listOf("usfs-1"), publish(clients, "us-vermont", clock).published)
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
            written("usfs-1"),
        )
    }

    @Test
    fun aDuplicateIsSkipped() = runTest {
        appView(listOf("c1", "c2"), duplicates = setOf("c1"))
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1") to listOf("c2"), result.duplicates to result.published)
        assertEquals(setOf("c2"), keys)
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
        assertEquals(emptyList(), fake.calls("com.atproto.repo.getRecord") + fake.calls(FakeServices.APPLY_WRITES))
    }

    @Test
    fun aDuplicateWhoseSegmentIsGoneIsPublishedNextRun() = runTest {
        appView(listOf("c1"), duplicates = setOf("c1"))
        assertEquals(listOf("c1"), publish(clients, "us-vermont", clock).duplicates)
        liveDuplicates.clear()
        assertEquals(listOf("c1"), publish(clients, "us-vermont", clock).published)
        assertEquals(setOf("c1"), keys)
    }

    @Test
    fun aCandidateNoLongerStagedIsntPublished() = runTest {
        appView(listOf("c1", "c2"), unstaged = setOf("c1"))
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1") to listOf("c2"), result.gone to result.published)
        assertEquals(setOf("c2"), keys)
    }

    @Test
    fun aCandidateWhoseOwnSegmentIsIndexedCountsAsPublishedNotDuplicate() = runTest {
        appView(listOf("c1", "c2"), duplicates = setOf("c2"))
        repo[key("c1")] = obj("""{"name":"Trail c1"}""")
        indexed += setOf("c1", "c2")
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1", "c2") to emptyList<String>(), result.present to result.duplicates)
        assertEquals(emptyList(), fake.calls("com.atproto.repo.getRecord") + fake.calls(FakeServices.APPLY_WRITES))
    }

    @Test
    fun anExistingRecordIsNeverRewrittenAndTheRestOfItsCommitIsSentAgain() = runTest {
        appView(listOf("c1", "c2"))
        repo[key("c1")] = obj("""{"name":"Old line"}""")
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1") to listOf("c2"), result.present to result.published)
        assertEquals(obj("""{"name":"Old line"}"""), repo[key("c1")])
        assertEquals(
            listOf(
                listOf(Triple("create", Nsid.FEED_SEGMENT, "c1"), Triple("create", Nsid.FEED_SEGMENT, "c2")),
                listOf(Triple("create", Nsid.FEED_SEGMENT, "c2")),
            ),
            fake.commits(),
            "the refused commit, then the one without c1",
        )
    }

    @Test
    fun aKeyCreatedMeanwhileCountsAsPresent() = runTest {
        appView(listOf("c1"))
        // Another run creates c1 between this run's check and its write.
        fake.serveRepo(repo) { commit ->
            if (commit == 1) repo[key("c1")] = obj("""{"name":"Theirs"}""")
            null
        }
        val result = publish(clients, "us-vermont", clock)
        assertEquals(listOf("c1") to emptyList<String>(), result.present to result.published)
        assertEquals(obj("""{"name":"Theirs"}"""), repo[key("c1")])
    }

    @Test
    fun aRefusedCommitWithNoHeldKeyStopsTheRun() = runTest {
        appView(listOf("c1", "c2"))
        fake.serveRepo(repo) { error(HttpStatusCode.InternalServerError, "InternalServerError") }
        val result = publish(clients, "us-vermont", clock)
        assertEquals(500, assertIs<XrpcException>(result.stoppedBy).status)
        assertEquals(emptyList(), result.published + result.present)
        assertEquals(emptySet(), keys)
    }

    @Test
    fun pagesAreFollowedPastTheFirst() = runTest {
        val ids = (1000 until 1501).map { "c$it" }
        appView(ids)
        val result = publish(clients, "us-vermont", clock)
        assertEquals(501, result.published.size)
        assertEquals(listOf(100, 100, 100, 100, 100, 1), fake.commits().map { it.size }, "a page's segments, 100 to a commit")
        assertEquals(listOf(null, "c1499"), fake.calls(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES).map { it.params["cursor"] })
        assertEquals(listOf("500"), fake.calls(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES).map { it.params["limit"] }.distinct())
    }

    @Test
    fun aRateLimitStopsTheRunKeepingWhatItDid() = runTest {
        val ids = (100 until 250).map { "c$it" }
        appView(ids)
        fake.serveRepo(repo) { commit -> if (commit == 2) error(HttpStatusCode.TooManyRequests, "RateLimitExceeded") else null }
        val result = publish(clients, "us-vermont", clock)
        assertEquals(ids.take(100), result.published)
        assertEquals(429, assertIs<XrpcException>(result.stoppedBy).status)
        assertEquals(ids.take(100).toSet(), keys)
        assertEquals(emptyList(), fake.calls("com.atproto.repo.getRecord"), "a rate limit is never taken for a held key")
    }

    @Test
    fun publishingEveryCandidateTakesUnriddenOnesStillSkippingDuplicatesAndTheUnstaged() = runTest {
        appView(emptyList(), duplicates = setOf("c1"), unstaged = setOf("c3"))
        val every = listOf("c1", "c2", "c3").map { SegmentCandidate(it, "ride", "Trail $it", "line-$it", "osm", "ODbL-1.0") }
        val result = publish(clients, "us-vermont", clock, every)
        assertEquals(Triple(listOf("c2"), listOf("c1"), listOf("c3")), Triple(result.published, result.duplicates, result.gone))
        assertEquals(setOf("c2"), keys)
        assertEquals(emptyList(), fake.calls(Nsid.IMPORTER_GET_RIDDEN_SEGMENT_CANDIDATES), "the file, not the ridden list")
    }

    @Test
    fun publishingEveryCandidateWritesTheStagedCandidateNotTheFilesCopy() = runTest {
        appView(emptyList())
        val stale = SegmentCandidate("c1", "ride", "Old name", "old-line", "osm", "ODbL-1.0")
        assertEquals(listOf("c1"), publish(clients, "us-vermont", clock, listOf(stale)).published)
        val record = written("c1")
        assertEquals("Trail c1" to "line-c1", record.name to record.line)
    }

    @Test
    fun everyCandidateIsCheckedAPageAtATime() = runTest {
        val ids = (1000 until 1600).map { "c$it" }
        appView(emptyList(), duplicates = ids.toSet())
        val every = ids.map { SegmentCandidate(it, "ride", "Trail $it", "line-$it", "osm", "ODbL-1.0") }
        assertEquals(ids, publish(clients, "us-vermont", clock, every).duplicates)
        assertEquals(listOf(500, 100), fake.calls(Nsid.IMPORTER_CHECK_SEGMENT_CANDIDATES).map { it.body!!.getValue("ids").jsonArray.size })
    }

    @Test
    fun aFileOfAnotherRegionIsRefusedBeforeAnythingIsCalled() = runTest {
        val file = File.createTempFile("us-vermont", ".ndjson").apply { deleteOnExit() }
        file.writeText(
            """{"region":"us-vermont","importerVersion":$IMPORTER_VERSION}""" +
                "\n" +
                """{"id":"c1","region":"us-vermont","sport":"ride","name":"Trail","line":"_p~iF~ps|U_ulLnnqC","lengthMeters":900,""" +
                """"source":"osm","license":"ODbL-1.0","osmWays":[1]}""" +
                "\n",
        )
        val refused = assertFailsWith<IllegalArgumentException> { publishRegion(clients, "us-utah", file) }
        assertEquals(true, "us-vermont" in refused.message.orEmpty())
        assertEquals(emptyList(), fake.calls)
    }
}
