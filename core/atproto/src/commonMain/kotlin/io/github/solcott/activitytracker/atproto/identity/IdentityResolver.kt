package io.github.solcott.activitytracker.atproto.identity

import io.github.solcott.activitytracker.atproto.AtprotoConfig
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcClient
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
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

    /**
     * The DID [handle] points at, from the first lookup that finds one. Throws [HandleNotFoundException] only when a lookup answered that
     * the handle has none, and otherwise rethrows the first lookup's transport failure: a device that couldn't ask is never told the handle
     * doesn't exist.
     */
    suspend fun resolveHandle(handle: Handle): Did {
        val lookups = mutableListOf<HandleLookup>()
        for (resolver in listOf(::resolveHandleXrpc, ::resolveHandleDns, ::resolveHandleWellKnown)) {
            val result = resolver(handle) ?: continue
            if (result is HandleLookup.Found) return result.did
            lookups += result
        }
        if (lookups.any { it is HandleLookup.Missing }) throw HandleNotFoundException(handle)
        throw lookups.filterIsInstance<HandleLookup.Unreachable>().first().cause
    }

    suspend fun resolveDid(did: Did): DidDocument {
        val response = http.get(didDocumentUrl(did))
        if (!response.status.isSuccess()) throw IdentityException("DID resolution failed for $did: ${response.status}")
        val doc = AtJson.decodeFromString(DidDocument.serializer(), response.bodyAsText())
        if (doc.id != did.value) throw IdentityException("DID document id mismatch for $did")
        return doc
    }

    /** Checks [did]'s syntax before building a URL from it, so a malformed DID is refused without a request. */
    private fun didDocumentUrl(did: Did): String =
        when {
            !isResolvableDid(did.value) -> throw IdentityException("Unsupported or malformed DID: $did")
            did.method == "plc" -> "${plcUrl.trimEnd('/')}/${did.value}"
            else -> didWebUrl(did)
        }

    /** The configured resolver: any XRPC error is its answer that the handle is missing. Null when no resolver is configured. */
    private suspend fun resolveHandleXrpc(handle: Handle): HandleLookup? {
        val url = handleResolverUrl ?: return null
        return runSuspendCatching { XrpcClient(http, url).query(ResolveHandle.method, ResolveHandle.Params(handle.value)).did }
            .fold(
                { HandleLookup.Found(it) },
                { if (it is XrpcException) HandleLookup.Missing else HandleLookup.Unreachable(it) },
            )
    }

    /** DNS-over-HTTPS for `_atproto.<handle>`: an answer without a DID means missing; a failed request means unreachable. */
    private suspend fun resolveHandleDns(handle: Handle): HandleLookup = lookup {
        val response =
            http.get("https://cloudflare-dns.com/dns-query") {
                url.parameters.append("name", "_atproto.${handle.value}")
                url.parameters.append("type", "TXT")
                header("Accept", "application/dns-json")
            }
        if (!response.status.isSuccess()) throw IdentityException("DNS-over-HTTPS answered ${response.status}")
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

    /**
     * `https://<handle>/.well-known/atproto-did`: any answer without a DID means missing. A failed request means unreachable, which on its
     * own isn't evidence of being offline, since an unknown domain fails the same way.
     */
    private suspend fun resolveHandleWellKnown(handle: Handle): HandleLookup = lookup {
        val response = http.get("https://${handle.value}/.well-known/atproto-did")
        if (!response.status.isSuccess()) return@lookup null
        response.bodyAsText().trim().takeIf { it.isDid() }?.let(::Did)
    }

    private inline fun lookup(block: () -> Did?): HandleLookup =
        runSuspendCatching(block).fold({ it?.let(HandleLookup::Found) ?: HandleLookup.Missing }, { HandleLookup.Unreachable(it) })

    /** One handle lookup's answer. */
    private sealed interface HandleLookup {
        data class Found(val did: Did) : HandleLookup

        data object Missing : HandleLookup

        data class Unreachable(val cause: Throwable) : HandleLookup
    }

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

open class IdentityException(message: String) : Exception(message)

/** Every lookup that answered said [handle] points at no DID. */
class HandleNotFoundException(val handle: Handle) : IdentityException("No account found for ${handle.value}")
