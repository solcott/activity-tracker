package io.github.solcott.activitytracker.app.names

import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText

/**
 * What the place-name sheet (segments/segment-names, feed/posts) says, chosen once for the shared and the native sheets
 * (dev/shared-display-text). Its conditions are [ProposePlaceNameState]'s.
 */
object ProposePlaceNameTexts {
    /** "Propose a name", or "Edit your proposal". */
    fun title(state: ProposePlaceNameState): AppText =
        AppText.named(if (state.editing) "segment_name_sheet_edit_title" else "segment_name_sheet_propose_title")

    /** Send for a new proposal, Save for an edit. */
    fun confirm(state: ProposePlaceNameState): AppText =
        AppText.named(if (state.editing) "segment_name_sheet_save" else "segment_name_sheet_send")

    /** Before the field: what proposing shows others, or that saving an edited name clears its votes. */
    fun lead(state: ProposePlaceNameState): AppText =
        AppText.named(if (state.editing) "segment_name_edit_warning" else "segment_name_disclosure")

    val field: AppText
        get() = AppText.named("segment_name_field")

    /** Under the field while [ProposePlaceNameState.showsInvalid]. */
    val invalid: AppText
        get() = AppText.named("segment_name_invalid")

    val shareToFeed: AppText
        get() = AppText.named("post_share_to_feed")

    val shareToFeedDetail: AppText
        get() = AppText.named("post_share_to_feed_detail")

    /** The audience picker's label. */
    val audience: AppText
        get() = AppText.named("visibility_label")

    /** How the audience [visibility] reads among the choices. */
    fun audience(visibility: String): AppText = LabelText.visibility(visibility)

    /** Why only Public is offered, or null when Followers and Mutuals are. */
    fun shareLimit(state: ProposePlaceNameState): AppText? =
        when (state.shareLimit) {
            ShareLimit.None -> null
            ShareLimit.SignInAgain -> AppText.named("post_share_public_only_sign_in")
            ShareLimit.SpacesUnsupported -> AppText.named("post_share_public_only_unsupported")
        }

    val shareText: AppText
        get() = AppText.named("post_share_text_field")

    /** Under the share text while it's too long. The limit is written as the shared sheet always wrote it, ungrouped. */
    val shareTextTooLong: AppText
        get() = AppText.named("post_share_text_too_long", AppText.written(ProposePlaceNameState.MAX_SHARE_TEXT_LENGTH.toString()))

    /** The most characters the name holds. */
    val nameLimit: Int
        get() = ProposePlaceNameState.MAX_NAME_LENGTH

    /** The most characters the share text holds. */
    val shareTextLimit: Int
        get() = ProposePlaceNameState.MAX_SHARE_TEXT_LENGTH

    /** The confirmation before the viewer's own proposal is deleted. */
    val deleteConfirmTitle: AppText
        get() = AppText.named("segment_name_delete_confirm_title")

    val deleteConfirmMessage: AppText
        get() = AppText.named("segment_name_delete_confirm_message")

    val delete: AppText
        get() = AppText.named("segment_name_delete")
}
