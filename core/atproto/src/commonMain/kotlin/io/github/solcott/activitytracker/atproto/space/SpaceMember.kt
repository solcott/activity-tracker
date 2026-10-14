package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.xrpc.RequestAuthenticator
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

/**
 * Calls made as a member of a space, with its credential from [credentials]: reads of any repo in it (at that repo's PDS) and, for a
 * syncer, `registerNotify` and `listRepoOps` at the authority's. Each request is signed with the credential's binding key and addressed to
 * the repo it touches. A call the PDS answers with an expired or revoked credential is retried once with a new one.
 */
class SpaceMember(private val credentials: SpaceCredentials, private val http: HttpClient) {
    /** Runs [call] against [repo]'s PDS with [space]'s credential. XRPC errors other than a stale credential are thrown as they are. */
    suspend fun <T> call(space: SpaceUri, repo: Did, call: suspend (XrpcClient) -> T): T {
        var retried = false
        while (true) {
            val credential = credentials.get(space)
            val client =
                XrpcClient(http, credentials.pdsUrl(repo), CredentialAuthenticator(credential.token, repo, credentials.bindingKey()))
            try {
                return call(client)
            } catch (e: XrpcException) {
                if (retried || e.error !in STALE_CREDENTIAL) throw e
                credentials.invalidate(space)
                retried = true
            }
        }
    }

    /** `Authorization: Atproto-Space <credential>` for the repo owner [audience], signed with the key the credential binds. */
    private class CredentialAuthenticator(private val credential: String, private val audience: Did, private val key: DpopKey) :
        RequestAuthenticator {
        override suspend fun authenticate(request: HttpRequestBuilder, nsid: String) {
            val authorization = "Atproto-Space $credential"
            request.header(HttpHeaders.Authorization, authorization)
            request.header(HttpMessageSignature.AUDIENCE, audience.value)
            HttpMessageSignature.sign(key, authorization, audience).forEach { (name, value) -> request.header(name, value) }
        }

        override suspend fun shouldRetry(response: HttpResponse) = false
    }

    private companion object {
        val STALE_CREDENTIAL = setOf(SpaceErrors.JWT_EXPIRED, SpaceErrors.CREDENTIAL_REVOKED)
    }
}
