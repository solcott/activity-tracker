package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.ActivityDetails
import io.github.solcott.activitytracker.appview.db.ActivityGear
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.activityDetails` records, which the Indexer hands off here.

/**
 * Details must sit under their own activity's rkey in the same repo, and in a tier space: they're only ever narrower than their activity,
 * so a repo copy is skipped (and logged, like any invalid record). Each tier's copy is its own row, served at [placement]'s visibility.
 */
@Suppress("LongParameterList") // The record, and where it's from.
internal fun JdbcTransaction.upsertDetails(
    record: ActivityDetailsRecord,
    author: Did,
    uri: AtUri,
    cid: String,
    placement: Placement,
    now: Long,
) {
    require(placement.origin != Origins.REPO) { "details in a repo" }
    val activity = record.activity
    require(activity.authority == author.value && activity.collection == Nsid.FEED_ACTIVITY && activity.rkey == uri.rkey) {
        "details not under their activity's rkey"
    }
    // Re-encoded through the shared model, so only the fields it knows are stored; a polyline over the lexicon's limit is dropped.
    val bounded = record.copy(polyline = record.polyline?.takeIf { it.length <= MAX_POLYLINE_LENGTH })
    ActivityDetails.upsert {
        it[ActivityDetails.uri] = uri.value
        it[origin] = placement.origin
        it[ActivityDetails.cid] = cid
        it[authorDid] = author.value
        it[activityUri] = activity.value
        it[visibility] = placement.visibility
        it[ActivityDetails.record] = AtJson.encodeToString(ActivityDetailsRecord.serializer(), bounded)
        it[indexedAt] = now
    }
    replaceActivityGear(activity, author, placement, ActivityGear.SOURCE_DETAILS, record.gear)
}

// The lexicon's maxLength for polyline.
private const val MAX_POLYLINE_LENGTH = 50_000
