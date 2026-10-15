package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.ProfileDetailRules
import io.github.solcott.activitytracker.appview.db.Activities
import io.github.solcott.activitytracker.appview.db.ActivityMediaBlobs
import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import io.github.solcott.activitytracker.lexicon.isDid
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
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

/**
 * Activity photo URLs in views point at this AppView ([mediaRoutes]). A public activity's photo redirects to the blob on the athlete's PDS;
 * a non-public one's is signed for one viewer ([privateUrl]) and served from the athlete's tier space.
 */
class MediaUrls(private val publicUrl: String, private val tokens: PrivateMediaTokens? = null) {
    fun url(did: String, cid: String): String = "$publicUrl$PATH/$did/$cid"

    /** [viewer]'s URL to a photo of a non-public activity; null when the AppView can't serve those (no signing key). */
    fun privateUrl(did: String, cid: String, viewer: Did): String? = tokens?.let { t ->
        URLBuilder(url(did, cid)).apply { t.params(did, cid, viewer).forEach { (k, v) -> parameters.append(k, v) } }.buildString()
    }

    companion object {
        const val PATH = "/img/media"
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
fun Route.avatarRoutes(db: Database, pds: PdsLookup) =
    blobRedirect(AvatarUrls.PATH, pds) { did, cid -> db.tx { isCurrentAvatar(did, cid) } }

/**
 * `GET /img/media/{did}/{cid}`: a photo on one of the athlete's indexed activities. On a public activity it redirects like [avatarRoutes].
 * Otherwise the URL must be signed for a viewer ([MediaUrls.privateUrl]) who may still see the activity; the AppView then fetches the photo
 * from the athlete's tier space with its own credential and serves it as private, never redirecting to the PDS.
 */
fun Route.mediaRoutes(db: Database, pds: PdsLookup, spaceBlobs: SpaceBlobs? = null, tokens: PrivateMediaTokens? = null) {
    blobRedirect(MediaUrls.PATH, pds, private = { did, cid -> privateMedia(db, spaceBlobs, tokens, did, cid) }) { did, cid ->
        db.tx { isPublicActivityMedia(did, cid) }
    }
}

/** Serves a photo of a non-public activity if the request is signed for a viewer who may see it. True if it was served. */
private suspend fun RoutingContext.privateMedia(
    db: Database,
    blobs: SpaceBlobs?,
    tokens: PrivateMediaTokens?,
    did: String,
    cid: String,
): Boolean {
    val query = call.request.queryParameters
    val signed =
        tokens?.verify(did, cid, query[PrivateMediaTokens.VIEWER], query[PrivateMediaTokens.EXPIRES], query[PrivateMediaTokens.SIGNATURE])
    val origin = signed?.let { db.tx { visibleMediaOrigin(did, cid, it.viewer) } }
    val blob = origin?.let { runSuspendCatching { blobs?.fetch(SpaceUri(it), Did(did), cid) }.getOrNull() }
    if (signed != null && blob != null) {
        // Never cached past the URL's expiry.
        call.response.header(HttpHeaders.CacheControl, "private, max-age=${signed.expiresIn.inWholeSeconds}")
        call.respondBytes(blob.bytes, blob.contentType?.let(ContentType::parse) ?: ContentType.Application.OctetStream)
    }
    return blob != null
}

private fun Route.blobRedirect(
    path: String,
    pds: PdsLookup,
    private: suspend RoutingContext.(did: String, cid: String) -> Boolean = { _, _ -> false },
    isServed: suspend (did: String, cid: String) -> Boolean,
) {
    get("$path/{did}/{cid}") {
        val did = call.parameters["did"]?.takeIf { it.isDid() }
        val cid = call.parameters["cid"]?.takeIf(ProfileDetailRules::isCid)
        if (did == null || cid == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        if (!isServed(did, cid)) {
            if (!private(did, cid)) call.respond(HttpStatusCode.NotFound)
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

/** Whether [cid] is a photo on one of [did]'s public activities, which anyone may fetch from the PDS. */
private fun JdbcTransaction.isPublicActivityMedia(did: String, cid: String): Boolean =
    !mediaRows(did, cid) { Activities.visibility eq Visibility.PUBLIC }.empty()

/** The tier space holding a photo [cid] of [did]'s that [viewer] may see, if there is one. */
private fun JdbcTransaction.visibleMediaOrigin(did: String, cid: String, viewer: Did): String? =
    mediaRows(did, cid) { visibleTo(viewer) and (Activities.origin neq Origins.REPO) }.firstOrNull()?.get(Activities.origin)

private fun JdbcTransaction.mediaRows(did: String, cid: String, where: () -> Op<Boolean>) =
    ActivityMediaBlobs.join(Activities, JoinType.INNER, ActivityMediaBlobs.activityUri, Activities.uri)
        .select(Activities.origin)
        .where { (ActivityMediaBlobs.authorDid eq did) and (ActivityMediaBlobs.cid eq cid) and where() }
        .limit(1)
