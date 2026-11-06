package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Posts
import io.github.solcott.activitytracker.lexicon.AtJson
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.PostEmbed
import io.github.solcott.activitytracker.lexicon.app.PostRecord
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.post` (feed/posts), from repos and the followers and mutuals spaces.

/**
 * [placement]'s copy of a post, placed like an activity ([place]). Only a post sharing [author]'s own proposal under the post's rkey is
 * accepted, and only from the repo, `followers` or `mutuals`; anything else throws, so it's skipped like an undecodable record. A rewrite
 * keeps the row's feed position.
 */
@Suppress("LongParameterList") // The record, and where it's from.
internal fun JdbcTransaction.upsertPost(author: Did, uri: AtUri, cid: String, json: JsonObject, placement: Placement, now: Long) {
    require(placement.visibility in POST_TIERS) { "a post at ${placement.visibility}" }
    val record = AtJson.decodeFromJsonElement(PostRecord.serializer(), json)
    val proposal = requireNotNull(record.embed as? PostEmbed.Proposal) { "a post with an unknown embed" }.proposal
    require(proposal.authority == author.value && proposal.collection == Nsid.FEED_SEGMENT_NAME_PROPOSAL && proposal.rkey == uri.rkey) {
        "a post sharing $proposal"
    }
    val text = record.text?.trim()?.takeIf { it.isNotEmpty() }
    require(
        text == null || (text.encodeToByteArray().size <= PostRecord.MAX_TEXT_BYTES && graphemes(text) <= PostRecord.MAX_TEXT_GRAPHEMES)
    ) {
        "post text over its limit"
    }
    place(Nsid.FEED_POST, uri, cid, json, placement, placement.visibility, now) {
        Posts.upsert(onUpdateExclude = listOf(Posts.sortAt, Posts.indexedAt)) {
            it[Posts.uri] = uri.value
            it[Posts.cid] = cid
            it[authorDid] = author.value
            it[Posts.record] = AtJson.encodeToString(JsonObject.serializer(), json)
            it[proposalUri] = proposal.value
            it[Posts.text] = text
            it[createdAt] = record.createdAt.toEpochMilliseconds()
            it[sortAt] = minOf(record.createdAt.toEpochMilliseconds(), now)
            it[indexedAt] = now
            it[visibility] = placement.visibility
            it[origin] = placement.origin
        }
    }
}

/** Posts have no Only me tier: a post in `self` would be seen by no one but its author. */
private val POST_TIERS = setOf(Visibility.PUBLIC, Visibility.FOLLOWERS, Visibility.MUTUALS)
