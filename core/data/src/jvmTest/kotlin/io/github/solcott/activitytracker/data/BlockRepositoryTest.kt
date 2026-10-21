package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.FakeAthletePds.Session
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.BlockSource
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetBlocks
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.ProfileViewerState
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive

/**
 * Blocking and muting in the data layer (`graph/blocks`, `graph/mutes`): where the records go, and what shows before the AppView agrees.
 */
class BlockRepositoryTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val bob = Did("did:plc:bob")
    private val carol = Did("did:plc:carol")
    private val writes = pds.writes
    private val stored = pds.stored

    private fun basic(of: Did) = ProfileViewBasic(of, Handle("${of.value.substringAfterLast(':')}.test"))

    private fun activity(author: Did, rkey: String, comments: Long = 0) =
        ActivityView(
            uri = AtUri.of(author, Nsid.FEED_ACTIVITY, rkey),
            cid = Cid("bafy$rkey"),
            author = basic(author),
            record =
                ActivityRecord(
                    sport = Sport.RUN,
                    startedAt = Instant.parse("2026-10-14T07:00:00Z"),
                    elapsedTime = 1800,
                    createdAt = Instant.parse("2026-10-14T07:31:00Z"),
                ),
            likeCount = 0,
            commentCount = comments,
            indexedAt = Instant.parse("2026-10-14T07:32:00Z"),
            visibility = Visibility.PUBLIC,
            viewer = ActivityViewerState(),
        )

    private fun commentBy(author: Did, rkey: String) =
        CommentView(
            AtUri.of(author, Nsid.FEED_COMMENT, rkey),
            Cid("bafy$rkey"),
            basic(author),
            "Nice",
            Instant.parse("2026-10-14T08:00:00Z"),
        )

    private fun profile(of: Did, viewer: ProfileViewerState = ProfileViewerState()) = ProfileView(of, basic(of).handle, viewer = viewer)

    private suspend fun Session.timeline() = feed.timeline().first().map { it.author.did }

    @Test
    fun aBlockGoesOnlyInTheInteractionsSpaceKeyedBySubject() = runTest {
        val s = pds.session()
        val uri = s.blocks.setBlocked(bob, blocked = true)
        assertEquals(AtUri.of(did, Nsid.GRAPH_BLOCK, bob.value), uri)
        assertEquals(listOf("put ${AthleteSpace.INTERACTIONS} ${Nsid.GRAPH_BLOCK}/${bob.value}"), writes)
        assertEquals(setOf(AthleteSpace.INTERACTIONS), pds.placesOf(Nsid.GRAPH_BLOCK, bob.value), "nothing public records it")
        assertEquals(
            bob.value,
            stored.getValue(AthleteSpace.INTERACTIONS).getValue("${Nsid.GRAPH_BLOCK}/${bob.value}")["subject"]?.jsonPrimitive?.content,
        )

        s.blocks.setBlocked(bob, blocked = true)
        assertEquals(1, stored.getValue(AthleteSpace.INTERACTIONS).keys.count { it.startsWith(Nsid.GRAPH_BLOCK) }, "one block per account")
        assertNull(s.blocks.setBlocked(did, blocked = true), "the athlete can't block themselves")
    }

    @Test
    fun unblockingDeletesThatKeyAndAMissingOneIsDone() = runTest {
        val s = pds.session()
        s.blocks.setBlocked(carol, blocked = false)
        assertEquals(0, s.db.outbox().pending(did.value).size, "a block that isn't there is deleted already")
        s.blocks.setBlocked(bob, blocked = true)
        s.blocks.setBlocked(bob, blocked = false)
        assertEquals(emptySet(), pds.placesOf(Nsid.GRAPH_BLOCK, bob.value))
    }

    @Test
    fun aSessionWithoutTheScopeIsRefusedBeforeAnythingChanges() = runTest {
        val s = pds.session(scope = "${OAuthClientConfig.DEFAULT_SCOPE}&collection=${Nsid.FEED_ACTIVITY}&collection=${Nsid.FEED_COMMENT}")
        val e = assertFailsWith<XrpcException> { s.blocks.setBlocked(bob, blocked = true) }
        assertEquals(SpaceErrors.SCOPE_MISSING, e.error)
        assertEquals(SpaceErrors.SCOPE_MISSING, assertFailsWith<XrpcException> { s.blocks.setMuted(bob, muted = true) }.error)
        assertEquals(emptyList(), writes)
        assertNull(s.feed.localBlocks.blocked(bob), "nothing shows blocked")
    }

    @Test
    fun aBlockLeavesEveryFeedAtOnceAndAMuteOnlyTheTimeline() = runTest {
        val s = pds.session()
        s.cache(activity(bob, "3kb"))
        s.cache(activity(carol, "3kc"))
        s.blocks.setMuted(carol, muted = true)
        assertEquals(listOf(bob), s.timeline(), "a muted account leaves the timeline")

        s.blocks.setBlocked(bob, blocked = true)
        assertEquals(emptyList(), s.timeline())
        s.blocks.setBlocked(bob, blocked = false)
        s.blocks.setMuted(carol, muted = false)
        assertEquals(setOf(bob, carol), s.timeline().toSet(), "and both come back at once")
    }

    @Test
    fun theProfileShowsTheBlockUntilTheAppViewServesIt() = runTest {
        val s = pds.session()
        s.blocks.setBlocked(bob, blocked = true)
        s.blocks.setMuted(carol, muted = true)
        assertEquals(AtUri.of(did, Nsid.GRAPH_BLOCK, bob.value), s.feed.localBlocks.applyTo(profile(bob)).viewer?.blocking)
        assertEquals(AtUri.of(did, Nsid.GRAPH_MUTE, carol.value), s.feed.localBlocks.applyTo(profile(carol)).viewer?.muting)
        s.blocks.setBlocked(bob, blocked = false)
        val served = profile(bob, ProfileViewerState(blocking = AtUri.of(did, Nsid.GRAPH_BLOCK, bob.value)))
        assertNull(s.feed.localBlocks.applyTo(served).viewer?.blocking, "the athlete's unblock wins until the AppView catches up")
    }

    @Test
    fun blockedCommentsLeaveTheListAndTheCountOnceUntilTheAppViewCatchesUp() = runTest {
        val s = pds.session()
        val carols = activity(carol, "3kc", comments = 2)
        val bobs = commentBy(bob, "3kc1")
        val alices = commentBy(Did("did:plc:dave"), "3kc2")
        pds.served = GetActivity.Output(carols, listOf(bobs, alices))
        pds.profiles[bob.value] = profile(bob)
        val page = s.feed.comments(carols.uri)

        s.blocks.setBlocked(bob, blocked = true)
        val shown = page.threads.orEmpty().map { s.feed.localBlocks.applyTo(it) }
        assertEquals(listOf(null, alices), shown.map { it.root }, "bob's comment leaves its place at once")
        assertEquals(1, s.feed.activity(carols.uri).commentCount, "and the count drops by it")
        assertEquals(1, s.feed.activity(carols.uri).commentCount, "on every reload until the AppView catches up")

        // The AppView now serves the block: it lists and counts bob's comment no more.
        pds.profiles[bob.value] = profile(bob, ProfileViewerState(blocking = AtUri.of(did, Nsid.GRAPH_BLOCK, bob.value)))
        pds.served = GetActivity.Output(carols.copy(commentCount = 1), listOf(alices))
        assertEquals(1, s.feed.activity(carols.uri).commentCount, "not taken off twice")
    }

    @Test
    fun aBlockerTheAppViewHidesSettlesToo() = runTest {
        val s = pds.session()
        val carols = activity(carol, "3kc", comments = 1)
        pds.served = GetActivity.Output(carols, listOf(commentBy(bob, "3kc1")))
        s.feed.comments(carols.uri)
        s.blocks.setBlocked(bob, blocked = true)
        // Bob blocks alice too, so the AppView answers his profile as unknown, and already leaves his comment out.
        pds.served = GetActivity.Output(carols.copy(commentCount = 0), emptyList())
        assertEquals(0, s.feed.activity(carols.uri).commentCount)
    }

    @Test
    fun listsLeaveOutWhatTheAthleteRemovedThisSession() = runTest {
        val s = pds.session()
        pds.blocks =
            listOf(
                GetBlocks.BlockView(basic(bob), listOf(BlockSource.APP), Instant.parse("2026-10-21T09:00:00Z")),
                GetBlocks.BlockView(basic(carol), listOf(BlockSource.APP, BlockSource.BLUESKY), Instant.parse("2026-10-20T09:00:00Z")),
            )
        pds.mutes = listOf(basic(bob))
        s.blocks.setBlocked(bob, blocked = false)
        s.blocks.setBlocked(carol, blocked = false)
        s.blocks.setMuted(bob, muted = false)
        assertEquals(listOf(carol), s.blocks.blocks().blocks.map { it.actor.did }, "a Bluesky block still holds")
        assertEquals(emptyList(), s.blocks.mutes().mutes)
    }
}
