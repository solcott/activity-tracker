package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Blocks
import io.github.solcott.activitytracker.appview.db.Follows
import io.github.solcott.activitytracker.appview.db.Mutes
import io.github.solcott.activitytracker.appview.db.Origins
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.BlockSource
import io.github.solcott.activitytracker.lexicon.app.GetBlocks
import io.github.solcott.activitytracker.lexicon.app.GetFollowRequests
import io.github.solcott.activitytracker.lexicon.app.GetFollowers
import io.github.solcott.activitytracker.lexicon.app.GetFollows
import io.github.solcott.activitytracker.lexicon.app.GetMutes
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * Read side of the social graph: who effectively follows an actor, whom they follow, who asks to follow them, and whom one blocks and
 * mutes.
 */
class GraphViews(private val db: Database, private val avatars: AvatarUrls) {
    suspend fun followers(viewer: Did?, params: GetFollowers.Params): GetFollowers.Output = db.tx {
        val subject = resolveSubject(viewer, params.actor)
        val (accounts, cursor) = followPage(viewer, Follows.subjectDid eq subject.did.value, Follows.authorDid, params.limit, params.cursor)
        GetFollowers.Output(subject, accounts, cursor)
    }

    suspend fun follows(viewer: Did?, params: GetFollows.Params): GetFollows.Output = db.tx {
        val subject = resolveSubject(viewer, params.actor)
        val (accounts, cursor) = followPage(viewer, Follows.authorDid eq subject.did.value, Follows.subjectDid, params.limit, params.cursor)
        GetFollows.Output(subject, accounts, cursor)
    }

    /**
     * Accounts whose follows of [viewer] are pending ([effectiveFollow] fails), newest indexed first. Declined ones are included: declines
     * live in the athlete's own space, which the AppView never sees, so the client filters them out. Keyset pagination as [followPage].
     */
    suspend fun followRequests(viewer: Did, params: GetFollowRequests.Params): GetFollowRequests.Output = db.tx {
        val pageSize = Paging.clamp(params.limit)
        val rows =
            Follows.select(Follows.uri, Follows.indexedAt, Follows.authorDid)
                .where {
                    // A follow a block makes ineffective isn't a request: the requester is blocked, not waiting.
                    (Follows.subjectDid eq viewer.value) and
                        not(effectiveFollow()) and
                        not(blocked(Follows.authorDid, Follows.subjectDid)) and
                        keysetBefore(Follows.indexedAt, Follows.uri, params.cursor)
                }
                .orderBy(Follows.indexedAt to SortOrder.DESC, Follows.uri to SortOrder.DESC)
                .limit(pageSize)
                .toList()
        val next = rows.takeIf { it.size == pageSize }?.last()?.let { "${it[Follows.indexedAt]}::${it[Follows.uri]}" }
        val profiles = basicProfiles(rows.map { it[Follows.authorDid] }, avatars)
        GetFollowRequests.Output(rows.mapNotNull { profiles[it[Follows.authorDid]] }.distinctBy { it.did }, next)
    }

    /** [actor]'s profile; an athlete who blocks [viewer] answers as an unknown one, as `getProfile` does. */
    private fun JdbcTransaction.resolveSubject(viewer: Did?, actor: String): ProfileViewBasic {
        val did =
            resolveActor(actor)?.takeUnless { viewer != null && ownBlock(it, viewer.value) != null }
                ?: throw XrpcError(HttpStatusCode.BadRequest.value, "ActorNotFound", "Unknown actor $actor")
        return basicProfiles(listOf(did), avatars).getValue(did)
    }

    /**
     * The accounts [viewer] blocks, newest first: one entry per account, naming each kind of record that blocks it. Paged by offset, since
     * an athlete's blocks are few; cursor is the offset.
     */
    suspend fun blocks(viewer: Did, params: GetBlocks.Params): GetBlocks.Output = db.tx {
        val rows = Blocks.selectAll().where { Blocks.authorDid eq viewer.value }.toList()
        val bySubject =
            rows
                .groupBy { it[Blocks.subjectDid] }
                .map { (subject, its) ->
                    val sources = its.map { if (it[Blocks.origin] == Origins.REPO) BlockSource.BLUESKY else BlockSource.APP }
                    Triple(subject, sources.distinct().sorted(), its.maxOf { it[Blocks.createdAt] })
                }
                .sortedWith(compareByDescending<Triple<String, List<String>, Long>> { it.third }.thenBy { it.first })
        val (page, next) = offsetPage(bySubject, params.limit, params.cursor)
        val profiles = basicProfiles(page.map { it.first }, avatars)
        val views = page.mapNotNull { (did, sources, at) ->
            profiles[did]?.let { GetBlocks.BlockView(it, sources, Instant.fromEpochMilliseconds(at)) }
        }
        GetBlocks.Output(views, next)
    }

    /** The accounts [viewer] mutes, newest first, paged by offset as [blocks]. */
    suspend fun mutes(viewer: Did, params: GetMutes.Params): GetMutes.Output = db.tx {
        val subjects =
            Mutes.selectAll()
                .where { Mutes.authorDid eq viewer.value }
                .orderBy(Mutes.createdAt to SortOrder.DESC, Mutes.subjectDid to SortOrder.ASC)
                .map { it[Mutes.subjectDid] }
                .distinct()
        val (page, next) = offsetPage(subjects, params.limit, params.cursor)
        val profiles = basicProfiles(page, avatars)
        GetMutes.Output(page.mapNotNull { profiles[it] }, next)
    }

    /**
     * Follows that [match], newest indexed first, as profiles of the [other] side, leaving out accounts in a block with [viewer]. Keyset
     * pagination on (indexed_at, uri) descending; cursor is `"<indexedAt>::<uri>"`.
     */
    private fun JdbcTransaction.followPage(
        viewer: Did?,
        match: Op<Boolean>,
        other: Column<String>,
        limit: Int?,
        cursor: String?,
    ): Pair<List<ProfileViewBasic>, String?> {
        val pageSize = Paging.clamp(limit)
        val rows =
            Follows.select(Follows.uri, Follows.indexedAt, other)
                .where {
                    match and effectiveFollow() and notBlocked(viewer, other) and keysetBefore(Follows.indexedAt, Follows.uri, cursor)
                }
                .orderBy(Follows.indexedAt to SortOrder.DESC, Follows.uri to SortOrder.DESC)
                .limit(pageSize)
                .toList()
        val next = rows.takeIf { it.size == pageSize }?.last()?.let { "${it[Follows.indexedAt]}::${it[Follows.uri]}" }
        val profiles = basicProfiles(rows.map { it[other] }, avatars)
        // An account can be followed more than once (duplicate records); list it once.
        return rows.mapNotNull { profiles[it[other]] }.distinctBy { it.did } to next
    }
}

/** One page of [all] at the offset in [cursor], and the next page's cursor while there may be more. */
private fun <T> offsetPage(all: List<T>, limit: Int?, cursor: String?): Pair<List<T>, String?> {
    val pageSize = Paging.clamp(limit)
    val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val page = all.drop(offset).take(pageSize)
    return page to (offset + pageSize).toString().takeIf { offset + pageSize < all.size }
}
