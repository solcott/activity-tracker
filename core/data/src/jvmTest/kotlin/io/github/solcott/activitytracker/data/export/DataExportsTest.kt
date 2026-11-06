package io.github.solcott.activitytracker.data.export

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** An account's export over its life (auth/data-export): one runner, cancelled, saved, expired, swept, resumed. */
class DataExportsTest {
    private val pds = FakeAthletePds()
    private val files = InMemoryExportFiles()
    private var now = Instant.parse("2026-11-05T12:00:00Z")
    private val clock =
        object : Clock {
            override fun now() = now
        }
    private lateinit var exports: DataExports

    @AfterTest
    fun stop() {
        if (::exports.isInitialized) exports.cancelAll()
    }

    private suspend fun setUp(): Pair<FakeAthletePds.Session, DataExporter> {
        val s = pds.session()
        exports = DataExports(s.db, files, clock)
        return s to DataExporter(s.db, s.clients, files, s.exporter, exports, clock, Logger)
    }

    private fun gear(rkey: String) {
        pds.stored.getOrPut(pds.repo) { mutableMapOf() }["${Nsid.GEAR_ITEM}/$rkey"] =
            Json.parseToJsonElement("""{"${'$'}type":"${Nsid.GEAR_ITEM}"}""").jsonObject
    }

    @Test
    fun oneRunnerPerAccountAtATime() = runTest {
        val (s, _) = setUp()
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            exports.exclusive(s.clients.did) {
                holding.complete(Unit)
                release.await()
                "first"
            }
        }
        holding.await()
        assertNull(exports.exclusive(s.clients.did) { "second" }, "a second runner returns at once")
        release.complete(Unit)
        assertEquals("first", first.await())
    }

    @Test
    fun cancellingStopsAndDeletesIt() = runTest {
        val (s, exporter) = setUp()
        gear("3ka")
        val id = exporter.start()
        exporter.run(id)
        assertTrue(files.list(id).isNotEmpty())

        exports.cancel(s.clients.did)

        assertNull(exports.current(s.clients.did))
        assertEquals(emptyList(), files.list(id))
        assertFalse(exports.hasExport(s.clients.did))
    }

    @Test
    fun progressFollowsTheExport() = runTest {
        val (s, exporter) = setUp()
        assertNull(exports.progress(s.clients.did).first())
        gear("3ka")
        val id = exporter.start()
        exporter.run(id)

        val progress = assertNotNull(exports.progress(s.clients.did).first())
        assertEquals(DataExportEntity.STATE_READY, progress.state)
        assertEquals(1, progress.records)
        assertEquals(now, progress.finishedAt)
    }

    @Test
    fun savingDeletesTheAppsCopy() = runTest {
        val (s, exporter) = setUp()
        val id = exporter.start()
        exporter.run(id)

        exports.saved(id)

        assertNull(exports.current(s.clients.did))
        assertEquals(emptyList(), files.list(id))
    }

    @Test
    fun signingOutCancelsIt() = runTest {
        val (s, exporter) = setUp()
        exporter.start()
        assertTrue(exports.hasExport(s.clients.did))

        exports.signedOut(s.clients.did)

        assertFalse(exports.hasExport(s.clients.did))
    }

    @Test
    fun aReadyExportExpiresAfterAWeek() = runTest {
        val (s, exporter) = setUp()
        val id = exporter.start()
        exporter.run(id)
        now += 6.days
        exports.sweep()
        assertNotNull(exports.current(s.clients.did), "kept for a week")

        now += 2.days
        exports.sweep()

        assertNull(exports.current(s.clients.did))
        assertEquals(emptyList(), files.list(id))
    }

    @Test
    fun aFolderNoExportNamesIsSwept() = runTest {
        setUp()
        files.write("orphan", "records/repo/x/y.json", "{}".encodeToByteArray())

        exports.sweep()

        assertEquals(emptyList(), files.exportIds())
    }

    @Test
    fun aSessionResumesAnExportLeftRunning() = runTest {
        val (s, exporter) = setUp()
        val id = exporter.start()
        val ran = CompletableDeferred<Unit>()
        exports.launcher = DataExportLauncher { _, inProcess -> ran.complete(Unit) }

        exports.resume(s.clients.did) { exporter.run(id) }

        ran.await()
    }

    @Test
    fun aPausedExportWaitsForTheAthlete() = runTest {
        val (s, exporter) = setUp()
        val id = exporter.start()
        s.db.dataExports().setState(id, DataExportEntity.STATE_PAUSED, DataExportEntity.REASON_NO_SPACE)
        var launched = false
        exports.launcher = DataExportLauncher { _, _ -> launched = true }

        exports.resume(s.clients.did) { exporter.run(id) }

        assertFalse(launched)
    }

    @Test
    fun aPausedExportGoesOnOnceThereIsRoom() = runTest {
        val (s, exporter) = setUp()
        gear("3ka")
        files.free = 0
        val id = exporter.start()
        exporter.run(id)
        assertEquals(DataExportEntity.STATE_PAUSED, s.db.dataExports().export(id)?.state)

        files.free = null
        exporter.run(id)

        assertEquals(DataExportEntity.STATE_READY, s.db.dataExports().export(id)?.state)
    }

    @Test
    fun aKilledRunResumesInANewRunner() = runTest {
        val (s, exporter) = setUp()
        (1..3).forEach { gear("3k$it") }
        val id = exporter.start()
        exporter.run(id)
        // As if killed before the README: a new runner over the same database finishes what's left.
        s.db.dataExports().setState(id, DataExportEntity.STATE_RUNNING)
        files.files[id]?.remove(DataExporter.README)

        DataExporter(s.db, s.clients, files, s.exporter, exports, clock, Logger).run(id)

        assertEquals(DataExportEntity.STATE_READY, s.db.dataExports().export(id)?.state)
        assertNotNull(files.read(id, DataExporter.README))
    }

    @Test
    fun withoutAPlatformLauncherItRunsInProcessToReady() = runTest {
        val (s, exporter) = setUp()
        gear("3ka")
        exporter.start()

        exports.launch(s.clients.did) { exporter.runCurrent() }

        val ready = exports.progress(s.clients.did).first { it?.state == DataExportEntity.STATE_READY }
        assertEquals(1, ready?.records)
    }

    @Test
    fun aSecondEntryPointReturnsWhileOneRuns() = runTest {
        val (s, exporter) = setUp()
        exporter.start()
        val holding = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async {
            exports.exclusive(s.clients.did) {
                holding.complete(Unit)
                release.await()
            }
        }
        holding.await()

        assertFalse(exporter.runCurrent(), "the background job finds the page's run going and returns")
        release.complete(Unit)
        first.await()
    }
}
