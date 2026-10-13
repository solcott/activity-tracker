package io.github.solcott.activitytracker.desktop

import com.sun.net.httpserver.HttpServer
import io.github.solcott.activitytracker.data.auth.AuthCoordinator
import io.github.solcott.activitytracker.data.auth.parseRedirectParams
import java.awt.Window
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Receives the OAuth redirect for the loopback client (`NetworkConfigs.desktop`), which only allows 127.0.0.1 redirect URIs. The browser
 * lands on `/callback?code=…&state=…` (or `?error=…` when sign-in failed); this hands the params to [AuthCoordinator.handleRedirect],
 * brings the app back to the front and answers the browser with a page saying how it went.
 */
class LoopbackRedirectServer(port: Int, authCoordinator: AuthCoordinator) {
    /** Set once the window exists, so a finished sign-in can raise it over the browser. */
    @Volatile var window: Window? = null

    private val scope = MainScope()
    private val server =
        HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0).apply {
            createContext(CALLBACK_PATH) { exchange ->
                val params = parseRedirectParams("http://127.0.0.1:$port${exchange.requestURI}")
                scope.launch {
                    authCoordinator.handleRedirect(params)
                    window?.toFront()
                }
                val body = redirectPage(params).encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(HTTP_OK, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }

    fun start() = server.start()

    fun stop() = server.stop(0)

    private companion object {
        const val CALLBACK_PATH = "/callback"
        const val HTTP_OK = 200
    }
}

/** The page the browser shows after the redirect: "Signed in", or the OAuth `error` (its `error_description` when present). */
internal fun redirectPage(params: Map<String, String>): String {
    val error = params["error"]
    val message =
        if (error == null) {
            "Signed in. You can close this tab and return to Activity Tracker."
        } else {
            val reason = params["error_description"]?.takeIf { it.isNotBlank() } ?: error
            "Sign-in failed: ${reason.escapeHtml()}. You can close this tab and return to Activity Tracker."
        }
    return "<!doctype html><title>Activity Tracker</title><p style=\"font-family:sans-serif\">$message</p>"
}

private fun String.escapeHtml(): String = buildString {
    for (c in this@escapeHtml) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
