package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.appview.db.Actors
import io.github.solcott.activitytracker.appview.db.Likes
import io.github.solcott.activitytracker.atproto.identity.IdentityResolver
import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.Handle
import io.github.solcott.activitytracker.lexicon.app.ProfileViewBasic
import io.github.solcott.activitytracker.lexicon.isDid
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

// Query helpers shared by [Views] and [GraphViews]; cursors and paging are in Paging.kt.

/** The DID [actor] names: a DID, or a verified handle. `handle.invalid` names no one (identity/handle-verification). */
internal fun JdbcTransaction.resolveActor(actor: String): String? {
    val handle = actor.lowercase()
    if (!actor.isDid() && handle == IdentityResolver.INVALID_HANDLE.value) return null
    val condition = if (actor.isDid()) Actors.did eq actor else Actors.handle eq handle
    return Actors.select(Actors.did).where(condition).firstOrNull()?.get(Actors.did)
}

/**
 * The DID [actor] names, for a query about that athlete, or null when [viewer] may not know of them: unknown, an athlete who blocks the
 * viewer (answered exactly as an unknown one; the viewer's own block shows in `viewer.blocking`), or an account not served to the viewer
 * (inactive or taken down). Every query naming an athlete resolves them through this, so each answers the same way.
 */
internal fun JdbcTransaction.resolveShownActor(viewer: Did?, actor: String): String? =
    resolveActor(actor)?.takeIf { did -> (viewer == null || ownBlock(did, viewer.value) == null) && isAccountServed(did, viewer) }

/** [resolveShownActor], or `ActorNotFound` when [viewer] may not know of [actor], as every query naming an athlete answers. */
internal fun JdbcTransaction.requireShownActor(viewer: Did?, actor: String): String =
    resolveShownActor(viewer, actor) ?: throw XrpcError(HttpStatusCode.BadRequest.value, "ActorNotFound", "Unknown actor $actor")

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
