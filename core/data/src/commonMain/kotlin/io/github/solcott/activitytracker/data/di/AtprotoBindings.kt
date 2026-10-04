package io.github.solcott.activitytracker.data.di

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthClient
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.atproto.oauth.OAuthStore
import io.ktor.client.HttpClient
import kotlin.time.Clock

/** App-wide protocol singletons. [AtprotoConfig] is supplied by each platform's app graph, and the [HttpClient] by `HttpBindings`. */
@ContributesTo(AppScope::class)
@BindingContainer
object AtprotoBindings {
    @Provides fun clock(): Clock = Clock.System

    /** Kermit's global logger; its default writer goes to Logcat, OSLog or the browser console. Tests pass their own. */
    @Provides fun logger(): Logger = Logger

    @Provides
    @SingleIn(AppScope::class)
    fun identityResolver(http: HttpClient, config: AtprotoConfig): IdentityResolver = IdentityResolver(http, config)

    @Provides
    @SingleIn(AppScope::class)
    fun oauthClient(
        http: HttpClient,
        identity: IdentityResolver,
        config: AtprotoConfig,
        clock: Clock,
    ): OAuthClient = OAuthClient(http, identity, config.oauth, clock)

    @Provides
    @SingleIn(AppScope::class)
    fun sessionManager(client: OAuthClient, store: OAuthStore, clock: Clock, logger: Logger): OAuthSessionManager =
        OAuthSessionManager(client, store, clock, logger)
}

/** Subdirectory of the platform cache dir that holds the persistent HttpCache. */
internal const val HTTP_CACHE_DIR = "http-cache"
