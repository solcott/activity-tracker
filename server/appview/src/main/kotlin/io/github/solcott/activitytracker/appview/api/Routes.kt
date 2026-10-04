package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.AppViewConfig
import io.github.solcott.activitytracker.appview.auth.AuthException
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.XrpcErrorBody
import io.github.solcott.activitytracker.lexicon.XrpcParams
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import io.github.solcott.activitytracker.lexicon.app.GetAuthorActivities
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetProfile
import io.github.solcott.activitytracker.lexicon.app.GetTimeline
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.util.toMap
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

fun Route.xrpcRoutes(views: Views, graph: GraphViews, verifier: ServiceAuthVerifier) {
    xrpcQuery(GetTimeline.method, verifier) { params, viewer ->
        views.timeline(viewer ?: throw AuthException("Authentication required"), params)
    }
    xrpcQuery(GetAuthorActivities.method, verifier) { params, viewer -> views.authorActivities(viewer, params) }
    xrpcQuery(GetActivity.method, verifier) { params, viewer -> views.activity(viewer, params) }
    xrpcQuery(GetProfile.method, verifier) { params, viewer -> views.profile(viewer, params.actor) }
    xrpcQuery(GetFollowers.method, verifier) { params, _ -> graph.followers(params) }
    xrpcQuery(GetFollows.method, verifier) { params, _ -> graph.follows(params) }
}

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

/** `did:web` document and OAuth client metadata for native apps. */
fun Route.wellKnownRoutes(config: AppViewConfig) {
    get("/.well-known/did.json") {
        val doc = buildJsonObject {
            putJsonArray("@context") { add("https://www.w3.org/ns/did/v1") }
            put("id", config.serviceDid)
            putJsonArray("service") {
                add(
                    buildJsonObject {
                        put("id", "#activity_appview")
                        put("type", "ActivityTrackerAppView")
                        put("serviceEndpoint", config.publicUrl)
                    }
                )
            }
        }
        call.respondText(doc.toString(), ContentType.Application.Json)
    }

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
