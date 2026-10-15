package io.github.solcott.activitytracker.data.feed

import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.activitytracker.lexicon.app.GetActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Comments the athlete hid (true) or unhid (false) on their own activities this session, by URI. The AppView serves them as they were until
 * it indexes the `feed.commentHide` write or delete, so its reads are laid over with these until then, as [LocalLikes] does for likes.
 */
class LocalCommentHides {
    private val hidden = MutableStateFlow(emptyMap<String, Boolean>())

    fun set(comment: AtUri, isHidden: Boolean) = hidden.update { it + (comment.value to isHidden) }

    /** Whether [comment] is hidden, preferring what was toggled here over the (possibly stale) view. */
    fun isHidden(comment: CommentView): Boolean = hidden.value[comment.uri.value] ?: (comment.hidden == true)

    /**
     * [output] with each comment toggled here marked as it was toggled, and its activity's count moved by the difference from what the
     * AppView reported: down for one hidden here that it still shows, up for one unhidden here that it still hides. So once it catches up,
     * nothing moves twice.
     */
    fun applyTo(output: GetActivity.Output): GetActivity.Output {
        val overrides = hidden.value
        var delta = 0
        val comments =
            output.comments.map { comment ->
                val mine = overrides[comment.uri.value] ?: return@map comment
                val reported = comment.hidden == true
                if (mine == reported) return@map comment
                delta += if (mine) -1 else 1
                comment.copy(hidden = true.takeIf { mine })
            }
        if (delta == 0) return output
        val activity = output.activity
        return output.copy(activity = activity.copy(commentCount = (activity.commentCount + delta).coerceAtLeast(0)), comments = comments)
    }
}
