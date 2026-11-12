package io.github.solcott.activitytracker.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppAlertDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppBottomSheet
import io.github.solcott.activitytracker.app.ui.adaptive.AppDialogAction
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import io.github.solcott.activitytracker.app.ui.adaptive.rememberAppSheetState
import io.github.solcott.activitytracker.app.ui.string
import io.github.solcott.activitytracker.lexicon.app.ActivityView
import io.github.solcott.activitytracker.lexicon.app.MemberView
import kotlinx.collections.immutable.persistentListOf

/**
 * The owner's combination editor (`feed/combined-sources`): the members, each of which can be taken out (confirmed), and where each kind of
 * data comes from. Every change applies at once, so the sheet only has Done.
 */
@Composable
internal fun EditCombinationSheet(
    activity: ActivityView,
    sources: DataSources?,
    eventSink: (ActivityDetailEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var removing by remember { mutableStateOf<MemberView?>(null) }
    AppBottomSheet(onDismiss = onDismiss, sheetState = rememberAppSheetState()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(CombinationTexts.title.string(), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            AppTextButton(onClick = onDismiss) { Text(CombinationTexts.done.string()) }
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(CombinationTexts.members.string(), style = MaterialTheme.typography.labelLarge)
            activity.members.orEmpty().forEach { member ->
                val name = memberName(member)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Text(name, style = MaterialTheme.typography.bodyMedium) }
                    AppIconButton(AppIcon.Close, CombinationTexts.remove(member).string(), onClick = { removing = member })
                }
            }
            sources?.let { DataSourcesSection(it, eventSink) }
        }
    }
    removing?.let { member ->
        val remove = CombinationTexts.remove(member).string()
        AppAlertDialog(
            title = remove,
            message = CombinationTexts.removeMessage.string(),
            actions = persistentListOf(AppDialogAction(remove, destructive = true) { eventSink(CombinationTexts.removeEvent(member)) }),
            cancelLabel = CombinationTexts.cancel.string(),
            onDismiss = { removing = null },
        )
    }
}
