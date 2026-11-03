package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mohamedrejeb.calf.ui.datepicker.DateBounds
import io.github.solcott.activitytracker.app.ui.adaptive.AppDatePickerDialog
import io.github.solcott.activitytracker.app.ui.adaptive.AppIcon
import io.github.solcott.activitytracker.app.ui.adaptive.AppIconButton
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextField
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime

private const val MILLIS_PER_DAY = 86_400_000L

/** [this] as the date picker's milliseconds: UTC midnight at its start. */
fun LocalDate.toPickerMillis(): Long = toEpochDays() * MILLIS_PER_DAY

/** The day the date picker's [millis] (UTC midnight at its start) stand for. */
fun pickedDate(millis: Long): LocalDate = LocalDate.fromEpochDays(millis.floorDiv(MILLIS_PER_DAY))

/**
 * A past date, shown as [shown], picked from a calendar that opens on [pickedMillis] (the picker's milliseconds, null for today) by the
 * [chooseLabel] button and that offers no day after today. [onPick] gets the day picked; a [clearLabel] button runs [onClear] when it isn't
 * null, which is when there's a date to clear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastDateField(
    shown: String,
    label: String,
    chooseLabel: String,
    clearLabel: String,
    pickedMillis: Long?,
    onPick: (Long) -> Unit,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var picking by remember { mutableStateOf(false) }
    AppTextField(
        rememberReadOnlyTextFieldState(shown),
        modifier,
        label = label,
        enabled = enabled,
        readOnly = true,
        trailingIcon = {
            Row {
                if (onClear != null) {
                    AppIconButton(AppIcon.Clear, clearLabel, onClear, enabled = enabled, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppIconButton(AppIcon.Calendar, chooseLabel, { picking = true }, enabled = enabled)
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
    )
    if (picking) {
        val clock = LocalClock.current
        val zone = LocalTimeZone.current
        // The picker's days are UTC midnights, so the last one allowed is the athlete's own today, wherever they are, not today in UTC.
        val pastOnly = remember(clock, zone) { DateBounds(maxDateMillis = clock.now().toLocalDateTime(zone).date.toPickerMillis()) }
        AppDatePickerDialog(
            initialSelectedDateMillis = pickedMillis,
            selectableDates = pastOnly,
            onConfirm = { millis ->
                millis?.let(onPick)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}
