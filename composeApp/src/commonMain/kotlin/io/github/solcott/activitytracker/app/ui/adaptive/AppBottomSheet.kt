package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.AdaptiveSheetState
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform

/** State for an [AppBottomSheet]. It opens fully, or at half height (iOS's medium detent) when [halfHeight]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppSheetState(halfHeight: Boolean = false): AdaptiveSheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = !halfHeight)

/** A modal sheet: a native `UISheetPresentationController` sheet on iOS, Material's modal bottom sheet elsewhere. Shown while composed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@NonRestartableComposable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    sheetState: AdaptiveSheetState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AdaptiveBottomSheet(onDismissRequest = onDismiss, adaptiveSheetState = sheetState, modifier = modifier, content = content)
}

/**
 * A sheet's title row with Cancel and a confirming action. iOS lays it out like a sheet's navigation bar: Cancel leading, the title centred
 * and [confirmLabel] trailing in semibold, all as text. Material platforms put the title first, then a text Cancel and a filled
 * [confirmLabel] button.
 */
@Composable
fun AppSheetHeader(
    title: String,
    cancelLabel: String,
    onCancel: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalUiPlatform.current == UiPlatform.Ios) {
        Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            AppTextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterStart)) { Text(cancelLabel) }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 88.dp),
            )
            AppTextButton(onClick = onConfirm, modifier = Modifier.align(Alignment.CenterEnd)) {
                Text(confirmLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        Row(
            modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            AppTextButton(onClick = onCancel) { Text(cancelLabel) }
            AppButton(onClick = onConfirm) { Text(confirmLabel) }
        }
    }
}
