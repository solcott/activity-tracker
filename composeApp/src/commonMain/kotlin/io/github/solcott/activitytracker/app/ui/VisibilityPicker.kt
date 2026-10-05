package io.github.solcott.activitytracker.app.ui

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.visibility_mutuals
import io.github.solcott.activitytracker.composeApp.resources.visibility_public
import io.github.solcott.activitytracker.composeApp.resources.visibility_self
import io.github.solcott.activitytracker.lexicon.app.Visibility
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
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, visibility ->
            SegmentedButton(
                selected = visibility == selected,
                onClick = { onSelect(visibility) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                enabled = enabled,
            ) {
                Text(stringResource(visibilityLabel(visibility)))
            }
        }
    }
}

private fun visibilityLabel(visibility: String) =
    when (visibility) {
        Visibility.PUBLIC -> Res.string.visibility_public
        Visibility.MUTUALS -> Res.string.visibility_mutuals
        else -> Res.string.visibility_self
    }
