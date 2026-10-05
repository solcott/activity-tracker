package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.isDid
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/** Avatar URLs in views point at this AppView, which redirects to the blob on the athlete's PDS ([avatarRoutes]). */
class AvatarUrls(private val publicUrl: String) {
    fun url(did: String, cid: String?): String? = cid?.let { "$publicUrl$PATH/$did/$it" }

    companion object {
        const val PATH = "/img/avatar"
    }
}

/** DID → PDS URL, cached for an hour so an account that moves PDS is picked up. */
fun interface PdsLookup {
    suspend fun pdsFor(did: Did): String?
}

class ResolvingPdsLookup(private val resolver: IdentityResolver, private val clock: Clock = Clock.System) : PdsLookup {
    private val cache = ConcurrentHashMap<Did, Pair<String, Instant>>()

    override suspend fun pdsFor(did: Did): String? =
        cache[did]?.takeIf { (_, at) -> clock.now() - at < TTL }?.first
            ?: runSuspendCatching { resolver.resolveDid(did).pdsEndpoint }
                .getOrNull()
                ?.trimEnd('/')
                ?.also { cache[did] = it to clock.now() }

    private companion object {
        val TTL = 1.hours
    }
}

/**
 * `GET /img/avatar/{did}/{cid}` redirects to `com.atproto.sync.getBlob` on the athlete's PDS. Only the athlete's current avatar is served,
 * so this can't be used to fetch arbitrary blobs. A CID names immutable content, so clients may cache the redirect.
 */
fun Route.avatarRoutes(db: Database, pds: PdsLookup) {
    get("${AvatarUrls.PATH}/{did}/{cid}") {
        val did = call.parameters["did"]?.takeIf { it.isDid() }
        val cid = call.parameters["cid"]?.takeIf(ProfileDetailRules::isCid)
        if (did == null || cid == null || !db.tx { isCurrentAvatar(did, cid) }) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val host = pds.pdsFor(Did(did))
        if (host == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        val target =
            URLBuilder(host)
                .apply {
                    appendPathSegments("xrpc", "com.atproto.sync.getBlob")
                    parameters.append("did", did)
                    parameters.append("cid", cid)
                }
                .buildString()
        call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
        call.respondRedirect(target, permanent = false)
    }
}

private fun JdbcTransaction.isCurrentAvatar(did: String, cid: String): Boolean =
    !Actors.select(Actors.did).where { (Actors.did eq did) and (Actors.avatarCid eq cid) }.empty()
