package io.github.solcott.activitytracker.data.feed

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.activity.OutboxProcessor
import io.github.solcott.activitytracker.data.activity.OutboxWriter
import io.github.solcott.activitytracker.data.activity.placeOf
import io.github.solcott.activitytracker.data.activity.requireSpacesFor
import io.github.solcott.activitytracker.data.activity.undoingOnFailure
import io.github.solcott.activitytracker.data.di.SessionClients
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Cid
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.Tid
import io.github.solcott.activitytracker.lexicon.app.PostEmbed
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostRecord
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.QuotedPostEmbed
import io.github.solcott.activitytracker.lexicon.app.Visibility
import io.github.solcott.activitytracker.lexicon.atproto.StrongRef
import kotlin.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The athlete's quote posts (feed/quote-posts): a `feed.post` quoting another post, written through the outbox at a new rkey in their repo
 * or the tier space for its audience, which is never wider than the quoted post's, and deleted from where it's stored. Both show at once
 * through [FeedRepository.localPosts] and [FeedRepository.localQuotes] until the AppView catches up, and are taken back if queueing fails.
 */
@SingleIn(SessionScope::class)
@Inject
class QuotePosts(
    private val clients: SessionClients,
    private val writer: OutboxWriter,
    private val outbox: OutboxProcessor,
    private val clock: Clock,
    private val feed: FeedRepository,
) {
    private val mutex = Mutex()

    /** Whether this session can quote for Followers or Mutuals: its PDS holds spaces and its grant covers posts. Public needs neither. */
    val canQuoteBeyondPublic: Boolean
        get() = clients.canWriteToSpaces(Nsid.FEED_POST)

    /** The signed-in athlete, who may delete only their own quotes. */
    val self
        get() = clients.did

    /**
     * Quotes [post] with [text] for [visibility], and returns the quote's URI. Refused before anything changes for blank text, a post not
     * yet listed ([isPending]), an audience wider than [post]'s as the view shows it, or a non-public audience this session can't write
     * posts to (as a like is).
     */
    suspend fun quote(post: PostView, text: String, visibility: String): AtUri {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "A quote needs text" }
        // A strong ref needs the post's real CID, which a post sent this session has only once the AppView lists it.
        require(!post.isPending) { "A post can be quoted once it's listed" }
        require(visibility in Visibility.atMost(post.visibilityOrPublic) && visibility in QUOTE_VISIBILITIES) {
            "A quote of a ${post.visibilityOrPublic} post can't be $visibility"
        }
        clients.requireSpacesFor(visibility, Nsid.FEED_POST)
        val rkey = Tid.next(clock)
        val uri = AtUri.of(clients.did, Nsid.FEED_POST, rkey)
        val now = clock.now()
        val view =
            PostView(
                uri = uri,
                cid = Cid(PENDING_POST_CID),
                author = ProfileViewBasic(clients.did, clients.handle),
                embed = PostEmbedView.Quote(post.asQuoted()),
                createdAt = now,
                indexedAt = now,
                text = trimmed,
                visibility = visibility.takeIf { it != Visibility.PUBLIC },
            )
        mutex.withLock {
            val previousPosts = feed.localPosts.changes.value
            val previousQuotes = feed.localQuotes.overrides.value
            feed.localQuotes.sent(post)
            feed.localPosts.put(view)
            undoingOnFailure({
                feed.localPosts.restore(previousPosts)
                feed.localQuotes.restore(previousQuotes)
            }) {
                writer.put(
                    Nsid.FEED_POST,
                    rkey,
                    PostRecord.serializer(),
                    PostRecord(PostEmbed.Quote(StrongRef(post.uri, post.cid)), now, trimmed),
                    space = clients.placeOf(visibility),
                )
            }
        }
        outbox.drain()
        return uri
    }

    /** Deletes the athlete's own [quote] from where its audience stores it, and leaves it out of every list at once. */
    suspend fun delete(quote: PostView) {
        val embed = quote.embed
        require(quote.author.did == clients.did && embed is PostEmbedView.Quote) { "Only your own quote can be deleted" }
        clients.requireSpacesFor(quote.visibilityOrPublic, Nsid.FEED_POST)
        val rkey = requireNotNull(quote.uri.rkey)
        mutex.withLock {
            val previousPosts = feed.localPosts.changes.value
            val previousQuotes = feed.localQuotes.overrides.value
            feed.localPosts.remove(quote.uri.value)
            embed.quoted.uriOrNull()?.let { feed.localQuotes.deleted(it.value) }
            undoingOnFailure({
                feed.localPosts.restore(previousPosts)
                feed.localQuotes.restore(previousQuotes)
            }) {
                writer.delete(Nsid.FEED_POST, rkey, clients.placeOf(quote.visibilityOrPublic))
            }
        }
        outbox.drain()
    }

    private companion object {
        /** Posts have no Only me. */
        val QUOTE_VISIBILITIES = setOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS)
    }
}

/** This post as a quote of it shows it, one level deep: a quote of a quote links to the post that one quotes. */
fun PostView.asQuoted(): QuotedPost.Post =
    QuotedPost.Post(
        uri = uri,
        cid = cid,
        author = author,
        createdAt = createdAt,
        text = text,
        visibility = visibility,
        embed =
            when (val shared = embed) {
                is PostEmbedView.Proposal -> shared
                is PostEmbedView.Quote -> shared.quoted.uriOrNull()?.let(QuotedPostEmbed::Link)
                is PostEmbedView.Unknown -> null
            },
    )

/** The quoted post's URI, unless it's a kind this client doesn't know. */
fun QuotedPost.uriOrNull(): AtUri? =
    when (this) {
        is QuotedPost.Post -> uri
        is QuotedPost.Unavailable -> uri
        is QuotedPost.Unknown -> null
    }
