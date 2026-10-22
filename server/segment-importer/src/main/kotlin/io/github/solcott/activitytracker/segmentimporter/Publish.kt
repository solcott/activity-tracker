@file:Suppress("MatchingDeclarationName") // The publish command; PublishResult is only what it returns.

package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.GetRiddenSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidate
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.atproto.CreateRecord
import io.github.solcott.activitytracker.lexicon.atproto.GetRecord
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

/** What a `publish` run did; [stoppedBy] is the error that ended it early, if one did. */
internal data class PublishResult(
    val published: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val present: List<String> = emptyList(),
    val stoppedBy: Exception? = null,
)

/**
 * Publishes [region]'s ridden candidates (segments/segment-import): each one `feed.checkSegment` finds no live duplicate of becomes a
 * `feed.segment` in the service account's repo, keyed by its id, with its own source, licence and attribution. A key the repo already holds
 * is never written again, so a published segment is never doubled or rewritten. Stops at the first failed call, keeping what it did;
 * running again carries on.
 */
internal suspend fun publish(clients: Clients, region: String, clock: Clock = Clock.System): PublishResult {
    val published = mutableListOf<String>()
    val duplicates = mutableListOf<String>()
    val present = mutableListOf<String>()
    val stoppedBy =
        try {
            val repo = clients.session.did()
            var cursor: String? = null
            do {
                val page = clients.asImporter {
                    clients.appView.query(
                        GetRiddenSegmentCandidates.method,
                        GetRiddenSegmentCandidates.Params(region, SegmentCandidates.MAX_BATCH, cursor),
                    )
                }
                for (candidate in page.candidates) {
                    val check = clients.appView.procedure(CheckSegment.method, CheckSegment.Input(candidate.sport, candidate.line))
                    when {
                        check.duplicates.isNotEmpty() -> duplicates += candidate.id
                        exists(clients, repo, candidate.id) -> present += candidate.id
                        create(clients, repo, candidate, clock) -> published += candidate.id
                        else -> present += candidate.id
                    }
                }
                cursor = page.cursor
            } while (cursor != null)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            e
        }
    return PublishResult(published, duplicates, present, stoppedBy)
}

/** Whether [repo] already holds a segment under [rkey]. */
private suspend fun exists(clients: Clients, repo: Did, rkey: String): Boolean =
    try {
        clients.pds.query(GetRecord.method, GetRecord.Params(repo.value, Nsid.FEED_SEGMENT, rkey))
        true
    } catch (e: XrpcException) {
        if (e.error != RECORD_NOT_FOUND) throw e
        false
    }

/**
 * Creates [candidate]'s segment; false when a concurrent run created it first (`createRecord` refuses a key that exists, and a re-check
 * finds it).
 */
private suspend fun create(clients: Clients, repo: Did, candidate: SegmentCandidate, clock: Clock): Boolean {
    val record =
        SegmentRecord(
            name = candidate.name,
            sport = candidate.sport,
            line = candidate.line,
            source = candidate.source,
            license = candidate.license,
            createdAt = clock.now(),
            attribution = candidate.attribution,
        )
    return try {
        clients.pds.procedure(
            CreateRecord.method,
            CreateRecord.Input(
                repo,
                Nsid.FEED_SEGMENT,
                AtJson.encodeToJsonElement(SegmentRecord.serializer(), record),
                candidate.id,
                false,
            ),
        )
        true
    } catch (e: XrpcException) {
        if (e.status != CLIENT_ERROR || !exists(clients, repo, candidate.id)) throw e
        false
    }
}

/** Publishes [region], printing what happened; throws what stopped it, after the summary. */
internal suspend fun publishRegion(clients: Clients, region: String) {
    val result = publish(clients, region)
    for (id in result.duplicates) println("Duplicate of a live segment, not published: $id")
    println(
        "Published ${result.published.size} segments for $region; ${result.duplicates.size} duplicates, " +
            "${result.present.size} already published"
    )
    result.stoppedBy?.let { throw it }
}

private const val RECORD_NOT_FOUND = "RecordNotFound"
private const val CLIENT_ERROR = 400
