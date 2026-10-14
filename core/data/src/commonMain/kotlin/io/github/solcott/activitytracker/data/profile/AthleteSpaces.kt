package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.atproto.space.CreateSpaceResult
import io.github.solcott.activitytracker.atproto.space.SpaceClient
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.PrivateDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.atproto.SpaceAppAccess
import io.github.solcott.activitytracker.lexicon.atproto.SpacePolicy
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `app.activitytracker.space.athlete` tier spaces and the `actor.privateDetails` record each one holds. [ensure] creates one of the
 * signed-in athlete's tier spaces the first time a save needs it, with the AppView ([managingApp]) deciding who reads it and only the
 * athlete writing, and points an existing one at the current managing app (the local tunnel's DID changes between runs). Each tier is
 * ensured once per session; reads never create spaces.
 */
class AthleteSpaces(private val spaces: SpaceClient, private val managingApp: suspend () -> String) {
    private val mutex = Mutex()
    private val ensured = mutableMapOf<String, CompletableDeferred<SpaceUri>>()

    /** The athlete's space for [tier] ([AthleteSpace]), whether or not it exists. */
    fun uri(tier: String): SpaceUri = SpaceUri.of(spaces.did, Nsid.SPACE_ATHLETE, tier)

    /**
     * The details in [authority]'s [tier] space; empty when there are none, this athlete may not read them, or the record doesn't decode.
     * Any client can write the record, so a malformed one counts as no details, and the athlete's next save replaces it.
     */
    suspend fun details(authority: Did, tier: String): ProfileDetails =
        when (val read = spaces.getRecord(SpaceUri.of(authority, Nsid.SPACE_ATHLETE, tier), authority, COLLECTION, RKEY)) {
            is SpaceRead.Found ->
                try {
                    AtJson.decodeFromJsonElement(PrivateDetailsRecord.serializer(), read.value.value).details
                } catch (_: IllegalArgumentException) {
                    // SerializationException is an IllegalArgumentException.
                    ProfileDetails()
                }
            SpaceRead.NotFound,
            SpaceRead.NoAccess -> ProfileDetails()
        }

    /** The signed-in athlete's own details in each of [TIERS], read with their session. */
    suspend fun own(): Map<String, ProfileDetails> = TIERS.associateWith { tier -> details(spaces.did, tier) }

    /** Writes [details] as [tier]'s record, creating the tier's space if this is its first detail, and returns them. */
    suspend fun put(tier: String, details: ProfileDetails): ProfileDetails {
        val record = AtJson.encodeToJsonElement(PrivateDetailsRecord.serializer(), PrivateDetailsRecord(details))
        spaces.putRecord(ensure(tier), COLLECTION, RKEY, record)
        return details
    }

    /** Deletes [tier]'s record, whose space exists because it held details. */
    suspend fun delete(tier: String) {
        spaces.deleteRecord(uri(tier), COLLECTION, RKEY)
    }

    /**
     * Makes sure [tier]'s space exists with a current read policy. Concurrent callers share one attempt; a failed one is retried next time.
     */
    suspend fun ensure(tier: String): SpaceUri {
        var owner = false
        val pending = mutex.withLock { ensured.getOrPut(tier) { CompletableDeferred<SpaceUri>().also { owner = true } } }
        if (!owner) return pending.await()
        // Every failure, cancellation included, must release the callers waiting on this attempt.
        @Suppress("TooGenericExceptionCaught")
        try {
            return create(tier).also { pending.complete(it) }
        } catch (e: Throwable) {
            mutex.withLock { ensured.remove(tier) }
            pending.completeExceptionally(e)
            throw e
        }
    }

    private suspend fun create(tier: String): SpaceUri {
        val readPolicy = SpacePolicy.ManagingApp(managingApp())
        val result = spaces.createSpace(Nsid.SPACE_ATHLETE, tier, readPolicy, SpacePolicy.MemberList(), SpaceAppAccess.Open())
        if (result is CreateSpaceResult.AlreadyExists) {
            val current = spaces.getSpace(result.uri)
            if (current?.readPolicy != readPolicy) spaces.updateSpace(result.uri, readPolicy = readPolicy)
        }
        return result.uri
    }

    companion object {
        /** The tiers that hold details, most widely seen first; public ones live in the profile record. */
        val TIERS = listOf(AthleteSpace.MUTUALS, AthleteSpace.SELF)

        private const val COLLECTION = Nsid.ACTOR_PRIVATE_DETAILS
        private const val RKEY = PrivateDetailsRecord.RKEY
    }
}
