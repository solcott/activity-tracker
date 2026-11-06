@file:Suppress("MatchingDeclarationName") // The handoff command; HandoffResult is only what it returns.

package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.DiveSiteRecord
import io.github.solcott.activitytracker.lexicon.app.GetHandoffs
import io.github.solcott.activitytracker.lexicon.app.SegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SegmentRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentSource
import io.github.solcott.activitytracker.lexicon.app.SubjectHandoff
import io.github.solcott.activitytracker.lexicon.atproto.CreateRecord
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.serialization.json.JsonElement

/** The licence a handed-off athlete segment or site was published under, and is republished under. */
private const val HANDOFF_LICENSE = "CC0-1.0"

/** What a `handoff` run did; [stoppedBy] is the error that ended the run early, if one did. */
internal data class HandoffResult(
    val published: List<String> = emptyList(),
    val present: List<String> = emptyList(),
    val skipped: List<String> = emptyList(),
    val stoppedBy: Exception? = null,
)

/**
 * Republishes every pending handoff `importer.getHandoffs` lists (segments/segment-handoffs, dive-sites/site-handoffs): a segment as a
 * `feed.segment`, a site as a `feed.diveSite`, in the service account's repo under the handoff's id, with `source` `athlete`, `CC0-1.0` and
 * no attribution. The AppView lists only handoffs no live subject covers, so there is no duplicate check here. A key the repo already holds
 * is never written again. An entry of an unknown kind, or missing what its kind needs, is skipped. Stops at the first failed call, keeping
 * what it did; running again carries on.
 */
internal suspend fun handoff(clients: Clients, clock: Clock = Clock.System): HandoffResult {
    val published = mutableListOf<String>()
    val present = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    val stoppedBy =
        try {
            val repo = clients.session.did()
            var cursor: String? = null
            do {
                val page = clients.asImporter {
                    clients.appView.query(GetHandoffs.method, GetHandoffs.Params(SegmentCandidates.MAX_BATCH, cursor))
                }
                for (handoff in page.handoffs) {
                    val (collection, record) =
                        recordOf(handoff, clock)
                            ?: run {
                                skipped += handoff.id
                                null
                            }
                            ?: continue
                    when {
                        exists(clients, repo, handoff.id, collection) -> present += handoff.id
                        createRecord(clients, repo, collection, handoff.id, record) -> published += handoff.id
                        else -> present += handoff.id
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
    return HandoffResult(published, present, skipped, stoppedBy)
}

/** [handoff]'s collection and record, or null when it is of an unknown kind or lacks what its kind needs. */
private fun recordOf(handoff: SubjectHandoff, clock: Clock): Pair<String, JsonElement>? =
    when (handoff.kind) {
        SubjectHandoff.SEGMENT -> {
            val sport = handoff.sport
            val line = handoff.line
            if (sport == null || line == null) null
            else
                Nsid.FEED_SEGMENT to
                    AtJson.encodeToJsonElement(
                        SegmentRecord.serializer(),
                        SegmentRecord(
                            name = handoff.name,
                            sport = sport,
                            line = line,
                            source = SegmentSource.ATHLETE,
                            license = HANDOFF_LICENSE,
                            createdAt = clock.now(),
                            kind = handoff.segmentKind,
                        ),
                    )
        }
        SubjectHandoff.SITE -> {
            val lat = handoff.lat
            val lng = handoff.lng
            val radius = handoff.radius
            if (lat == null || lng == null || radius == null) null
            else
                Nsid.FEED_DIVE_SITE to
                    AtJson.encodeToJsonElement(
                        DiveSiteRecord.serializer(),
                        DiveSiteRecord(handoff.name, lat, lng, radius, SegmentSource.ATHLETE, HANDOFF_LICENSE, clock.now()),
                    )
        }
        else -> null
    }

/** Creates [record] under [rkey]; false when a concurrent run created it first (`createRecord` refuses a key that exists). */
private suspend fun createRecord(clients: Clients, repo: Did, collection: String, rkey: String, record: JsonElement): Boolean =
    try {
        clients.pds.procedure(CreateRecord.method, CreateRecord.Input(repo, collection, record, rkey, false))
        true
    } catch (e: XrpcException) {
        if (e.status != CLIENT_ERROR || !exists(clients, repo, rkey, collection)) throw e
        false
    }

/** Republishes the pending handoffs, printing what happened; throws what stopped it, after the summary. */
internal suspend fun handoffAll(clients: Clients) {
    val result = handoff(clients)
    for (id in result.skipped) println("Not a handoff this importer can publish, skipped: $id")
    println("Republished ${result.published.size} handed-off segments and sites; ${result.present.size} already published")
    result.stoppedBy?.let { throw it }
}
