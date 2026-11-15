@file:Suppress("MatchingDeclarationName") // The publish command; PublishResult is only what it returns.

package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.GetRiddenSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidateCheck
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement

/**
 * What a `publish` run did; [gone] were listed but no longer staged when checked (a `stage` finished meanwhile), and [stoppedBy] is the
 * error that ended the run early, if one did.
 */
internal data class PublishResult(
    val published: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val present: List<String> = emptyList(),
    val gone: List<String> = emptyList(),
    val stoppedBy: Exception? = null,
)

/**
 * Publishes [region]'s ridden candidates (segments/segment-import), or, given [every], each of those candidates whether ridden or not (an
 * operator's `publish --all`, with the region's staged candidates file): each one `importer.checkSegmentCandidates` finds no live duplicate
 * of becomes a `feed.segment` in the service account's repo, keyed by its id, with its own source, licence and attribution. Each page is
 * checked in one call, so a run's AppView calls grow with its pages, not with the duplicates it lists again every run; the check also
 * leaves out a candidate no longer staged, which is reported [PublishResult.gone], and gives each one as staged, which is what's published
 * (a stale [every] file's copy never is). Each page's segments go out through [Clients.writer], batched and paced; a key the repo already
 * holds is never written again, so a published segment is never doubled or rewritten. Stops at the first failed call, keeping what it did;
 * running again carries on.
 */
internal suspend fun publish(
    clients: Clients,
    region: String,
    clock: Clock = Clock.System,
    every: List<SegmentCandidate>? = null,
): PublishResult {
    val published = mutableListOf<String>()
    val duplicates = mutableListOf<String>()
    val present = mutableListOf<String>()
    val gone = mutableListOf<String>()
    val stoppedBy = stopOnError {
        val repo = clients.session.did()
        val pages = if (every != null) every.chunked(SegmentCandidates.MAX_BATCH).asFlow() else riddenPages(clients, region)
        pages.collect { page ->
            val checks = check(clients, region, page)
            val drafts = mutableListOf<Draft>()
            for (listed in page) {
                val check = checks[listed.id]
                // What's published is the candidate as staged, never a copy that may be older (a stale --all file).
                val candidate = check?.candidate ?: listed
                when {
                    check == null -> gone += listed.id
                    // The importer's own segment for this candidate is live, so the check lists it: already published.
                    AtUri.of(repo, Nsid.FEED_SEGMENT, listed.id) in check.duplicates -> present += listed.id
                    check.duplicates.isNotEmpty() -> duplicates += listed.id
                    else -> drafts += Draft(Nsid.FEED_SEGMENT, listed.id, segmentRecord(candidate, clock))
                }
            }
            clients.writer.create(repo, drafts) { draft, created -> if (created) published += draft.rkey else present += draft.rkey }
        }
    }
    return PublishResult(published, duplicates, present, gone, stoppedBy)
}

/** [region]'s ridden candidates the importer hasn't published yet, a page at a time. */
private fun riddenPages(clients: Clients, region: String): Flow<List<SegmentCandidate>> = flow {
    var cursor: String? = null
    do {
        val page = clients.asImporter {
            clients.appView.query(
                GetRiddenSegmentCandidates.method,
                GetRiddenSegmentCandidates.Params(region, SegmentCandidates.MAX_BATCH, cursor),
            )
        }
        emit(page.candidates)
        cursor = page.cursor
    } while (cursor != null)
}

/** Each of [page]'s candidates still staged in [region], as staged, with the live segments it duplicates; one call for the whole page. */
private suspend fun check(clients: Clients, region: String, page: List<SegmentCandidate>): Map<String, SegmentCandidateCheck> =
    if (page.isEmpty()) {
        emptyMap()
    } else {
        clients
            .asImporter {
                clients.appView.procedure(CheckSegmentCandidates.method, CheckSegmentCandidates.Input(region, page.map { it.id }))
            }
            .candidates
            .associateBy { it.id }
    }

/** [candidate] as a `feed.segment` record created now, with its own source, licence and attribution. */
private fun segmentRecord(candidate: SegmentCandidate, clock: Clock): JsonElement =
    AtJson.encodeToJsonElement(
        SegmentRecord.serializer(),
        SegmentRecord(
            name = candidate.name,
            sport = candidate.sport,
            line = candidate.line,
            source = candidate.source,
            license = candidate.license,
            createdAt = clock.now(),
            attribution = candidate.attribution,
            kind = candidate.kind,
        ),
    )

/**
 * Runs [block], a command's whole run, and returns the error that stopped it, or null when it finished; cancellation still propagates. A
 * command keeps what it did before the error and reports it with what stopped it, and running it again carries on.
 */
internal suspend fun stopOnError(block: suspend () -> Unit): Exception? =
    try {
        block()
        null
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        e
    }

/**
 * Publishes [region], printing what happened; throws what stopped it, after the summary. With [every], a candidates file of [region], it
 * publishes each of the file's candidates whether ridden or not; a file of another region is refused before anything is called.
 */
internal suspend fun publishRegion(clients: Clients, region: String, every: File? = null) {
    val candidates = every?.let { path ->
        val file = path.bufferedReader().useLines { readCandidates(it) }
        require(file.region == region) { "it holds ${file.region}'s candidates, not $region's" }
        file.candidates
    }
    val result = publish(clients, region, every = candidates)
    for (id in result.duplicates) println("Duplicate of a live segment, not published: $id")
    println(
        "Published ${result.published.size} segments for $region; ${result.duplicates.size} duplicates, " +
            "${result.present.size} already published, ${result.gone.size} no longer staged",
    )
    result.stoppedBy?.let { throw it }
}

internal const val RECORD_NOT_FOUND = "RecordNotFound"
