package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.ActionResult
import io.github.solcott.activitytracker.appview.admin.AdminOAuth
import io.github.solcott.activitytracker.appview.admin.AdminPage
import io.github.solcott.activitytracker.appview.admin.AdminRole
import io.github.solcott.activitytracker.appview.admin.AdminRoutes
import io.github.solcott.activitytracker.appview.admin.AdminSite
import io.github.solcott.activitytracker.appview.db.DatabaseFactory
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.crypto.DpopKey
import io.github.solcott.activitytracker.atproto.oauth.AuthorizationRequest
import io.github.solcott.activitytracker.atproto.oauth.OAuthException
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.html.p
import org.h2.jdbcx.JdbcDataSource
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * An admin site over H2 with a fake [AdminOAuth]: [identities] maps what an admin types to their DID, and the authorization server vouches
 * for whichever DID the sign-in started with unless [vouchFor] says otherwise.
 */
open class AdminTestBase {
    protected val alice = "did:plc:alice"
    protected val bob = "did:plc:bob"
    protected val carol = "did:plc:carol"

    protected val db: Database =
        JdbcDataSource().run {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            DatabaseFactory.migrate(this)
            Database.connect(this)
        }

    protected var now: Instant = Instant.parse("2026-11-18T12:00:00Z")
    protected val clock =
        object : Clock {
            override fun now() = now
        }

    protected var admins = mapOf(Did(alice) to AdminRole.Operator, Did(bob) to AdminRole.Viewer)
    protected val identities = mutableMapOf("alice.test" to alice, "bob.test" to bob, "carol.test" to carol)

    /** The DID the fake authorization server vouches for; null vouches for the one the sign-in started with. */
    protected var vouchFor: String? = null
    protected var completeFails = false
    protected val clients = mutableListOf<OAuthClientConfig>()

    protected val oauth =
        object : AdminOAuth {
            override suspend fun start(identifier: String, client: OAuthClientConfig): AuthorizationRequest {
                clients += client
                val did = identities[identifier] ?: identifier.takeIf { it.startsWith("did:") } ?: throw OAuthException("unknown")
                val state = "state-${UUID.randomUUID()}"
                val pending =
                    PendingAuthorization(
                        state = state,
                        pkceVerifier = "verifier",
                        dpopKey = DpopKey.Serialized("der", "raw"),
                        issuer = "https://auth.test",
                        tokenEndpoint = "https://auth.test/token",
                        expectedDid = Did(did),
                    )
                return AuthorizationRequest("https://auth.test/authorize?request_uri=$state", pending)
            }

            override suspend fun complete(client: OAuthClientConfig, pending: PendingAuthorization, callback: Map<String, String>): Did {
                if (completeFails || callback["code"] == null) throw OAuthException("refused")
                return Did(vouchFor ?: checkNotNull(pending.expectedDid).value)
            }
        }

    protected fun config(publicUrl: String = "http://localhost:8081") =
        AppViewConfig.fromEnv(mapOf("APPVIEW_PUBLIC_URL" to publicUrl)).copy(admins = admins)

    /** An action page for tests: `poke` reports its target, `boom` throws. */
    protected val testPage =
        object : AdminPage {
            override val path = "test"
            override val title = "Test"

            override fun AdminRoutes.install() {
                get(path) { page(title) { p { +"test page for ${admin.did.value}" } } }
                action("test/poke", "test.poke") { ActionResult("poked ${target.orEmpty()}") }
                action("test/boom", "test.boom") { error("boom") }
            }
        }

    protected fun site(publicUrl: String = "http://localhost:8081", pages: List<AdminPage> = listOf(testPage)) =
        AdminSite(config(publicUrl), db, oauth, pages, clock)

    /** Runs [block] against [site] mounted alone, with a client that doesn't follow redirects. */
    protected fun adminTest(makeSite: () -> AdminSite = { site() }, block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            val built = makeSite()
            application { routing { built.install(this) } }
            block(createClient { followRedirects = false })
        }

    /** Signs in as the account [identifier] names from `127.0.0.1`; returns the session cookie, or null when refused. */
    protected suspend fun HttpClient.signIn(identifier: String): String? {
        val start = submitForm("/admin/signin", Parameters.build { append("identifier", identifier) }) { loopbackHost() }
        val state = start.headers[HttpHeaders.Location]?.let { Url(it).parameters["request_uri"] }
        val browser = start.cookie("admin_signin")
        if (state == null || browser == null) return null
        val done =
            get("/admin/oauth/callback?state=$state&code=c&iss=https://auth.test") {
                loopbackHost()
                header(HttpHeaders.Cookie, "admin_signin=$browser")
            }
        return done.cookie("admin_session")?.takeIf { it.isNotEmpty() }
    }

    protected fun io.ktor.client.request.HttpRequestBuilder.loopbackHost() = header(HttpHeaders.Host, "127.0.0.1:8081")

    protected fun io.ktor.client.request.HttpRequestBuilder.session(cookie: String) {
        loopbackHost()
        header(HttpHeaders.Cookie, "admin_session=$cookie")
    }

    protected fun HttpResponse.cookie(name: String): String? =
        headers.getAll(HttpHeaders.SetCookie).orEmpty().map(::parseServerSetCookieHeader).lastOrNull { it.name == name }?.value

    protected fun HttpResponse.setCookie(name: String): String? =
        headers.getAll(HttpHeaders.SetCookie).orEmpty().lastOrNull { it.startsWith("$name=") }

    /** The session's CSRF token, read from a page's `hx-headers`. */
    protected fun csrfOf(html: String): String =
        Regex("X-CSRF-Token&quot;:&quot;([^&]+)&quot;|X-CSRF-Token\":\"([^\"]+)\"").find(html)!!.groupValues.drop(1).first {
            it.isNotEmpty()
        }
}
