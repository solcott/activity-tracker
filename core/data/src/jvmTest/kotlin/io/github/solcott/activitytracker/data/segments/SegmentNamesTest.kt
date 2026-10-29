package io.github.solcott.activitytracker.data.segments

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Naming segments in the data layer (segments/segment-names): reading names, where proposals and votes go, and what shows before. */
class SegmentNamesTest {
    private val pds = FakeAthletePds()
    private val alice = pds.did
    private val bob = Did("did:plc:bob")
    private val segment = AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "osm-1")
    private val now = Instant.parse("2026-10-28T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }

    private val bobs =
        SegmentNameProposalView(
            uri = AtUri.of(bob, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kbob"),
            cid = Cid("bafybob"),
            name = "Rooty Run",
            proposer = ProfileViewBasic(bob, Handle("bob.test")),
            openedAt = Instant.parse("2026-10-20T00:00:00Z"),
            up = 3,
            down = 1,
        )
    private val alices =
        bobs.copy(
            uri = AtUri.of(alice, Nsid.FEED_SEGMENT_NAME_PROPOSAL, "3kali"),
            cid = Cid("bafyali"),
            name = "Wiggly Woods",
            proposer = ProfileViewBasic(alice, Handle("alice.test")),
        )

    private val view =
        SegmentView(
            uri = segment,
            cid = Cid("bafyseg"),
            creator = ProfileViewBasic(Did("did:plc:importer"), Handle("importer.test")),
            record = SegmentRecord("Trail 214", "ride", "_p~iF~ps|U", "osm", "ODbL-1.0", Instant.parse("2026-01-01T00:00:00Z")),
            distanceMeters = 1200,
            name = "Wiggly Woods",
            nameProposals = listOf(bobs),
            viewer = SegmentViewerState(canProposeName = true, canVoteOnNames = true),
        )

    private suspend fun names(scope: String? = null): SegmentNames {
        val s = pds.session(scope)
        return SegmentNames(s.clients, OutboxWriter(s.db, s.clients, clock), OutboxProcessor(s.db, s.clients, Logger), clock)
    }

    private fun stored(place: String, collection: String): Map<String, JsonObject> =
        pds.stored[place].orEmpty().filterKeys { it.startsWith("$collection/") }

    @Test
    fun theCurrentNameComesFromTheAppViewOrFallsBackToTheRecord() = runTest {
        for ((served, expected) in listOf(view to "Wiggly Woods", view.copy(name = null, nameProposals = emptyList()) to "Trail 214")) {
            val json = AtJson.encodeToJsonElement(GetSegment.Output.serializer(), GetSegment.Output(served)).toString()
            val http =
                HttpClient(
                    MockEngine { request ->
                        val headers = headersOf("Content-Type", "application/json")
                        if (request.url.encodedPath.endsWith("getServiceAuth")) respond("""{"token":"jwt"}""", headers = headers)
                        else respond(json, headers = headers)
                    }
                )
            val (_, _, clients) = signedInTestSession(http, did = alice)
            val read = SegmentRepository(clients).segment(segment)
            assertEquals(expected, read.currentName)
            assertEquals("Trail 214", read.record.name)
            assertEquals(served.nameProposals, read.nameProposals)
        }
    }

    @Test
    fun aProposalGoesInTheRepoAndShowsAtOnce() = runTest {
        val names = names()
        val uri = names.propose(segment, "Rooty Ridge")
        assertEquals(listOf("put ${pds.repo} ${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/${uri.rkey}"), pds.writes)
        val record = stored(pds.repo, Nsid.FEED_SEGMENT_NAME_PROPOSAL).values.single()
        assertEquals(segment.value, record["segment"]?.jsonPrimitive?.content)
        assertEquals("Rooty Ridge", record["name"]?.jsonPrimitive?.content)

        val shown = names.applyTo(view)
        assertEquals(listOf("Rooty Run", "Rooty Ridge"), shown.nameProposals.map { it.name })
        assertFalse(shown.viewer!!.canProposeName)
        assertEquals(
            2,
            names.applyTo(view.copy(nameProposals = listOf(bobs, shown.nameProposals.last()))).nameProposals.size,
            "listed, it isn't added twice",
        )
    }

    @Test
    fun anEditRewritesTheSameRkeyAndClearsTheTotalsUntilListed() = runTest {
        val names = names()
        val mine = view.copy(nameProposals = listOf(alices))
        names.edit(segment, alices.uri, "Wiggly Woods Loop")
        assertEquals(listOf("put ${pds.repo} ${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kali"), pds.writes)
        val shown = names.applyTo(mine).nameProposals.single()
        assertEquals("Wiggly Woods Loop", shown.name)
        assertEquals(0 to 0, shown.up to shown.down)
        assertEquals(now, shown.openedAt)

        val listed = alices.copy(name = "Wiggly Woods Loop", up = 2, down = 0)
        assertEquals(
            listed,
            names.applyTo(mine.copy(nameProposals = listOf(listed))).nameProposals.single(),
            "the AppView's totals win once it lists the edit",
        )
    }

    @Test
    fun deletingHidesTheProposalAndOffersProposeAgain() = runTest {
        val names = names()
        val mine =
            view.copy(nameProposals = listOf(alices, bobs), viewer = SegmentViewerState(canProposeName = false, canVoteOnNames = true))
        names.delete(segment, alices.uri)
        assertEquals(listOf("delete ${pds.repo} ${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kali"), pds.writes)
        val shown = names.applyTo(mine)
        assertEquals(listOf(bobs), shown.nameProposals)
        assertTrue(shown.viewer!!.canProposeName)
    }

    @Test
    fun aVoteGoesInTheInteractionsSpaceAndMovesTheTotalsOnce() = runTest {
        val names = names()
        names.vote(segment, bobs, SegmentNameVoteDirection.UP, current = null)
        val (key, record) = stored(AthleteSpace.INTERACTIONS, Nsid.FEED_SEGMENT_NAME_VOTE).entries.single()
        assertEquals(emptyMap(), stored(pds.repo, Nsid.FEED_SEGMENT_NAME_VOTE), "nothing public records it")
        assertEquals(bobs.uri.value, record["subject"]!!.jsonObject["uri"]?.jsonPrimitive?.content)
        assertEquals("bafybob", record["subject"]!!.jsonObject["cid"]?.jsonPrimitive?.content)

        val shown = names.applyTo(view).nameProposals.single()
        assertEquals(4 to 1, shown.up to shown.down)
        val voteUri = AtUri.of(alice, Nsid.FEED_SEGMENT_NAME_VOTE, key.substringAfter('/'))
        assertEquals(SegmentNameViewerVote(voteUri, SegmentNameVoteDirection.UP), shown.viewerVote)

        val caughtUp = bobs.copy(up = 4, viewerVote = SegmentNameViewerVote(voteUri, SegmentNameVoteDirection.UP))
        assertEquals(caughtUp, names.applyTo(view.copy(nameProposals = listOf(caughtUp))).nameProposals.single(), "counted once")
    }

    @Test
    fun changingAVoteRewritesItsRkeyAndTakingItBackDeletesIt() = runTest {
        val names = names()
        val current = SegmentNameViewerVote(AtUri.of(alice, Nsid.FEED_SEGMENT_NAME_VOTE, "3kvote"), SegmentNameVoteDirection.UP)
        val voted = bobs.copy(viewerVote = current)
        names.vote(segment, voted, SegmentNameVoteDirection.DOWN, current)
        assertEquals(listOf("put ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_SEGMENT_NAME_VOTE}/3kvote"), pds.writes)
        assertEquals(2 to 2, names.applyTo(view.copy(nameProposals = listOf(voted))).nameProposals.single().let { it.up to it.down })

        names.vote(segment, voted, null, current)
        assertEquals("delete ${AthleteSpace.INTERACTIONS} ${Nsid.FEED_SEGMENT_NAME_VOTE}/3kvote", pds.writes.last())
        val cleared = names.applyTo(view.copy(nameProposals = listOf(voted))).nameProposals.single()
        assertEquals(2 to 1, cleared.up to cleared.down)
        assertNull(cleared.viewerVote)
    }

    @Test
    fun anOfflineVoteShowsAtOnceAndIsSentLater() = runTest {
        val names = names()
        pds.offline = true
        names.vote(segment, bobs, SegmentNameVoteDirection.DOWN, current = null)
        assertEquals(3 to 2, names.applyTo(view).nameProposals.single().let { it.up to it.down })
        assertEquals(emptyMap(), stored(AthleteSpace.INTERACTIONS, Nsid.FEED_SEGMENT_NAME_VOTE))
        pds.offline = false
        names.vote(segment, alices, SegmentNameVoteDirection.UP, current = null)
        val subjects =
            stored(AthleteSpace.INTERACTIONS, Nsid.FEED_SEGMENT_NAME_VOTE).values.map {
                it["subject"]!!.jsonObject["uri"]?.jsonPrimitive?.content
            }
        assertEquals(setOf(bobs.uri.value, alices.uri.value), subjects.toSet(), "the next write's drain sent the queued vote too")
    }

    @Test
    fun aSessionThatCantWriteVotesIsRefusedBeforeAnythingChanges() = runTest {
        val names = names(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        assertFalse(names.canVote)
        assertFailsWith<XrpcException> { names.vote(segment, bobs, SegmentNameVoteDirection.UP, current = null) }
        assertEquals(view, names.applyTo(view))
        assertEquals(emptyList(), pds.writes)
    }
}
