package io.github.solcott.activitytracker.data.segments

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.requireInteractionsFor
import io.github.solcott.activitytracker.data.activity.undoingOnFailure
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The athlete's part in naming segments (segments/segment-names): proposing a name (a public `feed.segmentNameProposal` in their repo),
 * editing or deleting their own open proposal (the same rkey), and voting on others' (a `feed.segmentNameVote` in their `interactions`
 * space, one rkey per proposal, rewritten to change it and deleted to take it back). Writes go through the outbox and show at once through
 * [changes], which [applyTo] lays over the AppView's view until it catches up, so a refresh can't undo them. Voting is refused before
 * anything changes for a session that can't write to its interactions space ([canVote]).
 */
@SingleIn(SessionScope::class)
@Inject
class SegmentNames(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** What the athlete changed this session, by segment URI. */
    val changes: StateFlow<Map<String, SegmentNameChanges>>
        field = MutableStateFlow(emptyMap())

    /** The signed-in athlete, whose own proposals they may edit or delete but not vote on. */
    val self: Did
        get() = clients.did

    /** Whether this session can vote: its PDS holds spaces and its `space:` grant covers votes. */
    val canVote: Boolean
        get() = clients.canWriteToSpaces(Nsid.FEED_SEGMENT_NAME_VOTE)

    /** Proposes [name] for [segment], and returns the proposal's URI. */
    suspend fun propose(segment: AtUri, name: String): AtUri {
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_SEGMENT_NAME_PROPOSAL, rkey)
        val now = clock.now()
        val sent =
            SegmentNameProposalView(
                uri = uri,
                cid = Cid(PENDING_CID),
                name = name,
                proposer = ProfileViewBasic(clients.did, clients.handle),
                openedAt = now,
                up = 0,
                down = 0,
            )
        write(segment, { it.copy(sent = sent) }) {
            writer.put(
                Nsid.FEED_SEGMENT_NAME_PROPOSAL,
                rkey,
                SegmentNameProposalRecord.serializer(),
                SegmentNameProposalRecord(segment, name, now),
            )
        }
        return uri
    }

    /** Renames the athlete's open [proposal] on [segment] to [name], which clears its votes. */
    suspend fun edit(segment: AtUri, proposal: AtUri, name: String) {
        val now = clock.now()
        write(segment, { it.withProposal(proposal) { change -> change.copy(name = name, editedAt = now) } }) {
            writer.put(
                Nsid.FEED_SEGMENT_NAME_PROPOSAL,
                requireNotNull(proposal.rkey),
                SegmentNameProposalRecord.serializer(),
                SegmentNameProposalRecord(segment, name, now),
            )
        }
    }

    /** Deletes the athlete's open [proposal] on [segment], which withdraws it. */
    suspend fun delete(segment: AtUri, proposal: AtUri) {
        write(segment, { it.withProposal(proposal) { change -> change.copy(deleted = true) } }) {
            writer.delete(Nsid.FEED_SEGMENT_NAME_PROPOSAL, requireNotNull(proposal.rkey))
        }
    }

    /**
     * Votes [direction] (a [SegmentNameVoteDirection] value) on [proposal] of [segment], at the version listed, or takes the athlete's vote
     * back when it's null. [current] is the vote the athlete sees now, whose record is rewritten or deleted.
     */
    suspend fun vote(segment: AtUri, proposal: SegmentNameProposalView, direction: String?, current: SegmentNameViewerVote?) {
        clients.requireInteractionsFor(Nsid.FEED_SEGMENT_NAME_VOTE, "Sign in again to vote on segment names")
        val uri = current?.uri ?: AtUri.of(clients.did, Nsid.FEED_SEGMENT_NAME_VOTE, Tid.next(clock))
        val rkey = requireNotNull(uri.rkey)
        val interactions = clients.tiers.uri(AthleteSpace.INTERACTIONS)
        write(segment, { it.withProposal(proposal.uri) { change -> change.copy(vote = VoteChange(uri, direction)) } }) {
            if (direction == null) {
                writer.delete(Nsid.FEED_SEGMENT_NAME_VOTE, rkey, interactions)
            } else {
                writer.put(
                    Nsid.FEED_SEGMENT_NAME_VOTE,
                    rkey,
                    SegmentNameVoteRecord.serializer(),
                    SegmentNameVoteRecord(StrongRef(proposal.uri, proposal.cid), direction, clock.now()),
                    space = interactions,
                )
            }
        }
    }

    /** [view] with this session's [changes] to its names laid over it. */
    fun applyTo(view: SegmentView): SegmentView = changes.value[view.uri.value]?.let { applyChanges(view, it, clients.did.value) } ?: view

    private suspend fun write(segment: AtUri, change: (SegmentNameChanges) -> SegmentNameChanges, queue: suspend () -> Unit) {
        mutex.withLock {
            val previous = changes.value
            changes.update { it + (segment.value to change(it[segment.value] ?: SegmentNameChanges())) }
            undoingOnFailure({ changes.value = previous }) { queue() }
        }
        outbox.drain()
    }

    private companion object {
        /** A sent proposal's CID until the AppView lists it: nobody can vote on one's own proposal, so it's never used. */
        const val PENDING_CID = "pending"
    }
}

/** The athlete's changes to one segment's names this session: a proposal they sent, and changes to listed proposals by URI. */
data class SegmentNameChanges(val sent: SegmentNameProposalView? = null, val proposals: Map<String, ProposalChange> = emptyMap()) {
    /** These changes with [change] applied to proposal [uri]: to [sent] when it's that one, else to the listed proposal. */
    fun withProposal(uri: AtUri, change: (ProposalChange) -> ProposalChange): SegmentNameChanges {
        if (uri == sent?.uri) {
            val changed = change(ProposalChange())
            return copy(sent = if (changed.deleted) null else sent.copy(name = changed.name ?: sent.name))
        }
        return copy(proposals = proposals + (uri.value to change(proposals[uri.value] ?: ProposalChange())))
    }
}

/** A change to one proposal: a new name and when it was given, its deletion, or the athlete's vote. */
data class ProposalChange(
    val name: String? = null,
    val editedAt: Instant? = null,
    val deleted: Boolean = false,
    val vote: VoteChange? = null,
)

/** The athlete's vote record [uri] and its [direction], null once taken back. */
data class VoteChange(val uri: AtUri, val direction: String?)

/**
 * [view] with [changes] by the athlete [self] laid over it. An edit holds until the AppView lists the new name, and resets the totals; a
 * vote moves the totals by its difference from the vote the AppView reports, so it never counts twice; a sent proposal shows until the
 * AppView lists it. Deleting one's own open proposal, or sending one, sets whether Propose is offered.
 */
internal fun applyChanges(view: SegmentView, changes: SegmentNameChanges, self: String): SegmentView {
    val listed =
        view.nameProposals.mapNotNull { proposal ->
            val change = changes.proposals[proposal.uri.value] ?: return@mapNotNull proposal
            if (change.deleted) return@mapNotNull null
            val edited =
                if (change.name != null && change.name != proposal.name) {
                    proposal.copy(name = change.name, openedAt = change.editedAt ?: proposal.openedAt, up = 0, down = 0, viewerVote = null)
                } else {
                    proposal
                }
            change.vote?.let { edited.withVote(it) } ?: edited
        }
    val sent = changes.sent?.takeIf { s -> view.nameProposals.none { it.uri == s.uri } }
    val ownDeleted = view.nameProposals.any { it.proposer.did.value == self && changes.proposals[it.uri.value]?.deleted == true }
    val viewer =
        view.viewer?.let {
            when {
                sent != null -> it.copy(canProposeName = false)
                ownDeleted -> it.copy(canProposeName = true)
                else -> it
            }
        }
    return view.copy(nameProposals = listed + listOfNotNull(sent), viewer = viewer)
}

private fun SegmentNameProposalView.withVote(change: VoteChange): SegmentNameProposalView {
    val before = viewerVote?.direction
    val after = change.direction
    if (before == after) return this
    fun count(direction: String?, of: String) = if (direction == of) 1 else 0
    return copy(
        up = up - count(before, SegmentNameVoteDirection.UP) + count(after, SegmentNameVoteDirection.UP),
        down = down - count(before, SegmentNameVoteDirection.DOWN) + count(after, SegmentNameVoteDirection.DOWN),
        viewerVote = after?.let { SegmentNameViewerVote(change.uri, it) },
    )
}
