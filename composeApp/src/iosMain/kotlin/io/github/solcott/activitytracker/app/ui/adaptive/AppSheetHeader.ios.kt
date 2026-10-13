package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
actual fun AppSheetHeader(
    title: String,
    cancelLabel: String,
    onCancel: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier,
) {
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
}
