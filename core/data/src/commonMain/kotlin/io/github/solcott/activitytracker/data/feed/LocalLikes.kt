package io.github.solcott.activitytracker.data.feed

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.PostViewerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * The viewer's likes toggled this session, keyed by subject URI (null once removed). The AppView only learns about a toggle once it comes
 * back through Jetstream, so its reads are laid over with this until then.
 */
@SingleIn(SessionScope::class)
@Inject
class LocalLikes {
    val overrides: StateFlow<Map<String, AtUri?>>
        field = MutableStateFlow(emptyMap())

    /** The viewer's like on [subject], preferring what was toggled locally over the (possibly stale) view it came from. */
    fun current(subject: InteractionSubject): AtUri? =
        overrides.value.let { if (subject.uri.value in it) it[subject.uri.value] else subject.like }

    fun set(subject: AtUri, like: AtUri?) = overrides.update { it + (subject.value to like) }

    /** Drops the override that set [like]: the athlete discarded it before it was sent (data/outbox). */
    fun forget(like: AtUri) = overrides.update { held -> held.filterValues { it != like } }

    fun applyTo(view: ActivityView): ActivityView = view.withLikeOverrides(overrides.value)

    fun applyTo(post: PostView): PostView = post.withLikeOverrides(overrides.value)
}

/** [ActivityView.withLikeOverrides] for a post (feed/posts). */
fun PostView.withLikeOverrides(overrides: Map<String, AtUri?>): PostView {
    if (uri.value !in overrides) return this
    val mine = overrides[uri.value]
    val delta = likeDelta(mine, reported = viewer?.like != null)
    return copy(likeCount = (likeCount + delta).coerceAtLeast(0), viewer = (viewer ?: PostViewerState()).copy(like = mine))
}

/** How far a local like ([mine]) moves a count that [reported] the viewer's like or not. */
private fun likeDelta(mine: AtUri?, reported: Boolean): Int =
    when {
        mine != null && !reported -> 1
        mine == null && reported -> -1
        else -> 0
    }

/** Replaces the viewer's like with any local override, adjusting the count by the difference from what the view reported. */
fun ActivityView.withLikeOverrides(overrides: Map<String, AtUri?>): ActivityView {
    if (uri.value !in overrides) return this
    val mine = overrides[uri.value]
    val delta = likeDelta(mine, reported = viewer?.like != null)
    return copy(
        likeCount = (likeCount + delta).coerceAtLeast(0),
        viewer = (viewer ?: ActivityViewerState()).copy(like = mine),
    )
}
