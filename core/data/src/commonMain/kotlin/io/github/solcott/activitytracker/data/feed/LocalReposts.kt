package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import io.github.solcott.activitytracker.lexicon.app.TimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The viewer's reposts toggled this session (feed/reposts), keyed by post URI (null once undone). The AppView only learns about a toggle
 * once it comes back through Jetstream, so its reads are laid over with this until then, as [LocalLikes] does for likes.
 */
class LocalReposts {
    val overrides: StateFlow<Map<String, AtUri?>>
        field = MutableStateFlow(emptyMap())

    /** The viewer's repost of [post], preferring what was toggled locally over the (possibly stale) view it came from. */
    fun current(post: PostView): AtUri? = overrides.value.let { if (post.uri.value in it) it[post.uri.value] else post.viewer?.repost }

    fun set(post: AtUri, repost: AtUri?) = overrides.update { it + (post.value to repost) }

    /** Drops the override that set [repost]: the athlete discarded it before it was sent (data/outbox). */
    fun forget(repost: AtUri) = overrides.update { held -> held.filterValues { it != repost } }

    fun applyTo(post: PostView): PostView = post.withRepostOverrides(overrides.value)
}

/** Replaces the viewer's repost of this post with any local override, moving the count by the difference from what the view reported. */
fun PostView.withRepostOverrides(overrides: Map<String, AtUri?>): PostView {
    if (uri.value !in overrides) return this
    val mine = overrides[uri.value]
    val delta = toggleDelta(mine, reported = viewer?.repost != null)
    return copy(repostCount = (repostCount + delta).coerceAtLeast(0), viewer = (viewer ?: PostViewerState()).copy(repost = mine))
}

/** [post] as this session's likes and reposts leave it ([LocalLikes], [LocalReposts]). */
internal fun PostView.withToggles(likes: Map<String, AtUri?>, reposts: Map<String, AtUri?>): PostView =
    withLikeOverrides(likes).withRepostOverrides(reposts)

/** A timeline item's post, as [withToggles] leaves it; anything else as it is. */
internal fun TimelineItem.withToggles(likes: Map<String, AtUri?>, reposts: Map<String, AtUri?>): TimelineItem =
    when (this) {
        is TimelineItem.Post -> TimelineItem.Post(post.withToggles(likes, reposts))
        is TimelineItem.Repost -> TimelineItem.Repost(reposted.copy(post = reposted.post.withToggles(likes, reposts)))
        else -> this
    }
