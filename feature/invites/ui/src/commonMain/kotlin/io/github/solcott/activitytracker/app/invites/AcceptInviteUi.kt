package io.github.solcott.activitytracker.app.invites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.ErrorText
import io.github.solcott.activitytracker.app.ui.RefreshStatus
import io.github.solcott.activitytracker.app.ui.RoutePreview
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ReadableScrollColumn
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_sign_in_again
import io.github.solcott.activitytracker.composeApp.resources.field_title
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

@CircuitInject(AcceptInviteScreen::class, SessionScope::class)
@Composable
fun AcceptInviteUi(state: AcceptInviteState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = AcceptInviteTexts.title.string(),
                onBack = { state.eventSink(AcceptInviteEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val retry = { state.eventSink(AcceptInviteEvent.Retry) }
        ContentStateLayout(state.content, UiError.Kind.LoadActivityInvite, retry, contentPadding = padding) { invite ->
            val busy = state.busy
            ReadableScrollColumn(padding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RefreshStatus(state.content, UiError.Kind.LoadActivityInvite, retry)
                if (invite == null) {
                    Text(AcceptInviteTexts.gone.string())
                    return@ReadableScrollColumn
                }
                InviteSummary(invite)
                invite.record.polyline?.let {
                    RoutePreview(it, Modifier.fillMaxWidth(), gaps = invite.record.polylineGaps?.toImmutableList())
                }
                Text(AcceptInviteTexts.intro(invite).string(), style = MaterialTheme.typography.bodyMedium)
                if (state.overlaps) Text(AcceptInviteTexts.overlap.string(), color = MaterialTheme.colorScheme.error)
                AppTextField(
                    state.title,
                    label = stringResource(Res.string.field_title),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                ActivityVisibilityForm(state.visibility, enabled = !busy)
                state.error?.let { error ->
                    ErrorText(error)
                    if (state.offersSignInAgain) {
                        AppSecondaryButton(onClick = { state.eventSink(AcceptInviteEvent.SignInAgain) }) {
                            Text(stringResource(Res.string.action_sign_in_again))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.canDecline) {
                        AppSecondaryButton(onClick = { state.eventSink(AcceptInviteEvent.Decline) }, enabled = !busy) {
                            Text(AcceptInviteTexts.decline.string())
                        }
                    }
                    AppButton(onClick = { state.eventSink(AcceptInviteEvent.Accept) }, enabled = !busy) {
                        Text(AcceptInviteTexts.accept(state.accepting).string())
                    }
                }
            }
        }
    }
}
