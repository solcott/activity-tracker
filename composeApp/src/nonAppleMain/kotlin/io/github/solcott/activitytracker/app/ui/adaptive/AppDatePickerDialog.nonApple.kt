package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_ok
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AppDatePickerDialog(
    initialSelectedDateMillis: Long?,
    selectableDates: SelectableDates,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialSelectedDateMillis, selectableDates = selectableDates)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.selectedDateMillis) }) { Text(stringResource(Res.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) } },
    ) {
        DatePicker(pickerState)
    }
}
