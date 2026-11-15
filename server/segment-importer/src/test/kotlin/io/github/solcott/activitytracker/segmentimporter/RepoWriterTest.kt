package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** [RepoWriter] (segments/segment-import): the importer's records go out in few commits, paced under a relay's limits. */
class RepoWriterTest {
    private val fake = FakeServices()

    private fun draft(rkey: String, bytes: Int = 10) =
        Draft(Nsid.FEED_SEGMENT, rkey, JsonObject(mapOf("x" to JsonPrimitive("a".repeat(bytes)))))

    @Test
    fun aCommitHoldsAtMostAHundredRecords() {
        val drafts = (1..250).map { draft("r$it") }
        assertEquals(listOf(100, 100, 50), RepoWriter.batches(drafts).map { it.size })
        assertEquals(drafts, RepoWriter.batches(drafts).flatten(), "in order")
    }

    @Test
    fun aCommitHoldsAtMostAMegabyteOfRecordsAndALargerRecordGoesAlone() {
        val drafts = listOf(draft("a", 400_000), draft("b", 400_000), draft("c", 400_000), draft("d", 1_200_000), draft("e"))
        assertEquals(
            listOf(listOf("a", "b"), listOf("c"), listOf("d"), listOf("e")),
            RepoWriter.batches(drafts).map { b -> b.map { it.rkey } },
        )
    }

    @Test
    fun commitsArePacedToTheHourlyRate() = runTest {
        val times = mutableListOf<Long>()
        fake.serveRepo(mutableMapOf()) {
            times += testScheduler.currentTime
            null
        }
        val clients = Clients(fake.http, fake.settings.copy(commitsPerHour = 3_600), testScheduler.timeSource)
        val created = mutableListOf<String>()
        clients.writer.create(Did(FakeServices.DID), (1..250).map { draft("r$it") }) { draft, _ -> created += draft.rkey }
        assertEquals(listOf(0L, 1_000L, 2_000L), times, "one commit a second at 3,600 an hour, the first at once")
        assertEquals(250, created.size)
    }

    @Test
    fun theDefaultRateIsUnderAnUntrustedPdssRelayLimit() {
        assertEquals(RepoWriter.DEFAULT_COMMITS_PER_HOUR, FakeServices().settings.commitsPerHour)
        assertEquals(true, RepoWriter.DEFAULT_COMMITS_PER_HOUR < UNTRUSTED_RELAY_PER_HOUR)
    }

    private companion object {
        /** What an indigo relay lets a PDS it doesn't trust send an hour: 2,500 plus its default 100 accounts. */
        const val UNTRUSTED_RELAY_PER_HOUR = 2_600
    }
}
