package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.data.db.OutboxEntity
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord

/** One of the signed-in athlete's activities, as an import compares against it. [cid] is null while it waits in the outbox. */
data class OwnActivity(val uri: AtUri, val cid: Cid?, val record: ActivityRecord)

/** The activity this outbox write publishes for [did], or null when it's another kind of write. */
internal fun OutboxEntity.pendingActivity(did: Did): OwnActivity? {
    val json = recordJson?.takeIf { collection == Nsid.FEED_ACTIVITY && operation == OutboxProcessor.OP_PUT }
    val record = json?.let { runCatching { AtJson.decodeFromString(ActivityRecord.serializer(), it) }.getOrNull() } ?: return null
    return OwnActivity(AtUri.of(did, Nsid.FEED_ACTIVITY, rkey), cid = null, record)
}
