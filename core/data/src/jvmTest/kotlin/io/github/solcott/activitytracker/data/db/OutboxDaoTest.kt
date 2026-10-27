package io.github.solcott.activitytracker.data.db

import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/** Rejected writes (data/outbox): what they hold back, and what the counts and the rejected list say. */
class OutboxDaoTest {
    private val did = "did:plc:alice"

    @Test
    fun aRejectedWriteHoldsBackOnlyItsRkey() = runTest {
        val dao = inMemoryDatabase().outbox()
        val activity = dao.insert(row(Nsid.FEED_ACTIVITY, "3kact", """{"title":"Morning ride"}"""))
        dao.insert(row(Nsid.FEED_ACTIVITY_DETAILS, "3kact"))
        dao.insert(row(Nsid.FEED_LIKE, "3klike"))
        dao.insert(row(Nsid.FEED_LIKE, "3kbob", did = "did:plc:bob"))
        assertEquals(3, dao.pendingCount(did).first())

        dao.markRejected(activity, at = 42, error = "XRPC 400 InvalidRecord")

        assertEquals(1, dao.pendingCount(did).first(), "the activity is rejected and its details are held; the like is still to send")
        val rejected = dao.rejected(did, Nsid.FEED_ACTIVITY).first().single()
        assertEquals(RejectedOutboxWrite("3kact", Nsid.FEED_ACTIVITY, "put", 42, """{"title":"Morning ride"}"""), rejected)
        assertEquals(1, dao.pendingCount("did:plc:bob").first(), "another account's writes aren't held")
    }

    @Test
    fun aRejectedDetailsWriteNamesItsQueuedActivity() = runTest {
        val dao = inMemoryDatabase().outbox()
        dao.insert(row(Nsid.FEED_ACTIVITY, "3kact", """{"title":"Old"}"""))
        dao.insert(row(Nsid.FEED_ACTIVITY, "3kact", """{"title":"New"}"""))
        val details = dao.insert(row(Nsid.FEED_ACTIVITY_DETAILS, "3kact"))
        dao.markRejected(details, at = 1, error = "refused")

        assertEquals("""{"title":"New"}""", dao.rejected(did, Nsid.FEED_ACTIVITY).first().single().activityJson)
    }

    @Test
    fun clearingAndDeletingAGroup() = runTest {
        val dao = inMemoryDatabase().outbox()
        val activity = dao.insert(row(Nsid.FEED_ACTIVITY, "3kact"))
        dao.insert(row(Nsid.FEED_ACTIVITY_DETAILS, "3kact"), OutboxBlobEntity("shared", did, byteArrayOf(1), "application/octet-stream"))
        dao.insert(row(Nsid.FEED_LIKE, "3klike"))
        dao.markRejected(activity, at = 1, error = "refused")

        dao.clearRejected(did, "3kact")
        assertEquals(3, dao.pendingCount(did).first())
        assertEquals(emptyList(), dao.rejected(did, Nsid.FEED_ACTIVITY).first())

        dao.deleteGroup(did, "3kact")
        assertEquals(listOf("3klike"), dao.pending(did).map { it.rkey })
        assertEquals(emptyList(), dao.blobs(), "the group's shared blob goes with it")
    }

    private fun row(collection: String, rkey: String, json: String = "{}", did: String = this.did) =
        OutboxEntity(did = did, operation = "put", collection = collection, rkey = rkey, recordJson = json, createdAt = 0)
}
