@file:Suppress("TooManyFunctions") // A route group per area of the API, and the plumbing they share.

package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.AppViewConfig
import io.github.solcott.activitytracker.appview.auth.AppViewSigningKey
import io.github.solcott.activitytracker.appview.auth.AuthException
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.appview.moderation.ModeratorSubjects
import io.github.solcott.activitytracker.appview.sync.SpaceSyncer
import io.github.solcott.activitytracker.appview.sync.SyncRegistrations
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.XrpcErrorBody
import io.github.solcott.activitytracker.lexicon.XrpcParams
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSite
import io.github.solcott.activitytracker.lexicon.app.CheckDiveSites
import io.github.solcott.activitytracker.lexicon.app.CheckSegment
import io.github.solcott.activitytracker.lexicon.app.CheckSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.FinishSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvite
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvitees
import io.github.solcott.activitytracker.lexicon.app.GetActivityInvites
import io.github.solcott.activitytracker.lexicon.app.GetActivitySegments
import io.github.solcott.activitytracker.lexicon.app.GetActivityStreams
import io.github.solcott.activitytracker.lexicon.app.GetActorGear
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivitiesByStart
import io.github.solcott.activitytracker.lexicon.app.GetAuthorPosts
import io.github.solcott.activitytracker.lexicon.app.GetBlocks
import io.github.solcott.activitytracker.lexicon.app.GetComments
import io.github.solcott.activitytracker.lexicon.app.GetDiveSite
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteConditions
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteDives
import io.github.solcott.activitytracker.lexicon.app.GetDiveSiteRegulars
import io.github.solcott.activitytracker.lexicon.app.GetFollowRequests
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetGear
import io.github.solcott.activitytracker.lexicon.app.GetGearActivities
import io.github.solcott.activitytracker.lexicon.app.GetHandoffs
import io.github.solcott.activitytracker.lexicon.app.GetImportBatch
import io.github.solcott.activitytracker.lexicon.app.GetLikes
import io.github.solcott.activitytracker.lexicon.app.GetModerationRole
import io.github.solcott.activitytracker.lexicon.app.GetMutes
import io.github.solcott.activitytracker.lexicon.app.GetPost
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetQuotes
import io.github.solcott.activitytracker.lexicon.app.GetReposts
import io.github.solcott.activitytracker.lexicon.app.GetRiddenSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.GetSegment
import io.github.solcott.activitytracker.lexicon.app.GetSegmentEfforts
import io.github.solcott.activitytracker.lexicon.app.GetSegmentLeaderboard
import io.github.solcott.activitytracker.lexicon.app.GetSegmentMayors
import io.github.solcott.activitytracker.lexicon.app.GetSubject
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.github.solcott.activitytracker.lexicon.app.NotifySpace
import io.github.solcott.activitytracker.lexicon.app.PutSegmentCandidates
import io.github.solcott.activitytracker.lexicon.app.SearchActors
import io.github.solcott.activitytracker.lexicon.app.SearchDiveSites
import io.github.solcott.activitytracker.lexicon.app.SearchSegmentNames
import io.github.solcott.activitytracker.lexicon.app.SearchSegments
import io.github.solcott.activitytracker.lexicon.atproto.ManagedSpace
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUpdate
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.util.toMap
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
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
    invites: InviteViews,
    spaceAccess: SpaceAccess,
    verifier: ServiceAuthVerifier,
    managingAppVerifier: ServiceAuthVerifier,
) {
    xrpcQuery(GetTimeline.method, verifier) { params, viewer -> views.timeline(requireViewer(viewer), params) }
    xrpcQuery(GetAuthorActivities.method, verifier) { params, viewer -> views.authorActivities(viewer, params) }
    xrpcQuery(GetAuthorPosts.method, verifier) { params, viewer -> views.authorPosts(viewer, params) }
    xrpcQuery(GetPost.method, verifier) { params, viewer -> views.post(viewer, params) }
    xrpcQuery(GetAuthorActivitiesByStart.method, verifier) { params, viewer -> views.authorActivitiesByStart(viewer, params) }
    xrpcQuery(GetImportBatch.method, verifier) { params, viewer -> views.importBatch(viewer, params) }
    xrpcQuery(GetActivity.method, verifier) { params, viewer -> views.activity(viewer, params) }
    xrpcQuery(GetActivityStreams.method, verifier) { params, viewer -> streams.streams(viewer, params) }
    xrpcQuery(GetActivityInvites.method, verifier) { params, viewer -> invites.invites(requireViewer(viewer), params) }
    xrpcQuery(GetActivityInvite.method, verifier) { params, viewer -> invites.invite(requireViewer(viewer), params) }
    xrpcQuery(GetActivityInvitees.method, verifier) { params, viewer -> invites.invitees(requireViewer(viewer), params) }
    xrpcQuery(GetLikes.method, verifier) { params, viewer -> views.likes(viewer, params) }
    xrpcQuery(GetReposts.method, verifier) { params, viewer -> views.reposts(viewer, params) }
    xrpcQuery(GetQuotes.method, verifier) { params, viewer -> views.quotes(viewer, params) }
    xrpcQuery(GetComments.method, verifier) { params, viewer -> views.comments(viewer, params) }
    xrpcQuery(GetProfile.method, verifier) { params, viewer -> views.profile(viewer, params.actor) }
    xrpcQuery(GetGear.method, verifier) { params, viewer -> views.gear(viewer, params) }
    xrpcQuery(GetActorGear.method, verifier) { params, viewer -> views.actorGear(viewer, params) }
    xrpcQuery(GetGearActivities.method, verifier) { params, viewer -> views.gearActivities(viewer, params) }
    xrpcQuery(GetDiveSiteDives.method, verifier) { params, viewer -> views.diveSiteDives(viewer, params) }
    xrpcQuery(SearchActors.method, verifier) { params, viewer -> search.search(requireViewer(viewer), params) }
    xrpcQuery(GetFollowers.method, verifier) { params, viewer -> graph.followers(viewer, params) }
    xrpcQuery(GetFollows.method, verifier) { params, viewer -> graph.follows(viewer, params) }
    xrpcQuery(GetFollowRequests.method, verifier) { params, viewer -> graph.followRequests(requireViewer(viewer), params) }
    xrpcQuery(GetBlocks.method, verifier) { params, viewer -> graph.blocks(requireViewer(viewer), params) }
    xrpcQuery(GetMutes.method, verifier) { params, viewer -> graph.mutes(requireViewer(viewer), params) }
    // The managing-app callout from an athlete's PDS, whose token is addressed to the managing app rather than the AppView.
    xrpcQuery(ManagedSpace.CheckAccess.method, managingAppVerifier) { params, issuer -> spaceAccess.check(requireViewer(issuer), params) }
}

/** Segments and the efforts on them ([SegmentViews]). */
fun Route.segmentRoutes(segments: SegmentViews, verifier: ServiceAuthVerifier) {
    xrpcQuery(GetSegment.method, verifier) { params, viewer -> segments.segment(viewer, params) }
    xrpcQuery(SearchSegments.method, verifier) { params, viewer -> segments.search(viewer, params) }
    xrpcQuery(SearchSegmentNames.method, verifier) { params, viewer -> segments.searchNames(viewer, params) }
    xrpcQuery(GetActivitySegments.method, verifier) { params, viewer -> segments.activitySegments(viewer, params) }
    xrpcQuery(GetSegmentEfforts.method, verifier) { params, viewer -> segments.segmentEfforts(viewer, params) }
    xrpcQuery(GetSegmentLeaderboard.method, verifier) { params, viewer -> segments.leaderboard(viewer, params) }
    xrpcQuery(GetSegmentMayors.method, verifier) { params, viewer -> segments.mayors(viewer, params) }
    xrpcProcedure(CheckSegment.method, verifier) { input, caller -> segments.check(requireViewer(caller), input) }
}

/**
 * Dive sites and what's derived from their visits ([DiveSiteViews]; the dive-sites capabilities). `checkDiveSite` and the importer's
 * `checkDiveSites` write nothing.
 */
fun Route.diveSiteRoutes(sites: DiveSiteViews, verifier: ServiceAuthVerifier) {
    xrpcQuery(GetDiveSite.method, verifier) { params, viewer -> sites.site(viewer, params) }
    xrpcQuery(SearchDiveSites.method, verifier) { params, viewer -> sites.search(viewer, params) }
    xrpcQuery(GetDiveSiteConditions.method, verifier) { params, viewer -> sites.conditions(viewer, params) }
    xrpcQuery(GetDiveSiteRegulars.method, verifier) { params, viewer -> sites.regulars(viewer, params) }
    xrpcProcedure(CheckDiveSite.method, verifier) { input, caller -> sites.check(requireViewer(caller), input) }
    xrpcProcedure(CheckDiveSites.method, verifier) { input, caller -> sites.checkImported(requireViewer(caller), input) }
}

/**
 * The segment importer's candidate calls ([SegmentCandidateViews]; segments/segment-candidates): service auth from the configured importer
 * only. `put` and `finish` write to the AppView's own staging tables, never to a repo or space; `check` writes nothing.
 */
fun Route.segmentCandidateRoutes(candidates: SegmentCandidateViews, verifier: ServiceAuthVerifier) {
    xrpcProcedure(PutSegmentCandidates.method, verifier) { input, caller -> candidates.put(requireViewer(caller), input) }
    xrpcProcedure(FinishSegmentCandidates.method, verifier) { input, caller -> candidates.finish(requireViewer(caller), input) }
    xrpcQuery(GetRiddenSegmentCandidates.method, verifier) { params, viewer -> candidates.ridden(requireViewer(viewer), params) }
    xrpcProcedure(CheckSegmentCandidates.method, verifier) { input, caller -> candidates.check(requireViewer(caller), input) }
    xrpcQuery(GetHandoffs.method, verifier) { params, viewer -> candidates.handoffs(requireViewer(viewer), params) }
}

/**
 * Athletes' tier spaces, which the AppView syncs as a member ([SpaceSyncer] and its [SyncRegistrations]; both null when no signing key is
 * configured, and then nothing is joined):
 * - `notifySpace`: an athlete announces one of their tier spaces, with service auth for the AppView;
 * - `confirmRegistration`: before registering the AppView for a space's updates, the authority's PDS asks it to echo a challenge for the
 *   token its `authorizeSpaceSync` carried;
 * - `notifySpaceUpdate`: the authority's PDS says the space changed. Neither carries service auth: the token, which the AppView issued for
 *   that space alone, is what authenticates them, and any other is refused with a 400 (also how a syncer unregisters, by refusing an expiry
 *   notice). A write by the authority, a removal, a lapsed registration or a deletion notice makes the space due; only the sync's own
 *   `SpaceDeleted` answer drops it.
 */
fun Route.spaceSyncRoutes(syncer: SpaceSyncer?, registrations: SyncRegistrations?, verifier: ServiceAuthVerifier) {
    xrpcProcedure(NotifySpace.method, verifier) { input, caller ->
        syncer?.announce(announced(input.space, requireViewer(caller)))
        JsonObject(emptyMap())
    }
    unauthenticatedProcedure(Space.ConfirmRegistration.method) { input ->
        requireIssued(registrations, input.space, input.token)
        Space.ConfirmRegistration.Output(input.challenge)
    }
    unauthenticatedProcedure(Space.NotifySpaceUpdate.method) { input ->
        requireIssued(registrations, input.space, input.token)
        val space = input.space
        when (val update = input.update) {
            // Only the authority writes to an athlete's space; another repo's update has nothing for the AppView.
            is SpaceUpdate.RepoWrite -> if (update.repo == space.authority) syncer?.notified(space)
            is SpaceUpdate.RepoRemoved -> if (update.repo == space.authority) syncer?.notified(space)
            is SpaceUpdate.SpaceDeleted,
            is SpaceUpdate.RegistrationExpired -> syncer?.notified(space, reauthorize = true)
            is SpaceUpdate.Unknown -> Unit
        }
        JsonObject(emptyMap())
    }
}

/** A call back from a space host is answered only with the token this AppView registered for [space]. */
private suspend fun requireIssued(registrations: SyncRegistrations?, space: SpaceUri, token: String) {
    if (registrations?.issued(space, token) != true) {
        throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidToken", "Not a registration of this AppView for $space")
    }
}

/** [space], if [caller] may announce it: it must be one of their own athlete tier or interactions spaces. */
private fun announced(space: SpaceUri, caller: Did): SpaceUri {
    val error =
        when {
            space.authority != caller -> NotifySpace.NOT_SPACE_AUTHORITY to "Not your space: $space"
            !SpaceSyncer.isSyncable(space) -> NotifySpace.UNSUPPORTED_SPACE to "Not a syncable athlete space: $space"
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

/** The moderator routes (`moderation/private-subjects`); each needs service auth. */
fun Route.moderationRoutes(moderators: ModeratorSubjects, verifier: ServiceAuthVerifier) {
    xrpcQuery(GetSubject.method, verifier) { params, viewer -> moderators.subject(requireViewer(viewer), params.uri) }
    xrpcQuery(GetModerationRole.method, verifier) { _, viewer -> moderators.role(requireViewer(viewer)) }
}

/**
 * Serves a typed procedure, decoding its JSON input. Every procedure needs a caller, so one without is refused before its body is read, and
 * a body over [MAX_PROCEDURE_BODY_BYTES] (far past the importer's largest batch) is refused unread past the limit.
 */
private fun <I, O> Route.xrpcProcedure(
    method: XrpcProcedure<I, O>,
    verifier: ServiceAuthVerifier,
    handler: suspend (input: I, caller: Did) -> O,
) {
    post("/xrpc/${method.nsid}") {
        val caller = requireViewer(call.viewer(verifier, method.nsid))
        val input = call.procedureInput(method)
        call.respondText(AtJson.encodeToString(method.output, handler(input, caller)), ContentType.Application.Json)
    }
}

/**
 * Serves a procedure that carries no service auth, as a space host's calls to a syncer don't: the handler authenticates the input itself.
 */
private fun <I, O> Route.unauthenticatedProcedure(method: XrpcProcedure<I, O>, handler: suspend (input: I) -> O) {
    post("/xrpc/${method.nsid}") {
        val input = call.procedureInput(method)
        call.respondText(AtJson.encodeToString(method.output, handler(input)), ContentType.Application.Json)
    }
}

/** A procedure's input, refused when it's too large or doesn't decode. */
private suspend fun <I> ApplicationCall.procedureInput(method: XrpcProcedure<I, *>): I {
    val bytes = receiveChannel().readBuffer(MAX_PROCEDURE_BODY_BYTES + 1).readByteArray()
    if (bytes.size > MAX_PROCEDURE_BODY_BYTES) {
        throw XrpcError(HttpStatusCode.PayloadTooLarge.value, "PayloadTooLarge", "The input is too large")
    }
    return runCatching { AtJson.decodeFromString(method.input, bytes.decodeToString()) }
        .getOrElse { e -> throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", e.message ?: "Invalid input", cause = e) }
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
                },
            )
        }
    }
    putJsonArray("service") {
        add(
            buildJsonObject {
                put("id", "#activity_appview")
                put("type", "ActivityTrackerAppView")
                put("serviceEndpoint", config.publicUrl)
            },
        )
        // Athletes' spaces name this service as their managingApp, so their PDSes call checkAccess here.
        add(
            buildJsonObject {
                put("id", "#${AppViewConfig.MANAGING_APP_FRAGMENT}")
                put("type", "AtprotoSpaceService")
                put("serviceEndpoint", config.publicUrl)
            },
        )
    }
}

/** The most a procedure's input may be: 500 candidates of the longest lines `put` accepts fit with room to spare. */
private const val MAX_PROCEDURE_BODY_BYTES = 64L * 1024 * 1024
