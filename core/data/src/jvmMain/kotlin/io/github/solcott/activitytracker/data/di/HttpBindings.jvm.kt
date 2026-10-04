package io.github.solcott.activitytracker.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.createHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cache.storage.FileStorage
import kotlinx.io.files.Path

@ContributesTo(AppScope::class)
@BindingContainer
object HttpBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun httpClient(dirs: DesktopAppDirs): HttpClient =
        createHttpClient(cacheStorage = FileStorage(Path(dirs.cache.toString(), HTTP_CACHE_DIR)))
}
