package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.AppViewConfig
import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.auth.AuthException
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.XrpcErrorBody
import io.github.solcott.activitytracker.lexicon.XrpcParams
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.GetFollowRequests
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.NotifySpace
import io.github.solcott.activitytracker.lexicon.app.SearchActors
import io.github.solcott.activitytracker.lexicon.atproto.SimpleSpace
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.toMap
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

@Suppress("LongParameterList") // One view per area of the API.
fun Route.xrpcRoutes(
    views: Views,
    graph: GraphViews,
    search: ActorSearch,
    streams: ActivityStreamsViews,
    spaceAccess: SpaceAccess,
    verifier: ServiceAuthVerifier,
    managingAppVerifier: ServiceAuthVerifier,
) {
    xrpcQuery(GetTimeline.method, verifier) { params, viewer -> views.timeline(requireViewer(viewer), params) }
    xrpcQuery(GetAuthorActivities.method, verifier) { params, viewer -> views.authorActivities(viewer, params) }
    xrpcQuery(GetAuthorActivitiesByStart.method, verifier) { params, viewer -> views.authorActivitiesByStart(viewer, params) }
    xrpcQuery(GetActivity.method, verifier) { params, viewer -> views.activity(viewer, params) }
    xrpcQuery(GetActivityStreams.method, verifier) { params, viewer -> streams.streams(viewer, params) }
    xrpcQuery(GetLikes.method, verifier) { params, viewer -> views.likes(viewer, params) }
    xrpcQuery(GetProfile.method, verifier) { params, viewer -> views.profile(viewer, params.actor) }
    xrpcQuery(SearchActors.method, verifier) { params, viewer ->
        requireViewer(viewer)
        search.search(params)
    }
    xrpcQuery(GetFollowers.method, verifier) { params, _ -> graph.followers(params) }
    xrpcQuery(GetFollows.method, verifier) { params, _ -> graph.follows(params) }
    xrpcQuery(GetFollowRequests.method, verifier) { params, viewer -> graph.followRequests(requireViewer(viewer), params) }
    // The managing-app callout from an athlete's PDS, whose token is addressed to the managing app rather than the AppView.
    xrpcQuery(SimpleSpace.CheckUserAccess.method, managingAppVerifier) { params, issuer ->
        spaceAccess.check(requireViewer(issuer), params)
    }
}

/**
 * Athletes' tier spaces, which the AppView syncs as a member ([SpaceSyncer]; null when no signing key is configured, and then nothing is
 * joined):
 * - `notifySpace`: an athlete announces one of their tier spaces, with service auth for the AppView;
 * - `notifyWrite`: the authority's PDS says the space advanced, with service auth addressed to [syncerVerifier]'s syncer service id, so a
 *   token for the AppView or the managing app never works here, or the reverse.
 */
fun Route.spaceSyncRoutes(syncer: SpaceSyncer?, verifier: ServiceAuthVerifier, syncerVerifier: ServiceAuthVerifier) {
    xrpcProcedure(NotifySpace.method, verifier) { input, caller ->
        syncer?.announce(announced(input.space, requireViewer(caller)))
        JsonObject(emptyMap())
    }
    xrpcProcedure(Space.NotifyWrite.method, syncerVerifier) { input, issuer ->
        // Only the space's authority, whose PDS hosts the space, sends these.
        if (requireViewer(issuer) != input.space.authority)
            throw AuthException("notifyWrite from ${issuer?.value}, not ${input.space.authority}")
        // Only the authority writes to an athlete's space; a notification for another repo has nothing for the AppView.
        if (input.repo == input.space.authority) syncer?.notified(input.space)
        JsonObject(emptyMap())
    }
}

/** [space], if [caller] may announce it: it must be one of their own athlete tier spaces. */
private fun announced(space: SpaceUri, caller: Did): SpaceUri {
    val error =
        when {
            space.authority != caller -> NotifySpace.NOT_SPACE_AUTHORITY to "Not your space: $space"
            !SpaceSyncer.isSyncable(space) -> NotifySpace.UNSUPPORTED_SPACE to "Not an athlete tier space: $space"
            else -> null
        }
    if (error != null) throw XrpcError(HttpStatusCode.BadRequest.value, error.first, error.second)
    return space
}

/** The authenticated caller, for queries that need one. */
private fun requireViewer(viewer: Did?): Did = viewer ?: throw AuthException("Authentication required")

/** Serves a typed query. Auth is optional here; handlers that need a viewer throw [AuthException]. */
private fun <P, O> Route.xrpcQuery(
    method: XrpcQuery<P, O>,
    verifier: ServiceAuthVerifier,
    handler: suspend (params: P, viewer: Did?) -> O,
) {
    get("/xrpc/${method.nsid}") {
        val params = runCatching {
            XrpcParams.decode(method.params, call.request.queryParameters.toMap())
        }
            .getOrElse { e ->
                throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", e.message ?: "Invalid parameters", cause = e)
            }
        val viewer = call.viewer(verifier, method.nsid)
        call.respondText(
            AtJson.encodeToString(method.output, handler(params, viewer)),
            ContentType.Application.Json,
        )
    }
}

/** Serves a typed procedure, decoding its JSON input. Auth is optional here, as for [xrpcQuery]. */
private fun <I, O> Route.xrpcProcedure(
    method: XrpcProcedure<I, O>,
    verifier: ServiceAuthVerifier,
    handler: suspend (input: I, caller: Did?) -> O,
) {
    post("/xrpc/${method.nsid}") {
        val caller = call.viewer(verifier, method.nsid)
        val body = call.receiveText()
        val input = runCatching {
            AtJson.decodeFromString(method.input, body)
        }
            .getOrElse { e -> throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", e.message ?: "Invalid input", cause = e) }
        call.respondText(AtJson.encodeToString(method.output, handler(input, caller)), ContentType.Application.Json)
    }
}

private suspend fun ApplicationCall.viewer(verifier: ServiceAuthVerifier, nsid: String): Did? {
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    if (!header.startsWith("Bearer ")) throw AuthException("Expected a Bearer service-auth token")
    return verifier.verify(header.removePrefix("Bearer "), nsid)
}

suspend fun ApplicationCall.respondXrpcError(status: Int, error: String, message: String?) {
    respondText(
        AtJson.encodeToString(XrpcErrorBody.serializer(), XrpcErrorBody(error, message)),
        ContentType.Application.Json,
        HttpStatusCode.fromValue(status),
    )
}

/**
 * `did:web` document and OAuth client metadata for native apps. With a [signingKey], the document also publishes it as `#atproto`, which
 * PDSes use to verify the delegation tokens the AppView signs to sync athletes' spaces.
 */
fun Route.wellKnownRoutes(config: AppViewConfig, signingKey: AppViewSigningKey? = null) {
    get("/.well-known/did.json") { call.respondText(didDocument(config, signingKey).toString(), ContentType.Application.Json) }

    get("/oauth/client-metadata.json") {
        val metadata = buildJsonObject {
            put("client_id", "${config.publicUrl}/oauth/client-metadata.json")
            put("client_name", "Activity Tracker")
            put("client_uri", config.publicUrl)
            put("application_type", "native")
            putJsonArray("grant_types") {
                add("authorization_code")
                add("refresh_token")
            }
            putJsonArray("response_types") { add("code") }
            putJsonArray("redirect_uris") { add("${config.oauthRedirectScheme}:/oauth/callback") }
            put("scope", OAuthClientConfig.DEFAULT_SCOPE)
            put("token_endpoint_auth_method", "none")
            put("dpop_bound_access_tokens", true)
        }
        call.respondText(metadata.toString(), ContentType.Application.Json)
    }

    get("/xrpc/_health") {
        call.respondText(
            buildJsonObject {
                putJsonObject("status") { put("ok", true) }
            }
                .toString(),
            ContentType.Application.Json,
        )
    }
}

/** The AppView's `did:web` document: its services and, with a [signingKey], the `#atproto` key PDSes check its delegation tokens with. */
private fun didDocument(config: AppViewConfig, signingKey: AppViewSigningKey?): JsonObject = buildJsonObject {
    putJsonArray("@context") {
        add("https://www.w3.org/ns/did/v1")
        if (signingKey != null) add("https://w3id.org/security/multikey/v1")
    }
    put("id", config.serviceDid)
    if (signingKey != null) {
        putJsonArray("verificationMethod") {
            add(
                buildJsonObject {
                    put("id", "${config.serviceDid}#atproto")
                    put("type", "Multikey")
                    put("controller", config.serviceDid)
                    put("publicKeyMultibase", signingKey.publicMultibase)
                }
            )
        }
    }
    putJsonArray("service") {
        add(
            buildJsonObject {
                put("id", "#activity_appview")
                put("type", "ActivityTrackerAppView")
                put("serviceEndpoint", config.publicUrl)
            }
        )
        // Athletes' spaces name this service as their managingApp, so their PDSes call checkUserAccess here.
        add(
            buildJsonObject {
                put("id", "#${AppViewConfig.MANAGING_APP_FRAGMENT}")
                put("type", "AtprotoSpaceService")
                put("serviceEndpoint", config.publicUrl)
            }
        )
        // The AppView registers this id for athletes' tier spaces, so their PDSes send notifyWrite here.
        add(
            buildJsonObject {
                put("id", "#${AppViewConfig.SYNCER_FRAGMENT}")
                put("type", "AtprotoSpaceSyncer")
                put("serviceEndpoint", config.publicUrl)
            }
        )
    }
}
