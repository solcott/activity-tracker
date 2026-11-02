package io.github.solcott.activitytracker.data.importsave

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.activityfile.ActivityFileException
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.vinceglb.filekit.AndroidFile
import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps a save's picked documents with persisted read grants while the app's budget allows ([planKeeping]: Android's cap less the grants
 * held and [SPARE_GRANTS]), and past it copies sources of [MAX_COPY_BYTES] or less into `noBackupFilesDir/import-saves/<saveId>/`, which
 * backups never carry. Nothing else in the app persists URI grants, so every persisted read grant is a save's, and [sweep] releases those
 * no live save names.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class AndroidSourceKeeper(private val context: Context, logger: Logger, private val io: CoroutineDispatcher = Dispatchers.IO) :
    SourceKeeper {
    private val log = logger.withTag("AndroidSourceKeeper")
    private val resolver
        get() = context.contentResolver

    private val copiesDir: File
        get() = File(context.noBackupFilesDir, COPIES_DIR)

    override suspend fun keep(saveId: String, sources: List<ImportSource>): List<KeptSource?> =
        withContext(io) {
            val uris = sources.map { documentUri(it) }
            val ways =
                planKeeping(
                    existingGrants = resolver.persistedUriPermissions.size,
                    sdkInt = Build.VERSION.SDK_INT,
                    candidates = sources.mapIndexed { i, source -> KeepCandidate(source.size, grantable = uris[i] != null) },
                )
            sources.mapIndexed { i, source ->
                val path = filePath(source)
                when {
                    // A plain file (never from the picker, but cheap): its path needs no grant.
                    path != null -> KeptSource(KeptSource.Kind.BOOKMARK, path.encodeToByteArray())
                    ways[i] == KeepWay.GRANT ->
                        uris[i]?.let { grant(it) } ?: source.takeIf { it.size <= MAX_COPY_BYTES }?.let { copy(saveId, i, it) }
                    ways[i] == KeepWay.COPY -> copy(saveId, i, source)
                    else -> null
                }
            }
        }

    override suspend fun open(kept: KeptSource): ReopenedSource? =
        withContext(io) {
            val stored = kept.data.decodeToString()
            val file =
                when {
                    kept.kind == KeptSource.Kind.BOOKMARK && stored.startsWith(CONTENT_PREFIX) -> {
                        val uri = Uri.parse(stored)
                        if (!readable(uri)) return@withContext null
                        PlatformFile(uri)
                    }
                    else -> File(stored).takeIf { it.isFile }?.let { PlatformFile(it) } ?: return@withContext null
                }
            ReopenedSource(PickedFileSource(file))
        }

    override suspend fun release(saveId: String, kept: List<KeptSource>) =
        withContext(io) {
            kept.forEach { source -> grantedUri(source)?.let { releaseGrant(it) } }
            saveDir(saveId).deleteRecursively()
            Unit
        }

    override suspend fun sweep(live: Map<String, List<KeptSource>>) =
        withContext(io) {
            val liveUris = live.values.flatten().mapNotNullTo(HashSet()) { grantedUri(it)?.toString() }
            resolver.persistedUriPermissions
                .filter { it.isReadPermission && it.uri.toString() !in liveUris }
                .forEach { releaseGrant(it.uri) }
            copiesDir.listFiles()?.filter { it.name !in live.keys }?.forEach { it.deleteRecursively() }
            Unit
        }

    /** Persists a read grant on [uri]; null when its provider gave none to persist (a picker that isn't `OPEN_DOCUMENT`). */
    private fun grant(uri: Uri): KeptSource? =
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            KeptSource(KeptSource.Kind.BOOKMARK, uri.toString().encodeToByteArray())
        } catch (e: SecurityException) {
            log.w(e) { "Couldn't persist a grant on a picked file" }
            null
        }

    private fun releaseGrant(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            log.w(e) { "Couldn't release a picked file's grant" }
        }
    }

    private fun readable(uri: Uri): Boolean =
        try {
            resolver.openInputStream(uri)?.close() != null
        } catch (e: SecurityException) {
            log.i(e) { "A kept file's grant is gone" }
            false
        } catch (e: IOException) {
            log.i(e) { "A kept file is gone" }
            false
        }

    /** Streams [source] into the save's copies. */
    private suspend fun copy(saveId: String, index: Int, source: ImportSource): KeptSource? {
        val target = File(saveDir(saveId), index.toString())
        return try {
            target.parentFile?.mkdirs()
            target.outputStream().use { source.copyTo(it) }
            KeptSource(KeptSource.Kind.COPY, target.absolutePath.encodeToByteArray())
        } catch (e: IOException) {
            log.w(e) { "Couldn't copy a picked file" }
            target.delete()
            null
        } catch (e: ActivityFileException) {
            log.w(e) { "Couldn't read a picked file to copy" }
            target.delete()
            null
        } finally {
            source.release()
        }
    }

    private fun saveDir(saveId: String): File {
        require(saveId.isNotEmpty() && '/' !in saveId && saveId != "." && saveId != "..") { "Not a save id: $saveId" }
        return File(copiesDir, saveId)
    }

    private companion object {
        const val COPIES_DIR = "import-saves"
    }
}

private const val CONTENT_PREFIX = "content://"
private const val COPY_CHUNK = 1 shl 20

private fun documentUri(source: ImportSource): Uri? =
    ((source as? PlatformBacked)?.file?.androidFile as? AndroidFile.UriWrapper)?.uri?.takeIf { it.scheme == "content" }

private fun filePath(source: ImportSource): String? =
    ((source as? PlatformBacked)?.file?.androidFile as? AndroidFile.FileWrapper)?.file?.absolutePath

private fun grantedUri(kept: KeptSource): Uri? =
    kept.data.decodeToString().takeIf { kept.kind == KeptSource.Kind.BOOKMARK && it.startsWith(CONTENT_PREFIX) }?.let(Uri::parse)

/** Writes all of [this] to [out], a chunk at a time so a large file is never held whole. */
private suspend fun ImportSource.copyTo(out: OutputStream) {
    var offset = 0L
    var chunk = read(offset, COPY_CHUNK)
    while (chunk.isNotEmpty()) {
        out.write(chunk)
        offset += chunk.size
        chunk = if (chunk.size < COPY_CHUNK) ByteArray(0) else read(offset, COPY_CHUNK)
    }
}
