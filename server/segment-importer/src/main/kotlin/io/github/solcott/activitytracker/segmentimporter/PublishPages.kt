package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.lexicon.AtUri
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * What a `publish` or `publish-sites` run did; [gone] were listed but no longer staged when checked (a `stage` finished meanwhile; sites
 * are never staged, so never gone), and [stoppedBy] is the error that ended the run early, if one did.
 */
internal data class PublishResult(
    val published: List<String> = emptyList(),
    val duplicates: List<String> = emptyList(),
    val present: List<String> = emptyList(),
    val gone: List<String> = emptyList(),
    val stoppedBy: Exception? = null,
)

/**
 * A listed item [id] as its page's check found it: [staged] is what's published, null when no longer staged, and [duplicates] the live
 * records it duplicates.
 */
internal class Checked<T>(val id: String, val staged: T?, val duplicates: List<AtUri>)

/**
 * Publishes [pages] as [collection] records in the service account's repo, keyed by id: [check] checks a whole page in one call, giving
 * each listed item as [Checked], and each one that's still staged and duplicates nothing live becomes [record] of it. The importer's own
 * live record is the duplicate the check finds for an item, which counts as already published. Each page's records go out through
 * [Clients.writer], batched and paced; a key the repo already holds is never written again. Stops at the first failed call, keeping what it
 * did; running again carries on.
 */
internal suspend fun <T> publishPages(
    clients: Clients,
    collection: String,
    pages: Flow<List<T>>,
    check: suspend (List<T>) -> List<Checked<T>>,
    record: (T) -> JsonElement,
): PublishResult {
    val published = mutableListOf<String>()
    val duplicates = mutableListOf<String>()
    val present = mutableListOf<String>()
    val gone = mutableListOf<String>()
    val stoppedBy = stopOnError {
        val repo = clients.session.did()
        pages.collect { page ->
            val drafts = mutableListOf<Draft>()
            for (checked in check(page)) {
                val staged = checked.staged
                when {
                    staged == null -> gone += checked.id
                    AtUri.of(repo, collection, checked.id) in checked.duplicates -> present += checked.id
                    checked.duplicates.isNotEmpty() -> duplicates += checked.id
                    else -> drafts += Draft(collection, checked.id, record(staged))
                }
            }
            clients.writer.create(repo, drafts) { draft, created -> if (created) published += draft.rkey else present += draft.rkey }
        }
    }
    return PublishResult(published, duplicates, present, gone, stoppedBy)
}

/**
 * Prints what a run publishing [region]'s [what] did, naming each duplicate of a live [liveWhat], with how many were no longer staged when
 * [countGone]; then throws what stopped it, if anything did.
 */
internal fun PublishResult.report(region: String, what: String, liveWhat: String, countGone: Boolean) {
    for (id in duplicates) println("Duplicate of a live $liveWhat, not published: $id")
    val goneCount = if (countGone) ", ${gone.size} no longer staged" else ""
    println(
        "Published ${published.size} $what for $region; ${duplicates.size} duplicates, ${present.size} already published$goneCount",
    )
    stoppedBy?.let { throw it }
}
