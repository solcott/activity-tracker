package io.github.solcott.activitytracker.segmentimporter

import io.github.solcott.activitytracker.atproto.xrpc.RequestAuthenticator
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.XrpcErrorBody
import io.github.solcott.activitytracker.lexicon.atproto.CreateSession
import io.github.solcott.activitytracker.lexicon.atproto.RefreshSession
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A legacy PDS session for the importer's service account: signs in with `createSession` and an app password, sends the access JWT as
 * `Bearer`, and when the PDS says it expired, refreshes it, or signs in again if the refresh is refused. Only for this server tool: clients
 * sign in with OAuth.
 */
internal class PasswordSession(
    private val http: HttpClient,
    private val pdsUrl: String,
    private val identifier: String,
    private val password: String,
) : RequestAuthenticator {
    private val mutex = Mutex()
    private var session: CreateSession.Output? = null
    /** Set when the PDS rejected the access JWT, so the next request gets a fresh one. */
    private var rejected = false

    /** The signed-in account's DID, signing in first if needed. */
    suspend fun did(): Did = current().did

    override suspend fun authenticate(request: HttpRequestBuilder, nsid: String) {
        request.header(HttpHeaders.Authorization, "Bearer ${current().accessJwt}")
    }

    override suspend fun shouldRetry(response: HttpResponse): Boolean {
        val expired =
            response.status == HttpStatusCode.Unauthorized || (response.status == HttpStatusCode.BadRequest && errorOf(response) in EXPIRED)
        if (expired) mutex.withLock { rejected = true }
        return expired
    }

    private suspend fun current(): CreateSession.Output = mutex.withLock {
        val held = session
        val next =
            when {
                held == null -> signIn()
                rejected -> refresh(held) ?: signIn()
                else -> held
            }
        rejected = false
        session = next
        next
    }

    private suspend fun signIn(): CreateSession.Output {
        val response =
            http.post("$pdsUrl/xrpc/${CreateSession.method.nsid}") {
                contentType(ContentType.Application.Json)
                setBody(AtJson.encodeToString(CreateSession.Input.serializer(), CreateSession.Input(identifier, password)))
            }
        if (!response.status.isSuccess())
            throw SignInFailed("$identifier can't sign in at $pdsUrl: ${response.status} ${errorOf(response)}")
        return AtJson.decodeFromString(CreateSession.Output.serializer(), response.bodyAsText())
    }

    /** A refreshed session, or null when the PDS refused the refresh JWT. */
    private suspend fun refresh(held: CreateSession.Output): CreateSession.Output? {
        val response = http.post("$pdsUrl/xrpc/${RefreshSession.NSID}") { header(HttpHeaders.Authorization, "Bearer ${held.refreshJwt}") }
        if (!response.status.isSuccess()) return null
        return AtJson.decodeFromString(CreateSession.Output.serializer(), response.bodyAsText())
    }

    private suspend fun errorOf(response: HttpResponse): String? = runCatching {
        AtJson.decodeFromString(XrpcErrorBody.serializer(), response.bodyAsText()).error
    }
        .getOrNull()

    class SignInFailed(message: String) : Exception(message)

    private companion object {
        /** What a PDS answers a legacy session's expired or revoked access JWT with. */
        val EXPIRED = setOf("ExpiredToken", "InvalidToken")
    }
}
