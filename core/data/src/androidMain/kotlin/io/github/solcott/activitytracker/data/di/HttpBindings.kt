package io.github.solcott.activitytracker.data.di

import android.content.Context
import io.github.solcott.activitytracker.atproto.xrpc.createHttpClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.storage.FileStorage
import kotlinx.io.files.Path

@ContributesTo(AppScope::class)
@BindingContainer
object HttpBindings {
    /** The HTTP cache lives in the app's cache dir, so the OS can evict it under storage pressure. */
    @Provides
    @SingleIn(AppScope::class)
    fun httpClient(context: Context): HttpClient =
        createHttpClient(cacheStorage = FileStorage(Path(context.cacheDir.path, HTTP_CACHE_DIR)))
}
