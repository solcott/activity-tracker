package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.lexicon.Did
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import kotlinx.html.FlowContent

/** The signed-in admin a request is for, with the role configured now and their session's CSRF token. */
data class Admin(val did: Did, val role: AdminRole, val csrfToken: String) {
    val isOperator: Boolean
        get() = role == AdminRole.Operator
}

/** What an action handler is given: the call, its admin, the `target` form field and the action's other fields. */
class ActionCall(val call: ApplicationCall, val admin: Admin, val target: String?, val params: Map<String, String>) {
    internal val audited = linkedMapOf<String, String>()

    /**
     * Adds [key] to the audit entry's settings beside the posted fields, such as the value an action replaced (identifiers and times only).
     */
    fun audit(key: String, value: String) {
        audited[key] = value
    }
}

/**
 * What an action reports back to the operator; [message] is shown as is, so it holds identifiers and counts only. [detail] (identifiers and
 * counts only) is recorded as the audit outcome `ok: <detail>`. A [refused] action changed nothing: it's recorded as `refused: <refused>`,
 * a short fixed reason, and answered [status].
 */
data class ActionResult(
    val message: String,
    val detail: String? = null,
    val refused: String? = null,
    val status: HttpStatusCode = HttpStatusCode.UnprocessableEntity,
) {
    companion object {
        fun refused(reason: String, message: String, status: HttpStatusCode = HttpStatusCode.UnprocessableEntity) =
            ActionResult(message, refused = reason, status = status)
    }
}

/**
 * A page of the admin site (admin/admin-site). Each later change adds its operator pages as one of these, in its own files, and lists it in
 * the AppView's wiring; the site builds its navigation from the list and draws every page in the same layout.
 */
interface AdminPage {
    /** The page's path under `/admin/`, with no leading slash. */
    val path: String

    /** Its name in the navigation and its heading. */
    val title: String

    /** Registers the page's routes. Paths given to [AdminRoutes] are relative to `/admin/`. */
    fun AdminRoutes.install()

    /**
     * Paths under `/admin/`, page and fragments, that show [subject] (a DID or record URI), for the privacy test: none of them may print a
     * record's content.
     */
    fun privacyProbes(subject: String): List<String> = listOf(path)
}

/** How a page registers routes: every route is admin-only, and every state change goes through [action], which audits it. */
interface AdminRoutes {
    /** A page or fragment any admin may load. */
    fun get(path: String, body: suspend AdminGet.() -> Unit)

    /**
     * A state change named [name], operators only: it's a POST that must carry the session's CSRF token, and it's recorded in the audit log
     * whatever its outcome, refusals included.
     */
    fun action(path: String, name: String, body: suspend ActionCall.() -> ActionResult)
}

/** A page or fragment request: draw a whole page with [page], or answer an htmx request with just a [fragment]. */
interface AdminGet {
    val call: ApplicationCall
    val admin: Admin

    suspend fun page(title: String, body: FlowContent.() -> Unit)

    suspend fun fragment(body: FlowContent.() -> Unit)
}

/** Registers [page]'s routes on [routes]. */
internal fun AdminPage.installOn(routes: AdminRoutes) = with(routes) { install() }
