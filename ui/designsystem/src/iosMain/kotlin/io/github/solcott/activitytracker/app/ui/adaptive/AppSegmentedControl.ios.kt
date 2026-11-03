package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.collections.immutable.ImmutableList
import platform.UIKit.UIAction
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISegmentedControl
import platform.UIKit.UISegmentedControlNoSegment

/**
 * A native `UISegmentedControl`, which draws its own track, thumb and dark mode and reads its segments to VoiceOver. It's rebuilt when
 * [labels] change, since UIKit's segments are set up one by one. It goes over the Compose canvas: its track is translucent, and under the
 * canvas it would show the window's white through its cut-out rather than the page or card it sits on. So it hides itself under the top bar
 * ([UnderTopBar]), which can't blur it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun AppSegmentedControl(
    labels: ImmutableList<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val underBar = rememberUnderTopBar()
    key(labels) {
        UIKitView(
            factory = {
                UISegmentedControl(items = labels).apply {
                    addAction(UIAction.actionWithHandler { _ -> currentOnSelect(selectedSegmentIndex.toInt()) }, UIControlEventValueChanged)
                }
            },
            update = {
                it.selectedSegmentIndex = if (selected in labels.indices) selected.toLong() else UISegmentedControlNoSegment.toLong()
                it.enabled = enabled
                underBar.update(it)
            },
            modifier = modifier.height(IOS_HEIGHT).then(underBar.modifier),
            properties =
                UIKitInteropProperties(
                    interactionMode = if (underBar.covered) null else COOPERATIVE,
                    isNativeAccessibilityEnabled = true,
                    placedAsOverlay = true,
                ),
        )
    }
}

/** UIKitView's default interaction mode, which a covered control gives up. */
private val COOPERATIVE = UIKitInteropInteractionMode.Cooperative()

/** `UISegmentedControl`'s own height. */
private val IOS_HEIGHT = 32.dp
