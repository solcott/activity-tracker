package io.github.solcott.activitytracker.data.profile

import co.touchlab.kermit.Logger
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.atproto.xrpc.XrpcException
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.outcomeFlow
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.FollowApprovalRecord
import io.github.solcott.activitytracker.lexicon.app.GetFollowRequests
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.atproto.ApplyWrites
import io.github.solcott.activitytracker.lexicon.atproto.DeleteRecord
import io.github.solcott.activitytracker.lexicon.atproto.ListRecords
import io.github.solcott.activitytracker.lexicon.atproto.PutRecord
import io.github.solcott.activitytracker.lexicon.atproto.SpaceErrors
import io.github.solcott.dataresult.Outcome
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

/**
 * The signed-in athlete's answers to follow requests, while their `followPolicy` is approve. Approvals are public records in their repo,
 * keyed by the follower's DID; declines live only in their `self` tier space, so the AppView never learns of them and [followRequests]
 * splits them off itself. Turning approval on is a profile save ([ProfileRepository.save]).
 */
@SingleIn(SessionScope::class)
@Inject
class FollowRequestsRepository(private val clients: SessionClients, private val clock: Clock, logger: Logger) {
    private val logger = logger.withTag("FollowRequestsRepository")

    private val tiers = clients.tiers

    /**
     * Everyone asking to follow the signed-in athlete, split into [FollowRequests.pending] and the ones they [FollowRequests.declined].
     * Reads up to [MAX_PAGES] pages, since the AppView can't leave declines out (they're private to the athlete's own space).
     */
    suspend fun followRequests(): FollowRequests {
        val declined =
            try {
                tiers.declined()
            } catch (e: XrpcException) {
                if (e.error != SpaceErrors.SCOPE_MISSING) throw e
                null
            }
        val all = mutableListOf<ProfileViewBasic>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = clients.appView.query(GetFollowRequests.method, GetFollowRequests.Params(PAGE_SIZE, cursor))
            all += page.requests
            cursor = page.cursor?.takeIf { page.requests.isNotEmpty() }
        } while (cursor != null && ++pages < MAX_PAGES)
        val (declinedRequests, pending) = all.distinctBy { it.did }.partition { it.did in declined.orEmpty() }
        return FollowRequests(pending, declinedRequests, truncated = cursor != null, needsSignIn = declined == null)
    }

    /** [followRequests] as a stream ([outcomeFlow]): [FollowRequests.needsSignIn] stays on the answer, never a failure. */
    fun followRequestsOutcome(): Flow<Outcome<FollowRequests>> = outcomeFlow { followRequests() }

    /** Whether the athlete can approve or decline [did]: its records are keyed by the DID, which must be a valid record key. */
    fun canAnswer(did: Did): Boolean = isRecordKey(did)

    /** Approves [did]'s follow, and forgets the athlete's decline of them when they were [declined]. */
    suspend fun approve(did: Did, declined: Boolean = false) {
        clients.pds.procedure(
            PutRecord.method,
            PutRecord.Input(
                repo = clients.did,
                collection = Nsid.GRAPH_FOLLOW_APPROVAL,
                rkey = FollowApprovalRecord.rkey(did),
                record = AtJson.encodeToJsonElement(FollowApprovalRecord.serializer(), FollowApprovalRecord(did, clock.now())),
            ),
        )
        // The approval already wins: a follower no longer pending never shows as declined, so a failure here only leaves a stale record.
        if (declined) {
            runSuspendCatching { tiers.deleteDecline(did) }.onFailure { e -> logger.w(e) { "Couldn't clear a decline" } }
        }
    }

    /** Declines [did]'s follow request, privately: the decline lives only in the athlete's `self` space. */
    suspend fun decline(did: Did) {
        tiers.putDecline(did, clock.now())
    }

    /** Removes [did] from the athlete's followers by deleting their approval, so their follow waits for approval again. */
    suspend fun removeFollower(did: Did) {
        clients.pds.procedure(
            DeleteRecord.method,
            DeleteRecord.Input(clients.did, Nsid.GRAPH_FOLLOW_APPROVAL, FollowApprovalRecord.rkey(did)),
        )
    }

    private companion object {
        const val MAX_PAGES = 5
    }
}

/**
 * The signed-in athlete's follow requests. [truncated]: there were more than were read. [needsSignIn]: the session predates the decline
 * collection, so declines couldn't be read and everyone shows as [pending].
 */
data class FollowRequests(
    val pending: List<ProfileViewBasic>,
    val declined: List<ProfileViewBasic>,
    val truncated: Boolean = false,
    val needsSignIn: Boolean = false,
)

/** atproto's record key syntax. */
private val RKEY = Regex("^[A-Za-z0-9._:~-]{1,512}$")

private const val PAGE_SIZE = 100

private fun isRecordKey(did: Did): Boolean = RKEY.matches(did.value)

/**
 * Approves everyone who follows the athlete now, before their policy turns to approve: under an open policy every follow counts, so that's
 * all of their followers. Approvals that already exist are skipped, so a retry after a failure writes only the rest.
 */
internal suspend fun approveCurrentFollowers(clients: SessionClients, clock: Clock) {
    val current = mutableSetOf<Did>()
    var cursor: String? = null
    do {
        val page = clients.appView.query(GetFollowers.method, GetFollowers.Params(clients.did.value, cursor = cursor))
        page.followers.mapTo(current) { it.did }
        cursor = page.cursor?.takeIf { page.followers.isNotEmpty() }
    } while (cursor != null)
    val approved = mutableSetOf<String>()
    cursor = null
    do {
        val page =
            clients.pds.query(ListRecords.method, ListRecords.Params(clients.did.value, Nsid.GRAPH_FOLLOW_APPROVAL, PAGE_SIZE, cursor))
        page.records.mapNotNullTo(approved) { it.uri.rkey }
        cursor = page.cursor?.takeIf { page.records.isNotEmpty() }
    } while (cursor != null)
    val now = clock.now()
    current
        .filter { isRecordKey(it) && FollowApprovalRecord.rkey(it) !in approved }
        .chunked(ApplyWrites.MAX_WRITES)
        .forEach { chunk ->
            val writes = chunk.map { follower ->
                ApplyWrites.create(
                    Nsid.GRAPH_FOLLOW_APPROVAL,
                    FollowApprovalRecord.rkey(follower),
                    AtJson.encodeToJsonElement(FollowApprovalRecord.serializer(), FollowApprovalRecord(follower, now)),
                )
            }
            clients.pds.procedure(ApplyWrites.method, ApplyWrites.Input(clients.did, writes))
        }
}
