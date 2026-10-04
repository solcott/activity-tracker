package io.github.solcott.activitytracker.atproto.identity

import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.atproto.DidDocument
import io.github.solcott.activitytracker.lexicon.atproto.ResolveHandle
import io.github.solcott.activitytracker.lexicon.isDid
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ResolvedIdentity(
    val did: Did,
    /** Verified handle, or `handle.invalid` if the DID document doesn't claim it. */
    val handle: Handle,
    val pdsUrl: String,
    val document: DidDocument,
)

/** Resolves handles and DIDs to a DID document and PDS endpoint. */
class IdentityResolver(
    private val http: HttpClient,
    private val plcUrl: String,
    private val handleResolverUrl: String?,
) {
    constructor(
        http: HttpClient,
        config: AtprotoConfig,
    ) : this(http, config.plcUrl, config.handleResolverUrl)

    suspend fun resolve(identifier: String): ResolvedIdentity {
        val input = identifier.trim().removePrefix("@").removePrefix("at://")
        val did = if (input.isDid()) Did(input) else resolveHandle(Handle(input.lowercase()))
        val doc = resolveDid(did)
        val claimed = doc.handle
        val handle =
            when {
                input.isDid() -> claimed?.let(::Handle) ?: INVALID_HANDLE
                claimed.equals(input, ignoreCase = true) -> Handle(input.lowercase())
                else -> INVALID_HANDLE
            }
        val pds = doc.pdsEndpoint ?: throw IdentityException("$did has no #atproto_pds service")
        return ResolvedIdentity(did, handle, pds.trimEnd('/'), doc)
    }

    suspend fun resolveHandle(handle: Handle): Did =
        resolveHandleXrpc(handle)
            ?: resolveHandleDns(handle)
            ?: resolveHandleWellKnown(handle)
            ?: throw IdentityException("Could not resolve handle ${handle.value}")

    suspend fun resolveDid(did: Did): DidDocument {
        val response = http.get(didDocumentUrl(did))
        if (!response.status.isSuccess()) throw IdentityException("DID resolution failed for $did: ${response.status}")
        val doc = AtJson.decodeFromString(DidDocument.serializer(), response.bodyAsText())
        if (doc.id != did.value) throw IdentityException("DID document id mismatch for $did")
        return doc
    }

    private fun didDocumentUrl(did: Did): String =
        when (did.method) {
            "plc" -> "${plcUrl.trimEnd('/')}/${did.value}"
            "web" -> didWebUrl(did)
            else -> throw IdentityException("Unsupported DID method: ${did.method}")
        }

    private suspend fun resolveHandleXrpc(handle: Handle): Did? {
        val url = handleResolverUrl ?: return null
        return runSuspendCatching { XrpcClient(http, url).query(ResolveHandle.method, ResolveHandle.Params(handle.value)).did }.getOrNull()
    }

    private suspend fun resolveHandleDns(handle: Handle): Did? = runSuspendCatching {
        val response =
            http.get("https://cloudflare-dns.com/dns-query") {
                url.parameters.append("name", "_atproto.${handle.value}")
                url.parameters.append("type", "TXT")
                header("Accept", "application/dns-json")
            }
        if (!response.status.isSuccess()) return null
        val answers = AtJson.parseToJsonElement(response.bodyAsText()).jsonObject["Answer"]?.jsonArray.orEmpty()
        answers.firstNotNullOfOrNull { answer ->
            answer.jsonObject["data"]
                ?.jsonPrimitive
                ?.content
                ?.trim('"')
                ?.takeIf { it.startsWith("did=") }
                ?.let { Did(it.removePrefix("did=")) }
        }
    }
        .getOrNull()

    private suspend fun resolveHandleWellKnown(handle: Handle): Did? = runSuspendCatching {
        val response = http.get("https://${handle.value}/.well-known/atproto-did")
        if (!response.status.isSuccess()) return null
        response.bodyAsText().trim().takeIf { it.isDid() }?.let(::Did)
    }
        .getOrNull()

    companion object {
        val INVALID_HANDLE = Handle("handle.invalid")

        /** `did:web:host%3Aport` → `https://host:port/.well-known/did.json` (plain http for localhost). */
        fun didWebUrl(did: Did): String {
            val host = did.value.removePrefix("did:web:").replace("%3A", ":")
            require(!host.contains(':') || host.substringAfter(':').all(Char::isDigit)) { "did:web paths are not supported: $did" }
            val scheme = if (host.startsWith("localhost") || host.startsWith("127.0.0.1")) "http" else "https"
            return "$scheme://$host/.well-known/did.json"
        }
    }
}

class IdentityException(message: String) : Exception(message)
