package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.visibility.uiErrorOf
import io.github.solcott.activitytracker.atproto.runSuspendCatching
import io.github.solcott.activitytracker.data.moderation.ReportTarget
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.lexicon.app.ActivityView

/** The single-flight key of blocking the activity's author from the report sheet. */
private object BlockReportedAuthor

/**
 * The report sheet's block of [target]'s author, after its confirmation: for a reported comment it goes the way the comment menu's does, so
 * their comments leave the list with their count; the activity's author is blocked directly. A failure, or null once it's done, goes to
 * [onError].
 */
internal fun SingleFlight.blockReported(
    target: ReportTarget,
    current: ActivityView?,
    commentActions: (ActivityView) -> CommentActions,
    blocks: BlockRepository,
    onError: (UiError?) -> Unit,
) {
    if (target is ReportTarget.Comment && current != null) {
        commentActions(current).on(ActivityDetailEvent.BlockCommenter(target.uri), current)
        return
    }
    launch(BlockReportedAuthor) {
        runSuspendCatching { blocks.setBlocked(target.author, true) }
            .onSuccess { onError(null) }
            .onFailure { onError(uiErrorOf(UiError.Kind.UpdateBlock, it)) }
    }
}
