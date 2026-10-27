package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.RejectedChange
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Changes the PDS refused (data/outbox): listed for the athlete, retried, or discarded to show what the PDS holds. */
class RejectedChangesTest {
    private val pds = FakeAthletePds()
    private val start = Instant.parse("2026-10-14T09:00:00Z")

    private fun draft(title: String = "Morning ride", startedAt: Instant = start) =
        ActivityDraft(
            type = ActivityType(Sport.RIDE),
            title = title,
            description = null,
            startedAt = startedAt,
            elapsed = 60.minutes,
            streams = ActivityStreamData(time = (0..3600 step 10).toList(), heartRate = (0..3600 step 10).map { 140 }),
        )

    private fun storedRecord(uri: AtUri) =
        AtJson.decodeFromJsonElement(
            ActivityRecord.serializer(),
            pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
        )

    private fun view(uri: AtUri, record: ActivityRecord, author: Did = pds.did) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(author, Handle("alice.test")),
            record = record,
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
        )

    /** Bob's activity, which Alice likes. */
    private val bobs = AtUri("at://did:plc:bob/${Nsid.FEED_ACTIVITY}/3kbob")

    @Test
    fun aRejectedActivityAndARejectedLikeAreTwoChanges() = runTest {
        val s = pds.session()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        s.activities.publish(draft())
        pds.failPutsOf = Nsid.FEED_LIKE
        s.activities.toggleLike(view(bobs, ActivityRecord(Sport.RIDE, start, 3600, start), author = Did("did:plc:bob")))

        val changes = s.rejected.changes.first()
        assertEquals(listOf(RejectedChange.Kind.Activity, RejectedChange.Kind.Like), changes.map { it.kind })
        assertEquals("Morning ride", changes.first().title)
        assertEquals(start, changes.first().startedAt)
        assertEquals(null, changes.last().title)
        assertEquals(0, s.activities.pendingWrites.first(), "neither is still uploading, and the activity's other records are held")
    }

    @Test
    fun retryingAnAcceptedActivitySendsItThenWhatItHeld() = runTest {
        val s = pds.session()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        val uri = s.activities.publish(draft())
        pds.failPutsOf = null
        pds.writes.clear()

        assertEquals(0, s.rejected.retry(s.rejected.changes.first().single()))

        assertEquals("put ${pds.repo} ${Nsid.FEED_ACTIVITY}/${uri.rkey}", pds.writes.first(), "the activity goes first")
        assertTrue(pds.writes.drop(1).all { it.endsWith("/${uri.rkey}") } && pds.writes.size > 1, "then the records it held back")
        assertEquals(emptyList(), s.rejected.changes.first())
        assertEquals(emptyList(), s.db.outbox().pending(pds.did.value))
    }

    @Test
    fun aRetryRefusedAgainStaysRejected() = runTest {
        val s = pds.session()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        s.activities.publish(draft())
        val queued = s.db.outbox().pending(pds.did.value).size
        pds.writes.clear()

        s.rejected.retry(s.rejected.changes.first().single())

        assertEquals(1, s.rejected.changes.first().size)
        assertEquals(emptyList(), pds.writes, "the held records aren't sent")
        assertEquals(queued, s.db.outbox().pending(pds.did.value).size)
    }

    @Test
    fun discardingANewActivityRemovesItAndWhatItHeld() = runTest {
        val s = pds.session()
        // A day earlier, so it doesn't overlap the one refused.
        val kept = s.activities.publish(draft("Kept", startedAt = start - 24.hours))
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        val uri = s.activities.publish(draft())

        s.rejected.discard(s.rejected.changes.first().single())

        assertEquals(emptyList(), s.db.outbox().pending(pds.did.value))
        assertEquals(emptyList(), s.rejected.changes.first())
        // Readers that count queued activities as the athlete's own (imports' duplicate check) read the outbox, now without it.
        assertEquals(emptySet(), pds.stored.values.flatMap { it.keys }.filter { it.endsWith("/${uri.rkey}") }.toSet())
        assertEquals(setOf(pds.repo), pds.placesOf(Nsid.FEED_ACTIVITY, kept.rkey!!), "the published one stays")
    }

    @Test
    fun discardingARefusedEditShowsThePublishedRecord() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())
        val published = view(uri, storedRecord(uri))
        pds.served = GetActivity.Output(published)
        s.cache(published)
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        s.manager.edit(published, ActivityEdit(title = "Renamed", description = "", diveSite = null, media = emptyList()))
        assertEquals("Renamed", s.feed.rewrites.observe(uri).first()?.title)

        s.rejected.discard(s.rejected.changes.first().single())

        assertNull(s.feed.rewrites.observe(uri).first(), "the edit is no longer laid over the AppView's view")
        assertEquals("Morning ride", s.cached(published).record.title)
        assertEquals("Morning ride", storedRecord(uri).title)
        assertEquals(emptyList(), s.db.outbox().pending(pds.did.value))
    }

    @Test
    fun discardingARejectedLikeForgetsIt() = runTest {
        val s = pds.session()
        val activity = view(bobs, ActivityRecord(Sport.RIDE, start, 3600, start), author = Did("did:plc:bob"))
        pds.served = GetActivity.Output(activity)
        pds.failPutsOf = Nsid.FEED_LIKE
        s.activities.toggleLike(activity)
        assertEquals(1, s.feed.activity(bobs).likeCount, "the like is laid over the AppView's view")

        s.rejected.discard(s.rejected.changes.first().single())

        val shown = s.feed.activity(bobs)
        assertEquals(0, shown.likeCount, "the AppView's view stands")
        assertNull(shown.viewer?.like)
        assertEquals(emptyList(), s.db.outbox().pending(pds.did.value))
    }
}
