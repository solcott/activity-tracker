package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun AppProgressIndicator(modifier: Modifier, small: Boolean) {
    CircularProgressIndicator(modifier = if (small) modifier.size(16.dp) else modifier, strokeWidth = if (small) 2.dp else 4.dp)
}
