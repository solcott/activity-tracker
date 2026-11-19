package io.github.solcott.activitytracker.appview.admin

import io.github.solcott.activitytracker.appview.db.AdminSessions
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.Did
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

/** A live admin session: whose it is and the CSRF token its actions must carry. */
data class AdminSession(val did: Did, val csrfToken: String)

/**
 * Admin site sessions (admin/admin-site). The cookie carries a random secret; the table keeps only its SHA-256, so a database read can't be
 * replayed as a session. Sessions last a fixed [LIFETIME], with no renewal, and survive restarts.
 */
class AdminSessionStore(private val db: Database, private val clock: Clock = Clock.System) {
    /** Starts a session for [did]; returns the cookie's secret. */
    suspend fun create(did: Did): String {
        val secret = randomSecret()
        val now = clock.now()
        db.tx {
            AdminSessions.insert {
                it[tokenHash] = sha256Hex(secret)
                it[AdminSessions.did] = did.value
                it[csrfToken] = randomSecret()
                it[createdAt] = now.toEpochMilliseconds()
                it[expiresAt] = (now + LIFETIME).toEpochMilliseconds()
            }
        }
        return secret
    }

    /** The live session [secret] opens, or null for an unknown or expired one. */
    suspend fun find(secret: String): AdminSession? {
        val now = clock.now().toEpochMilliseconds()
        return db.tx {
            AdminSessions.selectAll()
                .where { AdminSessions.tokenHash eq sha256Hex(secret) }
                .firstOrNull()
                ?.takeIf { it[AdminSessions.expiresAt] > now }
                ?.let { AdminSession(Did(it[AdminSessions.did]), it[AdminSessions.csrfToken]) }
        }
    }

    /** Ends the session [secret] opens, if any. */
    suspend fun delete(secret: String) {
        db.tx { AdminSessions.deleteWhere { tokenHash eq sha256Hex(secret) } }
    }

    /** Deletes expired sessions; returns how many. */
    suspend fun prune(): Int {
        val now = clock.now().toEpochMilliseconds()
        return db.tx { AdminSessions.deleteWhere { expiresAt less now } }
    }

    companion object {
        val LIFETIME = 12.hours
    }
}

private val random = SecureRandom()

/** 32 random bytes, base64url without padding. */
internal fun randomSecret(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(SECRET_BYTES).also(random::nextBytes))

private const val SECRET_BYTES = 32

internal fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray()).toHexString()

/** Compares secrets in time independent of where they differ. */
internal fun sameSecret(a: String, b: String): Boolean = MessageDigest.isEqual(a.encodeToByteArray(), b.encodeToByteArray())
