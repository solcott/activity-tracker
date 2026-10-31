package io.github.solcott.activitytracker.activityfile

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest

class BatchReaderJvmTest {
    private val reader = ActivityFileReader(GarminJavaFitDecoder())
    private val run = FitFixtures.run(Instant.parse("2026-09-01T07:00:00Z"))
    private val ride = FitFixtures.indoorRide(Instant.parse("2026-09-02T07:00:00Z"))
    private val gpx = BatchFixtures.gpx(Instant.parse("2026-09-03T07:00:00Z"))
    private val logbook = UddfFixtures.logbook().encodeToByteArray()

    @Test
    fun scansAZipOfMixedFiles() = runTest {
        val zip =
            zipOf(
                "run.fit" to run,
                "rides/ride.fit.gz" to gzipped(ride),
                "walk.gpx" to gpx,
                "dives.uddf" to logbook,
                "broken.fit" to run.copyOf(run.size / 3),
                "readme.txt" to "hello".encodeToByteArray(),
                "__MACOSX/._run.fit" to ByteArray(10),
            )
        val events = BatchReader(listOf(ByteArrayImportSource("export.zip", zip)), reader).scan().toList()

        val read = events.filterIsInstance<ScanEvent.Read>()
        assertEquals(listOf("run.fit", "ride.fit.gz", "walk.gpx", "dives.uddf"), read.map { it.name })
        assertEquals(
            listOf(listOf("run.fit"), listOf("rides/ride.fit.gz"), listOf("walk.gpx"), listOf("dives.uddf")),
            read.map { it.ref.path },
        )
        assertEquals(listOf(1, 1, 1, 2), read.map { it.read.activities.size })
        assertTrue(read[1].read.gzipped)
        assertEquals(listOf(ScanEvent.Skipped("broken.fit", SkipReason.Damaged)), events.filterIsInstance<ScanEvent.Skipped>())
        assertEquals(ScanEvent.Progress(files = 4, activities = 5, ignored = 2), events.filterIsInstance<ScanEvent.Progress>().last())
    }

    @Test
    fun scansSeveralPickedFilesAndListsTheUnreadable() = runTest {
        val sources =
            listOf(
                ByteArrayImportSource("a.fit", run),
                ByteArrayImportSource("notes.txt", "not an activity".encodeToByteArray()),
                ByteArrayImportSource("b.gpx.gz", gzipped(gpx)),
            )
        val events = BatchReader(sources, reader).scan().toList()
        assertEquals(listOf(EntryRef(0), EntryRef(2)), events.filterIsInstance<ScanEvent.Read>().map { it.ref })
        assertEquals(listOf(ScanEvent.Skipped("notes.txt", SkipReason.Unsupported)), events.filterIsInstance<ScanEvent.Skipped>())
    }

    @Test
    fun aDevicesOtherFitFilesInAnArchiveAreLeftOutNotDamaged() = runTest {
        val monitoring = FitFixtures.monitoring(Instant.parse("2026-09-01T00:00:00Z"))
        val uploads = zipOf("run.fit" to run, "wellness.fit" to monitoring)
        val events = BatchReader(listOf(ByteArrayImportSource("export.zip", zipOf("uploads.zip" to uploads))), reader).scan().toList()
        assertEquals(listOf("run.fit"), events.filterIsInstance<ScanEvent.Read>().map { it.name })
        assertEquals(emptyList(), events.filterIsInstance<ScanEvent.Skipped>())
        assertEquals(ScanEvent.Progress(files = 1, activities = 1, ignored = 1), events.filterIsInstance<ScanEvent.Progress>().last())

        // Picked on its own, it's listed: the athlete chose it.
        val picked = BatchReader(listOf(ByteArrayImportSource("wellness.fit", monitoring)), reader).scan().toList()
        assertEquals(listOf(ScanEvent.Skipped("wellness.fit", SkipReason.Unsupported)), picked.filterIsInstance<ScanEvent.Skipped>())
    }

    @Test
    fun scansAZipInsideAZip() = runTest {
        val inner = zipOf("1.fit" to run, "2.fit" to ride, "summary.json" to "{}".encodeToByteArray())
        val outer = zipOf("DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip" to inner, "other.gpx" to gpx)
        val events = BatchReader(listOf(ByteArrayImportSource("garmin.zip", outer)), reader).scan().toList()
        val read = events.filterIsInstance<ScanEvent.Read>()
        assertEquals(
            listOf(
                listOf("DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip", "1.fit"),
                listOf("DI_CONNECT/DI-Connect-Uploaded-Files/UploadedFiles_0-_Part1.zip", "2.fit"),
                listOf("other.gpx"),
            ),
            read.map { it.ref.path },
        )
        assertEquals(1, events.filterIsInstance<ScanEvent.Progress>().last().ignored)
    }

    @Test
    fun skipsAMemberThatInflatesPastTheLimit() = runTest {
        val zip = zipOf("bomb.gpx" to ByteArray(MAX_UNZIPPED_BYTES + 1), "walk.gpx" to gpx)
        val events = BatchReader(listOf(ByteArrayImportSource("x.zip", zip)), reader).scan().toList()
        assertEquals(listOf(ScanEvent.Skipped("bomb.gpx", SkipReason.TooLarge)), events.filterIsInstance<ScanEvent.Skipped>())
        assertEquals(listOf("walk.gpx"), events.filterIsInstance<ScanEvent.Read>().map { it.name })
    }

    @Test
    fun readsChosenFilesAgainAsFound() = runTest {
        val inner = zipOf("1.fit" to run, "2.fit" to ride, "3.fit" to run)
        val outer = zipOf("parts/part1.zip" to inner, "walk.gpx.gz" to gzipped(gpx))
        val sources = listOf(ByteArrayImportSource("export.zip", outer), ByteArrayImportSource("ride.fit", ride))
        val batch = BatchReader(sources, reader)
        val refs = batch.scan().toList().filterIsInstance<ScanEvent.Read>().map { it.ref }

        val found = mutableMapOf<EntryRef, FoundFile>()
        val wanted = listOf(refs[2], refs[0], refs[4], refs[3])
        batch.readEach(wanted) { ref, result -> found[ref] = result.getOrThrow() }

        assertEquals(wanted.toSet(), found.keys)
        assertContentEquals(run, found.getValue(refs[2]).bytes)
        assertEquals("3.fit", found.getValue(refs[2]).name)
        // Found as stored: still gzipped.
        assertContentEquals(gzipped(gpx), found.getValue(refs[3]).bytes)
        assertContentEquals(ride, found.getValue(refs[4]).bytes)
        assertEquals("ride.fit", batch.read(refs[4]).name)
    }

    @Test
    fun readsANestedArchiveOncePerCall() = runTest {
        val inner = zipOf(*Array(10) { "$it.fit" to run })
        val outer = zipOf("part1.zip" to inner, stored = setOf("part1.zip"))
        val source = CountingSource(ByteArrayImportSource("export.zip", outer))
        val batch = BatchReader(listOf(source), reader)
        val refs = batch.scan().toList().filterIsInstance<ScanEvent.Read>().map { it.ref }
        assertEquals(10, refs.size)

        source.bytesRead = 0
        var count = 0
        batch.readEach(refs) { _, result -> result.getOrThrow().also { count++ } }
        assertEquals(10, count)
        assertTrue(source.bytesRead < inner.size * 2, "read ${source.bytesRead} bytes for a ${inner.size}-byte nested zip")
    }

    private class CountingSource(private val source: ImportSource) : ImportSource by source {
        var bytesRead = 0L

        override suspend fun read(offset: Long, length: Int): ByteArray = source.read(offset, length).also { bytesRead += it.size }
    }

    @Test
    fun reportsAFileThatIsGone() = runTest {
        val batch = BatchReader(listOf(ByteArrayImportSource("x.zip", zipOf("a.fit" to run))), reader)
        var result: Result<FoundFile>? = null
        batch.readEach(listOf(EntryRef(0, listOf("missing.fit")))) { _, read -> result = read }
        assertTrue(result!!.isFailure)
    }

    @Test
    fun stopsWhenTheCollectorCancels() = runTest {
        val zip = zipOf(*Array(20) { "f$it.fit" to run })
        val events = BatchReader(listOf(ByteArrayImportSource("x.zip", zip)), reader).scan().take(3).toList()
        assertEquals(3, events.size)
    }

    private fun gzipped(bytes: ByteArray): ByteArray =
        java.io.ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
}

/** Small activity files written in tests, never recorded ones. */
internal object BatchFixtures {
    fun gpx(start: Instant): ByteArray = buildString {
        append("""<gpx creator="Test" version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><type>running</type><trkseg>""")
        repeat(120) { i ->
            val time = start + (i * 10).seconds
            append("""<trkpt lat="${47.6 + i * 0.0001}" lon="-122.3"><time>$time</time></trkpt>""")
        }
        append("</trkseg></trk></gpx>")
    }
        .encodeToByteArray()
}
