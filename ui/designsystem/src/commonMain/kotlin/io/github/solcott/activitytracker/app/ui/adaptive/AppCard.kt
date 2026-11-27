package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A surface for one group of content, like a metric's card on an activity's page: an outlined Material card. It fills the width and clips
 * [content] to its shape; pad the content inside it.
 */
@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

/**
 * An [AppCard] that opens something when tapped: [onClick] runs, and its press and hover layer stays inside the card's corners. Pad the
 * content inside it.
 */
@Composable
fun AppCard(onClick: () -> Unit, modifier: Modifier = Modifier, onClickLabel: String? = null, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth().clip(CardShape).clickable(onClickLabel = onClickLabel, onClick = onClick).handCursor(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

private val CardShape = RoundedCornerShape(16.dp)
