package io.github.solcott.activitytracker.app.ui.adaptive

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pull to refresh: Material's spinner disc, or on iOS a native activity indicator that fades in as you pull, like `UIRefreshControl`. The
 * indicator appears below the top of [contentPadding] (a `Scaffold`'s), so content can scroll under the top bar while the indicator stays
 * in view.
 */
@Composable
expect fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit,
)
