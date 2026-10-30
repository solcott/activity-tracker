package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Picks a day, shown while composed. Material platforms get Material's date picker dialog; iOS gets a sheet with UIKit's date wheels and
 * Cancel / Done. Dates are UTC milliseconds at the start of the day, as Material's picker uses; [onConfirm] gets null when none was picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
expect fun AppDatePickerDialog(
    initialSelectedDateMillis: Long?,
    selectableDates: SelectableDates,
    onConfirm: (Long?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
)
