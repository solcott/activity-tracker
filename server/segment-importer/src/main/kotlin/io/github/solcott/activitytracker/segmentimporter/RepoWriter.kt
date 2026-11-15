package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.ApplyWrites
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import io.ktor.http.HttpStatusCode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement

/** A record to write to the service account's repo: [value] under [rkey] in [collection]. */
internal data class Draft(val collection: String, val rkey: String, val value: JsonElement)

/**
 * Writes the service account's records (segments/segment-import) in `applyWrites` commits of at most [MAX_WRITES] records and about
 * [MAX_BYTES] of them, at most [commitsPerHour] commits an hour. A relay rate-limits each PDS by commits, not records, and a Jetstream
 * verifies each account's commits through a short queue, so a commit per record makes a region's import crawl through a relay's limits or
 * overflow Jetstream; batched and paced, an import of 150,000 segments is some 1,500 commits, under an untrusted PDS's relay limits.
 */
internal class RepoWriter(
    private val pds: XrpcClient,
    commitsPerHour: Int = DEFAULT_COMMITS_PER_HOUR,
    timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val pacer = Pacer(1.hours / commitsPerHour, timeSource)

    /**
     * Creates [drafts] in [repo], never overwriting a record: a commit refused because it holds a key the repo already has is sent again
     * without those keys. [done] hears each draft once its commit lands, created or already present. Throws the first error that isn't a
     * held key, after reporting what landed before it.
     */
    suspend fun create(repo: Did, drafts: List<Draft>, done: (Draft, created: Boolean) -> Unit) {
        for (batch in batches(drafts)) createBatch(repo, batch, done)
    }

    /** Rewrites [drafts], records [repo] already holds. Throws the first error, keeping the commits before it. */
    suspend fun update(repo: Did, drafts: List<Draft>) {
        for (batch in batches(drafts)) commit(repo, batch.map { ApplyWrites.update(it.collection, it.rkey, it.value) })
    }

    private suspend fun createBatch(repo: Did, batch: List<Draft>, done: (Draft, Boolean) -> Unit) {
        if (batch.isEmpty()) return
        try {
            commit(repo, batch.map { ApplyWrites.create(it.collection, it.rkey, it.value) })
            batch.forEach { done(it, true) }
        } catch (e: XrpcException) {
            // A commit is all or nothing: find the keys another run created and send the rest again. A rate limit is never a held key.
            if (e.status == HttpStatusCode.TooManyRequests.value) throw e
            val held = batch.filter { exists(pds, repo, it.rkey, it.collection) }
            if (held.isEmpty()) throw e
            held.forEach { done(it, false) }
            createBatch(repo, batch - held.toSet(), done)
        }
    }

    private suspend fun commit(repo: Did, writes: List<ApplyWrites.Write>) {
        pacer.await()
        pds.procedure(ApplyWrites.method, ApplyWrites.Input(repo, writes))
    }

    companion object {
        /**
         * Commits an hour unless set (`SEGMENT_IMPORTER_COMMITS_PER_HOUR`): under the 2,600 an hour a relay allows a PDS it doesn't trust
         * (indigo's 2,500 plus its default 100 accounts), so an import never waits on a relay's limits.
         */
        const val DEFAULT_COMMITS_PER_HOUR = 2_400

        /** Records a commit holds at most: half the 200 a firehose commit may, leaving room for the repo's tree nodes. */
        const val MAX_WRITES = 100

        /** Bytes of records, as JSON, a commit holds at most (one larger record goes alone): half a firehose commit's 2 MB of blocks. */
        const val MAX_BYTES = 1_000_000

        /** [drafts] in order, cut into commits of at most [MAX_WRITES] records and [MAX_BYTES] of them. */
        fun batches(drafts: List<Draft>): List<List<Draft>> = buildList {
            val batch = mutableListOf<Draft>()
            var bytes = 0
            for (draft in drafts) {
                val size = draft.value.toString().length
                if (batch.isNotEmpty() && (batch.size == MAX_WRITES || bytes + size > MAX_BYTES)) {
                    add(batch.toList())
                    batch.clear()
                    bytes = 0
                }
                batch += draft
                bytes += size
            }
            if (batch.isNotEmpty()) add(batch.toList())
        }
    }
}

/** Lets one commit through per [interval]: the first at once, each next one [interval] after the one before. */
private class Pacer(private val interval: Duration, private val timeSource: TimeSource) {
    private var last: TimeMark? = null

    suspend fun await() {
        last?.let { delay(interval - it.elapsedNow()) }
        last = timeSource.markNow()
    }
}

/** Whether [repo] already holds a record of [collection] under [rkey]. */
internal suspend fun exists(pds: XrpcClient, repo: Did, rkey: String, collection: String): Boolean =
    try {
        pds.query(GetRecord.method, GetRecord.Params(repo.value, collection, rkey))
        true
    } catch (e: XrpcException) {
        if (e.error != RECORD_NOT_FOUND) throw e
        false
    }
