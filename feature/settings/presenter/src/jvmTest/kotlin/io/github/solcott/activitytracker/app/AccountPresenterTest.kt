package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.settings.AccountEvent
import io.github.solcott.activitytracker.app.settings.AccountPresenter
import io.github.solcott.activitytracker.app.settings.AccountScreen
import io.github.solcott.activitytracker.app.settings.AccountState
import io.github.solcott.activitytracker.app.settings.DataExportStatus
import io.github.solcott.activitytracker.app.settings.EndingSession
import io.github.solcott.activitytracker.app.settings.LeaveScreen
import io.github.solcott.activitytracker.app.settings.hostOf
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.auth.EndedSession
import io.github.solcott.activitytracker.data.auth.GrantFeature
import io.github.solcott.activitytracker.data.auth.GrantStatus
import io.github.solcott.activitytracker.data.auth.PendingWork
import io.github.solcott.activitytracker.data.auth.SignOutCheck
import io.github.solcott.activitytracker.data.importsave.NewImportSave
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** The Account page (auth/account-page): the account, its grant, and ending the session with pending work. */
class AccountPresenterTest {
    private val fixture = SessionTestFixture()

    private suspend fun account(
        navigator: FakeNavigator = FakeNavigator(AccountScreen),
        before: suspend (SessionTestFixture.Repos) -> Unit = {},
        block: suspend app.cash.turbine.ReceiveTurbine<AccountState>.(SessionTestFixture.Repos) -> Unit,
    ) {
        val repos = fixture.repos()
        before(repos)
        presenterTestOf(
            {
                AccountPresenter(
                    navigator,
                    repos.clients,
                    repos.auth,
                    SignOutCheck(repos.uploads, repos.saves, repos.exports, repos.clients),
                    repos.exports,
                    repos.dataExporter,
                )
            },
        ) {
            block(repos)
        }
    }

    /** Queues a run while the PDS can't be reached, so it waits on the device. */
    private suspend fun SessionTestFixture.Repos.queueARun() {
        fixture.offline = true
        activities.publish(ActivityDraft(ActivityType(Sport.RUN), "Run", null, Instant.parse("2026-10-14T06:12:00Z"), 30.minutes))
        fixture.offline = false
    }

    @Test
    fun showsTheAccountAndACurrentGrant() = runTest {
        account { repos ->
            val state = awaitItem()
            assertEquals("alice.test", state.handle)
            assertEquals(repos.clients.did.value, state.did)
            assertEquals(hostOf(repos.clients.pdsUrl), state.pdsHost)
            assertEquals(GrantStatus.Current, state.grant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSignInWithoutTheSpaceScopeIsOutdated() = runTest {
        fixture.scope = "atproto transition:generic"
        account {
            assertEquals(GrantStatus.Outdated(GrantFeature.all), awaitItem().grant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun signingOutWithNothingPendingEndsTheSessionAtOnce() = runTest {
        account { repos ->
            awaitItem().eventSink(AccountEvent.SignOut)
            repos.auth.session.first { it == null }
            assertNull(repos.auth.lastEnd.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun signingInAgainKeepsTheAccountForTheLoginScreen() = runTest {
        account { repos ->
            awaitItem().eventSink(AccountEvent.SignInAgain)
            repos.auth.session.first { it == null }
            assertEquals(EndedSession.Reason.SignInAgain, repos.auth.lastEnd.value?.reason)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pendingUploadsAskFirstAndCancellingStaysSignedIn() = runTest {
        account(before = { it.queueARun() }) { repos ->
            awaitItem().eventSink(AccountEvent.SignOut)
            val asking = awaitUntil { it.confirming != null }
            assertEquals(EndingSession(signInAgain = false, PendingWork(changes = 1, importSaving = false)), asking.confirming)

            asking.eventSink(AccountEvent.Dismiss)
            awaitUntil { it.confirming == null }
            assertEquals(repos.clients.did, repos.auth.session.value?.did)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmingSignsOutAndKeepsTheQueuedWrites() = runTest {
        account(before = { it.queueARun() }) { repos ->
            awaitItem().eventSink(AccountEvent.SignOut)
            val asking = awaitUntil { it.confirming != null }
            // As the dialog sends them: it dismisses itself, then runs its action; a second tap changes nothing.
            asking.eventSink(AccountEvent.Dismiss)
            asking.eventSink(AccountEvent.Confirm(signInAgain = false))
            asking.eventSink(AccountEvent.Confirm(signInAgain = false))
            repos.auth.session.first { it == null }
            assertNull(repos.auth.lastEnd.value)
            assertEquals(1, repos.uploads.summary.first().own)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anImportStillSavingIsMentioned() = runTest {
        account(
            before = { repos ->
                repos.saveStore.start(NewImportSave("3kbatch", UploadSource.Files("day1.gpx", 0), emptyList(), emptyList(), "{}"))
            },
        ) { _ ->
            awaitItem().eventSink(AccountEvent.SignInAgain)
            val asking = awaitUntil { it.confirming != null }
            assertEquals(EndingSession(signInAgain = true, PendingWork(changes = 0, importSaving = true)), asking.confirming)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noExportOffersOne() = runTest {
        account {
            assertEquals(DataExportStatus.None, awaitItem().export)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun exportingRunsToReadyAndADoubleTapStartsOne() = runTest {
        account { repos ->
            val state = awaitItem()
            state.eventSink(AccountEvent.Export)
            state.eventSink(AccountEvent.Export)
            val ready = awaitUntil { it.export is DataExportStatus.Ready }.export as DataExportStatus.Ready
            assertEquals(0, ready.problems)
            assertEquals(ready.archive.id, repos.exports.current(repos.clients.did)?.id, "one export, however often it was asked for")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun savingDeletesTheAppsCopy() = runTest {
        account(before = { repos -> repos.dataExporter.run(repos.dataExporter.start()) }) { repos ->
            val ready = awaitUntil { it.export is DataExportStatus.Ready }
            ready.eventSink(AccountEvent.ExportSaved)
            awaitUntil { it.export == DataExportStatus.None }
            assertNull(repos.exports.current(repos.clients.did))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSharedExportAsksBeforeDeletingAndKeepingLeavesIt() = runTest {
        account(before = { repos -> repos.dataExporter.run(repos.dataExporter.start()) }) { repos ->
            awaitUntil { it.export is DataExportStatus.Ready }.eventSink(AccountEvent.ExportShared)
            val asking = awaitUntil { it.askingKeptShared }
            asking.eventSink(AccountEvent.KeptShared(kept = false))
            val kept = awaitUntil { !it.askingKeptShared }
            assertEquals(true, kept.export is DataExportStatus.Ready)

            kept.eventSink(AccountEvent.ExportShared)
            awaitUntil { it.askingKeptShared }.eventSink(AccountEvent.KeptShared(kept = true))
            awaitUntil { it.export == DataExportStatus.None }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancellingDeletesTheExport() = runTest {
        account(before = { repos -> repos.dataExporter.start() }) { repos ->
            awaitUntil { it.export is DataExportStatus.Running }.eventSink(AccountEvent.CancelExport)
            awaitUntil { it.export == DataExportStatus.None }
            assertNull(repos.exports.current(repos.clients.did))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailedSaveSaysSo() = runTest {
        account(before = { repos -> repos.dataExporter.run(repos.dataExporter.start()) }) { _ ->
            awaitUntil { it.export is DataExportStatus.Ready }.eventSink(AccountEvent.ExportSaveFailed)
            assertEquals(UiError.Kind.SaveDataExport, awaitUntil { it.exportError != null }.exportError?.kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anExportNotYetSavedIsMentionedOnSignOutAndSigningOutDeletesIt() = runTest {
        account(before = { repos -> repos.dataExporter.start() }) { repos ->
            awaitItem().eventSink(AccountEvent.SignOut)
            val asking = awaitUntil { it.confirming != null }
            assertEquals(PendingWork(changes = 0, importSaving = false, exportPending = true), asking.confirming?.pending)

            asking.eventSink(AccountEvent.Dismiss)
            asking.eventSink(AccountEvent.Confirm(signInAgain = false))
            repos.auth.session.first { it == null }
            assertNull(repos.exports.current(repos.clients.did))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun leaveOpensTheLeaveScreen() = runTest {
        val navigator = FakeNavigator(AccountScreen)
        account(navigator = navigator) {
            awaitItem().eventSink(AccountEvent.Leave)
            assertEquals(LeaveScreen(), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hostOfDropsSchemePortAndPath() {
        assertEquals("pds.example.com", hostOf("https://pds.example.com:443/"))
        assertEquals("localhost", hostOf("http://localhost:2583"))
    }
}
