package io.github.solcott.activitytracker.app.posts

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.AuthorHeader
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_report
import io.github.solcott.activitytracker.composeApp.resources.comments_count
import io.github.solcott.activitytracker.composeApp.resources.like_content_description
import io.github.solcott.activitytracker.composeApp.resources.like_count
import io.github.solcott.activitytracker.composeApp.resources.post_card_counts
import io.github.solcott.activitytracker.composeApp.resources.post_card_dive_site
import io.github.solcott.activitytracker.composeApp.resources.post_card_more
import io.github.solcott.activitytracker.composeApp.resources.post_card_open
import io.github.solcott.activitytracker.composeApp.resources.post_card_open_place
import io.github.solcott.activitytracker.composeApp.resources.post_card_proposes
import io.github.solcott.activitytracker.composeApp.resources.post_card_segment
import io.github.solcott.activitytracker.composeApp.resources.post_card_unavailable
import io.github.solcott.activitytracker.composeApp.resources.post_reposted_by
import io.github.solcott.activitytracker.composeApp.resources.post_reposted_by_open
import io.github.solcott.activitytracker.composeApp.resources.post_reposted_by_others
import io.github.solcott.activitytracker.composeApp.resources.post_reposted_by_you
import io.github.solcott.activitytracker.composeApp.resources.post_show_quotes
import io.github.solcott.activitytracker.composeApp.resources.post_status_adopted
import io.github.solcott.activitytracker.composeApp.resources.post_status_closed
import io.github.solcott.activitytracker.composeApp.resources.post_status_expired
import io.github.solcott.activitytracker.composeApp.resources.post_status_open
import io.github.solcott.activitytracker.composeApp.resources.post_status_rejected
import io.github.solcott.activitytracker.composeApp.resources.post_status_superseded
import io.github.solcott.activitytracker.composeApp.resources.quote_content_description
import io.github.solcott.activitytracker.composeApp.resources.quote_count
import io.github.solcott.activitytracker.composeApp.resources.quote_delete
import io.github.solcott.activitytracker.composeApp.resources.quoted_post_link
import io.github.solcott.activitytracker.composeApp.resources.quoted_post_open
import io.github.solcott.activitytracker.composeApp.resources.quoted_post_unavailable
import io.github.solcott.activitytracker.composeApp.resources.repost_content_description
import io.github.solcott.activitytracker.composeApp.resources.repost_count
import io.github.solcott.activitytracker.composeApp.resources.segment_name_totals
import io.github.solcott.activitytracker.composeApp.resources.segment_name_vote_down
import io.github.solcott.activitytracker.composeApp.resources.segment_name_vote_up
import io.github.solcott.activitytracker.composeApp.resources.undo_repost_content_description
import io.github.solcott.activitytracker.data.feed.isPending
import io.github.solcott.activitytracker.data.units.formatWhole
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.QuotedPost
import io.github.solcott.activitytracker.lexicon.app.QuotedPostEmbed
import io.github.solcott.activitytracker.lexicon.app.ReasonRepost
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One post (feed/posts) in a timeline, on a profile or atop its own page: its author and audience, their text, the proposal it shares as it
 * stands now, its name, status and totals, with up and down votes when the viewer may vote, or for a quote (feed/quote-posts) the quoted
 * post one level deep, and its like, comment, repost and quote counts, with like and repost buttons on someone else's post and Quote on
 * any. A tap opens the post page (feed/post-page); the segment or dive site line opens that, the quoted post its page, and the author their
 * profile. In a timeline through a repost (feed/reposts), [reason] heads it with who reposted it, which opens their profile. [canReport]
 * offers Report on someone else's post; the viewer's own quote offers Delete. [onQuoteCount], on the post page, lists its quotes.
 */
@Composable
fun PostCard(
    post: PostView,
    viewerDid: String,
    canReport: Boolean,
    onEvent: (PostEvent) -> Unit,
    modifier: Modifier = Modifier,
    reason: ReasonRepost? = null,
    onQuoteCount: (() -> Unit)? = null,
) {
    val openLabel = stringResource(Res.string.post_card_open)
    val own = post.author.did.value == viewerDid
    Card(modifier.fillMaxWidth().clickable(onClickLabel = openLabel) { onEvent(PostEvent.OpenPost(post)) }.handCursor()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            reason?.let { RepostedBy(it, viewerDid, onEvent) }
            Header(post, own, canReport, onEvent)
            post.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            when (val embed = post.embed) {
                is PostEmbedView.Proposal -> Proposal(post, embed, onEvent)
                is PostEmbedView.Quote -> QuotedPost(embed.quoted, onEvent)
                is PostEmbedView.Unknown ->
                    Text(
                        stringResource(Res.string.post_card_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Counts(post, own, onEvent, onQuoteCount)
        }
    }
}

/** "Reposted by …" above a post in the timeline through a repost (feed/reposts), opening the reposter's profile. */
@Composable
private fun RepostedBy(reason: ReasonRepost, viewerDid: String, onEvent: (PostEvent) -> Unit) {
    val text =
        when {
            reason.by.did.value == viewerDid -> stringResource(Res.string.post_reposted_by_you)
            reason.others > 0 ->
                pluralStringResource(Res.plurals.post_reposted_by_others, reason.others.toInt(), reason.by.shownName, reason.others.toInt())
            else -> stringResource(Res.string.post_reposted_by, reason.by.shownName)
        }
    Row(
        Modifier.testTag(TestTags.POST_REPOSTED_BY)
            .clickable(onClickLabel = stringResource(Res.string.post_reposted_by_open)) { onEvent(PostEvent.OpenReposter(reason.by)) }
            .handCursor(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformIcon(AppIcon.Repost, contentDescription = null, size = 16.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The like and repost buttons on someone else's post, Quote on any, and the post's like, comment, repost and quote counts as the viewer is
 * shown them; the quote count lists the quotes when [onQuoteCount] is given.
 */
@Composable
private fun Counts(post: PostView, own: Boolean, onEvent: (PostEvent) -> Unit, onQuoteCount: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!own) {
            val given = post.viewer?.like != null
            AppIconButton(
                AppIcon.Like,
                contentDescription = stringResource(Res.string.like_content_description),
                onClick = { onEvent(PostEvent.Like(post)) },
                modifier = Modifier.testTag(TestTags.POST_LIKE),
                filled = given,
                tint = if (given) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
            )
            val reposted = post.viewer?.repost != null
            AppIconButton(
                AppIcon.Repost,
                contentDescription =
                    stringResource(if (reposted) Res.string.undo_repost_content_description else Res.string.repost_content_description),
                onClick = { onEvent(PostEvent.Repost(post)) },
                modifier = Modifier.testTag(TestTags.POST_REPOST),
                filled = reposted,
                tint = if (reposted) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
            )
        }
        // A post sent this session can be quoted once it's listed: a quote names it by its CID (feed/quote-posts).
        if (!post.isPending) {
            AppIconButton(
                AppIcon.Quote,
                contentDescription = stringResource(Res.string.quote_content_description),
                onClick = { onEvent(PostEvent.Quote(post)) },
                modifier = Modifier.testTag(TestTags.POST_QUOTE),
            )
        }
        val quotes = pluralStringResource(Res.plurals.quote_count, post.quoteCount.toInt(), formatWhole(post.quoteCount))
        Text(
            stringResource(
                Res.string.post_card_counts,
                pluralStringResource(Res.plurals.like_count, post.likeCount.toInt(), formatWhole(post.likeCount)),
                pluralStringResource(Res.plurals.comments_count, post.commentCount.toInt(), formatWhole(post.commentCount)),
                pluralStringResource(Res.plurals.repost_count, post.repostCount.toInt(), formatWhole(post.repostCount)),
                quotes,
            ),
            modifier =
                if (onQuoteCount == null) Modifier
                else
                    Modifier.testTag(TestTags.POST_QUOTE_COUNT)
                        .clickable(onClickLabel = stringResource(Res.string.post_show_quotes)) { onQuoteCount() }
                        .handCursor(),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * The quoted post in a quote (feed/quote-posts), one level deep: its author, audience and text, with its proposal named, or a link when it
 * is itself a quote; a tap opens it. "Post unavailable" in its place opens nothing.
 */
@Composable
private fun QuotedPost(quoted: QuotedPost, onEvent: (PostEvent) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val framed = Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    when (quoted) {
        is QuotedPost.Post ->
            Column(
                framed
                    .testTag(TestTags.QUOTED_POST)
                    .clickable(onClickLabel = stringResource(Res.string.quoted_post_open)) { onEvent(PostEvent.OpenQuoted(quoted.uri)) }
                    .handCursor()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    quoted.author.shownName + " · " + visibilityLabel(quoted.visibilityOrPublic),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                quoted.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = QUOTED_TEXT_LINES) }
                when (val embed = quoted.embed) {
                    is PostEmbedView.Proposal ->
                        Text(stringResource(Res.string.post_card_proposes, embed.name), style = MaterialTheme.typography.titleSmall)
                    is QuotedPostEmbed.Link ->
                        Text(
                            stringResource(Res.string.quoted_post_link),
                            Modifier.testTag(TestTags.QUOTED_POST_LINK)
                                .clickable(onClickLabel = stringResource(Res.string.quoted_post_open)) {
                                    onEvent(PostEvent.OpenQuoted(embed.uri))
                                }
                                .handCursor(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    is QuotedPostEmbed.Unknown,
                    null -> Unit
                }
            }
        is QuotedPost.Unavailable,
        is QuotedPost.Unknown ->
            Text(
                stringResource(Res.string.quoted_post_unavailable),
                framed.testTag(TestTags.QUOTED_POST_UNAVAILABLE).padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

private const val QUOTED_TEXT_LINES = 6

@Composable
private fun Header(post: PostView, own: Boolean, canReport: Boolean, onEvent: (PostEvent) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AuthorHeader(post.author, Modifier.weight(1f), onClick = { onEvent(PostEvent.OpenAuthor(post)) }) {
            Text(
                visibilityLabel(post.visibility ?: Visibility.PUBLIC),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Delete only on the viewer's own quote (feed/quote-posts), Report only on someone else's post.
        val canDelete = own && post.embed is PostEmbedView.Quote
        if (canDelete || (!own && canReport)) {
            Box {
                var open by remember { mutableStateOf(false) }
                AppIconButton(AppIcon.More, stringResource(Res.string.post_card_more), onClick = { open = true })
                val label = stringResource(if (canDelete) Res.string.quote_delete else Res.string.action_report)
                AppMenu(
                    expanded = open,
                    onDismiss = { open = false },
                    items =
                        persistentListOf(
                            AppMenuItem(label) {
                                open = false
                                onEvent(if (canDelete) PostEvent.Delete(post) else PostEvent.Report(post))
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun Proposal(post: PostView, embed: PostEmbedView.Proposal, onEvent: (PostEvent) -> Unit) {
    val place =
        embed.segment?.let { stringResource(Res.string.post_card_segment, it.name) }
            ?: embed.diveSite?.let { stringResource(Res.string.post_card_dive_site, it.name) }.orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            place,
            Modifier.clickable(onClickLabel = stringResource(Res.string.post_card_open_place)) { onEvent(PostEvent.Open(post)) }
                .handCursor(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.post_card_proposes, embed.name), style = MaterialTheme.typography.titleMedium)
                Text(
                    statusLabel(embed.status) + " · " + stringResource(Res.string.segment_name_totals, embed.up, embed.down),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (embed.canVote) {
                val vote = embed.viewerVote?.direction
                fun toggle(direction: String) = onEvent(PostEvent.Vote(post, direction.takeIf { it != vote }))
                AppIconButton(
                    AppIcon.ChevronUp,
                    stringResource(Res.string.segment_name_vote_up, embed.name),
                    onClick = { toggle(SegmentNameVoteDirection.UP) },
                    modifier = Modifier.testTag(TestTags.POST_VOTE_UP),
                    tint =
                        if (vote == SegmentNameVoteDirection.UP) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppIconButton(
                    AppIcon.ChevronDown,
                    stringResource(Res.string.segment_name_vote_down, embed.name),
                    onClick = { toggle(SegmentNameVoteDirection.DOWN) },
                    modifier = Modifier.testTag(TestTags.POST_VOTE_DOWN),
                    tint =
                        if (vote == SegmentNameVoteDirection.DOWN) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun statusLabel(status: String): String =
    stringResource(
        when (status) {
            PostProposalStatus.OPEN -> Res.string.post_status_open
            PostProposalStatus.ADOPTED -> Res.string.post_status_adopted
            PostProposalStatus.REJECTED -> Res.string.post_status_rejected
            PostProposalStatus.EXPIRED -> Res.string.post_status_expired
            PostProposalStatus.SUPERSEDED -> Res.string.post_status_superseded
            else -> Res.string.post_status_closed
        },
    )
