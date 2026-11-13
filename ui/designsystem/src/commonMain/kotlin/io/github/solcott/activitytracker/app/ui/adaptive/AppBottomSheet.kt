package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A modal sheet: Material's modal bottom sheet. Shown while composed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@NonRestartableComposable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    sheetState: AppSheetState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState.material, modifier = modifier, content = content)
}

/**
 * A sheet's title row with Cancel and a confirming action: the title first, then a text Cancel and a filled [confirmLabel] button.
 * [confirmEnabled] false shows the confirming action as unavailable.
 */
@Composable
fun AppSheetHeader(
    title: String,
    cancelLabel: String,
    onCancel: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        AppTextButton(onClick = onCancel) { Text(cancelLabel) }
        AppButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
    }
}
