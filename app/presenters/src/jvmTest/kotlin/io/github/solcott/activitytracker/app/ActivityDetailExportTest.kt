package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest

/** Exporting the owner's activity as a file (`feed/activity-export`) from its detail screen. */
class ActivityDetailExportTest : ActivityDetailTestBase() {
    private val start = Instant.parse("2026-09-30T10:00:00Z")
    private val route = (0..2).map { LocationSample(51.5, -0.12 + it * 0.001, null, null, 5.0, start + it.seconds) }
    private val heartRate =
        """{"uri":"$activityUri","visibility":"public","channels":["heartRate"],"data":{"time":[0,1,2],"heartRate":[120,130,140]}}"""

    @Test
    fun theOwnerSeesTheFormatsTheirDataAllows() = runTest {
        author = staleAlice
        track = route
        streams = heartRate
        detail(repos()) {
            val export = awaitUntil { it.loaded()?.export?.formats?.size == 2 }.loaded()!!.export!!
            assertEquals(persistentListOf(ExportFormat.GPX, ExportFormat.TCX), export.formats)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anIndoorActivityOffersTcxOnly() = runTest {
        author = staleAlice
        streams = heartRate
        detail(repos()) {
            val export = awaitUntil { it.loaded()?.export != null }.loaded()!!.export!!
            assertEquals(persistentListOf(ExportFormat.TCX), export.formats)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun someoneElsesActivityOffersNoExport() = runTest {
        track = route
        streams = heartRate
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.charts != null }.loaded()!!
            assertNull(loaded.export)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aManualEntryOffersNoExport() = runTest {
        author = staleAlice
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.isOwn == true }.loaded()!!
            assertNull(loaded.export)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun exportingTwiceWhileTheFirstRunsBuildsOneFile() = runTest {
        author = staleAlice
        track = route
        streams = heartRate
        detail(repos()) {
            val ready = awaitUntil { it.loaded()?.export?.formats?.size == 2 }.loaded()!!
            val before = streamsRequests
            val gate = CompletableDeferred<Unit>()
            streamsGate = gate
            ready.eventSink(ActivityDetailEvent.Export(ExportFormat.GPX))
            ready.eventSink(ActivityDetailEvent.Export(ExportFormat.GPX))
            awaitUntil { it.loaded()?.export?.busy == true }
            gate.complete(Unit)
            val doneState = awaitUntil { it.loaded()?.export?.let { e -> e.file != null && !e.busy } == true }
            val done = doneState.loaded()!!.export!!
            assertEquals(1, streamsRequests - before, "one export read the streams")
            val file = done.file!!
            assertTrue(file.name.startsWith("Run 2026-") && file.name.endsWith(".gpx"), file.name)
            assertTrue("<gpxtpx:hr>130</gpxtpx:hr>" in file.bytes.decodeToString())

            doneState.eventSink(ActivityDetailEvent.ExportHandedOff)
            assertNull(awaitUntil { it.loaded()?.export?.file == null }.loaded()!!.export!!.file)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnsentActivitySaysSo() = runTest {
        author = staleAlice
        track = route
        val r = repos()
        val queued =
            OutboxEntity(
                did = alice.value,
                operation = "put",
                collection = Nsid.FEED_ACTIVITY_STREAMS,
                rkey = "3kbob1",
                recordJson = "{}",
                createdAt = 0,
            )
        r.db.outbox().insert(queued)
        detail(r) {
            awaitUntil { it.loaded()?.export != null }.eventSink(ActivityDetailEvent.Export(ExportFormat.GPX))
            val export = awaitUntil { it.loaded()?.export?.error != null }.loaded()!!.export!!
            assertEquals(UiError.Kind.ExportNotSent, export.error!!.kind)
            assertNull(export.file)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOfflineExportFails() = runTest {
        author = staleAlice
        track = route
        detail(repos()) {
            val ready = awaitUntil { it.loaded()?.export != null }
            offline = true
            ready.eventSink(ActivityDetailEvent.Export(ExportFormat.GPX))
            val export = awaitUntil { it.loaded()?.export?.error != null }.loaded()!!.export!!
            assertEquals(UiError.Kind.ExportActivity, export.error!!.kind)
            assertNull(export.file)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
