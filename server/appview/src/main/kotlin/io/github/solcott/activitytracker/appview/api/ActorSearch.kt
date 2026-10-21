package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.tx
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.Paging
import io.github.solcott.activitytracker.lexicon.app.SearchActors
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select

/**
 * Finds athletes by handle, display name or DID. Only accounts in `actors` can match, and the indexer adds an account there only when it
 * writes one of this app's records, so other atproto accounts never show up. Nobody in a block with the viewer is found.
 */
class ActorSearch(private val db: Database, private val avatars: AvatarUrls) {
    suspend fun search(viewer: Did, params: SearchActors.Params): SearchActors.Output {
        val q = normalize(params.q)
        if (q.isEmpty()) return SearchActors.Output(emptyList())
        val pageSize = Paging.clamp(params.limit)
        val offset = params.cursor?.toLongOrNull()?.coerceAtLeast(0) ?: 0
        return db.tx {
            // An unresolvable account's handle is the placeholder handle.invalid, which isn't the athlete's to be found by.
            val hasHandle = Actors.handle neq IdentityResolver.INVALID_HANDLE.value
            val name = Actors.displayName.lowerCase()
            val prefix = LikePattern.ofLiteral(q, ESCAPE) + "%"
            val anywhere = LikePattern("%", ESCAPE) + prefix
            val rank =
                Case()
                    .When((Actors.did eq q) or (hasHandle and (Actors.handle eq q)), intLiteral(RANK_EXACT))
                    .When(hasHandle and (Actors.handle like prefix), intLiteral(RANK_HANDLE_PREFIX))
                    .When((name like prefix) or (name like LikePattern("% ", ESCAPE) + prefix), intLiteral(RANK_NAME_WORD))
                    .Else(intLiteral(RANK_ANYWHERE))
            val rows =
                Actors.select(Actors.did)
                    .where {
                        ((Actors.did eq q) or (hasHandle and (Actors.handle like anywhere)) or (name like anywhere)) and
                            notBlocked(viewer, Actors.did) and
                            listed(viewer, Actors.did)
                    }
                    .orderBy(rank to SortOrder.ASC, Actors.handle to SortOrder.ASC, Actors.did to SortOrder.ASC)
                    .limit(pageSize)
                    .offset(offset)
                    .map { it[Actors.did] }
            val profiles = basicProfiles(rows, avatars)
            SearchActors.Output(rows.mapNotNull { profiles[it] }, (offset + pageSize).toString().takeIf { rows.size == pageSize })
        }
    }

    private companion object {
        const val RANK_EXACT = 0
        const val RANK_HANDLE_PREFIX = 1
        const val RANK_NAME_WORD = 2
        const val RANK_ANYWHERE = 3
        const val ESCAPE = '\\'

        /** Lower case (handles are stored lower case), without the `@` people type before a handle, and within the lexicon's maxLength. */
        fun normalize(q: String): String = q.trim().removePrefix("@").trim().take(SearchActors.MAX_QUERY_LENGTH).lowercase()
    }
}
