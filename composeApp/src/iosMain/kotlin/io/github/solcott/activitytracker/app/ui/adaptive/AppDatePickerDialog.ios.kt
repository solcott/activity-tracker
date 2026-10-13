package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.datepicker.AdaptiveDatePicker
import com.mohamedrejeb.calf.ui.datepicker.UIKitDisplayMode
import com.mohamedrejeb.calf.ui.datepicker.rememberAdaptiveDatePickerState
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_cancel
import io.github.solcott.activitytracker.composeApp.resources.action_done
import kotlinx.coroutines.launch
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
    val sheetState = rememberAppSheetState(halfHeight = true)
    val pickerState =
        rememberAdaptiveDatePickerState(
            initialSelectedDateMillis = initialSelectedDateMillis,
            initialUIKitDisplayMode = UIKitDisplayMode.Wheels,
            selectableDates = selectableDates,
        )
    val scope = rememberCoroutineScope()
    val close: (() -> Unit) -> Unit = { then -> scope.launch { sheetState.hide() }.invokeOnCompletion { then() } }
    AppBottomSheet(onDismiss = onDismiss, sheetState = sheetState, modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppTextButton(onClick = { close(onDismiss) }) { Text(stringResource(Res.string.action_cancel)) }
            Box(Modifier.weight(1f))
            AppTextButton(onClick = { close { onConfirm(pickerState.selectedDateMillis) } }) {
                Text(stringResource(Res.string.action_done))
            }
        }
        AdaptiveDatePicker(
            pickerState,
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            // The wheels sit straight on the sheet, as in UIKit.
            colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
        )
    }
}
