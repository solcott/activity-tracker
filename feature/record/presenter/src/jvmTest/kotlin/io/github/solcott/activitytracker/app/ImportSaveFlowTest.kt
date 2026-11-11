package io.github.solcott.activitytracker.app

import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.BatchReader
import io.github.solcott.activitytracker.activityfile.ByteArrayImportSource
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.app.record.BatchSaveRunner
import io.github.solcott.activitytracker.app.record.DuplicateCheck
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.record.PlanDrafting
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.db.ImportSaveItemEntity
import io.github.solcott.activitytracker.data.importsave.ImportCounts
import io.github.solcott.activitytracker.data.importsave.ImportSaveRunner
import io.github.solcott.activitytracker.data.importsave.PlannedActivity
import io.github.solcott.activitytracker.data.importsave.PlannedChoices
import io.github.solcott.activitytracker.data.importsave.encodePath
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import java.lang.ref.WeakReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A batch save outlives the import screen (feed/batch-import): leaving doesn't stop it and import opened again shows it; a save that stops
 * short says what it saved by Save, and saving again finishes the same import; no activity's draft is held once it's queued.
 */
@Suppress("InjectDispatcher") // The save runs app-wide on real threads, as the app's does, apart from the presenter's test clock.
class ImportSaveFlowTest {
    private val fixture = SessionTestFixture()
    private val files = ActivityFileReader { error("not FIT") }

    /** A GPX run of 10 minutes, [day] days after the first of September. */
    private fun gpx(day: Int): ByteArray {
        val begin = Instant.parse("2026-09-01T07:00:00Z") + day.days
        return buildString {
            append("""<gpx creator="Test" version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><type>running</type><trkseg>""")
            repeat(61) { i ->
                append("""<trkpt lat="${47.6 + i * 0.0001}" lon="-122.3"><time>${begin + (i * 10).seconds}</time></trkpt>""")
            }
            append("</trkseg></trk></gpx>")
        }
            .encodeToByteArray()
    }

    private fun runs(count: Int): List<ImportSource> = (1..count).map { ByteArrayImportSource("day$it.gpx", gpx(it)) }

    /** A file whose reads, once [armed], wait for [open]: read freely while the batch is reviewed, held up while it's saved. */
    private class Gated(private val of: ImportSource, val open: CompletableDeferred<Unit> = CompletableDeferred()) : ImportSource by of {
        @Volatile var armed = false

        override suspend fun read(offset: Long, length: Int): ByteArray {
            if (armed) open.await()
            return of.read(offset, length)
        }
    }

    private fun SessionTestFixture.Repos.realRunner() = BatchSaveRunner(saveStore, saves, clients, activities, manager, files)

    private fun presenter(repos: SessionTestFixture.Repos, runner: ImportSaveRunner = repos.realRunner()) =
        @androidx.compose.runtime.Composable {
            ImportActivityPresenter(
                ImportActivityScreen(),
                FakeNavigator(ImportActivityScreen()),
                repos.activities,
                repos.manager,
                files,
                repos.typeChoices,
                repos.gearChoices,
                repos.newDeviceChoices,
                repos.profiles,
                repos.feed,
                repos.defaults,
                repos.units,
                repos.suggestions,
                repos.saveStore,
                repos.saves,
                runner,
            )
        }

    private fun savedTitles(): List<String> =
        fixture.putRecords
            .filter { it.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY }
            .map { AtJson.decodeFromJsonElement(ActivityRecord.serializer(), it as JsonObject).title.orEmpty() }

    @Test
    fun leavingDuringASaveDoesntStopItAndImportShowsIt() = runTest {
        val repos = fixture.repos()
        val gate = CompletableDeferred<Unit>()
        val gated = Gated(runs(4)[2], gate)
        val sources = runs(4).mapIndexed { i, source -> if (i == 2) gated else source }
        presenterTestOf(presenter(repos)) {
            awaitItem().eventSink(ImportActivityEvent.FilesPicked(sources.toImmutableList()))
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            gated.armed = true
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batch?.progress?.queued == 2 }
            // The athlete goes Back: the screen leaves the composition.
            cancelAndIgnoreRemainingEvents()
        }
        presenterTestOf(presenter(repos)) {
            val saving = awaitUntil { it.savingImport != null }
            assertEquals(2 to 4, saving.savingImport?.progress?.let { it.queued to it.total }, "import opened again shows the save")
            gate.complete(Unit)
            awaitUntil { it.savingImport == null }
            cancelAndIgnoreRemainingEvents()
        }
        withContext(Dispatchers.Default) { repos.saveStore.counts.first { it.isEmpty() } }
        assertEquals(ImportCounts(4, 0), repos.saveStore.importCounts(lastBatch(repos)))
    }

    private suspend fun lastBatch(repos: SessionTestFixture.Repos): String = repos.uploads.queue.first().imports.single().id

    /**
     * Runs the real runner until [after] activities are saved, then stops it and reports [failure], as a run failing there would; with
     * [queuedNext], having queued the next activity under its planned key but not marked it saved, as a combine whose read failed would.
     */
    private class FailAfter(
        private val repos: SessionTestFixture.Repos,
        private val real: ImportSaveRunner,
        private val after: Int,
        private val queuedNext: Boolean = false,
    ) : ImportSaveRunner {
        var failed = false

        override suspend fun runAll() {
            if (failed) return real.runAll()
            failed = true
            coroutineScope {
                val id = repos.saveStore.runnable().single().batchId
                val run = launch(Dispatchers.Default) { real.runAll() }
                repos.saveStore.counts.first { saves -> saves.single().done >= after }
                run.cancelAndJoin()
                if (queuedNext) {
                    val next = repos.saveStore.pending(id).first()
                    repos.activities.publish(
                        ActivityDraft(
                            ActivityType(Sport.RUN),
                            "Queued",
                            null,
                            Instant.fromEpochMilliseconds(0),
                            600.seconds,
                            rkey = next.rkey,
                        ),
                    )
                }
                repos.saves.ended(id, IllegalStateException("The PDS refused it"))
            }
        }
    }

    @Test
    fun aSaveThatStopsShortSaysSoBySaveAndSavingAgainFinishesTheImport() = runTest {
        val repos = fixture.repos()
        val gate = CompletableDeferred<Unit>()
        val gated = Gated(runs(5)[3], gate)
        val sources = runs(5).mapIndexed { i, source -> if (i == 3) gated else source }
        val runner = FailAfter(repos, repos.realRunner(), after = 3)
        presenterTestOf(presenter(repos, runner)) {
            awaitItem().eventSink(ImportActivityEvent.FilesPicked(sources.toImmutableList()))
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            gated.armed = true
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            // The count saved and what's left to select come from separate flows, so wait for both rather than the first
            // failure state: one may land before the other.
            val failed = awaitUntil { state ->
                val failure = state.batch?.saveFailure
                failure != null && failure.saved to failure.total == 3 to 5 && state.batch?.selectedCount == 2
            }
            assertEquals(false, failed.saving)

            gate.complete(Unit)
            failed.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(5, savedTitles().size, "each activity saved once")
        val import = repos.uploads.queue.first().imports.single()
        assertEquals(5, import.saved, "one import of all five")
    }

    @Test
    fun savingAgainKeepsTheKeyOfAnActivityTheFailedRunQueued() = runTest {
        val repos = fixture.repos()
        val gate = CompletableDeferred<Unit>()
        val gated = Gated(runs(5)[3], gate)
        val sources = runs(5).mapIndexed { i, source -> if (i == 3) gated else source }
        val runner = FailAfter(repos, repos.realRunner(), after = 3, queuedNext = true)
        presenterTestOf(presenter(repos, runner)) {
            awaitItem().eventSink(ImportActivityEvent.FilesPicked(sources.toImmutableList()))
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            gated.armed = true
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            val failed = awaitUntil { it.batch?.saveFailure != null && it.batch?.selectedCount == 2 }
            gate.complete(Unit)
            failed.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        val activityRkeys =
            fixture.putRkeys.zip(fixture.putRecords).filter { (_, record) ->
                record.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY
            }
        assertEquals(5, activityRkeys.map { it.first }.toSet().size, "the queued activity is rewritten under its key, not queued again")
    }

    @Test
    fun noDraftIsHeldOnceItsActivityIsQueued() = runTest {
        val count = 40
        val sources = runs(count)
        val items =
            sources.indices.map { idx ->
                ImportSaveItemEntity(
                    "3kbatch",
                    idx,
                    idx,
                    idx,
                    encodePath(emptyList()),
                    0,
                    Tid.next(),
                    PlannedActivity(type = ActivityType(Sport.RUN), visibility = Visibility.PUBLIC, startedAt = 0).encode(),
                )
            }
        val drafting = PlanDrafting(BatchReader(sources, files), files, PlannedChoices(), "3kbatch")
        val drafts = mutableListOf<WeakReference<ActivityDraft>>()
        drafting.each(items) { _, draft -> drafts += WeakReference(assertNotNull(draft)) }

        repeat(50) {
            if (drafts.all { draft -> draft.get() == null }) return@repeat
            System.gc()
            // The collector runs on its own thread, in real time.
            @Suppress("SleepInsteadOfDelay") Thread.sleep(20)
        }
        assertEquals(count, drafts.size)
        assertNull(drafts.firstOrNull { it.get() != null }?.get(), "every draft was let go once handed on")
    }

    @Test
    fun aDuplicateCheckThatCouldntReachTheLogCanBeRetried() = runTest {
        // The AppView can't be read (its service auth comes from the PDS).
        fixture.ownActivities = null
        presenterTestOf(presenter(fixture.repos())) {
            awaitItem().eventSink(ImportActivityEvent.FilesPicked(runs(1).toImmutableList()))
            val failed = awaitUntil { it.preview != null && it.duplicateCheck == DuplicateCheck.Failed }
            fixture.ownActivities = emptyList()
            failed.eventSink(ImportActivityEvent.RetryDuplicateCheck)
            awaitUntil { it.duplicateCheck == DuplicateCheck.Done }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
