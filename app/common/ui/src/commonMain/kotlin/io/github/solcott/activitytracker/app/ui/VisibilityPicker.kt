package io.github.solcott.activitytracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.text.LabelText
import io.github.solcott.activitytracker.app.ui.adaptive.AppMenuItem
import io.github.solcott.activitytracker.app.visibility.VisibilityTexts
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.visibility_label
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * A Public / Followers / Mutuals / Only me choice of who may see something, as a dropdown: four segments don't fit a phone's width.
 * [selected] is a [Visibility] value, one of [options] (all four by default; a chart's are capped at its activity's). [approvesFollowers]
 * is the athlete's follow policy: without approval anyone can follow them, so choosing Followers says so.
 */
@Composable
fun VisibilityPicker(
    selected: String?,
    onSelect: (String) -> Unit,
    approvesFollowers: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    options: ImmutableList<String> = ALL,
) {
    val labels = options.map { it to visibilityLabel(it) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MenuField(
            shown = labels.firstOrNull { it.first == selected }?.second.orEmpty(),
            label = stringResource(Res.string.visibility_label),
            items = labels.map { (value, label) -> AppMenuItem(label, checked = value == selected) { onSelect(value) } }.toImmutableList(),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
        VisibilityTexts.followersNote(selected, approvesFollowers)?.let { note ->
            Text(
                note.string(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val ALL: ImmutableList<String> = Visibility.all.toImmutableList()

/** A [Visibility] value's name, as pickers show it. */
@Composable fun visibilityLabel(visibility: String): String = LabelText.visibility(visibility).string()
