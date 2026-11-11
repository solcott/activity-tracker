package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.lexicon.app.Visibility

/** A non-public tier's short label (Followers, Mutuals, Only me), as badges and locks show it; null for public or an unknown tier. */
@Composable fun narrowTierLabel(visibility: String): String? = VisibilityTexts.badge(visibility)?.string()

/** Who may see a non-public activity, as a small label beside it; nothing for a public one. [visibility] is a [Visibility] value. */
@Composable
fun VisibilityBadge(visibility: String, modifier: Modifier = Modifier) {
    val label = narrowTierLabel(visibility) ?: return
    val description = VisibilityTexts.badgeDescription(visibility)?.string().orEmpty()
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            modifier
                .semantics { contentDescription = description }
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
