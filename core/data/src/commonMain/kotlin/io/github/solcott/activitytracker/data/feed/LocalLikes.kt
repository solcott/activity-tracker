package io.github.solcott.activitytracker.data.feed

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.solcott.activitytracker.data.di.SessionScope
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.ActivityViewerState
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

    /** The viewer's like on [activity], preferring what was toggled locally over the (possibly stale) view. */
    fun current(activity: ActivityView): AtUri? =
        overrides.value.let { if (activity.uri.value in it) it[activity.uri.value] else activity.viewer?.like }

    fun set(subject: AtUri, like: AtUri?) = overrides.update { it + (subject.value to like) }

    /** Drops the override that set [like]: the athlete discarded it before it was sent (data/outbox). */
    fun forget(like: AtUri) = overrides.update { held -> held.filterValues { it != like } }

    fun applyTo(view: ActivityView): ActivityView = view.withLikeOverrides(overrides.value)
}

/** Replaces the viewer's like with any local override, adjusting the count by the difference from what the view reported. */
fun ActivityView.withLikeOverrides(overrides: Map<String, AtUri?>): ActivityView {
    if (uri.value !in overrides) return this
    val mine = overrides[uri.value]
    val reported = viewer?.like != null
    val delta =
        when {
            mine != null && !reported -> 1
            mine == null && reported -> -1
            else -> 0
        }
    return copy(
        likeCount = (likeCount + delta).coerceAtLeast(0),
        viewer = (viewer ?: ActivityViewerState()).copy(like = mine),
    )
}
