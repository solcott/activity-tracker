package io.github.solcott.activitytracker.app.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.slack.circuit.codegen.annotations.CircuitInject
import io.github.solcott.activitytracker.app.ui.ActivityDetailsForm
import io.github.solcott.activitytracker.app.ui.ActivityVisibilityForm
import io.github.solcott.activitytracker.app.ui.activityTypeLabel
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppProgressIndicator
import io.github.solcott.activitytracker.app.ui.adaptive.AppScaffold
import io.github.solcott.activitytracker.app.ui.adaptive.AppSecondaryButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTopBar
import io.github.solcott.activitytracker.app.ui.adaptive.ScrollbarBox
import io.github.solcott.activitytracker.app.ui.adaptive.readableWidth
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppTopBarScrollBehavior
import io.github.solcott.activitytracker.app.ui.errorText
import io.github.solcott.activitytracker.app.ui.visibilityLabel
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_retry
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
import io.github.solcott.activitytracker.data.di.SessionScope
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

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
        val scroll = rememberScrollState()
        ScrollbarBox(scroll, contentPadding = padding) {
            Column(
                Modifier.verticalScroll(scroll).padding(padding).readableWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val details = state.details
                val type = state.type
                if (details == null || type == null) {
                    if (state.error == null) AppProgressIndicator()
                    state.error?.let {
                        Text(errorText(it), color = MaterialTheme.colorScheme.error)
                        AppSecondaryButton(onClick = { state.eventSink(EditActivityEvent.Retry) }) {
                            Text(stringResource(Res.string.action_retry))
                        }
                    }
                } else {
                    ActivityDetailsForm(state.title, titlePlaceholder = activityTypeLabel(type), fields = details, enabled = !state.saving)
                    state.visibility?.let { visibility ->
                        ActivityVisibilityForm(
                            visibility,
                            onActivityChange = { state.eventSink(EditActivityEvent.VisibilityChanged(it)) },
                            onCategoryChange = { category, tier ->
                                state.eventSink(EditActivityEvent.CategoryVisibilityChanged(category, tier))
                            },
                            enabled = !state.saving,
                        )
                    }
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
