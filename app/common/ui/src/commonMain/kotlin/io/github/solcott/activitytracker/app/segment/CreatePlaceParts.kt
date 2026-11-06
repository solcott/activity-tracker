package io.github.solcott.activitytracker.app.segment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppTextButton
import kotlinx.collections.immutable.ImmutableList

// Pieces the Create segment and Create dive site screens share.

/**
 * [heading] (that what's drawn matches places already there) over a button for each of [labels], opening that place ([onOpen], by index).
 */
@Composable
fun PlaceDuplicates(heading: String, labels: ImmutableList<String>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(heading, style = MaterialTheme.typography.bodyMedium)
        labels.forEachIndexed { index, label -> AppTextButton(onClick = { onOpen(index) }) { Text(label) } }
    }
}

/** Why a create screen can't offer its form, filling the screen. */
@Composable
fun CreatePlaceMessage(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
