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
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_badge_description
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_badge_followers
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_badge_mutuals
import io.github.solcott.activitytracker.composeApp.resources.activity_visibility_badge_self
import io.github.solcott.activitytracker.lexicon.app.Visibility
import org.jetbrains.compose.resources.stringResource

/** Who may see a non-public activity, as a small label beside it; nothing for a public one. [visibility] is a [Visibility] value. */
@Composable
fun VisibilityBadge(visibility: String, modifier: Modifier = Modifier) {
    val label =
        when (Visibility.known(visibility)) {
            Visibility.FOLLOWERS -> stringResource(Res.string.activity_visibility_badge_followers)
            Visibility.MUTUALS -> stringResource(Res.string.activity_visibility_badge_mutuals)
            Visibility.SELF -> stringResource(Res.string.activity_visibility_badge_self)
            else -> return
        }
    val description = stringResource(Res.string.activity_visibility_badge_description, label)
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
