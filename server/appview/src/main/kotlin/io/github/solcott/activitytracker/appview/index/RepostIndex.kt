package io.github.solcott.activitytracker.appview.index

import io.github.solcott.activitytracker.appview.db.Reposts
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Nsid
import io.github.solcott.activitytracker.lexicon.app.RepostRecord
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

// Indexing of `feed.repost` (feed/reposts), from reposters' repos and their interactions spaces.

/**
 * [placement]'s repost. Only a repost of another athlete's `feed.post` is kept; anything else throws, so it's skipped like an undecodable
 * record. Whether the reposter may see the post is judged when it's read (`repostsShownTo`), since the post, the follow or its approval may
 * be indexed after the repost. A replay keeps the row's feed position.
 */
internal fun JdbcTransaction.upsertRepost(record: RepostRecord, author: Did, uri: AtUri, placement: Placement, now: Long) {
    val subject = record.subject.uri
    require(subject.collection == Nsid.FEED_POST) { "repost of $subject" }
    require(subject.authority != author.value) { "repost of own post" }
    Reposts.upsert(onUpdateExclude = listOf(Reposts.sortAt, Reposts.indexedAt)) {
        it[Reposts.uri] = uri.value
        it[authorDid] = author.value
        it[subjectUri] = subject.value
        it[subjectCid] = record.subject.cid.value
        it[createdAt] = record.createdAt.toEpochMilliseconds()
        it[sortAt] = minOf(record.createdAt.toEpochMilliseconds(), now)
        it[indexedAt] = now
        it[origin] = placement.origin
    }
}
