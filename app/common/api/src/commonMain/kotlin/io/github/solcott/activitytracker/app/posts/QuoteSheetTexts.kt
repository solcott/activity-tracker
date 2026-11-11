package io.github.solcott.activitytracker.app.posts

import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.units.formatWhole

/**
 * What the quote sheet and the delete confirmation (feed/quote-posts) say, for the shared and the native sheet (dev/shared-display-text).
 */
object QuoteSheetTexts {
    val title: AppText
        get() = AppText.named("quote_sheet_title")

    val post: AppText
        get() = AppText.named("quote_sheet_post")

    val cancel: AppText
        get() = AppText.named("action_cancel")

    /** The field's label. */
    val field: AppText
        get() = AppText.named("quote_text_field")

    /** Under the field while the words are longer than a post holds. */
    val tooLong: AppText
        get() = AppText.named("quote_text_too_long", AppText.written(formatWhole(QuoteFields.MAX_TEXT_LENGTH)))

    /** Why only Public is offered. */
    val publicOnly: AppText
        get() = AppText.named("post_share_public_only_sign_in")

    /** The audience row's name. */
    val audienceName: AppText
        get() = AppText.named("visibility_label")

    /** An audience as the picker names it. */
    fun audience(visibility: String): AppText = LabelText.visibility(visibility)

    /** The quoted post's author, over its text. */
    fun quotedAuthor(fields: QuoteFields): AppText = AppText.written(fields.quoting?.author?.shownName.orEmpty())

    val deleteTitle: AppText
        get() = AppText.named("quote_delete_title")

    val deleteMessage: AppText
        get() = AppText.named("quote_delete_message")

    val deleteConfirm: AppText
        get() = AppText.named("quote_delete_confirm")
}
