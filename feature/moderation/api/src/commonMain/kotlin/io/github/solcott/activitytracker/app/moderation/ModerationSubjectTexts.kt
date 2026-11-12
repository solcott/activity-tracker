package io.github.solcott.activitytracker.app.moderation

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.lexicon.app.CommentView

/**
 * What the Moderation page says that follows a rule, and when it offers Open (moderation/private-subjects, dev/shared-display-text). The
 * shared `ModerationUi` and the native iOS page ask them.
 */
object ModerationSubjectTexts {
    /** "Visible to: Followers": who may see the subject, by its `Visibility` value; one the app doesn't know reads as Only me. */
    fun visibility(visibility: String): AppText = AppText.named("moderation_visibility", LabelText.visibility(visibility))

    /** The comment's author: their display name, else their `@handle`. */
    fun commentAuthor(comment: CommentView): AppText =
        comment.author.displayName?.let(AppText::written) ?: LabelText.handle(comment.author.handle.value)

    /** Open is held while a subject opens. */
    fun canOpen(state: ModerationState): Boolean = !state.loading
}
