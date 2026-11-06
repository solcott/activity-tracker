package io.github.solcott.activitytracker.app.posts

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.shownName
import io.github.solcott.activitytracker.app.ui.Avatar
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenu
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.ui.adaptive.handCursor
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_report
import io.github.solcott.activitytracker.composeApp.resources.post_card_dive_site
import io.github.solcott.activitytracker.composeApp.resources.post_card_more
import io.github.solcott.activitytracker.composeApp.resources.post_card_open
import io.github.solcott.activitytracker.composeApp.resources.post_card_proposes
import io.github.solcott.activitytracker.composeApp.resources.post_card_segment
import io.github.solcott.activitytracker.composeApp.resources.post_card_unavailable
import io.github.solcott.activitytracker.composeApp.resources.post_status_adopted
import io.github.solcott.activitytracker.composeApp.resources.post_status_closed
import io.github.solcott.activitytracker.composeApp.resources.post_status_expired
import io.github.solcott.activitytracker.composeApp.resources.post_status_open
import io.github.solcott.activitytracker.composeApp.resources.post_status_rejected
import io.github.solcott.activitytracker.composeApp.resources.post_status_superseded
import io.github.solcott.activitytracker.composeApp.resources.segment_name_totals
import io.github.solcott.activitytracker.composeApp.resources.segment_name_vote_down
import io.github.solcott.activitytracker.composeApp.resources.segment_name_vote_up
import io.github.solcott.activitytracker.data.moderation.ModerationDecision
import io.github.solcott.activitytracker.lexicon.app.PostEmbedView
import io.github.solcott.activitytracker.lexicon.app.PostProposalStatus
import io.github.solcott.activitytracker.lexicon.app.PostView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/**
 * One post (feed/posts) in a timeline or on a profile: its author and audience, their text, and the proposal it shares as it stands now,
 * its name, status and totals, with up and down votes when the viewer may vote. A tap opens the segment or dive site. [canReport] offers
 * Report on someone else's post.
 */
@Composable
fun PostCard(post: PostView, viewerDid: String, canReport: Boolean, onEvent: (PostEvent) -> Unit, modifier: Modifier = Modifier) {
    val embed = post.embed as? PostEmbedView.Proposal
    val openLabel = stringResource(Res.string.post_card_open)
    Card(modifier.fillMaxWidth().clickable(onClickLabel = openLabel) { onEvent(PostEvent.Open(post)) }.handCursor()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Header(post, own = post.author.did.value == viewerDid, canReport, onEvent)
            post.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (embed == null) {
                Text(
                    stringResource(Res.string.post_card_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Proposal(post, embed, onEvent)
            }
        }
    }
}

@Composable
private fun Header(post: PostView, own: Boolean, canReport: Boolean, onEvent: (PostEvent) -> Unit) {
    val author = post.author.shownName
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable { onEvent(PostEvent.OpenAuthor(post)) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(post.author.avatar, author, 32.dp, blur = ModerationDecision.mediaOf(null, post.author.labels))
            Column {
                Text(author, style = MaterialTheme.typography.labelLarge)
                Text(
                    visibilityLabel(post.visibility ?: Visibility.PUBLIC),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!own && canReport) {
            Box {
                var open by remember { mutableStateOf(false) }
                AppIconButton(AppIcon.More, stringResource(Res.string.post_card_more), onClick = { open = true })
                val reportLabel = stringResource(Res.string.action_report)
                AppMenu(
                    expanded = open,
                    onDismiss = { open = false },
                    items =
                        persistentListOf(
                            AppMenuItem(reportLabel) {
                                open = false
                                onEvent(PostEvent.Report(post))
                            }
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
        Text(place, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        }
    )
