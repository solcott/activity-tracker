package io.github.solcott.activitytracker.app

import io.github.solcott.activitytracker.app.detail.ActivityDetailEvent
import io.github.solcott.activitytracker.app.detail.OriginalFile
import io.github.solcott.activitytracker.data.activity.ExportFormat
import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            val export = awaitUntil { it.loaded()?.export?.formats?.size == 3 }.loaded()!!.export!!
            assertEquals(persistentListOf(ExportFormat.GPX, ExportFormat.TCX, ExportFormat.FIT), export.formats)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOwnDiveAlsoOffersUddf() = runTest {
        author = staleAlice
        sport = "dive"
        track = route
        streams = heartRate
        detail(repos()) {
            val export = awaitUntil { it.loaded()?.export?.formats?.size == 4 }.loaded()!!.export!!
            assertEquals(persistentListOf(ExportFormat.GPX, ExportFormat.TCX, ExportFormat.FIT, ExportFormat.UDDF), export.formats)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOwnRunDoesntOfferUddf() = runTest {
        author = staleAlice
        track = route
        streams = heartRate
        detail(repos()) {
            val export = awaitUntil { it.loaded()?.export?.formats?.size == 3 }.loaded()!!.export!!
            assertFalse(ExportFormat.UDDF in export.formats)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anIndoorActivityOffersTcxAndFit() = runTest {
        author = staleAlice
        streams = heartRate
        detail(repos()) {
            val export = awaitUntil { it.loaded()?.export != null }.loaded()!!.export!!
            assertEquals(persistentListOf(ExportFormat.TCX, ExportFormat.FIT), export.formats)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theNoteSaysLapsAreMissingOnlyWhenNoneAreStored() = runTest {
        author = staleAlice
        streams = heartRate
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.export != null && it.loaded()?.charts != null }.loaded()!!
            assertFalse(loaded.export!!.hasLaps)
            cancelAndIgnoreRemainingEvents()
        }
        streams =
            """{"uri":"$activityUri","visibility":"public","channels":["heartRate","laps"],"data":{"time":[0,1,2],""" +
                """"heartRate":[120,130,140],"laps":[{"start":0,"elapsed":1},{"start":1,"elapsed":1}]}}"""
        detail(repos()) {
            assertTrue(awaitUntil { it.loaded()?.export?.hasLaps == true }.loaded()!!.export!!.hasLaps)
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
            val ready = awaitUntil { it.loaded()?.export?.formats?.size == 3 }.loaded()!!
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

    private val fit = byteArrayOf(14, 16, 0, 0, 0, 0, 0, 0) + ".FIT".encodeToByteArray() + ByteArray(32) { it.toByte() }

    @Test
    fun anImportedActivityOffersItsOriginalFile() = runTest {
        author = staleAlice
        track = route
        streams = heartRate
        original = "Morning_Ride.fit" to fit
        detail(repos()) {
            val export = awaitUntil { it.loaded()?.export?.original != null && it.loaded()?.export?.formats?.size == 3 }.loaded()!!.export!!
            assertEquals(OriginalFile("Morning_Ride.fit", "fit"), export.original)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRecordedActivityOffersNoOriginal() = runTest {
        author = staleAlice
        track = route
        streams = heartRate
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.export?.formats?.size == 3 && it.loaded()?.charts != null }.loaded()!!
            assertNull(loaded.export!!.original)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anImportWithNeitherTrackNorStreamsStillOffersExport() = runTest {
        author = staleAlice
        original = "Summary.fit" to fit
        detail(repos()) {
            val export = awaitUntil { it.loaded()?.export != null }.loaded()!!.export!!
            assertEquals(persistentListOf(), export.formats, "nothing to rebuild a file from")
            assertEquals("Summary.fit", export.original?.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun someoneElsesImportOffersNoOriginal() = runTest {
        track = route
        streams = heartRate
        original = "Bobs_Ride.fit" to fit
        detail(repos()) {
            val loaded = awaitUntil { it.loaded()?.charts != null }.loaded()!!
            assertNull(loaded.export)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun downloadingTwiceWhileTheFirstRunsDownloadsOnce() = runTest {
        author = staleAlice
        original = "Morning_Ride.fit" to fit
        detail(repos()) {
            val ready = awaitUntil { it.loaded()?.export?.original != null }
            val gate = CompletableDeferred<Unit>()
            originalGate = gate
            ready.eventSink(ActivityDetailEvent.DownloadOriginal)
            ready.eventSink(ActivityDetailEvent.DownloadOriginal)
            awaitUntil { it.loaded()?.export?.busy == true }
            gate.complete(Unit)
            val done = awaitUntil { it.loaded()?.export?.let { e -> e.file != null && !e.busy } == true }.loaded()!!.export!!
            assertEquals(1, originalReads, "one download read the file")
            val file = done.file!!
            assertEquals("Morning_Ride.fit", file.name)
            assertEquals("application/vnd.ant.fit", file.mimeType)
            assertContentEquals(fit, file.bytes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anOriginalThatCantBeReadFails() = runTest {
        author = staleAlice
        original = "Morning_Ride.fit" to fit
        detail(repos()) {
            val ready = awaitUntil { it.loaded()?.export?.original != null }
            offline = true
            ready.eventSink(ActivityDetailEvent.DownloadOriginal)
            val export = awaitUntil { it.loaded()?.export?.error != null }.loaded()!!.export!!
            assertEquals(UiError.Kind.DownloadOriginal, export.error!!.kind)
            assertNull(export.file)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
