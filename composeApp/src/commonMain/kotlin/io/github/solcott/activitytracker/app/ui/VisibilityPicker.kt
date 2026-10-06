package io.github.solcott.activitytracker.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.app.ui.adaptive.AppSegmentedControl
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.visibility_mutuals
import io.github.solcott.activitytracker.composeApp.resources.visibility_public
import io.github.solcott.activitytracker.composeApp.resources.visibility_self
import io.github.solcott.activitytracker.lexicon.app.Visibility
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** A Public / Mutuals / Only me segmented choice of who may see a profile detail. [selected] is a [Visibility] value. */
@Composable
fun VisibilityPicker(
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val options = listOf(Visibility.PUBLIC, Visibility.MUTUALS, Visibility.SELF)
    AppSegmentedControl(
        labels =
            persistentListOf(
                stringResource(Res.string.visibility_public),
                stringResource(Res.string.visibility_mutuals),
                stringResource(Res.string.visibility_self),
            ),
        selected = options.indexOf(selected),
        onSelect = { onSelect(options[it]) },
        modifier = modifier,
        enabled = enabled,
    )
}
