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
 * lands on `/callback?code=…&state=…`; this hands the params to [AuthCoordinator.handleRedirect] and brings the app back to the front.
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
                val body = PAGE.encodeToByteArray()
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
        const val PAGE =
            "<!doctype html><title>Activity Tracker</title>" +
                "<p style=\"font-family:sans-serif\">Signed in. You can close this tab and return to Activity Tracker.</p>"
    }
}
