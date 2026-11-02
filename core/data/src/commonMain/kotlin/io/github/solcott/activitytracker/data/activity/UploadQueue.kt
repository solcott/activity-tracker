package io.github.solcott.activitytracker.data.activity

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.RateLimit
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.BulkLeft
import io.github.solcott.activitytracker.data.db.ImportSaveCounts
import io.github.solcott.activitytracker.data.db.ImportSaveEntity
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.data.db.UploadBatchEntity
import io.github.solcott.activitytracker.data.db.UploadItemEntity
import io.github.solcott.activitytracker.data.db.UploadItemRow
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.importsave.ImportSaves
import io.github.solcott.activitytracker.data.toDataError
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.atproto.UploadBlob
import io.github.solcott.dataresult.Origin
import io.github.solcott.dataresult.Outcome
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/** One activity saved or edited, in the upload queue (data/upload-queue). */
@Immutable
data class UploadItem(
    val id: Long,
    /** The activity, or null for a file skipped at save. */
    val uri: AtUri?,
    /** The import it belongs to, or null for the athlete's own. */
    val batchId: String?,
    /** An edit of an activity uploaded before, rather than a new one. */
    val isEdit: Boolean,
    val state: UploadState,
    val title: String?,
    val sport: String?,
    val subSport: String?,
    val startedAt: Instant?,
    /** Writes sent, of [total]. */
    val sent: Int,
    val total: Int,
    val finishedAt: Instant?,
) {
    /** Waiting and refused items can be cancelled; once a write of it is sent or sending, an activity can't (data/upload-queue). */
    val cancellable: Boolean
        get() = uri != null && (state == UploadState.Waiting || state == UploadState.Refused)
}

/** What an import was saved from (feed/batch-import): the account exports recognized, or else the files picked. */
@Immutable
sealed interface UploadSource {
    /** Account exports, by kind (`Strava`, `Garmin`), in the order first read. */
    data class Export(val kinds: List<String>) : UploadSource

    /** Files with no export recognized: the [first] one's name, of [count]. */
    data class Files(val first: String, val count: Int) : UploadSource

    /** How it's stored ([UploadBatchEntity.source]). */
    fun encode(): String =
        when (this) {
            is Export -> "$EXPORT${kinds.joinToString(",")}"
            is Files -> "$FILES$count:$first"
        }

    companion object {
        private const val EXPORT = "export:"
        private const val FILES = "files:"

        /** [encode]'s value read back; anything else reads as one file of that name. */
        fun decode(value: String): UploadSource =
            when {
                value.startsWith(EXPORT) -> Export(value.removePrefix(EXPORT).split(',').filter { it.isNotEmpty() })
                value.startsWith(FILES) -> {
                    val rest = value.removePrefix(FILES)
                    val count = rest.substringBefore(':').toIntOrNull()
                    if (count == null) Files(rest, 1) else Files(rest.substringAfter(':'), count)
                }
                else -> Files(value, 1)
            }
    }
}

/**
 * One saved import and its activities: [live] ones in the order they'll be sent, then [finished] ones, newest first. [saved] counts its
 * activities logged so far while the save runs, or for good when the save stopped short.
 */
@Immutable
data class UploadImport(
    val id: String,
    val source: UploadSource,
    val savedAt: Instant,
    val saved: Int,
    val skipped: Int,
    val uploaded: Int,
    val refused: Int,
    val live: List<UploadItem>,
    val finished: List<UploadItem>,
    /** Whether its save is still running or stopped short; null once every selected activity is saved or skipped, or it's cancelled. */
    val save: ImportSaveStatus? = null,
)

/** How far an import's save got (data/upload-queue), beside its uploads. */
@Immutable
sealed interface ImportSaveStatus {
    /** Still saving: [done] activities saved or skipped, of [total] selected. */
    data class Saving(val done: Int, val total: Int) : ImportSaveStatus

    /** Stopped after [saved] of [total] and can't resume: the athlete picks the files again to import the rest. */
    data class Stopped(val saved: Int, val total: Int) : ImportSaveStatus
}

/**
 * The upload queue as listed (data/upload-queue): the item uploading now, the athlete's own live items in the order they'll be sent, each
 * import's group, the athlete's own finished items newest first, and refused changes that aren't activities logged (likes, comments…).
 */
@Immutable
data class UploadQueueView(
    val current: UploadItem?,
    val own: List<UploadItem>,
    val imports: List<UploadImport>,
    val finished: List<UploadItem>,
    val refusedOther: List<RejectedChange>,
) {
    val isEmpty: Boolean
        get() = current == null && own.isEmpty() && imports.isEmpty() && finished.isEmpty() && refusedOther.isEmpty()

    /** Whether anything is finished, for Clear. */
    val hasFinished: Boolean
        get() = finished.isNotEmpty() || imports.any { it.finished.isNotEmpty() }
}

/** Across the imports still uploading: [uploaded] of the [saved] activities, for Android's progress notification (data/upload-queue). */
@Immutable data class ImportProgress(val uploaded: Int, val saved: Int)

/**
 * The feed's upload row (data/outbox): activities uploading, the athlete's [own] apart from [imported] ones, [other] changes uploading,
 * [refused] changes, and both pauses.
 */
@Immutable
data class UploadSummary(
    val own: Int = 0,
    val imported: Int = 0,
    val other: Int = 0,
    val refused: Int = 0,
    val importsPaused: Boolean = false,
    /** Until when the server paused the upload, while anything is uploading. */
    val serverPausedUntil: Instant? = null,
) {
    val uploading: Boolean
        get() = own + imported + other > 0
}

/**
 * The athlete's view of their uploads (data/upload-queue): the queue derived from the upload log and the outbox, the feed's summary, the
 * time left for imports, cancelling what hasn't started, pausing imports, and keeping finished items for a week.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("TooManyFunctions") // One method per thing the athlete does with the queue.
class UploadQueue(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val rejected: RejectedChanges,
    private val clock: Clock,
    private val saves: ImportSaves,
) {
    private val did: String
        get() = clients.did.value

    private val pacing: AccountPacing
        get() = clients.pacing

    val queue: Flow<UploadQueueView> =
        combine(db.uploads().items(did), db.uploads().batches(did), db.importSaves().counts(did), rejected.changes, pacing.sending) {
            items,
            batches,
            plans,
            refused,
            sending ->
            view(items, batches, plans.associateBy { it.batchId }, refused, sending)
        }

    /** [queue] for a screen's content: loading, then each view read from the device, or why it couldn't be read. */
    val queueOutcomes: Flow<Outcome<UploadQueueView>> =
        queue
            .map<UploadQueueView, Outcome<UploadQueueView>> { Outcome.Data(it, Origin.Cache) }
            .onStart { emit(Outcome.Loading) }
            .catch { emit(Outcome.Error(it.toDataError(), Origin.Cache)) }

    val summary: Flow<UploadSummary> =
        combine(
                db.uploads().items(did),
                db.uploads().otherChanges(did),
                rejected.changes.map { changes -> changes.distinctBy { it.rkey }.size },
                pacing.importsPaused,
                pacing.shownPause,
            ) { items, other, refused, paused, pause ->
                val uploading = items.filter { it.finishedAt == null && !it.skipped && it.rejected == 0 }
                val own = uploading.count { it.batchId == null }
                val imported = uploading.size - own
                UploadSummary(
                    own = own,
                    imported = imported,
                    other = other,
                    refused = refused,
                    importsPaused = paused == true,
                    serverPausedUntil = pause?.takeIf { own + imported + other > 0 },
                )
            }
            .distinctUntilChanged()

    /**
     * About how long the imports' writes left take (data/upload-queue), from the limits the PDS stated and the pace of the last
     * [AccountPacing.PACE_WINDOW]; null with neither, with nothing left, or while the athlete paused imports.
     */
    val estimate: Flow<Duration?> =
        combine(
            db.uploads().bulkLeft(did, BLOB_COLLECTIONS),
            pacing.rateLimits,
            pacing.bulkSent,
            pacing.importsPaused,
        ) { left, limits, sent, paused ->
            if (paused == true) null else timeLeft(left, limits.values, sent, clock.now())
        }

    /** [ImportProgress] while an import has activities to upload and the athlete hasn't paused imports; null otherwise. */
    val importProgress: Flow<ImportProgress?> =
        combine(queue, pacing.importsPaused) { view, paused ->
                val uploading = view.imports.filter { group -> group.live.isNotEmpty() || view.current?.batchId == group.id }
                if (paused == true || uploading.isEmpty()) null
                else ImportProgress(uploaded = uploading.sumOf { it.uploaded }, saved = uploading.sumOf { it.saved })
            }
            .distinctUntilChanged()

    /** Whether an import has activities waiting and the athlete hasn't paused imports: closing the app on desktop or web asks first. */
    val importsActive: Flow<Boolean> =
        combine(db.uploads().liveImported(did), pacing.importsPaused) { live, paused -> live > 0 && paused != true }.distinctUntilChanged()

    /** Loads whether the athlete paused imports, kept on the device across sessions, when this account's pacing doesn't know yet. */
    suspend fun loadPause() {
        if (pacing.importsPaused.value == null) pacing.pauseImports(db.keyValue().get(UploadLog.importsPausedKey(did)) != null)
    }

    /**
     * Holds back every import's writes until [resumeImports], the athlete's own still going (data/upload-queue). Once asked, it finishes
     * even if the screen that asked closes first, as do resuming, cancelling and clearing.
     */
    suspend fun pauseImports() {
        withContext(NonCancellable) {
            pacing.pauseImports(true)
            db.keyValue().put(KeyValueEntity(UploadLog.importsPausedKey(did), "true"))
        }
    }

    /** Sends imports again, once any pause the server asked for ends; the pacer wakes at once. */
    suspend fun resumeImports() {
        withContext(NonCancellable) {
            pacing.pauseImports(false)
            db.keyValue().delete(UploadLog.importsPausedKey(did))
        }
    }

    /**
     * Cancels the activity at [rkey] (data/upload-queue): drops every queued write of it, none of them sent, and its item. A new activity
     * leaves the athlete's lists; a cancelled edit leaves the activity as last uploaded. Refused, and returns false, once a write of it is
     * sent or being sent, unless it's refused by the server, which holds back the rest.
     */
    suspend fun cancel(rkey: String): Boolean =
        withContext(NonCancellable) {
            rejected.discard(rkey) {
                val started = (db.uploads().live(did, rkey)?.sentWrites ?: 0) > 0
                started && db.outbox().pending(did, rkey).none { it.rejectedAt != null }
            }
        }

    /**
     * Cancels the rest of the import [batchId] (data/upload-queue): its activities not yet saved, which a running save stops before, and
     * each waiting and refused activity, keeping those started; returns how many activities it cancelled from the queue. A cancelled import
     * never reads as stopped.
     */
    suspend fun cancelRest(batchId: String): Int =
        withContext(NonCancellable) {
            if (db.importSaves().save(batchId) != null) {
                db.importSaves().setState(batchId, ImportSaveEntity.STATE_CANCELLED)
                // A runner on it drops the save itself between activities; otherwise nothing will, so it ends now.
                if (batchId !in saves.running.value) saves.end(batchId)
            }
            db.uploads().setBatchTotal(batchId, 0)
            db.uploads().liveOfBatch(did, batchId).mapNotNull { it.rkey }.count { cancel(it) }
        }

    /** Sends the refused change at [rkey] again. */
    suspend fun retry(rkey: String) {
        rejected.retry(rkey)
    }

    /** Drops items finished a week ago, and imports left with none: at session start and when the queue opens. */
    suspend fun prune() {
        UploadLog.prune(db, did, clock.now())
    }

    /** Drops every finished item now, and imports left with none. */
    suspend fun clearFinished() {
        withContext(NonCancellable) { UploadLog.clearFinished(db, did, clock.now()) }
    }

    private fun view(
        items: List<UploadItemRow>,
        batches: List<UploadBatchEntity>,
        plans: Map<String, ImportSaveCounts>,
        refused: List<RejectedChange>,
        sending: String?,
    ) = run {
        val shown = items.map { it.toItem(sending) }
        val byId = items.zip(shown).toMap()
        val live = items.filter { it.finishedAt == null && !it.skipped }.sortedBy { it.firstWrite ?: Long.MAX_VALUE }
        val current =
            live.firstOrNull { it.rkey != null && it.rkey == sending }
                ?: live.firstOrNull { byId.getValue(it).state == UploadState.Uploading }
        fun finishedOf(rows: List<UploadItemRow>) =
            rows.filter { it.finishedAt != null }.sortedByDescending { it.finishedAt }.map(byId::getValue)
        val logged = live.mapNotNullTo(mutableSetOf()) { it.rkey }
        UploadQueueView(
            current = current?.let(byId::getValue),
            own = live.filter { it.batchId == null && it != current }.map(byId::getValue),
            imports =
                batches.map { batch ->
                    val rows = items.filter { it.batchId == batch.id }
                    val shownRows = rows.map(byId::getValue)
                    UploadImport(
                        id = batch.id,
                        source = UploadSource.decode(batch.source),
                        savedAt = Instant.fromEpochMilliseconds(batch.savedAt),
                        saved = maxOf(batch.saved, rows.count { !it.skipped }),
                        skipped = batch.skipped,
                        uploaded = shownRows.count { it.state == UploadState.Uploaded },
                        refused = shownRows.count { it.state == UploadState.Refused },
                        live = live.filter { it.batchId == batch.id && it != current }.map(byId::getValue),
                        finished = finishedOf(rows),
                        save = saveStatus(batch, plans[batch.id]),
                    )
                },
            finished = finishedOf(items.filter { it.batchId == null }),
            refusedOther = refused.distinctBy { it.rkey }.filter { it.rkey !in logged },
        )
    }

    /**
     * [batch]'s save: saving while its [plan] runs, stopped when the plan stopped, or when no plan remains and it saved and skipped fewer
     * than it selected (a total of 0, from before imports were counted, never is).
     */
    private fun saveStatus(batch: UploadBatchEntity, plan: ImportSaveCounts?): ImportSaveStatus? =
        when {
            plan == null ->
                ImportSaveStatus.Stopped(batch.saved, batch.total).takeIf { batch.total > 0 && batch.saved + batch.skipped < batch.total }
            plan.state == ImportSaveEntity.STATE_RUNNING -> ImportSaveStatus.Saving(plan.done + plan.skipped, plan.total)
            plan.state == ImportSaveEntity.STATE_STOPPED -> ImportSaveStatus.Stopped(plan.done, plan.total)
            else -> null
        }

    private fun UploadItemRow.toItem(sending: String?) =
        UploadItem(
            id = id,
            uri = rkey?.let { AtUri.of(clients.did, Nsid.FEED_ACTIVITY, it) },
            batchId = batchId,
            isEdit = kind == UploadItemEntity.KIND_EDIT,
            state = state(sending),
            title = title,
            sport = sport,
            subSport = subSport,
            startedAt = startedAt?.let(Instant::fromEpochMilliseconds),
            sent = sentWrites,
            total = totalWrites,
            finishedAt = finishedAt?.let(Instant::fromEpochMilliseconds),
        )

    companion object {
        /** The collections whose writes upload a blob first, which the PDS's blob limit counts. */
        val BLOB_COLLECTIONS =
            listOf(Nsid.FEED_ACTIVITY_STREAMS, Nsid.FEED_ACTIVITY_TRACK, Nsid.FEED_ACTIVITY_SOURCE, Nsid.FEED_SEGMENT_TRACK)
    }
}

/** The share of a stated limit bulk writes may use (data/outbox): the tenth left over is kept for the athlete's own. */
private const val BULK_SHARE = 0.9

/** About what a record write costs against the PDS's write limits, which count points (a create costs more than a delete). */
private const val POINTS_PER_WRITE = 2

/** Fewer writes than this in the pace window say nothing of the pace. */
private const val MIN_PACE_SAMPLES = 2

/**
 * About how long [left] takes (data/upload-queue): the later of what the stated [limits] allow (each at [BULK_SHARE] per window, a blob
 * limit counting [BulkLeft.blobs], a write limit every write) and the pace of [sent] at [now]; null with neither, or nothing left.
 */
internal fun timeLeft(left: BulkLeft, limits: Collection<RateLimit>, sent: List<Instant>, now: Instant): Duration? {
    if (left.writes == 0) return null
    val byLimits =
        limits
            .mapNotNull { limit ->
                val window = limit.windowSeconds?.seconds ?: return@mapNotNull null
                val units = if (limit.method == UploadBlob.NSID) left.blobs else left.writes * POINTS_PER_WRITE
                val perWindow = limit.limit * BULK_SHARE
                if (perWindow <= 0) null else window * (units / perWindow)
            }
            .maxOrNull()
    val recent = sent.filter { it > now - AccountPacing.PACE_WINDOW }
    val byPace =
        if (recent.size < MIN_PACE_SAMPLES) {
            null
        } else {
            val span = (now - recent.first()).coerceAtLeast(1.minutes)
            (span.inWholeMilliseconds.toDouble() / recent.size * left.writes).milliseconds
        }
    return listOfNotNull(byLimits, byPace).maxOrNull()
}
