package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.PostQuoteChains
import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostEmbed
import io.github.solcott.activitytracker.lexicon.app.PostRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.post` (feed/posts, feed/quote-posts), from repos and the followers and mutuals spaces.

/**
 * [placement]'s copy of a post, placed like an activity ([place]). Accepted only from the repo, `followers` or `mutuals`, and only when it
 * shares [author]'s own proposal under the post's rkey, or quotes another `feed.post` with non-blank text; anything else throws, so it's
 * skipped like an undecodable record. A rewrite keeps the row's feed position. A quote's chain is kept in [PostQuoteChains].
 */
@Suppress("LongParameterList") // The record, and where it's from.
internal fun JdbcTransaction.upsertPost(author: Did, uri: AtUri, cid: String, json: JsonObject, placement: Placement, now: Long) {
    require(placement.visibility in POST_TIERS) { "a post at ${placement.visibility}" }
    val record = AtJson.decodeFromJsonElement(PostRecord.serializer(), json)
    val text = record.text?.trim()?.takeIf { it.isNotEmpty() }
    require(
        text == null || (text.encodeToByteArray().size <= PostRecord.MAX_TEXT_BYTES && graphemes(text) <= PostRecord.MAX_TEXT_GRAPHEMES)
    ) {
        "post text over its limit"
    }
    var proposal: AtUri? = null
    var quoted: PostEmbed.Quote? = null
    when (val embed = record.embed) {
        is PostEmbed.Proposal -> {
            val shared = embed.proposal
            require(shared.authority == author.value && shared.collection == Nsid.FEED_SEGMENT_NAME_PROPOSAL && shared.rkey == uri.rkey) {
                "a post sharing $shared"
            }
            proposal = shared
        }
        is PostEmbed.Quote -> {
            val subject = embed.subject.uri
            requireNotNull(text) { "a quote without text" }
            require(subject.collection == Nsid.FEED_POST && subject.rkey != null && subject != uri) { "a quote of $subject" }
            // A quote may not close a loop: a chain is a line from a quote to its first post (feed/quote-posts).
            require(
                PostQuoteChains.select(PostQuoteChains.postUri)
                    .where { (PostQuoteChains.postUri eq subject.value) and (PostQuoteChains.ancestorUri eq uri.value) }
                    .empty()
            ) {
                "a quote of $subject, which quotes it"
            }
            require(subject.value.length <= MAX_URI_LENGTH && embed.subject.cid.value.length <= MAX_CID_LENGTH) {
                "a quote's subject too long"
            }
            quoted = embed
        }
        is PostEmbed.Unknown -> throw IllegalArgumentException("a post with an unknown embed")
    }
    place(Nsid.FEED_POST, uri, cid, json, placement, placement.visibility, now) {
        Posts.upsert(onUpdateExclude = listOf(Posts.sortAt, Posts.indexedAt)) {
            it[Posts.uri] = uri.value
            it[Posts.cid] = cid
            it[authorDid] = author.value
            it[Posts.record] = AtJson.encodeToString(JsonObject.serializer(), json)
            it[proposalUri] = proposal?.value
            it[quotedUri] = quoted?.subject?.uri?.value
            it[quotedCid] = quoted?.subject?.cid?.value
            it[Posts.text] = text
            it[createdAt] = record.createdAt.toEpochMilliseconds()
            it[sortAt] = minOf(record.createdAt.toEpochMilliseconds(), now)
            it[indexedAt] = now
            it[visibility] = placement.visibility
            it[origin] = placement.origin
        }
        rebuildQuoteChains(uri, quoted?.subject?.uri)
    }
}

/**
 * Rebuilds [post]'s own chain from the post it [quotes] (none for a proposal post), then every quote whose chain runs through [post]: a
 * chain is a line, so a descendant's rows deeper than [post] all come from [post]'s and are replaced by them. Capped at
 * [PostQuoteChains.MAX_DEPTH]. [upsertPost] refuses a quote that would close a loop its chain shows; a longer loop reaches the cap, so none
 * of its quotes is served.
 */
internal fun JdbcTransaction.rebuildQuoteChains(post: AtUri, quotes: AtUri?) {
    PostQuoteChains.deleteWhere { postUri eq post.value }
    val own = buildList {
        if (quotes != null) {
            add(quotes.value to 1)
            PostQuoteChains.select(PostQuoteChains.ancestorUri, PostQuoteChains.depth)
                .where { PostQuoteChains.postUri eq quotes.value }
                .forEach { add(it[PostQuoteChains.ancestorUri] to it[PostQuoteChains.depth] + 1) }
        }
    }
    insertChainRows(post.value, own, offset = 0)
    val descendants =
        PostQuoteChains.selectAll()
            .where { (PostQuoteChains.ancestorUri eq post.value) and (PostQuoteChains.postUri neq post.value) }
            .map { it[PostQuoteChains.postUri] to it[PostQuoteChains.depth] }
    for ((descendant, depth) in descendants) {
        PostQuoteChains.deleteWhere { (postUri eq descendant) and (PostQuoteChains.depth greater depth) }
        insertChainRows(descendant, own, offset = depth)
    }
}

private fun JdbcTransaction.insertChainRows(post: String, ancestors: List<Pair<String, Int>>, offset: Int) {
    val rows = ancestors.map { (ancestor, depth) -> ancestor to depth + offset }.filter { it.second <= PostQuoteChains.MAX_DEPTH }
    PostQuoteChains.batchInsert(rows, ignore = true) { (ancestor, depth) ->
        this[PostQuoteChains.postUri] = post
        this[PostQuoteChains.ancestorUri] = ancestor
        this[PostQuoteChains.depth] = depth
    }
}

/** Drops [post]'s own chain once no copy of it is indexed; quotes of it keep theirs, which end at it while it isn't served. */
internal fun JdbcTransaction.dropQuoteChain(post: AtUri) {
    if (Posts.select(Posts.uri).where { Posts.uri eq post.value }.empty()) PostQuoteChains.deleteWhere { postUri eq post.value }
}

/** Drops the chains of every post by [author], with their account. */
internal fun JdbcTransaction.dropQuoteChainsBy(author: Did) {
    // Escaped: a DID may hold LIKE metacharacters, such as the `%` of a did:web's encoded port.
    PostQuoteChains.deleteWhere { postUri like LikePattern.ofLiteral("at://${author.value}/${Nsid.FEED_POST}/") + "%" }
}

/** Posts have no Only me tier: a post in `self` would be seen by no one but its author. */
private val POST_TIERS = setOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS)

private const val MAX_CID_LENGTH = 255
