package io.github.solcott.activitytracker.app.detail

import io.github.solcott.activitytracker.app.SingleFlight
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.launchAction
import io.github.solcott.activitytracker.data.profile.BlockRepository
import io.github.solcott.activitytracker.lexicon.Did

/** The single-flight key of blocking the activity's author from the report sheet. */
private object BlockReportedAuthor

/**
 * The report sheet's block of the reported [author], after its confirmation: the activity's author, blocked directly (its comments are
 * reported from the Comments screen). A failure, or null once it's done, goes to [onError].
 */
internal fun SingleFlight.blockReported(author: Did, blocks: BlockRepository, onError: (UiError?) -> Unit) {
    launchAction(BlockReportedAuthor, UiError.Kind.UpdateBlock, onError) {
        blocks.setBlocked(author, true)
        onError(null)
    }
}
