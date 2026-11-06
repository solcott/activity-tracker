package io.github.solcott.activitytracker.app

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.headersOf

/**
 * The fake PDS's and AppView's answers that every test engine gives the same way ([ActivityTestBase], [SessionTestFixture]): a service-auth
 * token, and the AppView's DID document naming its space service. Null for any other [path], which the engine answers itself.
 */
internal fun MockRequestHandleScope.sharedTestRoute(path: String): HttpResponseData? {
    val json = headersOf("Content-Type", "application/json")
    return when (path) {
        "/xrpc/com.atproto.server.getServiceAuth" -> respond("""{"token":"service-jwt"}""", headers = json)
        "/.well-known/did.json" ->
            respond(
                """{"id":"did:web:appview.test","service":[
                   {"id":"#activitytracker_space","type":"AtprotoSpaceService","serviceEndpoint":"http://appview.test"}]}""",
                headers = json,
            )
        else -> null
    }
}
