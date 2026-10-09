package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.ExperimentalCalfUiApi
import com.mohamedrejeb.calf.ui.dialog.AdaptiveBasicAlertDialog
import com.mohamedrejeb.calf.ui.dialog.uikit.AlertDialogIosAction
import com.mohamedrejeb.calf.ui.dialog.uikit.AlertDialogIosActionStyle
import com.mohamedrejeb.calf.ui.dialog.uikit.AlertDialogIosProperties
import kotlinx.collections.immutable.ImmutableList

/**
 * Asks the athlete to confirm, shown while composed: a native `UIAlertController` on iOS, Material's alert dialog elsewhere. Any number of
 * [actions] (Calf's two-button alert can't offer a third choice), then [cancelLabel]; picking one runs it, and every way out dismisses.
 */
@OptIn(ExperimentalCalfUiApi::class)
@Composable
fun AppAlertDialog(
    title: String,
    message: String,
    actions: ImmutableList<AppDialogAction>,
    cancelLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iosProperties =
        remember(title, message, actions, cancelLabel, onDismiss) {
            AlertDialogIosProperties(
                title = title,
                text = message,
                actions =
                    actions.map { action ->
                        AlertDialogIosAction(
                            title = action.label,
                            style = if (action.destructive) AlertDialogIosActionStyle.Destructive else AlertDialogIosActionStyle.Default,
                            onClick = {
                                onDismiss()
                                action.onClick()
                            },
                        )
                    } + AlertDialogIosAction(cancelLabel, AlertDialogIosActionStyle.Cancel, onDismiss),
            )
        }
    AdaptiveBasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier, iosProperties = iosProperties) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // One action sits beside Cancel, as Material lays out a confirmation; more stack, end-aligned, as it does buttons too long
                // to share a row.
                if (actions.size == 1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        AppTextButton(onClick = onDismiss) { Text(cancelLabel) }
                        ActionButton(actions.single(), onDismiss)
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                        actions.forEach { ActionButton(it, onDismiss) }
                        AppTextButton(onClick = onDismiss) { Text(cancelLabel) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(action: AppDialogAction, onDismiss: () -> Unit) {
    AppTextButton(
        onClick = {
            onDismiss()
            action.onClick()
        }
    ) {
        Text(action.label, color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}
