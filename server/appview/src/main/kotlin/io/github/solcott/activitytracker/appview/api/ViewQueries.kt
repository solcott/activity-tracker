package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.isDid
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

// Query helpers shared by [Views] and [GraphViews].

internal fun JdbcTransaction.resolveActor(actor: String): String? {
    val condition = if (actor.isDid()) Actors.did eq actor else Actors.handle eq actor.lowercase()
    return Actors.select(Actors.did).where(condition).firstOrNull()?.get(Actors.did)
}

/** Rows strictly after a `"<sort>::<uri>"` cursor in (sort, uri) descending order; everything when there is no valid cursor. */
internal fun keysetBefore(sort: Column<Long>, uri: Column<String>, cursor: String?): Op<Boolean> {
    val (sortValue, uriValue) = cursor?.let(::parseCursor) ?: return Op.TRUE
    return (sort less sortValue) or ((sort eq sortValue) and (uri less uriValue))
}

/**
 * The ascending counterpart of [keysetBefore]: rows after the cursor's (sort, uri). The redundant `sort >= cursor` lets an index on (…,
 * sort) seek to the cursor instead of filtering every earlier row, which Postgres doesn't do for the `OR` alone.
 */
internal fun keysetAfter(sort: Column<Long>, uri: Column<String>, cursor: String?): Op<Boolean> {
    val (sortValue, uriValue) = cursor?.let(::parseCursor) ?: return Op.TRUE
    return (sort greaterEq sortValue) and ((sort greater sortValue) or ((sort eq sortValue) and (uri greater uriValue)))
}

/** Splits a `"<sort>::<key>"` cursor; null when it is malformed. */
internal fun parseCursor(cursor: String): Pair<Long, String>? {
    val (sortAt, uri) = cursor.split("::", limit = 2).takeIf { it.size == 2 } ?: return null
    return sortAt.toLongOrNull()?.let { it to uri }
}

/** Basic views of [dids], with each account's labels, in two queries. */
internal fun JdbcTransaction.basicProfiles(dids: Collection<String>, avatars: AvatarUrls): Map<String, ProfileViewBasic> {
    val distinct = dids.distinct()
    val labels = labelViews(distinct.associateWith { null })
    return Actors.selectAll()
        .where { Actors.did inList distinct }
        .associate {
            it[Actors.did] to
                ProfileViewBasic(
                    Did(it[Actors.did]),
                    Handle(it[Actors.handle]),
                    it[Actors.displayName],
                    avatars.url(it[Actors.did], it[Actors.avatarCid]),
                    labels[it[Actors.did]],
                )
        }
}

/** [viewer]'s like of each of [uris] they liked. They may see every row, so their own like is always admitted. */
internal fun JdbcTransaction.viewerLikes(viewer: Did, uris: List<String>): Map<String, String> =
    Likes.select(Likes.uri, Likes.subjectUri)
        .where { (Likes.authorDid eq viewer.value) and (Likes.subjectUri inList uris) }
        .associate { it[Likes.subjectUri] to it[Likes.uri] }
