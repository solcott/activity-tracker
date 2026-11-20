package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.update

/** `Forced resync of an account's spaces` (spaces/appview-syncer): what an operator's resync of one account does to its spaces. */
class SpaceResyncTest : SpaceSyncFixture() {
    private val bobInteractions = space(bob, AthleteSpace.INTERACTIONS)
    private val aliceMutuals = space(alice, AthleteSpace.MUTUALS)

    @Test
    fun aRecordTheSpaceNoLongerHoldsIsDroppedByAForcedResync() = testApplication {
        // Bob's likes of alice's activities, from his interactions space.
        start()
        pds.put(bobInteractions, Nsid.FEED_LIKE, "3kk1", like("3kr1"))
        client.notifySpace(bobInteractions)
        runDue()
        assertEquals(setOf("3kk1"), likes().keys)

        // A like the AppView holds that the space doesn't: its delete was missed. Ordinary pulls never notice.
        runBlocking {
            indexer.spaceWrite(bobInteractions, Nsid.FEED_LIKE, "3kk2", "bafy3kk2", AtJson.parseToJsonElement(like("3kr1")).jsonObject)
        }
        clock.now += 6.minutes
        runDue()
        assertEquals(setOf("3kk1", "3kk2"), likes().keys)

        runBlocking { syncer.resyncAccount(Did(bob)) }
        runDue()
        assertEquals(setOf("3kk1"), likes().keys)
        assertNotNull(syncState(bobInteractions)?.get(SpaceSync.setHash), "a successful backfill stores the set hash again")
    }

    @Test
    fun aForcedResyncWhoseBackfillFailsIsBackfilledAgain() = testApplication {
        start()
        pds.put(bobInteractions, Nsid.FEED_LIKE, "3kk1", like("3kr1"))
        client.notifySpace(bobInteractions)
        runDue()
        val hash = assertNotNull(syncState(bobInteractions)?.get(SpaceSync.setHash))
        runBlocking {
            indexer.spaceWrite(bobInteractions, Nsid.FEED_LIKE, "3kk2", "bafy3kk2", AtJson.parseToJsonElement(like("3kr1")).jsonObject)
        }

        runBlocking { syncer.resyncAccount(Did(bob)) }
        // A sync running when the resync was forced stores its set hash again, then the forced backfill fails.
        runBlocking { db.tx { SpaceSync.update({ SpaceSync.space eq bobInteractions.value }) { it[setHash] = hash } } }
        pds.duringNextListing = { error("The PDS went away mid-listing") }
        runDue()
        assertEquals(setOf("3kk1", "3kk2"), likes().keys, "nothing dropped by a failed backfill")

        // Past the one-minute backoff, well within the hour after which any space is backfilled again.
        clock.now += 2.minutes
        runDue()
        assertEquals(setOf("3kk1"), likes().keys, "still forced, so backfilled rather than pulled")
    }

    @Test
    fun aSpaceNeverAnnouncedStartsSyncing() = testApplication {
        start()
        pds.put(aliceMutuals, Nsid.FEED_ACTIVITY, "3kr1", activity("hidden"))
        assertNull(syncState(aliceMutuals))

        val queued = runBlocking { syncer.resyncAccount(Did(alice)) }
        assertEquals(4, queued, "followers, mutuals, self and interactions")
        runDue()
        assertEquals(setOf("3kr1"), activities().keys)
    }

    @Test
    fun aSpaceThatDoesntExistBacksOffAndKeepsTheRest() = testApplication {
        start()
        pds.put(bobInteractions, Nsid.FEED_LIKE, "3kk1", like("3kr1"))
        client.notifySpace(bobInteractions)
        runDue()
        val bobMutuals = space(bob, AthleteSpace.MUTUALS)
        pds.notFound += bobMutuals

        runBlocking { syncer.resyncAccount(Did(bob)) }
        runDue()
        val mutuals = assertNotNull(syncState(bobMutuals), "announced, so tracked while its PDS decides")
        assertNotNull(mutuals[SpaceSync.notFoundSince], "answered SpaceNotFound, so backing off rather than dropped")
        assertEquals(setOf("3kk1"), likes().keys)
    }
}
