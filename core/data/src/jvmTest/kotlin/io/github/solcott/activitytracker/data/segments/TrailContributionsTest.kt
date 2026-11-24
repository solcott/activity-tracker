package io.github.solcott.activitytracker.data.segments

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewerState
import io.github.solcott.activitytracker.lexicon.app.TrailAttributes
import io.github.solcott.activitytracker.lexicon.app.TrailDetail
import io.github.solcott.activitytracker.lexicon.app.TrailDetailInfo
import io.github.solcott.activitytracker.lexicon.app.TrailDetailSuggestionView
import io.github.solcott.activitytracker.lexicon.app.TrailDetailViewerVote
import io.github.solcott.activitytracker.lexicon.app.TrailDetailVoteDirection
import io.github.solcott.activitytracker.lexicon.app.TrailSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Suggesting, voting on and confirming trail details in the data layer (segments/trail-detail-suggestions). */
class TrailContributionsTest {
    private val pds = FakeAthletePds()
    private val alice = pds.did
    private val bob = Did("did:plc:bob")
    private val segment = AtUri.of(Did("did:plc:importer"), Nsid.FEED_SEGMENT, "osm-1")
    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }

    private val bobs =
        TrailDetailSuggestionView(
            uri = AtUri.of(bob, Nsid.FEED_TRAIL_DETAIL_SUGGESTION, "3kbob"),
            cid = Cid("bafybob"),
            detail = TrailDetail.DOG,
            value = TrailAttributes(dog = "no"),
            author = ProfileViewBasic(bob, Handle("bob.test")),
            openedAt = Instant.parse("2026-11-10T00:00:00Z"),
            up = 2,
            down = 1,
        )

    private val view =
        SegmentView(
            uri = segment,
            cid = Cid("bafyseg"),
            creator = ProfileViewBasic(Did("did:plc:importer"), Handle("importer.test")),
            record = SegmentRecord("Trail 214", "ride", "_p~iF~ps|U", "osm", "ODbL-1.0", Instant.parse("2026-01-01T00:00:00Z")),
            distanceMeters = 1200,
            trail = TrailAttributes(dog = "leashed"),
            trailDetails = listOf(TrailDetailInfo(TrailDetail.DOG, TrailSource.OSM, confirmations = 4)),
            trailSuggestions = listOf(bobs),
            viewer = SegmentViewerState(canSuggestTrailDetails = true, canVoteOnTrailDetails = true),
        )

    private suspend fun contributions(scope: String? = null): TrailContributions {
        val s = pds.session(scope)
        return TrailContributions(s.clients, OutboxWriter(s.db, s.clients, clock), OutboxProcessor(s.db, s.clients, Logger), clock, s.db)
    }

    private fun stored(place: String, collection: String): Map<String, JsonObject> =
        pds.stored[place].orEmpty().filterKeys { it.startsWith("$collection/") }

    @Test
    fun aSuggestionGoesInTheRepoAndShowsAtOnce() = runTest {
        val trail = contributions()
        val uri = trail.suggest(segment, TrailDetail.LIT, TrailAttributes(lit = "yes"))
        assertEquals(listOf("put ${pds.repo} ${Nsid.FEED_TRAIL_DETAIL_SUGGESTION}/${uri.rkey}"), pds.writes)
        val record = stored(pds.repo, Nsid.FEED_TRAIL_DETAIL_SUGGESTION).values.single()
        assertEquals("lit", record["detail"]?.jsonPrimitive?.content)
        assertEquals("yes", record["value"]!!.jsonObject["lit"]?.jsonPrimitive?.content)
        val shown = trail.applyTo(view)
        assertEquals(listOf(TrailDetail.DOG, TrailDetail.LIT), shown.trailSuggestions.map { it.detail })
        assertEquals(listOf(TrailDetail.LIT), shown.viewer?.closedTrailDetails)
        assertEquals(2, trail.applyTo(view.copy(trailSuggestions = listOf(bobs, shown.trailSuggestions.last()))).trailSuggestions.size)
    }

    @Test
    fun aValueNoRiderMaySuggestIsRefused() = runTest {
        val trail = contributions()
        assertFailsWith<IllegalArgumentException> {
            trail.suggest(segment, TrailDetail.WEBSITE, TrailAttributes(website = "https://x.test"))
        }
        assertEquals(emptyList(), pds.writes)
    }

    @Test
    fun aVoteGoesInTheInteractionsSpaceAndMovesTheTotalsOnce() = runTest {
        val trail = contributions()
        trail.vote(segment, bobs, TrailDetailVoteDirection.UP, current = null)
        val (key, record) = stored(AthleteSpace.INTERACTIONS, Nsid.FEED_TRAIL_DETAIL_VOTE).entries.single()
        assertEquals(emptyMap(), stored(pds.repo, Nsid.FEED_TRAIL_DETAIL_VOTE), "nothing public records it")
        assertEquals(bobs.uri.value, record["subject"]!!.jsonObject["uri"]?.jsonPrimitive?.content)
        val shown = trail.applyTo(view).trailSuggestions.single()
        assertEquals(3 to 1, shown.up to shown.down)
        val voteUri = AtUri.of(alice, Nsid.FEED_TRAIL_DETAIL_VOTE, key.substringAfter('/'))
        val caughtUp = bobs.copy(up = 3, viewerVote = TrailDetailViewerVote(voteUri, TrailDetailVoteDirection.UP))
        assertEquals(caughtUp, trail.applyTo(view.copy(trailSuggestions = listOf(caughtUp))).trailSuggestions.single(), "counted once")
    }

    @Test
    fun aConfirmationNamesTheValueAndMovesTheCountOnce() = runTest {
        val trail = contributions()
        trail.confirm(segment, TrailDetail.DOG, view.trail!!, TrailDetailVoteDirection.STILL, current = null)
        val record = stored(AthleteSpace.INTERACTIONS, Nsid.FEED_TRAIL_DETAIL_VOTE).values.single()
        assertEquals(segment.value, record["segment"]?.jsonPrimitive?.content)
        assertEquals("dog", record["detail"]?.jsonPrimitive?.content)
        assertEquals(setOf("dog"), record["value"]!!.jsonObject.keys, "only the detail's own fields")
        assertEquals("still", record["direction"]?.jsonPrimitive?.content)
        val info = trail.applyTo(view).trailDetails.single()
        assertEquals(5, info.confirmations)
        assertEquals(TrailDetailVoteDirection.STILL, info.viewerConfirmation?.direction)
        val changed = info.viewerConfirmation
        trail.confirm(segment, TrailDetail.DOG, view.trail!!, TrailDetailVoteDirection.CHANGED, current = changed)
        assertEquals(4, trail.applyTo(view).trailDetails.single().confirmations, "changed takes the still back")
        assertEquals(1, stored(AthleteSpace.INTERACTIONS, Nsid.FEED_TRAIL_DETAIL_VOTE).size, "the same record, rewritten")
    }

    @Test
    fun theQuickCheckShowsOncePerSeason() = runTest {
        val trail = contributions()
        assertTrue(trail.quickCheckDue(segment))
        trail.quickCheckDone(segment)
        assertFalse(trail.quickCheckDue(segment))
    }

    @Test
    fun aSessionThatCantWriteVotesIsRefusedBeforeAnythingChanges() = runTest {
        val trail = contributions(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}")
        assertFalse(trail.canVote)
        assertFailsWith<XrpcException> { trail.vote(segment, bobs, TrailDetailVoteDirection.UP, current = null) }
        assertFailsWith<XrpcException> { trail.confirm(segment, TrailDetail.DOG, view.trail!!, TrailDetailVoteDirection.STILL, null) }
        assertEquals(view, trail.applyTo(view))
        assertTrue(pds.writes.isEmpty())
    }
}
