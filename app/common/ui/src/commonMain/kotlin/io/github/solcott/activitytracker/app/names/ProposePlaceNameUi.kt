package io.github.solcott.activitytracker.app.names

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.slack.circuit.subcircuit.SubCircuitInject
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.app.ui.VisibilityPicker
import io.github.solcott.activitytracker.app.ui.adaptive.AppSheetHeader
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.post_share_public_only_sign_in
import io.github.solcott.activitytracker.composeApp.resources.post_share_public_only_unsupported
import io.github.solcott.activitytracker.composeApp.resources.post_share_text_field
import io.github.solcott.activitytracker.composeApp.resources.post_share_text_too_long
import io.github.solcott.activitytracker.composeApp.resources.post_share_to_feed
import io.github.solcott.activitytracker.composeApp.resources.post_share_to_feed_detail
import io.github.solcott.activitytracker.composeApp.resources.segment_name_disclosure
import io.github.solcott.activitytracker.composeApp.resources.segment_name_edit_warning
import io.github.solcott.activitytracker.composeApp.resources.segment_name_field
import io.github.solcott.activitytracker.composeApp.resources.segment_name_invalid
import io.github.solcott.activitytracker.composeApp.resources.segment_name_sheet_edit_title
import io.github.solcott.activitytracker.composeApp.resources.segment_name_sheet_propose_title
import io.github.solcott.activitytracker.composeApp.resources.segment_name_sheet_save
import io.github.solcott.activitytracker.composeApp.resources.segment_name_sheet_send
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/**
 * The name sheet's content: the name, and before it's sent what proposing shows others (a new proposal) or that saving clears the votes (an
 * edit), then Share to feed (feed/posts).
 */
@SubCircuitInject(ProposePlaceNameScreen::class, SessionScope::class)
@Composable
fun ProposePlaceNameUi(state: ProposePlaceNameState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        AppSheetHeader(
            title =
                stringResource(
                    if (state.editing) Res.string.segment_name_sheet_edit_title else Res.string.segment_name_sheet_propose_title,
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = { state.eventSink(ProposePlaceNameEvent.Cancel) },
            confirmLabel = stringResource(if (state.editing) Res.string.segment_name_sheet_save else Res.string.segment_name_sheet_send),
            onConfirm = { if (state.canSend) state.eventSink(ProposePlaceNameEvent.Send) },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (state.editing) Res.string.segment_name_edit_warning else Res.string.segment_name_disclosure),
                style = MaterialTheme.typography.bodyMedium,
            )
            AppTextField(
                state = state.name,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(Res.string.segment_name_field),
                enabled = !state.sending,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            if (state.name.text.isNotEmpty() && !state.valid) {
                Text(
                    stringResource(Res.string.segment_name_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ShareToFeed(state)
            state.error?.let { ErrorText(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Share to feed (feed/posts): the switch, then with it on the audience, why only Public may be offered, and the optional text. */
@Composable
private fun ShareToFeed(state: ProposePlaceNameState) {
    val enabled = !state.sending && !state.shareLoading
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.post_share_to_feed), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(Res.string.post_share_to_feed_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppSwitch(
                checked = state.sharing,
                onCheckedChange = { state.eventSink(ProposePlaceNameEvent.SharingChanged(it)) },
                modifier = Modifier.testTag(TestTags.SHARE_TO_FEED),
                enabled = enabled,
            )
        }
        if (!state.sharing) return@Column
        VisibilityPicker(
            selected = state.audience,
            onSelect = { state.eventSink(ProposePlaceNameEvent.AudienceChanged(it)) },
            approvesFollowers = false,
            enabled = enabled,
            options = state.audiences,
        )
        when (state.shareLimit) {
            ShareLimit.None -> Unit
            ShareLimit.SignInAgain -> LimitNote(stringResource(Res.string.post_share_public_only_sign_in))
            ShareLimit.SpacesUnsupported -> LimitNote(stringResource(Res.string.post_share_public_only_unsupported))
        }
        AppTextField(
            state = state.shareText,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.post_share_text_field),
            enabled = enabled,
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6),
        )
        if (!state.shareTextValid) {
            Text(
                stringResource(Res.string.post_share_text_too_long, ProposePlaceNameState.MAX_SHARE_TEXT_LENGTH),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun LimitNote(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
