package io.github.solcott.activitytracker.appview.sync

import io.github.solcott.activitytracker.appview.db.SpaceSync
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.atproto.Space
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/**
 * The AppView's notification registrations for the spaces it syncs: each `authorizeSpaceSync` carries [notifyFor]'s endpoint and token, and
 * the space host's calls back (`confirmRegistration`, `notifySpaceUpdate`) carry no service auth, only that token, which [issued] checks.
 * One random token per space, made on its first authorization and kept, so every reauthorization renews the same registration.
 */
class SyncRegistrations(
    private val db: Database,
    /** The AppView's public base URL, which the space host calls `{endpoint}/xrpc/…` on. */
    private val endpoint: String,
    private val random: SecureRandom = SecureRandom(),
) {
    /** The registration to carry when authorizing [space], or null for a space this AppView doesn't sync. */
    suspend fun notifyFor(space: SpaceUri): Space.AuthorizeSpaceSync.Notify? {
        val token = tokenOf(space) ?: return null
        return Space.AuthorizeSpaceSync.Notify(endpoint, token)
    }

    /** Whether [token] is the one this AppView issued for [space], compared in constant time. */
    suspend fun issued(space: SpaceUri, token: String): Boolean {
        val stored = db.tx { stored(space) } ?: return false
        return MessageDigest.isEqual(stored.encodeToByteArray(), token.encodeToByteArray())
    }

    /** [space]'s token, made now if it has none; null when the space isn't synced. */
    private suspend fun tokenOf(space: SpaceUri): String? = db.tx {
        stored(space)
            ?: run {
                val fresh = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(ByteArray(TOKEN_BYTES).also(random::nextBytes))
                // Only a row without one takes it, so two first authorizations racing agree on whichever landed.
                SpaceSync.update({ (SpaceSync.space eq space.value) and SpaceSync.notifyToken.isNull() }) { it[notifyToken] = fresh }
                stored(space)
            }
    }

    private fun stored(space: SpaceUri): String? =
        SpaceSync.select(SpaceSync.notifyToken).where { SpaceSync.space eq space.value }.singleOrNull()?.get(SpaceSync.notifyToken)

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
