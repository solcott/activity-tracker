package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.PendingBlob
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivitySourceRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.SourceFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Outbox writes sharing one blob (`feed/activity-source-file`): stored once, uploaded once, and a refused original dropped. */
class OutboxSharedBlobTest {
    private val pds = FakeAthletePds()
    private val logbook = byteArrayOf(0x1f, 0x8b.toByte(), 8, 0) + "a logbook".encodeToByteArray()

    private fun source(rkey: String, entry: Int) =
        ActivitySourceRecord(
            activity = AtUri("at://${pds.did}/${Nsid.FEED_ACTIVITY}/$rkey"),
            format = SourceFormat.UDDF,
            fileName = "log.uddf",
            size = 100,
            compressed = true,
            createdAt = Instant.parse("2026-10-16T12:00:00Z"),
            entry = entry,
        )

    private class Outbox(val session: FakeAthletePds.Session, val writer: OutboxWriter, val processor: OutboxProcessor)

    private suspend fun outbox(): Outbox {
        val session = pds.session()
        return Outbox(
            session,
            OutboxWriter(session.db, session.clients, Clock.System),
            OutboxProcessor(session.db, session.clients, Logger),
        )
    }

    private suspend fun Outbox.putSources(vararg rkeys: String, bytes: ByteArray = logbook) {
        val self = session.clients.tiers.uri(AthleteSpace.SELF)
        rkeys.forEachIndexed { entry, rkey ->
            writer.put(
                Nsid.FEED_ACTIVITY_SOURCE,
                rkey,
                ActivitySourceRecord.serializer(),
                source(rkey, entry),
                PendingBlob(bytes, ActivitySourceRecord.MIME_TYPE, ActivitySourceRecord.FILE_FIELD, key = "logbook"),
                space = self,
            )
        }
    }

    private suspend fun Outbox.putRepo(rkey: String) {
        writer.put(Nsid.FEED_ACTIVITY, rkey, JsonObject.serializer(), buildJsonObject { put("\$type", Nsid.FEED_ACTIVITY) })
    }

    private fun storedSourceCid(rkey: String) =
        pds.stored
            .getValue(AthleteSpace.SELF)
            .getValue("${Nsid.FEED_ACTIVITY_SOURCE}/$rkey")
            .getValue("file")
            .jsonObject
            .getValue("ref")
            .jsonObject
            .getValue("\$link")
            .jsonPrimitive
            .content

    @Test
    fun threeWritesSharingAKeyStoreTheBlobOnce() = runTest {
        val outbox = outbox()
        outbox.putSources("3ka", "3kb", "3kc")

        val db = outbox.session.db
        assertEquals(1, db.outbox().blobs().size)
        val rows = db.outbox().pending(pds.did.value)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.blob == null && it.blobKey == "logbook" && it.blobField == ActivitySourceRecord.FILE_FIELD })
    }

    @Test
    fun aSharedBlobIsUploadedOnceAndDroppedOnceSent() = runTest {
        val outbox = outbox()
        outbox.putSources("3ka", "3kb", "3kc")

        assertEquals(0, outbox.processor.drain())

        assertEquals(1, pds.uploads)
        assertEquals(setOf(storedSourceCid("3ka")), setOf("3ka", "3kb", "3kc").map(::storedSourceCid).toSet())
        assertTrue(outbox.session.db.outbox().blobs().isEmpty())
        assertTrue(outbox.session.db.outbox().pending(pds.did.value).isEmpty())
    }

    @Test
    fun aSavedRefThePdsNoLongerHasIsUploadedAgain() = runTest {
        val outbox = outbox()
        outbox.putSources("3ka", "3kb")
        assertEquals(0, outbox.processor.drain { it.rkey == "3ka" })
        assertEquals(1, outbox.session.db.outbox().blobs().size, "the second write still names it")

        pds.blobs.clear() // The first record was deleted elsewhere and the PDS reclaimed the blob.
        assertEquals(0, outbox.processor.drain())

        assertEquals(2, pds.uploads)
        assertTrue(storedSourceCid("3kb") in pds.blobs)
        assertTrue(outbox.session.db.outbox().blobs().isEmpty())
    }

    @Test
    fun anOriginalThePdsRefusesAsTooLargeIsDroppedAndTheRestAreSent() = runTest {
        val outbox = outbox()
        pds.blobUploadLimit = 4
        outbox.putRepo("3kact")
        outbox.putSources("3kact")
        outbox.putRepo("3knext")

        assertEquals(0, outbox.processor.drain())

        assertEquals(setOf("${Nsid.FEED_ACTIVITY}/3kact", "${Nsid.FEED_ACTIVITY}/3knext"), pds.stored.getValue(pds.repo).keys)
        assertNull(pds.stored[AthleteSpace.SELF]?.get("${Nsid.FEED_ACTIVITY_SOURCE}/3kact"))
        assertTrue(outbox.session.db.outbox().pending(pds.did.value).isEmpty())
        assertTrue(outbox.session.db.outbox().blobs().isEmpty())
    }

    @Test
    fun otherWritesRefusedAsTooLargeAreRejectedAndTheRestSent() = runTest {
        val outbox = outbox()
        pds.blobUploadLimit = 4
        outbox.writer.put(
            Nsid.FEED_ACTIVITY_TRACK,
            "3ktrk",
            JsonObject.serializer(),
            buildJsonObject { put("\$type", Nsid.FEED_ACTIVITY_TRACK) },
            PendingBlob("<?xml version=\"1.0\"?><gpx/>".encodeToByteArray(), "application/gpx+xml", "track"),
            space = outbox.session.clients.tiers.uri(AthleteSpace.SELF),
        )
        outbox.putRepo("3knext")

        assertEquals(1, outbox.processor.drain(), "only the track's original is dropped; a track is kept as rejected (data/outbox)")
        assertNotNull(outbox.session.db.outbox().pending(pds.did.value).single().rejectedAt)
        assertEquals(setOf("${Nsid.FEED_ACTIVITY}/3knext"), pds.stored.getValue(pds.repo).keys)
    }
}
