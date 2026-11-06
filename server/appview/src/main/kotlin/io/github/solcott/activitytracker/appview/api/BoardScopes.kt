package io.github.solcott.activitytracker.appview.api

import io.github.solcott.activitytracker.lexicon.Did
import io.github.solcott.activitytracker.lexicon.app.LeaderboardScope
import io.ktor.http.HttpStatusCode
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.or

// The scope every rank board (segment leaderboards, Mayors, dive-site Regulars) takes: everyone, or the viewer and whom they follow.

/**
 * Whether [scope] (a [LeaderboardScope] value, null for everyone) is the following scope; `InvalidRequest` when unknown, or without a
 * [viewer].
 */
internal fun followingScope(viewer: Did?, scope: String?): Boolean {
    val chosen = scope ?: LeaderboardScope.EVERYONE
    if (chosen !in LeaderboardScope.all) invalidScope("Unknown scope $chosen")
    val following = chosen == LeaderboardScope.FOLLOWING
    if (following && viewer == null) invalidScope("The following scope needs a signed-in viewer")
    return following
}

/** With [following], rows whose [author] is [viewer] or someone they effectively follow ([effectivelyFollowedBy]); every row otherwise. */
internal fun followedOrOwn(author: Column<String>, viewer: Did?, following: Boolean): Op<Boolean> {
    val own = viewer?.value
    return if (following && own != null) (author eq own) or (author inSubQuery effectivelyFollowedBy(own)) else Op.TRUE
}

private fun invalidScope(message: String): Nothing = throw XrpcError(HttpStatusCode.BadRequest.value, "InvalidRequest", message)
