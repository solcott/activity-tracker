package io.github.solcott.activitytracker.data.export

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.account.AccountJobLauncher
import io.github.solcott.activitytracker.data.account.AccountRunner
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.db.DataExportItemCount
import io.github.solcott.activitytracker.data.db.DataExportItemEntity
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** An account's data export as the Account page shows it (auth/account-page). */
data class DataExportProgress(
    val id: String,
    val state: String,
    val reason: String?,
    /** Records listed so far. */
    val records: Int,
    val blobsDone: Int,
    val blobsTotal: Int,
    val activitiesDone: Int,
    val activitiesTotal: Int,
    /** Items left out because they couldn't be read. */
    val problems: Int,
    val unsentChanges: Int,
    val spacesIncluded: Boolean,
    val neededBytes: Long?,
    val handle: String,
    val createdAt: Instant,
    val finishedAt: Instant?,
)

/**
 * The data exports running app-wide (auth/data-export): one runner per account at a time, whichever session graph or process entry point
 * (the Account page, a background job) starts it; and an account's export from start to its deletion, which happens once it's saved,
 * cancelled, a week after it's ready, or at sign-out. Its staged files ([ExportFiles]) go with it.
 */
@SingleIn(AppScope::class)
@Inject
@Suppress("TooManyFunctions") // Each step of an export's life on the device: launch, progress, save, cancel, expiry and sign-out.
class DataExports(private val db: AppDatabase, private val files: ExportFiles, private val clock: Clock) {
    private val runner = AccountRunner()

    /**
     * The platform's way of keeping an export alive, set at startup: Android's foreground WorkManager job, iOS's background task; null runs
     * exports in this process.
     */
    var launcher: AccountJobLauncher? by runner::launcher

    private val dao
        get() = db.dataExports()

    /** [did]'s export and how far it's got, or null when they have none. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun progress(did: Did): Flow<DataExportProgress?> =
        dao.exportFlow(did.value).flatMapLatest { export ->
            if (export == null) flowOf(null)
            else dao.countsFlow(export.id).map { counts -> progressOf(export, counts, dao.recordCount(export.id)) }
        }

    /** [did]'s export as it stands, or null. */
    suspend fun current(did: Did): DataExportProgress? =
        dao.exportOf(did.value)?.let { progressOf(it, dao.counts(it.id), dao.recordCount(it.id)) }

    private fun progressOf(export: DataExportEntity, counts: List<DataExportItemCount>, records: Int): DataExportProgress {
        fun count(kind: String, state: String? = null) =
            counts.filter { it.kind == kind && (state == null || it.state == state) }.sumOf { it.count }
        return DataExportProgress(
            id = export.id,
            state = export.state,
            reason = export.reason,
            records = records,
            blobsDone = count(DataExportItemEntity.KIND_BLOB) - count(DataExportItemEntity.KIND_BLOB, DataExportItemEntity.STATE_PENDING),
            blobsTotal = count(DataExportItemEntity.KIND_BLOB),
            activitiesDone =
                count(DataExportItemEntity.KIND_ACTIVITY) - count(DataExportItemEntity.KIND_ACTIVITY, DataExportItemEntity.STATE_PENDING),
            activitiesTotal = count(DataExportItemEntity.KIND_ACTIVITY),
            problems = counts.filter { it.state == DataExportItemEntity.STATE_SKIPPED }.sumOf { it.count },
            unsentChanges = export.unsentChanges,
            spacesIncluded = export.spacesIncluded,
            neededBytes = export.neededBytes,
            handle = export.handle,
            createdAt = Instant.fromEpochMilliseconds(export.createdAt),
            finishedAt = export.finishedAt?.let(Instant::fromEpochMilliseconds),
        )
    }

    /** Export [id]'s archive, once it's ready, named for [zone]'s date; null while it isn't. */
    suspend fun archive(id: String, zone: TimeZone): ExportArchive? {
        val export = dao.export(id)?.takeIf { it.state == DataExportEntity.STATE_READY } ?: return null
        val started = Instant.fromEpochMilliseconds(export.createdAt)
        return ExportArchive(id, ExportArchive.nameOf(export.handle, started, zone), started.toLocalDateTime(zone), ArchiveZipper(files))
    }

    /** Whether [did] has an export running or waiting to be saved: signing out would cancel and delete it. */
    suspend fun hasExport(did: Did): Boolean = dao.exportOf(did.value) != null

    /** Runs [block] holding [did]'s export lock, or returns null at once when another runner holds it: that one finishes the export. */
    suspend fun <T> exclusive(did: Did, block: suspend () -> T): T? = runner.exclusive(did, block)

    /** Starts [did]'s export running: through the platform's [launcher], else [inProcess] in this app-wide scope. */
    fun launch(did: Did, inProcess: suspend () -> Unit) = runner.launch(did, inProcess)

    /** Runs [block], [did]'s export, in this app-wide scope, until it ends, is cancelled or [did] signs out. */
    fun runInProcess(did: Did, block: suspend () -> Unit) = runner.runInProcess(did, block)

    /** Stops and deletes [did]'s export, whatever its state, with its staged files. */
    suspend fun cancel(did: Did) =
        withContext(NonCancellable) {
            runner.cancel(did)
            dao.exportOf(did.value)?.let { delete(it.id) }
        }

    /** The athlete saved export [id]'s archive: the app's copy goes. */
    suspend fun saved(id: String) = withContext(NonCancellable) { delete(id) }

    /** [did] signed out: their export stops and is deleted (auth/account-page). */
    suspend fun signedOut(did: Did) = cancel(did)

    /** Stops every in-process export, for tests. */
    internal fun cancelAll() = runner.cancelAll()

    /**
     * Deletes ready exports a week old ([EXPIRY]) and staged folders no export names (a crash between deleting the row and the files), at
     * session start.
     */
    suspend fun sweep() =
        withContext(NonCancellable) {
            val expired = (clock.now() - EXPIRY).toEpochMilliseconds()
            dao.all()
                .filter { it.state == DataExportEntity.STATE_READY && (it.finishedAt ?: it.createdAt) < expired }
                .forEach { delete(it.id) }
            val live = dao.all().map { it.id }.toSet()
            files.exportIds().filter { it !in live }.forEach { files.delete(it) }
        }

    /**
     * A session for [did] started: sweeps, then [launch]es [run] if [did]'s export was left running (the app was killed, or quit, mid-way).
     * A paused or failed one waits for the athlete to try again.
     */
    suspend fun resume(did: Did, run: suspend () -> Unit) {
        sweep()
        if (dao.exportOf(did.value)?.state == DataExportEntity.STATE_RUNNING) launch(did, run)
    }

    private suspend fun delete(id: String) {
        dao.drop(id)
        files.delete(id)
    }

    companion object {
        /** How long a ready export waits to be saved before the app deletes it. */
        val EXPIRY = 7.days
    }
}
