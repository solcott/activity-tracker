package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.data.activity.HeldPhotos
import io.github.solcott.activitytracker.data.activity.mediaViews
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityRecord
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Activity records the athlete rewrote this session (a merge or an edit), by URI. The AppView serves the old record until Jetstream brings
 * it the new one, so its reads are laid over with these until they match. A photo the AppView hasn't indexed yet shows from the bytes this
 * device uploaded ([held]), or, for a public activity, from the athlete's PDS ([ownBlobUrl]).
 */
class LocalRewrites(private val held: HeldPhotos, private val ownBlobUrl: (cid: String) -> String) {
    private val records = MutableStateFlow(emptyMap<String, ActivityRecord>())

    fun set(uri: AtUri, record: ActivityRecord) = records.update { it + (uri.value to record) }

    /** The record the athlete rewrote [uri] to, while the AppView hasn't caught up; null otherwise. */
    fun observe(uri: AtUri): Flow<ActivityRecord?> = records.map { it[uri.value] }.distinctUntilChanged()

    /**
     * [view] with the record the athlete rewrote it to, until the AppView serves that record itself. A blob the rewrite added is uploaded
     * after it's queued, so only the AppView's copy has its ref.
     */
    fun applyTo(view: ActivityView): ActivityView {
        val mine = records.value[view.uri.value]
        val caughtUp = mine != null && view.record.copy(track = null) == mine.copy(track = null)
        if (caughtUp) records.update { it - view.uri.value }
        return if (mine == null || caughtUp) view else rewritten(view, mine)
    }

    /** [view] showing [record], which the athlete just wrote, photos and all. */
    fun rewritten(view: ActivityView, record: ActivityRecord): ActivityView =
        view.copy(
            record = record.copy(track = record.track ?: view.record.track),
            media = mediaViews(record, view.media, view.visibilityOrPublic, held, ownBlobUrl),
        )
}
