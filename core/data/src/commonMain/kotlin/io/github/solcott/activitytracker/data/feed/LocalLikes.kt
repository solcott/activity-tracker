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
 * The viewer's kudos toggled this session, keyed by subject URI (null once removed). The AppView only learns about a toggle once it comes
 * back through Jetstream, so its reads are laid over with this until then.
 */
@SingleIn(SessionScope::class)
@Inject
class LocalKudos {
    val overrides: StateFlow<Map<String, AtUri?>>
        field = MutableStateFlow(emptyMap())

    /** The viewer's kudos on [activity], preferring what was toggled locally over the (possibly stale) view. */
    fun current(activity: ActivityView): AtUri? =
        overrides.value.let { if (activity.uri.value in it) it[activity.uri.value] else activity.viewer?.kudos }

    fun set(subject: AtUri, kudos: AtUri?) = overrides.update { it + (subject.value to kudos) }

    fun applyTo(view: ActivityView): ActivityView = view.withKudosOverrides(overrides.value)
}

/** Replaces the viewer's kudos with any local override, adjusting the count by the difference from what the view reported. */
fun ActivityView.withKudosOverrides(overrides: Map<String, AtUri?>): ActivityView {
    if (uri.value !in overrides) return this
    val mine = overrides[uri.value]
    val reported = viewer?.kudos != null
    val delta =
        when {
            mine != null && !reported -> 1
            mine == null && reported -> -1
            else -> 0
        }
    return copy(
        kudosCount = (kudosCount + delta).coerceAtLeast(0),
        viewer = (viewer ?: ActivityViewerState()).copy(kudos = mine),
    )
}
