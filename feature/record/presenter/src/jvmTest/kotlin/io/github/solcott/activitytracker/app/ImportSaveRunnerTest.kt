package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.ByteArrayImportSource
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.app.record.BatchSaveRunner
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.db.ImportSaveEntity
import io.github.solcott.activitytracker.data.db.ImportSaveItemEntity
import io.github.solcott.activitytracker.data.importsave.ImportSaveLauncher
import io.github.solcott.activitytracker.data.importsave.NewImportSave
import io.github.solcott.activitytracker.data.importsave.PlannedActivity
import io.github.solcott.activitytracker.data.importsave.PlannedChoices
import io.github.solcott.activitytracker.data.importsave.encodePath
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A persisted batch save worked through by its runner (feed/batch-import): resumed after a run stopped anywhere, it saves only what's left,
 * each activity under its planned record key; a file it can't reopen skips its activities, one it never kept stops the save, and a
 * cancelled save stops between activities. A session's start resumes a save left unfinished.
 */
@Suppress("InjectDispatcher") // Saves run app-wide on real threads, as the app's do.
class ImportSaveRunnerTest {
    private val fixture = SessionTestFixture()
    private val files = ActivityFileReader { error("not FIT") }

    /** A GPX run of 10 minutes on day [day]. */
    private fun gpx(day: Int): ByteArray {
        val begin = Instant.parse("2026-09-${day.toString().padStart(2, '0')}T07:00:00Z")
        return buildString {
            append("""<gpx creator="Test" version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><type>running</type><trkseg>""")
            repeat(61) { i ->
                append("""<trkpt lat="${47.6 + i * 0.0001}" lon="-122.3"><time>${begin + (i * 10).seconds}</time></trkpt>""")
            }
            append("</trkseg></trk></gpx>")
        }
            .encodeToByteArray()
    }

    /** A save of one run from each of [sources], in order. */
    private fun saveOf(batchId: String, sources: List<ImportSource>) =
        NewImportSave(
            batchId,
            UploadSource.Files("day1.gpx", sources.size),
            sources,
            sources.indices.map { idx ->
                ImportSaveItemEntity(
                    batchId,
                    position = idx,
                    batchIndex = idx,
                    sourceIdx = idx,
                    pathJson = encodePath(emptyList()),
                    entry = 0,
                    rkey = Tid.next(),
                    planJson =
                        PlannedActivity(
                                title = "Run ${idx + 1}",
                                type = ActivityType(Sport.RUN),
                                visibility = Visibility.PUBLIC,
                                startedAt = 0,
                            )
                            .encode(),
                )
            },
            PlannedChoices().encode(),
        )

    private fun runs(count: Int) = (1..count).map { ByteArrayImportSource("day$it.gpx", gpx(it)) }

    private fun SessionTestFixture.Repos.runner() = BatchSaveRunner(saveStore, saves, clients, activities, manager, files)

    private fun savedRkeys(): List<String> =
        fixture.putRecords
            .filter { it.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY }
            .map { AtJson.decodeFromJsonElement(ActivityRecord.serializer(), it as JsonObject).title.orEmpty() }

    /** A file whose reads wait for [open], so a run can be stopped while it's read. */
    private class Gated(private val of: ImportSource, val open: CompletableDeferred<Unit>) : ImportSource by of {
        override suspend fun read(offset: Long, length: Int): ByteArray {
            open.await()
            return of.read(offset, length)
        }
    }

    @Test
    fun aRunStoppedPartwaySavesOnlyTheRestWhenResumed() = runTest {
        val repos = fixture.repos()
        val sources = runs(8)
        val gate = CompletableDeferred<Unit>()
        val save = saveOf("3kbatch", sources.mapIndexed { i, source -> if (i == 3) Gated(source, gate) else source })
        repos.saveStore.start(save)
        withContext(Dispatchers.Default) {
            val run = launch { repos.runner().runAll() }
            repos.saveStore.counts.first { it.single().done == 3 }
            // The process dies while the fourth file is read.
            run.cancel()
            run.join()
        }
        // A new process reopens the files.
        repos.saves.hold("3kbatch", sources)
        withContext(Dispatchers.Default) { repos.runner().runAll() }

        assertNull(repos.saveStore.save("3kbatch"), "a finished save is dropped")
        assertEquals(setOf(8 to 0), setOfNotNull(repos.saveStore.importCounts("3kbatch")))
        val rkeys = repos.saveStore.savedUris("3kbatch").map { it.rkey }
        assertEquals(save.items.map { it.rkey }.toSet(), rkeys.toSet(), "each activity under its planned key, once")
        assertEquals(8, rkeys.size)
    }

    @Test
    fun aSessionStartResumesASaveLeftInTheDatabase() = runTest {
        val repos = fixture.repos()
        val sources = runs(4)
        val save = saveOf("3kbatch", sources)
        repos.saveStore.start(save)
        // The app was killed before the save ran; the next launch reopens the files and starts a session.
        repos.saves.hold("3kbatch", sources)
        val launched = mutableListOf<Did>()
        repos.saves.launcher = ImportSaveLauncher { did, inProcess ->
            launched += did
            repos.saves.runInProcess(did, inProcess)
        }

        repos.saves.resume(repos.saveStore.account, repos.saveStore, repos.runner())
        withContext(Dispatchers.Default) { withTimeout(30.seconds) { repos.saveStore.counts.first { it.isEmpty() } } }

        assertEquals(listOf(repos.saveStore.account), launched, "handed to the platform's launcher, once")
        assertEquals(save.items.map { it.rkey }.toSet(), repos.saveStore.savedUris("3kbatch").map { it.rkey }.toSet())
    }

    @Test
    fun aSessionStartWithNothingLeftLaunchesNothing() = runTest {
        val repos = fixture.repos()
        val launched = mutableListOf<Did>()
        repos.saves.launcher = ImportSaveLauncher { did, _ -> launched += did }

        repos.saves.resume(repos.saveStore.account, repos.saveStore, repos.runner())

        assertEquals(emptyList(), launched)
    }

    @Test
    fun aFileNeverKeptStopsTheSaveWithWhatItSaved() = runTest {
        val repos = fixture.repos()
        repos.saveStore.start(saveOf("3kbatch", runs(4)))
        // A new process (a reloaded web page) has no picked files, and nothing kept them.
        repos.saves.drop("3kbatch")
        withContext(Dispatchers.Default) { repos.runner().runAll() }

        assertEquals(ImportSaveEntity.STATE_STOPPED, repos.saveStore.save("3kbatch")?.state)
        val counts = repos.saveStore.counts.first().single()
        assertEquals(0 to 4, counts.done to counts.total)
        assertEquals(emptyList(), savedRkeys())
    }

    @Test
    fun aCancelledSaveStopsBetweenActivities() = runTest {
        val repos = fixture.repos()
        val sources = runs(4)
        val gate = CompletableDeferred<Unit>()
        repos.saveStore.start(saveOf("3kbatch", sources.mapIndexed { i, source -> if (i == 2) Gated(source, gate) else source }))
        withContext(Dispatchers.Default) {
            val run = launch { repos.runner().runAll() }
            repos.saveStore.counts.first { it.single().done == 2 }
            repos.saveStore.cancel("3kbatch")
            gate.complete(Unit)
            run.join()
        }

        assertNull(repos.saveStore.save("3kbatch"), "a cancelled save is dropped")
        assertEquals(2, repos.saveStore.savedUris("3kbatch").size, "the two saved stay")
    }
}
