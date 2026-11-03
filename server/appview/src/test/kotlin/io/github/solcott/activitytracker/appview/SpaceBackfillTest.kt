package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.ActivityInvites
import io.github.solcott.activitytracker.appview.db.GroupPrefs
import io.github.solcott.activitytracker.appview.db.LeaderboardBrackets
import io.github.solcott.activitytracker.appview.db.SegmentNameVotes
import io.github.solcott.activitytracker.appview.db.SegmentPrefs
import io.github.solcott.activitytracker.appview.db.SegmentTracks
import io.github.solcott.activitytracker.appview.index.Indexer
import io.github.solcott.activitytracker.appview.index.spaceRkeys
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityInviteRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** A backfill drops every kind of record a space is synced for once the listing lacks it, though its delete op was compacted away. */
class SpaceBackfillTest : SpaceSyncFixture() {
    private val aliceSelf = space(alice, AthleteSpace.SELF)
    private val aliceInteractions = space(alice, AthleteSpace.INTERACTIONS)
    private val inviteRkey = ActivityInviteRecord.rkey("3kf1", Did(bob))

    @Test
    fun deletedSelfRecordsAreDroppedByABackfill() = testApplication {
        start()
        pds.put(aliceSelf, Nsid.FEED_ACTIVITY, "3kf1", activity())
        pds.put(aliceSelf, Nsid.FEED_SEGMENT_TRACK, "3kf1", segmentTrack("3kf1"))
        pds.put(aliceSelf, Nsid.ACTOR_SEGMENT_PREFS, "self", record(Nsid.ACTOR_SEGMENT_PREFS, """"matching":true"""))
        pds.put(aliceSelf, Nsid.ACTOR_GROUP_PREFS, "self", record(Nsid.ACTOR_GROUP_PREFS, """"mode":"connected""""))
        pds.put(aliceSelf, Nsid.ACTOR_LEADERBOARD_BRACKETS, "self", record(Nsid.ACTOR_LEADERBOARD_BRACKETS, null))
        pds.put(aliceSelf, Nsid.FEED_ACTIVITY_INVITE, inviteRkey, invite())
        client.notifySpace(aliceSelf)
        runDue()
        assertEquals(SelfRows(tracks = 1, segmentPrefs = 1, groupPrefs = 1, brackets = 1, invites = 1), selfRows())

        // While the AppView is away, all of them but the activity are deleted and the op log compacted past the deletes.
        for (collection in SELF_ONLY) pds.delete(aliceSelf, collection, if (collection == Nsid.FEED_SEGMENT_TRACK) "3kf1" else "self")
        pds.delete(aliceSelf, Nsid.FEED_ACTIVITY_INVITE, inviteRkey)
        pds.compactedTo[aliceSelf] = pds.rev(aliceSelf)
        clock.now += 2.hours
        runDue()
        assertEquals(SelfRows(), selfRows())
        assertEquals(setOf("3kf1"), activities().keys, "the listed activity stays")
    }

    @Test
    fun aDeletedNameVoteIsDroppedByABackfill() = testApplication {
        start()
        pds.put(aliceInteractions, Nsid.FEED_SEGMENT_NAME_VOTE, "3kv1", vote())
        pds.put(aliceInteractions, Nsid.FEED_SEGMENT_NAME_VOTE, "3kv2", vote())
        client.notifySpace(aliceInteractions)
        runDue()
        assertEquals(setOf("3kv1", "3kv2"), votes())

        pds.delete(aliceInteractions, Nsid.FEED_SEGMENT_NAME_VOTE, "3kv1")
        pds.compactedTo[aliceInteractions] = pds.rev(aliceInteractions)
        clock.now += 2.hours
        runDue()
        assertEquals(setOf("3kv2"), votes())
    }

    @Test
    fun everyCollectionASpaceIsSyncedForListsItsIndexedRkeys() {
        val keys = listOf(AthleteSpace.INTERACTIONS, AthleteSpace.SELF, Visibility.FOLLOWERS, Visibility.MUTUALS, "unknownTier")
        transaction(db) {
            for (key in keys) {
                val space = space(alice, key)
                for (collection in Indexer.spaceCollections(key)) assertEquals(
                    emptySet(),
                    spaceRkeys(space, collection),
                    "$key $collection",
                )
            }
            assertFailsWith<IllegalStateException> { spaceRkeys(aliceSelf, Nsid.ACTOR_PRIVATE_DETAILS) }
        }
    }

    private data class SelfRows(
        val tracks: Long = 0,
        val segmentPrefs: Long = 0,
        val groupPrefs: Long = 0,
        val brackets: Long = 0,
        val invites: Long = 0,
    )

    private fun selfRows() =
        transaction(db) {
            SelfRows(
                SegmentTracks.selectAll().count(),
                SegmentPrefs.selectAll().count(),
                GroupPrefs.selectAll().count(),
                LeaderboardBrackets.selectAll().count(),
                ActivityInvites.selectAll().count(),
            )
        }

    private fun votes() = transaction(db) { SegmentNameVotes.selectAll().map { it[SegmentNameVotes.uri].substringAfterLast('/') }.toSet() }

    private fun record(type: String, fields: String?) =
        """{"${'$'}type":"$type",${fields?.let { "$it," }.orEmpty()}"updatedAt":"2026-10-14T08:00:00Z"}"""

    private fun segmentTrack(rkey: String) =
        """{"${'$'}type":"${Nsid.FEED_SEGMENT_TRACK}","activity":"at://$alice/${Nsid.FEED_ACTIVITY}/$rkey",""" +
            """"createdAt":"2026-10-14T07:31:00Z",""" +
            """"data":{"${'$'}type":"blob","ref":{"${'$'}link":"bafkreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"},""" +
            """"mimeType":"application/octet-stream","size":10}}"""

    private fun invite() =
        """{"${'$'}type":"${Nsid.FEED_ACTIVITY_INVITE}","subject":"at://$alice/${Nsid.FEED_ACTIVITY}/3kf1","invitee":"$bob",""" +
            """"createdAt":"2026-10-14T08:00:00Z"}"""

    private fun vote() =
        """{"${'$'}type":"${Nsid.FEED_SEGMENT_NAME_VOTE}","subject":{"uri":"at://$bob/${Nsid.FEED_SEGMENT_NAME_PROPOSAL}/3kp1",""" +
            """"cid":"bafyreihwzzyxpay7qndclffr6usq5te3of4fra7yucklue2b5ckxhq7aeq"},"direction":"up","createdAt":"2026-10-14T08:00:00Z"}"""

    private companion object {
        val SELF_ONLY = listOf(Nsid.FEED_SEGMENT_TRACK, Nsid.ACTOR_SEGMENT_PREFS, Nsid.ACTOR_GROUP_PREFS, Nsid.ACTOR_LEADERBOARD_BRACKETS)
    }
}
