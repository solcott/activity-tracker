package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.auth.AccountMarks
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A device whose athlete left Activity Tracker on another device (auth/leaving): it never creates again a space it used, and its outbox
 * sends nothing until the athlete rejoins.
 */
class LeftElsewhereTest {
    private val pds = FakeAthletePds()

    private suspend fun FakeAthletePds.Session.queue(collection: String, rkey: String, tier: String? = null) =
        OutboxWriter(db, clients, Clock.System)
            .put(
                collection,
                rkey,
                JsonObject.serializer(),
                buildJsonObject { put("\$type", collection) },
                space = tier?.let(clients.tiers::uri),
            )

    private fun FakeAthletePds.Session.processor() = OutboxProcessor(db, clients, Logger)

    @Test
    fun aDeviceRemembersTheSpacesItCreates() = runTest {
        val s = pds.session()
        s.queue(Nsid.FEED_ACTIVITY, "3ka", AthleteSpace.SELF)
        assertEquals(0, s.processor().drain())
        assertEquals(setOf(AthleteSpace.SELF), AccountMarks(s.db).knownSpaces(pds.did))
    }

    @Test
    fun theDesktopAfterALeaveOnThePhoneSendsNothing() = runTest {
        val s = pds.session()
        s.queue(Nsid.FEED_ACTIVITY, "3ka", AthleteSpace.SELF)
        s.processor().drain()
        // The phone left: every space is gone. A later session on the desktop has a ride queued.
        pds.stored.clear()
        pds.created.clear()
        val desktop = pds.session()
        AccountMarks(desktop.db).rememberSpace(pds.did, AthleteSpace.SELF)
        desktop.queue(Nsid.FEED_ACTIVITY, "3kb")
        desktop.queue(Nsid.FEED_ACTIVITY, "3kc", AthleteSpace.FOLLOWERS)
        val writes = pds.writes.size

        assertEquals(2, desktop.processor().drain())
        assertTrue(desktop.clients.accountLeft.value)
        assertEquals(writes, pds.writes.size, "nothing sent, not even to the repo")
        assertFalse(pds.spaceExists(AthleteSpace.SELF))
        assertFalse(pds.spaceExists(AthleteSpace.FOLLOWERS))
    }

    @Test
    fun aKnownSpaceThatIsGoneIsNeverCreatedAgain() = runTest {
        val s = pds.session()
        AccountMarks(s.db).rememberSpace(pds.did, AthleteSpace.FOLLOWERS)
        // `self` is unknown here, so the session's check passes; the Followers space is the one found gone.
        s.queue(Nsid.FEED_ACTIVITY, "3kc", AthleteSpace.FOLLOWERS)

        assertEquals(1, s.processor().drain())
        assertTrue(s.clients.accountLeft.value)
        assertEquals(emptyList(), pds.created)
    }

    @Test
    fun aNewDeviceCreatesSpacesAsItNeedsThem() = runTest {
        val s = pds.session()
        s.queue(Nsid.FEED_ACTIVITY, "3kc", AthleteSpace.FOLLOWERS)
        assertEquals(0, s.processor().drain())
        assertFalse(s.clients.accountLeft.value)
        assertEquals(listOf(AthleteSpace.FOLLOWERS), pds.created)
    }

    @Test
    fun aCheckThatCantReachThePdsCountsAsStillHere() = runTest {
        val s = pds.session()
        AccountMarks(s.db).rememberSpace(pds.did, AthleteSpace.SELF)
        pds.pdsUnreachable = true
        assertFalse(s.clients.checkStillHere())
        pds.pdsUnreachable = false
        // Not checked yet, so the next drain asks again and finds `self` gone.
        assertTrue(s.clients.checkStillHere())
    }

    @Test
    fun rejoiningCreatesTheSpacesAgainAndSends() = runTest {
        val s = pds.session()
        AccountMarks(s.db).rememberSpace(pds.did, AthleteSpace.SELF)
        s.queue(Nsid.FEED_ACTIVITY, "3ka", AthleteSpace.SELF)
        assertEquals(1, s.processor().drain())

        s.clients.rejoin()
        assertEquals(0, s.processor().drain())
        assertFalse(s.clients.accountLeft.value)
        assertTrue(pds.spaceExists(AthleteSpace.SELF))
    }
}
