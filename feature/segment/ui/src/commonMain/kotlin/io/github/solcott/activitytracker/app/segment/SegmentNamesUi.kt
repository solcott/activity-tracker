package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slack.circuit.subcircuit.SubCircuitContent
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.segment_name_delete
import io.github.solcott.activitytracker.composeApp.resources.segment_name_delete_confirm_message
import io.github.solcott.activitytracker.composeApp.resources.segment_name_delete_confirm_title
import io.github.solcott.activitytracker.composeApp.resources.segment_name_edit
import io.github.solcott.activitytracker.composeApp.resources.segment_name_history
import io.github.solcott.activitytracker.composeApp.resources.segment_name_history_by
import io.github.solcott.activitytracker.composeApp.resources.segment_name_history_entry
import io.github.solcott.activitytracker.composeApp.resources.segment_name_proposals
import io.github.solcott.activitytracker.composeApp.resources.segment_name_propose
import io.github.solcott.activitytracker.composeApp.resources.segment_name_proposed_by
import io.github.solcott.activitytracker.composeApp.resources.segment_name_totals
import io.github.solcott.activitytracker.composeApp.resources.segment_name_vote_down
import io.github.solcott.activitytracker.composeApp.resources.segment_name_vote_up
import io.github.solcott.activitytracker.composeApp.resources.segment_name_voting_unavailable
import io.github.solcott.activitytracker.composeApp.resources.segment_originally
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import io.github.solcott.activitytracker.lexicon.app.SegmentView
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

// A segment's community names (segments/segment-names) on its screen: its original name, its name history, the open proposals with their
// votes, Propose, and the viewer's own proposal's Edit and Delete.

/** "originally …" under a segment's title, when a community name replaced its original. */
@Composable
fun OriginalName(original: String, modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.segment_originally, original),
        modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The segment's names: Propose when allowed, the open proposals with votes, and the name history, collapsed until opened. */
@Composable
fun SegmentNames(state: SegmentState, segment: SegmentView, modifier: Modifier = Modifier) {
    // Nothing to show takes no space.
    val nothingListed = segment.nameProposals.isEmpty() && segment.nameHistory.isEmpty()
    if (nothingListed && !state.canPropose && state.nameError == null) return
    Column(modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.canPropose) {
            AppSecondaryButton(onClick = { state.eventSink(SegmentEvent.ProposeName) }) {
                Text(stringResource(Res.string.segment_name_propose))
            }
        }
        if (segment.nameProposals.isNotEmpty()) {
            Text(stringResource(Res.string.segment_name_proposals), style = MaterialTheme.typography.titleMedium)
            if (state.canVote && !state.canVoteHere) {
                Text(
                    stringResource(Res.string.segment_name_voting_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            segment.nameProposals.forEach { proposal -> Proposal(state, proposal, own = proposal.proposer.did.value == state.self) }
        }
        state.nameError?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (segment.nameHistory.isNotEmpty()) History(segment)
    }
}

@Composable
private fun Proposal(state: SegmentState, proposal: SegmentNameProposalView, own: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(proposal.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(Res.string.segment_name_proposed_by, handleLabel(proposal.proposer.handle.value)) +
                    " · " +
                    stringResource(Res.string.segment_name_totals, proposal.up, proposal.down),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (own) {
            AppIconButton(
                AppIcon.ManualEntry,
                stringResource(Res.string.segment_name_edit),
                onClick = { state.eventSink(SegmentEvent.EditProposal(proposal.uri)) },
            )
            AppIconButton(
                AppIcon.Delete,
                stringResource(Res.string.segment_name_delete),
                onClick = { state.eventSink(SegmentEvent.DeleteProposal(proposal.uri)) },
            )
        } else if (state.canVote) {
            val vote = proposal.viewerVote?.direction
            fun toggle(direction: String) = state.eventSink(SegmentEvent.Vote(proposal.uri, direction.takeIf { it != vote }))
            AppIconButton(
                AppIcon.ChevronUp,
                stringResource(Res.string.segment_name_vote_up, proposal.name),
                onClick = { toggle(SegmentNameVoteDirection.UP) },
                enabled = state.canVoteHere,
                tint =
                    if (vote == SegmentNameVoteDirection.UP) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppIconButton(
                AppIcon.ChevronDown,
                stringResource(Res.string.segment_name_vote_down, proposal.name),
                onClick = { toggle(SegmentNameVoteDirection.DOWN) },
                enabled = state.canVoteHere,
                tint =
                    if (vote == SegmentNameVoteDirection.DOWN) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun History(segment: SegmentView) {
    var open by rememberSaveable { mutableStateOf(false) }
    AppTextButton(onClick = { open = !open }) { Text(stringResource(Res.string.segment_name_history)) }
    if (open) segment.nameHistory.forEach { HistoryEntry(it) }
}

@Composable
private fun HistoryEntry(adoption: SegmentNameAdoption) {
    Column {
        Text(
            stringResource(Res.string.segment_name_history_entry, adoption.name, dateOf(adoption.adoptedAt), adoption.up, adoption.down),
            style = MaterialTheme.typography.bodyMedium,
        )
        adoption.proposer?.let {
            Text(
                stringResource(Res.string.segment_name_history_by, handleLabel(it.handle.value)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable private fun dateOf(instant: Instant): String = effortDateLabel(instant.toLocalDateTime(LocalTimeZone.current).date.toString())

/** The name sheet while it's open, and the confirmation before the viewer's own proposal is deleted. */
@Composable
fun SegmentNameDialogs(state: SegmentState) {
    state.nameSheet?.let { sheet ->
        AppBottomSheet(
            onDismiss = { state.eventSink(SegmentEvent.NameSheet(ProposeSegmentNameOuterEvent.Closed)) },
            sheetState = rememberAppSheetState(),
        ) {
            SubCircuitContent(sheet, outerEventSink = { state.eventSink(SegmentEvent.NameSheet(it)) })
        }
    }
    if (state.confirmDelete != null) {
        val delete = stringResource(Res.string.segment_name_delete)
        AppAlertDialog(
            title = stringResource(Res.string.segment_name_delete_confirm_title),
            message = stringResource(Res.string.segment_name_delete_confirm_message),
            actions = persistentListOf(AppDialogAction(delete, destructive = true) { state.eventSink(SegmentEvent.ConfirmDelete(true)) }),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { state.eventSink(SegmentEvent.ConfirmDelete(false)) },
        )
    }
}
