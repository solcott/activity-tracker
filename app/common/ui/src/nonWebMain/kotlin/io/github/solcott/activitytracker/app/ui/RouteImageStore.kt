package io.github.solcott.activitytracker.app.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

/**
 * The image loader for feed cards' map images, apart from the photos' singleton so that signing out can drop every map image (some show
 * routes that aren't public) without touching photos: Coil's disk cache can't clear entries by kind.
 */
internal class RouteImageStore(private val directory: Path, private val dispatcher: CoroutineDispatcher = Dispatchers.Default) {
    private var loader: ImageLoader? = null

    /** Built without a context, so a start signed out can clear it before any card asks for the loader. */
    private val diskCache by lazy { DiskCache.Builder().directory(directory).maxSizeBytes(DISK_BYTES).build() }

    private var sessionKnown = false
    private var sessionDid: String? = null

    /** The process's loader, built on first use. */
    fun loader(context: PlatformContext): ImageLoader =
        loader
            ?: ImageLoader.Builder(context)
                .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_SHARE).build() }
                .diskCache { diskCache }
                .components {
                    add(RouteSnapshotKeyer())
                    add(RouteSnapshotFetcher.Factory())
                }
                .build()
                .also { loader = it }

    /**
     * Tells the store who is signed in, once the session is restored and whenever it changes. Signed out, at startup too in case the app
     * stopped mid sign-out, and on a switch to another account, it drops every map image from memory and disk.
     */
    suspend fun onSession(did: String?) {
        val first = !sessionKnown
        val previous = sessionDid
        sessionKnown = true
        sessionDid = did
        val switched = !first && previous != null && previous != did
        if (did == null || switched) clear()
    }

    private suspend fun clear() {
        loader?.memoryCache?.clear()
        withContext(dispatcher) { diskCache.clear() }
    }

    private companion object {
        const val MEMORY_SHARE = 0.1
        const val DISK_BYTES = 64L * 1024 * 1024
    }
}

/** The process's [RouteImageStore], in the cache directory Coil uses for its own. */
internal val RouteImages = RouteImageStore(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "route-maps")
