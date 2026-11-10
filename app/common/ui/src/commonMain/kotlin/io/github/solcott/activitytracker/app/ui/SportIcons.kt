package io.github.solcott.activitytracker.app.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.solcott.activitytracker.app.icon.SportIconNames
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.allDrawableResources
import io.github.solcott.activitytracker.lexicon.app.ActivityType
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * The icon for [type]: its sub-sport's, else its sport's, else a generic one for a sport another client added. Which image that is comes
 * from `:app-common-api`'s [SportIconNames], shared with the native iOS UI; this finds the vendored drawable of that name.
 */
fun activityTypeIcon(type: ActivityType): DrawableResource = Res.allDrawableResources.getValue(SportIconNames.of(type))

/** [type]'s icon. Decorative by default, since it always sits next to the type's label. */
@Composable
fun ActivityTypeIcon(
    type: ActivityType,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
) {
    Icon(vectorResource(activityTypeIcon(type)), contentDescription, modifier, tint)
}
