package io.github.solcott.activitytracker.app.names

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
import io.github.solcott.activitytracker.app.segment.effortDateLabel
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.LocalTimeZone
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.handleLabel
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.segment_name_delete
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
import io.github.solcott.activitytracker.lexicon.AtUri
import io.github.solcott.activitytracker.lexicon.app.SegmentNameAdoption
import io.github.solcott.activitytracker.lexicon.app.SegmentNameProposalView
import io.github.solcott.activitytracker.lexicon.app.SegmentNameVoteDirection
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

// A place's community names (segments/segment-names) on its screen, for any place named by proposal and vote (a segment): its original
// name, its name history, the open proposals with their votes, Propose, and the viewer's own proposal's Edit and Delete. The host's
// presenter owns what they do (`rememberPlaceNames`); these only lay them out and send to its [PlaceNameFields.eventSink].

/** "originally …" under a place's title, when a community name replaced its original. */
@Composable
fun OriginalName(original: String, modifier: Modifier = Modifier) {
    Text(
        stringResource(Res.string.segment_originally, original),
        modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The place's names: Propose when [canPropose], the open [proposals] with votes, and the name [history], collapsed until opened. [canVote]
 * says the viewer may vote and [fields] the rest: whether this session can send a vote (the controls are disabled with why when not), the
 * viewer's DID, whose own proposals offer Edit and Delete instead of votes, and why the last vote or deletion failed.
 */
@Composable
fun PlaceNames(
    proposals: ImmutableList<SegmentNameProposalView>,
    history: ImmutableList<SegmentNameAdoption>,
    canPropose: Boolean,
    canVote: Boolean,
    fields: PlaceNameFields,
    modifier: Modifier = Modifier,
) {
    val error = fields.error
    val canVoteHere = fields.canVoteHere
    val send = fields.eventSink
    // Nothing to show takes no space.
    val nothingListed = proposals.isEmpty() && history.isEmpty()
    if (nothingListed && !canPropose && error == null) return
    Column(modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canPropose) {
            AppSecondaryButton(onClick = { send(PlaceNameEvent.ProposeName) }) { Text(stringResource(Res.string.segment_name_propose)) }
        }
        if (proposals.isNotEmpty()) {
            Text(stringResource(Res.string.segment_name_proposals), style = MaterialTheme.typography.titleMedium)
            if (canVote && !canVoteHere) {
                Text(
                    stringResource(Res.string.segment_name_voting_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            proposals.forEach { proposal ->
                Proposal(
                    proposal,
                    own = proposal.proposer.did.value == fields.viewerDid,
                    canVote = canVote,
                    canVoteHere = canVoteHere,
                    onEdit = { send(PlaceNameEvent.EditProposal(it)) },
                    onDelete = { send(PlaceNameEvent.DeleteProposal(it)) },
                    onVote = { uri, direction -> send(PlaceNameEvent.Vote(uri, direction)) },
                )
            }
        }
        error?.let { ErrorText(it, style = MaterialTheme.typography.bodySmall) }
        if (history.isNotEmpty()) History(history)
    }
}

@Composable
private fun Proposal(
    proposal: SegmentNameProposalView,
    own: Boolean,
    canVote: Boolean,
    canVoteHere: Boolean,
    onEdit: (AtUri) -> Unit,
    onDelete: (AtUri) -> Unit,
    onVote: (AtUri, String?) -> Unit,
) {
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
            AppIconButton(AppIcon.ManualEntry, stringResource(Res.string.segment_name_edit), onClick = { onEdit(proposal.uri) })
            AppIconButton(AppIcon.Delete, stringResource(Res.string.segment_name_delete), onClick = { onDelete(proposal.uri) })
        } else if (canVote) {
            val vote = proposal.viewerVote?.direction
            fun toggle(direction: String) = onVote(proposal.uri, direction.takeIf { it != vote })
            AppIconButton(
                AppIcon.ChevronUp,
                stringResource(Res.string.segment_name_vote_up, proposal.name),
                onClick = { toggle(SegmentNameVoteDirection.UP) },
                enabled = canVoteHere,
                tint =
                    if (vote == SegmentNameVoteDirection.UP) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppIconButton(
                AppIcon.ChevronDown,
                stringResource(Res.string.segment_name_vote_down, proposal.name),
                onClick = { toggle(SegmentNameVoteDirection.DOWN) },
                enabled = canVoteHere,
                tint =
                    if (vote == SegmentNameVoteDirection.DOWN) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun History(history: ImmutableList<SegmentNameAdoption>) {
    var open by rememberSaveable { mutableStateOf(false) }
    AppTextButton(onClick = { open = !open }) { Text(stringResource(Res.string.segment_name_history)) }
    if (open) history.forEach { HistoryEntry(it) }
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

/** [fields]' name sheet while it's open, and the confirmation before the viewer's own proposal is deleted while one is asked for. */
@Composable
fun PlaceNameDialogs(fields: PlaceNameFields) {
    val onSheetEvent = { event: ProposePlaceNameOuterEvent -> fields.eventSink(PlaceNameEvent.Sheet(event)) }
    val onConfirmDelete = { confirmed: Boolean -> fields.eventSink(PlaceNameEvent.ConfirmDelete(confirmed)) }
    fields.sheet?.let {
        AppBottomSheet(onDismiss = { onSheetEvent(ProposePlaceNameOuterEvent.Closed) }, sheetState = rememberAppSheetState()) {
            SubCircuitContent(it, outerEventSink = onSheetEvent)
        }
    }
    if (fields.confirmDelete != null) {
        val delete = ProposePlaceNameTexts.delete.string()
        AppAlertDialog(
            title = ProposePlaceNameTexts.deleteConfirmTitle.string(),
            message = ProposePlaceNameTexts.deleteConfirmMessage.string(),
            actions = persistentListOf(AppDialogAction(delete, destructive = true) { onConfirmDelete(true) }),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { onConfirmDelete(false) },
        )
    }
}
