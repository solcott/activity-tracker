package io.github.solcott.activitytracker.data.profile

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.requireInteractionsFor
import io.github.solcott.activitytracker.data.activity.undoingOnFailure
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.data.feed.FeedRepository
import io.github.solcott.activitytracker.data.feed.LocalBlocks
import io.github.solcott.activitytracker.data.outcomeOf
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.AthleteSpace
import io.github.solcott.activitytracker.lexicon.app.BlockRecord
import io.github.solcott.activitytracker.lexicon.app.BlockSource
import io.github.solcott.activitytracker.lexicon.app.GetBlocks
import io.github.solcott.activitytracker.lexicon.app.GetMutes
import io.github.solcott.activitytracker.lexicon.app.MuteRecord
import io.github.solcott.activitytracker.lexicon.app.ProfileView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.dataresult.Outcome
import io.github.solcott.uistate.Page
import io.github.solcott.uistate.pageOf
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The athlete's blocks and mutes. Each is a `graph.block` or `graph.mute` keyed by the subject's DID in the athlete's interactions space
 * only, so only they and the AppView know of it; writing one twice is the same record, and removing one deletes that rkey. Writes go
 * through the outbox and show at once through [FeedRepository.localBlocks]. A session whose `space:` grant doesn't cover the collection is
 * refused before anything changes. Bluesky blocks (`app.bsky.graph.block`) count too, but this app never writes them.
 */
@SingleIn(SessionScope::class)
@Inject
class BlockRepository(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val feed: FeedRepository,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    private val local: LocalBlocks
        get() = feed.localBlocks

    /** Blocks ([blocked]) or unblocks [subject]; nothing happens for the athlete themselves. Returns the block's URI, or null. */
    suspend fun setBlocked(subject: Did, blocked: Boolean): AtUri? {
        if (subject == clients.did) return null
        val uri = AtUri.of(clients.did, Nsid.GRAPH_BLOCK, BlockRecord.rkey(subject)).takeIf { blocked }
        mutex.withLock {
            clients.requireInteractionsFor(Nsid.GRAPH_BLOCK)
            val previous = local.block(subject, uri)
            undoingOnFailure({ local.restoreBlock(subject, previous) }) {
                if (blocked) {
                    writer.put(
                        Nsid.GRAPH_BLOCK,
                        subject.value,
                        BlockRecord.serializer(),
                        BlockRecord(subject, clock.now()),
                        space = interactions,
                    )
                } else {
                    writer.delete(Nsid.GRAPH_BLOCK, subject.value, interactions)
                }
            }
        }
        outbox.drain()
        return uri
    }

    /** Mutes ([muted]) or unmutes [subject]; nothing happens for the athlete themselves. Returns the mute's URI, or null. */
    suspend fun setMuted(subject: Did, muted: Boolean): AtUri? {
        if (subject == clients.did) return null
        val uri = AtUri.of(clients.did, Nsid.GRAPH_MUTE, MuteRecord.rkey(subject)).takeIf { muted }
        mutex.withLock {
            clients.requireInteractionsFor(Nsid.GRAPH_MUTE)
            val previous = local.mute(subject, uri)
            undoingOnFailure({ local.restoreMute(subject, previous) }) {
                if (muted) {
                    writer.put(
                        Nsid.GRAPH_MUTE,
                        subject.value,
                        MuteRecord.serializer(),
                        MuteRecord(subject, clock.now()),
                        space = interactions,
                    )
                } else {
                    writer.delete(Nsid.GRAPH_MUTE, subject.value, interactions)
                }
            }
        }
        outbox.drain()
        return uri
    }

    /** [view] with the athlete's own blocks and mutes of its athlete as they left them ([LocalBlocks.applyTo]). */
    fun applyTo(view: ProfileView): ProfileView = local.applyTo(view)

    /** A page of the athlete's blocks, newest first, without those they removed this session and the AppView still lists. */
    suspend fun blocks(cursor: String? = null): GetBlocks.Output {
        val page = clients.appView.query(GetBlocks.method, GetBlocks.Params(cursor = cursor))
        return page.copy(blocks = page.blocks.filterNot { local.blocked(it.actor.did) == false && it.sources == listOf(BlockSource.APP) })
    }

    /** A page of the athlete's mutes, newest first, without those they removed this session and the AppView still lists. */
    suspend fun mutes(cursor: String? = null): GetMutes.Output {
        val page = clients.appView.query(GetMutes.method, GetMutes.Params(cursor = cursor))
        return page.copy(mutes = page.mutes.filterNot { local.muted(it.did) == false })
    }

    /** A page of [blocks] for a paged list. */
    suspend fun blocksPage(cursor: String?): Outcome<Page<GetBlocks.BlockView, Unit>> = outcomeOf {
        blocks(cursor).let { pageOf(it.blocks, it.cursor) }
    }

    /** A page of [mutes] for a paged list. */
    suspend fun mutesPage(cursor: String?): Outcome<Page<ProfileViewBasic, Unit>> = outcomeOf {
        mutes(cursor).let { pageOf(it.mutes, it.cursor) }
    }

    private val interactions
        get() = clients.tiers.uri(AthleteSpace.INTERACTIONS)
}
