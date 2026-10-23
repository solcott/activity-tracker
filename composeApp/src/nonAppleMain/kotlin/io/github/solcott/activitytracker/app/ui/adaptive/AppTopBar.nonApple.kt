package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.solcott.activitytracker.app.ui.TestTags
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable actual fun rememberAppTopBarScrollBehavior(): TopAppBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

@Composable
actual fun AppTopBar(
    title: String,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    subtitle: String?,
    scrollBehavior: TopAppBarScrollBehavior?,
    titleMenu: ImmutableList<AppMenuItem>?,
    actions: @Composable RowScope.() -> Unit,
) {
    val back = onBack?.takeUnless { LocalIsNavRoot.current }
    TopAppBar(
        title = {
            Column {
                TopBarTitle(title, LocalTextStyle.current, titleMenu)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        },
        modifier = modifier,
        navigationIcon = {
            back?.let { AppIconButton(AppIcon.Back, stringResource(Res.string.action_back), it, Modifier.testTag(TestTags.BACK)) }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        scrollBehavior = scrollBehavior,
    )
}
