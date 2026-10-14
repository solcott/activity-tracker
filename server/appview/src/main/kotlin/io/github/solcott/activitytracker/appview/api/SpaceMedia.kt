package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.atproto.space.SpaceMember
import io.github.solcott.activitytracker.atproto.xrpc.XrpcBytes
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Blobs held in athletes' tier spaces, read with the AppView's own credential: photos and stream data of non-public activities. */
fun interface SpaceBlobs {
    /** The blob, or null when the space doesn't have it. Other failures throw. */
    suspend fun fetch(space: SpaceUri, repo: Did, cid: String): XrpcBytes?
}

/**
 * [SpaceBlobs] through `com.atproto.space.getBlob` as a member, refusing any blob over [maxBytes]. Blobs are content-addressed, so recent
 * ones are kept in memory, up to [cacheBytes] in all, and a photo in many viewers' feeds is fetched from the athlete's PDS once. Callers
 * decide who may see a blob before asking for it.
 */
class MemberSpaceBlobs(private val member: SpaceMember, private val maxBytes: Int = MAX_BYTES, private val cacheBytes: Long = CACHE_BYTES) :
    SpaceBlobs {
    private val cache = LinkedHashMap<String, XrpcBytes>(CACHE_ENTRIES, LOAD_FACTOR, true)
    private var cached = 0L

    override suspend fun fetch(space: SpaceUri, repo: Did, cid: String): XrpcBytes? {
        val key = "${repo.value} $cid"
        synchronized(cache) { cache[key] }
            ?.let {
                return it
            }
        val blob =
            try {
                member
                    .call(space, repo) { pds -> pds.queryBytes(Space.GetBlob.method, Space.GetBlob.Params(space, repo, Cid(cid))) }
                    .takeIf { it.bytes.size <= maxBytes }
            } catch (e: XrpcException) {
                if (e.error != SpaceErrors.BLOB_NOT_FOUND && e.error != SpaceErrors.SPACE_NOT_FOUND) throw e
                null
            }
        if (blob != null) remember(key, blob)
        return blob
    }

    private fun remember(key: String, blob: XrpcBytes) =
        synchronized(cache) {
            if (blob.bytes.size > cacheBytes || cache.containsKey(key)) return
            cache[key] = blob
            cached += blob.bytes.size
            val oldest = cache.entries.iterator()
            while (cached > cacheBytes && oldest.hasNext()) {
                cached -= oldest.next().value.bytes.size
                oldest.remove()
            }
        }

    private companion object {
        /** Above the largest photo or stream data blob the lexicons allow. */
        const val MAX_BYTES = 16 * 1024 * 1024
        const val CACHE_BYTES = 64L * 1024 * 1024
        const val CACHE_ENTRIES = 64
        const val LOAD_FACTOR = 0.75f
    }
}

/**
 * Signs the photo URLs of non-public activities for one viewer. Image loaders fetch photos without service auth, so the URL itself names
 * the viewer, and the media route checks the signature and then the viewer's access as of the request. Expiry is rounded up to the next
 * hour so a viewer's URLs stay stable (and cacheable on their device) within it. The key is derived from the AppView's signing key.
 */
class PrivateMediaTokens(signingKey: String, private val clock: Clock = Clock.System, private val ttl: Duration = 1.hours) {
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest("activitytracker-media-url:$signingKey".toByteArray()), ALG)

    /** Query parameters for [viewer]'s URL to [cid] of [did]. */
    fun params(did: String, cid: String, viewer: Did): List<Pair<String, String>> {
        val hour = 1.hours.inWholeSeconds
        val exp = ((clock.now() + ttl).epochSeconds / hour + 1) * hour
        return listOf(VIEWER to viewer.value, EXPIRES to exp.toString(), SIGNATURE to sign(did, cid, viewer.value, exp))
    }

    /** The viewer a URL with [viewer], [exp] and [sig] was signed for, if it's genuine and current. */
    fun verify(did: String, cid: String, viewer: String?, exp: String?, sig: String?): Did? {
        val expires = exp?.toLongOrNull()?.takeIf { it >= clock.now().epochSeconds }
        val genuine =
            viewer != null &&
                sig != null &&
                expires != null &&
                MessageDigest.isEqual(sign(did, cid, viewer, expires).toByteArray(), sig.toByteArray())
        return viewer?.takeIf { genuine }?.let(::Did)
    }

    private fun sign(did: String, cid: String, viewer: String, exp: Long): String {
        val mac = Mac.getInstance(ALG).apply { init(key) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("$did\n$cid\n$viewer\n$exp".toByteArray()))
    }

    companion object {
        const val VIEWER = "viewer"
        const val EXPIRES = "exp"
        const val SIGNATURE = "sig"
        private const val ALG = "HmacSHA256"
    }
}
