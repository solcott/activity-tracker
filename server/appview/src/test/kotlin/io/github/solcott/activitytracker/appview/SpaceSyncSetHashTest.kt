package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * The syncer's set-hash checks and its handling of `SpaceNotFound` (`spaces/appview-syncer`), against a fake Spaces PDS whose commits carry
 * real `data`.
 */
class SpaceSyncSetHashTest : SpaceSyncFixture() {
    private fun listings() = pds.requests.count { it.url.encodedPath.endsWith("listRecords") }

    /** alice's followers space announced and synced once, holding [rkeys]. */
    private fun ApplicationTestBuilder.synced(vararg rkeys: String) {
        rkeys.forEach { pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, it, activity()) }
        runBlocking { client.notifySpace(aliceFollowers) }
        runDue()
        assertEquals(rkeys.toSet(), activities().keys)
    }

    @Test
    fun aPullMatchingTheSetHashIsIndexedWithoutAListing() = testApplication {
        start()
        val self = space(alice, AthleteSpace.SELF)
        // Records the AppView never indexes still count toward the set hash.
        pds.put(self, Nsid.ACTOR_PRIVATE_DETAILS, "self", """{"details":{"weight":61500}}""")
        pds.put(self, Nsid.FEED_ACTIVITY, "3kf1", activity())
        client.notifySpace(self)
        runDue()
        assertTrue(syncState(self)!![SpaceSync.setHash] != null)
        val before = listings()

        pds.put(self, Nsid.FEED_ACTIVITY, "3kf2", activity())
        pds.put(self, Nsid.ACTOR_PRIVATE_DETAILS, "self", """{"details":{"weight":61000}}""")
        client.notifyWrite(self)
        runDue()
        assertEquals(setOf("3kf1", "3kf2"), activities().keys)
        assertEquals(before, listings(), "pulled, not backfilled")
        assertEquals(pds.rev(self), syncState(self)!![SpaceSync.repoRev])
    }

    @Test
    fun aWriteDuringABackfillStartsItOver() = testApplication {
        start()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        pds.duringNextListing = { pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf2", activity()) }
        client.notifySpace(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1", "3kf2"), activities().keys)
        assertEquals(2, listings(), "the first listing ran past its head")
        assertEquals(pds.rev(aliceFollowers), syncState(aliceFollowers)!![SpaceSync.repoRev])
        assertEquals(0, syncState(aliceFollowers)!![SpaceSync.failures])
    }

    @Test
    fun aBackfillThatNeverMatchesKeepsWhatWasIndexedAndBacksOff() = testApplication {
        start()
        synced("3kf1", "3kf2")
        val rev = syncState(aliceFollowers)!![SpaceSync.repoRev]

        pds.wrongData += aliceFollowers
        pds.delete(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf2")
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1", "3kf2"), activities().keys, "nothing dropped on a hash that doesn't add up")
        assertEquals(rev, syncState(aliceFollowers)!![SpaceSync.repoRev])
        assertEquals(1, syncState(aliceFollowers)!![SpaceSync.failures])

        pds.wrongData.clear()
        clock.now += 1.minutes
        runDue()
        assertEquals(setOf("3kf1"), activities().keys)
        assertEquals(0, syncState(aliceFollowers)!![SpaceSync.failures])
    }

    @Test
    fun aSpaceSyncedBeforeSetHashesIsBackfilled() = testApplication {
        start()
        synced("3kf1")
        transaction(db) { SpaceSync.update({ SpaceSync.space eq aliceFollowers.value }) { it[setHash] = null } }
        val before = listings()

        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf2", activity())
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1", "3kf2"), activities().keys)
        assertEquals(before + 1, listings())
        assertTrue(syncState(aliceFollowers)!![SpaceSync.setHash] != null)
    }

    @Test
    fun aClearedSpaceIsResyncedAndKeptSyncing() = testApplication {
        start()
        synced("3kf1", "3kf2")

        pds.clear(aliceFollowers)
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(emptyMap(), activities())
        assertEquals(pds.rev(aliceFollowers), syncState(aliceFollowers)!![SpaceSync.repoRev])

        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf3", activity())
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf3"), activities().keys)
    }

    @Test
    fun aSpaceClearedThenWrittenServesOnlyTheNewRecord() = testApplication {
        start()
        synced("3kf1", "3kf2")

        pds.clear(aliceFollowers)
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf3", activity())
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf3"), activities().keys)
    }

    @Test
    fun aMigratedRepoJustAdvances() = testApplication {
        start()
        synced("3kf1")
        val before = listings()

        pds.migrate(aliceFollowers)
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1"), activities().keys)
        assertEquals(pds.rev(aliceFollowers), syncState(aliceFollowers)!![SpaceSync.repoRev])
        assertEquals(before, listings(), "no resync")
    }

    @Test
    fun spaceNotFoundKeepsTheSpaceUntilItHasLastedADay() = testApplication {
        start()
        synced("3kf1")

        // The PDS can't reach the managing app, so it answers the AppView's new credential request with SpaceNotFound.
        pds.notFound += aliceFollowers
        clock.now += 15.minutes
        credentials.invalidate(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1"), activities().keys, "still served")
        val since = syncState(aliceFollowers)!![SpaceSync.notFoundSince]
        assertEquals(clock.now.toEpochMilliseconds(), since)

        clock.now += 23.hours
        credentials.invalidate(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1"), activities().keys, "23 h in")
        assertEquals(since, syncState(aliceFollowers)!![SpaceSync.notFoundSince])

        clock.now += 1.hours
        credentials.invalidate(aliceFollowers)
        runDue()
        assertEquals(emptyMap(), activities())
        assertEquals(null, syncState(aliceFollowers))
    }

    @Test
    fun aSyncThatSucceedsEndsTheNotFoundRun() = testApplication {
        start()
        synced("3kf1")

        pds.notFound += aliceFollowers
        clock.now += 15.minutes
        credentials.invalidate(aliceFollowers)
        runDue()
        assertTrue(syncState(aliceFollowers)!![SpaceSync.notFoundSince] != null)

        pds.notFound.clear()
        clock.now += 1.hours
        runDue()
        assertEquals(null, syncState(aliceFollowers)!![SpaceSync.notFoundSince])

        pds.notFound += aliceFollowers
        clock.now += 23.hours
        credentials.invalidate(aliceFollowers)
        runDue()
        assertEquals(clock.now.toEpochMilliseconds(), syncState(aliceFollowers)!![SpaceSync.notFoundSince], "a new run")
        assertEquals(setOf("3kf1"), activities().keys)
    }

    @Test
    fun anotherFailureEndsTheNotFoundRun() = testApplication {
        start()
        synced("3kf1")

        pds.notFound += aliceFollowers
        clock.now += 15.minutes
        credentials.invalidate(aliceFollowers)
        runDue()
        assertTrue(syncState(aliceFollowers)!![SpaceSync.notFoundSince] != null)

        pds.notFound.clear()
        pds.unreachable += Did(alice)
        clock.now += 1.hours
        runDue()
        assertEquals(null, syncState(aliceFollowers)!![SpaceSync.notFoundSince])
        assertEquals(2, syncState(aliceFollowers)!![SpaceSync.failures])
    }
}
