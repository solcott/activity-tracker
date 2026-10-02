package io.github.solcott.activitytracker.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.storage.FileStorage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@ContributesTo(AppScope::class)
@BindingContainer
object HttpBindings {
    /** The HTTP cache lives in Caches, which is not backed up and which the OS can purge. */
    @OptIn(ExperimentalForeignApi::class)
    @Provides
    @SingleIn(AppScope::class)
    fun httpClient(): HttpClient {
        val caches =
            NSFileManager.defaultManager.URLForDirectory(
                directory = NSCachesDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )
        return createHttpClient(cacheStorage = FileStorage(Path(requireNotNull(caches?.path), HTTP_CACHE_DIR)))
    }
}
