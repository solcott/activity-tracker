package io.github.solcott.activitytracker.app.interactions

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.moderation.ModerationTexts
import io.github.solcott.activitytracker.app.moderation.NoticeText
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.CommentView
import io.github.solcott.uistate.LoadStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/** What a row of a subject's comment list is (`CommentRow`). */
enum class CommentRowKind {
    /** A listed comment or reply. */
    Comment,

    /** In place of a top-level comment that isn't listed for the viewer, while some of its replies are. */
    Unavailable,

    /** The viewer's comment or reply the AppView doesn't list yet: it can be deleted, not answered. */
    Sent,

    /** "View n more replies" under a thread, its progress, or why they couldn't load. */
    MoreReplies,
}

/** One item of a comment's "…" menu: its words, what it does to [comment], and whether it reads as destructive. */
@Immutable
data class CommentMenuItem(val label: AppText, val action: CommentAction, val comment: AtUri, val destructive: Boolean) {
    /** Delete and Block author ask first ([CommentsTexts.deleteTitle], [CommentsTexts.blockTitle]). */
    val confirms: Boolean
        get() = action == CommentAction.Delete || action == CommentAction.BlockAuthor

    /** What choosing it sends once confirmed; null for Report, which opens the report sheet. */
    fun event(): CommentEvent? =
        when (action) {
            CommentAction.Reply -> CommentEvent.ReplyTo(comment)
            CommentAction.Delete -> CommentEvent.Delete(comment)
            CommentAction.Hide -> CommentEvent.Hide(comment)
            CommentAction.Unhide -> CommentEvent.Unhide(comment)
            CommentAction.MuteAuthor -> CommentEvent.MuteAuthor(comment)
            CommentAction.BlockAuthor -> CommentEvent.BlockAuthor(comment)
            CommentAction.Report -> null
        }
}

/** What a comment action asks before it's done: the alert's [title] and [message], and its destructive [confirm] button. */
@Immutable data class CommentConfirmation(val title: AppText, val message: AppText, val confirm: AppText)

/**
 * One row of a subject's comment list as both UIs order it (feed/comments): a comment, a placeholder, a just-sent comment, or a thread's
 * "View more replies". [key] is stable across pages; [uri] is the comment's, or the thread's for a placeholder or the more row.
 */
@Immutable
data class CommentRow(
    val key: String,
    val kind: CommentRowKind,
    /** Indented under its thread's top-level comment. */
    val isReply: Boolean,
    val uri: AtUri,
    /** The listed comment, for a [CommentRowKind.Comment] row. */
    val comment: CommentView? = null,
    val author: AppText? = null,
    val text: String? = null,
    /** Hidden by the subject's author: dimmed, with [hiddenNote]. */
    val hidden: Boolean = false,
    val hiddenNote: AppText? = null,
    val offersReply: Boolean = false,
    /** The "…" menu's items, in order; empty for no menu. */
    val menu: ImmutableList<CommentMenuItem> = persistentListOf(),
    /** What its labels and its author's call for, above the text. */
    val notices: ImmutableList<NoticeText> = persistentListOf(),
    /** "View n more replies", for a [CommentRowKind.MoreReplies] row while its replies aren't loading or failed. */
    val more: AppText? = null,
    val repliesLoading: Boolean = false,
    val repliesError: UiError? = null,
)

/**
 * What a subject's comment list says and offers (feed/comments), decided once for the shared and the native list (dev/shared-display-text).
 */
@Suppress("TooManyFunctions") // One per piece of the list, so both UIs ask the same.
object CommentsTexts {
    val title: AppText
        get() = AppText.named("detail_section_comments")

    /** "3 comments", hidden ones left out. */
    fun count(state: CommentsState): AppText =
        AppText.Plural("detail_comment_count", state.count.toInt(), persistentListOf(AppText.written(formatWhole(state.count))))

    /**
     * The viewer replies to any listed comment that isn't hidden, deletes their own comments, hides or unhides others' on their own
     * subject, and mutes, blocks or reports the author of anyone else's, Report only with a labeler configured ([reportAvailable]). A
     * placeholder and a just-sent comment never come here.
     */
    fun actions(comment: CommentView, state: CommentsState, reportAvailable: Boolean): ImmutableSet<CommentAction> {
        val hidden = comment.hidden == true
        val manage =
            when {
                comment.author.did == state.viewerDid -> CommentAction.Delete
                !state.isOwn -> null
                hidden -> CommentAction.Unhide
                else -> CommentAction.Hide
            }
        val others = comment.author.did != state.viewerDid
        return listOfNotNull(
                CommentAction.Reply.takeIf { !hidden },
                manage,
                CommentAction.MuteAuthor.takeIf { others },
                CommentAction.BlockAuthor.takeIf { others },
                CommentAction.Report.takeIf { others && reportAvailable },
            )
            .toImmutableSet()
    }

    /** A menu item's words. */
    fun label(action: CommentAction): AppText =
        AppText.named(
            when (action) {
                CommentAction.Reply -> "comment_reply"
                CommentAction.Delete -> "detail_delete"
                CommentAction.Hide -> "hide_comment"
                CommentAction.Unhide -> "unhide_comment"
                CommentAction.MuteAuthor -> "mute_commenter"
                CommentAction.BlockAuthor -> "block_commenter"
                CommentAction.Report -> "report_comment"
            },
        )

    /** [actions] past Reply, a button under the comment, as its menu lists them. */
    fun menu(actions: Set<CommentAction>, comment: AtUri): ImmutableList<CommentMenuItem> =
        actions
            .filter { it != CommentAction.Reply }
            .sortedBy { it.ordinal }
            .map { CommentMenuItem(label(it), it, comment, destructive = it == CommentAction.Delete || it == CommentAction.BlockAuthor) }
            .toImmutableList()

    /** The note under a hidden comment: to the subject's author, that only they and its author see it; to its author, who hid it. */
    fun hiddenNote(state: CommentsState): AppText =
        if (state.isOwn) AppText.named("comment_hidden") else AppText.named("comment_hidden_by", AppText.written(state.author.shownName))

    /** Every row of the list in order (decision 3 of ios-native-post-card), Report left in the menus when [reportAvailable]. */
    fun rows(state: CommentsState, reportAvailable: Boolean): ImmutableList<CommentRow> = buildList {
        for (thread in state.paging.items) {
            val prefix = "thread/${thread.uri.value}"
            val root = thread.root
            add(
                if (root == null) CommentRow("$prefix|root", CommentRowKind.Unavailable, isReply = false, uri = thread.uri)
                else commentRow("$prefix|root", root, isReply = false, state, reportAvailable),
            )
            thread.replies.forEach { add(commentRow("$prefix|r|${it.uri.value}", it, isReply = true, state, reportAvailable)) }
            thread.sentReplies.forEach { add(sentRow("$prefix|s|${it.uri.value}", it, isReply = true)) }
            if (thread.moreReplies > 0 || thread.repliesState is RepliesState.Failed) {
                val replies = thread.repliesState
                add(
                    CommentRow(
                        "$prefix|more",
                        CommentRowKind.MoreReplies,
                        isReply = true,
                        uri = thread.uri,
                        more = moreReplies(thread.moreReplies).takeIf { replies == RepliesState.Idle },
                        repliesLoading = replies == RepliesState.Loading,
                        repliesError = (replies as? RepliesState.Failed)?.error,
                    ),
                )
            }
        }
        state.sent.forEach { add(sentRow("sent/${it.uri.value}", it, isReply = false)) }
    }
        .toImmutableList()

    /** "View 3 more replies". */
    fun moreReplies(count: Int): AppText =
        AppText.Plural("view_more_replies", count, persistentListOf(AppText.written(formatWhole(count.toLong()))))

    val unavailable: AppText
        get() = AppText.named("comment_unavailable")

    /** The next page or a reload of threads is loading. */
    fun loading(state: CommentsState): Boolean = state.paging.refresh is LoadStatus.Loading || state.paging.append is LoadStatus.Loading

    /** Why the first page, a reload or the next page couldn't load, which Retry loads again; null when none failed. */
    fun loadFailure(state: CommentsState): UiError? {
        val failed = (state.paging.refresh as? LoadStatus.Failed) ?: (state.paging.append as? LoadStatus.Failed) ?: return null
        return UiError(UiError.Kind.LoadComments, failed.error)
    }

    /** "Replying to @carol.test" over the field; null for a top-level comment. */
    fun replyingTo(state: CommentsState): AppText? =
        state.replyingTo?.let { AppText.named("comment_replying_to", LabelText.handle(it.author.handle.value)) }

    val cancelReply: AppText
        get() = AppText.named("comment_cancel_reply")

    val placeholder: AppText
        get() = AppText.named("detail_comment_placeholder")

    val send: AppText
        get() = AppText.named("detail_send_comment")

    val more: AppText
        get() = AppText.named("comment_more")

    val deleteTitle: AppText
        get() = AppText.named("delete_comment_title")

    val deleteMessage: AppText
        get() = AppText.named("delete_comment_message")

    /** "Block Carol?", naming the comment's [author]. */
    fun blockTitle(author: AppText): AppText = AppText.named("block_confirm_title", author)

    val blockMessage: AppText
        get() = AppText.named("block_confirm_message")

    val blockConfirm: AppText
        get() = AppText.named("action_block")

    val cancel: AppText
        get() = AppText.named("action_cancel")

    /**
     * What [action] asks before it's done, naming the comment's [author] where it needs to: Delete and Block author ask
     * ([CommentMenuItem.confirms]); every other action is done at once, and asks nothing.
     */
    fun confirmation(action: CommentAction, author: AppText): CommentConfirmation? =
        when (action) {
            CommentAction.Delete -> CommentConfirmation(deleteTitle, deleteMessage, label(CommentAction.Delete))
            CommentAction.BlockAuthor -> CommentConfirmation(blockTitle(author), blockMessage, blockConfirm)
            else -> null
        }

    private fun commentRow(key: String, comment: CommentView, isReply: Boolean, state: CommentsState, report: Boolean): CommentRow {
        val actions = actions(comment, state, report)
        val hidden = comment.hidden == true
        val decision = ModerationDecision.of(comment.labels, comment.author.labels, isOwn = comment.author.did == state.viewerDid)
        return CommentRow(
            key = key,
            kind = CommentRowKind.Comment,
            isReply = isReply,
            uri = comment.uri,
            comment = comment,
            author = AppText.written(comment.author.shownName),
            text = comment.text,
            hidden = hidden,
            hiddenNote = if (hidden) hiddenNote(state) else null,
            offersReply = CommentAction.Reply in actions,
            menu = menu(actions, comment.uri),
            notices = ModerationTexts.notices(decision, isOwn = false).toImmutableList(),
        )
    }

    private fun sentRow(key: String, comment: SentComment, isReply: Boolean): CommentRow =
        CommentRow(
            key = key,
            kind = CommentRowKind.Sent,
            isReply = isReply,
            uri = comment.uri,
            author = AppText.written(comment.author.shownName),
            text = comment.text,
            menu = menu(setOf(CommentAction.Delete), comment.uri),
        )
}
