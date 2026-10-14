package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.visibility_followers
import io.github.solcott.activitytracker.composeApp.resources.visibility_followers_open_note
import io.github.solcott.activitytracker.composeApp.resources.visibility_label
import io.github.solcott.activitytracker.composeApp.resources.visibility_mutuals
import io.github.solcott.activitytracker.composeApp.resources.visibility_public
import io.github.solcott.activitytracker.composeApp.resources.visibility_self
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * A Public / Followers / Mutuals / Only me choice of who may see something, as a dropdown: four segments don't fit a phone's width.
 * [selected] is a [Visibility] value. [approvesFollowers] is the athlete's follow policy: without approval anyone can follow them, so
 * choosing Followers says so.
 */
@Composable
fun VisibilityPicker(
    selected: String?,
    onSelect: (String) -> Unit,
    approvesFollowers: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val labels =
        listOf(
            Visibility.PUBLIC to stringResource(Res.string.visibility_public),
            Visibility.FOLLOWERS to stringResource(Res.string.visibility_followers),
            Visibility.MUTUALS to stringResource(Res.string.visibility_mutuals),
            Visibility.SELF to stringResource(Res.string.visibility_self),
        )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MenuField(
            shown = labels.firstOrNull { it.first == selected }?.second.orEmpty(),
            label = stringResource(Res.string.visibility_label),
            items = labels.map { (value, label) -> AppMenuItem(label, checked = value == selected) { onSelect(value) } }.toImmutableList(),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        if (selected == Visibility.FOLLOWERS && !approvesFollowers) {
            Text(
                stringResource(Res.string.visibility_followers_open_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
