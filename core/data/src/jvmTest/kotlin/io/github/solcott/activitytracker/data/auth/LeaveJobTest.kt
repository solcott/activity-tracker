package io.github.solcott.activitytracker.data.auth

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.export.testDataExports
import io.github.solcott.activitytracker.data.importsave.testImportSaves
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.location.ActivityRecorder
import io.github.solcott.activitytracker.location.FakeLocationTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Leaving Activity Tracker (auth/leaving): everything the app put in the account goes, in order, and an interrupted leave resumes. */
class LeaveJobTest {
    private val pds = FakeAthletePds()

    private class Leaving(
        val job: LeaveJob,
        val auth: AuthRepository,
        val marks: AccountMarks,
        val session: io.github.solcott.activitytracker.data.di.TestSession,
    )

    private suspend fun leaving(): Leaving {
        val s = signedInTestSession(pds.http)
        val marks = AccountMarks(s.db)
        val exports = testDataExports(s.db)
        val auth =
            AuthRepository(s.manager, s.db, pds.http, ActivityRecorder(FakeLocationTracker(), Clock.System), testImportSaves(s.db), exports)
        val job =
            LeaveJob(
                s.db,
                s.clients,
                marks,
                auth,
                ActivityRecorder(FakeLocationTracker(), Clock.System),
                testImportSaves(s.db),
                exports,
                Logger,
            )
        return Leaving(job, auth, marks, s)
    }

    private fun record(type: String) = buildJsonObject { put("\$type", type) }

    /** Alice's account: public activities, a profile, a Bluesky post, and records in her `self` and Followers spaces. */
    private fun account() {
        pds.stored[pds.repo] =
            mutableMapOf(
                "${Nsid.FEED_ACTIVITY}/3ka" to record(Nsid.FEED_ACTIVITY),
                "${Nsid.FEED_ACTIVITY}/3kb" to record(Nsid.FEED_ACTIVITY),
                "${Nsid.ACTOR_PROFILE}/self" to record(Nsid.ACTOR_PROFILE),
                "app.bsky.feed.post/3kp" to record("app.bsky.feed.post"),
            )
        pds.stored[AthleteSpace.SELF] = mutableMapOf("${Nsid.FEED_ACTIVITY_TRACK}/3ka" to record(Nsid.FEED_ACTIVITY_TRACK))
        pds.stored[AthleteSpace.FOLLOWERS] = mutableMapOf("${Nsid.FEED_ACTIVITY}/3kc" to record(Nsid.FEED_ACTIVITY))
    }

    @Test
    fun leavingRemovesTheAppsDataAndKeepsTheRest() = runTest {
        account()
        val l = leaving()
        OutboxWriter(l.session.db, l.session.clients, Clock.System)
            .put(Nsid.FEED_ACTIVITY, "3kq", JsonObject.serializer(), record(Nsid.FEED_ACTIVITY))
        l.marks.rememberSpace(pds.did, AthleteSpace.SELF)

        l.job.start()

        assertEquals(
            listOf("deleteSpace ${AthleteSpace.SELF}", "deleteSpace ${AthleteSpace.FOLLOWERS}"),
            pds.writes.filter { it.startsWith("deleteSpace") },
        )
        assertFalse(pds.spaceExists(AthleteSpace.SELF))
        assertFalse(pds.spaceExists(AthleteSpace.FOLLOWERS))
        assertEquals(setOf("app.bsky.feed.post/3kp"), pds.stored.getValue(pds.repo).keys, "Bluesky records stay")
        assertEquals(emptyList(), l.session.db.outbox().pending(pds.did.value), "the queued ride is discarded, never sent")
        assertFalse(pds.writes.any { it.contains("3kq") })
        assertNull(l.marks.leaving(pds.did))
        assertEquals(emptySet(), l.marks.knownSpaces(pds.did))
        assertEquals(EndedSession.Reason.Left, l.auth.lastEnd.value?.reason)
        assertNull(l.auth.session.value)
    }

    @Test
    fun aRetiredAppCollectionStillInTheRepoGoesToo() = runTest {
        pds.stored[pds.repo] = mutableMapOf("${Nsid.NAMESPACE}.feed.retired/1" to record("${Nsid.NAMESPACE}.feed.retired"))
        leaving().job.start()
        assertEquals(emptySet(), pds.stored[pds.repo].orEmpty().keys)
    }

    @Test
    fun anAccountWithNoSpacesLeavesToo() = runTest {
        pds.stored[pds.repo] = mutableMapOf("${Nsid.FEED_ACTIVITY}/3ka" to record(Nsid.FEED_ACTIVITY))
        val l = leaving()
        l.job.start()
        assertEquals(emptySet(), pds.stored[pds.repo].orEmpty().keys)
        assertEquals(EndedSession.Reason.Left, l.auth.lastEnd.value?.reason)
    }

    @Test
    fun aRateLimitIsWaitedOutAndTheLeaveCarriesOn() = runTest {
        account()
        pds.applyWritesRateLimited = 1
        val l = leaving()
        l.job.start()
        assertEquals(setOf("app.bsky.feed.post/3kp"), pds.stored.getValue(pds.repo).keys)
        assertNull(l.job.progress.value?.error)
    }

    @Test
    fun aRefusalStopsTheLeaveAndRetryFinishesIt() = runTest {
        account()
        pds.failApplyWritesOf = Nsid.FEED_ACTIVITY
        val l = leaving()
        l.job.start()

        assertIs<Exception>(l.job.progress.value?.error)
        assertEquals(LeaveStep.Repo.name, l.marks.leaving(pds.did))
        assertTrue(l.job.inProgress())
        assertTrue(l.auth.session.value != null, "still signed in, to retry")

        pds.failApplyWritesOf = null
        l.job.run()
        assertEquals(setOf("app.bsky.feed.post/3kp"), pds.stored.getValue(pds.repo).keys)
        assertFalse(l.job.inProgress())
    }

    @Test
    fun aLeaveResumesAtTheStepItReached() = runTest {
        account()
        val l = leaving()
        // Interrupted after the spaces went: they're gone, and the repo is next.
        pds.stored.remove(AthleteSpace.SELF)
        pds.stored.remove(AthleteSpace.FOLLOWERS)
        l.marks.setLeaving(pds.did, LeaveStep.Repo.name)

        l.job.run()

        assertEquals(emptyList(), pds.writes.filter { it.startsWith("deleteSpace") }, "the spaces step isn't repeated")
        assertEquals(setOf("app.bsky.feed.post/3kp"), pds.stored.getValue(pds.repo).keys)
        assertEquals(EndedSession.Reason.Left, l.auth.lastEnd.value?.reason)
    }

    @Test
    fun aRunningExportHoldsTheLeaveBack() = runTest {
        val l = leaving()
        assertFalse(l.job.exportRunning())
        l.session.db.dataExports().insert(DataExportEntity("e1", pds.did.value, "alice.test", "pds.test", createdAt = 0))
        assertTrue(l.job.exportRunning())
    }

    @Test
    fun theSummaryCountsTheAthletesActiveSegmentsAndSitesApart() = runTest {
        val seg = { rkey: String -> "at://${pds.did.value}/${Nsid.FEED_SEGMENT}/$rkey" }
        pds.stored[pds.repo] =
            mutableMapOf(
                "${Nsid.FEED_SEGMENT}/1" to record(Nsid.FEED_SEGMENT),
                "${Nsid.FEED_SEGMENT}/2" to record(Nsid.FEED_SEGMENT),
                "${Nsid.FEED_SEGMENT}/3" to record(Nsid.FEED_SEGMENT),
                "${Nsid.FEED_DIVE_SITE}/4" to record(Nsid.FEED_DIVE_SITE),
            )
        pds.statuses[seg("1")] = "active"
        pds.statuses[seg("2")] = "proposed"
        pds.statuses["at://${pds.did.value}/${Nsid.FEED_DIVE_SITE}/4"] = "active"

        assertEquals(SharedPlaces(activeSegments = 1, otherSegments = 2, activeSites = 1, otherSites = 0), leaving().job.sharedPlaces())
    }

    @Test
    fun beginRefusesWhileAnExportRunsAndDiscardsNothing() = runTest {
        val l = leaving()
        l.session.db.dataExports().insert(DataExportEntity("e1", pds.did.value, "alice.test", "pds.test", createdAt = 0))
        kotlin.test.assertFailsWith<LeaveJob.ExportRunningException> { l.job.begin() }
        assertFalse(l.job.inProgress())
    }

    @Test
    fun beginRefusesWhenThePdsCantBeReachedAndDiscardsNothing() = runTest {
        val l = leaving()
        OutboxWriter(l.session.db, l.session.clients, Clock.System)
            .put(Nsid.FEED_ACTIVITY, "3kq", JsonObject.serializer(), record(Nsid.FEED_ACTIVITY))
        pds.pdsUnreachable = true
        kotlin.test.assertFailsWith<java.io.IOException> { l.job.begin() }
        assertFalse(l.job.inProgress())
        assertEquals(1, l.session.db.outbox().pending(pds.did.value).size, "the queued ride is still there")
    }
}
