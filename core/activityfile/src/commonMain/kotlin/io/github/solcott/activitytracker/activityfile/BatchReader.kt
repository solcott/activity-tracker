package io.github.solcott.activitytracker.activityfile

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Where an activity file is among a batch's picked files: the [source] it was picked as, then the [path] of archive members leading to it
 * (empty for a picked file itself, one member for a file in a zip, two for a file in a zip in a zip).
 */
data class EntryRef(val source: Int, val path: List<String> = emptyList())

/** An activity file as found: [bytes] as picked or as stored in its archive (still gzipped, if it was), named [name] without folders. */
class FoundFile(val ref: EntryRef, val name: String, val bytes: ByteArray)

/** What a [BatchReader.scan] reports, in the order it reads. */
sealed interface ScanEvent {
    /**
     * The file at [ref], named [name] and [size] bytes as found, holds [read]'s activities, in file order; [originalTooLarge] when it can't
     * be kept as its source. [export] says what the account export it came in says of each activity (null for any other file).
     */
    class Read(
        val ref: EntryRef,
        val name: String,
        val read: ImportedFile,
        val originalTooLarge: Boolean,
        val size: Long = 0,
        val export: FileExport? = null,
    ) : ScanEvent {
        /** What the export says of the activity at [index] in [read], if anything. */
        fun exportOf(index: Int): AccountExportActivity? = export?.activities?.getOrNull(index)
    }

    /** A file that looked like one we read, but isn't, or can't be: listed for the athlete with [reason]. */
    data class Skipped(val name: String, val reason: SkipReason) : ScanEvent

    /** So far: [files] activity files read, [activities] found in them, and [ignored] archive members that aren't activity files. */
    data class Progress(val files: Int, val activities: Int, val ignored: Int) : ScanEvent
}

/** A [kind] of account export's say on each activity of a file in it, in file order: null where it has none. */
class FileExport(val kind: ExportKind, val activities: List<AccountExportActivity?>)

/** Why a file in a batch wasn't read. */
enum class SkipReason {
    /** Not a format we read, or compressed in a way we don't. */
    Unsupported,

    /** Truncated or corrupt. */
    Damaged,

    /** Past a size limit, its own or its archive's. */
    TooLarge,
}

/**
 * Reads a batch of picked [sources]: each a file we read, a gzipped one, or a zip of them (zips inside zips too), without ever holding an
 * archive whole. [scan] reports every activity file in turn, and [readEach] reads chosen ones again to preview or save, so the batch itself
 * keeps only summaries. Archive listings are kept between calls.
 */
class BatchReader(private val sources: List<ImportSource>, private val reader: ActivityFileReader) {
    private val archives = mutableMapOf<Int, ZipArchive?>()

    /** Every activity file in the sources, in order, on [Dispatchers.Default]; cancelling the collection stops it. */
    @Suppress("InjectDispatcher") // CPU-bound reading, like ActivityFileReader.
    fun scan(): Flow<ScanEvent> = flow { Scan(this).run() }.flowOn(Dispatchers.Default)

    /**
     * Reads the files at [refs] again, handing each to [onRead] as found or as the error reading it, in the order the sources hold them:
     * one pass serves every file a nested archive holds.
     */
    suspend fun readEach(refs: Collection<EntryRef>, onRead: suspend (EntryRef, Result<FoundFile>) -> Unit) {
        val wanted = refs.toSet()
        for ((source, inSource) in wanted.groupBy { it.source }.entries.sortedBy { it.key }) {
            val outcome = runSuspendCatchingFile { readFrom(source, inSource, onRead) }
            outcome.exceptionOrNull()?.let { error -> inSource.forEach { onRead(it, Result.failure(error)) } }
        }
    }

    /** The one file at [ref]. */
    suspend fun read(ref: EntryRef): FoundFile {
        var found: Result<FoundFile>? = null
        readEach(listOf(ref)) { _, result -> found = result }
        return (found ?: Result.failure(ActivityFileException("${ref.path.lastOrNull() ?: sources[ref.source].name} is gone"))).getOrThrow()
    }

    private suspend fun readFrom(source: Int, refs: List<EntryRef>, onRead: suspend (EntryRef, Result<FoundFile>) -> Unit) {
        val file = sources[source]
        val whole = refs.firstOrNull { it.path.isEmpty() }
        if (whole != null) onRead(whole, runSuspendCatchingFile { FoundFile(whole, file.name, file.readAll(MAX_UNZIPPED_BYTES)) })
        val inArchive = refs.filter { it.path.isNotEmpty() }
        if (inArchive.isEmpty()) return
        val archive = archiveOf(source) ?: throw UnsupportedActivityFileException()
        val byPath = archive.entries.associateBy { it.path }
        for ((member, below) in inArchive.groupBy { it.path.first() }) {
            val entry = byPath[member]
            if (entry == null) {
                below.forEach { onRead(it, Result.failure(ActivityFileException("$member is gone"))) }
                continue
            }
            val outcome = runSuspendCatchingFile { readMember(archive.open(entry), entry.name, below, depth = 1, onRead) }
            outcome.exceptionOrNull()?.let { error -> below.forEach { onRead(it, Result.failure(error)) } }
        }
    }

    /** Reads [refs] from [data], a member at [depth] of their paths: the file itself, or a zip holding them. */
    private suspend fun readMember(
        data: ByteInput,
        name: String,
        refs: List<EntryRef>,
        depth: Int,
        onRead: suspend (EntryRef, Result<FoundFile>) -> Unit,
    ) {
        val here = refs.firstOrNull { it.path.size == depth }
        if (here != null) {
            onRead(here, runSuspendCatchingFile { FoundFile(here, name, data.readAll(MAX_UNZIPPED_BYTES)) })
            return
        }
        val pending = refs.groupBy { it.path[depth] }.toMutableMap()
        val stream = ZipStream(data)
        var next = stream.next()
        while (next != null) {
            pending.remove(next.entry.path)?.let { below -> readMember(next.data, next.entry.name, below, depth + 1, onRead) }
            next = if (pending.isEmpty()) null else stream.next()
        }
        pending.values.flatten().forEach { onRead(it, Result.failure(ActivityFileException("${it.path.last()} is gone"))) }
    }

    /** [source]'s zip listing, or null when it isn't a zip. */
    private suspend fun archiveOf(source: Int): ZipArchive? =
        archives.getOrPut(source) {
            val file = sources[source]
            if (isZip(file.read(0, ZIP_SIGNATURE_BYTES))) ZipArchive.read(file).archive else null
        }

    /** One scan's walk and running counts. */
    private inner class Scan(private val out: FlowCollector<ScanEvent>) {
        private var files = 0
        private var activities = 0
        private var ignored = 0
        /** Decompressed bytes read from the current top-level archive, against [MAX_ARCHIVE_BYTES]. */
        private var archiveBytes = 0L
        /** The current top-level archive reached a limit: the rest of it is left unread. */
        private var archiveStopped = false
        /** The current top-level archive's account export metadata, if it is one. */
        private var export: ExportMetadata? = null

        suspend fun run() {
            sources.forEachIndexed { index, source ->
                val outcome = runSuspendCatchingFile {
                    val head = source.read(0, ZIP_SIGNATURE_BYTES)
                    export = null
                    if (isZip(head)) scanArchive(index, source)
                    else scanFile(EntryRef(index), source.name) { source.readAll(MAX_UNZIPPED_BYTES) }
                }
                outcome.exceptionOrNull()?.let { out.emit(ScanEvent.Skipped(source.name, it.skipReason())) }
            }
            out.emit(ScanEvent.Progress(files, activities, ignored))
        }

        private suspend fun scanArchive(index: Int, source: ImportSource) {
            archiveBytes = 0
            archiveStopped = false
            val listing = ZipArchive.read(source)
            archives[index] = listing.archive
            export = ExportMetadata.of(listing.archive)
            for (entry in listing.archive.entries) {
                if (!archiveStopped) scanMember(EntryRef(index, listOf(entry.path)), entry, source.name) { listing.archive.open(entry) }
            }
            if (listing.truncated && !archiveStopped) out.emit(ScanEvent.Skipped(source.name, SkipReason.TooLarge))
        }

        /** Scans one archive member, unless the archive has reached a limit ([archiveStopped]). */
        private suspend fun scanMember(ref: EntryRef, entry: ZipEntry, archiveName: String, open: suspend () -> ByteInput) {
            val kind = memberKind(entry.path)
            when {
                entry.isDirectory || archiveStopped -> Unit
                kind == MemberKind.Other -> ignored++
                !entry.isSupported -> out.emit(ScanEvent.Skipped(entry.name, SkipReason.Unsupported))
                archiveBytes + entry.size > MAX_ARCHIVE_BYTES -> {
                    archiveStopped = true
                    out.emit(ScanEvent.Skipped(archiveName, SkipReason.TooLarge))
                }
                else -> {
                    val outcome = runSuspendCatchingFile { scanContent(ref, entry, kind, archiveName, open) }
                    // A nested zip that ends early can't be read further; the archive itself still can. A device's other FIT files
                    // (Garmin's
                    // uploads are mostly daily wellness monitoring) are left out like any other file that isn't an activity.
                    when (val failure = outcome.exceptionOrNull()) {
                        null -> Unit
                        is NotAnActivityFileException -> ignored++
                        else -> out.emit(ScanEvent.Skipped(entry.name, failure.skipReason()))
                    }
                }
            }
        }

        private suspend fun scanContent(
            ref: EntryRef,
            entry: ZipEntry,
            kind: MemberKind,
            archiveName: String,
            open: suspend () -> ByteInput,
        ) {
            if (kind == MemberKind.Archive) {
                // Its members count themselves as they're read.
                scanNested(ref, open(), archiveName)
            } else {
                val data = CountingInput(open())
                try {
                    scanFile(ref, entry.name) { data.readAll(MAX_UNZIPPED_BYTES) }
                } finally {
                    archiveBytes += data.count
                }
            }
        }

        private suspend fun scanNested(ref: EntryRef, data: ByteInput, archiveName: String) {
            val stream = ZipStream(data)
            var next = stream.next()
            var members = 1
            while (next != null && !archiveStopped && members <= MAX_ARCHIVE_MEMBERS) {
                val member = next
                scanMember(ref.copy(path = ref.path + member.entry.path), member.entry, archiveName) { member.data }
                next = stream.next()
                members++
            }
            if (next != null && members > MAX_ARCHIVE_MEMBERS) {
                out.emit(ScanEvent.Skipped(ref.path.last().substringAfterLast('/'), SkipReason.TooLarge))
            }
        }

        private suspend fun scanFile(ref: EntryRef, name: String, bytes: suspend () -> ByteArray) {
            val found = bytes()
            val read = reader.readFile(found)
            files++
            activities += read.activities.size
            val export = export
            out.emit(
                ScanEvent.Read(
                    ref,
                    name,
                    read,
                    originalTooLarge = storedSizeExceeds(found, read),
                    size = found.size.toLong(),
                    export = export?.let { found -> FileExport(found.kind, read.activities.map { found.lookup(ref.path, it) }) },
                )
            )
            out.emit(ScanEvent.Progress(files, activities, ignored))
        }
    }

    /** [input] counting the bytes read through it. */
    private class CountingInput(private val input: ByteInput) : ByteInput {
        var count = 0L
            private set

        override suspend fun read(into: ByteArray, offset: Int, length: Int): Int =
            input.read(into, offset, length).also { if (it > 0) count += it }
    }

    private enum class MemberKind {
        File,
        Archive,
        Other,
    }

    private companion object {
        const val ZIP_SIGNATURE_BYTES = 4

        /** Decompressed bytes read per top-level archive, far beyond any account export: stops one built to run forever. */
        const val MAX_ARCHIVE_BYTES = 10L * 1024 * 1024 * 1024

        val FILE_EXTENSIONS = setOf("fit", "gpx", "tcx", "xml", "uddf", "ssrf", "gz")

        /** What an archive member's name says it is; folders' worth of a Mac's metadata are nothing. */
        fun memberKind(path: String): MemberKind {
            val name = path.substringAfterLast('/')
            if (path.startsWith("__MACOSX/") || name.startsWith("._")) return MemberKind.Other
            val extension = name.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "zip" -> MemberKind.Archive
                in FILE_EXTENSIONS -> MemberKind.File
                else -> MemberKind.Other
            }
        }

        /** Whether [bytes], kept as [SourceFile.prepare] would, pass its limit; gzipped only when that could matter. */
        suspend fun storedSizeExceeds(bytes: ByteArray, read: ImportedFile): Boolean =
            when {
                bytes.size <= SourceFile.MAX_STORED_BYTES -> false
                read.gzipped -> true
                else -> gzip(bytes).size > SourceFile.MAX_STORED_BYTES
            }

        fun Throwable.skipReason(): SkipReason =
            when (this) {
                is FileTooLargeException -> SkipReason.TooLarge
                is UnsupportedActivityFileException,
                is NotAnActivityFileException,
                is UnsupportedArchiveEntryException,
                is ZipStreamEndedException -> SkipReason.Unsupported
                else -> SkipReason.Damaged
            }
    }
}

/**
 * [block]'s result, or the [ActivityFileException] (or other failure reading a file) it threw; cancellation still propagates. Parsers can
 * fail in ways of their own (an XML library's exception), all of which mean the file can't be read.
 */
@Suppress("TooGenericExceptionCaught") // Any failure reading one file is that file's, never the batch's.
private suspend fun <T> runSuspendCatchingFile(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: kotlin.coroutines.cancellation.CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
