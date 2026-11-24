package io.github.solcott.activitytracker.app.interactions

import androidx.compose.runtime.Immutable
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.text.AppText
import io.github.solcott.activitytracker.data.feed.SentComment
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.Did
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.TimeZone

// A subject's comments block on its page (feed/comments): the count, the first comments in brief, and the ways to the Comments screen.

/**
 * The comments block an activity's or a post's page shows near its top: [count] comments (hidden ones left out, moved by the viewer's own
 * changes), which its counts row shows by a comment icon, the first [rows] in brief, or "Loading comments…" while the first page is
 * [loading], and its events, which open the subject's Comments screen.
 */
@Immutable
data class CommentsPreview(
    val count: Long,
    val rows: ImmutableList<CommentPreviewRow> = persistentListOf(),
    val loading: Boolean = false,
    val eventSink: (CommentsPreviewEvent) -> Unit = {},
) {
    /** "14 comments": the comment count's spoken name beside its icon. */
    val countText: AppText
        get() = AppText.Plural("detail_comment_count", count.toInt(), persistentListOf(AppText.written(formatWhole(count))))

    /** The count alone, "14", shown beside the comment icon. */
    val countNumber: String
        get() = formatWhole(count)

    /** "Loading comments…" while the first page is on its way and there are comments to show. */
    val loadingText: AppText?
        get() = if (loading && count > 0 && rows.isEmpty()) AppText.named("comments_loading") else null

    /** "View all" while there are more than the preview shows (the count is in the counts row); null otherwise, and while loading. */
    val viewAll: AppText?
        get() = if (count > rows.size && loadingText == null) AppText.named("comments_view_all") else null

    /** The row that opens the Comments screen ready to type: "Add a comment". */
    val addComment: AppText
        get() = CommentsTexts.placeholder

    companion object {
        /** How many comments the block shows at most. */
        const val MAX_ROWS: Int = 2

        /**
         * The block for a subject with [count] comments, from its first page of [threads] and the viewer's ([viewer]) just-sent top-level
         * [sent] comments: the first [MAX_ROWS] listed top-level comments that aren't hidden and whose labels and author's call for
         * nothing, then the viewer's just-sent ones while there's room. Replies and placeholders never show here. [loading] is whether the
         * first page has yet to answer.
         */
        @Suppress("LongParameterList") // The count, the page and the viewer's own, and how to open the screen.
        fun of(
            count: Long,
            threads: List<CommentThread>,
            sent: List<SentComment>,
            viewer: Did,
            loading: Boolean = false,
            eventSink: (CommentsPreviewEvent) -> Unit = {},
        ): CommentsPreview {
            val listed =
                threads
                    .asSequence()
                    .mapNotNull { it.root }
                    .filter { it.hidden != true }
                    .filter { comment ->
                        ModerationDecision.of(comment.labels, comment.author.labels, isOwn = comment.author.did == viewer) ==
                            ModerationDecision.None
                    }
            val rows =
                (listed.map { CommentPreviewRow(it.uri.value, it.author.shownName, it.text, it.createdAt) } +
                        sent
                            .asSequence()
                            .filter { it.root == null }
                            .map { CommentPreviewRow(it.uri.value, it.author.shownName, it.text, it.createdAt) })
                    .take(MAX_ROWS)
                    .toList()
            return CommentsPreview(count, rows.toImmutableList(), loading, eventSink)
        }
    }
}

/**
 * One comment in brief: [author]'s name, when it was written ([createdAt], which [posted] says), and the start of its [text], which both
 * UIs cut to two lines. [key] is its URI.
 */
@Immutable
data class CommentPreviewRow(val key: String, val author: String, val text: String, val createdAt: Instant) {
    /** "5 minutes ago" or "Yesterday at 6:12 PM", as the Comments screen says it ([CommentsTexts.posted]). */
    fun posted(now: Instant, zone: TimeZone): AppText = CommentsTexts.posted(createdAt, now, zone)
}

/** What the comments block does: both open the subject's Comments screen. */
sealed interface CommentsPreviewEvent {
    /** The comment count, a row or "View all": the Comments screen. */
    data object OpenAll : CommentsPreviewEvent

    /** "Add a comment": the Comments screen with its field focused. */
    data object Compose : CommentsPreviewEvent
}
