package io.github.solcott.activitytracker.data.activity

import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.UploadBatchEntity
import io.github.solcott.activitytracker.data.db.UploadItemEntity
import io.github.solcott.activitytracker.data.db.UploadItemRow
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The upload queue's log (data/upload-queue): an item per activity saved or edited, beside the outbox writes it stands for. Callers log an
 * activity next to its first outbox write; [OutboxProcessor] counts each write sent; state is derived from what the outbox still holds
 * ([UploadItemRow.state]).
 */
internal object UploadLog {
    /** How long finished items stay listed. */
    val RETENTION = 7.days

    /** The `key_value` entry that holds whether [did] paused imports ([AccountPacing.importsPaused]); present while paused. */
    fun importsPausedKey(did: String) = "uploads.importsPaused.$did"

    /**
     * Logs [record] at [rkey] as a new activity or an edit ([kind]), of the import [batchId] if any. An activity already live (waiting or
     * uploading) keeps its one item, under [record]'s title and type.
     */
    @Suppress("LongParameterList") // The item's fields.
    suspend fun queued(db: AppDatabase, did: String, rkey: String, kind: String, record: ActivityRecord, batchId: String?, now: Instant) {
        db.uploads()
            .queued(
                UploadItemEntity(
                    did = did,
                    rkey = rkey,
                    batchId = batchId,
                    kind = kind,
                    title = record.title,
                    sport = record.sport,
                    subSport = record.subSport,
                    startedAt = record.startedAt.toEpochMilliseconds(),
                    queuedAt = now.toEpochMilliseconds(),
                    recordJson =
                        if (kind == UploadItemEntity.KIND_NEW) AtJson.encodeToString(ActivityRecord.serializer(), record) else null,
                ),
            )
    }

    /**
     * Starts the import [batchId] from [source], saved at [now], of [total] selected activities; its counts come with [counted]. Starting
     * it again (saving again after a failure) keeps when it was first saved and its counts.
     */
    @Suppress("LongParameterList") // The batch's fields.
    suspend fun batch(db: AppDatabase, did: String, batchId: String, source: String, total: Int, now: Instant) {
        db.uploads().insertBatchIfAbsent(UploadBatchEntity(id = batchId, did = did, source = source, savedAt = now.toEpochMilliseconds()))
        db.uploads().setBatchTotal(batchId, total)
    }

    /** The import [batchId] saved [saved] activities and skipped [skipped]. */
    suspend fun counted(db: AppDatabase, batchId: String, saved: Int, skipped: Int) {
        db.uploads().countBatch(batchId, saved, skipped)
    }

    /** Logs a file of the import [batchId] that couldn't be read again at save: finished as it's logged, so it ages out with the rest. */
    @Suppress("LongParameterList") // The item's fields.
    suspend fun skipped(db: AppDatabase, did: String, batchId: String, title: String?, sport: String?, startedAt: Instant?, now: Instant) {
        val at = now.toEpochMilliseconds()
        db.uploads()
            .insert(
                UploadItemEntity(
                    did = did,
                    rkey = null,
                    batchId = batchId,
                    kind = UploadItemEntity.KIND_NEW,
                    title = title,
                    sport = sport,
                    subSport = null,
                    startedAt = startedAt?.toEpochMilliseconds(),
                    queuedAt = at,
                    finishedAt = at,
                    skipped = true,
                ),
            )
    }

    /** Removes [rkey]'s live item: the activity was deleted, and its delete counts with other changes. */
    suspend fun removed(db: AppDatabase, did: String, rkey: String) {
        db.uploads().deleteLive(did, rkey)
    }

    /** Drops items finished more than [RETENTION] before [now], and imports left with none. */
    suspend fun prune(db: AppDatabase, did: String, now: Instant) {
        db.uploads().prune(did, now.toEpochMilliseconds(), (now - RETENTION).toEpochMilliseconds())
    }

    /** Drops every finished item, and imports left with none: the queue's Clear. */
    suspend fun clearFinished(db: AppDatabase, did: String, now: Instant) {
        db.uploads().prune(did, now.toEpochMilliseconds(), Long.MAX_VALUE)
    }
}

/** An upload queue item's state (data/upload-queue). */
enum class UploadState {
    /** None of its writes is sent. */
    Waiting,

    /** Some are sent, or one is being sent. */
    Uploading,

    /** All are sent. */
    Uploaded,

    /** One was refused by the server. */
    Refused,

    /** An imported file that couldn't be read again at save. */
    Skipped,
}

/** This item's state, [sending] being the rkey whose write is being sent now ([AccountPacing.sending]). */
fun UploadItemRow.state(sending: String?): UploadState =
    when {
        skipped -> UploadState.Skipped
        finishedAt != null -> UploadState.Uploaded
        rejected > 0 -> UploadState.Refused
        sentWrites > 0 || (rkey != null && rkey == sending) -> UploadState.Uploading
        else -> UploadState.Waiting
    }

/** Writes sent of all this item's writes sent or still queued: a later edit's writes extend the total. */
val UploadItemRow.totalWrites: Int
    get() = sentWrites + remaining
