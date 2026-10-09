package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Activity records the athlete rewrote this session (a merge), by URI. The AppView serves the old record until Jetstream brings it the new
 * one, so its reads are laid over with these until they match.
 */
class LocalRewrites {
    private val records = MutableStateFlow(emptyMap<String, ActivityRecord>())

    fun set(uri: AtUri, record: ActivityRecord) = records.update { it + (uri.value to record) }

    /**
     * [view] with the record the athlete rewrote it to, until the AppView serves that record itself. A blob the rewrite added is uploaded
     * after it's queued, so only the AppView's copy has its ref.
     */
    fun applyTo(view: ActivityView): ActivityView {
        val mine = records.value[view.uri.value]
        val caughtUp = mine != null && view.record.copy(track = null) == mine.copy(track = null)
        if (caughtUp) records.update { it - view.uri.value }
        return if (mine == null || caughtUp) view else view.copy(record = mine.copy(track = mine.track ?: view.record.track))
    }
}
