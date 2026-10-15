package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Activities the athlete deleted this session, recordings they split off their group, and comments they deleted, by URI. The AppView serves
 * them as they were until it catches up with the change, so its reads are laid over with these until then. A TID is never reused, so
 * nothing here needs to expire.
 */
class LocalDeletes {
    private val deleted = MutableStateFlow(emptySet<String>())
    private val ungrouped = MutableStateFlow(emptySet<String>())
    private val comments = MutableStateFlow(emptySet<String>())

    fun delete(uri: AtUri) = deleted.update { it + uri.value }

    fun ungroup(recording: AtUri) = ungrouped.update { it + recording.value }

    fun deleteComment(uri: AtUri) = comments.update { it + uri.value }

    operator fun contains(uri: AtUri): Boolean = uri.value in deleted.value

    /** [views] without the deleted activities, and each main activity without the recordings deleted or split off. */
    fun filter(views: List<ActivityView>): List<ActivityView> = views.filter { it.uri !in this }.map { applyTo(it) }

    /** [view] without the recordings deleted or split off from it. */
    fun applyTo(view: ActivityView): ActivityView = view.withoutRecordings(deleted.value + ungrouped.value)

    /**
     * [output] without the deleted comments, and its activity's count lowered by as many as were left out: only those the AppView still
     * has, so a comment it already dropped isn't taken off twice, and not hidden ones, which it never counted.
     */
    fun applyTo(output: GetActivity.Output): GetActivity.Output {
        val (gone, kept) = output.comments.partition { it.uri.value in comments.value }
        if (gone.isEmpty()) return output
        val dropped = gone.count { it.hidden != true }
        val activity = output.activity
        return output.copy(activity = activity.copy(commentCount = (activity.commentCount - dropped).coerceAtLeast(0)), comments = kept)
    }
}

/** This activity without the recordings in [uris] listed; no list at all once none are left, as for an activity recorded once. */
internal fun ActivityView.withoutRecordings(uris: Set<String>): ActivityView {
    val listed = recordings ?: return this
    val kept = listed.filterNot { it.uri.value in uris }
    return if (kept.size == listed.size) this else copy(recordings = kept.ifEmpty { null })
}
