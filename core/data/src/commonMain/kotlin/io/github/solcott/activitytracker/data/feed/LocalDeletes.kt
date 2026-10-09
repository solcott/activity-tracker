package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Activities the athlete deleted this session, and recordings they split off their group, by URI. The AppView serves both as they were
 * until Jetstream brings it the change, so its reads are laid over with these until then. A TID is never reused, so nothing here needs to
 * expire.
 */
class LocalDeletes {
    private val deleted = MutableStateFlow(emptySet<String>())
    private val ungrouped = MutableStateFlow(emptySet<String>())

    fun delete(uri: AtUri) = deleted.update { it + uri.value }

    fun ungroup(recording: AtUri) = ungrouped.update { it + recording.value }

    operator fun contains(uri: AtUri): Boolean = uri.value in deleted.value

    /** [views] without the deleted activities, and each main activity without the recordings deleted or split off. */
    fun filter(views: List<ActivityView>): List<ActivityView> = views.filter { it.uri !in this }.map { applyTo(it) }

    /** [view] without the recordings deleted or split off from it. */
    fun applyTo(view: ActivityView): ActivityView = view.withoutRecordings(deleted.value + ungrouped.value)
}

/** This activity without the recordings in [uris] listed; no list at all once none are left, as for an activity recorded once. */
internal fun ActivityView.withoutRecordings(uris: Set<String>): ActivityView {
    val listed = recordings ?: return this
    val kept = listed.filterNot { it.uri.value in uris }
    return if (kept.size == listed.size) this else copy(recordings = kept.ifEmpty { null })
}
