package io.github.solcott.activitytracker.data.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.atproto.xrpc.ServiceAuthAuthenticator
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient
import kotlin.time.Clock

/**
 * XRPC clients for the signed-in account: [pds] writes to the user's repo with DPoP-bound tokens; [appView] reads aggregated views,
 * authenticated by service-auth JWTs minted by that PDS.
 */
@SingleIn(SessionScope::class)
@Inject
class SessionClients(
    session: OAuthSession,
    http: HttpClient,
    sessionManager: OAuthSessionManager,
    config: AtprotoConfig,
    clock: Clock,
) {
    val did: Did = session.did

    val pds: XrpcClient =
        XrpcClient(
            http = http,
            // Follows the live session so a PDS migration picked up on refresh is respected.
            serviceUrl = { sessionManager.session.value?.pdsUrl ?: session.pdsUrl },
            authenticator = sessionManager.pdsAuthenticator,
        )

    val appView: XrpcClient =
        XrpcClient(
            http = http,
            serviceUrl = config.appViewUrl,
            authenticator = ServiceAuthAuthenticator({ pds }, config.appViewDid, clock),
        )
}
