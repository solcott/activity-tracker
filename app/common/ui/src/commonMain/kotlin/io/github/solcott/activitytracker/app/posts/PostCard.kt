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
import io.github.solcott.activitytracker.app.ui.AuthorHeader
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.PlatformIcon
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.ReasonRepost
import kotlinx.collections.immutable.persistentListOf

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
    val content = remember(post, viewerDid, reason) { PostCardContent.of(post, viewerDid, reason) }
    val openLabel = PostCardContent.open().string()
    Card(modifier.fillMaxWidth().clickable(onClickLabel = openLabel) { onEvent(PostEvent.OpenPost(post)) }.handCursor()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content.repostedBy()?.let { RepostedBy(content, it.string(), onEvent) }
            Header(content, canReport, onEvent)
            content.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            content.proposal?.let { Proposal(content, it, onEvent) }
            if (content.quoted != null || content.quotedUnavailable) QuotedPost(content, onEvent)
            if (content.embedUnknown) {
                Text(
                    content.embedUnknownText().string(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Counts(content, onEvent, onQuoteCount)
        }
    }
}

/** "Reposted by …" above a post in the timeline through a repost (feed/reposts), opening the reposter's profile. */
@Composable
private fun RepostedBy(content: PostCardContent, text: String, onEvent: (PostEvent) -> Unit) {
    val reposter = content.reposter ?: return
    Row(
        Modifier.testTag(TestTags.POST_REPOSTED_BY)
            .clickable(onClickLabel = PostCardContent.openReposter().string()) { onEvent(PostEvent.OpenReposter(reposter)) }
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
private fun Counts(content: PostCardContent, onEvent: (PostEvent) -> Unit, onQuoteCount: (() -> Unit)?) {
    val post = content.post
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (content.offersLike) {
            AppIconButton(
                AppIcon.Like,
                contentDescription = PostCardContent.like().string(),
                onClick = { onEvent(PostEvent.Like(post)) },
                modifier = Modifier.testTag(TestTags.POST_LIKE),
                filled = content.liked,
                tint = if (content.liked) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
            )
        }
        if (content.offersRepost) {
            AppIconButton(
                AppIcon.Repost,
                contentDescription = content.repostLabel().string(),
                onClick = { onEvent(PostEvent.Repost(post)) },
                modifier = Modifier.testTag(TestTags.POST_REPOST),
                filled = content.reposted,
                tint = if (content.reposted) MaterialTheme.colorScheme.tertiary else Color.Unspecified,
            )
        }
        if (content.offersQuote) {
            AppIconButton(
                AppIcon.Quote,
                contentDescription = PostCardContent.quote().string(),
                onClick = { onEvent(PostEvent.Quote(post)) },
                modifier = Modifier.testTag(TestTags.POST_QUOTE),
            )
        }
        Text(
            content.counts().string(),
            modifier =
                if (onQuoteCount == null) Modifier
                else
                    Modifier.testTag(TestTags.POST_QUOTE_COUNT)
                        .clickable(onClickLabel = PostCardContent.showQuotes().string()) { onQuoteCount() }
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
private fun QuotedPost(content: PostCardContent, onEvent: (PostEvent) -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val framed = Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    val quoted = content.quoted
    if (quoted == null) {
        Text(
            content.quotedUnavailableText().string(),
            framed.testTag(TestTags.QUOTED_POST_UNAVAILABLE).padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val openLabel = content.quotedOpen().string()
    Column(
        framed
            .testTag(TestTags.QUOTED_POST)
            .clickable(onClickLabel = openLabel) { onEvent(PostEvent.OpenQuoted(quoted.uri)) }
            .handCursor()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content.quotedHeader()?.let {
            Text(it.string(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        quoted.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = QUOTED_TEXT_LINES) }
        content.quotedProposes()?.let { Text(it.string(), style = MaterialTheme.typography.titleSmall) }
        content.quotedLink?.let { link ->
            Text(
                content.quotedLinkText().string(),
                Modifier.testTag(TestTags.QUOTED_POST_LINK)
                    .clickable(onClickLabel = openLabel) { onEvent(PostEvent.OpenQuoted(link)) }
                    .handCursor(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val QUOTED_TEXT_LINES = 6

@Composable
private fun Header(content: PostCardContent, canReport: Boolean, onEvent: (PostEvent) -> Unit) {
    val post = content.post
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AuthorHeader(post.author, Modifier.weight(1f), onClick = { onEvent(PostEvent.OpenAuthor(post)) }) {
            Text(
                content.audience().string(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Delete only on the viewer's own quote (feed/quote-posts), Report only on someone else's post.
        if (content.offersMenu(canReport)) {
            Box {
                var open by remember { mutableStateOf(false) }
                AppIconButton(AppIcon.More, PostCardContent.more().string(), onClick = { open = true })
                val label = content.menuItem().string()
                AppMenu(
                    expanded = open,
                    onDismiss = { open = false },
                    items =
                        persistentListOf(
                            AppMenuItem(label) {
                                open = false
                                onEvent(content.menuEvent())
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun Proposal(content: PostCardContent, embed: PostEmbedView.Proposal, onEvent: (PostEvent) -> Unit) {
    val post = content.post
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            content.place()?.string().orEmpty(),
            Modifier.clickable(onClickLabel = PostCardContent.openPlace().string()) { onEvent(PostEvent.Open(post)) }.handCursor(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(content.proposes()?.string().orEmpty(), style = MaterialTheme.typography.titleMedium)
                Text(
                    content.proposalStatus()?.string().orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (embed.canVote) {
                AppIconButton(
                    AppIcon.ChevronUp,
                    content.voteUp().string(),
                    onClick = { onEvent(content.vote(up = true)) },
                    modifier = Modifier.testTag(TestTags.POST_VOTE_UP),
                    tint = if (content.votedUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppIconButton(
                    AppIcon.ChevronDown,
                    content.voteDown().string(),
                    onClick = { onEvent(content.vote(up = false)) },
                    modifier = Modifier.testTag(TestTags.POST_VOTE_DOWN),
                    tint = if (content.votedDown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
