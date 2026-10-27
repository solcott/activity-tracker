package io.github.solcott.activitytracker.data

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.lexicon.Nsid
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Writes the PDS refuses (data/outbox): kept as rejected, holding back only their own record's writes, while failures that may pass stop
 * the run.
 */
class OutboxRejectionTest {
    private val pds = FakeAthletePds()

    private class Outbox(val session: FakeAthletePds.Session, val writer: OutboxWriter, val processor: OutboxProcessor)

    private suspend fun outbox(): Outbox {
        val session = pds.session()
        return Outbox(
            session,
            OutboxWriter(session.db, session.clients, Clock.System),
            OutboxProcessor(session.db, session.clients, Logger),
        )
    }

    private suspend fun Outbox.put(collection: String, rkey: String) {
        writer.put(collection, rkey, JsonObject.serializer(), buildJsonObject { put("\$type", collection) })
    }

    private suspend fun Outbox.rows() = session.db.outbox().pending(pds.did.value)

    @Test
    fun aRefusedWriteIsRejectedAndNotSentAgain() = runTest {
        val outbox = outbox()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        outbox.put(Nsid.FEED_ACTIVITY, "3kact")

        assertEquals(1, outbox.processor.drain(), "a rejected write isn't applied")
        assertNotNull(outbox.rows().single().rejectedAt)
        val puts = pds.writes.size

        outbox.processor.drain()
        assertEquals(puts, pds.writes.size, "a rejected write isn't sent on the next run")
        assertEquals(1, outbox.rows().single().attempts)
    }

    @Test
    fun aRejectedActivityHoldsBackItsDetailsWhileALikeGoesOn() = runTest {
        val outbox = outbox()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        outbox.put(Nsid.FEED_ACTIVITY, "3kact")
        outbox.put(Nsid.FEED_ACTIVITY_DETAILS, "3kact")
        outbox.put(Nsid.FEED_ACTIVITY_STREAMS, "3kact")
        outbox.put(Nsid.FEED_LIKE, "3klike")

        assertEquals(3, outbox.processor.drain(), "the activity and the two writes it holds back")

        assertEquals(listOf("put repo ${Nsid.FEED_LIKE}/3klike"), pds.writes)
        assertEquals(
            listOf(Nsid.FEED_ACTIVITY, Nsid.FEED_ACTIVITY_DETAILS, Nsid.FEED_ACTIVITY_STREAMS),
            outbox.rows().map { it.collection },
        )
        assertEquals(0, outbox.session.activities.pendingWrites.first(), "nothing is left to upload; the rest waits for the athlete")
    }

    @Test
    fun aFilteredDrainDoesntSendAWriteHeldByOneItSkipped() = runTest {
        val outbox = outbox()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        outbox.put(Nsid.FEED_ACTIVITY, "3kact")
        outbox.processor.drain()
        pds.failPutsOf = null
        outbox.put(Nsid.FEED_ACTIVITY_ZONES, "3kact")

        assertEquals(1, outbox.processor.drain { it.collection == Nsid.FEED_ACTIVITY_ZONES })
        assertEquals(emptyList(), pds.writes)
    }

    @Test
    fun aServerErrorStopsTheRunWithNothingRejected() = runTest {
        val outbox = outbox()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        pds.failPutsStatus = HttpStatusCode.InternalServerError
        outbox.put(Nsid.FEED_ACTIVITY, "3kact")
        outbox.put(Nsid.FEED_LIKE, "3klike")

        assertEquals(2, outbox.processor.drain())
        assertEquals(emptyList(), pds.writes, "the like waits behind the activity")
        assertEquals(listOf(null, null), outbox.rows().map { it.rejectedAt })
    }

    @Test
    fun beingAskedToWaitStopsTheRunWithNothingRejected() = runTest {
        val outbox = outbox()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        pds.failPutsStatus = HttpStatusCode.TooManyRequests
        outbox.put(Nsid.FEED_ACTIVITY, "3kact")

        assertEquals(1, outbox.processor.drain())
        assertNull(outbox.rows().single().rejectedAt)
    }

    @Test
    fun anExpiredSessionIsntARejection() = runTest {
        val outbox = outbox()
        pds.failPutsOf = Nsid.FEED_ACTIVITY
        pds.failPutsStatus = HttpStatusCode.Unauthorized
        outbox.put(Nsid.FEED_ACTIVITY, "3kact")

        outbox.processor.drain()
        assertNull(outbox.rows().single().rejectedAt)
    }

    @Test
    fun noConnectionStopsTheRunWithNothingRejected() = runTest {
        val outbox = outbox()
        outbox.put(Nsid.FEED_ACTIVITY, "3kact")
        outbox.put(Nsid.FEED_LIKE, "3klike")
        pds.pdsUnreachable = true

        assertEquals(2, outbox.processor.drain())
        assertEquals(listOf(null, null), outbox.rows().map { it.rejectedAt })

        pds.pdsUnreachable = false
        assertEquals(0, outbox.processor.drain())
        assertEquals(listOf("put repo ${Nsid.FEED_ACTIVITY}/3kact", "put repo ${Nsid.FEED_LIKE}/3klike"), pds.writes)
    }
}
