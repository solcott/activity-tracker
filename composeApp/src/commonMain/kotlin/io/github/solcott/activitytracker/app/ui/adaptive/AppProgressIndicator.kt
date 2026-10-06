package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform

/** An indeterminate spinner: Material's ring, or iOS's grey activity indicator. [small] sizes it for a button. */
@Composable
fun AppProgressIndicator(modifier: Modifier = Modifier, small: Boolean = false) {
    val ios = LocalUiPlatform.current == UiPlatform.Ios
    AdaptiveCircularProgressIndicator(
        modifier = if (small) modifier.size(16.dp) else modifier,
        color = if (ios) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        strokeWidth = if (small) 2.dp else 4.dp,
    )
}
