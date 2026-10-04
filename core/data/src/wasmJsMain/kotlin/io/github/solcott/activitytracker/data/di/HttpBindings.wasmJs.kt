package io.github.solcott.activitytracker.data.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.xrpc.createHttpClient
import io.ktor.client.HttpClient

@ContributesTo(AppScope::class)
@BindingContainer
object HttpBindings {
    /** No HttpCache: requests go through `fetch`, and the browser's HTTP cache already covers them. */
    @Provides @SingleIn(AppScope::class) fun httpClient(): HttpClient = createHttpClient()
}
