package io.github.solcott.activitytracker.appview

import io.github.solcott.activitytracker.appview.admin.AdminRole
import io.github.solcott.activitytracker.appview.admin.AdminSite
import io.github.solcott.activitytracker.appview.admin.AuditFilter
import io.github.solcott.activitytracker.appview.auth.ServiceAuthVerifier
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The admin site's shell (admin/admin-site): who may use it, sign-in, sessions, actions and their audit, and its browser surface. */
class AdminSiteTest : AdminTestBase() {
    private suspend fun HttpClient.page(path: String, cookie: String): HttpResponse = get(path) { session(cookie) }

    private suspend fun HttpClient.act(
        path: String,
        cookie: String,
        csrf: String?,
        form: Map<String, String> = emptyMap(),
        origin: String? = null,
    ) =
        submitForm(path, Parameters.build { form.forEach { (k, v) -> append(k, v) } }) {
            session(cookie)
            csrf?.let { header(AdminSite.CSRF_HEADER, it) }
            origin?.let { header(HttpHeaders.Origin, it) }
        }

    private fun entries() = runBlocking { site().audit.page(AuditFilter(), null, 100).entries }

    // Who may use it

    @Test
    fun noAdminsMountsNothing() = testApplication {
        val verifier = ServiceAuthVerifier("did:web:localhost%3A8081", keys = { null })
        application { appView(AppViewConfig.fromEnv(emptyMap()), db, verifier, adminOAuth = oauth) }
        for (path in listOf("/admin", "/admin/signin", "/admin/audit")) assertEquals(HttpStatusCode.NotFound, client.get(path).status, path)
    }

    @Test
    fun adminSignsInAndSeesTheSite() = adminTest { http ->
        val cookie = assertNotNull(http.signIn("alice.test"))
        val home = http.page("/admin", cookie)
        assertEquals(HttpStatusCode.OK, home.status)
        val html = home.bodyAsText()
        assertTrue(alice in html && "operator" in html && "Sign out" in html)
        assertTrue("/admin/test" in html && "/admin/audit" in html, "navigation lists every page")
    }

    @Test
    fun nonAdminIsRefusedBeforeTheAuthorizationServer() = adminTest { http ->
        val start = http.submitForm("/admin/signin", Parameters.build { append("identifier", "carol.test") }) { loopbackHost() }
        assertEquals(HttpStatusCode.Forbidden, start.status)
        assertNull(start.headers[HttpHeaders.Location])
    }

    @Test
    fun accountVouchedForMustBeAnAdmin() = adminTest { http ->
        vouchFor = carol
        assertNull(http.signIn("alice.test"))
    }

    @Test
    fun tamperedOrReplayedCallbackIsRefused() = adminTest { http ->
        val start = http.submitForm("/admin/signin", Parameters.build { append("identifier", "alice.test") }) { loopbackHost() }
        val state = io.ktor.http.Url(start.headers[HttpHeaders.Location]!!).parameters["request_uri"]
        val browser = start.cookie("admin_signin")!!
        // Another browser, without the cookie that started it.
        val replay = http.get("/admin/oauth/callback?state=$state&code=c") { loopbackHost() }
        assertEquals(HttpStatusCode.BadRequest, replay.status)
        assertNull(replay.cookie("admin_session")?.takeIf { it.isNotEmpty() })
        // Taken once: the right browser can't use it after that either.
        val again =
            http.get("/admin/oauth/callback?state=$state&code=c") {
                loopbackHost()
                header(HttpHeaders.Cookie, "admin_signin=$browser")
            }
        assertEquals(HttpStatusCode.BadRequest, again.status)
        val unknown =
            http.get("/admin/oauth/callback?state=nope&code=c") {
                loopbackHost()
                header(HttpHeaders.Cookie, "admin_signin=$browser")
            }
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
    }

    @Test
    fun failedAuthorizationCreatesNoSession() = adminTest { http ->
        completeFails = true
        assertNull(http.signIn("alice.test"))
    }

    @Test
    fun expiredSignInIsRefused() = adminTest { http ->
        val start = http.submitForm("/admin/signin", Parameters.build { append("identifier", "alice.test") }) { loopbackHost() }
        val state = io.ktor.http.Url(start.headers[HttpHeaders.Location]!!).parameters["request_uri"]
        now += 11.minutes
        val late =
            http.get("/admin/oauth/callback?state=$state&code=c") {
                loopbackHost()
                header(HttpHeaders.Cookie, "admin_signin=${start.cookie("admin_signin")}")
            }
        assertEquals(HttpStatusCode.BadRequest, late.status)
    }

    @Test
    fun callbackPageMovesOnWithAStrictSessionCookie() = adminTest { http ->
        val start = http.submitForm("/admin/signin", Parameters.build { append("identifier", "alice.test") }) { loopbackHost() }
        val signInCookie = start.setCookie("admin_signin")!!
        assertTrue("SameSite=Lax" in signInCookie && "HttpOnly" in signInCookie && "Path=/admin" in signInCookie, signInCookie)
        val state = io.ktor.http.Url(start.headers[HttpHeaders.Location]!!).parameters["request_uri"]
        val done =
            http.get("/admin/oauth/callback?state=$state&code=c") {
                loopbackHost()
                header(HttpHeaders.Cookie, "admin_signin=${start.cookie("admin_signin")}")
            }
        assertEquals(HttpStatusCode.OK, done.status)
        assertTrue("http-equiv=\"refresh\"" in done.bodyAsText())
        val session = done.setCookie("admin_session")!!
        assertTrue("SameSite=Strict" in session && "HttpOnly" in session && "Path=/admin" in session, session)
        assertFalse("Secure" in session, "plain cookies only on a loopback host")
    }

    @Test
    fun cookiesAreSecureOffLoopback() =
        adminTest({ site("https://appview.example.com") }) { http ->
            val start =
                http.submitForm("/admin/signin", Parameters.build { append("identifier", "alice.test") }) {
                    header(HttpHeaders.Host, "appview.example.com")
                }
            assertTrue("Secure" in start.setCookie("admin_signin")!!)
        }

    // OAuth clients

    @Test
    fun loopbackHostUsesTheLoopbackClient() = adminTest { http ->
        http.signIn("alice.test")
        val client = clients.single()
        assertEquals("http://127.0.0.1:8081/admin/oauth/callback", client.redirectUri)
        assertTrue(client.clientId.startsWith("http://localhost?"), client.clientId)
        assertEquals("atproto", client.scope)
    }

    @Test
    fun publicHostUsesHostedMetadata() =
        adminTest({ site("https://appview.example.com") }) { http ->
            http.submitForm("/admin/signin", Parameters.build { append("identifier", "alice.test") }) {
                header(HttpHeaders.Host, "127.0.0.1:8081")
                // Through a tunnel: not this machine, though the Host is.
                header(HttpHeaders.XForwardedFor, "203.0.113.9")
            }
            val client = clients.single()
            assertEquals("https://appview.example.com/admin/oauth/client-metadata.json", client.clientId)
            assertEquals("https://appview.example.com/admin/oauth/callback", client.redirectUri)
        }

    @Test
    fun clientMetadataAskForIdentityOnly() =
        adminTest({ site("https://appview.example.com") }) { http ->
            val response = http.get("/admin/oauth/client-metadata.json")
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            val metadata = AtJson.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("https://appview.example.com/admin/oauth/client-metadata.json", metadata["client_id"]!!.jsonPrimitive.content)
            assertEquals("atproto", metadata["scope"]!!.jsonPrimitive.content)
            assertEquals("web", metadata["application_type"]!!.jsonPrimitive.content)
            assertEquals("none", metadata["token_endpoint_auth_method"]!!.jsonPrimitive.content)
            assertEquals("[\"https://appview.example.com/admin/oauth/callback\"]", metadata["redirect_uris"].toString())
        }

    @Test
    fun noClientMetadataWithoutHttps() = adminTest { http ->
        assertEquals(HttpStatusCode.NotFound, http.get("/admin/oauth/client-metadata.json").status)
    }

    @Test
    fun localhostMovesTo127() = adminTest { http ->
        val response = http.get("/admin/audit?x=1") { header(HttpHeaders.Host, "localhost:8081") }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("http://127.0.0.1:8081/admin/audit?x=1", response.headers[HttpHeaders.Location])
    }

    // Sessions and roles

    @Test
    fun signedOutPagesAskToSignIn() = adminTest { http ->
        val response = http.get("/admin/audit") { loopbackHost() }
        assertEquals(HttpStatusCode.Found, response.status)
        assertEquals("/admin/signin", response.headers[HttpHeaders.Location])
        assertEquals(HttpStatusCode.Unauthorized, http.act("/admin/test/poke", "nope", null).status)
    }

    @Test
    fun sessionExpiresAfterTwelveHours() = adminTest { http ->
        val cookie = http.signIn("alice.test")!!
        now += 11.hours
        assertEquals(HttpStatusCode.OK, http.page("/admin", cookie).status)
        now += 1.hours
        assertEquals(HttpStatusCode.Found, http.page("/admin", cookie).status)
    }

    @Test
    fun signOutEndsTheSession() = adminTest { http ->
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.page("/admin", cookie).bodyAsText())
        val out = http.submitForm("/admin/signout", Parameters.build { append(AdminSite.CSRF_FIELD, csrf) }) { session(cookie) }
        assertEquals(HttpStatusCode.Found, out.status)
        assertEquals(HttpStatusCode.Found, http.page("/admin", cookie).status)
    }

    @Test
    fun sessionSurvivesARestart() {
        var cookie: String? = null
        adminTest { http -> cookie = http.signIn("alice.test") }
        adminTest { http -> assertEquals(HttpStatusCode.OK, http.page("/admin", cookie!!).status) }
    }

    @Test
    fun removedAdminIsRefused() {
        var cookie: String? = null
        adminTest { http -> cookie = http.signIn("alice.test") }
        admins = mapOf(Did(bob) to AdminRole.Viewer)
        adminTest { http -> assertEquals(HttpStatusCode.Forbidden, http.page("/admin", cookie!!).status) }
    }

    @Test
    fun loweredRoleLoadsPagesButCantAct() {
        var cookie: String? = null
        adminTest { http -> cookie = http.signIn("alice.test") }
        admins = mapOf(Did(alice) to AdminRole.Viewer)
        adminTest { http ->
            val page = http.page("/admin", cookie!!)
            assertEquals(HttpStatusCode.OK, page.status)
            assertEquals(HttpStatusCode.Forbidden, http.act("/admin/test/poke", cookie!!, csrfOf(page.bodyAsText())).status)
        }
    }

    // Actions and their audit

    @Test
    fun operatorActionIsAudited() = adminTest { http ->
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.page("/admin", cookie).bodyAsText())
        val response = http.act("/admin/test/poke", cookie, csrf, mapOf("target" to "did:plc:x", "mode" to "fast"))
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue("poked did:plc:x" in response.bodyAsText())
        val entry = entries().single()
        assertEquals(listOf(alice, "test.poke", "did:plc:x", "ok"), listOf(entry.admin, entry.action, entry.target, entry.outcome))
        assertEquals(mapOf("mode" to "fast"), entry.params)
        assertEquals(now, entry.at)
    }

    @Test
    fun csrfFormFieldWorksToo() = adminTest { http ->
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.page("/admin", cookie).bodyAsText())
        assertEquals(HttpStatusCode.OK, http.act("/admin/test/poke", cookie, null, mapOf(AdminSite.CSRF_FIELD to csrf)).status)
        assertEquals(emptyMap(), entries().single().params, "the token isn't recorded")
    }

    @Test
    fun viewerActionIsRefusedAndRecorded() = adminTest { http ->
        val cookie = http.signIn("bob.test")!!
        val page = http.page("/admin", cookie)
        assertEquals(HttpStatusCode.OK, page.status)
        assertEquals(HttpStatusCode.Forbidden, http.act("/admin/test/poke", cookie, csrfOf(page.bodyAsText())).status)
        assertEquals("refused: viewer", entries().single().outcome)
    }

    @Test
    fun forgedActionsAreRefusedAndRecorded() = adminTest { http ->
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.page("/admin", cookie).bodyAsText())
        assertEquals(HttpStatusCode.Forbidden, http.act("/admin/test/poke", cookie, null).status)
        assertEquals(HttpStatusCode.Forbidden, http.act("/admin/test/poke", cookie, "wrong").status)
        assertEquals(HttpStatusCode.Forbidden, http.act("/admin/test/poke", cookie, csrf, origin = "https://evil.example").status)
        assertEquals(HttpStatusCode.OK, http.act("/admin/test/poke", cookie, csrf, origin = "http://127.0.0.1:8081").status)
        assertEquals(listOf("ok", "refused: cross-origin", "refused: csrf", "refused: csrf"), entries().map { it.outcome })
    }

    @Test
    fun actionDetailAndRefusalAreRecorded() = adminTest { http ->
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.page("/admin", cookie).bodyAsText())
        assertEquals(HttpStatusCode.OK, http.act("/admin/test/count", cookie, csrf).status)
        val refused = http.act("/admin/test/refuse", cookie, csrf)
        assertEquals(HttpStatusCode.UnprocessableEntity, refused.status)
        assertTrue("That's not valid." in refused.bodyAsText())
        assertEquals(HttpStatusCode.OK, http.act("/admin/test/poke", cookie, csrf).status)
        assertEquals(listOf("ok", "refused: invalid", "ok: 3 counted"), entries().map { it.outcome })
    }

    @Test
    fun failedActionIsRecorded() = adminTest { http ->
        val cookie = http.signIn("alice.test")!!
        val csrf = csrfOf(http.page("/admin", cookie).bodyAsText())
        assertEquals(HttpStatusCode.InternalServerError, http.act("/admin/test/boom", cookie, csrf).status)
        assertEquals("failed: IllegalStateException", entries().single().outcome)
    }

    @Test
    fun pruneDropsOldEntriesAndExpiredSessions(): Unit = runBlocking {
        val site = site()
        site.audit.record(alice, "old", null, emptyMap(), "ok")
        val stale = site.sessions.create(Did(alice))
        now += 396.days
        site.audit.record(alice, "new", null, emptyMap(), "ok")
        val fresh = site.sessions.create(Did(alice))
        assertEquals(1, site.audit.prune())
        assertEquals(1, site.sessions.prune())
        assertEquals(listOf("new"), site.audit.page(AuditFilter(), null, 10).entries.map { it.action })
        assertNull(site.sessions.find(stale))
        assertNotNull(site.sessions.find(fresh))
    }

    @Test
    fun keptAtLeastAYear(): Unit = runBlocking {
        val site = site()
        site.audit.record(alice, "year", null, emptyMap(), "ok")
        now += 366.days
        assertEquals(0, site.audit.prune())
    }

    // The audit page

    @Test
    fun viewerReadsTheAuditLogFilteredAndPaged() = adminTest { http ->
        runBlocking {
            val audit = site().audit
            repeat(55) { audit.record(alice, "a.$it", "did:plc:t", emptyMap(), "ok") }
            audit.record(carol, "c.only", null, emptyMap(), "ok")
        }
        val cookie = http.signIn("bob.test")!!
        val all = http.page("/admin/audit", cookie).bodyAsText()
        assertTrue("c.only" in all && "Older" in all)
        val filtered = http.page("/admin/audit?admin=$alice", cookie).bodyAsText()
        assertFalse("c.only" in filtered)
        val next = Regex("href=\"(/admin/audit\\?[^\"]*cursor=[^\"]+)\"").find(filtered)!!.groupValues[1].replace("&amp;", "&")
        val older = http.page(next, cookie).bodyAsText()
        assertTrue("a.0<" in older && "a.54<" !in older && "c.only" !in older, "page two keeps the filter")
    }

    // The browser surface

    @Test
    fun securityHeadersOnEveryAdminResponse() = adminTest { http ->
        val response = http.get("/admin/signin") { loopbackHost() }
        val csp = response.headers["Content-Security-Policy"].orEmpty()
        assertTrue("script-src 'self'" in csp && "frame-ancestors 'none'" in csp, csp)
        assertEquals("DENY", response.headers["X-Frame-Options"])
        assertEquals("same-origin", response.headers["Referrer-Policy"], "no-referrer would make Chrome post forms with Origin: null")
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
    }

    @Test
    fun servesHtmxAndStylesFromTheAppView() = adminTest { http ->
        val htmx = http.get("/admin/static/htmx-${AdminSite.HTMX_VERSION}.min.js")
        assertEquals(HttpStatusCode.OK, htmx.status)
        assertEquals("text/javascript", htmx.contentType()?.withoutParameters().toString())
        assertTrue("htmx" in htmx.bodyAsText())
        val css = http.get("/admin/static/admin-${AdminSite.CSS_VERSION}.css")
        assertEquals(HttpStatusCode.OK, css.status)
        assertEquals(ContentType.Text.CSS, css.contentType()?.withoutParameters())
    }

    @Test
    fun htmxVersionMatchesTheCatalog() {
        val catalog =
            generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "gradle/libs.versions.toml") }.first { it.isFile }
        assertTrue("htmx = \"${AdminSite.HTMX_VERSION}\"" in catalog.readText())
    }

    @Test
    fun corsStaysOffAdminButOnXrpc() = testApplication {
        val verifier = ServiceAuthVerifier("did:web:localhost%3A8081", keys = { null })
        application { appView(config(), db, verifier, adminOAuth = oauth) }
        val preflight =
            client.options("/xrpc/app.activitytracker.feed.getTimeline") {
                header(HttpHeaders.Origin, "http://web.example")
                header(HttpHeaders.AccessControlRequestMethod, "GET")
            }
        assertEquals("*", preflight.headers[HttpHeaders.AccessControlAllowOrigin])
        val health = client.get("/xrpc/_health") { header(HttpHeaders.Origin, "http://web.example") }
        assertEquals("*", health.headers[HttpHeaders.AccessControlAllowOrigin])
        val admin =
            client.get("/admin/signin") {
                header(HttpHeaders.Host, "127.0.0.1:8081")
                header(HttpHeaders.Origin, "http://web.example")
            }
        assertEquals(HttpStatusCode.OK, admin.status)
        assertNull(admin.headers[HttpHeaders.AccessControlAllowOrigin])
        val adminPreflight =
            client.options("/admin/test/poke") {
                header(HttpHeaders.Origin, "http://web.example")
                header(HttpHeaders.AccessControlRequestMethod, "POST")
            }
        assertNull(adminPreflight.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
