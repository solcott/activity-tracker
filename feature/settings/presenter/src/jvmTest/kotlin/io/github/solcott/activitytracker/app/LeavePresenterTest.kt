package io.github.solcott.activitytracker.app

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.settings.LeaveEvent
import io.github.solcott.activitytracker.app.settings.LeaveExport
import io.github.solcott.activitytracker.app.settings.LeavePresenter
import io.github.solcott.activitytracker.app.settings.LeaveScreen
import io.github.solcott.activitytracker.app.settings.LeaveState
import io.github.solcott.activitytracker.app.settings.SharedPlacesSummary
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.auth.EndedSession
import io.github.solcott.activitytracker.data.auth.LeaveStep
import io.github.solcott.activitytracker.data.auth.SignOutCheck
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.dataresult.DataError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The leave screen (auth/leaving): what it says before the athlete confirms, what holds the leave back, and the leave's progress. */
class LeavePresenterTest {
    private val fixture = SessionTestFixture()

    private suspend fun leave(
        screen: LeaveScreen = LeaveScreen(),
        navigator: FakeNavigator = FakeNavigator(screen),
        before: suspend (SessionTestFixture.Repos) -> Unit = {},
        block: suspend app.cash.turbine.ReceiveTurbine<LeaveState>.(SessionTestFixture.Repos) -> Unit,
    ) {
        val repos = fixture.repos()
        before(repos)
        presenterTestOf(
            {
                LeavePresenter(
                    screen,
                    navigator,
                    repos.clients,
                    repos.auth,
                    repos.leaveJob,
                    SignOutCheck(repos.uploads, repos.saves, repos.exports, repos.clients),
                    repos.exports,
                    repos.dataExporter,
                    repos.recorder,
                )
            },
        ) {
            block(repos)
        }
    }

    /** Queues a run while the PDS can't be reached, so it waits on the device. */
    private suspend fun SessionTestFixture.Repos.queueARun(start: String = "2026-10-14T06:12:00Z") {
        fixture.offline = true
        activities.publish(ActivityDraft(ActivityType(Sport.RUN), "Run", null, Instant.parse(start), 30.minutes))
        fixture.offline = false
    }

    @Test
    fun theSummaryCountsSegmentsAndSitesOthersUseApart() = runTest {
        fixture.ownSegments += mapOf("1" to "active", "2" to "active", "3" to "active", "4" to "proposed", "5" to "proposed")
        fixture.ownSites += mapOf("6" to "active")
        leave {
            val loaded = awaitUntil { state -> state.places is SharedPlacesSummary.Loaded }
            assertEquals(SharedPlacesSummary.Loaded(activeSegments = 3, otherSegments = 2, activeSites = 1, otherSites = 0), loaded.places)
            assertEquals("alice.test", loaded.handle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun countsThatCantLoadSaySoAndLeavingIsStillAllowed() = runTest {
        leave(before = { fixture.offline = true }) {
            val failed = awaitUntil { state -> state.places is SharedPlacesSummary.Failed }
            val error = (failed.places as SharedPlacesSummary.Failed).error
            assertEquals(UiError(UiError.Kind.LoadSharedPlaces, DataError.Network), error)
            failed.confirmHandle.setTextAndPlaceCursorAtEnd("alice.test")
            assertTrue(failed.canLeave)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uploadsWaitingAreCounted() = runTest {
        leave(
            before = {
                it.queueARun()
                it.queueARun("2026-10-15T06:12:00Z")
            },
        ) {
            assertEquals(2, awaitUntil { state -> state.pending != null }.pending?.changes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun leavingIsOnlyOnceTheHandleMatches() = runTest {
        leave {
            val state = awaitItem()
            assertFalse(state.canLeave)
            state.confirmHandle.setTextAndPlaceCursorAtEnd("alice.tset")
            assertFalse(state.canLeave)
            state.eventSink(LeaveEvent.Leave)
            state.confirmHandle.setTextAndPlaceCursorAtEnd(" @Alice.Test ")
            assertTrue(state.canLeave)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(emptyList(), fixture.deletedSpaces, "a wrong handle starts nothing")
    }

    @Test
    fun cancellingChangesNothing() = runTest {
        val navigator = FakeNavigator(LeaveScreen())
        leave(navigator = navigator, before = { it.queueARun() }) { repos ->
            awaitItem().eventSink(LeaveEvent.Back)
            navigator.awaitPop()
            assertNull(repos.marks.leaving(repos.clients.did))
            assertEquals(1, repos.uploads.summary.first().own, "the queued run stays")
            assertNotNull(repos.auth.session.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aGrantFromBeforeSpaceDeletionMustSignInAgain() = runTest {
        fixture.scope = OAuthClientConfig.DEFAULT_SCOPE.replace("&manage=delete", "")
        leave { repos ->
            val state = awaitItem()
            assertTrue(state.needsSignInAgain)
            state.confirmHandle.setTextAndPlaceCursorAtEnd("alice.test")
            assertFalse(state.canLeave)
            state.eventSink(LeaveEvent.SignInAgain)
            repos.auth.session.first { it == null }
            assertEquals(EndedSession.Reason.SignInAgain, repos.auth.lastEnd.value?.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnreachablePdsStopsTheLeaveBeforeAnythingIsDiscarded() = runTest {
        leave(before = { it.queueARun() }) { repos ->
            val state = awaitUntil { it.places is SharedPlacesSummary.Loaded }
            state.confirmHandle.setTextAndPlaceCursorAtEnd("alice.test")
            fixture.offline = true
            state.eventSink(LeaveEvent.Leave)
            val refused = awaitUntil { it.error != null && !it.starting }
            assertEquals(UiError(UiError.Kind.StartLeave, DataError.Network), refused.error)
            assertNull(refused.run)
            assertNull(repos.marks.leaving(repos.clients.did))
            fixture.offline = false
            assertEquals(1, repos.uploads.summary.first().own, "nothing is discarded")
            assertNotNull(repos.auth.session.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRunningExportHoldsLeavingBackUntilCancelled() = runTest {
        leave(before = { it.dataExporter.start() }) { repos ->
            val running = awaitUntil { it.export == LeaveExport.Running }
            running.confirmHandle.setTextAndPlaceCursorAtEnd("alice.test")
            assertFalse(running.canLeave)
            running.eventSink(LeaveEvent.CancelExport)
            val cancelled = awaitUntil { it.export == LeaveExport.None }
            assertTrue(cancelled.canLeave)
            assertNull(repos.exports.current(repos.clients.did))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnsavedExportIsOfferedForSavingOnTheAccountPage() = runTest {
        val navigator = FakeNavigator(LeaveScreen())
        leave(navigator = navigator, before = { it.dataExporter.run(it.dataExporter.start()) }) {
            val ready = awaitUntil { state -> state.export == LeaveExport.Ready }
            ready.confirmHandle.setTextAndPlaceCursorAtEnd("alice.test")
            assertTrue(ready.canLeave, "leaving deletes it, but may go ahead")
            ready.eventSink(LeaveEvent.SaveExport)
            navigator.awaitPop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun exportingFirstStartsTheExport() = runTest {
        leave { repos ->
            awaitItem().eventSink(LeaveEvent.Export)
            awaitUntil { it.export != LeaveExport.None }
            assertNotNull(repos.exports.current(repos.clients.did))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmingLeavesAndSignsOutWithTheLeftNote() = runTest {
        leave(before = { it.queueARun() }) { repos ->
            val state = awaitUntil { it.places is SharedPlacesSummary.Loaded }
            state.confirmHandle.setTextAndPlaceCursorAtEnd("alice.test")
            state.eventSink(LeaveEvent.Leave)
            state.eventSink(LeaveEvent.Leave)
            repos.auth.session.first { it == null }
            assertEquals(EndedSession.Reason.Left, repos.auth.lastEnd.value?.reason)
            assertEquals(AthleteSpace.SELF, fixture.deletedSpaces.first(), "self goes first")
            assertEquals(fixture.deletedSpaces.distinct(), fixture.deletedSpaces, "one leave, however often it was confirmed")
            assertEquals(0, repos.uploads.summary.first().own, "the queued run is discarded, never sent")
            assertEquals(emptyList(), fixture.putRkeys)
            assertNull(repos.marks.leaving(repos.clients.did))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRefusedDeleteStopsTheLeaveAndRetryFinishesIt() = runTest {
        fixture.refuseSpaceDeletes = true
        leave { repos ->
            val state = awaitUntil { it.places is SharedPlacesSummary.Loaded }
            state.confirmHandle.setTextAndPlaceCursorAtEnd("alice.test")
            state.eventSink(LeaveEvent.Leave)
            val stopped = awaitUntil { it.run?.error != null }
            assertEquals(UiError.Kind.Leave, stopped.run?.error?.kind)
            assertEquals(LeaveStep.Spaces, stopped.run?.step)
            assertEquals(LeaveStep.Spaces.name, repos.marks.leaving(repos.clients.did))
            assertNotNull(repos.auth.session.value, "still signed in, to retry")

            fixture.refuseSpaceDeletes = false
            stopped.eventSink(LeaveEvent.Retry)
            repos.auth.session.first { it == null }
            assertEquals(EndedSession.Reason.Left, repos.auth.lastEnd.value?.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aLeaveRecordedBeforeARestartResumes() = runTest {
        leave(
            screen = LeaveScreen(resume = true),
            before = { repos -> repos.marks.setLeaving(repos.clients.did, LeaveStep.Repo.name) },
        ) { repos ->
            assertNotNull(awaitItem().run, "only the progress shows")
            repos.auth.session.first { it == null }
            assertEquals(EndedSession.Reason.Left, repos.auth.lastEnd.value?.reason)
            assertEquals(emptyList(), fixture.deletedSpaces, "the spaces step isn't repeated")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
