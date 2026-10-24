package io.github.solcott.activitytracker.data.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.oauth.OAuthSession
import io.github.solcott.activitytracker.atproto.oauth.OAuthSessionManager
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.ManagingApp
import io.github.solcott.activitytracker.atproto.space.SpaceClient
import io.github.solcott.activitytracker.atproto.space.SpaceCredentials
import io.github.solcott.activitytracker.atproto.xrpc.ServiceAuthAuthenticator
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.data.profile.AthleteSpaces
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.NoParams
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.NotifySpace
import io.github.solcott.activitytracker.lexicon.atproto.DescribeServer
import io.ktor.client.HttpClient
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    val handle: Handle = session.handle

    /** The app's labeler, which reports go to; null where none is configured, and then the app offers no Report. */
    val labelerDid: Did? = config.labelerDid

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

    /** The athlete's tier spaces, shared so each is ensured, and announced to the AppView, once per session. */
    val tiers: AthleteSpaces =
        AthleteSpaces(spaces, ::managingApp) { space -> appView.procedure(NotifySpace.method, NotifySpace.Input(space)) }

    /**
     * Whether the athlete's PDS serves Spaces, from its `describeServer` ([resolveSpacesSupport]). [SpacesSupport.Unknown] until that
     * answers, or when it fails, and then the scope alone decides, as before the check existed.
     */
    val spacesSupport: StateFlow<SpacesSupport>
        field = MutableStateFlow(SpacesSupport.Unknown)

    /**
     * Whether this session may use the athlete's tier spaces at all. One signed in before the `space:` scope existed can't, so it publishes
     * public activities only until the athlete signs in again; nor can one on a PDS without Spaces ([spacesSupport]).
     */
    val canUseSpaces: Boolean
        get() = hasSpacesScope && spacesSupport.value != SpacesSupport.Unsupported

    /**
     * Whether this session was granted the `space:` scope, whatever the PDS supports: without it, what the athlete has in spaces is
     * unreadable.
     */
    val hasSpacesScope: Boolean
        get() = athleteSpaceScopes.isNotEmpty()

    /**
     * Whether this session may write [collection] to the athlete's tier spaces. The PDS fills each `space:` grant with the collections the
     * space type declares when it issues the token, and refuses a write to any other with `ScopeMissingError`, so a session signed in
     * before the declaration listed [collection] can't write it. A grant that lists no collections is the scope as requested (no
     * declaration was filled in), and is taken to cover them all, as [canUseSpaces] does. Never on a PDS without Spaces.
     */
    fun canWriteToSpaces(collection: String): Boolean =
        spacesSupport.value != SpacesSupport.Unsupported &&
            athleteSpaceScopes.any { scope ->
                val collections = scopeParams(scope, "collection")
                collections.isEmpty() || "*" in collections || collection in collections
            }

    /**
     * Asks the athlete's PDS once whether it serves Spaces, at session start. The Spaces alpha's `describeServer` says so with a temporary
     * `spacesAlpha: true`; a PDS that answers without it can't host spaces. A failed lookup leaves [SpacesSupport.Unknown], so a flaky PDS
     * never hides private data from an athlete who has it. When upstream drops the flag, delete this and [spacesSupport].
     */
    suspend fun resolveSpacesSupport() {
        if (spacesSupport.value != SpacesSupport.Unknown) return
        val described = runSuspendCatching { pds.query(DescribeServer.method, NoParams) }.getOrNull() ?: return
        spacesSupport.value = if (described.spacesAlpha == true) SpacesSupport.Supported else SpacesSupport.Unsupported
    }

    private val athleteSpaceScopes: List<String> = session.scope.split(' ').filter { it.startsWith("space:${Nsid.SPACE_ATHLETE}") }

    private val managingAppLock = Mutex()
    private var resolvedManagingApp: String? = null

    /** The AppView's managing-app id for athletes' spaces, from its DID document ([ManagingApp.resolve]); looked up once per session. */
    suspend fun managingApp(): String = managingAppLock.withLock {
        resolvedManagingApp ?: ManagingApp.resolve(http, config.appViewUrl).also { resolvedManagingApp = it }
    }
}

/** Whether the athlete's PDS serves atproto Spaces ([SessionClients.spacesSupport]). */
enum class SpacesSupport {
    Supported,
    Unsupported,

    /** Not asked yet, or the PDS didn't answer: the session's scope decides. */
    Unknown,
}

/**
 * A non-public save refused before anything was written because the athlete's PDS doesn't host spaces ([SpacesSupport.Unsupported]). Unlike
 * a missing `space:` scope, signing in again doesn't help.
 */
class SpacesUnsupportedException : IllegalStateException("This server doesn't support private data yet")

/** The values of [name] in [scope]'s query (`space:<type>?collection=a&collection=b`). */
private fun scopeParams(scope: String, name: String): List<String> =
    scope.substringAfter('?', "").split('&').mapNotNull { param -> param.split('=', limit = 2).takeIf { it[0] == name }?.getOrNull(1) }
