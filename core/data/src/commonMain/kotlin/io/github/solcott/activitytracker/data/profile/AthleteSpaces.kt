package io.github.solcott.activitytracker.data.profile

import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.space.CreateSpaceResult
import io.github.solcott.activitytracker.atproto.space.SpaceClient
import io.github.solcott.activitytracker.atproto.space.SpaceRead
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.FollowDeclineRecord
import io.github.solcott.activitytracker.lexicon.app.PrivateDetailsRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileDetails
import io.github.solcott.activitytracker.lexicon.atproto.SpacePolicy
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `app.activitytracker.space.athlete` tier spaces, the `actor.privateDetails` record each one holds, and the follow requests the athlete
 * declined (`graph.followDecline`, in their `self` space only). [ensure] creates one of the signed-in athlete's spaces (a tier, or
 * [AthleteSpace.INTERACTIONS] for their likes on non-public activities, which is never one of [TIERS]) the first time a write needs it,
 * with the AppView ([managingApp]) deciding who reads it and only the athlete writing, and points an existing one at the current managing
 * app (the local tunnel's DID changes between runs), then tells the AppView it exists ([announce], `notifySpace`) so it syncs what's in it.
 * Each tier is ensured once per session; reads never create spaces. A failed announcement doesn't fail the save: the next session announces
 * again.
 */
class AthleteSpaces(
    private val spaces: SpaceClient,
    private val managingApp: suspend () -> String,
    private val announce: suspend (SpaceUri) -> Unit = {},
) {
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

    /** Records, privately in the athlete's `self` space, that they declined [subject]'s follow request. */
    suspend fun putDecline(subject: Did, at: Instant) {
        val record = AtJson.encodeToJsonElement(FollowDeclineRecord.serializer(), FollowDeclineRecord(subject, at))
        spaces.putRecord(ensure(AthleteSpace.SELF), DECLINES, FollowDeclineRecord.rkey(subject), record)
    }

    /** Forgets the athlete's decline of [subject]. Their `self` space exists, since it holds the decline. */
    suspend fun deleteDecline(subject: Did) {
        spaces.deleteRecord(uri(AthleteSpace.SELF), DECLINES, FollowDeclineRecord.rkey(subject))
    }

    /** Everyone the signed-in athlete has declined; empty when their `self` space doesn't exist yet. A decline's rkey is its subject. */
    suspend fun declined(): Set<Did> =
        when (val read = spaces.listAllRecords(uri(AthleteSpace.SELF), spaces.did, DECLINES, PAGE_SIZE)) {
            is SpaceRead.Found -> read.value.mapNotNull { record -> record.rkey.takeIf { it.startsWith("did:") }?.let(::Did) }.toSet()
            SpaceRead.NotFound,
            SpaceRead.NoAccess -> emptySet()
        }

    /**
     * Makes sure the space for [tier] (or [AthleteSpace.INTERACTIONS]) exists with a current read policy. Concurrent callers share one
     * attempt; a failed one is retried next time.
     */
    suspend fun ensure(tier: String): SpaceUri {
        var owner = false
        val pending = mutex.withLock { ensured.getOrPut(tier) { CompletableDeferred<SpaceUri>().also { owner = true } } }
        if (!owner) return pending.await()
        // Every failure, cancellation included, must release the callers waiting on this attempt.
        @Suppress("TooGenericExceptionCaught")
        try {
            val uri = create(tier)
            runSuspendCatching { announce(uri) }
            return uri.also { pending.complete(it) }
        } catch (e: Throwable) {
            mutex.withLock { ensured.remove(tier) }
            pending.completeExceptionally(e)
            throw e
        }
    }

    /**
     * Creates [tier]'s space with the AppView as its managing app, or, when it exists, puts that policy back if it differs in any way (an
     * old tunnel's managing app, another policy type, published records), since `updateSpace` replaces the whole policy.
     */
    private suspend fun create(tier: String): SpaceUri {
        val policy = SpacePolicy.Managed(managingApp())
        val result = spaces.createSpace(Nsid.SPACE_ATHLETE, tier, policy)
        if (result is CreateSpaceResult.AlreadyExists) {
            val current = spaces.getSpace(result.uri)
            if (current?.policy != policy) spaces.updateSpace(result.uri, policy)
        }
        return result.uri
    }

    companion object {
        /** The tiers that hold details, most widely seen first; public ones live in the profile record. Never the interactions space. */
        val TIERS = listOf(AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS, AthleteSpace.SELF)

        private const val COLLECTION = Nsid.ACTOR_PRIVATE_DETAILS
        private const val DECLINES = Nsid.GRAPH_FOLLOW_DECLINE
        private const val PAGE_SIZE = 100
        private const val RKEY = PrivateDetailsRecord.RKEY
    }
}
