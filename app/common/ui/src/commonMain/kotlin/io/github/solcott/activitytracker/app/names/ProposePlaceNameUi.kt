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
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.data.di.SessionScope
import org.jetbrains.compose.resources.stringResource

/**
 * The name sheet's content: the name, and before it's sent what proposing shows others (a new proposal) or that saving clears the votes (an
 * edit), then Share to feed (feed/posts). Its words and conditions are Kotlin's (`ProposePlaceNameTexts`, `ProposePlaceNameState`), shared
 * with the native sheet.
 */
@SubCircuitInject(ProposePlaceNameScreen::class, SessionScope::class)
@Composable
fun ProposePlaceNameUi(state: ProposePlaceNameState, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        AppSheetHeader(
            title = ProposePlaceNameTexts.title(state).string(),
            cancelLabel = stringResource(Res.string.action_cancel),
            onCancel = { state.eventSink(ProposePlaceNameEvent.Cancel) },
            confirmLabel = ProposePlaceNameTexts.confirm(state).string(),
            onConfirm = { if (state.canSend) state.eventSink(ProposePlaceNameEvent.Send) },
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ProposePlaceNameTexts.lead(state).string(), style = MaterialTheme.typography.bodyMedium)
            AppTextField(
                state = state.name,
                modifier = Modifier.fillMaxWidth(),
                label = ProposePlaceNameTexts.field.string(),
                enabled = !state.sending,
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            if (state.showsInvalid) {
                Text(
                    ProposePlaceNameTexts.invalid.string(),
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
    val enabled = state.shareEnabled
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ProposePlaceNameTexts.shareToFeed.string(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    ProposePlaceNameTexts.shareToFeedDetail.string(),
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
        ProposePlaceNameTexts.shareLimit(state)?.let {
            Text(it.string(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AppTextField(
            state = state.shareText,
            modifier = Modifier.fillMaxWidth(),
            label = ProposePlaceNameTexts.shareText.string(),
            enabled = enabled,
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6),
        )
        if (!state.shareTextValid) {
            Text(
                ProposePlaceNameTexts.shareTextTooLong.string(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
