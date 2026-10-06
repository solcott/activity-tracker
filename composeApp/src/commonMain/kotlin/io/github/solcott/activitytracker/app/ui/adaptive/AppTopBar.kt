package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform
import io.github.solcott.activitytracker.composeApp.resources.Res
import io.github.solcott.activitytracker.composeApp.resources.action_back
import org.jetbrains.compose.resources.stringResource

/**
 * Whether this screen is the bottom of the back stack, which is a tab's root once signed in. `App` provides it per back-stack record. A
 * root's bar has no back button, and on iOS it shows a large title.
 */
val LocalIsNavRoot = compositionLocalOf { false }

/**
 * A screen's title bar. Material platforms get Material 3's top app bar. iOS gets a navigation bar drawn in Compose (so it slides with the
 * screen and follows the swipe-back gesture): a back chevron, a centred title, tinted bar buttons, and a large title at a tab's root.
 *
 * [onBack] shows a back button unless this is the root screen ([LocalIsNavRoot]). Put [AppIconButton]s and [AppMenu]s in [actions].
 */
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val root = LocalIsNavRoot.current
    val back = onBack?.takeUnless { root }
    if (LocalUiPlatform.current == UiPlatform.Ios) {
        IosNavigationBar(title, subtitle, back, large = root, actions, modifier)
    } else {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            },
            modifier = modifier,
            navigationIcon = { back?.let { AppIconButton(AppIcon.Back, stringResource(Res.string.action_back), it) } },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }
}

@Composable
private fun IosNavigationBar(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    large: Boolean,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        CenteredBar(
            leading = { onBack?.let { AppIconButton(AppIcon.Back, stringResource(Res.string.action_back), it) } },
            title = {
                if (!large) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            },
            trailing = { Row(verticalAlignment = Alignment.CenterVertically, content = actions) },
            modifier = Modifier.fillMaxWidth().height(IOS_BAR_HEIGHT).padding(horizontal = 4.dp),
        )
        if (large) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            }
        } else {
            HorizontalDivider(thickness = HAIRLINE, color = colors.outlineVariant)
        }
    }
}

/** UIKit's bar layout: the title is centred on the bar, not between the buttons, and only shrinks as far as the wider side allows. */
@Composable
private fun CenteredBar(
    leading: @Composable () -> Unit,
    title: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box(Modifier.layoutId(LEADING)) { leading() }
            Box(Modifier.layoutId(TITLE)) { title() }
            Box(Modifier.layoutId(TRAILING)) { trailing() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leadingPlaceable = measurables.first { it.layoutId == LEADING }.measure(loose)
        val trailingPlaceable = measurables.first { it.layoutId == TRAILING }.measure(loose)
        val side = maxOf(leadingPlaceable.width, trailingPlaceable.width)
        val titleWidth = (constraints.maxWidth - 2 * side).coerceAtLeast(0)
        val titlePlaceable =
            measurables.first { it.layoutId == TITLE }.measure(Constraints(maxWidth = titleWidth, maxHeight = constraints.maxHeight))
        val height = constraints.maxHeight
        layout(constraints.maxWidth, height) {
            leadingPlaceable.place(0, (height - leadingPlaceable.height) / 2)
            titlePlaceable.place((constraints.maxWidth - titlePlaceable.width) / 2, (height - titlePlaceable.height) / 2)
            trailingPlaceable.place(constraints.maxWidth - trailingPlaceable.width, (height - trailingPlaceable.height) / 2)
        }
    }
}

private const val LEADING = "leading"
private const val TITLE = "title"
private const val TRAILING = "trailing"
private val IOS_BAR_HEIGHT = 44.dp
private val HAIRLINE = 0.5.dp
