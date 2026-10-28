@file:Suppress("MatchingDeclarationName") // The refine command; RefineResult is only what it returns.

package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
import io.github.solcott.activitytracker.location.SegmentGeometry
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a `refine` run did: segments given their candidate's finer line ([refined]), already holding it ([unchanged]), whose trail moved
 * further than [REFINE_MAX_M] ([moved]), or whose candidate isn't in the file ([missing]); [stoppedBy] is the error that ended the run
 * early, if one did.
 */
internal data class RefineResult(
    val refined: List<String> = emptyList(),
    val unchanged: List<String> = emptyList(),
    val moved: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val stoppedBy: Exception? = null,
)

/** How far apart a published line and its candidate's may be and still be the same path drawn finer: the old 5 m tolerance plus 1 m. */
internal const val REFINE_MAX_M = 6.0

/** The spacing lines are compared at, in meters. */
private const val COMPARE_EVERY_M = 2.0

/** A page of the repo's segments; listRecords allows up to 100. */
private const val PAGE = 100

/**
 * Gives the service account's published segments [file]'s finer lines (segments/segment-import): each `feed.segment` in its repo whose rkey
 * is a candidate id in [file] gets the candidate's line, every other field kept as stored, when the lines differ and lie within
 * [REFINE_MAX_M] of each other everywhere ([SegmentGeometry.hausdorffMeters]). A trail that moved further keeps its line, as it does for
 * `publish`. Nothing is deleted, and only the account's own repo is read or written. Stops at the first failed call, keeping what it did;
 * running again carries on.
 */
internal suspend fun refine(clients: Clients, file: CandidatesFile): RefineResult {
    val candidates = file.candidates.associateBy { it.id }
    val refined = mutableListOf<String>()
    val unchanged = mutableListOf<String>()
    val moved = mutableListOf<String>()
    val missing = mutableListOf<String>()
    val stoppedBy =
        try {
            val repo = clients.session.did()
            var cursor: String? = null
            do {
                val page = clients.pds.query(ListRecords.method, ListRecords.Params(repo.value, Nsid.FEED_SEGMENT, PAGE, cursor))
                for (record in page.records) {
                    val rkey = record.uri.rkey ?: continue
                    val candidate = candidates[rkey]
                    val stored = record.value.lineOrNull()
                    when {
                        candidate == null -> missing += rkey
                        stored == candidate.line -> unchanged += rkey
                        stored == null || !samePath(stored, candidate.line) -> moved += rkey
                        else -> {
                            val rewritten = JsonObject(record.value + ("line" to JsonPrimitive(candidate.line)))
                            clients.pds.procedure(PutRecord.method, PutRecord.Input(repo, Nsid.FEED_SEGMENT, rkey, rewritten))
                            refined += rkey
                        }
                    }
                }
                cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
            } while (cursor != null)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            e
        }
    return RefineResult(refined, unchanged, moved, missing, stoppedBy)
}

/** Whether two encoded segment lines are the same path: within [REFINE_MAX_M] of each other all along. */
internal fun samePath(a: String, b: String): Boolean {
    val first = SegmentGeometry.decodePolyline(a)
    val second = SegmentGeometry.decodePolyline(b)
    return first != null && second != null && SegmentGeometry.hausdorffMeters(first, second, COMPARE_EVERY_M) <= REFINE_MAX_M
}

private fun JsonObject.lineOrNull(): String? = (this["line"] as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.contentOrNull

/** Reads [path] and refines the published segments it names, printing what happened; throws what stopped it, after the summary. */
internal suspend fun refineFile(clients: Clients, path: File) {
    val file = path.bufferedReader().useLines { readCandidates(it) }
    val result = refine(clients, file)
    for (id in result.moved) println("Trail moved, line kept: $id")
    println(
        "Refined ${result.refined.size} segments from ${file.region}'s candidates; ${result.unchanged.size} unchanged, " +
            "${result.moved.size} moved, ${result.missing.size} not in this file"
    )
    result.stoppedBy?.let { throw it }
}
