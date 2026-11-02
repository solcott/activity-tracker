package io.github.solcott.activitytracker.data.importsave

import co.touchlab.kermit.Logger
import io.github.solcott.activitytracker.activityfile.ImportSource
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.releaseBookmark
import io.github.vinceglb.filekit.resolveBookmarkData
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Keeps picked files as FileKit bookmarks: a security-scoped bookmark on iOS and sandboxed macOS, a path bookmark on other desktops. A
 * bookmark holds no system grant, so [release] and [sweep] have nothing to free; a reopened file's scope lease ends with
 * [ReopenedSource.close]. Bound in `iosMain` and `jvmMain`.
 */
class BookmarkSourceKeeper(logger: Logger, private val io: CoroutineDispatcher = Dispatchers.IO) : SourceKeeper {
    private val log = logger.withTag("BookmarkSourceKeeper")

    override suspend fun keep(saveId: String, sources: List<ImportSource>): List<KeptSource?> = sources.map { source ->
        (source as? PlatformBacked)?.file?.let { bookmark(it) }
    }

    override suspend fun open(kept: KeptSource): ReopenedSource? {
        if (kept.kind != KeptSource.Kind.BOOKMARK) return null
        return withContext(io) {
            val resolution = attempt("resolve") { PlatformFile.resolveBookmarkData(kept.data) } ?: return@withContext null
            val file = resolution.file
            if (attempt("check") { file.exists() } != true) {
                file.releaseBookmark()
                return@withContext null
            }
            val refreshed = if (resolution.shouldRefresh) bookmark(file) else null
            ReopenedSource(PickedFileSource(file), refreshed, onClose = { file.releaseBookmark() })
        }
    }

    override suspend fun release(saveId: String, kept: List<KeptSource>) = Unit

    override suspend fun sweep(live: Map<String, List<KeptSource>>) = Unit

    private suspend fun bookmark(file: PlatformFile): KeptSource? =
        attempt("bookmark") { KeptSource(KeptSource.Kind.BOOKMARK, file.bookmarkData().bytes) }

    // FileKit and the platform report a file it can't bookmark or resolve in several ways (FileKitException, IOException, NSError- and
    // JNA-backed failures on macOS): any of them means the source isn't kept or is gone.
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> attempt(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w(e) { "Couldn't $what a kept source" }
            null
        }
}
