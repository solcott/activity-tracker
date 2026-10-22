package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.UiError
import io.github.solcott.activitytracker.app.ui.ActivityDetailsForm
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.ContentStateLayout
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSwitch
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_save
import io.github.solcott.activitytracker.composeApp.resources.action_saving
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_narrowing_body
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_narrowing_confirm
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_narrowing_title
import io.github.solcott.activitytracker.composeApp.resources.core_charts_widening_body
import io.github.solcott.activitytracker.composeApp.resources.core_charts_widening_confirm
import io.github.solcott.activitytracker.composeApp.resources.core_charts_widening_title
import io.github.solcott.activitytracker.composeApp.resources.edit_activity_title
import io.github.solcott.activitytracker.composeApp.resources.edit_discard
import io.github.solcott.activitytracker.composeApp.resources.edit_discard_message
import io.github.solcott.activitytracker.composeApp.resources.edit_discard_title
import io.github.solcott.activitytracker.composeApp.resources.edit_leaderboard_exclusion
import io.github.solcott.activitytracker.composeApp.resources.edit_leaderboard_exclusion_help
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** Whether segment leaderboards leave this activity out, for a group ride or a stretch in a car. */
@Composable
private fun LeaderboardExclusion(
    excluded: Boolean,
    enabled: Boolean,
    eventSink: (EditActivityEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(excluded, enabled = enabled, role = Role.Switch) {
                    eventSink(EditActivityEvent.LeaderboardExclusionChanged(it))
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.edit_leaderboard_exclusion), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            AppSwitch(excluded, onCheckedChange = null, enabled = enabled)
        }
        Text(stringResource(Res.string.edit_leaderboard_exclusion_help), style = MaterialTheme.typography.bodySmall)
    }
}

@CircuitInject(EditActivityScreen::class, SessionScope::class)
@Composable
fun EditActivityUi(state: EditActivityState, modifier: Modifier = Modifier) {
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    AppScaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(Res.string.edit_activity_title),
                onBack = { state.eventSink(EditActivityEvent.Back) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        ContentStateLayout(
            state.content,
            UiError.Kind.LoadActivity,
            { state.eventSink(EditActivityEvent.Retry) },
            contentPadding = padding,
        ) {
            val scroll = rememberScrollState()
            ScrollbarBox(scroll, contentPadding = padding) {
                Column(
                    Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val details = state.details ?: return@Column
                    val type = state.type ?: return@Column
                    ActivityDetailsForm(state.title, titlePlaceholder = activityTypeLabel(type), fields = details, enabled = !state.saving)
                    state.visibility?.let { visibility ->
                        ActivityVisibilityForm(
                            visibility,
                            enabled = !state.saving,
                        )
                    }
                    state.leaderboardExclusion?.let { excluded -> LeaderboardExclusion(excluded, enabled = !state.saving, state.eventSink) }
                    state.error?.let { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                    AppButton(onClick = { state.eventSink(EditActivityEvent.Save) }, enabled = !state.saving) {
                        Text(stringResource(if (state.saving) Res.string.action_saving else Res.string.action_save))
                    }
                }
            }
        }
    }
    if (state.confirmDiscard) {
        AppAlertDialog(
            title = stringResource(Res.string.edit_discard_title),
            message = stringResource(Res.string.edit_discard_message),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.edit_discard), destructive = true) {
                        state.eventSink(EditActivityEvent.Discard)
                    }
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { state.eventSink(EditActivityEvent.KeepEditing) },
        )
    }
    if (state.confirmNarrowing) {
        AppAlertDialog(
            title = stringResource(Res.string.activity_visibility_narrowing_title),
            message = stringResource(Res.string.activity_visibility_narrowing_body),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.activity_visibility_narrowing_confirm)) {
                        state.eventSink(EditActivityEvent.ConfirmNarrowing)
                    }
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { state.eventSink(EditActivityEvent.CancelNarrowing) },
        )
    }
    state.confirmCoreCharts?.let { visibility ->
        AppAlertDialog(
            title = stringResource(Res.string.core_charts_widening_title),
            message = stringResource(Res.string.core_charts_widening_body, visibilityLabel(visibility)),
            actions =
                persistentListOf(
                    AppDialogAction(stringResource(Res.string.core_charts_widening_confirm)) {
                        state.eventSink(EditActivityEvent.ConfirmCoreCharts)
                    }
                ),
            cancelLabel = stringResource(Res.string.action_cancel),
            onDismiss = { state.eventSink(EditActivityEvent.CancelCoreCharts) },
        )
    }
}
