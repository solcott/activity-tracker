package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            if (isRefreshing || state.distanceFraction > 0f) {
                ActivityIndicator(
                    Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding() + 12.dp),
                    alpha = if (isRefreshing) 1f else state.distanceFraction.coerceIn(0f, 1f),
                )
            }
        },
        content = content,
    )
}
