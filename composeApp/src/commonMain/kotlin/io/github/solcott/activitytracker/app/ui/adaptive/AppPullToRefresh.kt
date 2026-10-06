package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.solcott.activitytracker.app.ui.theme.LocalUiPlatform
import io.github.solcott.activitytracker.app.ui.theme.UiPlatform

/** Pull to refresh: Material's spinner disc, or on iOS a bare activity indicator that fades in as you pull, like `UIRefreshControl`. */
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val ios = LocalUiPlatform.current == UiPlatform.Ios
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            if (!ios) {
                PullToRefreshDefaults.Indicator(state = state, isRefreshing = isRefreshing, modifier = Modifier.align(Alignment.TopCenter))
            } else if (isRefreshing || state.distanceFraction > 0f) {
                AppProgressIndicator(
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp).graphicsLayer {
                        alpha = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f)
                    }
                )
            }
        },
        content = content,
    )
}
