package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Pulls are indexed only under a commit whose MAC verifies (`spaces/appview-syncer`, Oct 22 alpha). */
class SpaceSyncMacTest : SpaceSyncFixture() {
    @Test
    fun aPullWhoseCommitNamesAnotherSpaceIndexesNothing() = testApplication {
        start()
        client.notifySpace(aliceFollowers)
        runDue()
        val synced = syncState(aliceFollowers)!![SpaceSync.repoRev]

        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        pds.commitSpace = space(alice, AthleteSpace.MUTUALS)
        client.notifyWrite(aliceFollowers)
        runDue()

        assertEquals(emptyMap(), activities())
        val state = syncState(aliceFollowers)!!
        assertEquals(synced, state[SpaceSync.repoRev])
        assertEquals(1, state[SpaceSync.failures])
    }

    @Test
    fun aMacThatFailsTwiceBacksOffAndIndexesNothing() = testApplication {
        start()
        val carolMutuals = space(carol, AthleteSpace.MUTUALS)
        client.notifySpace(aliceFollowers)
        client.notifySpace(carolMutuals)
        runDue()
        val resolutions = pds.requests.count { it.url.host == "plc.test" && it.url.encodedPath == "/$alice" }

        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        pds.put(carolMutuals, Nsid.FEED_ACTIVITY, "3kc1", activity())
        pds.macMode = FakeSpacesPds.MacMode.WRONG_KEY
        client.notifyWrite(aliceFollowers)
        runDue()

        assertEquals(emptyMap(), activities())
        val failed = syncState(aliceFollowers)!!
        assertEquals(1, failed[SpaceSync.failures])
        assertTrue(failed[SpaceSync.nextAttemptAt] > clock.now.toEpochMilliseconds(), "backed off")
        assertEquals(
            resolutions + 1,
            pds.requests.count { it.url.host == "plc.test" && it.url.encodedPath == "/$alice" },
            "the DID document was resolved again before giving up",
        )

        pds.macMode = FakeSpacesPds.MacMode.VALID
        clock.now += 1.hours
        runDue()
        assertEquals(setOf("3kf1", "3kc1"), activities().keys)
    }

    @Test
    fun aPullWithoutAMacIndexesNothing() = testApplication {
        start()
        client.notifySpace(aliceFollowers)
        runDue()
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        pds.macMode = FakeSpacesPds.MacMode.OMITTED
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(emptyMap(), activities())
        assertEquals(1, syncState(aliceFollowers)!![SpaceSync.failures])
    }

    @Test
    fun aRotatedKemKeyIsPickedUpOnTheRetry() = testApplication {
        start()
        client.notifySpace(aliceFollowers)
        runDue()
        pds.rotateKem(Did(alice))
        pds.put(aliceFollowers, Nsid.FEED_ACTIVITY, "3kf1", activity())
        client.notifyWrite(aliceFollowers)
        runDue()
        assertEquals(setOf("3kf1"), activities().keys)
        assertEquals(0, syncState(aliceFollowers)!![SpaceSync.failures])
    }
}
