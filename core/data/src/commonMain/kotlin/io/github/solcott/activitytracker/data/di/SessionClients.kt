package io.github.solcott.activitytracker.data.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.atproto.space.ManagingApp
import io.github.solcott.activitytracker.atproto.space.SpaceClient
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.xrpc.ServiceAuthAuthenticator
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * XRPC clients for the signed-in account: [pds] writes to the user's repo with DPoP-bound tokens; [appView] reads aggregated views,
 * authenticated by service-auth JWTs minted by that PDS; [spaces] reads and writes atproto spaces. The Spaces binding key and credentials
 * live only as long as this session.
 */
@SingleIn(SessionScope::class)
@Inject
class SessionClients(
    private val session: OAuthSession,
    private val http: HttpClient,
    private val sessionManager: OAuthSessionManager,
    private val config: AtprotoConfig,
    identity: IdentityResolver,
    clock: Clock,
) {
    val did: Did = session.did

    /** The account's PDS base URL. Follows the live session so a PDS migration picked up on refresh is respected. */
    val pdsUrl: String
        get() = sessionManager.session.value?.pdsUrl ?: session.pdsUrl

    val pds: XrpcClient =
        XrpcClient(
            http = http,
            serviceUrl = { pdsUrl },
            authenticator = sessionManager.pdsAuthenticator,
        )

    val appView: XrpcClient =
        XrpcClient(
            http = http,
            serviceUrl = config.appViewUrl,
            authenticator = ServiceAuthAuthenticator({ pds }, config.appViewDid, clock),
        )

    val spaces: SpaceClient =
        SpaceClient(
            did = did,
            pds = pds,
            credentials = SpaceCredentials(pds, http, identity, clock = clock),
            http = http,
        )

    private val managingAppLock = Mutex()
    private var resolvedManagingApp: String? = null

    /** The AppView's managing-app id for athletes' spaces, from its DID document ([ManagingApp.resolve]); looked up once per session. */
    suspend fun managingApp(): String = managingAppLock.withLock {
        resolvedManagingApp ?: ManagingApp.resolve(http, config.appViewUrl).also { resolvedManagingApp = it }
    }
}
