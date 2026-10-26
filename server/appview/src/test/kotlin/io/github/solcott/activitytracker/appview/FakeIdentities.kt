package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException

/**
 * DID documents and handle lookups for handle checks (identity/handle-verification), served by a [MockEngine]: the PLC directory at
 * `plc.test`, the handle resolver at `resolver.test`, DNS-over-HTTPS and every handle's `.well-known`.
 */
class FakeIdentities {
    /** DID → the handle its document claims, null for none. A DID that isn't here has a document that can't be fetched. */
    val claims = mutableMapOf<String, String?>()

    /** Handle → the DID it resolves to. A handle that isn't here resolves to nothing. */
    val handles = mutableMapOf<String, String>()

    /** Every handle lookup fails to connect. */
    var lookupsDown = false

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val client =
        HttpClient(
            MockEngine { request ->
                val url = request.url
                when {
                    url.host == "plc.test" -> {
                        val did = url.encodedPath.trimStart('/')
                        if (did !in claims) throw IOException("PLC unreachable")
                        val aka = claims[did]?.let { """"at://$it"""" }.orEmpty()
                        respond("""{"id":"$did","alsoKnownAs":[$aka]}""", headers = json)
                    }
                    lookupsDown -> throw IOException("lookups unreachable")
                    url.host == "resolver.test" ->
                        handles[url.parameters["handle"]]?.let { respond("""{"did":"$it"}""", headers = json) }
                            ?: respond(
                                """{"error":"InvalidRequest","message":"Unable to resolve handle"}""",
                                HttpStatusCode.BadRequest,
                                json,
                            )
                    url.host == "cloudflare-dns.com" -> respond("""{"Status":3}""", headers = json)
                    else -> respond("not found", HttpStatusCode.NotFound)
                }
            }
        ) {
            expectSuccess = false
        }

    val resolver = IdentityResolver(client, "http://plc.test", "http://resolver.test")
}
