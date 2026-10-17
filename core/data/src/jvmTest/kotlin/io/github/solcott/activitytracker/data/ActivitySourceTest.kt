package io.github.solcott.activitytracker.data

import io.github.solcott.activitytracker.activityfile.ActivityFileFormat
import io.github.solcott.activitytracker.activityfile.ImportedFile
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.data.activity.ActivityDraft
import io.github.solcott.activitytracker.data.activity.ActivityEdit
import io.github.solcott.activitytracker.data.activity.DraftSource
import io.github.solcott.activitytracker.data.activity.OwnActivity
import io.github.solcott.activitytracker.data.activity.SourceInfo
import io.github.solcott.activitytracker.data.activity.VisibilityChoice
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.RecordingView
import io.github.solcott.activitytracker.lexicon.app.SourceFormat
import io.github.solcott.activitytracker.lexicon.app.Sport
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * An imported activity's original file (`feed/activity-source-file`): a `feed.activitySource` record in the athlete's `self` space whatever
 * the activity's visibility, written, merged and deleted with it, and one stored blob for a whole logbook.
 */
class ActivitySourceTest {
    private val pds = FakeAthletePds()
    private val did = pds.did
    private val start = Instant.parse("2026-10-14T07:00:00Z")
    private val fit = byteArrayOf(14, 16, 0, 0, 0, 0, 0, 0) + ".FIT".encodeToByteArray() + ByteArray(64) { it.toByte() }

    private suspend fun sourceFile(
        bytes: ByteArray = fit,
        name: String = "Morning_Ride.fit",
        format: ActivityFileFormat = ActivityFileFormat.FIT,
    ) = requireNotNull(SourceFile.prepare(bytes, name, ImportedFile(format, gzipped = false, activities = emptyList())))

    private fun draft(source: DraftSource? = null, choice: VisibilityChoice = VisibilityChoice.PUBLIC) =
        ActivityDraft(
                type = ActivityType(Sport.RIDE),
                title = "Ride",
                description = null,
                startedAt = start,
                elapsed = 30.minutes,
                source = source,
            )
            .withVisibility(choice)

    private fun sourcesOf(rkey: String) = pds.placesOf(Nsid.FEED_ACTIVITY_SOURCE, rkey)

    private fun storedSource(rkey: String) = pds.stored.getValue(Visibility.SELF).getValue("${Nsid.FEED_ACTIVITY_SOURCE}/$rkey")

    private fun cidOf(rkey: String) =
        storedSource(rkey).getValue("file").jsonObject.getValue("ref").jsonObject.getValue("\$link").jsonPrimitive.content

    private fun view(uri: AtUri, recordings: List<AtUri> = emptyList()) =
        ActivityView(
            uri = uri,
            cid = Cid("bafyrec"),
            author = ProfileViewBasic(did, Handle("alice.test")),
            record = ActivityRecord(sport = Sport.RIDE, startedAt = start, elapsedTime = 1800, createdAt = Clock.System.now()),
            likeCount = 0,
            commentCount = 0,
            indexedAt = Clock.System.now(),
            recordings = recordings.map { RecordingView(it, start) }.ifEmpty { null },
        )

    @Test
    fun aPublicImportsSourceGoesToTheSelfSpaceOnly() = runTest {
        val uri = pds.session().activities.publish(draft(DraftSource(sourceFile())))
        val rkey = uri.rkey!!

        assertEquals(setOf(pds.repo), pds.placesOf(Nsid.FEED_ACTIVITY, rkey))
        assertEquals(setOf(Visibility.SELF), sourcesOf(rkey), "never in the repo")
        assertTrue(pds.stored.getValue(pds.repo).keys.none { Nsid.FEED_ACTIVITY_SOURCE in it })
        assertTrue("Morning_Ride" !in pds.stored.getValue(pds.repo).toString(), "nothing of the file is in the repo")
        val source = storedSource(rkey)
        assertEquals(uri.value, source.getValue("activity").jsonPrimitive.content)
        assertEquals(SourceFormat.FIT, source.getValue("format").jsonPrimitive.content)
        assertEquals("Morning_Ride.fit", source.getValue("fileName").jsonPrimitive.content)
        assertEquals(fit.size.toString(), source.getValue("size").jsonPrimitive.content)
        assertNull(source["entry"])
    }

    @Test
    fun anOfflineImportWritesItsSourceAfterReconnecting() = runTest {
        val s = pds.session()
        pds.offline = true
        val uri = s.activities.publish(draft(DraftSource(sourceFile(name = "Run.tcx", format = ActivityFileFormat.TCX))))
        assertEquals(2, s.activities.pendingWrites.first(), "the activity and its source wait together")
        assertEquals(SourceInfo("Run.tcx", SourceFormat.TCX, queued = true), s.sources.info(uri))

        pds.offline = false
        assertEquals(0, s.activities.sync())

        assertEquals(setOf(pds.repo), pds.placesOf(Nsid.FEED_ACTIVITY, uri.rkey!!))
        assertEquals(setOf(Visibility.SELF), sourcesOf(uri.rkey!!))
        assertEquals(SourceInfo("Run.tcx", SourceFormat.TCX, queued = false), s.sources.info(uri))
    }

    @Test
    fun aRecordedActivityHasNoSource() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())

        assertEquals(emptySet(), sourcesOf(uri.rkey!!))
        assertNull(s.sources.info(uri))
    }

    @Test
    fun aLogbookBatchStoresOneFileForEveryDive() = runTest {
        val s = pds.session()
        val logbook = sourceFile("<divelog/>".repeat(100).encodeToByteArray(), "log.ssrf", ActivityFileFormat.SUBSURFACE)

        val uris = s.activities.publishAll((0..2).map { draft(DraftSource(logbook, entry = it)) })

        assertEquals(1, pds.uploads, "the logbook is uploaded once")
        assertEquals(1, uris.map { cidOf(it.rkey!!) }.toSet().size, "every dive names the same blob")
        assertEquals(listOf("0", "1", "2"), uris.map { storedSource(it.rkey!!).getValue("entry").jsonPrimitive.content })
        assertTrue(s.db.outbox().blobs().isEmpty())
    }

    @Test
    fun readGivesBackTheOriginalBytes() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(DraftSource(sourceFile())))

        assertContentEquals(fit, s.sources.read(uri))
    }

    @Test
    fun deleteSkipsASessionThatCantWriteSources() = runTest {
        val s = pds.session(scope = "atproto transition:generic")

        s.sources.delete(AtUri.of(did, Nsid.FEED_ACTIVITY, "3kold"))

        assertTrue(s.db.outbox().pending(did.value).isEmpty())
    }

    @Test
    fun aVisibilityChangeLeavesTheSourceWhereItIs() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(DraftSource(sourceFile())))
        val writesBefore = pds.writes.size

        s.manager.setVisibility(view(uri), VisibilityChoice(Visibility.FOLLOWERS, Visibility.FOLLOWERS))

        assertEquals(setOf(Visibility.SELF), sourcesOf(uri.rkey!!))
        assertTrue(pds.writes.drop(writesBefore).none { Nsid.FEED_ACTIVITY_SOURCE in it }, "the source isn't touched")
    }

    @Test
    fun mergingIntoASourcelessDiveAddsTheImportsFile() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft())

        s.activities.mergeInto(OwnActivity(uri, Cid("bafyrec"), ownRecord(uri)), draft(DraftSource(sourceFile(name = "dive.xml"))))
        assertEquals(0, s.activities.sync())

        assertEquals("dive.xml", storedSource(uri.rkey!!).getValue("fileName").jsonPrimitive.content)
    }

    @Test
    fun mergingKeepsAnExistingSource() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(DraftSource(sourceFile(name = "log.uddf", format = ActivityFileFormat.UDDF))))
        val writesBefore = pds.writes.size

        s.activities.mergeInto(OwnActivity(uri, Cid("bafyrec"), ownRecord(uri)), draft(DraftSource(sourceFile())))
        assertEquals(0, s.activities.sync())

        assertTrue(pds.writes.drop(writesBefore).none { Nsid.FEED_ACTIVITY_SOURCE in it }, "the stored source isn't replaced")
        assertEquals("log.uddf", storedSource(uri.rkey!!).getValue("fileName").jsonPrimitive.content)
    }

    @Test
    fun mergingIntoAnUnsentActivityKeepsItsQueuedSource() = runTest {
        val s = pds.session()
        pds.offline = true
        val uri = s.activities.publish(draft(DraftSource(sourceFile(name = "first.fit"))))

        s.activities.mergeInto(OwnActivity(uri, cid = null, ownRecordQueued()), draft(DraftSource(sourceFile(name = "second.fit"))))
        pds.offline = false
        assertEquals(0, s.activities.sync())

        assertEquals(1, pds.writes.count { it.startsWith("put ${Visibility.SELF} ${Nsid.FEED_ACTIVITY_SOURCE}/") })
        assertEquals("first.fit", storedSource(uri.rkey!!).getValue("fileName").jsonPrimitive.content)
    }

    @Test
    fun deletingAnActivityDeletesItsSource() = runTest {
        val s = pds.session()
        val uri = s.activities.publish(draft(DraftSource(sourceFile())))

        s.manager.delete(view(uri), withRecordings = false)

        assertEquals(emptySet(), sourcesOf(uri.rkey!!))
    }

    @Test
    fun deletingWithRecordingsDeletesEverySource() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft(DraftSource(sourceFile())))
        val recording = s.activities.publish(draft(DraftSource(sourceFile())))

        s.manager.delete(view(main, recordings = listOf(recording)), withRecordings = true)

        assertEquals(emptySet(), sourcesOf(main.rkey!!))
        assertEquals(emptySet(), sourcesOf(recording.rkey!!))
    }

    @Test
    fun deletingOnlyTheMainActivityKeepsItsRecordingsSources() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft(DraftSource(sourceFile())))
        val recording = s.activities.publish(draft(DraftSource(sourceFile())))

        s.manager.delete(view(main, recordings = listOf(recording)), withRecordings = false)

        assertEquals(emptySet(), sourcesOf(main.rkey!!))
        assertEquals(setOf(Visibility.SELF), sourcesOf(recording.rkey!!))
    }

    @Test
    fun deletingOneDiveOfALogbookLeavesTheOthersReadable() = runTest {
        val s = pds.session()
        val bytes = "<uddf/>".repeat(50).encodeToByteArray()
        val logbook = sourceFile(bytes, "log.uddf", ActivityFileFormat.UDDF)
        val uris = s.activities.publishAll((0..2).map { draft(DraftSource(logbook, entry = it)) })

        s.manager.delete(view(uris[1]), withRecordings = false)

        assertEquals(emptySet(), sourcesOf(uris[1].rkey!!))
        assertContentEquals(bytes, s.sources.read(uris[0]))
        assertContentEquals(bytes, s.sources.read(uris[2]))
    }

    @Test
    fun editingAndUngroupingLeaveTheSources() = runTest {
        val s = pds.session()
        val main = s.activities.publish(draft(DraftSource(sourceFile())))
        val recording = s.activities.publish(draft(DraftSource(sourceFile())))
        val writesBefore = pds.writes.size

        s.manager.edit(view(main), ActivityEdit(title = "Renamed", description = "", diveSite = null, media = emptyList()))
        s.manager.ungroup(recording, from = main)

        assertEquals(setOf(Visibility.SELF), sourcesOf(main.rkey!!))
        assertEquals(setOf(Visibility.SELF), sourcesOf(recording.rkey!!))
        assertTrue(pds.writes.drop(writesBefore).none { Nsid.FEED_ACTIVITY_SOURCE in it })
    }

    private fun ownRecord(uri: AtUri): ActivityRecord =
        AtJson.decodeFromJsonElement(
            ActivityRecord.serializer(),
            pds.stored.getValue(pds.repo).getValue("${Nsid.FEED_ACTIVITY}/${uri.rkey}"),
        )

    private fun ownRecordQueued() =
        ActivityRecord(sport = Sport.RIDE, startedAt = start, elapsedTime = 1800, createdAt = Clock.System.now())
}
