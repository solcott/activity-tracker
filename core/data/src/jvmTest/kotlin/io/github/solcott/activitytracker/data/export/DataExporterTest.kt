package io.github.solcott.activitytracker.data.export

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.activityfile.ActivityFileFormat
import io.github.solcott.activitytracker.activityfile.ActivityFileReader
import io.github.solcott.activitytracker.activityfile.GarminJavaFitDecoder
import io.github.solcott.activitytracker.activityfile.ImportedFile
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.data.FakeAthletePds
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.db.DataExportItemEntity
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.DetailCategory
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.location.Gpx
import io.github.solcott.activitytracker.location.LocationSample
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** A whole-account data export (auth/data-export), read straight from the athlete's PDS. */
class DataExporterTest {
    private val pds = FakeAthletePds()
    private val files = InMemoryExportFiles()
    private val start = Instant.parse("2026-10-14T07:00:00Z")
    private val route = (0..2).map { LocationSample(47.6 + it * 0.0001, -122.3, 50.0, null, 5.0, start + it.seconds) }
    private val fit = byteArrayOf(14, 16, 0, 0, 0, 0, 0, 0) + ".FIT".encodeToByteArray() + ByteArray(64) { it.toByte() }

    private fun draft(title: String = "Lunch run", gpx: Boolean = true) =
        ActivityDraft(
            type = ActivityType(Sport.RUN),
            title = title,
            description = null,
            startedAt = start,
            elapsed = 30.minutes,
            distanceMeters = 5000.0,
            route = if (gpx) route else emptyList(),
            gpx = if (gpx) Gpx.write("Run", Sport.RUN, route).encodeToByteArray() else null,
        )

    private suspend fun session(scope: String? = null) =
        pds.session(scope).let { s ->
            s to DataExporter(s.db, s.clients, files, s.exporter, DataExports(s.db, files, Clock.System), Clock.System, Logger)
        }

    private suspend fun exportAll(scope: String? = null): Pair<String, DataExportEntity> {
        val (s, exporter) = session(scope)
        val id = exporter.start()
        exporter.run(id)
        return id to requireNotNull(s.db.dataExports().export(id))
    }

    private fun manifest(id: String): JsonObject = Json.parseToJsonElement(files.text(id, DataExporter.MANIFEST)!!).jsonObject

    private fun put(place: String, collection: String, rkey: String, json: String) {
        pds.stored.getOrPut(place) { mutableMapOf() }["$collection/$rkey"] = Json.parseToJsonElement(json).jsonObject
    }

    private fun staged(id: String, place: String, collection: String, rkey: String) =
        files.text(id, DataExporter.recordPath(place, collection, rkey))

    @Test
    fun anOnlyMeRunWithItsTrackAndOriginal() = runTest {
        val (s, _) = session()
        val file = ImportedFile(ActivityFileFormat.FIT, false, activities = emptyList())
        val uri =
            s.activities.publish(
                draft()
                    .copy(source = DraftSource(requireNotNull(SourceFile.prepare(fit, "Morning_Ride.fit", file))))
                    .withVisibility(VisibilityChoice(Visibility.SELF))
            )
        val rkey = requireNotNull(uri.rkey)

        val (id, export) = exportAll()

        assertEquals(DataExportEntity.STATE_READY, export.state)
        assertNotNull(staged(id, AthleteSpace.SELF, Nsid.FEED_ACTIVITY, rkey))
        assertNotNull(staged(id, AthleteSpace.SELF, Nsid.FEED_ACTIVITY_TRACK, rkey))
        assertNotNull(staged(id, AthleteSpace.SELF, Nsid.FEED_ACTIVITY_SOURCE, rkey))
        assertNull(staged(id, DataExporter.REPO, Nsid.FEED_ACTIVITY, rkey), "an Only me run is never in the repo")
        val original = assertNotNull(files.read(id, "activities/2026-10-14-Morning_Ride.fit"))
        assertContentEquals(fit, original, "the original, byte for byte")
        assertEquals(
            "activities/2026-10-14-Morning_Ride.fit",
            manifest(id)["activities"]!!.jsonObject[uri.toString()]?.toString()?.trim('"'),
        )
        assertTrue(files.list(id).any { it.startsWith("blobs/self/") }, "the track's blob is staged")
    }

    @Test
    fun privateSettingsAreIncluded() = runTest {
        put(AthleteSpace.SELF, Nsid.ACTOR_PRIVACY_ZONES, "self", """{"${'$'}type":"${Nsid.ACTOR_PRIVACY_ZONES}","zones":[]}""")
        put(AthleteSpace.MUTUALS, Nsid.ACTOR_PRIVATE_DETAILS, "self", """{"${'$'}type":"${Nsid.ACTOR_PRIVATE_DETAILS}","details":{}}""")
        put(AthleteSpace.SELF, Nsid.ACTOR_ACTIVITY_DEFAULTS, "self", """{"${'$'}type":"${Nsid.ACTOR_ACTIVITY_DEFAULTS}"}""")
        put(AthleteSpace.SELF, Nsid.ACTOR_SEGMENT_PREFS, "self", """{"${'$'}type":"${Nsid.ACTOR_SEGMENT_PREFS}","matching":true}""")

        val (id, _) = exportAll()

        assertNotNull(staged(id, AthleteSpace.SELF, Nsid.ACTOR_PRIVACY_ZONES, "self"))
        assertNotNull(staged(id, AthleteSpace.MUTUALS, Nsid.ACTOR_PRIVATE_DETAILS, "self"))
        assertNotNull(staged(id, AthleteSpace.SELF, Nsid.ACTOR_ACTIVITY_DEFAULTS, "self"))
        assertNotNull(staged(id, AthleteSpace.SELF, Nsid.ACTOR_SEGMENT_PREFS, "self"))
    }

    @Test
    fun interactionsAreIncluded() = runTest {
        listOf(Nsid.FEED_LIKE, Nsid.FEED_COMMENT, Nsid.GRAPH_BLOCK, Nsid.GRAPH_MUTE, Nsid.FEED_SEGMENT_NAME_VOTE).forEach {
            put(AthleteSpace.INTERACTIONS, it, "3kint", """{"${'$'}type":"$it"}""")
        }

        val (id, _) = exportAll()

        listOf(Nsid.FEED_LIKE, Nsid.FEED_COMMENT, Nsid.GRAPH_BLOCK, Nsid.GRAPH_MUTE, Nsid.FEED_SEGMENT_NAME_VOTE).forEach {
            assertNotNull(staged(id, AthleteSpace.INTERACTIONS, it, "3kint"), it)
        }
    }

    @Test
    fun aMissingSpaceIsEmpty() = runTest {
        put(DataExporter.REPO, Nsid.ACTOR_PROFILE, "self", """{"${'$'}type":"${Nsid.ACTOR_PROFILE}"}""")

        val (id, export) = exportAll()

        assertEquals(DataExportEntity.STATE_READY, export.state)
        assertFalse(files.list(id).any { it.startsWith("records/mutuals/") })
        assertNotNull(staged(id, DataExporter.REPO, Nsid.ACTOR_PROFILE, "self"))
    }

    @Test
    fun aRunSplitAcrossTiersIsStagedWhereEachPartLivesAndRebuildsWhole() = runTest {
        val (s, _) = session()
        val streams = ActivityStreamData(time = listOf(0, 1, 2), heartRate = listOf(140, 141, 142), speed = listOf(3000, 3000, 3000))
        val uri =
            s.activities.publish(
                draft()
                    .copy(avgHeartRate = 141, streams = streams)
                    .withVisibility(VisibilityChoice(Visibility.PUBLIC, mapOf(DetailCategory.HEART_RATE to Visibility.SELF)))
            )
        val rkey = requireNotNull(uri.rkey)

        val (id, _) = exportAll()

        assertNotNull(staged(id, DataExporter.REPO, Nsid.FEED_ACTIVITY, rkey))
        assertNotNull(staged(id, AthleteSpace.SELF, Nsid.FEED_ACTIVITY_DETAILS, rkey))
        val path = manifest(id)["activities"]!!.jsonObject[uri.toString()].toString().trim('"')
        assertEquals("activities/2026-10-14-Lunch run.fit", path)
        val read = ActivityFileReader(GarminJavaFitDecoder()).read(files.read(id, path)!!).single()
        assertEquals(listOf(140, 141, 142), read.streams?.heartRate, "the self tier's heart rate, merged in")
        assertEquals(141, read.avgHeartRate)
        assertEquals(3, read.samples.size, "the full track")
    }

    @Test
    fun aBlobTwoRecordsNameIsFetchedOnce() = runTest {
        pds.blobs["bafyshared"] = "x".encodeToByteArray()
        val blob = """{"${'$'}type":"blob","ref":{"${'$'}link":"bafyshared"},"mimeType":"image/webp","size":1}"""
        put(DataExporter.REPO, Nsid.GEAR_ITEM, "3ka", """{"${'$'}type":"${Nsid.GEAR_ITEM}","photo":$blob}""")
        put(DataExporter.REPO, Nsid.GEAR_ITEM, "3kb", """{"${'$'}type":"${Nsid.GEAR_ITEM}","photo":$blob}""")

        val (id, _) = exportAll()

        assertEquals(listOf("blobs/repo/bafyshared"), files.list(id).filter { it.startsWith("blobs/") })
        assertEquals(1, pds.requests.count { it.startsWith("/xrpc/com.atproto.sync.getBlob") })
    }

    @Test
    fun theAppViewIsNeverAsked() = runTest {
        val (s, _) = session()
        s.activities.publish(draft())
        pds.appViewOffline = true

        val (id, export) = exportAll()

        assertEquals(DataExportEntity.STATE_READY, export.state)
        assertEquals(1, manifest(id)["activities"]!!.jsonObject.size)
    }

    @Test
    fun anUnreadableBlobIsListedNotFatal() = runTest {
        val blob = """{"${'$'}type":"blob","ref":{"${'$'}link":"bafygone"},"mimeType":"image/webp","size":1}"""
        put(DataExporter.REPO, Nsid.GEAR_ITEM, "3ka", """{"${'$'}type":"${Nsid.GEAR_ITEM}","photo":$blob}""")

        val (id, export) = exportAll()

        assertEquals(DataExportEntity.STATE_READY, export.state)
        val problems = manifest(id)["problems"].toString()
        assertTrue("bafygone" in problems && DataExporter.REASON_NOT_FOUND in problems, problems)
        assertTrue("1 item(s) that couldn't be read" in files.text(id, DataExporter.README)!!)
    }

    @Test
    fun offlineFromTheStartFailsResumably() = runTest {
        val (s, exporter) = session()
        val id = exporter.start()
        pds.offline = true

        exporter.run(id)

        val export = requireNotNull(s.db.dataExports().export(id))
        assertEquals(DataExportEntity.STATE_FAILED, export.state)
        assertEquals(DataExportEntity.REASON_OFFLINE, export.reason)

        pds.offline = false
        exporter.run(id)
        assertEquals(DataExportEntity.STATE_READY, s.db.dataExports().export(id)?.state, "trying again finishes it")
    }

    @Test
    fun withoutSpaceAccessOnlyTheRepoIsExported() = runTest {
        put(AthleteSpace.SELF, Nsid.ACTOR_PRIVACY_ZONES, "self", """{"${'$'}type":"${Nsid.ACTOR_PRIVACY_ZONES}"}""")
        put(DataExporter.REPO, Nsid.ACTOR_PROFILE, "self", """{"${'$'}type":"${Nsid.ACTOR_PROFILE}"}""")

        val (id, export) = exportAll(scope = "atproto transition:generic")

        assertFalse(export.spacesIncluded)
        assertNotNull(staged(id, DataExporter.REPO, Nsid.ACTOR_PROFILE, "self"))
        assertFalse(files.list(id).any { it.startsWith("records/self/") })
        assertTrue("couldn't read your athlete spaces" in files.text(id, DataExporter.README)!!)
    }

    @Test
    fun otherAppsRecordsAreLeftOut() = runTest {
        put(DataExporter.REPO, "app.bsky.feed.post", "3kpost", """{"text":"hi"}""")
        put(AthleteSpace.SELF, "app.bsky.graph.block", "3kblock", """{"subject":"did:plc:bob"}""")

        val (id, _) = exportAll()

        assertFalse(files.list(id).any { "app.bsky" in it }, files.list(id).toString())
    }

    @Test
    fun unsentChangesAreCounted() = runTest {
        val (s, exporter) = session()
        pds.offline = true
        runCatching { s.activities.publish(draft()) }
        pds.offline = false

        val id = exporter.start()

        assertTrue((s.db.dataExports().export(id)?.unsentChanges ?: 0) > 0)
    }

    @Test
    fun aDeviceWithoutRoomPauses() = runTest {
        pds.blobs["bafybig"] = ByteArray(10)
        val blob = """{"${'$'}type":"blob","ref":{"${'$'}link":"bafybig"},"mimeType":"image/webp","size":${200L * 1024 * 1024}}"""
        put(DataExporter.REPO, Nsid.GEAR_ITEM, "3ka", """{"${'$'}type":"${Nsid.GEAR_ITEM}","photo":$blob}""")
        files.free = 300L * 1024 * 1024

        val (_, export) = exportAll()

        assertEquals(DataExportEntity.STATE_PAUSED, export.state)
        assertEquals(DataExportEntity.REASON_NO_SPACE, export.reason)
        assertTrue((export.neededBytes ?: 0) > 0)
    }

    @Test
    fun anAccountHasOneExport() = runTest {
        val (_, exporter) = session()
        assertEquals(exporter.start(), exporter.start())
    }

    @Test
    fun aResumedExportRewritesNothingItFinished() = runTest {
        val (s, exporter) = session()
        s.activities.publish(draft())
        val id = exporter.start()
        exporter.run(id)
        val writes = files.writes
        // As if the process died after the activity's file and before the manifest.
        s.db.dataExports().setState(id, DataExportEntity.STATE_RUNNING)
        exporter.run(id)
        assertEquals(writes + 2, files.writes, "only the manifest and README again")
        assertEquals(1, s.db.dataExports().items(id, DataExportItemEntity.KIND_ACTIVITY).size)
    }

    @Test
    fun aListingInterruptedPartWayGoesOnFromItsCursor() = runTest {
        (1..150).forEach {
            put(DataExporter.REPO, Nsid.GEAR_ITEM, "3k${it.toString().padStart(3, '0')}", """{"${'$'}type":"${Nsid.GEAR_ITEM}"}""")
        }
        val (s, exporter) = session()
        val id = exporter.start()
        // The connection drops after the first page of gear.
        pds.onRequest = { path -> if (path.endsWith("listRecords") && pds.requests.last().contains(Nsid.GEAR_ITEM)) pds.offline = true }
        exporter.run(id)
        assertEquals(DataExportEntity.STATE_FAILED, s.db.dataExports().export(id)?.state)
        assertEquals(100, files.list(id).count { "/${Nsid.GEAR_ITEM}/" in it })

        pds.onRequest = null
        pds.offline = false
        pds.requests.clear()
        exporter.run(id)

        assertEquals(150, files.list(id).count { "/${Nsid.GEAR_ITEM}/" in it })
        assertEquals(1, pds.requests.count { it.contains(Nsid.GEAR_ITEM) }, "only the second page is read again")
        assertEquals(150, manifest(id)["counts"]!!.jsonObject["records"].toString().toInt())
    }
}
