package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

/** An [AppBottomSheet]'s state: [hide] slides it away, for a sheet that closes itself before its caller drops it. */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class AppSheetState internal constructor(internal val material: SheetState) {
    suspend fun hide() = material.hide()
}

/** State for an [AppBottomSheet]. It opens fully, or at half height when [halfHeight]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppSheetState(halfHeight: Boolean = false): AppSheetState {
    val material = rememberModalBottomSheetState(skipPartiallyExpanded = !halfHeight)
    return remember(material) { AppSheetState(material) }
}
