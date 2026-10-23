package io.github.solcott.activitytracker.atproto.space

import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.xrpc.RequestAuthenticator
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

/** A credentialed read whose MAC didn't verify, even after the author's DID document was resolved again. The response isn't used. */
class SpaceMacException(message: String) : Exception(message)

/**
 * Calls made as a member of a space, with its credential from [credentials]: reads of any repo in it (at that repo's PDS) and, for a
 * syncer, `registerNotify` and `listRepoOps` at the authority's. Each request is signed with its credential's key and addressed to the repo
 * it touches. A call the PDS answers with an expired or revoked credential is retried once with a new one.
 *
 * `getRecord` and `listRepoOps` carry MACs ([getRecord], [listRepoOps]): each request agrees a fresh key with the repo owner's
 * `#atproto_kem` key, and a response is returned only once its `Atproto-Space-Mac` verifies. A failed check re-resolves the owner's DID
 * document (the key may have rotated) and tries once more, then throws [SpaceMacException].
 */
class SpaceMember(private val credentials: SpaceCredentials, private val http: HttpClient) {
    /** Runs [call] against [repo]'s PDS with [space]'s credential. XRPC errors other than a stale credential are thrown as they are. */
    suspend fun <T> call(space: SpaceUri, repo: Did, call: suspend (XrpcClient) -> T): T {
        var retried = false
        while (true) {
            val credential = credentials.get(space)
            val client = XrpcClient(http, credentials.pdsUrl(repo), CredentialAuthenticator(credential.token, repo, credential.key))
            try {
                return call(client)
            } catch (e: XrpcException) {
                if (retried || e.error !in STALE_CREDENTIAL) throw e
                credentials.invalidate(space)
                retried = true
            }
        }
    }

    /** `getRecord` from [repo]'s copy of [space], returned only with a verified record MAC. */
    suspend fun getRecord(space: SpaceUri, repo: Did, collection: String, rkey: String): Space.GetRecord.Output {
        val uri = AtUri("${space.value}/${repo.value}/$collection/$rkey")
        return verified(repo, "record $uri") { kem ->
            val verifier = SpaceMac.forRecord(kem, uri)
            val response =
                call(space, repo) {
                    it.queryWithHeaders(
                        Space.GetRecord.method,
                        Space.GetRecord.Params(space, repo, collection, rkey),
                        mapOf(SpaceMac.WANT_MAC_HEADER to verifier.wantMacHeader),
                    )
                }
            val record = response.value
            val result =
                if (record.uri != uri) SpaceMacResult.Malformed("Response is for ${record.uri}")
                else verifier.verify(response.headers[SpaceMac.MAC_HEADER], record.cid)
            record to result
        }
    }

    /**
     * One `listRepoOps` page of [repo]'s copy of [space]. A page that carries a commit is returned only once its MAC verifies and it names
     * [space] and [repo]; one without a commit carries no MAC.
     */
    suspend fun listRepoOps(space: SpaceUri, repo: Did, params: Space.ListRepoOps.Params): Space.ListRepoOps.Output =
        verified(repo, "ops of $repo in $space") { kem ->
            val verifier = SpaceMac.forCommit(kem, space, repo)
            val response =
                call(space, repo) {
                    it.queryWithHeaders(Space.ListRepoOps.method, params, mapOf(SpaceMac.WANT_MAC_HEADER to verifier.wantMacHeader))
                }
            val page = response.value
            val commit = page.commit
            val result =
                when {
                    commit == null -> SpaceMacResult.Valid
                    commit.space != space || commit.repo != repo ->
                        SpaceMacResult.Malformed("Commit is for ${commit.repo} in ${commit.space}")
                    else -> verifier.verify(response.headers[SpaceMac.MAC_HEADER], commit.rev, commit.data.decode())
                }
            page to result
        }

    /** Runs [read] with [repo]'s KEM key, and once more with a freshly resolved one if its MAC doesn't verify. */
    private suspend fun <T> verified(repo: Did, what: String, read: suspend (kem: String) -> Pair<T, SpaceMacResult>): T {
        val (first, firstResult) = read(credentials.kemKey(repo))
        if (firstResult == SpaceMacResult.Valid) return first
        val (second, secondResult) = read(credentials.kemKey(repo, refresh = true))
        if (secondResult == SpaceMacResult.Valid) return second
        throw SpaceMacException("MAC check failed for $what: $secondResult")
    }

    /** `Authorization: Atproto-Space-Credential <credential>` for the repo owner [audience], signed with the key the credential binds. */
    private class CredentialAuthenticator(private val credential: String, private val audience: Did, private val key: DpopKey) :
        RequestAuthenticator {
        override suspend fun authenticate(request: HttpRequestBuilder, nsid: String) {
            val authorization = "$SCHEME $credential"
            request.header(HttpHeaders.Authorization, authorization)
            request.header(HttpMessageSignature.AUDIENCE, audience.value)
            HttpMessageSignature.sign(key, authorization, audience).forEach { (name, value) -> request.header(name, value) }
        }

        override suspend fun shouldRetry(response: HttpResponse) = false
    }

    private companion object {
        const val SCHEME = "Atproto-Space-Credential"
        val STALE_CREDENTIAL = setOf(SpaceErrors.JWT_EXPIRED, SpaceErrors.CREDENTIAL_REVOKED)
    }
}
