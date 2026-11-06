package io.github.solcott.activitytracker.data.auth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.db.AppDatabase
import io.github.solcott.activitytracker.data.db.KeyValueEntity
import io.github.solcott.activitytracker.lexicon.Did
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * What this device remembers about an account across sessions for leaving Activity Tracker (auth/leaving): which of the athlete's spaces it
 * has used, so it never creates one again after a leave elsewhere, and whether a leave started here is still running, so a restart resumes
 * it. Kept in `key_value` under the account's DID, since both outlive a session.
 */
@SingleIn(AppScope::class)
@Inject
class AccountMarks(private val db: AppDatabase) {
    /** The keys of [did]'s athlete spaces this device has used. */
    suspend fun knownSpaces(did: Did): Set<String> =
        db.keyValue().get(knownKey(did))?.split(SEPARATOR)?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    /** Remembers that this device used [did]'s space [skey]. */
    suspend fun rememberSpace(did: Did, skey: String) {
        val known = knownSpaces(did)
        if (skey in known) return
        db.keyValue().put(KeyValueEntity(knownKey(did), (known + skey).sorted().joinToString(SEPARATOR)))
    }

    /** Forgets [did]'s spaces, so a rejoin or a fresh start may create them again. */
    suspend fun forgetSpaces(did: Did) = db.keyValue().delete(knownKey(did))

    /** The step a leave of [did] started on this device has reached, or null when none is running. */
    suspend fun leaving(did: Did): String? = db.keyValue().get(leavingKey(did))

    /** [leaving], as it changes. */
    fun observeLeaving(did: Did): Flow<String?> = db.keyValue().observe(leavingKey(did)).map { it }

    suspend fun setLeaving(did: Did, step: String) = db.keyValue().put(KeyValueEntity(leavingKey(did), step))

    suspend fun clearLeaving(did: Did) = db.keyValue().delete(leavingKey(did))

    private companion object {
        const val SEPARATOR = ","

        fun knownKey(did: Did) = "spaces.known:${did.value}"

        fun leavingKey(did: Did) = "leaving:${did.value}"
    }
}
