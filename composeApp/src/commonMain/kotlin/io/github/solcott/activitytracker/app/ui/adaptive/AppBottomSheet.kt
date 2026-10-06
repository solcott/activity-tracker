package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.AdaptiveSheetState
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState

/** State for an [AppBottomSheet]. It opens fully, or at half height (iOS's medium detent) when [halfHeight]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppSheetState(halfHeight: Boolean = false): AdaptiveSheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = !halfHeight)

/** A modal sheet: a native `UISheetPresentationController` sheet on iOS, Material's modal bottom sheet elsewhere. Shown while composed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    sheetState: AdaptiveSheetState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    AdaptiveBottomSheet(onDismissRequest = onDismiss, adaptiveSheetState = sheetState, modifier = modifier, content = content)
}
