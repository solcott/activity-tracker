package io.github.solcott.activitytracker.app

import app.cash.turbine.ReceiveTurbine
import co.touchlab.kermit.Logger
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.app.detail.ActivityDetailScreen
import io.github.solcott.activitytracker.app.uploads.UploadsEvent
import io.github.solcott.activitytracker.app.uploads.UploadsPresenter
import io.github.solcott.activitytracker.app.uploads.UploadsScreen
import io.github.solcott.activitytracker.app.uploads.UploadsState
import io.github.solcott.activitytracker.data.activity.ImportSaveStatus
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.RejectedChanges
import io.github.solcott.activitytracker.data.activity.UploadQueue
import io.github.solcott.activitytracker.data.activity.UploadQueueView
import io.github.solcott.activitytracker.data.activity.UploadState
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.ImportSaveEntity
import io.github.solcott.activitytracker.data.db.ImportSaveItemEntity
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.data.db.UploadBatchEntity
import io.github.solcott.activitytracker.data.db.UploadItemEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.signedInTestSession
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalLikes
import io.github.solcott.activitytracker.data.importsave.testImportSaves
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.location.FakeLocationTracker
import io.github.solcott.activitytracker.location.LocationTracker
import io.github.solcott.activitytracker.location.UnsupportedLocationTracker
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/**
 * The upload queue screen (data/upload-queue): listed live, cancelled after confirming, imports paused and resumed, finished items cleared.
 */
class UploadsPresenterTest {
    private val did = "did:plc:alice"

    /** Held until completed, so a test can send an event twice while the first put is in flight. */
    @Volatile private var gate: CompletableDeferred<Unit>? = null

    private val puts: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val http =
        HttpClient(
            MockEngine { request ->
                val json = headersOf("Content-Type", "application/json")
                if (request.url.encodedPath.endsWith("com.atproto.repo.putRecord")) {
                    gate?.await()
                    puts += request.url.encodedPath
                    respond("""{"uri":"at://$did/x/y","cid":"bafyrec"}""", HttpStatusCode.OK, json)
                } else {
                    respond("{}", HttpStatusCode.OK, json)
                }
            },
        )

    /** A waiting activity at [rkey], of the import [batchId] if given, with [writes] queued. */
    private suspend fun AppDatabase.waiting(
        rkey: String,
        title: String,
        batchId: String? = null,
        writes: Int = 2,
        rejected: Boolean = false,
    ) {
        uploads()
            .queued(
                UploadItemEntity(
                    did = did,
                    rkey = rkey,
                    batchId = batchId,
                    kind = UploadItemEntity.KIND_NEW,
                    title = title,
                    sport = "ride",
                    subSport = null,
                    startedAt = 0,
                    queuedAt = 0,
                ),
            )
        repeat(writes) {
            val id =
                outbox()
                    .insert(
                        OutboxEntity(
                            did = did,
                            operation = "put",
                            collection = Nsid.FEED_ACTIVITY,
                            rkey = rkey,
                            recordJson = "{}",
                            createdAt = 0,
                            bulk = batchId != null,
                        ),
                    )
            if (rejected && it == 0) outbox().markRejected(id, at = 1, error = "refused")
        }
    }

    private suspend fun presenterTest(
        navigator: FakeNavigator = FakeNavigator(UploadsScreen),
        location: LocationTracker = FakeLocationTracker(),
        seed: suspend AppDatabase.() -> Unit,
        block: suspend ReceiveTurbine<UploadsState>.(AppDatabase, SessionClients) -> Unit,
    ) {
        val (db, _, clients) = signedInTestSession(http)
        db.seed()
        val likes = LocalLikes()
        val rejected = RejectedChanges(db, clients, OutboxProcessor(db, clients, Logger), FeedRepository(db, clients, likes, Logger), likes)
        val uploads = UploadQueue(db, clients, rejected, Clock.System, testImportSaves(db))
        presenterTestOf({ UploadsPresenter(navigator, uploads, location) }) { block(db, clients) }
    }

    @Test
    fun listsTheAthletesOwnAndEachImport() = runTest {
        presenterTest(
            seed = {
                uploads().putBatch(UploadBatchEntity("3kbatch", did, "export:Strava", savedAt = 0, saved = 2))
                waiting("3kown", "Morning run")
                waiting("3kone", "One", batchId = "3kbatch")
                waiting("3ktwo", "Two", batchId = "3kbatch")
            },
        ) { _, _ ->
            val view = awaitUntil { it.content.data != null }.content.data!!
            assertEquals(listOf("Morning run"), view.own.map { it.title })
            assertEquals(listOf("One", "Two"), view.imports.single().live.map { it.title })
            assertEquals(UploadState.Waiting, view.own.single().state)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancellingAsksFirstThenDropsTheActivity() = runTest {
        presenterTest(seed = { waiting("3kown", "Morning run") }) { db, _ ->
            awaitUntil { it.content.data?.own?.size == 1 }.eventSink(UploadsEvent.Cancel("3kown"))
            awaitUntil { it.confirmingCancel == "3kown" }.eventSink(UploadsEvent.Dismiss)
            val dismissed = awaitUntil { it.confirmingCancel == null }
            assertEquals(2, db.outbox().pending(did).size, "nothing is dropped before confirming")

            dismissed.eventSink(UploadsEvent.Cancel("3kown"))
            awaitUntil { it.confirmingCancel != null }.eventSink(UploadsEvent.ConfirmCancel("3kown"))
            val cancelled = awaitUntil { it.content.data?.isEmpty == true }
            assertEquals(emptyList(), db.outbox().pending(did))
            assertEquals(emptyList(), puts)
            assertNull(cancelled.error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCancelRefusedBecauseItStartedSaysSo() = runTest {
        presenterTest(seed = { waiting("3kown", "Morning run") }) { db, _ ->
            val waiting = awaitUntil { it.content.data?.own?.size == 1 }
            // A write of it is sent between the tap and the cancel.
            db.uploads().countSent(did, "3kown")
            waiting.eventSink(UploadsEvent.ConfirmCancel("3kown"))
            val failed = awaitUntil { it.error != null }
            assertEquals(UiError.Kind.CancelUpload, failed.error!!.kind)
            assertEquals(1, failed.content.data!!.all().size, "it's kept")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aDoubleTappedSendAgainSendsOnce() = runTest {
        gate = CompletableDeferred()
        presenterTest(seed = { waiting("3kown", "Morning run", writes = 1, rejected = true) }) { _, _ ->
            val state = awaitUntil { it.content.data?.own?.singleOrNull()?.state == UploadState.Refused }
            state.eventSink(UploadsEvent.SendAgain("3kown"))
            state.eventSink(UploadsEvent.SendAgain("3kown"))
            gate!!.complete(Unit)

            awaitUntil { it.content.data?.finished?.size == 1 }
            assertEquals(1, puts.size, "a second tap while the first runs sends nothing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun pausingAndResumingImports() = runTest {
        presenterTest(seed = {}) { _, clients ->
            awaitUntil { it.content.data != null }.eventSink(UploadsEvent.PauseImports)
            awaitUntil { it.importsPaused }.eventSink(UploadsEvent.ResumeImports)
            awaitUntil { !it.importsPaused }
            assertEquals(false, clients.pacing.importsPaused.value)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancellingTheRestOfAnImport() = runTest {
        presenterTest(
            seed = {
                uploads().putBatch(UploadBatchEntity("3kbatch", did, "export:Strava", savedAt = 0, saved = 2))
                waiting("3kone", "One", batchId = "3kbatch")
                waiting("3ktwo", "Two", batchId = "3kbatch")
            },
        ) { db, _ ->
            awaitUntil { it.content.data?.imports?.size == 1 }.eventSink(UploadsEvent.CancelRest("3kbatch"))
            awaitUntil { it.confirmingCancelRest == "3kbatch" }.eventSink(UploadsEvent.ConfirmCancelRest("3kbatch"))
            awaitUntil { it.content.data?.imports?.singleOrNull()?.live?.isEmpty() == true }
            assertEquals(emptyList(), db.outbox().pending(did))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancellingTheRestOfAnImportStillSavingDropsItsUnsavedActivities() = runTest {
        presenterTest(
            seed = {
                uploads().putBatch(UploadBatchEntity("3kbatch", did, "export:Strava", savedAt = 0, saved = 1, total = 3))
                val items = (0 until 3).map { ImportSaveItemEntity("3kbatch", it, it, 0, "[]", it, "3krkey$it", "{}", done = it == 0) }
                importSaves().plan(ImportSaveEntity("3kbatch", did, 3, createdAt = 0, choicesJson = "{}"), emptyList(), items)
                waiting("3kone", "One", batchId = "3kbatch")
            },
        ) { db, _ ->
            val saving = awaitUntil { it.content.data?.imports?.singleOrNull()?.save != null }
            assertEquals(ImportSaveStatus.Saving(done = 1, total = 3), saving.content.data!!.imports.single().save)
            saving.eventSink(UploadsEvent.ConfirmCancelRest("3kbatch"))
            awaitUntil { state -> state.content.data?.imports?.singleOrNull()?.let { it.save == null && it.live.isEmpty() } == true }
            assertNull(db.importSaves().save("3kbatch"), "with no runner on it, the save ends now")
            assertEquals(emptyList(), db.outbox().pending(did))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clearDropsFinishedItems() = runTest {
        presenterTest(seed = { waiting("3kown", "Morning run", writes = 0) }) { _, _ ->
            // With no writes left, opening the queue finishes it.
            awaitUntil { it.content.data?.finished?.size == 1 }.eventSink(UploadsEvent.ClearFinished)
            awaitUntil { it.content.data?.isEmpty == true }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun showAllAndOpeningAnActivity() = runTest {
        val navigator = FakeNavigator(UploadsScreen)
        presenterTest(navigator, seed = {}) { _, _ ->
            awaitUntil { it.content.data != null }.eventSink(UploadsEvent.ShowAll("3kbatch"))
            awaitUntil { "3kbatch" in it.expandedImports }.eventSink(UploadsEvent.OpenActivity("at://$did/${Nsid.FEED_ACTIVITY}/3kown"))
            assertEquals(ActivityDetailScreen("at://$did/${Nsid.FEED_ACTIVITY}/3kown"), navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onDesktopAndWebUploadsGoOnOnlyWhileOpen() = runTest {
        presenterTest(location = UnsupportedLocationTracker(), seed = {}) { _, _ ->
            assertTrue(awaitUntil { it.content.data != null }.isOpenOnly)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun UploadQueueView.all() = listOfNotNull(current) + own + imports.flatMap { it.live + it.finished } + finished
}
