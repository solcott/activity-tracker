package io.github.solcott.activitytracker.appview.admin

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.solcott.activitytracker.appview.AppViewConfig
import io.github.solcott.activitytracker.atproto.OAuthClientConfig
import io.github.solcott.activitytracker.atproto.oauth.PendingAuthorization
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.request.host
import io.ktor.server.request.httpMethod
import io.ktor.server.request.port
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.uri
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.Duration as JavaDuration
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

/**
 * The AppView's admin site (admin/admin-site): sign-in through atproto OAuth for the admins [AppViewConfig.admins] lists, sessions, the
 * page shell, the audited [AdminRoutes.action], and the audit log page. [pages] are the operator pages later changes add.
 */
class AdminSite(
    private val config: AppViewConfig,
    db: Database,
    private val oauth: AdminOAuth,
    pages: List<AdminPage> = emptyList(),
    private val clock: Clock = Clock.System,
) {
    val sessions = AdminSessionStore(db, clock)
    val audit = AdminAudit(db, clock)

    /** Every page's summary, read for Home and the navigation's flags; lazily over [pages], which Home itself is one of. */
    val summaries = Summaries({ this.pages }, clock)

    /** Every page in navigation order: home, the operator pages, then the audit log. */
    val pages: List<AdminPage> = listOf(HomePage(summaries)) + pages + AuditPage(audit)

    /** Sign-ins between `/signin` and the callback, by OAuth `state`. Memory only: a restart mid-sign-in asks to start again. */
    private val pending: Cache<String, PendingSignIn> =
        Caffeine.newBuilder()
            .ticker { clock.now().toEpochMilliseconds() * NANOS_PER_MILLI }
            .expireAfterWrite(JavaDuration.ofMinutes(SIGN_IN_TTL.inWholeMinutes))
            .maximumSize(MAX_PENDING)
            .build()

    private class PendingSignIn(val authorization: PendingAuthorization, val client: OAuthClientConfig, val browserHash: String)

    /** Prunes expired sessions and old audit entries now and every [interval] until [scope] ends. */
    fun startMaintenance(scope: CoroutineScope, interval: Duration = 24.hours) {
        scope.launch {
            while (true) {
                runSuspendCatching { sessions.prune() }
                runSuspendCatching { audit.prune() }
                delay(interval)
            }
        }
    }

    /** Mounts the site under `/admin`. */
    fun install(parent: Route) {
        parent.route("/admin") {
            install(AdminHeaders)
            staticRoutes()
            signInRoutes()
            val routes = SiteRoutes(this)
            routes.get("nav") {
                // `current` is only matched against the pages' own paths, never echoed.
                val current = pages.firstOrNull { it.path == call.request.queryParameters["current"] }
                val flags = summaries.read().mapNotNull { s -> s.worst?.let { s.page.path to it } }.toMap()
                call.respondText(navFragment(pages, current, flags), ContentType.Text.Html)
            }
            pages.forEach { it.installOn(routes) }
        }
    }

    private fun Route.staticRoutes() {
        get("/static/htmx-$HTMX_VERSION.min.js") {
            call.respondResource("META-INF/resources/webjars/htmx.org/$HTMX_VERSION/dist/htmx.min.js", JS)
        }
        get("/static/admin-$CSS_VERSION.css") { call.respondResource("admin/admin.css", ContentType.Text.CSS) }
        get("/oauth/client-metadata.json") {
            if (!config.publicUrl.startsWith("https://")) return@get call.respondText("Not found", status = HttpStatusCode.NotFound)
            call.respondText(clientMetadata().toString(), ContentType.Application.Json)
        }
    }

    private fun Route.signInRoutes() {
        get("/signin") {
            if (call.redirectedToLoopbackIp()) return@get
            call.respondSignInPage()
        }
        post("/signin") {
            if (!call.sameOrigin()) return@post call.respondSignInPage("That request came from another site.", HttpStatusCode.Forbidden)
            val identifier = call.receiveParameters()["identifier"]?.trim().orEmpty()
            if (identifier.isEmpty()) return@post call.respondSignInPage("Enter a handle or DID.", HttpStatusCode.BadRequest)
            val client = clientFor(call)
            val request = runSuspendCatching {
                oauth.start(identifier, client)
            }
                .getOrElse {
                    log.info("Admin sign-in for {} couldn't start: {}", identifier, it.toString())
                    return@post call.respondSignInPage("Couldn't start signing in to that account.", HttpStatusCode.BadRequest)
                }
            // Refused before the account's server is ever involved.
            if (request.pending.expectedDid !in config.admins) return@post call.respondNotAdmin()
            val browser = randomSecret()
            pending.put(request.pending.state, PendingSignIn(request.pending, client, sha256Hex(browser)))
            call.response.cookies.append(cookie(call, SIGN_IN_COOKIE, browser, SIGN_IN_TTL, sameSite = "Lax"))
            call.respondRedirect(request.authorizeUrl)
        }
        get("/oauth/callback") { call.completeSignIn() }
        post("/signout") {
            val secret = call.request.cookies[SESSION_COOKIE]
            val session = secret?.let { sessions.find(it) }
            val token = call.receiveParameters()[CSRF_FIELD]
            if (session != null && call.sameOrigin() && sameSecret(token.orEmpty(), session.csrfToken)) sessions.delete(secret)
            call.response.cookies.append(cookie(call, SESSION_COOKIE, "", Duration.ZERO))
            call.respondRedirect("/admin/signin")
        }
    }

    private suspend fun ApplicationCall.completeSignIn() {
        val params = request.queryParameters.entries().associate { it.key to it.value.first() }
        val signIn = params["state"]?.let { state -> pending.getIfPresent(state)?.also { pending.invalidate(state) } }
        val browser = request.cookies[SIGN_IN_COOKIE]
        response.cookies.append(cookie(this, SIGN_IN_COOKIE, "", Duration.ZERO, sameSite = "Lax"))
        if (signIn == null || browser == null || !sameSecret(sha256Hex(browser), signIn.browserHash)) {
            respondSignInPage("That sign-in expired or was started in another browser. Start again.", HttpStatusCode.BadRequest)
        } else {
            runSuspendCatching { oauth.complete(signIn.client, signIn.authorization, params) }
                .onSuccess { did -> signedIn(did) }
                .onFailure {
                    log.info("Admin sign-in failed: {}", it.toString())
                    respondSignInPage("Signing in failed. Start again.", HttpStatusCode.BadRequest)
                }
        }
    }

    /** Starts [did]'s session once the authorization server has vouched for it, if it's an admin. */
    private suspend fun ApplicationCall.signedIn(did: Did) {
        if (did !in config.admins) return respondNotAdmin()
        response.cookies.append(cookie(this, SESSION_COOKIE, sessions.create(did), AdminSessionStore.LIFETIME))
        // A redirect would carry the cross-site navigation from the authorization server along, and the browser would withhold the
        // SameSite=Strict session cookie from it; a page that moves on itself starts a same-site navigation.
        respondHtmlPage(HttpStatusCode.OK, "Signed in", refreshTo = "/admin") { signedInBody() }
    }

    /** The admin [call] is for, or null once it has answered: sign in, or not an admin. */
    internal suspend fun adminOf(call: ApplicationCall): Admin? {
        val session = call.request.cookies[SESSION_COOKIE]?.let { sessions.find(it) }
        // The role configured now, so removing an admin or lowering their role takes effect at their next request.
        val role = session?.let { config.admins[it.did] }
        when {
            session == null && call.request.httpMethod.value == "GET" -> call.respondRedirect("/admin/signin")
            session == null -> call.respondText("Sign in again", status = HttpStatusCode.Unauthorized)
            role == null -> call.respondNotAdmin()
        }
        return if (session != null && role != null) Admin(session.did, role, session.csrfToken) else null
    }

    private inner class SiteRoutes(private val route: Route) : AdminRoutes {
        override fun get(path: String, body: suspend AdminGet.() -> Unit) {
            route.get(path) {
                if (call.redirectedToLoopbackIp()) return@get
                val admin = adminOf(call) ?: return@get
                PageCall(call, admin, pages, clock.now()).body()
            }
        }

        override fun action(path: String, name: String, body: suspend ActionCall.() -> ActionResult) {
            route.post(path) {
                val admin = adminOf(call) ?: return@post
                val form = call.receiveParameters()
                val target = form["target"]?.trim()?.takeIf { it.isNotEmpty() }
                val params = form.entries().filter { it.key != CSRF_FIELD && it.key != "target" }.associate { it.key to it.value.first() }
                val token = call.request.headers[CSRF_HEADER] ?: form[CSRF_FIELD]
                val refusal =
                    when {
                        !admin.isOperator -> "viewer"
                        !call.sameOrigin() -> "cross-origin"
                        token == null || !sameSecret(token, admin.csrfToken) -> "csrf"
                        else -> null
                    }
                var outcome = refusal?.let { "refused: $it" } ?: "failed: unknown"
                val action = ActionCall(call, admin, target, params)
                try {
                    if (refusal != null) return@post call.respondOutcome(HttpStatusCode.Forbidden, "Refused.")
                    val result = action.body()
                    if (result.refused != null) {
                        outcome = "refused: ${result.refused}"
                        call.respondOutcome(result.status, result.message)
                    } else {
                        outcome = result.detail?.let { "ok: $it" } ?: "ok"
                        call.respondOutcome(HttpStatusCode.OK, result.message)
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // Any failure is the action's outcome, recorded below; the request still gets an answer.
                    outcome = "failed: ${e::class.simpleName}"
                    log.warn("Admin action {} by {} failed", name, admin.did.value, e)
                    call.respondOutcome(HttpStatusCode.InternalServerError, "Failed: ${e::class.simpleName}")
                } finally {
                    runSuspendCatching { audit.record(admin.did.value, name, target, params + action.audited, outcome) }
                        .onFailure { log.error("Couldn't audit admin action {} by {}", name, admin.did.value, it) }
                }
            }
        }
    }

    /** The OAuth client a sign-in from [call] uses: a loopback client on a loopback host, else the hosted metadata at the public URL. */
    private fun clientFor(call: ApplicationCall): OAuthClientConfig =
        if (call.isLoopback()) {
            OAuthClientConfig.loopback("http://127.0.0.1:${call.request.port()}/admin/oauth/callback", AdminOAuth.SCOPE)
        } else {
            OAuthClientConfig("${config.publicUrl}$METADATA_PATH", "${config.publicUrl}/admin/oauth/callback", AdminOAuth.SCOPE)
        }

    private fun clientMetadata() = buildJsonObject {
        put("client_id", "${config.publicUrl}$METADATA_PATH")
        put("client_name", "Activity Tracker admin")
        put("client_uri", config.publicUrl)
        put("application_type", "web")
        putJsonArray("grant_types") { add("authorization_code") }
        putJsonArray("response_types") { add("code") }
        putJsonArray("redirect_uris") { add("${config.publicUrl}/admin/oauth/callback") }
        put("scope", AdminOAuth.SCOPE)
        put("token_endpoint_auth_method", "none")
        put("dpop_bound_access_tokens", true)
    }

    private fun cookie(call: ApplicationCall, name: String, value: String, maxAge: Duration, sameSite: String = "Strict") =
        Cookie(
            name = name,
            value = value,
            maxAge = maxAge.inWholeSeconds.toInt(),
            path = "/admin",
            secure = !call.isLoopback(),
            httpOnly = true,
            extensions = mapOf("SameSite" to sameSite),
        )

    companion object {
        /** The htmx WebJar's version in `gradle/libs.versions.toml`; a test fails when they differ. */
        const val HTMX_VERSION = "2.0.11"

        /** Bump with `admin/admin.css`, so browsers fetch the new one. */
        const val CSS_VERSION = "3"
        const val SESSION_COOKIE = "admin_session"
        const val SIGN_IN_COOKIE = "admin_signin"
        const val CSRF_FIELD = "_csrf"
        const val CSRF_HEADER = "X-CSRF-Token"
        const val METADATA_PATH = "/admin/oauth/client-metadata.json"
        private val SIGN_IN_TTL = 10.minutes
        private const val MAX_PENDING = 1_000L
        private const val NANOS_PER_MILLI = 1_000_000L
        private val JS = ContentType("text", "javascript")
        private val log = LoggerFactory.getLogger("AdminSite")
    }
}

/**
 * Every `/admin` response: no framing, scripts and styles from the AppView only, never cached, and no referrer to other sites. Not
 * `no-referrer`: Chrome then sends `Origin: null` on form posts, which [sameOrigin] refuses.
 */
private val AdminHeaders =
    createRouteScopedPlugin("AdminHeaders") {
        onCall { call ->
            call.response.headers.append("Content-Security-Policy", CSP)
            call.response.headers.append("X-Frame-Options", "DENY")
            call.response.headers.append("X-Content-Type-Options", "nosniff")
            call.response.headers.append("Referrer-Policy", "same-origin")
            call.response.headers.append(HttpHeaders.CacheControl, "no-store")
        }
    }

private const val CSP =
    "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; " +
        "form-action 'self' https: http://127.0.0.1:* http://localhost:*; base-uri 'none'; object-src 'none'"

private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]")

/** Reached on this machine directly, not through a tunnel or proxy: the only case the loopback OAuth client and plain cookies are for. */
internal fun ApplicationCall.isLoopback(): Boolean = request.host() in LOOPBACK_HOSTS && PROXY_HEADERS.none { request.headers[it] != null }

/** Headers a tunnel or proxy adds, which mean the request came from elsewhere though it reached a loopback address. */
private val PROXY_HEADERS = listOf(HttpHeaders.XForwardedFor, HttpHeaders.Forwarded, "CF-Connecting-IP")

/**
 * A loopback OAuth client's redirect is on `127.0.0.1`, and cookies set on `localhost` don't reach it, so a page asked for on `localhost`
 * moves there first. True when it answered.
 */
private suspend fun ApplicationCall.redirectedToLoopbackIp(): Boolean {
    if (!isLoopback() || request.host() != "localhost") return false
    respondRedirect("http://127.0.0.1:${request.port()}${request.uri}")
    return true
}

/** False for a request whose `Origin` names another site; no `Origin` passes, since `SameSite=Strict` and the CSRF token cover it. */
internal fun ApplicationCall.sameOrigin(): Boolean {
    val origin = request.headers[HttpHeaders.Origin] ?: return true
    val url = runCatching { Url(origin) }.getOrNull()?.takeIf { origin != "null" }
    val port = url?.specifiedPort?.takeIf { it != 0 }?.let { ":$it" }.orEmpty()
    return url != null && "${url.host}$port".equals(request.headers[HttpHeaders.Host], ignoreCase = true)
}

private suspend fun ApplicationCall.respondResource(path: String, type: ContentType) {
    val bytes = AdminSite::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
    if (bytes == null) respondText("Not found", status = HttpStatusCode.NotFound) else respondBytes(bytes, type)
}
