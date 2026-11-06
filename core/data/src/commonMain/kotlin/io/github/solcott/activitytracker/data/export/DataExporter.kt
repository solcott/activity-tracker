package io.github.solcott.activitytracker.data.export

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.SourceFile
import io.github.solcott.activitytracker.activityfile.readGpxTrack
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.activity.ActivityExporter
import io.github.solcott.activitytracker.data.activity.PlaceRecords
import io.github.solcott.activitytracker.data.activity.StoredActivity
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.DataExportEntity
import io.github.solcott.activitytracker.data.db.DataExportItemEntity
import io.github.solcott.activitytracker.data.db.DataExportListEntity
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AppCollections
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.ActivityDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivitySourceRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamData
import io.github.solcott.activitytracker.lexicon.app.ActivityStreamsRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityTrackRecord
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SyncGetBlob
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Runs the signed-in athlete's data export (auth/data-export) from wherever it stopped, straight from their PDS, never the AppView:
 * 1. lists every `app.activitytracker` collection in their repo and, when the session may, everything in each of their athlete spaces,
 *    staging each record's JSON as returned and noting the blobs records name and the activities they hold;
 * 2. fetches each blob from the place that names it;
 * 3. builds each activity's file from what was staged: its original, else a rebuilt FIT, else none;
 * 4. writes the manifest and README, and marks the export ready.
 *
 * Progress is in Room ([DataExportEntity] and its listings and items) and the staged files in [ExportFiles], so a run killed at any point
 * resumes without reading again what it wrote. An item that can't be read after retries is skipped with why, never failing the export; a
 * full device pauses it, and losing the PDS at the very start fails it, both resumable.
 */
@SingleIn(SessionScope::class)
@Inject
@Suppress("LongParameterList", "TooManyFunctions") // Injected collaborators; each phase of a run and the staged reads it builds on.
class DataExporter(
    private val db: AppDatabase,
    private val clients: SessionClients,
    private val files: ExportFiles,
    private val exporter: ActivityExporter,
    private val exports: DataExports,
    private val clock: Clock,
    logger: Logger,
) {
    private val log = logger.withTag("DataExporter")
    private val dao
        get() = db.dataExports()

    /**
     * Starts the athlete's export, or returns the one they have: an account has at most one (auth/data-export). Notes, for the manifest,
     * the changes on this device the PDS doesn't hold yet and whether this session may read their spaces.
     */
    suspend fun start(): String {
        dao.exportOf(clients.did.value)?.let {
            return it.id
        }
        val export =
            DataExportEntity(
                id = Tid.next(clock),
                did = clients.did.value,
                handle = clients.handle.value,
                pdsHost = clients.pdsUrl.substringAfter("://").substringBefore('/').substringBefore(':').ifEmpty { clients.pdsUrl },
                createdAt = clock.now().toEpochMilliseconds(),
                unsentChanges = db.outbox().pending(clients.did.value).size,
                spacesIncluded = clients.canUseSpaces,
            )
        dao.insert(export)
        return export.id
    }

    /**
     * Runs the athlete's export, if they have one not yet ready, holding the account's export lock: whichever entry point gets here first
     * (the Account page, a background job) runs it, and the others return at once. Returns whether it's still running afterwards (the
     * platform's job asks to run again), false once it's ready, paused, failed, cancelled or someone else is running it.
     */
    suspend fun runCurrent(): Boolean =
        exports.exclusive(clients.did) {
            val export = dao.exportOf(clients.did.value) ?: return@exclusive false
            if (export.state == DataExportEntity.STATE_READY) return@exclusive false
            run(export.id)
            dao.export(export.id)?.state == DataExportEntity.STATE_RUNNING
        } ?: false

    /** Runs export [id] until it's ready, paused or failed; returns at once if it's gone (cancelled). */
    @Suppress("ReturnCount") // Each phase can find the export cancelled, paused or gone, and stops there.
    suspend fun run(id: String) {
        val export = dao.export(id) ?: return
        if (export.did != clients.did.value) return
        dao.setState(id, DataExportEntity.STATE_RUNNING)
        try {
            if (!list(export)) return
            if (!checkSpace(id)) return
            fetchBlobs(id)
            if (dao.export(id) == null) return
            buildActivityFiles(id)
            if (dao.export(id) == null) return
            writeDescription(dao.export(id) ?: return)
            dao.ready(id, clock.now().toEpochMilliseconds())
        } catch (e: NoSpaceException) {
            dao.setState(id, DataExportEntity.STATE_PAUSED, DataExportEntity.REASON_NO_SPACE, e.needed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ListingFailedException) {
            log.w(e) { "Export $id couldn't list the athlete's records" }
            dao.setState(id, DataExportEntity.STATE_FAILED, DataExportEntity.REASON_OFFLINE)
        }
    }

    // 1. Listing.

    /** Lists every place not yet listed; false when the export is gone. Throws [ListingFailedException] when a listing can't be read. */
    private suspend fun list(export: DataExportEntity): Boolean {
        val lists = dao.lists(export.id).associateBy { it.place to it.collection }
        val walks =
            AppCollections.records.map { REPO to it } + if (export.spacesIncluded) SPACES.map { it to ALL_COLLECTIONS } else emptyList()
        for ((place, collection) in walks) {
            var list = lists[place to collection] ?: DataExportListEntity(export.id, place, collection)
            while (!list.done) {
                if (dao.export(export.id) == null) return false
                val page = withRetries { page(place, collection, list.cursor) } ?: throw ListingFailedException(place)
                val items = mutableListOf<DataExportItemEntity>()
                var written = 0
                for (record in page.records) {
                    if (!record.collection.startsWith("${Nsid.NAMESPACE}.")) continue
                    files.write(
                        export.id,
                        recordPath(place, record.collection, record.rkey),
                        RECORD_JSON.encodeToString(record.value).encodeToByteArray(),
                    )
                    written++
                    blobMentions(record.value).forEach {
                        items += DataExportItemEntity(export.id, DataExportItemEntity.KIND_BLOB, place, it.cid.value, it.size)
                    }
                    if (record.collection == Nsid.FEED_ACTIVITY) {
                        items += DataExportItemEntity(export.id, DataExportItemEntity.KIND_ACTIVITY, ANY_PLACE, record.rkey)
                    }
                }
                list =
                    list.copy(cursor = page.cursor, done = page.cursor == null || page.records.isEmpty(), records = list.records + written)
                dao.listed(list, items)
            }
        }
        return true
    }

    private class Page(val records: List<Listed>, val cursor: String?)

    private class Listed(val collection: String, val rkey: String, val value: JsonObject)

    /** One page of [collection] (or, in a space, of everything) at [place]; an empty last page when the space doesn't exist. */
    private suspend fun page(place: String, collection: String, cursor: String?): Page =
        if (place == REPO) {
            val out = clients.pds.query(ListRecords.method, ListRecords.Params(clients.did.value, collection, PAGE_SIZE, cursor))
            Page(out.records.map { Listed(collection, requireNotNull(it.uri.rkey), it.value) }, out.cursor)
        } else {
            when (val read = clients.spaces.listRecords(clients.tiers.uri(place), clients.did, null, PAGE_SIZE, cursor)) {
                is SpaceRead.Found -> Page(read.value.records.mapNotNull { listed(place, it) }, read.value.cursor)
                // A space never created holds nothing; the athlete's own space never refuses them, so a refusal reads as empty too.
                SpaceRead.NotFound,
                SpaceRead.NoAccess -> Page(emptyList(), null)
            }
        }

    // A listing may leave values out; such a record is read on its own.
    private suspend fun listed(place: String, record: Space.ListRecords.Record): Listed? {
        val value =
            record.value
                ?: (clients.spaces.getRecord(clients.tiers.uri(place), clients.did, record.collection, record.rkey) as? SpaceRead.Found)
                    ?.value
                    ?.value
                ?: return null
        return Listed(record.collection, record.rkey, value)
    }

    // 2. Blobs.

    /** Pauses the export when the device hasn't room for the blobs still to fetch, twice over (staged, then zipped); true when it has. */
    private suspend fun checkSpace(id: String): Boolean {
        val free = files.freeBytes() ?: return true
        val needed = 2 * dao.pendingBlobBytes(id) + EXPORT_SPACE_MARGIN
        val roomy = free >= needed
        if (!roomy) dao.setState(id, DataExportEntity.STATE_PAUSED, DataExportEntity.REASON_NO_SPACE, needed - free)
        return roomy
    }

    private suspend fun fetchBlobs(id: String) {
        while (true) {
            if (dao.export(id) == null) return
            val batch = dao.pending(id, DataExportItemEntity.KIND_BLOB, BATCH)
            if (batch.isEmpty()) return
            coroutineScope { batch.chunked(BATCH / CONCURRENCY).map { part -> async { part.forEach { fetchBlob(id, it) } } }.awaitAll() }
        }
    }

    private suspend fun fetchBlob(id: String, item: DataExportItemEntity) {
        val result =
            try {
                withRetries { blob(item.place, Cid(item.key)) }
            } catch (e: BlobUnavailableException) {
                skip(id, item, e.reason)
                return
            }
        if (result == null) {
            skip(id, item, REASON_UNREADABLE)
            return
        }
        val path = blobPath(item.place, item.key)
        files.write(id, path, result)
        dao.done(id, item.kind, item.place, item.key, path)
    }

    private suspend fun blob(place: String, cid: Cid): ByteArray =
        if (place == REPO) {
            try {
                clients.pds.queryBytes(SyncGetBlob.method, SyncGetBlob.Params(clients.did, cid)).bytes
            } catch (e: XrpcException) {
                if (e.status == HTTP_BAD_REQUEST && e.error in NOT_FOUND_ERRORS) throw BlobUnavailableException(REASON_NOT_FOUND)
                throw e
            }
        } else {
            when (val read = clients.spaces.getBlob(clients.tiers.uri(place), clients.did, cid)) {
                is SpaceRead.Found -> read.value.bytes
                SpaceRead.NotFound -> throw BlobUnavailableException(REASON_NOT_FOUND)
                SpaceRead.NoAccess -> throw BlobUnavailableException(REASON_NO_ACCESS)
            }
        }

    // 3. Activity files.

    private suspend fun buildActivityFiles(id: String) {
        val done = dao.items(id, DataExportItemEntity.KIND_ACTIVITY).filter { it.state != DataExportItemEntity.STATE_PENDING }
        val names = ArchiveNames(done.mapNotNull { it.path?.removePrefix("$ACTIVITIES/") })
        // A logbook's dives share one original: the first dive written names it, the rest point at that file.
        val originals = mutableMapOf<String, String>()
        done.forEach { item -> item.path?.let { path -> sourceOf(id, item.key)?.file?.ref?.link?.let { originals[it] = path } } }
        while (true) {
            if (dao.export(id) == null) return
            val batch = dao.pending(id, DataExportItemEntity.KIND_ACTIVITY, BATCH)
            if (batch.isEmpty()) return
            for (item in batch) {
                val path =
                    try {
                        activityFile(id, item.key, names, originals)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: NoSpaceException) {
                        throw e
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        // Any client can write the athlete's records, so one that won't build is skipped, never the whole export.
                        log.w(e) { "Export $id couldn't build activity ${item.key}'s file" }
                        skip(id, item, REASON_UNREADABLE)
                        continue
                    }
                dao.done(id, item.kind, item.place, item.key, path)
            }
        }
    }

    /** Stages [rkey]'s file and returns its path in the archive, or null when it has none (a manual entry, no record anywhere). */
    @Suppress("ReturnCount") // No record, an original already written, the original, then a rebuilt FIT or none.
    private suspend fun activityFile(id: String, rkey: String, names: ArchiveNames, originals: MutableMap<String, String>): String? {
        val stored = storedActivity(id, rkey)
        val record = stored.full ?: return null
        val source = sourceOf(id, rkey)
        val sourceCid = source?.file?.ref?.link
        if (source != null && sourceCid != null) {
            originals[sourceCid]?.let {
                return it
            }
            val bytes = files.read(id, blobPath(AthleteSpace.SELF, sourceCid))
            if (bytes != null) {
                val path = "$ACTIVITIES/${names.original(record, source.fileName)}"
                files.write(id, path, SourceFile.original(bytes, source.compressed))
                originals[sourceCid] = path
                return path
            }
        }
        val fit = exporter.rebuiltFit(stored, trackOf(id, rkey)) ?: return null
        val path = "$ACTIVITIES/${names.rebuilt(record, "fit")}"
        files.write(id, path, fit.bytes)
        return path
    }

    /** [rkey] as the staged records hold it, every place's record, details and streams. */
    private suspend fun storedActivity(id: String, rkey: String): StoredActivity {
        val places =
            Visibility.all.associateWith { tier ->
                val place = placeOfTier(tier)
                PlaceRecords(
                    activity =
                        staged(id, place, Nsid.FEED_ACTIVITY, rkey)?.let { AtJson.decodeFromJsonElement(ActivityRecord.serializer(), it) },
                    details =
                        staged(id, place, Nsid.FEED_ACTIVITY_DETAILS, rkey)?.let {
                            AtJson.decodeFromJsonElement(ActivityDetailsRecord.serializer(), it)
                        },
                    streams = staged(id, place, Nsid.FEED_ACTIVITY_STREAMS, rkey)?.let { streamData(id, place, it) },
                )
            }
        return StoredActivity(AtUri("at://${clients.did.value}/${Nsid.FEED_ACTIVITY}/$rkey"), places)
    }

    private suspend fun streamData(id: String, place: String, value: JsonObject): ActivityStreamData? {
        val cid = AtJson.decodeFromJsonElement(ActivityStreamsRecord.serializer(), value).data?.ref?.link
        return cid?.let { files.read(id, blobPath(place, it)) }
            ?.let { AtJson.decodeFromString(ActivityStreamData.serializer(), it.decodeToString()) }
    }

    private suspend fun trackOf(id: String, rkey: String) =
        staged(id, AthleteSpace.SELF, Nsid.FEED_ACTIVITY_TRACK, rkey)
            ?.let { AtJson.decodeFromJsonElement(ActivityTrackRecord.serializer(), it).track?.ref?.link }
            ?.let { files.read(id, blobPath(AthleteSpace.SELF, it)) }
            ?.let(::readGpxTrack)

    private suspend fun sourceOf(id: String, rkey: String): ActivitySourceRecord? =
        staged(id, AthleteSpace.SELF, Nsid.FEED_ACTIVITY_SOURCE, rkey)?.let {
            runCatching { AtJson.decodeFromJsonElement(ActivitySourceRecord.serializer(), it) }.getOrNull()
        }

    private suspend fun staged(id: String, place: String, collection: String, rkey: String): JsonObject? =
        files.read(id, recordPath(place, collection, rkey))?.let {
            RECORD_JSON.decodeFromString(JsonObject.serializer(), it.decodeToString())
        }

    // 4. Manifest and README.

    private suspend fun writeDescription(export: DataExportEntity) {
        val activities = dao.items(export.id, DataExportItemEntity.KIND_ACTIVITY)
        val blobs = dao.items(export.id, DataExportItemEntity.KIND_BLOB)
        val originals =
            activities
                .mapNotNull { item ->
                    val cid = sourceOf(export.id, item.key)?.file?.ref?.link ?: return@mapNotNull null
                    item.path?.let { cid to it }
                }
                .toMap()
        val manifest =
            ExportManifest(
                did = export.did,
                handle = export.handle,
                pds = export.pdsHost,
                exportedAt = clock.now().toString(),
                spacesIncluded = export.spacesIncluded,
                unsentChanges = export.unsentChanges,
                counts =
                    ExportManifest.Counts(
                        records = dao.recordCount(export.id),
                        blobs = blobs.count { it.state == DataExportItemEntity.STATE_DONE },
                        activityFiles = activities.mapNotNull { it.path }.toSet().size,
                    ),
                activities =
                    activities
                        .mapNotNull { item -> item.path?.let { "at://${export.did}/${Nsid.FEED_ACTIVITY}/${item.key}" to it } }
                        .toMap(),
                originals = originals,
                problems = dao.skipped(export.id).map { ExportManifest.Problem(it.kind, it.place, it.key, it.reason ?: REASON_UNREADABLE) },
            )
        files.write(export.id, MANIFEST, MANIFEST_JSON.encodeToString(ExportManifest.serializer(), manifest).encodeToByteArray())
        files.write(export.id, README, ExportReadme.text(manifest).encodeToByteArray())
    }

    // Shared.

    private suspend fun skip(id: String, item: DataExportItemEntity, reason: String) = dao.skip(id, item.kind, item.place, item.key, reason)

    /** [block], tried again after a failure that may pass (with backoff); null when every try failed. */
    @Suppress("ReturnCount") // A result, the last try failing, then nothing.
    private suspend fun <T> withRetries(block: suspend () -> T): T? {
        var wait = FIRST_RETRY
        repeat(TRIES) { attempt ->
            val result = runSuspendCatching { block() }
            result.exceptionOrNull()?.let { if (it is BlobUnavailableException || it is NoSpaceException) throw it }
            result.getOrNull()?.let {
                return it
            }
            if (attempt == TRIES - 1) return null
            val error = result.exceptionOrNull()
            val retryAfter = (error as? XrpcException)?.retryAfter?.let { it - clock.now() }
            delay(maxOf(wait, retryAfter ?: wait))
            wait *= 2
        }
        return null
    }

    private class ListingFailedException(place: String) : Exception("Couldn't list the athlete's records at $place")

    private class BlobUnavailableException(val reason: String) : Exception(reason)

    companion object {
        /** Where things go in the archive. */
        const val REPO = "repo"
        const val ACTIVITIES = "activities"
        const val MANIFEST = "manifest.json"
        const val README = "README.txt"

        /** The athlete's spaces an export reads, widest first. */
        val SPACES = listOf(AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS, AthleteSpace.SELF, AthleteSpace.INTERACTIONS)

        const val REASON_NOT_FOUND = "notFound"
        const val REASON_NO_ACCESS = "noAccess"
        const val REASON_UNREADABLE = "unreadable"

        /** A space's walk lists every collection at once. */
        internal const val ALL_COLLECTIONS = "*"

        /** An activity's item isn't tied to one place: its records may be split across several. */
        internal const val ANY_PLACE = "*"

        /** `records/<place>/<collection>/<rkey>.json`, the rkey's `:` (which some file systems refuse) written `%3A`. */
        fun recordPath(place: String, collection: String, rkey: String) = "records/$place/$collection/${rkey.replace(":", "%3A")}.json"

        fun blobPath(place: String, cid: String) = "blobs/$place/$cid"

        /** The repo holds the public tier; every other tier is its space. */
        fun placeOfTier(tier: String) = if (tier == Visibility.PUBLIC) REPO else tier

        private const val PAGE_SIZE = 100
        private const val BATCH = 32
        private const val CONCURRENCY = 4
        private const val TRIES = 4
        private val FIRST_RETRY = 1.seconds
        private const val HTTP_BAD_REQUEST = 400
        private val NOT_FOUND_ERRORS = setOf("BlobNotFound", "RepoNotFound", "InvalidRequest")
        private val RECORD_JSON = Json
        private val MANIFEST_JSON = Json {
            prettyPrint = true
            encodeDefaults = true
        }
    }
}
