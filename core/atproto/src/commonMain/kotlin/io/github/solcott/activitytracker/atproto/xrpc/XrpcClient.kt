package io.github.solcott.activitytracker.atproto.xrpc

import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.XrpcErrorBody
import io.github.solcott.activitytracker.lexicon.XrpcParams
import io.github.solcott.activitytracker.lexicon.XrpcProcedure
import io.github.solcott.activitytracker.lexicon.XrpcQuery
import io.github.solcott.activitytracker.lexicon.atproto.BlobRef
import io.github.solcott.activitytracker.lexicon.atproto.UploadBlob
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer

/**
 * Minimal XRPC client. One instance talks to one service (a user's PDS or the AppView); auth is pluggable via [RequestAuthenticator] (DPoP
 * for the PDS, service-auth JWTs for the AppView).
 */
class XrpcClient(
    private val http: HttpClient,
    private val serviceUrl: () -> String,
    private val authenticator: RequestAuthenticator? = null,
    private val extraHeaders: Map<String, String> = emptyMap(),
) {
    constructor(
        http: HttpClient,
        serviceUrl: String,
        authenticator: RequestAuthenticator? = null,
    ) : this(http, { serviceUrl }, authenticator)

    suspend fun <P, O> query(method: XrpcQuery<P, O>, params: P): O {
        val response =
            execute(method.nsid, HttpMethod.Get) {
                XrpcParams.encode(method.params, params).forEach { (k, v) -> url.parameters.append(k, v) }
            }
        return decode(response, method.output)
    }

    suspend fun <I, O> procedure(method: XrpcProcedure<I, O>, input: I): O {
        val body = AtJson.encodeToString(method.input, input)
        val response =
            execute(method.nsid, HttpMethod.Post) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        return decode(response, method.output)
    }

    suspend fun uploadBlob(bytes: ByteArray, mimeType: String): BlobRef {
        val response =
            execute(UploadBlob.NSID, HttpMethod.Post) {
                header(HttpHeaders.ContentType, mimeType)
                setBody(bytes)
            }
        return decode(response, UploadBlob.Output.serializer()).blob
    }

    private suspend fun execute(
        nsid: String,
        method: HttpMethod,
        configure: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        var attempt = 0
        while (true) {
            val response = http.request {
                this.method = method
                url.takeFrom(URLBuilder(serviceUrl()).appendPathSegments("xrpc", nsid))
                extraHeaders.forEach { (k, v) -> header(k, v) }
                configure()
                authenticator?.authenticate(this, nsid)
            }
            if (response.status.isSuccess()) return response
            if (attempt++ < MAX_RETRIES && authenticator?.shouldRetry(response) == true) continue
            throw response.toXrpcException()
        }
    }

    private suspend fun <O> decode(response: HttpResponse, serializer: KSerializer<O>): O {
        val text = response.bodyAsText()
        return AtJson.decodeFromString(serializer, text.ifBlank { "{}" })
    }

    private companion object {
        /** One retry covers both a fresh DPoP nonce and a refreshed access token. */
        const val MAX_RETRIES = 2
    }
}

private fun URLBuilder.takeFrom(other: URLBuilder) {
    protocol = other.protocol
    host = other.host
    port = other.port
    pathSegments = other.pathSegments
}

/** Caps how much of a non-JSON error body ends up in an exception message. */
private const val MAX_ERROR_BODY_CHARS = 500

internal suspend fun HttpResponse.toXrpcException(): XrpcException {
    val text = runSuspendCatching { bodyAsText() }.getOrDefault("")
    val body = runCatching { AtJson.decodeFromString(XrpcErrorBody.serializer(), text) }.getOrNull()
    return XrpcException(status.value, body?.error ?: "HttpError", body?.message ?: text.take(MAX_ERROR_BODY_CHARS))
}

class XrpcException(val status: Int, val error: String, override val message: String?) : Exception("XRPC $status $error: $message")

/** Adds credentials to outgoing requests and decides whether a failed response is retryable. */
interface RequestAuthenticator {
    suspend fun authenticate(request: HttpRequestBuilder, nsid: String)

    /** Called for non-2xx responses; update state (nonce, tokens) and return true to retry. */
    suspend fun shouldRetry(response: HttpResponse): Boolean
}
