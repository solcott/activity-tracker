package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select

/** Read side of the social graph: who follows an actor and whom they follow. */
class GraphViews(private val db: Database) {
    suspend fun followers(params: GetFollowers.Params): GetFollowers.Output = db.tx {
        val subject = resolveSubject(params.actor)
        val (accounts, cursor) = followPage(Follows.subjectDid, Follows.authorDid, subject.did.value, params.limit, params.cursor)
        GetFollowers.Output(subject, accounts, cursor)
    }

    suspend fun follows(params: GetFollows.Params): GetFollows.Output = db.tx {
        val subject = resolveSubject(params.actor)
        val (accounts, cursor) = followPage(Follows.authorDid, Follows.subjectDid, subject.did.value, params.limit, params.cursor)
        GetFollows.Output(subject, accounts, cursor)
    }

    private fun JdbcTransaction.resolveSubject(actor: String): ProfileViewBasic {
        val did = resolveActor(actor) ?: throw XrpcError(HttpStatusCode.BadRequest.value, "ActorNotFound", "Unknown actor $actor")
        return basicProfiles(listOf(did)).getValue(did)
    }

    /**
     * Follows where [match] is [did], newest indexed first, as profiles of the [other] side. Keyset pagination on (indexed_at, uri)
     * descending; cursor is `"<indexedAt>::<uri>"`.
     */
    private fun JdbcTransaction.followPage(
        match: Column<String>,
        other: Column<String>,
        did: String,
        limit: Int?,
        cursor: String?,
    ): Pair<List<ProfileViewBasic>, String?> {
        val pageSize = Paging.clamp(limit)
        val rows =
            Follows.select(Follows.uri, Follows.indexedAt, other)
                .where { (match eq did) and keysetBefore(Follows.indexedAt, Follows.uri, cursor) }
                .orderBy(Follows.indexedAt to SortOrder.DESC, Follows.uri to SortOrder.DESC)
                .limit(pageSize)
                .toList()
        val next = rows.takeIf { it.size == pageSize }?.last()?.let { "${it[Follows.indexedAt]}::${it[Follows.uri]}" }
        val profiles = basicProfiles(rows.map { it[other] })
        // An account can be followed more than once (duplicate records); list it once.
        return rows.mapNotNull { profiles[it[other]] }.distinctBy { it.did } to next
    }
}
