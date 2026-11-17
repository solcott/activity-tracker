package io.github.solcott.activitytracker.app.links

import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * A link's path as its decoded segments, with its query (ui/deep-links): the part every form of a link shares, whichever host or scheme it
 * came with. [path] writes it back, encoded.
 */
data class AppLink(val segments: List<String>, val query: Map<String, String> = emptyMap()) {
    /** The path and query, each segment and value encoded: `/profile/did:plc:abc/activity/3kx`, `/segment/…?board=run`. */
    val path: String
        get() {
            val url =
                URLBuilder(protocol = URLProtocol.HTTPS, host = PLACEHOLDER_HOST)
                    .apply {
                        pathSegments = segments
                        query.forEach { (name, value) -> parameters.append(name, value) }
                    }
                    .build()
            val path = url.encodedPath.ifEmpty { "/" }
            return if (url.encodedQuery.isEmpty()) path else "$path?${url.encodedQuery}"
        }

    override fun toString(): String = path

    companion object {
        private const val PLACEHOLDER_HOST = "link.invalid"

        fun of(vararg segments: String, query: Map<String, String?> = emptyMap()): AppLink =
            AppLink(segments.toList(), query.mapNotNull { (k, v) -> v?.let { k to it } }.toMap())
    }
}

/**
 * Which URLs are the app's links (ui/deep-links): https on [host] (the AppView's public host, which Android App Links and iOS Universal
 * Links verify; null when the build has none), the `activitytracker` scheme, whose URL host is the path's first segment, and a bare path
 * (the web app's own URL). Paths under `/oauth/`, `/xrpc/` and `/.well-known/` never are.
 */
class AppLinks(private val host: String?) {
    /** [url] as a link, or null when it isn't one. */
    @Suppress("ReturnCount") // Each way a URL fails to be a link returns null as soon as it shows.
    fun parse(url: String): AppLink? {
        val trimmed = url.trim()
        val parsed = runCatching { Url(if (trimmed.startsWith("/")) "https://$BARE_HOST$trimmed" else trimmed) }.getOrNull() ?: return null
        val segments =
            when {
                trimmed.startsWith("/") -> parsed.segments
                parsed.protocol.name == SCHEME -> listOfNotNull(parsed.host.takeIf { it.isNotEmpty() }) + parsed.segments
                parsed.protocol == URLProtocol.HTTPS && host != null && parsed.host.equals(host, ignoreCase = true) -> parsed.segments
                else -> return null
            }.filter { it.isNotEmpty() }
        if (segments.firstOrNull() in RESERVED) return null
        val query = parsed.parameters.names().associateWith { parsed.parameters[it].orEmpty() }
        return AppLink(segments, query)
    }

    companion object {
        /** The app's own scheme, the same on every build: `activitytracker://profile/alice.test`. */
        const val SCHEME = "activitytracker"

        private const val BARE_HOST = "bare.invalid"

        /** First segments that are the AppView's or sign-in's, never links. */
        val RESERVED = setOf("oauth", "xrpc", ".well-known")
    }
}
