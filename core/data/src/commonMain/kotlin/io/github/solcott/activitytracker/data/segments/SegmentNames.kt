package io.github.solcott.activitytracker.data.segments

import androidx.compose.runtime.Immutable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.placeOf
import io.github.solcott.activitytracker.data.activity.readOwn
import io.github.solcott.activitytracker.data.activity.requireInteractionsFor
import io.github.solcott.activitytracker.data.activity.requireSpacesFor
import io.github.solcott.activitytracker.data.activity.undoingOnFailure
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.di.SpacesSupport
import io.github.solcott.activitytracker.data.feed.LocalPosts
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.DiveSiteView
import io.github.solcott.activitytracker.lexicon.app.DiveSiteViewBasic
import io.github.solcott.activitytracker.lexicon.app.PostEmbed
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostRecord
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameViewerVote
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteRecord
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import io.github.solcott.activitytracker.lexicon.app.SegmentViewBasic
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.SpaceUri
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
@Suppress("TooManyFunctions") // Proposing, editing, deleting and voting, each beside its overlay, and sharing proposals as posts.
class SegmentNames(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val clock: Clock,
    private val posts: LocalPosts = LocalPosts(),
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

    /**
     * Whether this session can share a proposal with Followers or Mutuals (feed/posts): its PDS holds spaces and its grant covers posts.
     * Public sharing needs neither.
     */
    val canShareBeyondPublic: Boolean
        get() = clients.canWriteToSpaces(Nsid.FEED_POST)

    /** Whether the PDS can't hold spaces at all, so Followers and Mutuals sharing can't be fixed by signing in again. */
    val spacesUnsupported: Boolean
        get() = clients.spacesSupport.value == SpacesSupport.Unsupported

    /**
     * How the athlete's own [proposal] is shared now (feed/posts), read from where its post may be: their repo, and their followers and
     * mutuals spaces when the session may read them. The narrowest copy wins, as the AppView serves it; null when it isn't shared.
     */
    suspend fun share(proposal: AtUri): PostShare? {
        val rkey = requireNotNull(proposal.rkey)
        val places = listOf<SpaceUri?>(null) + if (clients.canUseSpaces) SHARE_TIERS.map(clients.tiers::uri) else emptyList()
        return places
            .mapNotNull { place ->
                val json = clients.readOwn(Nsid.FEED_POST, rkey, place) ?: return@mapNotNull null
                val record =
                    runCatching { AtJson.decodeFromJsonElement(PostRecord.serializer(), json) }.getOrNull() ?: return@mapNotNull null
                PostShare(place?.skey ?: Visibility.PUBLIC, record.text, record.createdAt)
            }
            .lastOrNull()
    }

    /**
     * Proposes [name] for [segment], and returns the proposal's URI. With [share], also shares it to the feed (feed/posts): a post at the
     * proposal's rkey, at the share's audience, shown at once as naming [place].
     */
    suspend fun propose(segment: AtUri, name: String, share: PostShare? = null, place: SharedPlace? = null): AtUri {
        share?.let { requireShareable(it) }
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
        val post = share?.let { postView(uri, name, now, 0, 0, it.copy(createdAt = now), place) }
        write(segment, { it.copy(sent = sent) }, { post?.let(posts::put) }) {
            writer.put(
                Nsid.FEED_SEGMENT_NAME_PROPOSAL,
                rkey,
                SegmentNameProposalRecord.serializer(),
                SegmentNameProposalRecord(segment, name, now),
            )
            if (share != null) queueShare(rkey, uri, share.copy(createdAt = now), before = null)
        }
        return uri
    }

    /**
     * Changes how the athlete's open [proposal] on [segment] is shared from [before] to [after] (null: not shared), without renaming it:
     * the post is written at its new audience and deleted from every other place, or deleted everywhere. [place] names what it's for, so
     * the post shows at once.
     */
    suspend fun setShare(
        segment: AtUri,
        proposal: SegmentNameProposalView,
        before: PostShare?,
        after: PostShare?,
        place: SharedPlace? = null,
    ) {
        after?.let { requireShareable(it) }
        val rkey = requireNotNull(proposal.uri.rkey)
        val postUri = AtUri.of(clients.did, Nsid.FEED_POST, rkey)
        val kept = after?.copy(createdAt = before?.createdAt ?: clock.now())
        val view = kept?.let { postView(proposal.uri, proposal.name, proposal.openedAt, proposal.up, proposal.down, it, place) }
        write(segment, { it }, { if (view != null) posts.put(view) else posts.remove(postUri.value) }) {
            if (kept != null) queueShare(rkey, proposal.uri, kept, before) else queueUnshare(rkey)
        }
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

    /** Deletes the athlete's open [proposal] on [segment], which withdraws it, and its post wherever it may be (feed/posts). */
    suspend fun delete(segment: AtUri, proposal: AtUri) {
        val rkey = requireNotNull(proposal.rkey)
        val postUri = AtUri.of(clients.did, Nsid.FEED_POST, rkey)
        write(segment, { it.withProposal(proposal) { change -> change.copy(deleted = true) } }, { posts.remove(postUri.value) }) {
            queueUnshare(rkey)
            writer.delete(Nsid.FEED_SEGMENT_NAME_PROPOSAL, rkey)
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

    /**
     * A post sharing a proposal (feed/posts) with this session's [changes] to that proposal laid over its embed: the athlete's vote moves
     * the totals by its difference from the vote the AppView reports, and their rename of their own proposal shows with the totals reset.
     */
    @Suppress("ReturnCount") // Each way a post has nothing of this session's to lay over it.
    fun applyTo(post: PostView): PostView {
        val embed = post.embed as? PostEmbedView.Proposal ?: return post
        val place = (embed.segment?.uri ?: embed.diveSite?.uri)?.value ?: return post
        val change = changes.value[place]?.proposals?.get(embed.proposal.uri.value) ?: return post
        val renamed =
            if (change.name != null && change.name != embed.name) embed.copy(name = change.name, up = 0, down = 0, viewerVote = null)
            else embed
        val voted =
            change.vote?.let { vote ->
                val before = renamed.viewerVote?.direction
                val after = vote.direction
                if (before == after) renamed
                else {
                    fun count(direction: String?, of: String) = if (direction == of) 1 else 0
                    renamed.copy(
                        up = renamed.up - count(before, SegmentNameVoteDirection.UP) + count(after, SegmentNameVoteDirection.UP),
                        down = renamed.down - count(before, SegmentNameVoteDirection.DOWN) + count(after, SegmentNameVoteDirection.DOWN),
                        viewerVote = after?.let { SegmentNameViewerVote(vote.uri, it) },
                    )
                }
            } ?: renamed
        return post.copy(embed = voted)
    }

    /** A timeline [item] with this session's changes laid over it when it's a post ([applyTo]). */
    fun withChanges(item: TimelineItem): TimelineItem = if (item is TimelineItem.Post) TimelineItem.Post(applyTo(item.post)) else item

    /**
     * Votes [direction] on the proposal [post] shares, or takes the athlete's vote back when it's null, as [vote] does from the segment
     * screen (feed/posts).
     */
    suspend fun vote(post: PostView, direction: String?) {
        val embed = requireNotNull(post.embed as? PostEmbedView.Proposal) { "Not a proposal post" }
        val place = requireNotNull(embed.segment?.uri ?: embed.diveSite?.uri)
        val listed =
            SegmentNameProposalView(
                uri = embed.proposal.uri,
                cid = embed.proposal.cid,
                name = embed.name,
                proposer = post.author,
                openedAt = embed.openedAt,
                up = embed.up,
                down = embed.down,
                viewerVote = embed.viewerVote,
            )
        vote(place, listed, direction, embed.viewerVote)
    }

    /** A dive site [view] with this session's [changes] to its names laid over it, as for a segment (dive-sites/site-names). */
    fun applyTo(view: DiveSiteView): DiveSiteView =
        changes.value[view.uri.value]?.let { changed ->
            val (proposals, canPropose) = applyNameChanges(view.nameProposals, view.viewer?.canProposeName, changed, clients.did.value)
            view.copy(nameProposals = proposals, viewer = view.viewer?.let { it.copy(canProposeName = canPropose ?: it.canProposeName) })
        } ?: view

    private suspend fun write(
        segment: AtUri,
        change: (SegmentNameChanges) -> SegmentNameChanges,
        postChange: () -> Unit = {},
        queue: suspend () -> Unit,
    ) {
        mutex.withLock {
            val previous = changes.value
            val previousPosts = posts.changes.value
            changes.update { it + (segment.value to change(it[segment.value] ?: SegmentNameChanges())) }
            postChange()
            undoingOnFailure({
                changes.value = previous
                posts.restore(previousPosts)
            }) {
                queue()
            }
        }
        outbox.drain()
    }

    /** Refuses a share this session can't write, before anything changes: a non-public one needs its grant to cover posts. */
    private fun requireShareable(share: PostShare) {
        require(share.visibility in SHARE_VISIBILITIES) { "A post can't be shared at ${share.visibility}" }
        clients.requireSpacesFor(share.visibility, Nsid.FEED_POST)
    }

    /** Queues the post sharing [proposal] at [share]'s place, then deletes of it from the other places it may be. */
    private suspend fun queueShare(rkey: String, proposal: AtUri, share: PostShare, before: PostShare?) {
        val place = clients.placeOf(share.visibility)
        writer.put(
            Nsid.FEED_POST,
            rkey,
            PostRecord.serializer(),
            PostRecord(PostEmbed.Proposal(proposal), requireNotNull(share.createdAt), share.text?.trim()?.ifEmpty { null }),
            space = place,
        )
        // A brand-new post is nowhere else yet.
        if (before != null) queueUnshare(rkey, except = place, includeRepo = share.visibility != Visibility.PUBLIC)
    }

    /**
     * Queues deletes of the post at [rkey] from the repo (unless [includeRepo] is false) and, when the session may write posts there, the
     * followers and mutuals spaces, less [except]. One that isn't there is deleted already.
     */
    private suspend fun queueUnshare(rkey: String, except: SpaceUri? = null, includeRepo: Boolean = true) {
        if (includeRepo) writer.delete(Nsid.FEED_POST, rkey)
        if (!clients.canWriteToSpaces(Nsid.FEED_POST)) return
        SHARE_TIERS.map(clients.tiers::uri).filter { it != except }.forEach { writer.delete(Nsid.FEED_POST, rkey, it) }
    }

    /** The athlete's post sharing [proposal] as it'll show until the AppView lists it. */
    @Suppress("LongParameterList") // The proposal as shown, and how it's shared.
    private fun postView(proposal: AtUri, name: String, openedAt: Instant, up: Int, down: Int, share: PostShare, place: SharedPlace?) =
        PostView(
            uri = AtUri.of(clients.did, Nsid.FEED_POST, requireNotNull(proposal.rkey)),
            cid = Cid(PENDING_CID),
            author = ProfileViewBasic(clients.did, clients.handle),
            embed =
                PostEmbedView.Proposal(
                    proposal = StrongRef(proposal, Cid(PENDING_CID)),
                    name = name,
                    status = PostProposalStatus.OPEN,
                    openedAt = openedAt,
                    up = up,
                    down = down,
                    segment = place?.segment,
                    diveSite = place?.diveSite,
                ),
            createdAt = requireNotNull(share.createdAt),
            indexedAt = requireNotNull(share.createdAt),
            text = share.text?.trim()?.ifEmpty { null },
            visibility = share.visibility.takeIf { it != Visibility.PUBLIC },
        )

    private companion object {
        /** A sent proposal's CID until the AppView lists it: nobody can vote on one's own proposal, so it's never used. */
        const val PENDING_CID = "pending"

        /** The tier spaces a post may be in, widest first: posts have no Only me. */
        val SHARE_TIERS = listOf(AthleteSpace.FOLLOWERS, AthleteSpace.MUTUALS)

        val SHARE_VISIBILITIES = setOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS)
    }
}

/**
 * How a name proposal is shared to the feed (feed/posts): with whom ([Visibility.PUBLIC], [Visibility.FOLLOWERS] or [Visibility.MUTUALS]),
 * the athlete's [text], and when the post was first written (null until it is).
 */
@Immutable data class PostShare(val visibility: String, val text: String? = null, val createdAt: Instant? = null)

/** What a shared proposal names, as the post shows it until the AppView lists it: a segment or a dive site. */
@Immutable data class SharedPlace(val segment: SegmentViewBasic? = null, val diveSite: DiveSiteViewBasic? = null)

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
    val (proposals, canPropose) = applyNameChanges(view.nameProposals, view.viewer?.canProposeName, changes, self)
    return view.copy(nameProposals = proposals, viewer = view.viewer?.let { it.copy(canProposeName = canPropose ?: it.canProposeName) })
}

/**
 * A named place's [listed] proposals and its [canPropose] flag (null without viewer state) with [changes] by [self] laid over them, as
 * [applyChanges] describes.
 */
internal fun applyNameChanges(
    listed: List<SegmentNameProposalView>,
    canPropose: Boolean?,
    changes: SegmentNameChanges,
    self: String,
): Pair<List<SegmentNameProposalView>, Boolean?> {
    val shown = listed.mapNotNull { proposal ->
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
    val sent = changes.sent?.takeIf { s -> listed.none { it.uri == s.uri } }
    val ownDeleted = listed.any { it.proposer.did.value == self && changes.proposals[it.uri.value]?.deleted == true }
    val flag = canPropose?.let {
        when {
            sent != null -> false
            ownDeleted -> true
            else -> it
        }
    }
    return (shown + listOfNotNull(sent)) to flag
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
