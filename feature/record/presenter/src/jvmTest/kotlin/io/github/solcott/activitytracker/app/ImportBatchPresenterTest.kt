package io.github.solcott.activitytracker.app

import androidx.compose.runtime.Composable
import com.slack.circuit.test.FakeNavigator
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.ByteArrayImportSource
import io.github.solcott.activitytracker.activityfile.ExportKind
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.solcott.activitytracker.app.record.ActivityTypeEvent
import io.github.solcott.activitytracker.app.record.BatchExport
import io.github.solcott.activitytracker.app.record.BatchFilter
import io.github.solcott.activitytracker.app.record.BatchSaveRunner
import io.github.solcott.activitytracker.app.record.DuplicateAction
import io.github.solcott.activitytracker.app.record.DuplicateCheck
import io.github.solcott.activitytracker.app.record.ImportActivityEvent
import io.github.solcott.activitytracker.app.record.ImportActivityPresenter
import io.github.solcott.activitytracker.app.record.ImportActivityScreen
import io.github.solcott.activitytracker.app.uploads.UploadsScreen
import io.github.solcott.activitytracker.data.activity.UploadSource
import io.github.solcott.activitytracker.data.activity.UploadState
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Importing several activities at once: several files, a zip, an account export; reviewing, previewing and saving them. */
class ImportBatchPresenterTest {
    private val fixture = SessionTestFixture()

    /** A GPX track of [minutes] minutes from [start], a point every 10 s, of the GPX [type]. */
    private fun gpx(start: String, minutes: Int = 30, type: String = "running"): ByteArray {
        val begin = Instant.parse(start)
        return buildString {
            append("""<gpx creator="Test" version="1.1" xmlns="http://www.topografix.com/GPX/1/1"><trk><type>$type</type><trkseg>""")
            repeat(minutes * 6 + 1) { i ->
                val time = begin + (i * 10).seconds
                append("""<trkpt lat="${47.6 + i * 0.0001}" lon="-122.3"><time>$time</time></trkpt>""")
            }
            append("</trkseg></trk></gpx>")
        }
            .encodeToByteArray()
    }

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (path, bytes) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun picked(vararg files: Pair<String, ByteArray>) =
        ImportActivityEvent.FilesPicked(files.map { (name, bytes) -> ByteArrayImportSource(name, bytes) }.toImmutableList())

    private fun presenter(repos: SessionTestFixture.Repos, navigator: FakeNavigator) =
        @Composable {
            ImportActivityPresenter(
                ImportActivityScreen(),
                navigator,
                repos.activities,
                repos.manager,
                ActivityFileReader { error("not FIT") },
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
                BatchSaveRunner(
                    repos.saveStore,
                    repos.saves,
                    repos.clients,
                    repos.activities,
                    repos.manager,
                    ActivityFileReader { error("not FIT") },
                ),
            )
        }

    /** The activities written to the repo and to tier spaces, by the place each went. */
    private fun saved(): List<Pair<String, ActivityRecord>> =
        fixture.putRecords.filter { it.isActivity() }.map { Visibility.PUBLIC to it.activity() } +
            fixture.spacePuts.filter { (_, record) -> record.isActivity() }.map { (tier, record) -> tier to record.activity() }

    private fun JsonObject.isActivity() = getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY

    private fun JsonObject.activity() = AtJson.decodeFromJsonElement(ActivityRecord.serializer(), this)

    private fun sources() =
        (fixture.putRecords + fixture.spacePuts.map { it.second }).filter {
            it.getValue("\$type").jsonPrimitive.content == Nsid.FEED_ACTIVITY_SOURCE
        }

    @Test
    fun oneFileWithOneActivityKeepsThePreviewForm() = runTest {
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked("run.gpx" to gpx("2026-09-01T07:00:00Z")))
            val read = awaitUntil { it.preview != null }
            assertNull(read.batch)
            assertEquals("run.gpx", read.preview?.fileName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun severalFilesMakeABatchOldestFirst() = runTest {
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked("b.gpx" to gpx("2026-09-02T07:00:00Z"), "a.gpx" to gpx("2026-09-01T07:00:00Z")))
            val read = awaitUntil { it.batch != null }
            assertNull(read.preview)
            assertEquals(listOf("a.gpx", "b.gpx"), read.batch?.rows?.map { it.fileName })
            assertEquals(listOf(true, true), read.batch?.rows?.map { it.selected })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun cancellingReadingKeepsNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow =
            object : ImportSource by ByteArrayImportSource("slow.gpx", gpx("2026-09-01T07:00:00Z")) {
                override suspend fun read(offset: Long, length: Int): ByteArray {
                    gate.await()
                    return ByteArrayImportSource(name, gpx("2026-09-01T07:00:00Z")).read(offset, length)
                }
            }
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(ImportActivityEvent.FilesPicked(persistentListOf(slow, slow)))
            awaitUntil { it.reading }.eventSink(ImportActivityEvent.CancelReading)
            val cancelled = awaitUntil { !it.reading }
            gate.complete(Unit)
            assertNull(cancelled.batch)
            assertNull(cancelled.preview)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun declinedActivitiesAreNotSaved() = runTest {
        val files = (0 until 10).map { "$it.gpx" to gpx("2026-09-${(it + 1).toString().padStart(2, '0')}T07:00:00Z") }
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked("week.zip" to zip(*files.toTypedArray())))
            val read = awaitUntil { it.batch?.rows?.size == 10 && it.duplicateCheck == DuplicateCheck.Done }
            read.eventSink(ImportActivityEvent.ItemToggled(3))
            awaitUntil { it.batch?.selectedCount == 9 }.eventSink(ImportActivityEvent.ItemToggled(7))
            awaitUntil { it.batch?.selectedCount == 8 }.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            assertEquals(0, awaitUntil { it.batchSaved != null }.batchSaved?.skipped)
            cancelAndIgnoreRemainingEvents()
        }
        val starts = saved().map { it.second.startedAt.toString().take(10) }
        assertEquals(8, starts.size)
        assertFalse("2026-09-04" in starts || "2026-09-08" in starts, starts.toString())
    }

    @Test
    fun aPreviewedActivityTakesTheTypeGivenItThere() = runTest {
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem()
                .eventSink(picked("walk.gpx" to gpx("2026-09-01T07:00:00Z"), "ride.gpx" to gpx("2026-09-02T07:00:00Z", type = "cycling")))
            awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }.eventSink(ImportActivityEvent.ItemOpened(0))
            val opened = awaitUntil { it.batch?.preview?.preview != null }
            assertEquals(ActivityType(Sport.RUN), opened.batch?.preview?.type?.selected)
            opened.batch?.preview?.type?.eventSink?.invoke(ActivityTypeEvent.Select(ActivityType(Sport.HIKE)))
            awaitUntil { it.batch?.preview?.type?.selected == ActivityType(Sport.HIKE) }.eventSink(ImportActivityEvent.ItemClosed)
            val closed = awaitUntil { it.batch?.preview == null }
            assertEquals(listOf(ActivityType(Sport.HIKE), ActivityType(Sport.RIDE)), closed.batch?.rows?.map { it.type })
            closed.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf(Sport.HIKE, Sport.RIDE), saved().sortedBy { it.second.startedAt }.map { it.second.sport })
    }

    @Test
    fun aStravaExportsTitlesApplyAndAPrivateActivityStaysPrivate() = runTest {
        val csv =
            """
            Activity ID,Activity Name,Activity Type,Activity Description,Activity Private,Filename
            1,Lunch loop,Run,Windy,false,activities/1.gpx
            2,Secret spot,Ride,,true,activities/2.gpx.gz
            """
                .trimIndent()
        val export =
            zip(
                "activities.csv" to csv.encodeToByteArray(),
                "activities/1.gpx" to gpx("2026-09-01T07:00:00Z"),
                "activities/2.gpx.gz" to gzip(gpx("2026-09-02T07:00:00Z", type = "cycling")),
            )
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked("export.zip" to export))
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals(listOf("Lunch loop", "Secret spot"), read.batch?.rows?.map { it.title })
            assertEquals(listOf(null, Visibility.SELF), read.batch?.rows?.map { it.narrowedTo })
            assertEquals<List<BatchExport>?>(listOf(BatchExport(ExportKind.Strava, privacy = true)), read.batch?.exports)
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        val byTitle = saved().associate { it.second.title to it.first }
        assertEquals(mapOf<String?, String>("Lunch loop" to Visibility.PUBLIC, "Secret spot" to Visibility.SELF), byTitle)
        assertEquals("Windy", saved().first { it.second.title == "Lunch loop" }.second.description)
        // The member as it was in the zip, gzipped, under its own name; the zip itself is stored nowhere.
        val names = sources().map { it.getValue("fileName").jsonPrimitive.content }.toSet()
        assertEquals(setOf("1.gpx", "2.gpx.gz"), names)
    }

    @Test
    fun eachPickedFileIsItsOwnSource() = runTest {
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem()
                .eventSink(
                    picked(
                        "a.gpx" to gpx("2026-09-01T07:00:00Z"),
                        "b.gpx" to gpx("2026-09-02T07:00:00Z"),
                        "c.gpx" to gpx("2026-09-03T07:00:00Z"),
                    )
                )
            awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
                .eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        val sources = sources()
        assertEquals(listOf("a.gpx", "b.gpx", "c.gpx"), sources.map { it.getValue("fileName").jsonPrimitive.content })
        // The fake PDS names every blob alike, so each source is told apart by its file: one per picked file.
        assertEquals(3, sources.size)
    }

    @Test
    fun aWatchAndAnEdgeRidePickedTogetherCombine() = runTest {
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem()
                .eventSink(
                    picked(
                        "watch.gpx" to gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling"),
                        "edge.gpx" to gpx("2026-09-01T08:58:00Z", minutes = 123, type = "cycling"),
                    )
                )
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            val rows = assertNotNull(read.batch).rows
            assertEquals(listOf("edge.gpx", "watch.gpx"), rows.map { it.fileName })
            assertEquals(0, rows[1].duplicate?.inBatchIndex)
            assertEquals(DuplicateAction.Combine, rows[1].duplicate?.action)
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        val combined = saved().map { it.second }.single { it.combined != null }
        assertEquals(2, combined.combined?.members?.size)
    }

    @Test
    fun everyActivityOfASaveSharesOneNewBatch() = runTest {
        suspend fun save(vararg files: Pair<String, ByteArray>) =
            presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
                awaitItem().eventSink(picked(*files))
                awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
                    .eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
                awaitUntil { it.batchSaved != null }
                cancelAndIgnoreRemainingEvents()
            }
        save(
            "watch.gpx" to gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling"),
            "edge.gpx" to gpx("2026-09-01T08:58:00Z", minutes = 123, type = "cycling"),
            "run.gpx" to gpx("2026-09-03T07:00:00Z"),
        )
        val first = saved().map { it.second }
        assertEquals(4, first.size, "two copies, their combined ride and the run")
        val batch = assertNotNull(first.first().importBatch)
        assertTrue(Tid.isValid(batch))
        assertEquals(listOf(batch), first.map { it.importBatch }.distinct())
        save("a.gpx" to gpx("2026-09-05T07:00:00Z"), "b.gpx" to gpx("2026-09-06T07:00:00Z"))
        val second = saved().map { it.second }.drop(first.size).map { it.importBatch }.distinct()
        assertEquals(1, second.size)
        assertNotEquals(batch, second.single())
    }

    @Test
    fun aLoggedActivityCombinedWithACopyNamesNoBatch() = runTest {
        val logged =
            ActivityView(
                uri = AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, "3kride"),
                cid = Cid("bafy3kride"),
                author = ProfileViewBasic(fixture.did, Handle("alice.test")),
                record =
                    ActivityRecord(Sport.RIDE, Instant.parse("2026-09-01T08:58:00Z"), 123 * 60L, Instant.parse("2026-09-02T00:00:00Z")),
                likeCount = 0,
                commentCount = 0,
                indexedAt = Instant.parse("2026-09-02T00:00:00Z"),
            )
        fixture.ownActivities = listOf(logged)
        fixture.activityRecord = AtJson.encodeToString(ActivityRecord.serializer(), logged.record)
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem()
                .eventSink(
                    picked(
                        "edge.gpx" to gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling"),
                        "run.gpx" to gpx("2026-09-03T07:00:00Z"),
                    )
                )
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals(DuplicateAction.Combine, read.batch?.rows?.get(0)?.duplicate?.action)
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        val records = saved().map { it.second }
        val combined = records.single { it.combined != null }
        assertNull(combined.importBatch, "the logged ride keeps having none")
        assertEquals(
            1,
            records.filter { it.combined == null }.mapNotNull { it.importBatch }.distinct().size,
            "the copy and the run share one",
        )
    }

    @Test
    fun copiesCombinedFromAnExportSaveAtTheNarrowestOfTheirVisibilities() = runTest {
        val csv =
            """
            Activity ID,Activity Name,Activity Type,Activity Private,Filename
            1,Watch ride,Ride,false,activities/1.gpx
            2,Edge ride,Ride,true,activities/2.gpx
            """
                .trimIndent()
        val export =
            zip(
                "activities.csv" to csv.encodeToByteArray(),
                // The public ride starts first, so it leads the group.
                "activities/1.gpx" to gpx("2026-09-01T08:58:00Z", minutes = 123, type = "cycling"),
                "activities/2.gpx" to gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling"),
            )
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked("export.zip" to export))
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals(DuplicateAction.Combine, read.batch?.rows?.get(1)?.duplicate?.action)
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        // The private ride never lands in the repo by joining a public one: the whole group goes to Only me.
        assertEquals(setOf(Visibility.SELF), saved().map { it.first }.toSet())
        assertEquals(3, saved().size)
    }

    @Test
    fun aPrivateCopyOfAPublicLoggedActivityIsSavedOnItsOwn() = runTest {
        val logged =
            ActivityView(
                uri = AtUri.of(fixture.did, Nsid.FEED_ACTIVITY, "3kride"),
                cid = Cid("bafy3kride"),
                author = ProfileViewBasic(fixture.did, Handle("alice.test")),
                record =
                    ActivityRecord(Sport.RIDE, Instant.parse("2026-09-01T08:58:00Z"), 123 * 60L, Instant.parse("2026-09-02T00:00:00Z")),
                likeCount = 0,
                commentCount = 0,
                indexedAt = Instant.parse("2026-09-02T00:00:00Z"),
            )
        fixture.ownActivities = listOf(logged)
        fixture.activityRecord = AtJson.encodeToString(ActivityRecord.serializer(), logged.record)
        val csv =
            """
            Activity ID,Activity Name,Activity Type,Activity Private,Filename
            1,Edge ride,Ride,true,activities/1.gpx
            2,Run,Run,false,activities/2.gpx
            """
                .trimIndent()
        val export =
            zip(
                "activities.csv" to csv.encodeToByteArray(),
                "activities/1.gpx" to gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling"),
                "activities/2.gpx" to gpx("2026-09-03T07:00:00Z"),
            )
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked("export.zip" to export))
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            assertEquals(DuplicateAction.Combine, read.batch?.rows?.get(0)?.duplicate?.action)
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        // Joining the public ride would publish it: it stays Only me, uncombined, and the logged ride is left as it was.
        assertEquals(
            mapOf<String?, String>("Edge ride" to Visibility.SELF, "Run" to Visibility.PUBLIC),
            saved().associate { it.second.title to it.first },
        )
        assertTrue(saved().none { it.second.combined != null })
    }

    @Test
    fun aFileGoneBeforeSavingIsSkipped() = runTest {
        var gone = false
        val vanishing =
            object : ImportSource by ByteArrayImportSource("gone.gpx", gpx("2026-09-02T07:00:00Z")) {
                private val inner = ByteArrayImportSource("gone.gpx", gpx("2026-09-02T07:00:00Z"))

                override suspend fun read(offset: Long, length: Int): ByteArray {
                    if (gone) throw io.github.solcott.activitytracker.activityfile.ActivityFileException("gone")
                    return inner.read(offset, length)
                }
            }
        val repos = fixture.repos()
        val navigator = FakeNavigator(ImportActivityScreen())
        presenterTestOf(presenter(repos, navigator)) {
            awaitItem()
                .eventSink(
                    ImportActivityEvent.FilesPicked(
                        persistentListOf(ByteArrayImportSource("kept.gpx", gpx("2026-09-01T07:00:00Z")), vanishing)
                    )
                )
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            gone = true
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            val done = awaitUntil { it.batchSaved != null }
            assertEquals(1, done.batchSaved?.saved)
            assertEquals(1, done.batchSaved?.skipped)

            // The upload queue lists the save as one import, its skipped file in it (feed/batch-import).
            val import = repos.uploads.queue.first().imports.single()
            assertEquals(UploadSource.Files("kept.gpx", 2), import.source)
            assertEquals(1, import.saved)
            assertEquals(1, import.skipped)
            assertEquals(listOf(UploadState.Skipped), import.finished.filter { it.state == UploadState.Skipped }.map { it.state })

            done.eventSink(ImportActivityEvent.Saved.ViewUploads)
            navigator.awaitPop()
            assertEquals(UploadsScreen, navigator.awaitNextScreen())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, saved().size)
    }

    @Test
    fun filteringToDuplicatesAndDeselectingLeavesTheNewOnes() = runTest {
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem()
                .eventSink(
                    picked(
                        "watch.gpx" to gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling"),
                        "edge.gpx" to gpx("2026-09-01T08:58:00Z", minutes = 123, type = "cycling"),
                        "run.gpx" to gpx("2026-09-03T07:00:00Z"),
                    )
                )
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            read.eventSink(ImportActivityEvent.FilterChanged(BatchFilter.Duplicates))
            val duplicates = awaitUntil { it.batch?.filter == BatchFilter.Duplicates }
            assertEquals(listOf("watch.gpx"), duplicates.batch?.shown?.map { it.fileName })
            duplicates.eventSink(ImportActivityEvent.AllShownSelected(false))
            val deselected = awaitUntil { it.batch?.selectedCount == 2 }
            assertEquals(listOf(true, false, true), deselected.batch?.rows?.map { it.selected })
            assertEquals(DuplicateAction.Ignore, deselected.batch?.rows?.get(1)?.duplicate?.action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun eachZonelessLogbookIsPlacedOnItsOwn() = runTest {
        fun logbook(time: String) =
            """<?xml version="1.0"?><uddf version="3.2.0" xmlns="http://www.streit.cc/uddf/3.2/"><profiledata><repetitiongroup id="r">
              <dive id="d1"><informationbeforedive><datetime>2026-09-20T$time:00</datetime></informationbeforedive>
              <samples><waypoint><depth>0</depth><divetime>0</divetime></waypoint><waypoint><depth>12</depth><divetime>600</divetime></waypoint>
              <waypoint><depth>0</depth><divetime>1200</divetime></waypoint></samples></dive></repetitiongroup></profiledata></uddf>"""
                .encodeToByteArray()
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked("trip1.uddf" to logbook("09:00"), "trip2.uddf" to logbook("14:00")))
            val read = awaitUntil { it.batch?.clocks?.size == 2 }
            val before = assertNotNull(read.batch).rows.map { it.start }
            read.eventSink(ImportActivityEvent.FileZoneChanged(read.batch!!.clocks[1].key, "Pacific/Kiritimati"))
            val moved = awaitUntil { it.batch?.clocks?.get(1)?.clock?.zone?.id == "Pacific/Kiritimati" }
            val zones = moved.batch!!.clocks.map { it.clock.zone.id }
            assertTrue(zones[0] != "Pacific/Kiritimati", zones.toString())
            // The clocks read the same in their own zones; only the moved file's moment changes.
            assertEquals(before, moved.batch!!.rows.map { it.start })
            moved.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        val starts = saved().map { it.second.startedAt }
        // 14:00 on Kiritimati's clock (UTC+14) is midnight UTC; the other log stays in this device's zone.
        assertTrue(Instant.parse("2026-09-20T00:00:00Z") in starts, starts.toString())
        assertTrue(starts.any { it != Instant.parse("2026-09-20T00:00:00Z") }, starts.toString())
    }

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    @Test
    fun theSameRideInTwoExportsIsSavedOnce() = runTest {
        val ride = gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling")
        val csv = "Activity ID,Activity Name,Activity Type,Filename\n1,Saturday ride,Ride,activities/1.gpx\n"
        val strava = zip("activities.csv" to csv.encodeToByteArray(), "activities/1.gpx" to ride)
        val other = zip("rides/saturday.gpx" to ride)
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked("strava.zip" to strava, "other.zip" to other))
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            val copy = assertNotNull(read.batch?.rows?.get(1)?.duplicate)
            assertTrue(copy.exactCopy)
            assertEquals(DuplicateAction.Ignore, copy.action)
            assertEquals(1, read.batch?.exactCopies)
            read.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(listOf("Saturday ride"), saved().map { it.second.title })
    }

    @Test
    @Suppress("InjectDispatcher") // The pacer runs on real threads, as the app's does, apart from the presenter's test clock.
    fun aLargeImportIsSentByTheSessionsPacerWithNoFeedLoad() = runTest {
        val repos = fixture.repos()
        // As `App` runs it for the session.
        backgroundScope.launch(Dispatchers.Default) { repos.pacer.run() }
        val days = 60
        val files =
            (1..days).map { day ->
                "run$day.gpx" to gpx("2026-07-${(day % 28 + 1).toString().padStart(2, '0')}T0${day % 10}:00:00Z", minutes = 1)
            }
        presenterTestOf(presenter(repos, FakeNavigator(ImportActivityScreen()))) {
            awaitItem().eventSink(picked(*files.toTypedArray()))
            awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
                .eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        withContext(Dispatchers.Default) { withTimeout(30.seconds) { repos.activities.pendingWrites.first { it == 0 } } }
        assertEquals(days, saved().size, "every activity reached the PDS though nothing synced after the save")
    }

    @Test
    fun ignoringEveryShownDuplicateLeavesTheNewOnes() = runTest {
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem()
                .eventSink(
                    picked(
                        "watch.gpx" to gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling"),
                        "edge.gpx" to gpx("2026-09-01T08:58:00Z", minutes = 123, type = "cycling"),
                        "run.gpx" to gpx("2026-09-03T07:00:00Z"),
                    )
                )
            awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
                .eventSink(ImportActivityEvent.FilterChanged(BatchFilter.Duplicates))
            awaitUntil { it.batch?.filter == BatchFilter.Duplicates }
                .eventSink(ImportActivityEvent.ShownDuplicatesAction(DuplicateAction.Ignore))
            val ignored = awaitUntil { it.batch?.rows?.get(1)?.duplicate?.action == DuplicateAction.Ignore }
            assertEquals(2, ignored.batch?.selectedCount)
            ignored.eventSink(ImportActivityEvent.SaveBatch(persistentMapOf()))
            awaitUntil { it.batchSaved != null }
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, saved().size)
        assertTrue(saved().none { it.second.combined != null })
    }

    @Test
    fun anOpenedDuplicateShowsWhatItRepeats() = runTest {
        presenterTestOf(presenter(fixture.repos(), FakeNavigator(ImportActivityScreen()))) {
            awaitItem()
                .eventSink(
                    picked(
                        "watch.gpx" to gpx("2026-09-01T09:00:00Z", minutes = 120, type = "cycling"),
                        "edge.gpx" to gpx("2026-09-01T08:58:00Z", minutes = 123, type = "cycling"),
                    )
                )
            val read = awaitUntil { it.batch != null && it.duplicateCheck == DuplicateCheck.Done }
            val first = assertNotNull(read.batch?.rows?.first())
            read.eventSink(ImportActivityEvent.ItemOpened(1))
            val matched = assertNotNull(awaitUntil { it.batch?.preview?.matched != null }.batch?.preview?.matched)
            assertEquals(first.start, matched.start)
            assertEquals(first.type, matched.type)
            assertEquals(first.elapsed, matched.elapsed)
            assertEquals(first.distanceMeters, matched.distanceMeters)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
