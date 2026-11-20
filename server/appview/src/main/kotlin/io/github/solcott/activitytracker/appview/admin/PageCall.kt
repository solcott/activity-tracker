package io.github.solcott.activitytracker.appview.admin

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.respondHtml
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlin.time.Instant
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.UL
import kotlinx.html.a
import kotlinx.html.aside
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.hiddenInput
import kotlinx.html.label
import kotlinx.html.lang
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.nav
import kotlinx.html.p
import kotlinx.html.script
import kotlinx.html.small
import kotlinx.html.span
import kotlinx.html.stream.createHTML
import kotlinx.html.textInput
import kotlinx.html.title
import kotlinx.html.ul

/** [AdminGet] for one request: pages share the layout, fragments are bare. */
internal class PageCall(
    override val call: ApplicationCall,
    override val admin: Admin,
    private val pages: List<AdminPage>,
    override val now: Instant,
) : AdminGet {
    override suspend fun page(title: String, body: FlowContent.() -> Unit) {
        val current = currentPage(pages, call.request.path())
        call.respondHtml { layout(title, admin, pages, current, body) }
    }

    override suspend fun fragment(body: FlowContent.() -> Unit) {
        call.respondText(createHTML().div { body() }, ContentType.Text.Html)
    }
}

/** The page [path] (a request path under `/admin`) belongs to: the one whose path is its first segment, Home for none. */
internal fun currentPage(pages: List<AdminPage>, path: String): AdminPage? {
    val first = path.removePrefix("/admin").trim('/').substringBefore('/')
    return pages.firstOrNull { it.path == first }
}

private fun HTML.document(
    title: String,
    refreshTo: String? = null,
    bodyAttributes: Map<String, String> = emptyMap(),
    main: FlowContent.() -> Unit,
) {
    lang = "en"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        // htmx would otherwise add a <style> the content security policy refuses, and never needs eval or inline scripts here.
        meta(
            name = "htmx-config",
            content =
                """{"includeIndicatorStyles":false,"allowEval":false,"allowScriptTags":false,""" +
                    """"responseHandling":[{"code":"204","swap":false},{"code":".*","swap":true}]}""",
        )
        refreshTo?.let {
            meta {
                attributes["http-equiv"] = "refresh"
                content = "0;url=$it"
            }
        }
        title("$title · AppView admin")
        link(rel = "stylesheet", href = "/admin/static/admin-${AdminSite.CSS_VERSION}.css")
        script(src = "/admin/static/htmx-${AdminSite.HTMX_VERSION}.min.js") { defer = true }
    }
    body {
        bodyAttributes.forEach { (k, v) -> attributes[k] = v }
        main()
    }
}

/**
 * The shared page (admin/admin-site): a sidebar with the site's name, the navigation to every page with the current one marked, who is
 * signed in with which role and sign-out; then the page. The navigation's problem flags load after the page, from `/admin/nav`, so no page
 * waits on any summary; without them the list drawn here stays.
 */
private fun HTML.layout(title: String, admin: Admin, pages: List<AdminPage>, current: AdminPage?, content: FlowContent.() -> Unit) =
    document(title, bodyAttributes = mapOf("hx-headers" to """{"${AdminSite.CSRF_HEADER}":"${admin.csrfToken}"}""")) {
        div("shell") {
            aside("sidebar") {
                a(href = "/admin", classes = "brand") {
                    +"AppView"
                    small { +"admin" }
                }
                nav("nav") {
                    attributes["aria-label"] = "Admin pages"
                    navList(pages, current)
                }
                div("who") {
                    span { +admin.did.value }
                    span("role") { +admin.role.id }
                    form(action = "/admin/signout", method = kotlinx.html.FormMethod.post) {
                        hiddenInput(name = AdminSite.CSRF_FIELD) { value = admin.csrfToken }
                        button { +"Sign out" }
                    }
                }
            }
            main {
                div("page-head") { h1 { +title } }
                content()
            }
        }
    }

/** The navigation's list, drawn with the page: every page with [current] marked; it asks `/admin/nav` for its flags once it loads. */
private fun FlowContent.navList(pages: List<AdminPage>, current: AdminPage?) {
    ul {
        attributes["hx-get"] = "/admin/nav?current=${current?.path.orEmpty().encodeURLParameter()}"
        attributes["hx-trigger"] = "load"
        attributes["hx-swap"] = "outerHTML"
        navItems(pages, current, flags = emptyMap())
    }
}

/** The `/admin/nav` fragment: the same list with a flag on each page whose summary has a `warn` or `bad` tile, by page path. */
internal fun navFragment(pages: List<AdminPage>, current: AdminPage?, flags: Map<String, TileStatus>): String =
    createHTML().ul { navItems(pages, current, flags) }

/** Every page, [current] marked for sight and assistive technology, and a flag where [flags] has one. */
private fun UL.navItems(pages: List<AdminPage>, current: AdminPage?, flags: Map<String, TileStatus>) {
    pages.forEach { page ->
        li {
            a(href = "/admin/${page.path}".trimEnd('/')) {
                if (page == current) attributes["aria-current"] = "page"
                span { +page.title }
                flags[page.path]?.let { status ->
                    val text = if (status == TileStatus.Bad) "needs attention now" else "needs attention"
                    span("flag ${status.css}") {
                        attributes["role"] = "img"
                        attributes["aria-label"] = text
                        attributes["title"] = text
                    }
                }
            }
        }
    }
}

internal suspend fun ApplicationCall.respondHtmlPage(
    status: HttpStatusCode,
    title: String,
    refreshTo: String? = null,
    body: FlowContent.() -> Unit,
) =
    respondHtml(status) {
        document(title, refreshTo) {
            div("card-page") {
                main("card") {
                    h1 { +title }
                    body()
                }
            }
        }
    }

internal suspend fun ApplicationCall.respondSignInPage(error: String? = null, status: HttpStatusCode = HttpStatusCode.OK) =
    respondHtmlPage(status, "Sign in") {
        p("muted") { +"AppView admin. Sign in with your atproto account." }
        error?.let { p("error") { +it } }
        form(action = "/admin/signin", method = kotlinx.html.FormMethod.post) {
            label {
                +"Handle or DID "
                textInput(name = "identifier") {
                    required = true
                    attributes["autocomplete"] = "username"
                }
            }
            button { +"Sign in" }
        }
    }

internal suspend fun ApplicationCall.respondNotAdmin() =
    respondHtmlPage(HttpStatusCode.Forbidden, "Not an admin") {
        p { +"This account isn't an admin of this AppView." }
        form(action = "/admin/signout", method = kotlinx.html.FormMethod.post) { button { +"Sign in as someone else" } }
    }

internal fun FlowContent.signedInBody() {
    p { a(href = "/admin") { +"Continue" } }
}

/** An action's answer, for htmx to put in place: [message] holds identifiers and counts only. */
internal suspend fun ApplicationCall.respondOutcome(status: HttpStatusCode, message: String) =
    respondText(
        createHTML().p(if (status == HttpStatusCode.OK) "outcome ok" else "outcome error") { +message },
        ContentType.Text.Html,
        status,
    )
